// On-device mod unpacking for Android (libmodunpack.so): a GameBanana download (.zip, .rar, .7z) -> a folder the
// runtime's mods/ reader takes as is (runtime/mods.c mods_list / find_romfs: a romfs/ folder up to 4 levels down,
// the set-up game's title id folder first).
//
// Only what the runtime reads is written: the entries under a folder named romfs (any case: RomFS, RomFs... become
// "romfs", the name runtime/mods.c looks for on a case-sensitive app storage). Everything else is counted, not
// written: game code (exefs/, .ips, .pchtxt, cheats/: the game code is built ahead of time into liblasgame.so, so the
// runtime can't apply it), read-mes, previews.
//
// One archive can hold several romfs/ folders. find_romfs (runtime/mods.c) takes the set-up game's title id folder
// first, then the FIRST folder readdir returns: with "Blue Eyes/romfs" + "Yellow Eyes/romfs", or "Exquisite Eevee
// PLUS/<Eevee id>/romfs" + "Peerless Pikachu PLUS/<Pikachu id>/romfs", the variant would be chance (the Eevee data on
// Pikachu). So the output is flat and unambiguous:
//   - one romfs/                                   -> <dst>/romfs
//   - one per game (each under a title id folder)  -> <dst>/<title id>/romfs (the runtime picks the game's)
//   - otherwise, several variants                  -> "<dst> - <variant>"/romfs each, the player chooses one
//
// Safe by construction: regular files and folders only (no links, devices), no absolute path, no "..", written into
// <dst>.part and renamed to <dst> at the end (a failed unpack leaves the previous version of the mod in place).
//
// The C entry point is mod_unpack(); the JNI one is com.letsgorecomp.ModUnpack.nativeUnpack(src, fd, dst).
#include <archive.h>
#include <archive_entry.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <locale.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

#include "modunpack.h"

static bool is_dir(const char *p) {
    struct stat sb;
    return !stat(p, &sb) && S_ISDIR(sb.st_mode);
}

static void rm_tree(const char *p) {  // (only ever called on <dst>, <dst>.part, <dst>.old)
    DIR *d = opendir(p);
    if (!d) { remove(p); return; }
    char c[4096];
    for (struct dirent *e; (e = readdir(d));) {
        if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
        snprintf(c, sizeof c, "%s/%s", p, e->d_name);
        struct stat sb;
        if (!lstat(c, &sb) && S_ISDIR(sb.st_mode)) rm_tree(c);
        else remove(c);
    }
    closedir(d);
    rmdir(p);
}

static void mkdirs(char *p) {  // every parent of the file p (p is modified and restored)
    for (char *s = strchr(p + 1, '/'); s; s = strchr(s + 1, '/')) {
        *s = 0;
        mkdir(p, 0755);
        *s = '/';
    }
}

static bool is_code(const char *path) {  // a path that patches the game's code (counted, never written)
    const char *ext = strrchr(path, '.');
    if (ext && (!strcasecmp(ext, ".ips") || !strcasecmp(ext, ".pchtxt"))) return true;
    for (const char *c = path; *c; c++)
        if ((c == path || c[-1] == '/') && (!strncasecmp(c, "exefs/", 6) || !strncasecmp(c, "exefs_patches/", 14) ||
                                            !strncasecmp(c, "cheats/", 7)))
            return true;
    return false;
}

// "a\b/RomFS/bin/x" -> "a/b/romfs/bin/x" (true), or false: not under a romfs folder, or unsafe.
static bool romfs_path(const char *in, char *out, size_t cap) {
    if (strlen(in) >= cap) return false;
    snprintf(out, cap, "%s", in);
    for (char *c = out; *c; c++) if (*c == '\\') *c = '/';
    while (out[0] == '.' && out[1] == '/') memmove(out, out + 2, strlen(out + 2) + 1);
    if (out[0] == '/') return false;
    bool under = false;
    for (char *comp = out; *comp;) {
        char *end = strchr(comp, '/');
        size_t n = end ? (size_t)(end - comp) : strlen(comp);
        if ((n == 2 && !strncmp(comp, "..", 2)) || (n == 0 && end)) return false;  // (.. or //)
        if (!under && end && n == 5 && !strncasecmp(comp, "romfs", 5)) { memcpy(comp, "romfs", 5); under = true; }
        if (!end) break;
        comp = end + 1;
    }
    return under;
}

static bool write_data(struct archive *a, const char *path) {
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return false;
    bool ok = true;
    for (;;) {
        const void *buf = NULL;
        size_t n = 0;
        la_int64_t off = 0;
        int r = archive_read_data_block(a, &buf, &n, &off);
        if (r == ARCHIVE_EOF) break;
        if (r < ARCHIVE_WARN) { ok = false; break; }
        // (RAR5 hands out empty blocks with buf left as it was: on the Fold4, pwrite(fd, <garbage>, 0) is EFAULT)
        if (n && pwrite(fd, buf, n, (off_t)off) != (ssize_t)n) { ok = false; break; }
    }
    if (close(fd) != 0) ok = false;
    return ok;
}

#define EEVEE_TITLE "0100187003A36000"
#define PIKACHU_TITLE "010003F003A34000"
#define MAX_ROOTS 16

// The title id folder in a prefix ("a/0100187003A36000/b"), or NULL.
static const char *title_of(const char *prefix) {
    const char *t = NULL;
    for (const char *c = prefix; *c; c++)
        if ((c == prefix || c[-1] == '/') && (c[16] == '/' || !c[16])) {
            if (!strncasecmp(c, EEVEE_TITLE, 16)) t = EEVEE_TITLE;
            else if (!strncasecmp(c, PIKACHU_TITLE, 16)) t = PIKACHU_TITLE;
        }
    return t;
}

// A variant's name: the last folder of the prefix that says something (not a title id, not atmosphere/, contents/...),
// letters, digits, spaces, '-' and '.' kept (as mods.c names an install), else the game, else its number.
static void variant_label(const char *prefix, int k, char *out, size_t cap) {
    static const char *generic[] = {"atmosphere", "contents", "titles", "switch", "emulator", "yuzu", "ryujinx", "sd", NULL};
    char tmp[1024];
    snprintf(tmp, sizeof tmp, "%s", prefix);
    out[0] = 0;
    for (char *comp = strtok(tmp, "/"); comp; comp = strtok(NULL, "/")) {
        bool skip = title_of(comp) != NULL;
        for (int i = 0; generic[i] && !skip; i++) skip = !strcasecmp(comp, generic[i]);
        if (skip) continue;
        size_t n = 0;
        for (const char *c = comp; *c && n + 1 < cap && n < 60; c++) {
            unsigned char ch = (unsigned char)*c;
            out[n++] = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '-' ||
                       ch == ' ' || ch == '.' ? (char)ch : '_';
        }
        out[n] = 0;
    }
    const char *t = title_of(prefix);
    if (!out[0] && t) snprintf(out, cap, "%s", !strcmp(t, EEVEE_TITLE) ? "Eevee" : "Pikachu");
    if (!out[0]) snprintf(out, cap, "%d", k + 1);
}

// <final>.new -> <final> (the previous version moved aside, then removed).
static bool swap_in(const char *final, char *err, size_t err_cap) {
    char nw[4200], old[4200];
    snprintf(nw, sizeof nw, "%s.new", final);
    snprintf(old, sizeof old, "%s.old", final);
    rm_tree(old);
    if (is_dir(final) && rename(final, old)) { snprintf(err, err_cap, "rename %s: %s", final, strerror(errno)); return false; }
    if (rename(nw, final)) { snprintf(err, err_cap, "rename %s: %s", nw, strerror(errno)); return false; }
    rm_tree(old);
    return true;
}

// part/<prefix>/romfs -> <final>.new/<sub>romfs
static bool move_root(const char *part, const char *prefix, const char *final, const char *sub, char *err, size_t err_cap) {
    char from[4200], to[4400];
    snprintf(from, sizeof from, "%s/%s%sromfs", part, prefix, prefix[0] ? "/" : "");
    snprintf(to, sizeof to, "%s.new/%s%sromfs", final, sub, sub[0] ? "/" : "");
    mkdirs(to);
    if (rename(from, to)) { snprintf(err, err_cap, "rename %s: %s", from, strerror(errno)); return false; }
    return true;
}

int mod_unpack(const char *src, int fd, const char *dst, ModUnpackStats *st, char *err, size_t err_cap) {
    ModUnpackStats z = {0};
    *st = z;
    err[0] = 0;
    char part[4096], out[4096], rel[4096];
    static char roots[MAX_ROOTS][1024];  // (one unpack at a time: the caller's install thread)
    int nroots = 0;
    if (snprintf(part, sizeof part, "%s.part", dst) >= (int)sizeof part) { if (fd >= 0) close(fd); return MOD_UNPACK_ERR_PATH; }
    rm_tree(part);
    mkdirs(part);  // (the mods folder itself, the first time)
    if (mkdir(part, 0755) && errno != EEXIST) {
        snprintf(err, err_cap, "mkdir %s: %s", part, strerror(errno));
        if (fd >= 0) close(fd);
        return MOD_UNPACK_ERR_WRITE;
    }

    // Names: libarchive converts them to the thread's charset, and gives NULL for a non-ASCII name under the "C"
    // locale (a zip from Windows: "Pok\x82mon" in CP437 -> no entry at all). UTF-8 for this thread only.
    locale_t utf8 = newlocale(LC_CTYPE_MASK, "C.UTF-8", (locale_t)0), prev = utf8 ? uselocale(utf8) : (locale_t)0;
    struct archive *a = archive_read_new();
    archive_read_support_format_zip(a);
    archive_read_support_format_rar(a);
    archive_read_support_format_rar5(a);
    archive_read_support_format_7zip(a);
    int r = fd >= 0 ? archive_read_open_fd(a, fd, 1 << 16) : archive_read_open_filename(a, src, 1 << 16);
    int ret = MOD_UNPACK_OK;
    if (r != ARCHIVE_OK) {
        snprintf(err, err_cap, "%s", archive_error_string(a) ? archive_error_string(a) : "unreadable archive");
        ret = MOD_UNPACK_ERR_ARCHIVE;
        goto end;
    }
    for (struct archive_entry *e;;) {
        r = archive_read_next_header(a, &e);
        if (r == ARCHIVE_EOF) break;
        if (r < ARCHIVE_WARN) {
            snprintf(err, err_cap, "%s", archive_error_string(a) ? archive_error_string(a) : "corrupt archive");
            ret = MOD_UNPACK_ERR_ARCHIVE;
            goto end;
        }
        const char *name = archive_entry_pathname_utf8(e);
        if (!name) name = archive_entry_pathname(e);
        if (!name) continue;
        mode_t type = archive_entry_filetype(e);
        if (type == AE_IFDIR) continue;  // (folders: made for the files they hold)
        if (archive_entry_is_encrypted(e)) { st->encrypted++; continue; }
        if (type != AE_IFREG || archive_entry_hardlink(e)) { st->other++; continue; }
        if (is_code(name)) { st->code++; continue; }
        if (!romfs_path(name, rel, sizeof rel)) { st->other++; continue; }
        if (snprintf(out, sizeof out, "%s/%s", part, rel) >= (int)sizeof out) { st->other++; continue; }
        {  // its romfs/ folder: one of the archive's roots
            char prefix[1024];
            char *r = strstr(rel, "romfs/");
            while (r && r != rel && r[-1] != '/') r = strstr(r + 1, "romfs/");
            size_t n = r ? (size_t)(r - rel) : 0;
            if (n) n--;  // (without the '/')
            if (n >= sizeof prefix) { st->other++; continue; }
            memcpy(prefix, rel, n);
            prefix[n] = 0;
            int k = 0;
            while (k < nroots && strcmp(roots[k], prefix)) k++;
            if (k == nroots) {
                if (nroots == MAX_ROOTS) { st->other++; continue; }
                snprintf(roots[nroots++], sizeof roots[0], "%s", prefix);
            }
        }
        mkdirs(out);
        if (!write_data(a, out)) {
            snprintf(err, err_cap, "%s: %s", rel, archive_error_string(a) ? archive_error_string(a) : strerror(errno));
            ret = archive_errno(a) ? MOD_UNPACK_ERR_ARCHIVE : MOD_UNPACK_ERR_WRITE;
            goto end;
        }
        st->files++;
        st->bytes += (unsigned long long)archive_entry_size(e);
    }
    if (!st->files) { ret = st->encrypted ? MOD_UNPACK_ERR_ENCRYPTED : MOD_UNPACK_ERR_NOT_MOD; goto end; }
    {
        // one per game? (every root under a title id, each game once)
        bool per_game = nroots == 2;
        for (int k = 0; k < nroots && per_game; k++) per_game = title_of(roots[k]) != NULL;
        if (per_game) per_game = strcmp(title_of(roots[0]), title_of(roots[1])) != 0;
        char final[4200], label[128];
        const char *base = strrchr(dst, '/');
        base = base ? base + 1 : dst;
        st->names[0] = 0;
        for (int k = 0; k < nroots; k++) {
            if (nroots == 1 || per_game) snprintf(final, sizeof final, "%s", dst), label[0] = 0;
            else variant_label(roots[k], k, label, sizeof label), snprintf(final, sizeof final, "%s - %s", dst, label);
            if (k == 0 || !(per_game)) { char nw[4300]; snprintf(nw, sizeof nw, "%s.new", final); rm_tree(nw); }
            if (!move_root(part, roots[k], final, per_game ? title_of(roots[k]) : "", err, err_cap)) { ret = MOD_UNPACK_ERR_WRITE; goto end; }
            if (per_game && k == 0) continue;  // (both games in one mod: swapped in after the second)
            if (!swap_in(final, err, err_cap)) { ret = MOD_UNPACK_ERR_WRITE; goto end; }
            size_t l = strlen(st->names);
            snprintf(st->names + l, sizeof st->names - l, "%s%s%s%s", l ? "|" : "", base, label[0] ? " - " : "", label);
            st->mods++;
        }
    }
end:
    archive_read_free(a);
    if (fd >= 0) close(fd);  // (handed over by the caller; libarchive's fd reader leaves it open)
    if (utf8) { uselocale(prev); freelocale(utf8); }
    rm_tree(part);  // (what is left: nothing on success, a partial unpack on failure)
    return ret;
}

#ifdef MODUNPACK_JNI
#include <jni.h>
// String nativeUnpack(String src, int fd, String dst): "OK files=.. bytes=.. code=.. other=.. encrypted=.. mods=<the
// mods/ folder names made, '|' between>" or
// "ERR <code> <message>". fd >= 0 (a ParcelFileDescriptor detached by the caller, a content:// download) wins over
// src; the library closes it.
JNIEXPORT jstring JNICALL Java_com_letsgorecomp_ModUnpack_nativeUnpack(JNIEnv *env, jclass cls, jstring jsrc, jint fd,
                                                                      jstring jdst) {
    (void)cls;
    const char *src = jsrc ? (*env)->GetStringUTFChars(env, jsrc, NULL) : NULL;
    const char *dst = (*env)->GetStringUTFChars(env, jdst, NULL);
    ModUnpackStats st;
    char err[512], msg[2600];
    int r = mod_unpack(src ? src : "", fd, dst, &st, err, sizeof err);
    if (r == MOD_UNPACK_OK)
        snprintf(msg, sizeof msg, "OK files=%u bytes=%llu code=%u other=%u encrypted=%u mods=%s", st.files, st.bytes,
                 st.code, st.other, st.encrypted, st.names);
    else
        snprintf(msg, sizeof msg, "ERR %d %s (code=%u other=%u encrypted=%u)", r, err, st.code, st.other, st.encrypted);
    if (src) (*env)->ReleaseStringUTFChars(env, jsrc, src);
    (*env)->ReleaseStringUTFChars(env, jdst, dst);
    return (*env)->NewStringUTF(env, msg);
}
#endif

#ifdef MODUNPACK_MAIN
// Test tool: modunpack <archive> <dst folder>
int main(int argc, char **argv) {
    if (argc != 3) { fprintf(stderr, "usage: %s <archive> <dst>\n", argv[0]); return 2; }
    ModUnpackStats st;
    char err[512];
    int r = mod_unpack(argv[1], -1, argv[2], &st, err, sizeof err);
    printf("%s r=%d files=%u bytes=%llu code=%u other=%u encrypted=%u mods=%s %s\n", r ? "ERR" : "OK", r, st.files,
           st.bytes, st.code, st.other, st.encrypted, st.names, err);
    return r ? 1 : 0;
}
#endif
