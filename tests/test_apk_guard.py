"""The final APK must be a valid archive containing what it claims to contain.

build-apk.sh checks the FINAL signed archive (not the staging directory): an
archive whose zip is broken, a dex that never made it in, or a native library that
is missing must be refused rather than shipped.

The guards are exercised against the real script. The archive is made defective by
substituting the packaging tools with small wrappers (a `zip` that drops a given
entry, an `unzip` that reports the archive as broken), so the real script really
runs, really builds a real APK, and the real guard really refuses it.

Run: python3 -m unittest discover -s tests -p test_apk_guard.py -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_SDK_ROOT'])
NDK = Path(os.environ['ANDROID_NDK_HOME'])
TC = NDK / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
GID = '0123456789abcdef'

# The native libraries build-apk.sh requires in every final archive.
REQUIRED_NATIVES = ('liblasrecomp.so', 'libSDL3.so', 'libc++_shared.so')

# A zip wrapper that appends the given files to an archive, but silently drops
# every file whose name contains one of the excluded fragments.
ZIP_WRAPPER = '''#!{python}
import sys, zipfile
args = [a for a in sys.argv[1:] if not a.startswith('-')]
archive, files = args[0], args[1:]
mode = 'a' if zipfile.is_zipfile(archive) else 'w'
with zipfile.ZipFile(archive, mode) as z:
    for f in files:
        if any(x in f for x in {excluded!r}):
            continue
        z.write(f, f)
'''

# An unzip wrapper whose integrity check always reports the archive as broken.
UNZIP_WRAPPER = '''#!{python}
import sys
args = [a for a in sys.argv[1:] if not a.startswith('-')]
if '-t' in sys.argv or '-tq' in sys.argv:
    sys.stderr.write('simulated: archive is broken\\n')
    sys.exit(1)
sys.exit(0)
'''


class ApkGuard(unittest.TestCase):
    """Reuses the packaging fixture of test_build_apk: a real, harmless APK."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='apk-guard-', dir=ROOT.parent)
        self.addCleanup(self.cleanup_nfs)
        self.root = Path(self.tmp.name)
        shutil.copy2(ROOT / 'build-apk.sh', self.root)
        for path in ('apk/java', 'apk/res', 'apk/assets/fonts', 'build/apk-lib',
                     'deps/SDL3/android-project/app/src/main/java',
                     'deps/SDL3/build-android', 'build/game'):
            (self.root / path).mkdir(parents=True)
        (self.root / 'apk/AndroidManifest.xml').write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="com.example.guardtest" android:versionCode="1" '
            'android:versionName="1"><application android:hasCode="true"/></manifest>')
        (self.root / 'apk/java/Fixture.java').write_text(
            'public class Fixture { Runnable callback = () -> {}; }')
        (self.root / 'apk/assets/fonts/test.txt').write_text('harmless font fixture')
        self.native('build/apk-lib/liblasrecomp.so')
        self.native('deps/SDL3/build-android/libSDL3.so')
        self.env = {**os.environ, 'LETSGO_KEYSTORE': str(self.root / 'debug.keystore'),
                    'LETSGO_KS_PASS': 'pass:android', 'LETSGO_KEY_PASS': 'pass:android',
                    'GAME': 'none'}
        # Do not stage a real private kit or inherit a caller's destination.
        for name in ('LETSGO_KIT_SOURCE', 'LETSGO_BUILD_ASSETS', 'LETSGO_APK_DEST'):
            self.env.pop(name, None)
        subprocess.run(['keytool', '-genkeypair', '-keystore', self.env['LETSGO_KEYSTORE'],
                        '-storepass', 'android', '-keypass', 'android', '-alias', 'debug',
                        '-dname', 'CN=Local packaging fixture', '-keyalg', 'RSA',
                        '-validity', '2'], check=True, capture_output=True)

    def cleanup_nfs(self):
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

    def build(self, env=None):
        result = subprocess.run(['bash', str(self.root / 'build-apk.sh')],
                                cwd=self.root, env=env or self.env,
                                capture_output=True, text=True)
        self.output = result.stdout + result.stderr
        return result.returncode

    def apk(self):
        return self.root / 'LetsGoRecomp-android.apk'

    def with_tools(self, zip_excludes=(), unzip_breaks=False):
        """An environment where the packaging tools misbehave on purpose."""
        bindir = self.root / 'fakebin'
        bindir.mkdir(exist_ok=True)
        if zip_excludes:
            z = bindir / 'zip'
            z.write_text(ZIP_WRAPPER.format(python=sys.executable,
                                            excluded=tuple(zip_excludes)))
            z.chmod(0o755)
        if unzip_breaks:
            u = bindir / 'unzip'
            u.write_text(UNZIP_WRAPPER.format(python=sys.executable))
            u.chmod(0o755)
        env = {**self.env, 'PATH': f'{bindir}:{os.environ["PATH"]}'}
        return env

    def test_healthy_apk_passes_the_guards(self):
        """The guards must not reject a correct archive."""
        self.assertEqual(self.build(), 0, self.output)
        self.assertIn('APK_NO_GAME_OK', self.output)
        with zipfile.ZipFile(self.apk()) as apk:
            names = apk.namelist()
        self.assertIn('classes.dex', names)
        for lib in REQUIRED_NATIVES:
            self.assertIn(f'lib/arm64-v8a/{lib}', names)

    def test_broken_archive_is_refused(self):
        """An archive reported as broken must be refused, not shipped."""
        env = self.with_tools(unzip_breaks=True)
        self.assertNotEqual(self.build(env), 0, self.output)
        self.assertIn('APK_ZIP_INVALID', self.output)

    def test_archive_without_dex_is_refused(self):
        """A final archive missing classes.dex must be refused."""
        env = self.with_tools(zip_excludes=('classes.dex',))
        self.assertNotEqual(self.build(env), 0, self.output)
        self.assertIn('APK_DEX_MISSING', self.output)

    def test_archive_without_native_library_is_refused(self):
        """A final archive missing a required native library must be refused."""
        env = self.with_tools(zip_excludes=('libSDL3.so',))
        self.assertNotEqual(self.build(env), 0, self.output)
        self.assertIn('APK_NATIVE_MISSING', self.output)

    def test_missing_packaging_tools_fail_before_any_work(self):
        """zip absent must fail loudly instead of producing a half APK."""
        env = {**self.env, 'PATH': '/nonexistent'}
        result = subprocess.run(['/bin/bash', str(self.root / 'build-apk.sh')],
                                cwd=self.root, env=env, capture_output=True, text=True)
        output = result.stdout + result.stderr
        self.assertNotEqual(result.returncode, 0, output)
        self.assertIn('ZIP_ABSENT', output)


if __name__ == '__main__':
    unittest.main()
