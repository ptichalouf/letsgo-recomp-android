"""What a release must carry: notices that resolve, and (with LETSGO_RELEASE_STAGING) the staged assets.

LETSGO_RELEASE_STAGING = the directory release.yml / apk.yml uploads: one GAME=none APK, SHA256SUMS and
letsgo-<ver>-source.tar.gz. Without it only the repository-side checks run.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tarfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/ci'))
import deps  # noqa: E402

NOTICES = ROOT / 'THIRD_PARTY_NOTICES.md'
FORBIDDEN = re.compile(r'(^|/)(liblasgame\.so|lasgame\.so|romfs\.bin|prod\.keys|title\.keys)$|'
                       r'(^|/)(exefs|romfs|generated-aarch64)/|\.(nsp|xci|nca|nso|nro)$', re.I)


def referenced_licenses():
    return sorted(set(re.findall(r'LICENSES/([A-Za-z0-9._-]+\.(?:txt|md|rst))', NOTICES.read_text(encoding='utf-8'))))


def sha256(p):
    return hashlib.sha256(Path(p).read_bytes()).hexdigest()


class Notices(unittest.TestCase):
    def test_every_license_named_in_the_notices_exists(self):
        refs = referenced_licenses()
        self.assertGreater(len(refs), 20)
        missing = [r for r in refs if not (ROOT / 'LICENSES' / r).is_file()]
        self.assertEqual(missing, [], 'THIRD_PARTY_NOTICES.md names license files that are not in LICENSES/')

    def test_every_pinned_dependency_is_in_the_notices(self):
        text = NOTICES.read_text(encoding='utf-8')
        d, _, lics = deps.parse()
        for l in lics:
            self.assertTrue('LICENSES/' + l['file'] in text, 'not in THIRD_PARTY_NOTICES.md: LICENSES/' + l['file'])
        for e in d.values():
            if e['name'].endswith('-license'):
                continue  # (license texts only, nothing of them is built)
            v = e['version'][:7] if len(e['version']) == 40 else e['version']
            self.assertTrue(v in text, f'THIRD_PARTY_NOTICES.md does not name {e["name"]} {v}')

    def test_nothing_says_the_kit_needs_a_third_party_apk(self):
        self.assertNotIn('Still missing', (ROOT / 'LICENSES/SOURCES.md').read_text(encoding='utf-8'))
        self.assertNotIn('upstream.apk', (ROOT / '.github/workflows/apk.yml').read_text(encoding='utf-8'))


@unittest.skipUnless(os.environ.get('LETSGO_RELEASE_STAGING'), 'set LETSGO_RELEASE_STAGING to a staged release')
class Staging(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dir = Path(os.environ['LETSGO_RELEASE_STAGING'])
        apks = sorted(cls.dir.glob('*.apk'))
        tars = sorted(cls.dir.glob('letsgo-*-source.tar.gz'))
        assert len(apks) == 1 and len(tars) == 1, (apks, tars)
        cls.apk, cls.tar = apks[0], tars[0]

    def test_sha256sums_cover_apk_and_source(self):
        sums = dict(reversed(l.split(None, 1)) for l in (self.dir / 'SHA256SUMS').read_text().splitlines() if l.strip())
        sums = {k.strip().lstrip('*'): v for k, v in sums.items()}
        self.assertEqual(sums.get(self.apk.name), sha256(self.apk))
        self.assertEqual(sums.get(self.tar.name), sha256(self.tar))

    def test_apk_is_game_free_and_carries_every_license(self):
        with zipfile.ZipFile(self.apk) as z:
            names = set(z.namelist())
            self.assertEqual([n for n in names if FORBIDDEN.search(n)], [])
            for r in referenced_licenses() + ['THIRD_PARTY_NOTICES.md']:
                self.assertIn('assets/licenses/' + r, names)
            self.assertIn('assets/buildkit/licenses/android-ndk-NOTICE.txt', names)
            cfg = json.loads(z.read('assets/buildkit/config.json'))
        self.assertEqual(cfg.get('kit_origin'), 'source', 'the kit must be built from deps.lock, not copied')
        self.assertEqual(cfg.get('deps_lock_sha256'), sha256(ROOT / 'deps.lock'))
        for lib in ('liblasrecomp.so', 'libSDL3.so', 'libavcodec.so', 'libunicorn.so', 'libzig.so', 'libadrenotools.so'):
            self.assertIn('lib/arm64-v8a/' + lib, names)

    def test_source_tarball_is_the_corresponding_source(self):
        d, _, _ = deps.parse()
        with tarfile.open(self.tar) as t:
            names = set(t.getnames())
            top = self.tar.name[:-len('-source.tar.gz')]
            for f in ('deps.lock', 'SOURCE-README.md', 'tools/ci/build-deps.sh', 'tools/ci/build-buildkit.sh',
                      'build-apk.sh', 'LICENSES/Unicorn-GPL-2.0.txt'):
                self.assertIn(f'{top}/{f}', names)
            for e in d.values():
                if e['source'] == 'yes':
                    m = f'{top}/deps/dl/{deps.filename(e)}'
                    self.assertIn(m, names)
                    self.assertEqual(hashlib.sha256(t.extractfile(m).read()).hexdigest(), e['sha256'], e['name'])
            self.assertEqual([n for n in names if FORBIDDEN.search(n)], [])


if __name__ == '__main__':
    unittest.main()
