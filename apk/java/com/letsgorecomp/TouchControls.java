package com.letsgorecomp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

// Minimal touch controls (the default). The game image is a 16:9 picture centred in the window (black bars around
// it), so everything that stays on screen lives in those bars and never covers the picture:
//   - left: a FIXED stick (always drawn at the same place, thin ring + small knob; touch it and slide): walking AND
//     menu cursor (the game reads the stick's directions as d-pad buttons, so no d-pad is needed);
//   - right: A (talk / confirm / throw), B (cancel), X (main menu) and "..." which opens a drawer with the rest:
//     Y (options / box / moves), L, R (move Pokemon or items), - and + (buddy, Pokedex), FPS counter, LOG.
// The drawer closes by itself a few seconds after its last use. Everything else on screen is the game's own touch
// screen (partner play, berries, aiming), passed through untouched: no right stick / ZR finger is needed on a phone.
// Bars: top / bottom on a squarish screen (Fold inner display), left / right on a wide one (Fold cover display).
// The old full layout (GamepadOverlay) stays available: pref "touch_layout" = "classic".
public class TouchControls extends ViewGroup {
    // Switch button bits (runtime/hle/input.c), sent like GamepadOverlay does
    private static final int[] KEYS = {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
    };
    private static final int[] BITS = {0, 1, 2, 3, 6, 7, 11, 10};
    // controls: 0 A, 1 B, 2 X, 3 Y, 4 L, 5 R, 6 Minus, 7 Plus (key indexes above); then the non-game ones
    private static final int MORE = 8, FPS = 9, LOG = 10, N = 11;
    private static final String[] LABEL = {"A", "B", "X", "Y", "L", "R", "\u2212", "+", "\u22ef", "FPS", "LOG"};
    private static final int[] DRAWER = {3, 4, 5, 6, 7, FPS, LOG};
    private static final long DRAWER_CLOSE_MS = 8000, DIM_MS = 4000;

    private final Activity activity;
    private final SharedPreferences prefs;
    private final float dp;
    private final Hit[] hit = new Hit[N];
    private final RectF[] disc = new RectF[N];
    private final boolean[] down = new boolean[N];
    private final Stick stick;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG), fpsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean drawerOpen, showFps;
    private long lastTouch = SystemClock.uptimeMillis(), lastDrawer;
    private String fpsText = "";
    // stick: touch zone (in this view's coordinates), CENTRED on the fixed stick centre (stickX, stickY); radius = full
    // tilt; knob = where the knob is drawn while held (the base never moves)
    private final RectF zone = new RectF();
    private float stickX, stickY, radius, knobX, knobY;
    private boolean stickActive;
    private float fpsX, fpsY;

    public TouchControls(Activity activity) {
        super(activity);
        this.activity = activity;
        prefs = activity.getSharedPreferences("letsgo", Activity.MODE_PRIVATE);
        showFps = prefs.getBoolean("touch_fps", false);
        dp = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setMotionEventSplittingEnabled(true);  // (each finger to its own control; the rest to the game)
        for (int i = 0; i < N; i++) {
            disc[i] = new RectF();
            hit[i] = new Hit(i);
            hit[i].setContentDescription(LABEL[i]);
            addView(hit[i]);
        }
        stick = new Stick();
        stick.setContentDescription("stick");
        addView(stick);
        ring.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        fpsPaint.setTextSize(13 * dp);
        fpsPaint.setColor(Color.argb(200, 120, 230, 120));
        setDrawer(false);
        handler.post(tick);
    }

    // ---- input ----
    private void send(int i, boolean on) {
        if (i >= KEYS.length) return;
        try { LetsGoActivity.nativeSetButton(BITS[i], on); } catch (Throwable t) { }
        final KeyEvent ev = new KeyEvent(on ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, KEYS[i]);  // (SDL fallback, as before)
        activity.runOnUiThread(new Runnable() { @Override public void run() { activity.dispatchKeyEvent(ev); } });
    }

    private void press(int i, boolean on, View v) {
        if (down[i] == on) return;
        down[i] = on;
        lastTouch = SystemClock.uptimeMillis();
        if (on) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (isDrawer(i)) lastDrawer = lastTouch;
        if (i == MORE) { if (on) setDrawer(!drawerOpen); }
        else if (i == FPS) { if (on) { showFps = !showFps; prefs.edit().putBoolean("touch_fps", showFps).apply(); } }
        else if (i == LOG) { if (!on) shareLog(); }
        else send(i, on);
        invalidate();
    }

    private void releaseAll() {
        for (int i = 0; i < N; i++) if (down[i] && i < KEYS.length) { down[i] = false; send(i, false); }
        for (int i = 0; i < N; i++) down[i] = false;
        if (stickActive) { stickActive = false; setStick(0, 0); }
        invalidate();
    }

    private void setStick(float nx, float ny) {
        try { LetsGoActivity.nativeSetStick(0, (int) (nx * 32767), (int) (-ny * 32767)); } catch (Throwable t) { }  // (up = +Y)
    }

    private static boolean isDrawer(int i) {
        for (int d : DRAWER) if (d == i) return true;
        return false;
    }

    private void setDrawer(boolean open) {
        drawerOpen = open;
        lastDrawer = SystemClock.uptimeMillis();
        for (int d : DRAWER) {
            if (!open && down[d]) press(d, false, hit[d]);
            hit[d].setVisibility(open ? VISIBLE : GONE);
        }
        requestLayout();
        invalidate();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            boolean held = false;
            for (int d : DRAWER) held |= down[d];
            if (drawerOpen && !held && now - lastDrawer > DRAWER_CLOSE_MS && now - lastTouch > 1500) setDrawer(false);
            if (showFps) {
                try {
                    fpsText = String.format("%.0f fps  %.0f ms", LetsGoActivity.nativeGetFps(), LetsGoActivity.nativeGetFrameMs());
                } catch (Throwable t) { fpsText = ""; }
            }
            invalidate();  // (idle dimming + fps; a few cheap redraws per second)
            handler.postDelayed(this, showFps || drawerOpen || now - lastTouch < DIM_MS + 600 ? 500 : 2000);
        }
    };

    private void shareLog() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime"});
            java.io.InputStream in = p.getInputStream();
            java.io.File out = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "letsgo-log.txt");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close(); in.close();
            android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(out));
            activity.startActivity(android.content.Intent.createChooser(share, Txt.t("share_log", "Compartilhar log")));
        } catch (Exception e) { }
    }

    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (!focus) releaseAll(); }
    @Override protected void onDetachedFromWindow() { releaseAll(); handler.removeCallbacks(tick); super.onDetachedFromWindow(); }

    // ---- layout: everything in the black bars around the 16:9 picture ----
    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
        setMeasuredDimension(w, h);
        place(w, h);
        for (int i = 0; i < N; i++) measureExactly(hit[i], disc[i]);
        measureExactly(stick, zone);
    }

    private static void measureExactly(View v, RectF r) {
        v.measure(MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(r.width())), MeasureSpec.EXACTLY),
                  MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(r.height())), MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < N; i++) lay(hit[i], disc[i]);
        lay(stick, zone);
    }

    private static void lay(View v, RectF r) {
        v.layout(Math.round(r.left), Math.round(r.top), Math.round(r.left) + v.getMeasuredWidth(), Math.round(r.top) + v.getMeasuredHeight());
    }

    private void place(int w, int h) {
        boolean side = (long) w * 9 > (long) h * 16;          // wide screen: bars left / right
        float bar = side ? (w - h * 16f / 9) / 2 : (h - w * 9f / 16) / 2;
        float t = Math.max(bar, 112 * dp);                    // (no room: the controls go over the picture's edge)
        float a = Math.min(Math.min(76 * dp, t * 0.8f), 0.16f * Math.min(w, h)), gap = 10 * dp;
        float[] size = {a, a * 0.82f, a * 0.68f};             // A, B, X; then "..."
        float more = a * 0.55f;
        float m = (t - a) / 2;                                // A's margin to the screen edge (centred in the bar)
        // right cluster: A at the corner, then B, X, "..." along the bar (leftwards at the bottom, upwards at the side)
        float along = side ? h - m : w - m, across = side ? w - t / 2 : h - t / 2;
        int[] order = {0, 1, 2, MORE};
        for (int k = 0; k < order.length; k++) {
            float s = k < 3 ? size[k] : more;
            float c = along - s / 2;
            setDisc(disc[order[k]], side, c, across, s);
            along -= s + gap;
        }
        // drawer: a row centred in the top bar, or a 2-column grid at the top of the left bar
        float d = Math.min(52 * dp, t * 0.42f);
        if (!side) {
            float x = w / 2f - (DRAWER.length * d + (DRAWER.length - 1) * gap * 0.8f) / 2 + d / 2;
            for (int k = 0; k < DRAWER.length; k++, x += d + gap * 0.8f) disc[DRAWER[k]].set(x - d / 2, t / 2 - d / 2, x + d / 2, t / 2 + d / 2);
        } else {
            float colGap = Math.max(4 * dp, (t - 2 * d) / 3);
            for (int k = 0; k < DRAWER.length; k++) {
                float x = colGap + (k % 2) * (d + colGap) + d / 2, y = 8 * dp + (k / 2) * (d + gap * 0.8f) + d / 2;
                disc[DRAWER[k]].set(x - d / 2, y - d / 2, x + d / 2, y + d / 2);
            }
        }
        // stick: a fixed centre in the left part of the bottom bar, or low in the left bar, always below where the drawer
        // opens (so opening the drawer never moves it). Its touch zone is a box CENTRED on that centre, a little wider
        // than the ring (a thumb landing just outside the ring already walks in the right direction), kept inside the
        // bar and off the drawer. Recomputed on every measure: folding / unfolding re-centres it for the new screen.
        radius = Math.min(56 * dp, t * 0.42f);
        float reach = radius * 1.35f, top;
        if (!side) {
            stickX = m + radius + 12 * dp;
            stickY = h - t / 2;
            top = h - t;
        } else {
            top = disc[DRAWER[DRAWER.length - 1]].bottom + gap;
            stickX = t / 2;
            stickY = Math.min(Math.max(h - t * 0.95f, top + radius + 4 * dp), h - radius - 4 * dp);
        }
        float hw = Math.min(reach, Math.min(stickX, side ? t - stickX : reach));
        float hh = Math.min(reach, Math.min(h - stickY, Math.max(radius, stickY - top)));
        zone.set(stickX - hw, stickY - hh, stickX + hw, stickY + hh);
        fpsX = w - 8 * dp - fpsPaint.measureText("60 fps  33 ms");
        fpsY = side ? 22 * dp : t / 2;
    }

    private static void setDisc(RectF r, boolean side, float along, float across, float s) {
        float cx = side ? across : along, cy = side ? along : across;
        r.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
    }

    // ---- drawing (one pass, here: the child views only take touches) ----
    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float k = now - lastTouch > DIM_MS ? 0.55f : 1f;  // idle: fainter still
        for (int i = 0; i < N; i++) {
            if (hit[i].getVisibility() != VISIBLE) continue;
            RectF r = disc[i];
            float cx = r.centerX(), cy = r.centerY(), rad = r.width() / 2 - dp;
            fill.setColor(Color.argb((int) ((down[i] ? 120 : i == FPS && showFps ? 70 : 34) * k), 255, 255, 255));
            c.drawCircle(cx, cy, rad, fill);
            ring.setStrokeWidth(1.5f * dp);
            ring.setColor(Color.argb((int) (110 * k), 255, 255, 255));
            c.drawCircle(cx, cy, rad, ring);
            text.setColor(Color.argb((int) (190 * k), 255, 255, 255));
            text.setTextSize(i >= FPS ? r.width() * 0.26f : r.width() * 0.42f);
            c.drawText(LABEL[i], cx, cy - (text.ascent() + text.descent()) / 2, text);
        }
        // stick: always drawn at its fixed centre. A thin white ring over a thin dark halo (readable on black bars, grass
        // or tarmac alike) and a small knob (faint fill + ring); never a filled disc. Idle dimming stops at 80 %.
        float ks = stickActive ? 1f : Math.max(k, 0.8f), kr = radius * 0.42f;
        float kx = stickActive ? knobX : stickX, ky = stickActive ? knobY : stickY;
        ring.setStrokeWidth(3.5f * dp);
        ring.setColor(Color.argb((int) (70 * ks), 0, 0, 0));
        c.drawCircle(stickX, stickY, radius, ring);
        c.drawCircle(kx, ky, kr, ring);
        ring.setStrokeWidth(1.5f * dp);
        ring.setColor(Color.argb((int) ((stickActive ? 190 : 150) * ks), 255, 255, 255));
        c.drawCircle(stickX, stickY, radius, ring);
        fill.setColor(Color.argb(stickActive ? 110 : (int) (40 * ks), 255, 255, 255));
        c.drawCircle(kx, ky, kr, fill);
        ring.setColor(Color.argb((int) ((stickActive ? 220 : 160) * ks), 255, 255, 255));
        c.drawCircle(kx, ky, kr, ring);
        if (showFps && fpsText.length() > 0) c.drawText(fpsText, fpsX, fpsY, fpsPaint);
    }

    // a control's touch area (exactly its disc's square)
    private class Hit extends View {
        final int id;
        Hit(int id) { super(TouchControls.this.getContext()); this.id = id; }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) press(id, true, this);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) press(id, false, this);
            return true;
        }
    }

    // a fold / unfold changes the window: let go of everything (the stick is re-centred by the next measure)
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); releaseAll(); }

    // the stick's zone: the base stays at the fixed centre, the knob follows the thumb up to the ring (analog, full tilt
    // at the ring and beyond; the touch-down point already counts, so a press on the ring walks at once)
    private class Stick extends View {
        int pointer = -1;
        Stick() { super(TouchControls.this.getContext()); }
        private void track(float x, float y) {
            float dx = x - stickX, dy = y - stickY, len = (float) Math.hypot(dx, dy);
            if (len > radius) { dx *= radius / len; dy *= radius / len; len = radius; }
            knobX = stickX + dx; knobY = stickY + dy;
            float dead = radius * 0.18f;
            if (len < dead) setStick(0, 0);
            else { float s = (len - dead) / (radius - dead) / len; setStick(dx * s, dy * s); }
            lastTouch = SystemClock.uptimeMillis();
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            float ox = getLeft(), oy = getTop();
            if (a == MotionEvent.ACTION_DOWN) {
                pointer = e.getPointerId(0);
                stickActive = true;
                track(e.getX() + ox, e.getY() + oy);
            } else if (a == MotionEvent.ACTION_MOVE && pointer >= 0) {
                int p = e.findPointerIndex(pointer);
                if (p < 0) return true;
                track(e.getX(p) + ox, e.getY(p) + oy);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                pointer = -1;
                stickActive = false;
                setStick(0, 0);
            }
            TouchControls.this.invalidate();
            return true;
        }
    }
}
