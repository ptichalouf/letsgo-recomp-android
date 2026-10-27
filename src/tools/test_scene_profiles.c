// Host regression: exercise the real hooks, parser and cold-switch guard without any game payload or Vulkan.
// clang -D_GNU_SOURCE -I src/runtime src/tools/test_scene_profiles.c -o build/test_scene_profiles -lm
#include <assert.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "../runtime/hle.h"
static int registrations;
static int (*world_hook)(Cpu *, uint64_t), (*battle_hook)(Cpu *, uint64_t);
static uint64_t frame;
static bool known = true;
void rt_register_fnhook(uint64_t a, int (*fn)(Cpu *, uint64_t)) {
    registrations++;
    if (a == 0x70002f8bb0ull) world_hook = fn;
    if (a == 0x700028bb60ull) battle_hook = fn;
}
uint64_t rt_addr(uint64_t a) { return known ? a : 0; }
void rt_call_original(Cpu *c, uint64_t a) { (void)c; (void)a; }
void guest_call(Cpu *c, uint64_t a) { (void)c; (void)a; }
uint64_t renderer_game_frame(void) { return frame++; }
uint64_t plat_now_ns(void) { return 0; }
int rt_present_interval(void) { return 2; }
size_t strlcpy(char *dst, const char *src, size_t n) {
    size_t len = strlen(src);
    if (n) { size_t count = len < n - 1 ? len : n - 1; memcpy(dst, src, count); dst[count] = 0; }
    return len;
}
bool bind_settings_load(const char *key, const char *value) { (void)key; (void)value; return false; }
void bind_settings_save(FILE *f) { (void)f; }
#include "../runtime/settings.c"
#include "../runtime/hle/fps60.c"

int main(void) {
    setenv("LARECOMP_SETTINGS", "/nonexistent/scene-profiles-test.ini", 1);
    g_settings.fps = 30;
    fps60_configure();
    assert(!fps60_enabled() && registrations == 3 && world_hook && battle_hook);
    fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_MENU);
    world_hook(NULL, FIELD_UPDATE); fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_WORLD);
    fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_MENU);  // field marker consumed even at 30 fps
    battle_hook(NULL, BATTLE_START); fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_BATTLE);
    fps60_present(); assert(fps60_profile_scene() == RT_SCENE_BATTLE);
    world_hook(NULL, FIELD_UPDATE); fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_WORLD);
    fps60_scene_begin(); fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_BATTLE);  // wild battles use the explicit path
    fps60_configure();
    assert(fps60_profile_scene() == RT_SCENE_MENU);
    g_settings.fps = 60; fps60_configure();
    world_hook(NULL, FIELD_UPDATE); fps60_present();
    assert(fps60_profile_scene() == RT_SCENE_WORLD && fps60_world());
    fps60_present(); assert(fps60_profile_scene() == RT_SCENE_MENU && !fps60_world());
    known = false; fps60_configure(); assert(!fps60_enabled()); known = true;
    puts("SCENE_OK: boot, 30 Hz, menu, battle/capture, return, wild battle, reset, 60 Hz, unknown build");

    parse_line("profile_world_height = 480");
    parse_line("profile_battle_height = 1440");
    parse_line("profile_world_smaa = 0");
    parse_line("profile_battle_smaa = 1");
    parse_line("profile_world_aspect = 1");
    parse_line("profile_battle_aspect = 2");
    assert(g_settings.profile_world_height == 480 && g_settings.profile_battle_height == 1440);
    assert(!g_settings.profile_world_smaa && g_settings.profile_battle_smaa);
    assert(g_settings.profile_world_aspect == PASP_43 && g_settings.profile_battle_aspect == PASP_FILL);
    parse_line("profile_world_height = 90000"); assert(g_settings.profile_world_height == 0);
    parse_line("profile_battle_aspect = -1"); assert(g_settings.profile_battle_aspect == PASP_GAME);
    char path[] = "./scene-profiles-test-XXXXXX";
    int fd = mkstemp(path); assert(fd >= 0); close(fd);
    setenv("LARECOMP_SETTINGS", path, 1); settings_save();
    g_settings.profile_battle_height = 0; g_settings.profile_battle_smaa = false;
    FILE *f = fopen(path, "r"); assert(f); char line[256];
    while (fgets(line, sizeof line, f)) parse_line(line);
    fclose(f); unlink(path);
    assert(g_settings.profile_battle_height == 1440 && g_settings.profile_battle_smaa);
    puts("SETTINGS_OK: six profile keys, bounds, save/parse roundtrip");

    // Live switch. Off (default): nothing changes, the refusal is logged once per scene.
    Settings before = g_settings;
    world_hook(NULL, FIELD_UPDATE); fps60_present();
    battle_hook(NULL, BATTLE_START); fps60_present();
    fps60_scene_begin(); fps60_present();
    assert(settings_profiles_rejected());
    assert(settings_profile_live_height() == 0 && settings_profile_live_smaa() == -1);
    assert(!memcmp(&before, &g_settings, sizeof before));
    puts("GUARD_OK: profiles_auto off: no live value, no rendering setting changed");

    // On, SMAA wanted: refused until the renderer says its post pipelines are prewarmed.
    parse_line("profiles_auto = 1");
    g_settings.profile_world_height = 720; g_settings.profile_battle_height = 1440;
    g_settings.profile_world_smaa = false; g_settings.profile_battle_smaa = true;
    assert(!settings_profiles_rejected() && settings_profile_prewarm_wanted());
    for (int i = 0; i < 40; i++) { battle_hook(NULL, BATTLE_START); fps60_present(); }
    assert(settings_profile_live_height() == 0 && settings_profile_live_smaa() == -1);  // not prewarmed: refused
    settings_profile_set_prewarmed(true);
    // a one-frame world flash does not switch (hold), a settled scene does
    for (int i = 0; i < 5; i++) { world_hook(NULL, FIELD_UPDATE); fps60_present(); }
    assert(settings_profile_live_height() == 0);
    for (int i = 0; i < 30; i++) { battle_hook(NULL, BATTLE_START); fps60_present(); }
    assert(settings_profile_live_height() == 1440 && settings_profile_live_smaa() == 1);
    for (int i = 0; i < 30; i++) { world_hook(NULL, FIELD_UPDATE); fps60_present(); }
    assert(settings_profile_live_height() == 720 && settings_profile_live_smaa() == 0);
    assert(g_settings.antialias == before.antialias && g_settings.res_height == before.res_height);  // shared settings untouched
    puts("LIVE_OK: refused until prewarmed, hold against flashes, world 720/no SMAA <-> battle 1440/SMAA, shared settings untouched");

    // On, nothing to prewarm (no SMAA anywhere, bilinear): switches at once, no renderer dependency.
    settings_profile_set_prewarmed(false);
    g_settings.profile_battle_smaa = false; g_settings.upscaler = UPSCALE_BILINEAR;
    assert(!settings_profile_prewarm_wanted());
    for (int i = 0; i < 30; i++) { battle_hook(NULL, BATTLE_START); fps60_present(); }
    assert(settings_profile_live_height() == 1440 && settings_profile_live_smaa() == 0);
    puts("NOWARM_OK: no post pass to build, no wait");
    return 0;
}
