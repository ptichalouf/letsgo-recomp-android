// Mods: game files replaced or added from a folder, as Atmosphere's LayeredFS (fan translations come this way), and
// the game's language. runtime/mods.c; the start screen (runtime/ui/launcher_lg.mm) picks both.
//
//   mods/<name>/romfs/...                                  the game's RomFS paths (bin/message/English/...)
//   mods/<name>/atmosphere/contents/<title id>/romfs/...   (or any folder holding a romfs/ up to 4 levels down:
//                                                           a download unpacked as it came)
#pragma once
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MODS_MAX 32
// The folders under mods/ that hold a romfs/: names[i] (up to MODS_MAX), sorted by name. Returns how many.
int mods_list(char names[][128], int max);
// The active mods, HIGHEST PRIORITY FIRST: LARECOMP_MODS ("a|b|c", the launcher's ordered list), else LARECOMP_MOD
// (one mod), else larecomp.ini's mod. How many (folders without a romfs/ are left out), and each one's name / romfs/.
int mods_active_count(void);
const char *mods_active_name(int i);
const char *mods_active_romfs(int i);
// path: a RomFS path ("bin/message/English/..."). The index (mods_active_*) of the mod whose copy is served -- the
// first active mod that has the file -- with that host file in out; -1 = no active mod has it. report: log every
// other active mod that has it too (a conflict: its copy is hidden).
int mod_romfs_find(const char *path, char *out, size_t cap, bool report);
// mod_romfs_find without the report: true with the host file when an active mod replaces path.
bool mod_romfs_override(const char *path, char *out, size_t cap);
// The language whose texts a mod replaces (its romfs/bin/message/<language>/), as a language code ("en-US"), or NULL
// (none, or several).
const char *mod_language(const char *name);
// Installs a mod from a file the player downloaded (.zip / .rar / .7z, unpacked with Windows' tar) or a folder, on a
// background thread: its romfs/ (Let's Go Eevee's first) is copied to mods/<file name>/romfs.
enum { MOD_INSTALL_IDLE, MOD_INSTALL_RUNNING, MOD_INSTALL_DONE, MOD_INSTALL_FAIL_UNPACK, MOD_INSTALL_FAIL_NOT_MOD };
void mod_install_start(const char *src);
int mod_install_state(void);
const char *mod_install_name(void);  // after MOD_INSTALL_DONE: the mods/ folder name
// The language the game is told (nn::settings / applet "desired language"): that of the first active mod (in order)
// whose texts are one language's, else g_settings.game_lang.
const char *game_language(void);

#ifdef __cplusplus
}
#endif
