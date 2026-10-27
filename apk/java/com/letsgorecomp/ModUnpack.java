package com.letsgorecomp;

import java.io.File;

// Installs a downloaded mod (.zip, .rar, .7z) into the app's own mods folder, the one the runtime reads
// (LARECOMP_MODS_DIR, set by LetsGoActivity; runtime/mods.c). The player's game folder is never written.
// Native side: apk/modunpack/modunpack.c (libmodunpack.so, libarchive). Call it off the UI thread: a 250 MB archive
// takes ~3 s on the Fold4.
final class ModUnpack {
    private ModUnpack() {}

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
