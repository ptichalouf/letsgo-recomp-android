"""Run the real gamebuild.c scheduler with synthetic generator/compilation inputs.
Minimal host boundary headers isolate it from SDL/guest state. No game data.
The Android process branch is compiled on POSIX host, then separately with NDK.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Scheduler(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='scheduler-', dir=ROOT.parent)
        self.addCleanup(self.keep_evidence)
        self.root = Path(self.tmp.name)
        shutil.copy2(ROOT / 'src/runtime/gamebuild.c', self.root)
        shutil.copy2(ROOT / 'src/runtime/gamebuild.h', self.root)
        (self.root / 'gamelib.h').write_text('#define GAMELIB_ID "0123456789abcdef"\n#define GAMELIB_NAME "lasgame.so"\n')
        (self.root / 'rt.h').write_text('#define LOG(...) fprintf(stderr, __VA_ARGS__)\n')
        (self.root / 'paths.h').write_text('''#include <stdbool.h>
#define RT_APP_NAME "Test"
bool rt_resource(const char *, const char *, char *, size_t);
bool rt_dev_mode(void);
bool rt_remove_game_file(const char *, const char *);
''')
        (self.root / 'platform.h').write_text('''#include <stdbool.h>
#include <stdint.h>
#define PLAT_EXE_SUFFIX ""
#define PLAT_VENV_PYTHON "venv/bin/python"
typedef intptr_t PlatProc;
PlatProc plat_spawn(const char *const *, const char *, bool);
int plat_proc_wait(PlatProc, bool);
void plat_proc_kill(PlatProc);
uint64_t plat_now_ns(void);
void plat_sleep_until_ns(uint64_t);
''')
        (self.root / 'harness.c').write_text(r'''
#define _GNU_SOURCE
#include <time.h>
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
#include "gamebuild.c"
PlatProc plat_spawn(const char *const *a, const char *l, bool b) {
    (void)a; (void)l; (void)b; fputs("STUB_PLAT_SPAWN\n", stderr); _exit(91);
}
int plat_proc_wait(PlatProc p, bool block) {
    (void)p; (void)block; fputs("STUB_PLAT_WAIT\n", stderr); return 1;
}
void plat_proc_kill(PlatProc p) { (void)p; fputs("STUB_PLAT_KILL\n", stderr); }
uint64_t plat_now_ns(void) {
    struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
    return (uint64_t)t.tv_sec * 1000000000 + t.tv_nsec;
}
void plat_sleep_until_ns(uint64_t n) {
    struct timespec t = {n / 1000000000, n % 1000000000};
    clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &t, NULL);
}
bool rt_dev_mode(void) { return false; }
bool rt_resource(const char *name, const char *rel, char *out, size_t n) {
    (void)rel;
    const char *base = getenv("FIXTURE_TOOLS");
    snprintf(out, n, "%s/%s", base, strrchr(name, '/') + 1);
    return access(out, R_OK) == 0;
}
bool rt_remove_game_file(const char *dir, const char *name) {
    char path[2048]; snprintf(path, sizeof path, "%s/%s", dir, name);
    if (strcmp(name, ".gamebuild")) remove(path);
    return true;
}
int main(int argc, char **argv) {
    (void)argc;
    if (!strcmp(argv[1], "spawn")) {
        char *a[] = {"/bin/sh", "-c", "echo child-log; exit 7", NULL};
        PlatProc p = spawn(a, argv[2]);
        int r = p ? wait_for(p) : 255;
        printf("CHILD_EXIT=%d ACTIVE=%d\n", r, g_nprocs);
        return r == 7 && !g_nprocs ? 0 : 1;
    }
    if (!strcmp(argv[1], "cancel")) {
        char *a[] = {"/bin/sh", "-c", "sleep 30", NULL};
        PlatProc p = spawn(a, argv[2]);
        if (!p) return 2;
        stop(p);
        int s = 0;
        pid_t got = waitpid((pid_t)p, &s, WNOHANG);
        bool reaped = got == -1 && errno == ECHILD;
        printf("CANCEL_REAPED=%d ACTIVE=%d\n", reaped, g_nprocs);
        if (!reaped) { kill(-(pid_t)p, SIGKILL); waitpid((pid_t)p, NULL, 0); }
        return reaped && !g_nprocs ? 0 : 1;
    }
    GameBuildOpts o = {.jobs = 2}; char why[256] = {0};
    bool ok = gamebuild_run(argv[2], &o, why, sizeof why);
    printf("BUILD=%d WHY=%s\n", ok, why);
    return ok ? 0 : 1;
}
''')
        self.exe = self.root / 'harness'
        subprocess.run(['cc', '-D__ANDROID__', '-pthread', '-DGAMELIB_CFLAGS="-O1 -fPIC"',
                        str(self.root / 'harness.c'), '-o', str(self.exe)],
                       check=True, capture_output=True, text=True)

    def keep_evidence(self):
        # Failed generated source/intermediates are durable build diagnostics;
        # unlinking a synthetic run creates .fuse_hidden leftovers on TANK.
        out = ROOT / 'build/test-results'
        out.mkdir(parents=True, exist_ok=True)
        self.root.rename(out / self.root.name)
        self.tmp.cleanup()

    def prepare_pipeline(self, fail=False):
        tools = self.root / 'tools'
        tools.mkdir()
        (tools / 'cpu.h').write_text('/* synthetic */\n')
        (tools / 'hle_symbols.txt').write_text('# synthetic\n')
        (tools / 'gamelib_entry.c').write_text('int fixture_entry;\n')
        gen = tools / 'gen'
        gen.write_text('''#!/usr/bin/env python3
import pathlib, sys
module, nso, base, output = sys.argv[1:5]
p = pathlib.Path(output)
p.mkdir(parents=True, exist_ok=True)
(p / (module + '_000.c')).write_text('int fixture_' + module + ';\\n')
(p / (module + '_table.c')).write_text('int table_' + module + ';\\n')
print(module + ': progress 1/1 files 1', flush=True)
''')
        gen.chmod(0o700)
        cc = tools / 'cc'
        cc.write_text('#!/bin/sh\n' + ('exit 3\n' if fail else 'exec /usr/bin/cc "$@"\n'))
        cc.chmod(0o700)
        game = self.root / 'game'
        game.mkdir()
        (game / 'lasgame.so').write_bytes(b'previous-valid-library')
        self.env = {**os.environ, 'LARECOMP_GEN': str(gen), 'LARECOMP_CC': str(cc),
                    'FIXTURE_TOOLS': str(tools)}
        return game

    def test_synthetic_pipeline_generates_compiles_and_links(self):
        game = self.prepare_pipeline()
        r = subprocess.run([str(self.exe), 'build', str(game)], env=self.env,
                           capture_output=True, text=True, timeout=20)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertTrue((game / 'lasgame.so').read_bytes().startswith(b'\x7fELF'))
        self.assertEqual((game / 'lasgame.so.id').read_text(), '0123456789abcdef\n')

    def test_failed_build_preserves_installed_library(self):
        game = self.prepare_pipeline(fail=True)
        r = subprocess.run([str(self.exe), 'build', str(game)], env=self.env,
                           capture_output=True, text=True, timeout=20)
        self.assertNotEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual((game / 'lasgame.so').read_bytes(), b'previous-valid-library')

    def test_android_cancel_kills_and_reaps_child_without_platform_stub(self):
        log = self.root / 'cancel.log'
        r = subprocess.run([str(self.exe), 'cancel', str(log)], capture_output=True, text=True, timeout=5)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn('CANCEL_REAPED=1 ACTIVE=0', r.stdout)

    def test_android_exec_does_not_enter_platform_stub(self):
        log = self.root / 'child.log'
        r = subprocess.run([str(self.exe), 'spawn', str(log)], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn('CHILD_EXIT=7 ACTIVE=0', r.stdout)
        self.assertEqual(log.read_text(), 'child-log\n')


if __name__ == '__main__':
    unittest.main()
