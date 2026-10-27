package com.letsgorecomp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// The app's mods/ (<filesDir>/mods, LARECOMP_MODS_DIR), as the runtime reads it (src/runtime/mods.c), seen from the launcher. Plain files, no
// Android API, so apk/test/ModStoreTest.java runs it on a PC.
//
//   <data root>/mods/<name>/romfs/...   one mod: LayeredFS replacement files (mods.c find_romfs looks up to 4 levels
//                                       down; what this class installs always has romfs/ right inside <name>/)
//   the ONE active mod: LARECOMP_MOD (LetsGoActivity, from ModsPanel.active), else larecomp.ini's mod. The runtime
//                                       has a single slot: stacking several would need mods.c to search a list.
//
// Installing is ModUnpack's (libmodunpack.so): it writes <filesDir>/mods/<name>/.../romfs only. This class lists,
// names, removes, and reads ModUnpack's answer. The active mod is the launcher's preference (ModsPanel.active).
final class ModStore {
    static final String EEVEE_TITLE = "0100187003A36000", PIKACHU_TITLE = "010003F003A34000";
    static final String SOURCE_FILE = ".letsgo-mod.properties";  // where an installed mod came from (GameBanana id...)

    private ModStore() {}

    static File modsDir(File dataRoot) { return new File(dataRoot, "mods"); }

    /** The folder name for a mod, as mods.c install_thread makes it: letters, digits, space, '-', '.', the rest '_';
     *  60 characters at most, no trailing spaces or dots, never empty, never hidden. */
    static String safeName(String s) {
        StringBuilder b = new StringBuilder();
        if (s != null) {
            for (int i = 0; i < s.length() && b.length() < 60; i++) {
                char c = s.charAt(i);
                boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == ' ' || c == '.';
                b.append(ok ? c : '_');
            }
        }
        String n = b.toString().trim();
        while (n.endsWith(".")) n = n.substring(0, n.length() - 1).trim();
        while (n.startsWith(".")) n = n.substring(1).trim();
        return n.isEmpty() ? "mod" : n;
    }

    /** A name that can only be a folder directly under mods/ (no separators, not hidden, not "." or ".."). */
    static boolean validName(String n) {
        return n != null && !n.isEmpty() && !n.startsWith(".") && n.indexOf('/') < 0 && n.indexOf('\\') < 0 && n.indexOf(0) < 0;
    }

    // ------------------------------------------------------------------ what is installed

    /** Installed mods: folders of mods/ (not links) in which mods.c would find a romfs/ (same depth), sorted by name. */
    static List<String> installed(File dataRoot) {
        List<String> out = new ArrayList<>();
        File[] all = modsDir(dataRoot).listFiles();
        if (all == null) return out;
        for (File f : all) {
            if (f.getName().startsWith(".") || !f.isDirectory() || isLink(f)) continue;
            if (!romfsDirs(f, 4).isEmpty()) out.add(f.getName());
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Every folder named romfs (any case) under dir, up to depth levels down; a romfs/ is not searched inside. */
    static List<File> romfsDirs(File dir, int depth) {
        List<File> out = new ArrayList<>();
        collect(dir, depth, out);
        return out;
    }

    private static void collect(File dir, int depth, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        java.util.Arrays.sort(kids);
        for (File k : kids) {
            if (!k.isDirectory() || isLink(k)) continue;
            if (k.getName().equalsIgnoreCase("romfs")) out.add(k);
            else if (depth > 0) collect(k, depth - 1, out);
        }
    }

    static boolean isLink(File f) {
        try { return java.nio.file.Files.isSymbolicLink(f.toPath()); } catch (RuntimeException e) { return true; }
    }

    // ------------------------------------------------------------------ ModUnpack's answer

    /** "OK files=.. bytes=.. code=.. other=.. encrypted=.. mods=a|b" or "ERR <code> <message>" (ModUnpack.unpack). */
    static final class Unpacked {
        boolean ok;
        int err, code;
        final List<String> mods = new ArrayList<>();
        String message = "";

        static Unpacked parse(String s) {
            Unpacked u = new Unpacked();
            if (s == null) { u.err = 1; return u; }
            if (s.startsWith("OK")) {
                u.ok = true;
                for (String kv : s.substring(2).trim().split(" (?=[a-z]+=)")) {
                    int eq = kv.indexOf('=');
                    if (eq < 0) continue;
                    String k = kv.substring(0, eq), v = kv.substring(eq + 1);
                    if (k.equals("code")) u.code = parseInt(v);
                    if (k.equals("mods")) for (String m : v.split("\\|")) if (validName(m)) u.mods.add(m);
                }
            } else {
                String[] p = s.split(" ", 3);
                u.err = p.length > 1 ? parseInt(p[1]) : 1;
                u.message = p.length > 2 ? p[2] : "";
                int c = s.indexOf("code=");
                if (c >= 0) u.code = parseInt(s.substring(c + 5).split("[^0-9]")[0]);
            }
            return u;
        }

        private static int parseInt(String v) { try { return Integer.parseInt(v.trim()); } catch (RuntimeException e) { return 0; } }
    }

    static String stripExt(String n) {
        if (n == null) return "mod";
        String l = n.toLowerCase(Locale.ROOT);
        for (String e : ModUnpack.EXTENSIONS) if (l.endsWith(e)) return n.substring(0, n.length() - e.length());
        return n;
    }

    /** Bytes under f (links not followed). */
    static long size(File f) {
        if (isLink(f)) return 0;
        if (!f.isDirectory()) return f.length();
        long t = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) t += size(k);
        return t;
    }

    // ------------------------------------------------------------------ removing, origin

    /** Delete mods/<name> (only a folder directly inside mods/, links not followed). */
    static boolean remove(File dataRoot, String name) {
        if (!validName(name)) return false;
        File d = new File(modsDir(dataRoot), name);
        try {
            if (!d.getCanonicalFile().getParentFile().equals(modsDir(dataRoot).getCanonicalFile())) return false;
        } catch (IOException e) { return false; }
        deleteTree(d);
        return !d.exists();
    }

    static java.util.Properties source(File dataRoot, String name) {
        java.util.Properties p = new java.util.Properties();
        File f = new File(new File(modsDir(dataRoot), name), SOURCE_FILE);
        if (f.isFile()) {
            try { InputStream in = new FileInputStream(f); try { p.load(in); } finally { in.close(); } } catch (IOException ignored) {}
        }
        return p;
    }

    static void writeSource(File dataRoot, String name, java.util.Properties p) throws IOException {
        if (!validName(name)) return;
        OutputStream o = new FileOutputStream(new File(new File(modsDir(dataRoot), name), SOURCE_FILE));
        try { p.store(o, "Let's Go Recomp: where this mod came from"); } finally { o.close(); }
    }

    static void deleteTree(File f) {
        if (f.isDirectory() && !isLink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteTree(k);
        }
        f.delete();
    }

    static byte[] readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) b.write(buf, 0, n);
            return b.toByteArray();
        } finally { in.close(); }
    }
}
