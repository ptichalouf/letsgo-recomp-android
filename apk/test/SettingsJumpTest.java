package com.letsgorecomp;

import java.nio.file.Files;
import java.nio.file.Path;

/** Plain Java regression checks; run from the repository root. No APK/device required. */
public class SettingsJumpTest {
    private static int checks;
    private static void check(String name, boolean ok) {
        checks++;
        if (!ok) throw new AssertionError(name);
        System.out.println("ok " + name);
    }
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of(args.length == 0
                ? "apk/java/com/letsgorecomp/LetsGoLauncherActivity.java" : args[0]));
        // This first check fails on main 4494622: it has only one long, untitled column.
        for (String section : new String[]{"folder", "driver", "image", "game", "controls", "journal"}) {
            check("visible titled section " + section, source.contains("section(\"" + section + "\", Txt.t("));
        }
        check("settings separated from home", source.contains("showSettings(0)") && source.contains("showHome()"));
        check("rail follows scroll geometry", source.contains("setOnScrollChangeListener")
                && source.contains("SettingsJump.active(") && source.contains("railButtons[i].setSelected"));
        check("persistent scrollbar", source.contains("setScrollbarFadingEnabled(false)"));
        check("scrollbar reserves space", source.contains("SCROLLBARS_INSIDE_INSET"));
        check("48dp continuation fade", source.contains("setVerticalFadingEdgeEnabled(true)")
                && source.contains("setFadingEdgeLength(dp(48))"));
        check("248dp rail gated by size", source.contains("SettingsJump.wide(") && source.contains("dp(248)"));
        check("rotation relayout", source.contains("onConfigurationChanged(") && source.contains("layoutSettings("));
        check("back returns home", source.contains("onBackPressed()") && source.contains("settingsShowing"));
        check("restores screen after recreation", source.contains("onSaveInstanceState(")
                && source.contains("getBoolean(\"settingsShowing\""));
        check("all six group containers in scroll", source.contains("for (LinearLayout section : sections) settingsColumn.addView(section"));
        check("old hidden shortcut removed", !source.contains("SettingsJump.show("));
        check("rail scrolls for large fonts", source.contains("railScroll = scrolling(rail)")
                && source.contains("settingsBody.addView(railScroll"));
        check("driver selection is direct, not a cycle", source.contains("new android.widget.RadioButton(this)")
                && !source.contains("(idx + 1) % all.size()"));
        check("existing controls assigned to sections", source.contains("move(sections[2], resLabel, resSpinner")
                && source.contains("move(sections[3], langLabel, langSpinner, wild)")
                && source.contains("move(sections[4], touch, touchFull, gyroAim)"));
        // Reflective calls let the same test compile against the original helper: red is an assertion,
        // not a compilation error for methods which do not exist yet.
        check("wide at both exact thresholds", wide(600, 480));
        check("compact below width", !wide(599, 900));
        check("compact below height", !wide(960, 479));
        check("folded portrait", !wide(411, 960));
        int[] tops = {0, 300, 900, 1600, 1900, 2400};
        check("first section", active(tops, 0, true) == 0);
        check("before second title", active(tops, 299, true) == 0);
        check("on second title", active(tops, 300, true) == 1);
        check("reverse scroll", active(tops, 901, true) == 2);
        check("short last section at bottom", active(tops, 2000, false) == 5);
        check("unscrollable whole page keeps first", active(tops, 0, false) == 0);
        check("target with margin", SettingsJump.target(300, 8) == 292);
        check("target clamped", SettingsJump.target(5, 8) == 0);
        System.out.println("ALL OK: " + checks + " checks");
    }
    private static boolean wide(int w, int h) throws Exception {
        return (Boolean) SettingsJump.class.getDeclaredMethod("wide", int.class, int.class).invoke(null, w, h);
    }
    private static int active(int[] tops, int y, boolean more) throws Exception {
        return (Integer) SettingsJump.class.getDeclaredMethod("active", int[].class, int.class, boolean.class)
                .invoke(null, tops, y, more);
    }
}
