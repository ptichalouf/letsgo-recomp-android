package com.letsgorecomp;

// SettingsJump on a PC, with the launcher's real geometry (Fold7 unfolded, capture of 27/10: 1812 px visible, the
// settings title at ~1700 px, the first setting cut by the bottom edge):
//   javac -d /tmp/sjt apk/java/com/letsgorecomp/SettingsJump.java apk/test/SettingsJumpTest.java
//   java -cp /tmp/sjt com.letsgorecomp.SettingsJumpTest
public class SettingsJumpTest {
    static int fails;

    static void expect(String what, boolean want, boolean got) {
        boolean ok = want == got;
        if (!ok) fails++;
        System.out.println((ok ? "ok   " : "FAIL ") + what + ": " + got + (ok ? "" : " (want " + want + ")"));
    }

    public static void main(String[] a) {
        int h = 1812, title = 1700, margin = 21;
        expect("unfolded, top of the page: settings cut at the bottom -> shortcut", true, SettingsJump.show(title, 0, h, true));
        expect("folded (outer screen, landscape 1080 high): title far below -> shortcut", true, SettingsJump.show(title, 0, 1080, true));
        int y = SettingsJump.target(title, margin);
        expect("after the tap the column sits at the title", true, y == title - margin);
        expect("after the tap: no shortcut", false, SettingsJump.show(title, y, h, true));
        expect("title in the upper two thirds: no shortcut", false, SettingsJump.show(title, title - h / 2, h, true));
        expect("page cannot scroll further: no shortcut", false, SettingsJump.show(title, 0, h, false));
        expect("not laid out yet (height 0): no shortcut", false, SettingsJump.show(title, 0, 0, true));
        expect("target never above the top", true, SettingsJump.target(5, margin) == 0);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        if (fails != 0) System.exit(1);
    }
}
