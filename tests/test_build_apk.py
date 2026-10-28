"""Exercise the real APK toolchain with harmless, locally compiled native fixtures.
No game code or Android device is used. SDK/NDK/javac must be installed.
Run: python3 -m unittest discover -s tests -p test_build_apk.py -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_SDK_ROOT'])
NDK = Path(os.environ['ANDROID_NDK_HOME'])
TC = NDK / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
GID = '0123456789abcdef'


class Packaging(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='apk-test-', dir=ROOT.parent)
        self.addCleanup(self.cleanup_nfs)
        self.root = Path(self.tmp.name)
        shutil.copy2(ROOT / 'build-apk.sh', self.root)
        for path in ('apk/java', 'apk/res', 'apk/assets/fonts', 'build/apk-lib',
                     'deps/SDL3/android-project/app/src/main/java',
                     'deps/SDL3/build-android', 'build/game'):
            (self.root / path).mkdir(parents=True)
        (self.root / 'apk/AndroidManifest.xml').write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="com.example.packagetest" android:versionCode="1" '
            'android:versionName="1"><application android:hasCode="true"/></manifest>')
        (self.root / 'apk/java/Fixture.java').write_text(
            'public class Fixture { Runnable callback = () -> {}; }')
        (self.root / 'apk/assets/fonts/test.txt').write_text('harmless font fixture')
        self.native('build/apk-lib/liblasrecomp.so')
        self.native('deps/SDL3/build-android/libSDL3.so')
        self.env = {**os.environ, 'LETSGO_KEYSTORE': str(self.root / 'debug.keystore'),
                    'LETSGO_KS_PASS': 'pass:android', 'LETSGO_KEY_PASS': 'pass:android'}
        # These fixtures deliberately have no product builder; do not stage a real
        # private kit or inherit a caller's destination into synthetic builds.
        for name in ('LETSGO_KIT_SOURCE', 'LETSGO_BUILD_ASSETS', 'LETSGO_APK_DEST'):
            self.env.pop(name, None)
        subprocess.run(['keytool', '-genkeypair', '-keystore', self.env['LETSGO_KEYSTORE'],
                        '-storepass', 'android', '-keypass', 'android', '-alias', 'debug',
                        '-dname', 'CN=Local packaging fixture', '-keyalg', 'RSA',
                        '-validity', '2'], check=True, capture_output=True)

    def cleanup_nfs(self):
        # Keep successful packaging fixtures as inspectable build evidence. Unraid
        # FUSE-over-NFS leaves .fuse_hidden APKs on unlink; do not hide ENOTEMPTY
        # or move builds back to IA. Rename our own output, then remove temp name.
        out = ROOT / 'build/test-results'
        out.mkdir(parents=True, exist_ok=True)
        if self.root.exists():
            self.root.rename(out / self.root.name)
        self.tmp.cleanup()

    def native(self, dest, gid=GID):
        source = self.root / 'fixture.c'
        source.write_text('const char fixture_id[] = "' + gid + '";')
        subprocess.run([str(TC / 'aarch64-linux-android26-clang'), '-shared',
                        str(source), '-o', str(self.root / dest)], check=True, capture_output=True)

    def build(self, mode):
        env = {**self.env, 'GAME': mode}
        result = subprocess.run(['bash', str(self.root / 'build-apk.sh')],
                                cwd=self.root, env=env, capture_output=True, text=True)
        self.output = result.stdout + result.stderr
        return result.returncode

    def test_none_omits_game_even_when_present_in_apklib(self):
        self.native('build/game/liblasgame.so')
        self.native('build/apk-lib/liblasgame.so')
        (self.root / 'build/game/liblasgame.so.id').write_text(GID)
        self.assertEqual(self.build('none'), 0, self.output)
        self.assertIn('APK_NO_GAME_OK', self.output)
        with zipfile.ZipFile(self.root / 'LetsGoRecomp-android.apk') as apk:
            self.assertFalse(any(Path(n).name == 'liblasgame.so' for n in apk.namelist()))
            self.assertIn('lib/arm64-v8a/liblasrecomp.so', apk.namelist())

    def test_none_rejects_hidden_game_payload_before_signing(self):
        (self.root / 'apk/assets/romfs.bin').write_bytes(b'synthetic-not-a-game')
        self.assertNotEqual(self.build('none'), 0, self.output)
        self.assertIn('APK_GAME_PAYLOAD_FORBIDDEN', self.output)
        self.assertFalse((self.root / 'LetsGoRecomp-android.apk').exists())

    def test_none_rejects_game_payload_in_additional_kit_assets(self):
        extra = self.root / 'extra'; extra.mkdir()
        (extra / 'main_001.c').write_text('synthetic fixture')
        self.env['LETSGO_BUILD_ASSETS'] = str(extra)
        self.assertNotEqual(self.build('none'), 0, self.output)
        self.assertIn('APK_GAME_PAYLOAD_FORBIDDEN', self.output)

    def test_none_without_game_or_id_builds(self):
        self.assertEqual(self.build('none'), 0, self.output)
        self.assertIn('APK_NO_GAME_OK', self.output)

    def test_with_valid_pair_embeds_game(self):
        self.native('build/game/liblasgame.so')
        (self.root / 'build/game/liblasgame.so.id').write_text(GID)
        self.assertEqual(self.build('with'), 0, self.output)
        self.assertIn('APK_WITH_GAME_OK', self.output)
        with zipfile.ZipFile(self.root / 'LetsGoRecomp-android.apk') as apk:
            self.assertIn('lib/arm64-v8a/liblasgame.so', apk.namelist())

    def test_with_wrong_id_fails(self):
        self.native('build/game/liblasgame.so', gid='fedcba9876543210')
        (self.root / 'build/game/liblasgame.so.id').write_text(GID)
        self.assertNotEqual(self.build('with'), 0, self.output)
        self.assertIn('GAMELIB_ID_MISMATCH', self.output)

    def test_invalid_mode_fails(self):
        self.assertNotEqual(self.build('automatic'), 0, self.output)
        self.assertIn('GAME_MODE_INVALID', self.output)

    def test_with_missing_game_fails_explicitly_before_toolchain(self):
        self.assertNotEqual(self.build('with'), 0, self.output)
        self.assertIn('GAME_LIBRARY_MISSING', self.output)
        self.assertFalse((self.root / 'LetsGoRecomp-android.apk').exists())


if __name__ == '__main__':
    unittest.main()
