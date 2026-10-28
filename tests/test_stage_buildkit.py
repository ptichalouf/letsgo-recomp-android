"""Exercise the real staging script against the pinned, private upstream kit."""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class StageBuildkit(unittest.TestCase):
    def test_pinned_builtins_and_compiler_are_packaged_exactly(self):
        source = Path(os.environ['LETSGO_KIT_SOURCE'])
        subprocess.run(['python3', str(ROOT / 'tools/stage-buildkit.py')], check=True)
        out = ROOT / 'build/build-assets/buildkit'
        with zipfile.ZipFile(source / 'upstream.apk') as apk:
            with zipfile.ZipFile(io.BytesIO(apk.read('assets/ondevice.zip'))) as kit:
                name = 'cckit/clang/lib/libclang_rt.builtins-aarch64-android.a'
                self.assertTrue((out / name).is_file(), 'compiler-rt missing from staged kit')
                self.assertEqual((out / name).read_bytes(), kit.read(name))
                for name in kit.namelist():
                    if not name.endswith('/') and name.startswith(('pyhome/', 'cckit/clang/include/', 'cckit/sysroot/usr/include/')):
                        self.assertEqual(hashlib.sha256((out / name).read_bytes()).digest(), hashlib.sha256(kit.read(name)).digest(), name)
            self.assertEqual((ROOT / 'build/apk-lib/libzig.so').read_bytes(), apk.read('lib/arm64-v8a/libzig.so'))


if __name__ == '__main__':
    unittest.main()
