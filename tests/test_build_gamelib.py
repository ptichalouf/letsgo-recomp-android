"""Real NDK compilation of synthetic C only; no game data or device."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class GameBuild(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='gamelib-test-', dir=ROOT.parent)
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        shutil.copy2(ROOT / 'build-gamelib.sh', self.root)
        self.sources = ['tools/gen.py', 'tools/lifter.py', 'tools/analyze.py', 'tools/nso.py',
                        'tools/passes.py', 'tools/patches.py', 'tools/simd_c.py',
                        'tools/gamelib_entry.c', 'runtime/hle/hle_symbols.txt',
                        'runtime/cpu.h', 'runtime/simd.h', 'runtime/simd_fp.h', 'runtime/gamelib.h']
        for name in self.sources:
            p = self.root / 'src' / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('/* synthetic input */\n' if name.endswith(('.c', '.h')) else '# fixture\n')
        p = self.root / 'src/generated-aarch64/main/a.c'
        p.parent.mkdir(parents=True)
        p.write_text('int synthetic(void) { return 1; }\n')
        self.generated = p
        self.lib = self.root / 'build/game/liblasgame.so'
        self.lib.parent.mkdir(parents=True)
        self.lib.write_bytes(b'previous-working-library')
        self.tc = Path(os.environ['ANDROID_NDK_HOME']) / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
        (self.root / 'build/apk-lib').mkdir(parents=True)
        subprocess.run([str(self.tc / 'aarch64-linux-android26-clang'), '-shared',
                        '-x', 'c', '-', '-o', str(self.root / 'build/apk-lib/liblasrecomp.so')],
                       input='int runtime_fixture;\n', text=True, check=True, capture_output=True)

    def build(self, **extra):
        result = subprocess.run(['bash', str(self.root / 'build-gamelib.sh')],
                                env={**os.environ, 'JOBS': '2', **extra},
                                capture_output=True, text=True)
        self.output = result.stdout + result.stderr
        return result.returncode

    def test_success_ignores_stale_objects_and_has_runtime_dependency(self):
        objdir = self.root / 'build/game/obj'
        objdir.mkdir()
        (objdir / 'stale.o').write_bytes(b'not an object')
        self.assertEqual(self.build(), 0, self.output)
        self.assertIn('objects: 2/2', self.output)
        result = subprocess.run([str(self.tc / 'llvm-readelf'), '-d', str(self.lib)],
                                check=True, capture_output=True, text=True)
        self.assertIn('liblasrecomp.so', result.stdout)
        first_objects = sorted(p.name for p in objdir.glob('*.o'))
        self.assertEqual(self.build(), 0, self.output)
        self.assertEqual(first_objects, sorted(p.name for p in objdir.glob('*.o')))
        (self.root / 'src/runtime/cpu.h').write_text('/* changed header */\n')
        self.assertEqual(self.build(), 0, self.output)
        self.assertEqual(len(list(objdir.glob('*.o'))), len(first_objects) + 2)

    def test_link_failure_preserves_previous_library(self):
        (self.root / 'build/apk-lib/liblasrecomp.so').unlink()
        self.assertNotEqual(self.build(), 0, self.output)
        self.assertIn('LINK_FAIL', self.output)
        self.assertEqual(self.lib.read_bytes(), b'previous-working-library')

    def test_missing_identity_input_aborts(self):
        (self.root / 'src/tools/gen.py').unlink()
        self.assertNotEqual(self.build(), 0, self.output)
        self.assertNotIn('GAMELIB_OK', self.output)
        self.assertEqual(self.lib.read_bytes(), b'previous-working-library')

    def test_zero_jobs_aborts(self):
        self.assertNotEqual(self.build(JOBS='0'), 0, self.output)
        self.assertIn('JOBS_INVALID', self.output)

    def test_compiler_failure_cannot_link_or_replace_previous_library(self):
        self.generated.write_text('#error intentional_compile_failure\n')
        self.assertNotEqual(self.build(), 0, self.output)
        self.assertIn('GAMELIB_BUILD_FAIL', self.output)
        self.assertNotIn('GAMELIB_OK', self.output)
        self.assertEqual(self.lib.read_bytes(), b'previous-working-library')


if __name__ == '__main__':
    unittest.main()
