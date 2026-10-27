// Several mods at once, in the player's order (runtime/mods.c, LARECOMP_MODS), without the game: two mods that
// replace the same RomFS file, in both orders, and which copy mod_romfs_find serves. Build and run (from src/):
//   clang -O2 -Iruntime tools/test_mods_order.c runtime/mods.c -lpthread -o build/test_mods_order && build/test_mods_order
// (clang: runtime/cpu.h uses its builtins.) It makes its own mods/ in a temp folder; exit code 0 = all passed.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "gamesetup.h"
#include "mods.h"
#include "platform.h"
#include "romfs_build.h"
#include "settings.h"

Settings g_settings;
const char *game_name(const char *exefs) { (void)exefs; return "Eevee"; }
PlatProc plat_spawn(const char *const argv[], const char *log_path, bool append_log) { (void)argv, (void)log_path, (void)append_log; return 0; }
int plat_proc_wait(PlatProc p, bool block) { (void)p, (void)block; return -1; }
bool romfs_copy_tree(const char *src, const char *dst, void (*progress)(uint64_t, uint64_t)) { (void)src, (void)dst, (void)progress; return false; }

static int fails, checks;
static void check(const char *what, bool ok) {
    checks++;
    if (!ok) fails++;
    printf("%s %s\n", ok ? "ok  " : "FAIL", what);
}

static void put(const char *path, const char *text) {
    char d[1024];
    snprintf(d, sizeof d, "%s", path);
    for (char *c = d + 1; *c; c++) if (*c == '/') { *c = 0; mkdir(d, 0755); *c = '/'; }
    FILE *f = fopen(path, "w");
    fputs(text, f);
    fclose(f);
}

static char served[1200];
// which mod's copy of path is served: its name, or "-" when no active mod has it
static const char *who(const char *path) {
    int i = mod_romfs_find(path, served, sizeof served, true);
    return i < 0 ? "-" : mods_active_name(i);
}

int main(void) {
    char root[] = "/tmp/mods-order-XXXXXX";
    if (!mkdtemp(root)) return 2;
    if (chdir(root)) return 2;
    setenv("LARECOMP_MODS_DIR", "mods", 1);
    // Zed is created first (readdir tends to give it first): the order must still be the list's
    put("mods/Zed/romfs/bin/message/English/common/monsname.dat", "Z");
    put("mods/Zed/romfs/bin/only_zed.dat", "z");
    put("mods/Alpha/romfs/bin/message/English/common/monsname.dat", "A");
    put("mods/Alpha/romfs/bin/only_alpha.dat", "a");
    put("mods/French Texts/romfs/bin/message/French/common/x.dat", "f");
    put("mods/Not A Mod/readme.txt", "x");
    const char *common = "bin/message/English/common/monsname.dat";

    setenv("LARECOMP_MODS", "Alpha|Zed", 1);
    check("Alpha|Zed: two mods on", mods_active_count() == 2 && !strcmp(mods_active_name(0), "Alpha"));
    check("Alpha|Zed: the common file comes from Alpha", !strcmp(who(common), "Alpha") && strstr(served, "mods/Alpha/romfs/"));
    check("Alpha|Zed: each mod's own file is served too (stacked)", !strcmp(who("bin/only_alpha.dat"), "Alpha") && !strcmp(who("bin/only_zed.dat"), "Zed"));
    check("Alpha|Zed: a file neither has stays the game's", !strcmp(who("bin/none.dat"), "-"));

    setenv("LARECOMP_MODS", "Zed|Alpha", 1);
    check("Zed|Alpha: the common file now comes from Zed", !strcmp(who(common), "Zed") && strstr(served, "mods/Zed/romfs/"));
    check("Zed|Alpha: both own files still served", !strcmp(who("bin/only_alpha.dat"), "Alpha") && !strcmp(who("bin/only_zed.dat"), "Zed"));

    setenv("LARECOMP_MODS", "Alpha|Missing|Not A Mod|Alpha|../Zed|Zed", 1);
    check("missing, not-a-mod, repeated and ../ names are left out", mods_active_count() == 2 && !strcmp(mods_active_name(1), "Zed"));

    setenv("LARECOMP_MODS", "French Texts|Alpha", 1);
    check("language: the first mod that is one language's texts decides", !strcmp(game_language(), "fr"));
    setenv("LARECOMP_MODS", "Alpha|French Texts", 1);
    check("language: in the other order, English", !strcmp(game_language(), "en-US"));

    setenv("LARECOMP_MODS", "", 1);
    setenv("LARECOMP_MOD", "Zed", 1);
    check("LARECOMP_MODS set but empty: no mod (LARECOMP_MOD not used)", mods_active_count() == 0 && !strcmp(who(common), "-"));
    unsetenv("LARECOMP_MODS");
    check("no LARECOMP_MODS: LARECOMP_MOD, one mod (as before)", mods_active_count() == 1 && !strcmp(who(common), "Zed"));
    unsetenv("LARECOMP_MOD");
    snprintf(g_settings.mod, sizeof g_settings.mod, "Alpha");
    check("neither: larecomp.ini's mod", mods_active_count() == 1 && !strcmp(who(common), "Alpha"));

    char names[MODS_MAX][128];
    int n = mods_list(names, MODS_MAX);
    check("mods_list: sorted by name, folders without romfs/ left out", n == 3 && !strcmp(names[0], "Alpha")
          && !strcmp(names[1], "French Texts") && !strcmp(names[2], "Zed"));

    char cmd[1100];
    snprintf(cmd, sizeof cmd, "rm -rf '%s'", root);
    if (system(cmd)) {}
    printf("%d/%d checks passed\n", checks - fails, checks);
    return fails ? 1 : 0;
}
