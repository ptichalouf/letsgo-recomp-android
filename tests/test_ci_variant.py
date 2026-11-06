"""Static checks of the side-by-side CI test APK (applicationId com.letsgorecomp.ci).
The real packaging checks live in tests/test_build_apk.py (aapt2) and in apk.yml (aapt2 on the built APKs).
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CiVariant(unittest.TestCase):
    def test_manifest_keeps_the_release_package(self):
        m = (ROOT / 'apk/AndroidManifest.xml').read_text(encoding='utf-8')
        self.assertIn('package="com.letsgorecomp"', m)
        self.assertNotIn('.ci"', m)

    def test_build_script_defaults_to_the_manifest_package(self):
        s = (ROOT / 'build-apk.sh').read_text(encoding='utf-8')
        self.assertIn('APP_ID=${LETSGO_APP_ID:-}', s)
        self.assertIn('--rename-manifest-package', s)
        self.assertNotIn('com.letsgorecomp.ci', s)  # the id is chosen by the caller (CI), never baked in

    def test_ci_builds_the_variant_but_not_in_a_release(self):
        y = (ROOT / '.github/workflows/apk.yml').read_text(encoding='utf-8')
        self.assertIn('LETSGO_APP_ID=com.letsgorecomp.ci', y)
        self.assertIn("LETSGO_LABEL_SUFFIX=' (CI)'", y)
        self.assertIn('inputs.release', y)
        # the variant never lands in the staging directory the release publishes
        self.assertRegex(y, r'LETSGO_APK_DEST="\$TESTVAR/')
        self.assertIn('name: letsgo-test-apk-ci-', y)
        self.assertNotRegex(y, r'name: letsgo-apk-ci')

    def test_release_workflow_only_downloads_release_artifacts(self):
        r = (ROOT / '.github/workflows/release.yml').read_text(encoding='utf-8')
        self.assertIn('pattern: letsgo-apk-*', r)
        self.assertNotIn('com.letsgorecomp.ci', r)

    def test_java_does_not_hardcode_the_application_id(self):
        # Runtime code must use getPackageName(); only the Txt fallback and the class package may name it.
        for f in (ROOT / 'apk/java').rglob('*.java'):
            for n, line in enumerate(f.read_text(encoding='utf-8').splitlines(), 1):
                if re.search(r'"com\.letsgorecomp"', line):
                    self.assertTrue(f.name == 'Txt.java', f'{f}:{n} hard-codes the application id')


if __name__ == '__main__':
    unittest.main()
