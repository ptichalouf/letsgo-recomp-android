// On-device mod unpacking (apk/modunpack/modunpack.c).
#pragma once
#include <stddef.h>

enum {
    MOD_UNPACK_OK = 0,
    MOD_UNPACK_ERR_ARCHIVE = 1,    // unreadable or corrupt archive, or a format libarchive doesn't read
    MOD_UNPACK_ERR_NOT_MOD = 2,    // no file under a romfs/ folder (a code-only mod, a read-me...)
    MOD_UNPACK_ERR_ENCRYPTED = 3,  // only password-protected entries
    MOD_UNPACK_ERR_WRITE = 4,      // the destination can't be written (full, read-only)
    MOD_UNPACK_ERR_PATH = 5,       // destination path too long
};

typedef struct {
    unsigned files;           // written under <dst>/.../romfs/
    unsigned long long bytes; // their size
    unsigned code;            // game-code patches skipped (exefs/, .ips, .pchtxt, cheats/)
    unsigned other;           // other entries skipped (outside romfs/, links, unsafe paths)
    unsigned encrypted;       // password-protected entries skipped
    unsigned mods;            // mods/ folders made: 1, or one per variant
    char names[2048];         // their names ('|' between): <dst name>, or "<dst name> - <variant>"
} ModUnpackStats;

// Unpacks the archive (src, or the open file fd >= 0, which is then closed) into the folder dst (or "<dst> - <variant>"
// folders: modunpack.c), each replaced only on success. Returns MOD_UNPACK_*; err gets a message on failure.
int mod_unpack(const char *src, int fd, const char *dst, ModUnpackStats *st, char *err, size_t err_cap);
