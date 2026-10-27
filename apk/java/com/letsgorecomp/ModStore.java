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
//   <data root>/mods/.load-order        the player's order of the installed mods, each on or off (order() below)
//   the active mods, in that order: LARECOMP_MODS="a|b|c" (LetsGoActivity, from activeList), highest priority first.
//                                       mods.c serves a file several active mods have from the first one listed
//                                       (mod_romfs_find); conflicts() finds the same files, to say so on the screen.
//
// Installing is ModUnpack's (libmodunpack.so): it writes <filesDir>/mods/<name>/.../romfs only. This class lists,
// names, removes, orders, and reads ModUnpack's answer.
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

    // ------------------------------------------------------------------ load order and conflicts

    static final String ORDER_FILE = ".load-order";  // in mods/: one "on<TAB>name" / "off<TAB>name" line per mod

    /** One installed mod in the player's order. */
    static final class Entry {
        final String name;
        boolean on;
        Entry(String name, boolean on) { this.name = name; this.on = on; }
        @Override public String toString() { return (on ? "+" : "-") + name; }
    }

    /** A name the order file and LARECOMP_MODS can carry ('|' separates the list, one line per mod). */
    static boolean orderName(String n) {
        return validName(n) && n.indexOf('|') < 0 && n.indexOf('\n') < 0 && n.indexOf('\r') < 0 && n.indexOf('\t') < 0;
    }

    /** The installed mods in the player's order (mods/.load-order). A mod no longer installed is dropped and the
     *  others keep their places relative to each other; an installed mod the file does not know yet goes last, off
     *  (sorted by name among themselves). No file yet: installed() order, legacyActive (the one-mod screen's
     *  choice) on. Nothing is written here: saveOrder does. */
    static List<Entry> order(File dataRoot, String legacyActive) {
        List<String> inst = installed(dataRoot);
        List<Entry> out = new ArrayList<>();
        File f = new File(modsDir(dataRoot), ORDER_FILE);
        if (f.isFile()) {
            try {
                for (String line : new String(readAll(f), "UTF-8").split("\n")) {
                    int tab = line.indexOf('\t');
                    if (tab < 0) continue;
                    String name = line.substring(tab + 1);
                    if (!orderName(name) || !inst.contains(name) || indexOf(out, name) >= 0) continue;
                    out.add(new Entry(name, line.substring(0, tab).equals("on")));
                }
            } catch (IOException ignored) {}
        } else {
            for (String m : inst) if (orderName(m)) out.add(new Entry(m, m.equals(legacyActive)));
        }
        for (String m : inst) if (orderName(m) && indexOf(out, m) < 0) out.add(new Entry(m, false));
        return out;
    }

    static void saveOrder(File dataRoot, List<Entry> l) throws IOException {
        StringBuilder b = new StringBuilder();
        for (Entry e : l) if (orderName(e.name)) b.append(e.on ? "on" : "off").append('\t').append(e.name).append('\n');
        File dir = modsDir(dataRoot), tmp = new File(dir, ORDER_FILE + ".tmp");
        dir.mkdirs();
        OutputStream o = new FileOutputStream(tmp);
        try { o.write(b.toString().getBytes("UTF-8")); } finally { o.close(); }
        if (!tmp.renameTo(new File(dir, ORDER_FILE))) throw new IOException("cannot write " + ORDER_FILE);
    }

    static int indexOf(List<Entry> l, String name) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).name.equals(name)) return i;
        return -1;
    }

    /** Move name one place up (delta -1) or down (+1); false at either end. */
    static boolean move(List<Entry> l, String name, int delta) {
        int i = indexOf(l, name), j = i + delta;
        if (i < 0 || j < 0 || j >= l.size()) return false;
        Collections.swap(l, i, j);
        return true;
    }

    /** A freshly installed mod: on, and last when new (lowest priority: it never silently overrides the mods
     *  already on); one already in the list (installed again) keeps its place. */
    static void addInstalled(List<Entry> l, String name) {
        int i = indexOf(l, name);
        if (i >= 0) l.get(i).on = true;
        else if (orderName(name)) l.add(new Entry(name, true));
    }

    /** The active mods, highest priority first. */
    static List<String> active(List<Entry> l) {
        List<String> out = new ArrayList<>();
        for (Entry e : l) if (e.on) out.add(e.name);
        return out;
    }

    /** What LetsGoActivity hands to the runtime as LARECOMP_MODS ("" = no mod). */
    static String activeList(List<Entry> l) {
        StringBuilder b = new StringBuilder();
        for (String n : active(l)) { if (b.length() > 0) b.append('|'); b.append(n); }
        return b.toString();
    }

    /** One RomFS file two active mods both have: winner's copy is served (it is listed first), loser's is hidden. */
    static final class Conflict {
        final String path, winner, loser;
        Conflict(String path, String winner, String loser) { this.path = path; this.winner = winner; this.loser = loser; }
        @Override public String toString() { return path + ": " + winner + " > " + loser; }
    }

    /** The mod's romfs/ as mods.c find_romfs picks it for what ModUnpack installs (romfs/ right inside), else the
     *  first one found up to 4 levels down; null = none. */
    static File romfsRoot(File modDir) {
        File r = new File(modDir, "romfs");
        if (r.isDirectory() && !isLink(r)) return r;
        List<File> l = romfsDirs(modDir, 4);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Every file two of the active mods (highest priority first) both have, in list order: the same rule as
     *  mods.c mod_romfs_find (the first mod that has a file serves it). */
    static List<Conflict> conflicts(File dataRoot, List<String> active) {
        List<Conflict> out = new ArrayList<>();
        java.util.Map<String, String> owner = new java.util.HashMap<>();
        for (String m : active) {
            File root = romfsRoot(new File(modsDir(dataRoot), m));
            if (root == null) continue;
            List<String> files = new ArrayList<>();
            listFiles(root, "", files);
            for (String p : files) {
                String first = owner.get(p);
                if (first == null) owner.put(p, m);
                else out.add(new Conflict(p, first, m));
            }
        }
        return out;
    }

    private static void listFiles(File dir, String rel, List<String> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        java.util.Arrays.sort(kids);
        for (File k : kids) {
            if (isLink(k)) continue;
            String p = rel.isEmpty() ? k.getName() : rel + "/" + k.getName();
            if (k.isDirectory()) listFiles(k, p, out);
            else if (k.isFile()) out.add(p);
        }
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
