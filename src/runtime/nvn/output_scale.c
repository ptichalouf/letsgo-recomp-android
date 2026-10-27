// Output resolution, shared by the Metal and Vulkan backends (each calls output_scale() once per presented frame).
// Render targets the game sizes for the Switch's 1920x1080 screen are allocated at (sx, sy) times their size; the
// image is then drawn into (vx, vy, vw, vh) of the window. Video tab "Internal resolution" (g_settings.res_mode):
//   Match display  render at the window's own pixel size (default)
//   1x .. 4x       N times the Switch's docked image: 1080 lines x N (wide windows keep their shape: 32:9 at 2x =
//                  7680 x 2160); capped to the GPU's 16384-pixel texture limit
//   .75x / .50x / .25x  of Match display (scaled up to the window - with MetalFX on macOS when chosen)
//   Dynamic        Match display, lowered in steps while the GPU can't hold 60 fps and raised again when it can
//                  (each change reallocates the render targets: a short hitch, so it changes rarely, with hysteresis)
// Test overrides: LARECOMP_SCALE=<f> (render height = 1080 x f), LARECOMP_WIDE_TEST=<f> (forced shape).
// Image position (LARECOMP_VALIGN, or the Android property debug.larecomp.valign): unset / "center" = centred in the
// window (the default, unchanged); "top" = the box sits against the top edge, the band below is left to the
// controls (unfolded inner screen: game on the upper part). Only the vertical origin moves: size and scale are the
// same, and the touch screen follows (g_present_rect, runtime/hle/input.c, is set from vx/vy/vw/vh).
#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "../hle.h"
#include "../settings.h"
#include "output_scale.h"
#ifdef __ANDROID__
#include <sys/system_properties.h>
#endif

#define MAX_TEX 16384.0

// Dynamic resolution: a factor on the Match display scale, 0.5 .. 1 in 0.05 steps.
static double g_dyn = 1.0;
static double g_dyn_gpu_sum, g_dyn_dt_sum, g_last_t;
static int g_dyn_n, g_dyn_gpu_n, g_dyn_good;
static double g_dyn_cool_until, g_dyn_probe_wait = 5.0, g_dyn_probe_at;
static double g_dyn_last_raise = -100;
static double g_dyn_target_ms;

static void dyn_update(double now, double gpu_ms, double target_ms) {
    // Do not mix 30 Hz scene samples with 60 Hz world/menu samples.
    if (g_dyn_target_ms != target_ms) {
        g_dyn_target_ms = target_ms;
        g_last_t = now;
        g_dyn_dt_sum = g_dyn_gpu_sum = 0;
        g_dyn_n = g_dyn_gpu_n = g_dyn_good = 0;
        return;
    }
    double dt = g_last_t > 0 ? (now - g_last_t) * 1000.0 : target_ms;
    g_last_t = now;
    if (dt > 250) return;  // (loading, menus, a stall: not the GPU's steady state)
    g_dyn_dt_sum += dt;
    g_dyn_n++;
    if (gpu_ms >= 0) { g_dyn_gpu_sum += gpu_ms; g_dyn_gpu_n++; }
    if (g_dyn_n < 30) return;  // decide every ~half second
    double avg_dt = g_dyn_dt_sum / g_dyn_n, avg_gpu = g_dyn_gpu_n ? g_dyn_gpu_sum / g_dyn_gpu_n : -1;
    g_dyn_dt_sum = g_dyn_gpu_sum = 0;
    g_dyn_n = g_dyn_gpu_n = 0;
    if (now < g_dyn_cool_until) return;
    double prev = g_dyn;
    bool slow = avg_dt > target_ms * (17.4 / 16.666667) ||
                (avg_gpu >= 0 && avg_gpu > target_ms * (14.5 / 16.666667));
    if (slow) {
        g_dyn = fmax(0.5, g_dyn - (avg_dt > target_ms * (22.0 / 16.666667) ? 0.1 : 0.05));
        g_dyn_good = 0;
        if (now - g_dyn_last_raise < 3.0) g_dyn_probe_wait = fmin(g_dyn_probe_wait * 2, 60.0);  // raised too eagerly
    } else if (g_dyn < 1.0) {
        g_dyn_good++;
        // with GPU timing: raise when there is clear headroom; without (Vulkan): probe upwards now and then
        bool room = avg_gpu >= 0 ? avg_gpu < target_ms * (10.5 / 16.666667) && g_dyn_good >= 4 :
                                   now >= g_dyn_probe_at && g_dyn_good >= 2 * (int)g_dyn_probe_wait;
        if (room) { g_dyn = fmin(1.0, g_dyn + 0.05); g_dyn_good = 0; g_dyn_last_raise = now; g_dyn_probe_at = now + g_dyn_probe_wait; }
    }
    g_dyn = round(g_dyn * 20) / 20;
    if (g_dyn != prev) {
        g_dyn_cool_until = now + 1.5;
        LOG("[dynres] %.2f -> %.2f (frame %.1f ms, gpu %.1f ms)\n", prev, g_dyn, avg_dt, avg_gpu);
    }
}

double output_scale_dynamic_factor(void) { return g_dyn; }

// Image format (LARECOMP_ASPECT, set by the Android launcher): unset or "16:9" = the game's own 16:9, fitted in the
// window with black bars; "fill" = the window's own shape; "<w>:<h>" or "<w>x<h>" = that shape, fitted in the window.
// Any shape but 16:9 needs the camera hook (rt_cam_ldp, tools/patches.py: the projection gets the new aspect and,
// for an image narrower than 16:9, a taller field of view): until the game's camera has gone through it (another
// build of the game, without the patch) the image stays 16:9 instead of coming out stretched.
static double g_aspect = -1;  // wanted width/height, 0 = 16:9, -2 = fill the window
static volatile int g_cam_seen;
static double g_cam_shape = 1.0;
double output_scale_camera_shape(void) { return g_cam_shape; }
void output_scale_camera_seen(void) { g_cam_seen = 1; }

static void aspect_load(void) {
    const char *e = getenv("LARECOMP_ASPECT");
    g_aspect = 0;
    if (!e || !*e) return;
    if (!strcmp(e, "fill")) { g_aspect = -2; return; }
    double w = atof(e), h = 0;
    const char *sep = strpbrk(e, ":xX");
    if (sep) h = atof(sep + 1);
    if (w > 0 && h > 0 && w / h > 0.2 && w / h < 5.0 && fabs(w / h - 16.0 / 9.0) > 0.005) g_aspect = w / h;
    LOG("[res] format %s -> %s\n", e, g_aspect > 0 ? "fixed shape" : "16:9");
}

// Vertical origin of the image box: 0 = centred (default), 1 = top. Read once (a launch setting).
static int g_valign = -1;
static int valign_parse(const char *e) { return e && !strcmp(e, "top") ? 1 : 0; }
int output_scale_valign(void) {
    if (g_valign < 0) {
        const char *e = getenv("LARECOMP_VALIGN");
#ifdef __ANDROID__
        char prop[PROP_VALUE_MAX] = "";
        if ((!e || !*e) && __system_property_get("debug.larecomp.valign", prop) > 0) e = prop;
#endif
        g_valign = valign_parse(e);
        if (e && *e) LOG("[res] position %s -> %s\n", e, g_valign ? "top" : "centre");
    }
    return g_valign;
}

void output_scale(double dw, double dh, bool reshape_allowed, double now, double gpu_ms, OutScale *o) {
    double fit = fmin(dw / 1920.0, dh / 1080.0);
    static double forced = -1, wide_test = -1;
    static int native = -1;
    if (native < 0) {
        const char *e = getenv("LARECOMP_SCALE");
        native = e && !strcmp(e, "native");  // render at the screen's own pixels (Match display), whatever the shape
        forced = e && !native ? atof(e) : 0;
    }
    if (wide_test < 0) wide_test = getenv("LARECOMP_WIDE_TEST") ? atof(getenv("LARECOMP_WIDE_TEST")) : 0;
    if (g_aspect == -1) aspect_load();
    double want = g_aspect == -2 ? dw / dh : g_aspect > 0 ? g_aspect : 16.0 / 9.0;
    double shape = wide_test > 0 ? wide_test : want / (16.0 / 9.0);
    bool reshape = (reshape_allowed || (g_aspect != 0 && g_cam_seen)) && fabs(shape - 1.0) > 0.005;
    if (!reshape) shape = 1.0;
    g_cam_shape = shape;
    // the box the image goes in: the wanted shape fitted in the window (all of it for "fill")
    double f2 = fmin(dw / (1920.0 * shape), dh / 1080.0);
    double bw = 1920.0 * shape * f2, bh = 1080.0 * f2;
    // render height in units of the Switch's 1080 lines
    double h;
    int mode = g_settings.res_mode;
    if (native) h = bh / 1080.0;
    else if (forced > 0) h = forced;
    else if (g_settings.res_height > 0) h = g_settings.res_height / 1080.0;  // (the launcher's choice)
    else if (mode >= 1 && mode <= 8) h = mode;
    else {
        h = reshape ? bh / 1080.0 : fit;  // Match display
        if (mode == RES_DYNAMIC) {
            extern bool fps60_enabled(void), fps60_world(void), fps60_menu(void);
            bool at_60 = fps60_enabled() && (fps60_world() || fps60_menu());
            dyn_update(now, gpu_ms, at_60 ? 16.666667 : 33.333333);
            h *= g_dyn;
        }
        else if (mode == RES_Q75) h *= 0.75;
        else if (mode == RES_Q25) h *= 0.25;
        else if (mode == RES_Q50) h *= 0.5;
    }
    h = fmax(h, 0.1);  // (.25x of a small window)
    double sx = h * shape, sy = h;
    double cap = fmin(MAX_TEX / (1920.0 * sx), MAX_TEX / (1080.0 * sy));
    if (cap < 1.0) { sx *= cap; sy *= cap; }
    o->sx = sx; o->sy = sy; o->shape = shape;
    // where the image goes in the window: fills it when reshaped, else a fitted 16:9 box
    if (reshape) { o->vw = bw; o->vh = bh; }
    else { o->vw = 1920.0 * fit; o->vh = 1080.0 * fit; }
    if (wide_test > 0) {
        double f2 = fmin(dw / (1920.0 * wide_test), dh / 1080.0);
        o->vw = 1920.0 * wide_test * f2; o->vh = 1080.0 * f2;
    }
    o->vx = (dw - o->vw) / 2; o->vy = (dh - o->vh) / 2;
    if (output_scale_valign() == 1) o->vy = 0;  // (top: the free band is all below the image)
}
