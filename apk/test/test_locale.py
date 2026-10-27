"""Compile the real Txt against narrow Android fakes: application locale wins.
The device recipe additionally checks Android's actual resource resolution.
"""
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


class LauncherLocale(unittest.TestCase):
    def test_application_locale_over_resource_fallback(self):
        sources = {
            'android/os/Build.java': '''package android.os;
public class Build { public static class VERSION { public static int SDK_INT = 34; } }''',
            'android/os/LocaleList.java': '''package android.os;
public class LocaleList {
 private final java.util.Locale[] list;
 public LocaleList(java.util.Locale... list) { this.list = list; }
 public boolean isEmpty() { return list.length == 0; }
 public java.util.Locale get(int i) { return list[i]; }
}''',
            'android/app/LocaleManager.java': '''package android.app;
public class LocaleManager {
 public android.os.LocaleList locales = new android.os.LocaleList();
 public android.os.LocaleList getApplicationLocales() { return locales; }
}''',
            'android/content/res/Configuration.java': '''package android.content.res;
public class Configuration {
 public java.util.Locale locale = java.util.Locale.FRENCH;
 public Configuration() {}
 public Configuration(Configuration c) { locale = c.locale; }
 public void setLocale(java.util.Locale l) { locale = l; }
 public android.os.LocaleList getLocales() { return new android.os.LocaleList(locale); }
}''',
            'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
 public Configuration config = new Configuration();
 public Configuration getConfiguration() { return config; }
 public int getIdentifier(String k, String type, String pkg) { return k.equals("play") ? 1 : 0; }
 public String getString(int id) {
  String l = config.locale.getLanguage();
  return l.equals("fr") ? "Jouer" : l.equals("en") ? "Play" : "Jogar";
 }
}''',
            'android/content/Context.java': '''package android.content;
public class Context {
 public android.content.res.Resources res = new android.content.res.Resources();
 public android.app.LocaleManager manager = new android.app.LocaleManager();
 public String getPackageName() { return "com.letsgorecomp.uitest"; }
 public android.content.res.Resources getResources() { return res; }
 public Context getApplicationContext() { return this; }
 public <T> T getSystemService(Class<T> type) { return type.cast(manager); }
 public Context createConfigurationContext(android.content.res.Configuration c) {
  Context result = new Context(); result.res.config = c; return result;
 }
}''',
            'com/letsgorecomp/TxtLocaleTest.java': '''package com.letsgorecomp;
public class TxtLocaleTest {
 static void check(String actual, String expected) {
  if (!actual.equals(expected)) throw new AssertionError(actual + " != " + expected);
 }
 public static void main(String[] args) {
  android.content.Context c = new android.content.Context();
  // Activity resources may already resolve to the system's FR fallback without values-pt.
  for (String[] pair : new String[][] {{"pt", "Jogar"}, {"pt-BR", "Jogar"}, {"en", "Play"}, {"fr", "Jouer"}}) {
   c.manager.locales = new android.os.LocaleList(java.util.Locale.forLanguageTag(pair[0]));
   Txt.init(c); check(Txt.t("play", "fallback"), pair[1]);
  }
  c.manager.locales = new android.os.LocaleList();
  Txt.init(c); check(Txt.t("play", "fallback"), "Jouer");
  c.manager.locales = new android.os.LocaleList(java.util.Locale.ENGLISH);
  android.os.Build.VERSION.SDK_INT = 28;
  Txt.init(c); check(Txt.t("play", "fallback"), "Jouer");
  check(Txt.t("missing", "Fallback PT"), "Fallback PT");
  System.out.println("LOCALE_OK 7 checks (pt, pt-BR, en, fr, system, API28, missing key)");
 }
}''',
        }
        with tempfile.TemporaryDirectory() as folder:
            tmp = pathlib.Path(folder)
            for name, text in sources.items():
                p = tmp / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text)
            real = ROOT / 'apk/java/com/letsgorecomp/Txt.java'
            subprocess.run(['javac', '--release', '8', '-d', str(tmp), str(real),
                            *map(str, tmp.rglob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(tmp), 'com.letsgorecomp.TxtLocaleTest'], check=True)


if __name__ == '__main__':
    unittest.main()
