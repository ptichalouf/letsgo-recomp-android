package com.letsgorecomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Android overlay, independent of Vulkan: offers a rebuild after prolonged present silence. */
public class DisplayGuard extends View {
    /** How often the guard asks the runtime whether the picture is reaching the screen. */
    private static final long POLL_MS = 1000;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint button = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private final RectF btn = new RectF();

    /** The surface where the message is drawn (the game's picture area is behind it). */
    private float cx, cy, bw, bh;
    private boolean visible = false;
    private boolean touching;

    public DisplayGuard(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        bg.setColor(Color.argb(228, 8, 10, 14));
        title.setColor(Color.WHITE);
        title.setTextSize(17 * dp);
        title.setTextAlign(Paint.Align.CENTER);
        title.setFakeBoldText(true);
        body.setColor(Color.argb(220, 220, 224, 230));
        body.setTextSize(13 * dp);
        body.setTextAlign(Paint.Align.CENTER);
        button.setColor(Color.argb(255, 60, 130, 220));
        buttonText.setColor(Color.WHITE);
        buttonText.setTextSize(14 * dp);
        buttonText.setTextAlign(Paint.Align.CENTER);
        setVisibility(GONE);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            boolean stalled = false;
            try {
                stalled = LetsGoActivity.nativeDisplayStalled();
            } catch (Throwable ignored) { }
            if (stalled != visible) {
                visible = stalled;
                android.util.Log.w("lasrecomp", "[display-guard] visible=" + stalled
                        + " silent_ms=" + LetsGoActivity.nativeDisplayStalledMs());
                setVisibility(stalled ? VISIBLE : GONE);
                invalidate();
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    /** Start watching. Called by the activity once the game is running. */
    public void start() {
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, POLL_MS);
    }

    /** Stop watching (the activity goes away). */
    public void stop() {
        handler.removeCallbacks(tick);
        visible = false;
        setVisibility(GONE);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onMeasure(int ws, int hs) {
        setMeasuredDimension(MeasureSpec.getSize(ws), MeasureSpec.getSize(hs));
        // The message sits in the middle of the screen, over the game's picture.
        bw = Math.min(MeasureSpec.getSize(ws) - 48 * dp, 420 * dp);
        bh = 52 * dp;
        cx = MeasureSpec.getSize(ws) / 2f;
        cy = MeasureSpec.getSize(hs) / 2f;
    }

    @Override protected void onDraw(Canvas c) {
        if (!visible) return;
        float pad = 20 * dp;
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        String head = Txt.t("display_stalled_title", "A imagem parou");
        String line = Txt.t("display_stalled_body", "O jogo continua, mas nada aparece na tela.");
        String btnLabel = Txt.t("display_resume", "Retomar a imagem");
        float ty = cy - bh / 2 - 34 * dp;
        c.drawText(head, cx, ty, title);
        c.drawText(line, cx, ty + 22 * dp, body);
        btn.set(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
        c.drawRoundRect(btn, 10 * dp, 10 * dp, button);
        Paint.FontMetrics fm = buttonText.getFontMetrics();
        c.drawText(btnLabel, cx, cy - (fm.ascent + fm.descent) / 2, buttonText);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!visible) { touching = false; return false; }
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                touching = btn.contains(e.getX(), e.getY());
                return touching;
            case android.view.MotionEvent.ACTION_MOVE:
                if (!btn.contains(e.getX(), e.getY())) touching = false;
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                touching = false;
                return true;
            case android.view.MotionEvent.ACTION_UP:
                boolean click = touching && btn.contains(e.getX(), e.getY());
                touching = false;
                if (click) { performClick(); return true; }
                return false;
            default:
                return touching;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        android.util.Log.i("lasrecomp", "[display-guard] resume clicked");
        try { LetsGoActivity.nativeResumeDisplay(); } catch (UnsatisfiedLinkError ignored) { }
        return true;
    }
}
