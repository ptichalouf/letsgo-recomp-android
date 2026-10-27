"""Resource-level contract; does not replace device visual acceptance."""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'


def colors(folder):
    return {e.attrib['name']: e.text for e in ET.parse(ROOT / 'apk/res' / folder / 'colors.xml').getroot() if e.tag == 'color'}


def contrast(a, b):
    def luminance(c):
        rgb = [int(c[i:i+2], 16) / 255 for i in (1, 3, 5)]
        rgb = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
        return sum(v * w for v, w in zip(rgb, (.2126, .7152, .0722)))
    x, y = sorted((luminance(a), luminance(b)))
    return (y + .05) / (x + .05)


class ThemeContract(unittest.TestCase):
    def test_material_theme_is_launcher_only(self):
        manifest = ET.parse(ROOT / 'apk/AndroidManifest.xml').getroot()
        app = manifest.find('application')
        self.assertEqual('@android:style/Theme.NoTitleBar.Fullscreen', app.get(ANDROID + 'theme'))
        launcher = next(a for a in app.findall('activity') if a.get(ANDROID + 'name').endswith('.LetsGoLauncherActivity'))
        self.assertEqual('@style/Launcher', launcher.get(ANDROID + 'theme'))
        for folder in ('values', 'values-notnight'):
            styles = [e for file in (ROOT / 'apk/res' / folder).glob('*.xml') for e in ET.parse(file).getroot() if e.tag == 'style' and e.get('name') == 'Launcher']
            self.assertEqual(1, len(styles))
            parent = styles[0].get('parent', '')
            if parent == 'Launcher.Base':
                parent = next(e.get('parent', '') for e in ET.parse(ROOT / 'apk/res/values/styles.xml').getroot() if e.get('name') == parent)
            self.assertIn('Theme.Material', parent)
            self.assertIn('NoActionBar', parent)

    def test_palettes_and_contrasts(self):
        for folder in ('values', 'values-notnight'):
            p = colors(folder)
            for bg in ('bg', 'surface', 'surface2'):
                for fg in ('text', 'text2', 'text3', 'primary_text', 'error', 'warn', 'ok'):
                    with self.subTest(folder=folder, fg=fg, bg=bg):
                        self.assertGreaterEqual(contrast(p['lg_' + fg], p['lg_' + bg]), 4.5)
                self.assertGreaterEqual(contrast(p['lg_ctl'], p['lg_' + bg]), 3)
            self.assertGreaterEqual(contrast(p['lg_on_primary'], p['lg_primary']), 4.5)
            self.assertEqual(set(colors('values')), set(p))

    def test_java_has_no_palette_duplicate_or_animation(self):
        java = (ROOT / 'apk/java/com/letsgorecomp/Theme.java').read_text()
        self.assertNotRegex(java, r'0x[0-9a-fA-F]{6,}|Color\.rgb|ObjectAnimator|ValueAnimator')
        self.assertIn('TOUCH_MIN = 48', java)
        self.assertIn('getColor', java)

    def test_views_consume_theme_without_inline_palette(self):
        for name in ('LetsGoLauncherActivity', 'ModsPanel'):
            java = (ROOT / 'apk/java/com/letsgorecomp' / (name + '.java')).read_text()
            self.assertNotRegex(java, r'Color\.rgb|0x[0-9a-fA-F]{6,}|Color\.WHITE')
            self.assertIn('Theme.', java)
        launcher = (ROOT / 'apk/java/com/letsgorecomp/LetsGoLauncherActivity.java').read_text()
        for call in ('Theme.playButton', 'Theme.checkBox', 'Theme.selector', 'Theme.railButton', 'Theme.sectionHeader'):
            self.assertIn(call, launcher)
        mods = (ROOT / 'apk/java/com/letsgorecomp/ModsPanel.java').read_text()
        for call in ('Theme.tile', 'Theme.statusBar', 'Theme.toggle'):
            self.assertIn(call, mods)
        self.assertIn('screenWidthDp < 600', mods)

    def test_tokens_used_by_views_exist_in_both_palettes(self):
        for name in ("Theme", "LetsGoLauncherActivity", "ModsPanel"):
            java = (ROOT / "apk/java/com/letsgorecomp" / (name + ".java")).read_text()
            for key in re.findall(r'(?:Theme\.)?color\([^,]+, "([a-z0-9_]+)"\)', java):
                for folder in ("values", "values-notnight"):
                    self.assertIn("lg_" + key, colors(folder))

    def test_new_mod_labels_exist_in_all_languages(self):
        for folder in ('values', 'values-en', 'values-fr'):
            names = {e.get('name') for e in ET.parse(ROOT / 'apk/res' / folder / 'strings.xml').getroot()}
            # Version and skipped-code badges are not rendered: ModStore does not persist them (Michel 27/10, t_06785bed).
            for key in ('mods_summary', 'mods_failed', 'mods_dismiss'):
                self.assertIn(key, names)

    def test_five_keys_in_three_languages(self):
        for folder in ('values', 'values-en', 'values-fr'):
            # strings.xml plus strings_theme.xml: aapt merges every values file, a key may live in one only.
            names = [e.get('name') for f in sorted((ROOT / 'apk/res' / folder).glob('strings*.xml'))
                     for e in ET.parse(f).getroot() if e.tag == 'string']
            self.assertEqual(len(names), len(set(names)))
            for key in ('sec_image', 'sec_game', 'sec_controls', 'layout_zones', 'capture'):
                self.assertIn(key, names)


if __name__ == '__main__':
    unittest.main()
