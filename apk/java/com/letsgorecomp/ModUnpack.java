package com.letsgorecomp;

import java.io.File;

// Installs a downloaded mod (.zip, .rar, .7z) into the app's own mods folder, the one the runtime reads
// (LARECOMP_MODS_DIR, set by LetsGoActivity; runtime/mods.c). The player's game folder is never written.
// Native side: apk/modunpack/modunpack.c (libmodunpack.so, libarchive). Call it off the UI thread: a 250 MB archive
// takes ~3 s on the Fold4.
final class ModUnpack {
    private ModUnpack() {}

    // What libmodunpack opens: modunpack.c turns on libarchive's zip, rar, rar5 and 7zip readers, nothing else.
    // The file picker, the "unsupported format" message and GameBanana's file check all read these two lists;
    // apk/test/ModFormatsTest.java holds them equal to modunpack.c's readers.
    /** File name endings (lower case) of the archives libmodunpack opens. */
    static final String[] EXTENSIONS = {".zip", ".rar", ".7z"};
    /** Every MIME type phones give those archives. A .rar alone has four: Android's own MimeTypeMap says
     *  application/rar, and a picker filter without it greys the file out (seen on the Fold4, Android 16). */
    static final String[] MIME_TYPES = {
        "application/zip", "application/x-zip-compressed",
        "application/vnd.rar", "application/x-rar-compressed", "application/rar", "multipart/rar",
        "application/x-7z-compressed"};
    /** The type of a file the provider can't name: the picker shows it, and only its extension can admit it. */
    static final String UNKNOWN_TYPE = "application/octet-stream";

    /** The picker's EXTRA_MIME_TYPES: the archive types, plus the unknown type (sorted by accepts() afterwards). */
    static String[] pickerTypes() {
        String[] t = java.util.Arrays.copyOf(MIME_TYPES, MIME_TYPES.length + 1);
        t[MIME_TYPES.length] = UNKNOWN_TYPE;
        return t;
    }

    /** True when the name ends with one of EXTENSIONS (any case). */
    static boolean hasExtension(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(java.util.Locale.ROOT);
        for (String e : EXTENSIONS) if (l.endsWith(e)) return true;
        return false;
    }

    /** A picked document worth unpacking: an archive by its name, or, when the name says nothing, by its type. */
    static boolean accepts(String name, String mime) {
        if (hasExtension(name)) return true;
        if (mime == null) return false;
        String m = mime.toLowerCase(java.util.Locale.ROOT);
        for (String t : MIME_TYPES) if (t.equals(m)) return true;
        return false;
    }

    /** The mods folder: <files dir>/mods (private storage, case-sensitive, ~100x faster than shared storage). */
    static File dir(android.content.Context c) {
        return new File(c.getFilesDir(), "mods");
    }

    /**
     * Unpacks src (a file path) or fd (>= 0: a detached ParcelFileDescriptor, closed by the native side) into
     * dir/name, or "name - variant" folders when the archive holds several variants. Returns "OK files=.. bytes=..
     * code=.. other=.. encrypted=.. mods=<folder>|<folder>..." or "ERR <code> <message> (...)" (codes: modunpack.h).
     * The name to put in larecomp.ini ("mod = <folder>") is one of the folders after mods=.
     */
    static String unpack(String src, int fd, File dir, String name) {
        System.loadLibrary("modunpack");
        return nativeUnpack(src, fd, new File(dir, name).getAbsolutePath());
    }

    private static native String nativeUnpack(String src, int fd, String dst);
}
