package com.letsgorecomp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The mod file picker admits exactly what libmodunpack opens, no more, no less. The readers are read from
// apk/modunpack/modunpack.c (archive_read_support_*), so turning a reader on or off there fails this test until
// ModUnpack.EXTENSIONS and ModUnpack.MIME_TYPES follow. Run from the repository root:
//   AJ=$ANDROID_SDK_ROOT/platforms/android-34/android.jar
//   javac -encoding UTF-8 -cp $AJ -d /tmp/mft apk/java/com/letsgorecomp/{ModStore,GameBanana,ModsPanel,ModUnpack,Txt}.java apk/test/ModFormatsTest.java
//   java -cp /tmp/mft:$AJ com.letsgorecomp.ModFormatsTest
public class ModFormatsTest {
    static int fails, checks;

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what);
    }

    static String src(String p) throws Exception { return new String(Files.readAllBytes(Path.of(p)), "UTF-8"); }

    static Set<String> set(String... a) { return new TreeSet<>(Arrays.asList(a)); }

    public static void main(String[] args) throws Exception {
        // 1. What modunpack.c opens: libarchive reader -> file extension, and the MIME types that extension is given.
        // The MIME column is the inventory of names seen for each format (Android MimeTypeMap, IANA, browsers,
        // Windows); a format here and not in modunpack.c, or the reverse, fails below.
        Map<String, String> extOf = new TreeMap<>();
        extOf.put("zip", ".zip");
        extOf.put("rar", ".rar");
        extOf.put("rar5", ".rar");
        extOf.put("7zip", ".7z");
        Map<String, Set<String>> mimesOf = new TreeMap<>();
        mimesOf.put(".zip", set("application/zip", "application/x-zip-compressed"));
        mimesOf.put(".rar", set("application/vnd.rar", "application/x-rar-compressed", "application/rar", "multipart/rar"));
        mimesOf.put(".7z", set("application/x-7z-compressed"));

        String c = src("apk/modunpack/modunpack.c");
        Set<String> readers = new TreeSet<>();
        Matcher m = Pattern.compile("archive_read_support_(\\w+)\\s*\\(").matcher(c);
        while (m.find()) readers.add(m.group(1));
        System.out.println("modunpack.c readers: " + readers);
        check("modunpack.c turns on readers", !readers.isEmpty());
        Set<String> natExt = new TreeSet<>();
        for (String r : readers) {
            // filter_* (gzip, bzip2...) or format_all would let more files through than this table knows
            check("reader " + r + " is a known format", r.startsWith("format_") && extOf.containsKey(r.substring(7)));
            if (r.startsWith("format_") && extOf.containsKey(r.substring(7))) natExt.add(extOf.get(r.substring(7)));
        }
        Set<String> natMime = new TreeSet<>();
        for (String e : natExt) natMime.addAll(mimesOf.get(e));

        // 2. The Java lists equal the native ones, exactly.
        Set<String> exts = set(ModUnpack.EXTENSIONS), mimes = set(ModUnpack.MIME_TYPES);
        check("EXTENSIONS " + exts + " == modunpack.c " + natExt, exts.equals(natExt));
        check("no duplicate extension", exts.size() == ModUnpack.EXTENSIONS.length);
        check("MIME_TYPES " + mimes + " == modunpack.c " + natMime, mimes.equals(natMime));
        check("no duplicate type", mimes.size() == ModUnpack.MIME_TYPES.length);
        for (String e : ModUnpack.EXTENSIONS) check("extension " + e + " is lower case with its dot", e.startsWith(".") && e.equals(e.toLowerCase()));

        // 3. The picker's filter: every archive type + the unknown type, nothing else (no */*, no other family).
        Set<String> picker = new HashSet<>(Arrays.asList(ModUnpack.pickerTypes()));
        Set<String> want = new HashSet<>(natMime);
        want.add("application/octet-stream");
        check("picker types == archive types + application/octet-stream", picker.equals(want) && picker.size() == ModUnpack.pickerTypes().length);
        for (String t : new String[]{"application/rar", "application/x-rar-compressed", "multipart/rar", "application/vnd.rar"})
            check("picker lets a .rar typed " + t + " through", picker.contains(t));
        check("picker lets a .7z through", picker.contains("application/x-7z-compressed"));

        // 4. After the pick: admitted by extension when the type says nothing, never a file ModUnpack can't open.
        Object[][] cases = {
            {"pokepika.rar", "application/rar", true},             // the Fold4's own type for a .rar
            {"Pokepika.RAR", null, true},
            {"mod.part1.rar", "multipart/rar", true},
            {"mod.rar", "application/octet-stream", true},         // unknown type, admitted by the extension
            {"mod.7z", "application/octet-stream", true},
            {"mod.zip", "application/octet-stream", true},
            {"mod.7z", "application/x-7z-compressed", true},
            {"Mod v1.2", "application/x-7z-compressed", true},       // no usable extension: the type admits it
            {"Mod v1.2", "application/octet-stream", false},
            {"notes.txt", "application/octet-stream", false},
            {"mod.tar.gz", "application/gzip", false},             // libarchive could, modunpack.c doesn't
            {"mod.tar", "application/x-tar", false},
            {"photo.jpg", "image/jpeg", false},
            {"game.apk", "application/vnd.android.package-archive", false},
            {"mod.rar.part", "application/octet-stream", false},
            {null, null, false},
        };
        for (Object[] k : cases)
            check("accepts(" + k[0] + ", " + k[1] + ") == " + k[2], ModUnpack.accepts((String) k[0], (String) k[1]) == (Boolean) k[2]);
        for (String e : ModUnpack.EXTENSIONS) check("accepts any *" + e + " of unknown type", ModUnpack.accepts("x" + e, "application/octet-stream"));
        for (String t : ModUnpack.MIME_TYPES) check("accepts an extensionless file typed " + t, ModUnpack.accepts("x", t));

        // 5. The screen uses those lists, not lists of its own.
        String panel = src("apk/java/com/letsgorecomp/ModsPanel.java");
        check("ModsPanel's picker filter is ModUnpack.pickerTypes()", panel.contains("Intent.EXTRA_MIME_TYPES, ModUnpack.pickerTypes())"));
        check("ModsPanel checks the picked file with ModUnpack.accepts before unpacking",
            panel.contains("if (ModUnpack.accepts(name, type)) install("));
        for (String f : new String[]{"ModsPanel", "ModStore", "GameBanana"}) {
            String s = src("apk/java/com/letsgorecomp/" + f + ".java");
            check(f + " has no extension list of its own", !s.contains("\".rar\"") && !s.contains("\"rar\"") && !s.contains("\".7z\""));
        }
        check("GameBanana.fileProblem: .rar ok", !"format".equals(GameBanana.fileProblem("a.rar", 1, "clean", "ok", false)));
        check("GameBanana.fileProblem: .tar.gz refused", "format".equals(GameBanana.fileProblem("a.tar.gz", 1, "clean", "ok", false)));
        check("ModStore.stripExt follows EXTENSIONS", ModStore.stripExt("Pokepika.7z").equals("Pokepika") && ModStore.stripExt("a.tar.gz").equals("a.tar.gz"));

        System.out.println(checks - fails + "/" + checks + (fails == 0 ? " OK" : " FAILED"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
