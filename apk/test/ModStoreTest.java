package com.letsgorecomp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// The mod screen's pure parts on a PC, no phone, no game files (fake folders in a temp dir):
// ModStore (names, what is installed, removal, origin, ModUnpack's answer, the load order and the conflicts it shows),
// GameBanana.fileProblem, the amber button's default, and source checks (the game folder is never written, the active
// mods reach the runtime in order through LARECOMP_MODS). Run from the repository root:
//   AJ=$ANDROID_SDK_ROOT/platforms/android-34/android.jar
//   javac -encoding UTF-8 -cp $AJ -d /tmp/mst apk/java/com/letsgorecomp/{ModStore,GameBanana,ModsPanel,ModUnpack,Txt}.java apk/test/ModStoreTest.java
//   java -cp /tmp/mst:$AJ com.letsgorecomp.ModStoreTest
// Unpacking itself (libmodunpack.so) is tested natively: apk/modunpack/modunpack.c's test tool.
public class ModStoreTest {
    static int fails, checks;

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what);
    }

    static File tmp() {
        File t = new File(System.getProperty("java.io.tmpdir"), "mst-" + System.nanoTime());
        t.mkdirs();
        return t;
    }

    static void write(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(s.getBytes("UTF-8"));
        o.close();
    }

    static String src(String p) throws IOException { return new String(Files.readAllBytes(Path.of(p)), "UTF-8"); }

    static String names(List<ModStore.Entry> l) { return l.toString(); }

    /** Fills a mod folder the way ModUnpack leaves it: mods/<name>/romfs/<files>. */
    static void mod(File root, String name, String... files) throws IOException {
        for (String f : files) write(new File(ModStore.modsDir(root), name + "/romfs/" + f), name + ":" + f);
    }

    /** The French string res/values-fr gives `key', as Android reads it (quotes off, \\' -> '). */
    static String fr(String key) throws IOException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<string name=\"" + key + "\">\"(.*)\"</string>")
            .matcher(src("apk/res/values-fr/strings.xml"));
        return m.find() ? m.group(1).replace("\\'", "'") : null;
    }

    // ---- several mods on, in the player's order (mods/.load-order), and the conflicts the screen shows
    static void loadOrder() throws Exception {
        File root = tmp();
        String common = "bin/message/English/common/monsname.dat";
        mod(root, "Mod A", common, "bin/only_a.dat");
        mod(root, "Mod B", common, "bin/only_b1.dat", "bin/only_b2.dat");
        mod(root, "Mod C", "bin/only_c.dat");

        // no order file yet: the one-mod screen's choice seeds it
        List<ModStore.Entry> o = ModStore.order(root, "Mod B");
        check("order: first run, installed order, the old active mod on", names(o).equals("[-Mod A, +Mod B, -Mod C]"));
        check("order: nothing written until saved", !new File(ModStore.modsDir(root), ModStore.ORDER_FILE).exists());

        // turn A on, move it below B, save: what the runtime gets is B first
        o.get(ModStore.indexOf(o, "Mod A")).on = true;
        check("move: down", ModStore.move(o, "Mod A", 1) && names(o).equals("[+Mod B, +Mod A, -Mod C]"));
        check("move: past either end refused", !ModStore.move(o, "Mod B", -1) && !ModStore.move(o, "Mod C", 1) && !ModStore.move(o, "None", 1));
        ModStore.saveOrder(root, o);
        check("LARECOMP_MODS: active ones, highest priority first", ModStore.activeList(o).equals("Mod B|Mod A"));
        check("order survives closing the app (read back from the file)", names(ModStore.order(root, "")).equals("[+Mod B, +Mod A, -Mod C]"));
        check("the order file is not a mod", !ModStore.installed(root).contains(ModStore.ORDER_FILE));

        // two overlapping mods on: the screen's conflict, in both orders
        List<ModStore.Conflict> cs = ModStore.conflicts(root, ModStore.active(o));
        check("conflict: the common file, B (higher) over A", cs.size() == 1 && cs.get(0).path.equals(common)
            && cs.get(0).winner.equals("Mod B") && cs.get(0).loser.equals("Mod A"));
        String fmt = fr("mods_conflict");
        String msg = fmt == null ? "" : String.format(fmt, cs.get(0).path, cs.get(0).winner, cs.get(0).loser);
        System.out.println("     screen: " + msg);
        check("conflict: the screen names the file and both mods",
            msg.equals(common + " : dans Mod B et dans Mod A. C'est Mod B qui l'emporte (plus haut)."));
        ModStore.move(o, "Mod A", -1);
        cs = ModStore.conflicts(root, ModStore.active(o));
        check("conflict: other order, A over B", cs.size() == 1 && cs.get(0).winner.equals("Mod A") && cs.get(0).loser.equals("Mod B"));
        check("no conflict with a mod turned off", ModStore.conflicts(root, java.util.Arrays.asList("Mod A", "Mod C")).isEmpty());
        ModStore.move(o, "Mod A", 1);
        ModStore.saveOrder(root, o);

        // removing a mod: the others keep their places relative to each other
        o.get(ModStore.indexOf(o, "Mod C")).on = true;
        ModStore.move(o, "Mod C", -1);
        ModStore.saveOrder(root, o);                                     // B, C, A
        check("before removal", names(ModStore.order(root, "")).equals("[+Mod B, +Mod C, +Mod A]"));
        ModStore.remove(root, "Mod C");
        o = ModStore.order(root, "");
        check("removal: B still above A, nothing else moved", names(o).equals("[+Mod B, +Mod A]"));
        ModStore.saveOrder(root, o);
        // installed again: last (it never silently overrides the mods already on), and on
        mod(root, "Mod C", "bin/only_c.dat");
        o = ModStore.order(root, "");
        ModStore.addInstalled(o, "Mod C");
        check("reinstall: goes last, on; B and A unchanged", names(o).equals("[+Mod B, +Mod A, +Mod C]"));
        ModStore.addInstalled(o, "Mod B");
        check("installing a mod already listed keeps its place", names(o).equals("[+Mod B, +Mod A, +Mod C]"));

        // a damaged / hand-edited file never breaks the list
        write(new File(ModStore.modsDir(root), ModStore.ORDER_FILE), "garbage\non\tMod A\non\t../evil\noff\tMod A\non\tGone\n");
        check("damaged file: junk, unknown, repeated and ../ names dropped, the rest appended off",
            names(ModStore.order(root, "")).equals("[+Mod A, -Mod B, -Mod C]"));
        check("names with '|' can't go into LARECOMP_MODS", !ModStore.orderName("a|b") && !ModStore.orderName("a\tb") && ModStore.orderName("Mod A"));
        ModStore.deleteTree(root);
    }

    public static void main(String[] args) throws Exception {
        // ---- names (same rule as mods.c install_thread)
        check("safeName keeps letters/digits/space/-/.", ModStore.safeName("Xbox Buttons-v1.0").equals("Xbox Buttons-v1.0"));
        check("safeName replaces the rest", ModStore.safeName("Let's Go: Peerless/Eevee").equals("Let_s Go_ Peerless_Eevee"));
        check("safeName never hidden", ModStore.safeName("..hidden").equals("hidden"));
        check("safeName never empty", ModStore.safeName("").equals("mod") && ModStore.safeName(null).equals("mod"));
        check("safeName 60 chars", ModStore.safeName(new String(new char[100]).replace('\0', 'a')).length() == 60);
        check("validName refuses .., /, \\, hidden, empty", !ModStore.validName("..") && !ModStore.validName("a/b")
            && !ModStore.validName("a\\b") && !ModStore.validName(".install-1") && !ModStore.validName("") && !ModStore.validName(null));
        check("validName accepts a plain name", ModStore.validName("Pink Eevee") && ModStore.validName("Rival Blue - Variant A"));
        check("stripExt", ModStore.stripExt("pink_eevee.rar").equals("pink_eevee") && ModStore.stripExt("a.7Z").equals("a")
            && ModStore.stripExt("a.zip").equals("a") && ModStore.stripExt("readme.txt").equals("readme.txt") && ModStore.stripExt(null).equals("mod"));

        // ---- what is installed (the same depth as mods.c find_romfs: 4 levels)
        File root = tmp(), mods = ModStore.modsDir(root);
        write(new File(mods, "Pink Eevee/romfs/bin/a.bin"), "x");
        write(new File(mods, "Agumon/atmosphere/contents/0100187003A36000/romfs/bin/b.bin"), "yy");
        write(new File(mods, "Upper/RomFS/bin/c.bin"), "z");                      // (any case, like the launcher's scan)
        write(new File(mods, "Too Deep/a/b/c/d/e/romfs/bin/x.bin"), "x");         // 5 levels down: mods.c would not see it
        write(new File(mods, "No Romfs/readme.txt"), "x");
        write(new File(mods, ".install-3/romfs/bin/a.bin"), "x");                 // hidden work folder
        write(new File(mods, "loose.bin"), "x");
        List<String> inst = ModStore.installed(root);
        check("installed: romfs at depth 0..4, any case", inst.contains("Pink Eevee") && inst.contains("Agumon") && inst.contains("Upper"));
        check("installed: too deep, no romfs, hidden, files left out", !inst.contains("Too Deep") && !inst.contains("No Romfs")
            && !inst.contains(".install-3") && !inst.contains("loose.bin"));
        check("installed: sorted, case-insensitive", inst.equals(java.util.Arrays.asList("Agumon", "Pink Eevee", "Upper")));
        check("installed: no mods/ folder = empty list", ModStore.installed(tmp()).isEmpty());
        check("size counts bytes under a mod", ModStore.size(new File(mods, "Agumon")) == 2);

        // ---- origin (.letsgo-mod.properties)
        java.util.Properties p = new java.util.Properties();
        p.setProperty("gamebanana.mod", "543014");
        p.setProperty("author", "Someone");
        ModStore.writeSource(root, "Pink Eevee", p);
        check("source round trip", "543014".equals(ModStore.source(root, "Pink Eevee").getProperty("gamebanana.mod"))
            && "Someone".equals(ModStore.source(root, "Pink Eevee").getProperty("author")));
        check("source of a mod without one is empty", ModStore.source(root, "Upper").isEmpty());
        ModStore.writeSource(root, "../evil", p);
        check("writeSource refuses a name outside mods/", !new File(root, "evil").exists());

        // ---- removal: only a folder directly inside mods/, links not followed
        File outside = new File(root, "keep/romfs/bin/k.bin");
        write(outside, "k");
        check("remove refuses ..", !ModStore.remove(root, "..") && !ModStore.remove(root, "../keep") && outside.isFile());
        check("remove refuses a hidden / empty name", !ModStore.remove(root, ".install-3") && !ModStore.remove(root, ""));
        boolean linked = false;
        try {
            Files.createSymbolicLink(new File(mods, "Linked").toPath(), new File(root, "keep").toPath());
            linked = true;
        } catch (Exception e) { System.out.println("     (no symlinks here: link case skipped)"); }
        if (linked) {
            check("a linked folder is not listed as a mod", !ModStore.installed(root).contains("Linked"));
            check("remove refuses a link and never touches its target", !ModStore.remove(root, "Linked") && outside.isFile());
            new File(mods, "Linked").delete();
        }
        check("remove deletes the mod", ModStore.remove(root, "Pink Eevee") && !new File(mods, "Pink Eevee").exists());
        check("remove keeps the others", new File(mods, "Agumon").isDirectory() && ModStore.installed(root).contains("Upper"));

        // ---- ModUnpack's answer (modunpack.h codes)
        ModStore.Unpacked u = ModStore.Unpacked.parse("OK files=192 bytes=1234 code=0 other=3 encrypted=0 mods=Pokepika");
        check("parse OK: one mod", u.ok && u.mods.equals(java.util.Collections.singletonList("Pokepika")) && u.code == 0);
        u = ModStore.Unpacked.parse("OK files=10 bytes=9 code=1 other=0 encrypted=0 mods=Rival Blue - A|Rival Blue - B");
        check("parse OK: variants with spaces, code part counted", u.ok && u.mods.size() == 2
            && u.mods.get(1).equals("Rival Blue - B") && u.code == 1);
        u = ModStore.Unpacked.parse("OK files=1 bytes=1 code=0 other=0 encrypted=0 mods=../evil|.hidden|good");
        check("parse OK: unsafe names dropped", u.mods.equals(java.util.Collections.singletonList("good")));
        u = ModStore.Unpacked.parse("ERR 2 no romfs/ folder (code=4 other=1)");
        check("parse ERR: code-only mod", !u.ok && u.err == 2 && u.code == 4 && u.message.startsWith("no romfs"));
        u = ModStore.Unpacked.parse("ERR 4 cannot write");
        check("parse ERR: write", !u.ok && u.err == 4 && u.code == 0);
        check("parse null = archive error", !ModStore.Unpacked.parse(null).ok && ModStore.Unpacked.parse(null).err == 1);

        // ---- GameBanana: which files the CATALOGUE slot may install in the app
        check("file ok", GameBanana.fileProblem("a.zip", 100, "clean", "ok", false) == null
            && GameBanana.fileProblem("A.RAR", 100, "clean", "", false) == null && GameBanana.fileProblem("a.7z", 100, "clean", null, false) == null);
        check("file: other format", "format".equals(GameBanana.fileProblem("a.exe", 100, "clean", "ok", false)));
        check("file: archived", "archived".equals(GameBanana.fileProblem("a.zip", 100, "clean", "ok", true)));
        check("file: antivirus not clean", "antivirus".equals(GameBanana.fileProblem("a.zip", 100, "infected", "ok", false))
            && "antivirus".equals(GameBanana.fileProblem("a.zip", 100, null, "ok", false)));
        check("file: analysis not ok", "analysis".equals(GameBanana.fileProblem("a.zip", 100, "clean", "warn", false)));
        check("file: size 0 or too large", "size".equals(GameBanana.fileProblem("a.zip", 0, "clean", "ok", false))
            && "size".equals(GameBanana.fileProblem("a.zip", GameBanana.MAX_FILE + 1, "clean", "ok", false)));
        check("profile url on gamebanana.com", GameBanana.profileUrl(543014).equals("https://gamebanana.com/mods/543014"));

        // ---- the amber button: nothing decided, FILE unless debug.letsgo.mods_primary says otherwise
        check("slot default is FILE (no system property on a PC)", ModsPanel.primaryFromProperty() == ModsPanel.Primary.FILE);
        check("three slots", ModsPanel.Primary.values().length == 3);

        // ---- v2 rules, read in the sources
        String panel = src("apk/java/com/letsgorecomp/ModsPanel.java"), store = src("apk/java/com/letsgorecomp/ModStore.java");
        String act = src("apk/java/com/letsgorecomp/LetsGoActivity.java"), modsc = src("src/runtime/mods.c");
        check("panel never writes larecomp.ini", !panel.contains("larecomp.ini") && !panel.contains("Settings.write"));
        check("panel/store never use the game folder", !panel.contains("gameDir(") && !store.contains("gameDir("));
        check("panel installs into the app's mods dir", panel.contains("ModUnpack.dir(c)") && panel.contains("ModUnpack.unpack("));
        check("activity hands the ordered active list over", act.contains("ModsPanel.activeList(this)")
            && act.contains("setenv(\"LARECOMP_MODS\", mods") && act.contains("setenv(\"LARECOMP_MODS_DIR\""));
        check("runtime reads LARECOMP_MODS, then LARECOMP_MOD, then larecomp.ini", modsc.indexOf("getenv(\"LARECOMP_MODS\")") >= 0
            && modsc.indexOf("getenv(\"LARECOMP_MODS\")") < modsc.indexOf("getenv(\"LARECOMP_MOD\")") && modsc.contains("g_settings.mod"));
        check("runtime: the first active mod that has a file serves it (no readdir order)", modsc.contains("int mod_romfs_find(")
            && modsc.contains("if (won < 0) {"));
        check("no second unpacker (libmodarc) left", !new File("apk/java/com/letsgorecomp/ModArchive.java").exists()
            && !src("build-apk.sh").contains("modarc"));

        ModStore.deleteTree(root);
        loadOrder();
        System.out.println(checks - fails + "/" + checks + " checks passed");
        if (fails > 0) System.exit(1);
    }
}
