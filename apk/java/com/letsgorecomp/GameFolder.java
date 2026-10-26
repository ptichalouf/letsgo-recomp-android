package com.letsgorecomp;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

// The game folder the player brings, already extracted, checked with plain file reads (no Android API, so the
// checks also run on a PC: see apk/test/GameFolderTest.java). The layout is the runtime's (main.c boot(),
// paths.c, gamesetup.c find_game):
//   <folder>/extracted/exefs/main + main.npdm
//   <folder>/extracted/romfs.bin             (a RomFS image)
//   or <folder>/extracted/romfs/bin/message  (the RomFS folder; the runtime builds romfs.bin from it on first boot)
// <folder> is also the runtime's data root (LARECOMP_DATA_ROOT): it must be writable (saves, larecomp.ini, cache).
final class GameFolder {
    enum State {
        NOT_SET,          // no folder chosen
        MISSING,          // the chosen folder is gone (moved, renamed, card removed)
        UNREADABLE,       // it exists but can't be listed (no "all files" access)
        NO_EXTRACTED,     // no extracted/ in it
        LOOSE,            // exefs/ (or romfs) right in the chosen folder, not in extracted/
        NO_EXEFS,         // extracted/exefs/main or main.npdm missing, or main is not an NSO
        UNKNOWN_GAME,     // exefs is not Let's Go Eevee / Pikachu
        IMPORT_PARTIAL,   // an interrupted in-app import left its work files (.import/, no .import_ok)
        NO_ROMFS,         // neither romfs.bin nor romfs/bin/message
        ROMFS_DIR_INCOMPLETE, // romfs/ without some of the game's top folders (a copy that stopped midway)
        ROMFS_BIN_BROKEN, // romfs.bin shorter than its own tables say: an interrupted first-boot build or copy
        READ_ONLY,        // the folder can't be written (the runtime refuses to start)
        READY,            // exefs + a complete romfs.bin
        READY_FIRST_RUN   // exefs + romfs/: the first boot builds romfs.bin (several GB, a few minutes)
    }

    static final long TITLE_EEVEE = 0x0100187003A36000L, TITLE_PIKACHU = 0x010003F003A34000L;

    static final class Check {
        State state = State.NOT_SET;
        File root;          // the data root (the folder that holds extracted/)
        String game;        // "Let's Go, Eevee! 1.0.0"..., when exefs was read
        boolean hasRomfsDir;       // a romfs/ folder with every top folder the game needs (it can build romfs.bin)
        String romfsMissing;       // the first one missing, when romfs/ is there but not whole
        long romfsBinBytes = -1, romfsBinNeeded = -1;

        boolean playable() { return state == State.READY || state == State.READY_FIRST_RUN; }
    }

    /** The data root for a folder the player picked: the folder itself, or - when the player picked extracted/
     *  (or exefs/, romfs/ inside it) - the folder above, which is what the runtime wants. */
    static File dataRoot(File picked) {
        if (picked == null) return null;
        File f = picked;
        String n = f.getName();
        if ((n.equals("exefs") || n.equals("romfs")) && f.getParentFile() != null
                && f.getParentFile().getName().equals("extracted")) f = f.getParentFile();
        if (f.getName().equals("extracted") && new File(f, "exefs").isDirectory() && f.getParentFile() != null)
            f = f.getParentFile();
        return f;
    }

    static Check inspect(File root) {
        Check c = new Check();
        c.root = root;
        if (root == null) { c.state = State.NOT_SET; return c; }
        if (!root.exists()) { c.state = State.MISSING; return c; }
        if (!root.isDirectory() || root.list() == null) { c.state = State.UNREADABLE; return c; }
        File ex = new File(root, "extracted");
        if (!ex.isDirectory()) {
            boolean loose = new File(root, "exefs/main").isFile() || new File(root, "romfs.bin").isFile()
                    || new File(root, "romfs/bin/message").isDirectory();
            c.state = loose ? State.LOOSE : State.NO_EXTRACTED;
            return c;
        }
        File exefs = new File(ex, "exefs");
        File main = new File(exefs, "main"), npdm = new File(exefs, "main.npdm");
        if (!main.isFile() || !npdm.isFile() || !isNso(main)) { c.state = State.NO_EXEFS; return c; }
        long pid = programId(npdm);
        if (pid != TITLE_EEVEE && pid != TITLE_PIKACHU) { c.state = State.UNKNOWN_GAME; return c; }
        c.game = (pid == TITLE_EEVEE ? "Let's Go, Eevee!" : "Let's Go, Pikachu!") + " " + version(main);
        boolean importOk = new File(ex, ".import_ok").exists();
        if (!importOk && (new File(ex, ".import").isDirectory() || new File(root, ".import").isDirectory())) {
            c.state = State.IMPORT_PARTIAL;
            return c;
        }
        File bin = new File(ex, "romfs.bin");
        File romfs = new File(ex, "romfs");
        c.romfsMissing = romfs.isDirectory() ? missingRomfsPart(romfs) : null;
        c.hasRomfsDir = romfs.isDirectory() && c.romfsMissing == null;
        if (bin.isFile()) {
            c.romfsBinBytes = bin.length();
            c.romfsBinNeeded = romfsImageSize(bin);
            if (c.romfsBinNeeded < 0 || c.romfsBinBytes < c.romfsBinNeeded) { c.state = State.ROMFS_BIN_BROKEN; return c; }
        } else if (!romfs.isDirectory()) {
            c.state = State.NO_ROMFS;
            return c;
        } else if (!c.hasRomfsDir) {
            c.state = State.ROMFS_DIR_INCOMPLETE;
            return c;
        }
        if (!root.canWrite()) { c.state = State.READ_ONLY; return c; }
        c.state = bin.isFile() ? State.READY : State.READY_FIRST_RUN;
        return c;
    }

    /** Folders of the RomFS that the game cannot start without (same in Eevee and Pikachu, every region: the
     *  RomFS has every language). Cheap - a few stats, not a walk of the ~28,000 files - so it catches a copy
     *  that stopped between folders, not a file cut short inside one. */
    static final String[] ROMFS_PARTS = {"bin/archive", "bin/battle", "bin/field", "bin/message/English",
            "bin/pokemon", "bin/script", "bin/sound"};

    /** The first ROMFS_PARTS folder missing (or empty) under romfs, or null when they are all there. */
    static String missingRomfsPart(File romfs) {
        for (String part : ROMFS_PARTS) {
            String[] l = new File(romfs, part).list();
            if (l == null || l.length == 0) return part;
        }
        return null;
    }

    /** exefs/main starts with "NSO0". */
    static boolean isNso(File main) {
        byte[] m = read(main, 0, 4);
        return m != null && m[0] == 'N' && m[1] == 'S' && m[2] == 'O' && m[3] == '0';
    }

    /** The program id in main.npdm (META -> ACI0 + 0x10), as gamesetup.c program_id(); 0 if unreadable. */
    static long programId(File npdm) {
        byte[] meta = read(npdm, 0, 0x80);
        if (meta == null || meta[0] != 'M' || meta[1] != 'E' || meta[2] != 'T' || meta[3] != 'A') return 0;
        long aciOff = u32(meta, 0x70);
        byte[] aci = read(npdm, aciOff, 0x18);
        if (aci == null || aci[0] != 'A' || aci[1] != 'C' || aci[2] != 'I' || aci[3] != '0') return 0;
        return u64(aci, 0x10);
    }

    /** The game version from the NSO module id at 0x40 (gamesetup.c game_version()); "" if not a known one. */
    static String version(File main) {
        byte[] id = read(main, 0x40, 8);
        if (id == null) return "";
        String h = hex(id);
        if (h.equals("0a3f0d21e67dad80") || h.equals("1c375692de4f4b87")) return "1.0.0";
        if (h.equals("c208db6a4ef4361f")) return "1.0.2";
        return "";
    }

    /** The size a RomFS image must have, from its own header and file table: the end of its last table or of its
     *  last file's data, whichever is further. -1 if the header is not a RomFS one (or unreadable). A file
     *  shorter than this was cut short (romfs_build writes the tables last; a copy stops anywhere). */
    static long romfsImageSize(File bin) {
        byte[] h = read(bin, 0, 0x50);
        if (h == null || u64(h, 0) != 0x50) return -1;
        long end = 0;
        for (int i = 1; i + 1 < 9; i += 2) {
            long off = u64(h, 8 * i), size = u64(h, 8 * (i + 1));
            if (off < 0 || size < 0) return -1;
            end = Math.max(end, off + size);
        }
        long dataOff = u64(h, 8 * 9), fmetaOff = u64(h, 8 * 7), fmetaSize = u64(h, 8 * 8);
        if (dataOff < 0x50 || fmetaSize < 0 || fmetaSize > (64L << 20)) return -1;
        byte[] ft = read(bin, fmetaOff, (int) fmetaSize);
        if (ft == null) return end + 1;  // (the table itself is past the end of the file: cut short)
        for (int p = 0; p + 0x20 <= ft.length; ) {
            long off = u64(ft, p + 8), size = u64(ft, p + 16), nl = u32(ft, p + 28);
            if (off < 0 || size < 0 || nl > 0x300) return -1;
            end = Math.max(end, dataOff + off + size);
            p += 0x20 + (int) ((nl + 3) & ~3L);
        }
        return end;
    }

    /** Bytes of file data under dir (what the first boot copies into romfs.bin). */
    static long treeBytes(File dir) {
        long n = 0;
        File[] l = dir.listFiles();
        if (l == null) return 0;
        for (File f : l) n += f.isDirectory() ? treeBytes(f) : f.length();
        return n;
    }

    private static byte[] read(File f, long off, int n) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(f, "r");
            if (off < 0 || off + n > r.length()) return null;
            byte[] b = new byte[n];
            r.seek(off);
            r.readFully(b);
            return b;
        } catch (IOException | SecurityException e) {
            return null;
        } finally {
            if (r != null) try { r.close(); } catch (IOException ignored) {}
        }
    }

    private static long u32(byte[] b, int o) {
        return (b[o] & 0xffL) | (b[o + 1] & 0xffL) << 8 | (b[o + 2] & 0xffL) << 16 | (b[o + 3] & 0xffL) << 24;
    }

    private static long u64(byte[] b, int o) { return u32(b, o) | u32(b, o + 4) << 32; }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }

    private GameFolder() {}
}
