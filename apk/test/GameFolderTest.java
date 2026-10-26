package com.letsgorecomp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

// GameFolder's checks on a PC, without a phone or game files: fake folders in a temp dir. A RomFS image made by the
// runtime's own builder can be passed as the first argument (its full and cut-short copies are checked too):
//   javac -d /tmp/gft apk/java/com/letsgorecomp/GameFolder.java apk/test/GameFolderTest.java
//   java -cp /tmp/gft com.letsgorecomp.GameFolderTest [romfs.bin]
public class GameFolderTest {
    static int fails;

    static void expect(String what, GameFolder.State want, GameFolder.State got) {
        boolean ok = want == got;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got + (ok ? "" : " (want " + want + ")"));
    }

    public static void main(String[] a) throws IOException {
        File t = new File(System.getProperty("java.io.tmpdir"), "gft-" + System.nanoTime());
        File root = new File(t, "LetsGo"), ex = new File(root, "extracted");

        expect("nothing chosen", GameFolder.State.NOT_SET, GameFolder.inspect(null).state);
        expect("folder gone", GameFolder.State.MISSING, GameFolder.inspect(root).state);
        root.mkdirs();
        expect("empty folder", GameFolder.State.NO_EXTRACTED, GameFolder.inspect(root).state);
        new File(root, "exefs").mkdirs();
        write(new File(root, "exefs/main"), nso());
        expect("exefs/ not in extracted/", GameFolder.State.LOOSE, GameFolder.inspect(root).state);

        new File(ex, "exefs").mkdirs();
        expect("extracted/ without exefs", GameFolder.State.NO_EXEFS, GameFolder.inspect(root).state);
        write(new File(ex, "exefs/main"), nso());
        write(new File(ex, "exefs/main.npdm"), npdm(0x0100000000001000L));
        expect("another game", GameFolder.State.UNKNOWN_GAME, GameFolder.inspect(root).state);
        write(new File(ex, "exefs/main.npdm"), npdm(GameFolder.TITLE_EEVEE));
        GameFolder.Check c = GameFolder.inspect(root);
        expect("exefs only", GameFolder.State.NO_ROMFS, c.state);
        check("game name", "Let's Go, Eevee! 1.0.0".equals(c.game), c.game);

        new File(root, ".import").mkdirs();
        expect("interrupted in-app import", GameFolder.State.IMPORT_PARTIAL, GameFolder.inspect(root).state);
        new File(root, ".import").delete();

        new File(ex, "romfs/bin/message").mkdirs();
        c = GameFolder.inspect(root);
        expect("romfs/ with only an empty bin/message", GameFolder.State.ROMFS_DIR_INCOMPLETE, c.state);
        for (String part : GameFolder.ROMFS_PARTS) {
            new File(ex, "romfs/" + part).mkdirs();
            write(new File(ex, "romfs/" + part + "/x"), new byte[1]);
            c = GameFolder.inspect(root);
            if (c.state != GameFolder.State.READY_FIRST_RUN)
                expect("romfs/ without " + c.romfsMissing, GameFolder.State.ROMFS_DIR_INCOMPLETE, c.state);
        }
        expect("whole romfs/ folder", GameFolder.State.READY_FIRST_RUN, c.state);

        byte[] img = a.length > 0 ? readAll(new File(a[0])) : tinyRomfs();
        write(new File(ex, "romfs.bin"), img);
        c = GameFolder.inspect(root);
        expect("whole romfs.bin (" + img.length + " bytes)", GameFolder.State.READY, c.state);
        check("image size from its tables", c.romfsBinNeeded > 0 && c.romfsBinNeeded <= img.length,
            c.romfsBinNeeded + " of " + img.length);
        for (long cut : new long[]{img.length - 1, img.length / 2, 0x200, 0x10}) {
            RandomAccessFile r = new RandomAccessFile(new File(ex, "romfs.bin"), "rw");
            r.setLength(Math.min(cut, c.romfsBinNeeded - 1));
            r.close();
            GameFolder.Check k = GameFolder.inspect(root);
            expect("romfs.bin cut at " + Math.min(cut, c.romfsBinNeeded - 1), GameFolder.State.ROMFS_BIN_BROKEN, k.state);
            check("  romfs/ is there to rebuild it", k.hasRomfsDir, "" + k.hasRomfsDir);
        }

        check("picked extracted/ -> its parent", GameFolder.dataRoot(ex).equals(root), "" + GameFolder.dataRoot(ex));
        check("picked extracted/exefs -> the root", GameFolder.dataRoot(new File(ex, "exefs")).equals(root),
            "" + GameFolder.dataRoot(new File(ex, "exefs")));
        check("picked the root -> itself", GameFolder.dataRoot(root).equals(root), "" + GameFolder.dataRoot(root));

        delete(t);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void check(String what, boolean ok, String got) {
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got);
    }

    static byte[] nso() {
        byte[] b = new byte[0x100];
        b[0] = 'N'; b[1] = 'S'; b[2] = 'O'; b[3] = '0';
        byte[] id = {0x0a, 0x3f, 0x0d, 0x21, (byte) 0xe6, 0x7d, (byte) 0xad, (byte) 0x80};  // Eevee 1.0.0 module id
        System.arraycopy(id, 0, b, 0x40, 8);
        return b;
    }

    static byte[] npdm(long programId) {
        byte[] b = new byte[0x100];
        b[0] = 'M'; b[1] = 'E'; b[2] = 'T'; b[3] = 'A';
        put(b, 0x70, 0x80, 4);
        b[0x80] = 'A'; b[0x81] = 'C'; b[0x82] = 'I'; b[0x83] = '0';
        put(b, 0x90, programId, 8);
        return b;
    }

    /** One file "a" (100 bytes) in the root, laid out like romfs_build.c. */
    static byte[] tinyRomfs() {
        int data = 0x200, fsize = 100;
        int dhash = (data + fsize + 3) & ~3, dmeta = dhash + 4, dmetaSize = 0x18;
        int fhash = dmeta + dmetaSize, fmeta = fhash + 4, fmetaSize = 0x20 + 4;
        byte[] b = new byte[fmeta + fmetaSize];
        long[] hv = {0x50, dhash, 4, dmeta, dmetaSize, fhash, 4, fmeta, fmetaSize, data};
        for (int i = 0; i < hv.length; i++) put(b, 8 * i, hv[i], 8);
        int e = fmeta;
        put(b, e + 8, 0, 8);
        put(b, e + 16, fsize, 8);
        put(b, e + 28, 1, 4);
        b[e + 0x20] = 'a';
        return b;
    }

    static void put(byte[] b, int o, long v, int n) {
        for (int i = 0; i < n; i++) b[o + i] = (byte) (v >>> (8 * i));
    }

    static void write(File f, byte[] b) throws IOException {
        FileOutputStream o = new FileOutputStream(f);
        o.write(b);
        o.close();
    }

    static byte[] readAll(File f) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        byte[] b = new byte[(int) r.length()];
        r.readFully(b);
        r.close();
        return b;
    }

    static void delete(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File x : l) delete(x);
        f.delete();
    }
}
