// Image format of runtime/nvn/output_scale.c (LARECOMP_ASPECT / LARECOMP_SCALE), without launching the game:
// prints render size and image box for a window. Build (from src/):
//   cc -O2 -Iruntime tools/test_output_format.c runtime/nvn/output_scale.c -lm -o build/test_output_format
// Run: LARECOMP_ASPECT=fill LARECOMP_SCALE=native build/test_output_format 2000 1812 [camera]
//   (camera: as if the game's camera already went through rt_cam_ldp; without it the format stays 16:9)
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "settings.h"
#include "nvn/output_scale.h"

Settings g_settings;
bool fps60_enabled(void) { return false; }
bool fps60_world(void) { return false; }
bool fps60_menu(void) { return false; }
void output_scale_camera_seen(void);

int main(int argc, char **argv) {
    if (argc < 3) return 2;
    double dw = atof(argv[1]), dh = atof(argv[2]);
    if (argc > 3 && !strcmp(argv[3], "camera")) output_scale_camera_seen();
    OutScale o;
    output_scale(dw, dh, false, 1.0, -1, &o);
    printf("window %.0fx%.0f: render %.0fx%.0f (scale %.4f x %.4f, shape %.4f), image %.0fx%.0f at %.0f,%.0f\n", dw, dh,
           1920 * o.sx, 1080 * o.sy, o.sx, o.sy, o.shape, o.vw, o.vh, o.vx, o.vy);
    return 0;
}
