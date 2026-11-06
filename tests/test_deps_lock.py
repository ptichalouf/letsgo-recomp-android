"""deps.lock is complete and well-formed (offline; CI job `deps-lock` downloads and checks every sha256)."""
from pathlib import Path
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/ci'))
import deps  # noqa: E402

REQUIRED = {'sdl3', 'ffmpeg', 'shaderc', 'glslang', 'spirv-tools', 'spirv-headers', 'libadrenotools',
            'liblinkernsbypass', 'libarchive', 'xz', 'cpython-android', 'capstone', 'unicorn', 'lz4',
            'zig-aarch64-linux', 'zig-x86_64-linux', 'ryujinx-license', 'llvm-license'}


class DepsLock(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.deps, cls.tools, cls.lics = deps.parse()

    def test_every_dependency_is_pinned(self):
        self.assertFalse(REQUIRED - set(self.deps), 'missing from deps.lock')
        for e in self.deps.values():
            self.assertRegex(e['sha256'], r'^[0-9a-f]{64}$', e['name'])
            self.assertTrue(e['url'].startswith('https://'), e['name'])
            self.assertTrue(e['version'] and e['license'], e['name'])
            self.assertIn(e['source'], ('yes', 'no'), e['name'])
        names = [deps.filename(e) for e in self.deps.values()]
        self.assertEqual(len(names), len(set(names)), 'two entries download to the same file name')

    def test_copyleft_parts_ship_their_source(self):
        for e in self.deps.values():
            if re.search(r'\b(L?GPL)-', e['license']) and 'WITH Bison-exception' not in e['license']:
                self.assertEqual(e['source'], 'yes', e['name'] + ' is copyleft: its source goes in the tarball')

    def test_license_texts_exist_and_point_at_pinned_archives(self):
        pairs = [(l['file'], l['dep']) for l in self.lics]
        self.assertEqual(len(pairs), len(set(pairs)))
        for l in self.lics:
            self.assertIn(l['dep'], self.deps, l['file'])
            self.assertTrue((ROOT / 'LICENSES' / l['file']).is_file(), 'LICENSES/' + l['file'])
            if l['lines']:
                self.assertRegex(l['lines'], r'^\d+-\d+$')
        for e in self.deps.values():
            self.assertIn(e['name'], {l['dep'] for l in self.lics}, e['name'] + ' has no license text in LICENSES/')


if __name__ == '__main__':
    unittest.main()
