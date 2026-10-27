package com.letsgorecomp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** MASTER.md §§2–5. Resources own colors; this file owns sizes and component factories.
 * Used by the launcher and ModsPanel; callbacks and navigation remain with their owners.
 * No custom animation: platform feedback follows the system animation setting.
 */
final class Theme {
    static final float TITLE_L = 22, TITLE_M = 18, TITLE_S = 15;
    static final float BODY_L = 16, BODY = 14, BODY_S = 13, CAPTION = 12, BUTTON = 15, PLAY = 20;
    static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24, S6 = 32, S7 = 48;
    static final int R_CTL = 12, R_TILE = 16, R_BOX = 20, R_RAIL = 28;
    static final int TOUCH_MIN = 48, ROW_H = 56, TILE_H = 64, PLAY_H = 72;

    private Theme() {}

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    // Dynamic lookup matches this project's javac-before-aapt build (no generated R at javac).
    static int color(Context c, String token) {
        int id = c.getResources().getIdentifier("lg_" + token, "color", c.getPackageName());
        if (id == 0) throw new IllegalArgumentException("Unknown theme token: " + token);
        return c.getResources().getColor(id, c.getTheme());
    }

    static ColorStateList textColors(Context c, int enabled) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{color(c, "text_off"), enabled});
    }

    static GradientDrawable rounded(Context c, int fill, int radius, boolean outline) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radius));
        if (outline) d.setStroke(dp(c, 1), color(c, "ctl"));
        return d;
    }

    static RippleDrawable background(Context c, int fill, int radius, boolean outline) {
        GradientDrawable mask = rounded(c, Color.WHITE, radius, false);
        return new RippleDrawable(ColorStateList.valueOf(color(c, "press")),
                rounded(c, fill, radius, outline), mask);
    }

    static TextView label(Context c, String text, float sp, String token, boolean medium) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setTextColor(color(c, token));
        return t;
    }

    private static Button button(Context c, String text, boolean primary, boolean play) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, play ? PLAY : BUTTON);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(textColors(c, color(c, primary ? "on_primary" : "text")));
        b.setMinWidth(dp(c, TOUCH_MIN));
        b.setMinHeight(dp(c, play ? PLAY_H : TOUCH_MIN));
        b.setPadding(dp(c, S5), dp(c, S2), dp(c, S5), dp(c, S2));
        b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null);
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        int radius = play ? R_BOX : R_CTL;
        states.addState(new int[]{-android.R.attr.state_enabled}, rounded(c, Color.TRANSPARENT, radius, true));
        states.addState(new int[]{}, background(c, color(c, primary ? "primary" : "surface2"), radius, !primary));
        b.setBackground(states);
        b.setBackgroundTintList(null);
        return b;
    }

    static Button primaryButton(Context c, String text) { return button(c, text, true, false); }
    static Button playButton(Context c, String text) { return button(c, text, true, true); }
    static Button secondaryButton(Context c, String text) { return button(c, text, false, false); }

    // Caller owns the listener and translated strings; a row does not silently change navigation.
    static LinearLayout tile(Context c) {
        LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(c, S4), dp(c, S2), dp(c, S4), dp(c, S2));
        tile.setBackground(rounded(c, color(c, "surface"), R_TILE, false));
        return tile;
    }

    static LinearLayout row(Context c, String title, String value, boolean tile) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, tile ? TILE_H : ROW_H));
        row.setPadding(dp(c, S4), dp(c, S2), dp(c, S4), dp(c, S2));
        row.setBackground(background(c, tile ? color(c, "surface") : Color.TRANSPARENT,
                tile ? R_TILE : R_CTL, false));
        row.addView(label(c, title, TITLE_S, "text", true));
        if (value != null) row.addView(label(c, value, BODY_S, "text2", false));
        return row;
    }

    static LinearLayout selector(Context c, String title, String value) {
        LinearLayout row = row(c, title, value, false);
        row.setFocusable(true);
        row.setClickable(true);
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        return row;
    }

    static CheckBox checkBox(Context c, String text) {
        CheckBox cb = new CheckBox(c);
        cb.setText(text);
        cb.setTextColor(textColors(c, color(c, "text")));
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_S);
        cb.setMinHeight(dp(c, TOUCH_MIN));
        cb.setMinWidth(dp(c, TOUCH_MIN));
        cb.setButtonTintList(new ColorStateList(new int[][]{
                {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}},
                new int[]{color(c, "text_off"), color(c, "primary"), color(c, "ctl")}));
        return cb;
    }

    static void railButton(Context c, Button b) {
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_selected}, {}},
                new int[]{color(c, "primary_text"), color(c, "text")}));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, background(c, color(c, "primary_soft"), R_RAIL, false));
        states.addState(new int[]{}, background(c, Color.TRANSPARENT, R_RAIL, false));
        b.setBackground(states);
    }

    static void toggle(Context c, android.widget.CompoundButton b) {
        b.setMinWidth(dp(c, TOUCH_MIN));
        b.setMinHeight(dp(c, ROW_H));
        b.setTextColor(textColors(c, color(c, "text")));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_S);
        ColorStateList tint = new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{color(c, "primary"), color(c, "ctl")});
        if (b instanceof android.widget.Switch) {
            ((android.widget.Switch) b).setThumbTintList(tint);
            ((android.widget.Switch) b).setTrackTintList(tint);
        } else b.setButtonTintList(tint);
    }

    static void selector(Context c, android.widget.Spinner s) {
        s.setMinimumHeight(dp(c, ROW_H));
        s.setMinimumWidth(dp(c, TOUCH_MIN));
        s.setBackgroundTintList(ColorStateList.valueOf(color(c, "ctl")));
        s.setPopupBackgroundDrawable(rounded(c, color(c, "surface"), R_CTL, true));
    }

    static TextView sectionHeader(Context c, String text) {
        TextView t = label(c, text, TITLE_M, "text", true);
        t.setAccessibilityHeading(true);
        t.setPadding(dp(c, S5), dp(c, S4), dp(c, S5), dp(c, S2));
        return t;
    }

    static LinearLayout statusBar(Context c, String title, String subtitle) {
        LinearLayout bar = row(c, title, subtitle, false);
        ((TextView) bar.getChildAt(0)).setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_L);
        return bar;
    }

    static ScrollView scrollingPane(Context c, View content) {
        ScrollView s = new ScrollView(c);
        s.setScrollbarFadingEnabled(false);
        s.setVerticalFadingEdgeEnabled(true);
        s.setFadingEdgeLength(dp(c, S7));
        s.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }
}
