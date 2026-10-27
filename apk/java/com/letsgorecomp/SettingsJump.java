package com.letsgorecomp;

/** Geometry shared by the settings rail and the host-side regression tests. */
final class SettingsJump {
    private SettingsJump() {}

    static boolean wide(int widthDp, int heightDp) {
        return widthDp >= 600 && heightDp >= 480;
    }

    /** Last heading above the viewport; bottom wins for a short final section. */
    static int active(int[] tops, int scrollY, boolean canScrollDown) {
        if (tops.length == 0 || scrollY <= 0) return 0;
        if (!canScrollDown) return tops.length - 1;
        int active = 0;
        for (int i = 1; i < tops.length; i++) {
            if (tops[i] > scrollY) break;
            active = i;
        }
        return active;
    }

    static int target(int titleTop, int margin) {
        return Math.max(0, titleTop - margin);
    }
}
