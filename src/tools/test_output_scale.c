// Deterministic check of dynamic resolution pacing, without launching the game.
// Build: clang -O2 -Iruntime tools/test_output_scale.c runtime/nvn/output_scale.c -o build/test_output_scale.exe
// Image position: [LARECOMP_VALIGN=top] test_output_scale position <w> <h> - the box keeps its size and x, its y is
// centred (default) or 0 (top), and touches on the expected image's corners and centre land on the game's corners and
// centre through a copy of hle/input.c touch_at() fed with the box (plus a shifted box that must miss).
#include <math.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "settings.h"
#include "nvn/output_scale.h"

Settings g_settings = {.res_mode = RES_DYNAMIC};
static bool mode_60;
bool fps60_enabled(void) { return mode_60; }
bool fps60_world(void) { return mode_60; }
bool fps60_menu(void) { return false; }

// Faithful copy of hle/input.c touch_at() (window pixel -> game touch pixel, from g_present_rect, clamped), with
// pixel density 1 and the 1920 x 1080 docked touch screen; r = {x, y, w, h} as device.c sets it from vx/vy/vw/vh.
static void touch_map(const double r[4], double mx, double my, int *tx, int *ty) {
    double x = (mx - r[0]) / r[2] * 1920, y = (my - r[1]) / r[3] * 1080;
    *tx = (int)(x < 0 ? 0 : x > 1919 ? 1919 : x);
    *ty = (int)(y < 0 ? 0 : y > 1079 ? 1079 : y);
}
// Fixed window points, computed from the expected image box only (not from what output_scale returned): its top-left
// and bottom-right pixels and its centre must land on the game's corners and centre through the box output_scale
// gives. A box at the wrong place (e.g. still centred in top mode) moves them off by its error and fails.
static bool touch_lands(const double r[4], double ex, double ey, double ew, double eh) {
    int ax, ay, bx, by, cx, cy;
    touch_map(r, ex + 0.5, ey + 0.5, &ax, &ay);
    touch_map(r, ex + ew - 0.5, ey + eh - 0.5, &bx, &by);
    touch_map(r, ex + ew / 2, ey + eh / 2, &cx, &cy);
    return ax == 0 && ay == 0 && bx == 1919 && by == 1079 && abs(cx - 960) <= 1 && abs(cy - 540) <= 1;
}

static int position(double dw, double dh) {
    Settings keep = g_settings;
    g_settings.res_mode = 0;  // Match display: the box only depends on the window
    OutScale o;
    output_scale(dw, dh, false, 1.0, -1, &o);
    g_settings = keep;
    int top = output_scale_valign();
    double fit = dw / 1920.0 < dh / 1080.0 ? dw / 1920.0 : dh / 1080.0;
    double ew = 1920.0 * fit, eh = 1080.0 * fit, ex = (dw - ew) / 2, ey = top ? 0 : (dh - eh) / 2;
#define NEAR(a, b) (fabs((a) - (b)) < 1e-6)
    bool box = NEAR(o.vw, ew) && NEAR(o.vh, eh) && NEAR(o.vx, ex) && o.vy == ey && o.vy + o.vh <= dh + 1e-6;
    double r[4] = {o.vx, o.vy, o.vw, o.vh};
    bool touch = touch_lands(r, ex, ey, ew, eh);
    // negative control: the same points through a box shifted down by the free band (or 37 px when there is none)
    // must miss - the check above can fail
    double shift = dh - eh > 1 ? (dh - eh) / 2 : 37, bad[4] = {o.vx, o.vy + shift, o.vw, o.vh};
    bool control = !touch_lands(bad, ex, ey, ew, eh);
    bool ok = box && touch && control;
    printf("position %s window %.0fx%.0f: image %.3fx%.3f at %.3f,%.3f, band below %.3f px, touch %s, control %s: %s\n",
           top ? "top" : "centre", dw, dh, o.vw, o.vh, o.vx, o.vy, dh - o.vy - o.vh, touch ? "lands" : "MISSES",
           control ? "misses" : "LANDS", ok ? "ok" : "FAIL");
    return ok ? 0 : 1;
}

int main(int argc, char **argv) {
    if (argc == 4 && !strcmp(argv[1], "position")) return position(atof(argv[2]), atof(argv[3]));
    if (argc == 2 && !strcmp(argv[1], "transition")) {
        OutScale out;
        double now = 1.0;
        for (int phase = 0; phase < 3; phase++) {
            mode_60 = phase == 1;
            double frame_ms = mode_60 ? 16.666667 : 33.333333;
            for (int i = 0; i < 90; i++) {
                now += frame_ms / 1000.0;
                output_scale(1920, 1080, false, now, -1, &out);
            }
        }
        double factor = output_scale_dynamic_factor();
        printf("transition 30->60->30 scale=%.2f\n", factor);
        return factor >= 0.99 ? 0 : 1;
    }
    if (argc != 3) return 2;
    mode_60 = atoi(argv[1]) == 60;
    double frame_ms = atof(argv[2]);
    OutScale out;
    for (int i = 1; i <= 180; i++)
        output_scale(1920, 1080, false, 1.0 + i * frame_ms / 1000.0, -1, &out);
    double factor = output_scale_dynamic_factor();
    printf("target=%s measured=%.2fms scale=%.2f\n", argv[1], frame_ms, factor);
    if (frame_ms <= (mode_60 ? 16.67 : 33.34)) return factor >= 0.99 ? 0 : 1;
    return factor < 0.99 ? 0 : 1;
}
