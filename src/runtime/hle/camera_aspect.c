// The camera's projection for an image that is not 16:9 (LARECOMP_ASPECT, runtime/nvn/output_scale.c).
//
// Let's Go's camera object keeps its vertical field of view (radians) at +0xd0 and its aspect at +0xd4 (16/9, set
// by the constructor at main+0x652808 and by the camera scripts); the projection builders read both with one
// `ldp s, s, [xN, #0xd0]` (main+0x645ec0, +0x6461d0, +0x619b70 in Eevee 1.0.0: tools/patches.py replaces each with a
// call here). The game itself is left untouched: only what those three reads return changes.
//   wider than 16:9   aspect x shape, same vertical field of view (more on the sides: "Hor+", the PC ports' ultrawide)
//   narrower (a near-square foldable)   aspect x shape and a taller field of view that keeps the horizontal one
//                    (the same picture as 16:9 in width, more above and below: "Vert+")
// Only cameras at the game's 16:9 are changed; another aspect (a render to a texture of its own shape) is left alone.
#include <math.h>
#include <stdint.h>
#include <string.h>

#include "../cpu.h"
#include "../hle.h"

double output_scale_camera_shape(void);
void output_scale_camera_seen(void);

void rt_cam_ldp(Cpu *c, uint64_t ea, int rfov, int raspect) {
    float fov, aspect;
    memcpy(&fov, (const void *)(uintptr_t)ea, 4);
    memcpy(&aspect, (const void *)(uintptr_t)(ea + 4), 4);
    output_scale_camera_seen();
    double shape = output_scale_camera_shape();
    if (shape != 1.0 && fabsf(aspect - 16.0f / 9.0f) < 0.01f && fov > 0.01f && fov < 3.0f) {
        aspect = (float)(aspect * shape);
        if (shape < 1.0) fov = (float)fmin(2.0 * atan(tan(fov * 0.5) / shape), 2.6);
    }
    {   // (a few lines in the log: which reads run, with what)
        static int logged;
        if (logged < 12 && shape != 1.0) {
            logged++;
            LOG("[cam] read at %llx: fov %.4f aspect %.4f (shape %.3f)\n", (unsigned long long)ea, fov, aspect, shape);
        }
    }
    uint32_t a, b;
    memcpy(&a, &fov, 4);
    memcpy(&b, &aspect, 4);
    c->v[rfov] = a;  // (a scalar load clears the rest of the vector register)
    c->v[raspect] = b;
}
