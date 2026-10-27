package com.letsgorecomp;

// Resources follow the application's language, including Android 13+ per-app languages.
// values/ remains the Portuguese default; values-en|fr contain the existing translations.
class Txt {
    private static android.content.res.Resources res;
    private static String pkg = "com.letsgorecomp";

    static void init(android.content.Context ctx) {
        try {
            pkg = ctx.getPackageName();
            android.content.res.Configuration c =
                new android.content.res.Configuration(ctx.getResources().getConfiguration());
            java.util.Locale locale = c.getLocales().get(0);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                android.app.LocaleManager manager = ctx.getSystemService(android.app.LocaleManager.class);
                if (manager != null && !manager.getApplicationLocales().isEmpty()) {
                    locale = manager.getApplicationLocales().get(0);
                }
            }
            // Keep just the requested language. Otherwise [pt, fr] can select the explicit
            // French resources before the unqualified Portuguese resources (values/).
            c.setLocale(locale);
            res = ctx.createConfigurationContext(c).getResources();
        } catch (Exception e) { res = null; }
    }

    /** `key' in the phone's language; `pt', the string the code always had, when there is none. */
    static String t(String key, String pt) {
        if (res != null) {
            try {
                int id = res.getIdentifier(key, "string", pkg);
                if (id != 0) { String s = res.getString(id); if (s != null && !s.isEmpty()) return s; }
            } catch (Exception ignored) {}
        }
        return pt;
    }
}
