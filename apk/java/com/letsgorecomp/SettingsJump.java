package com.letsgorecomp;

// The launcher is one long column: on an unfolded phone the settings start at the very bottom, cut in half, and
// nothing says the page goes on. A "Settings ↓" shortcut shows in a bar under the page while the settings title
// is still low on the screen and the page can scroll further; a tap scrolls to it. Plain Java (no Android types)
// so apk/test/SettingsJumpTest.java checks it on a PC.
final class SettingsJump {
    private SettingsJump() {}

    /** Show the shortcut? titleTop: the settings title's top in the scrolled column; scrollY: how far the column is
     *  scrolled; viewH: the screen's height (the launcher's whole window); canScrollDown: the column has more below. Shown while the title sits in
     *  the lower third of the screen (or below it) and scrolling down can still bring it up. */
    static boolean show(int titleTop, int scrollY, int viewH, boolean canScrollDown) {
        if (!canScrollDown || viewH <= 0) return false;
        return titleTop - scrollY > viewH * 2 / 3;
    }

    /** Where a tap scrolls the column: the title at the top of the screen, a margin above it. */
    static int target(int titleTop, int margin) {
        return Math.max(0, titleTop - margin);
    }
}
