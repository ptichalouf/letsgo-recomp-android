"""Exercise the actual ui_run with SDL3 (Android wait bridge for host tests).

SDL3_TEST_ROOT names a native SDL build with the Android event path, see the
card's reproduction/provenance. No GPU, phone, or game data is needed.
"""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SDL = os.environ.get('SDL3_TEST_ROOT')
HARNESS = r'''
#include <SDL3/SDL.h>
#include <assert.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#define LOG(...) fprintf(stderr, __VA_ARGS__)
static atomic_bool ui_ready;
static SDL_Window *window;
static Uint64 deadline;
static atomic_int callback_count;
static atomic_int main_callbacks;
static int mode, received;
static void main_callback(void *p) { (void)p; ++main_callbacks; }
static int worker(void *p) {
    (void)p;
    for (int i = 0; i < 30; ++i) {
        assert(SDL_RunOnMainThread(main_callback, NULL, true));
        SDL_Delay(1);
    }
    return 0;
}
static const Uint32 types[] = {SDL_EVENT_KEY_DOWN, SDL_EVENT_KEY_UP,
    SDL_EVENT_FINGER_DOWN, SDL_EVENT_FINGER_UP, SDL_EVENT_GAMEPAD_ADDED,
    SDL_EVENT_GAMEPAD_REMOVED, SDL_EVENT_GAMEPAD_BUTTON_DOWN,
    SDL_EVENT_GAMEPAD_BUTTON_UP, SDL_EVENT_USER};
static void ui_window_init(void) {}
static int rt_reboot_requested(void) { return SDL_GetTicksNS() >= deadline ? 1 : 0; }
static bool input_user_blocked(SDL_Event *e) { (void)e; return false; }
static bool menu_handle_event(SDL_Event *e) { (void)e; return false; }
static void input_handle_event(SDL_Event *e) {
    assert(e->type == types[received % SDL_arraysize(types)]);
    ++received;
}
static void menu_apply_window_requests(SDL_Window *w) { (void)w; }
static Uint32 tick(void *data, SDL_TimerID id, Uint32 interval) {
    (void)data; (void)id;
    SDL_Event e = {0}; e.type = types[callback_count % SDL_arraysize(types)];
    assert(SDL_PushEvent(&e)); ++callback_count;
    return callback_count == 90 ? 0 : interval;
}
/* Native SDL library with only its Android event path selected. */
#define __ANDROID__ 1
/* ACTUAL_UI_RUN */
static double cpu_ms(void) {
    struct timespec t; clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1e6;
}
int main(int argc, char **argv) {
    mode = argc > 1 ? atoi(argv[1]) : 0;
    setvbuf(stdout, NULL, _IONBF, 0);
    assert(SDL_Init(SDL_INIT_EVENTS));
    SDL_SetEventEnabled(SDL_EVENT_POLL_SENTINEL, mode != 2);
    if (mode == 1) assert(SDL_AddTimer(1, tick, NULL));
    SDL_Thread *thread = mode == 3 ? SDL_CreateThread(worker, "producer", NULL) : NULL;
    if (mode == 3) assert(thread);
    deadline = SDL_GetTicksNS() + 300000000;
    double before = cpu_ms(); Uint64 wall = SDL_GetTicksNS();
    assert(ui_run() == 1);
    double used = cpu_ms() - before;
    double elapsed = (SDL_GetTicksNS() - wall) / 1e6;
    printf("mode=%d cpu_ms=%.3f wall_ms=%.3f received=%d callbacks=%d sentinel=%d\n",
        mode, used, elapsed, received, callback_count, SDL_EventEnabled(SDL_EVENT_POLL_SENTINEL));
    assert(SDL_EventEnabled(SDL_EVENT_POLL_SENTINEL) == (mode != 2));
    assert(elapsed >= 300 && elapsed < 1000);
    assert(used < elapsed * 0.20);
    if (mode == 1) assert(received == 90 && callback_count == 90);
    if (mode == 3) { assert(main_callbacks == 30); SDL_WaitThread(thread, NULL); }
    /* Restored sentinel must still terminate ordinary poll cycles after reboot. */
    SDL_Event e; int count = 0;
    while (SDL_PollEvent(&e)) assert(++count < 10);
    SDL_Quit(); return 0;
}
'''

@unittest.skipUnless(SDL, 'set SDL3_TEST_ROOT to the native Android-wait SDL build')
class EventWaitTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.tmp.cleanup)
        p = Path(cls.tmp.name)
        source_path = Path(os.environ.get('EVENT_WAIT_SOURCE',
                          str(ROOT / 'src/runtime/nvn/vulkan/device.c')))
        source = source_path.read_text()
        actual = source[source.index('int ui_run(void) {'):]
        (p / 'probe.c').write_text(HARNESS.replace('/* ACTUAL_UI_RUN */', actual))
        cls.exe = p / 'probe'
        assert SDL is not None
        sdl = Path(SDL)
        subprocess.run(['cc', '-std=c11', '-D_POSIX_C_SOURCE=200809L',
            '-Wall', '-Wextra', '-Werror', '-I' + str(sdl / 'include'),
            str(p / 'probe.c'), '-L' + str(sdl / 'build'),
            '-Wl,-rpath,' + str(sdl / 'build'), '-lSDL3', '-o', str(cls.exe)], check=True)

    def run_case(self, mode):
        result = subprocess.run([str(self.exe), str(mode)], capture_output=True,
                                text=True, timeout=5)
        print(result.stdout, end='')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_idle_wait_sleeps(self):
        self.run_case(0)

    def test_timer_wakes_and_input_events_are_not_lost(self):
        self.run_case(1)

    def test_cross_thread_main_callbacks_wake_waiter(self):
        self.run_case(3)

    def test_previously_disabled_sentinel_stays_disabled(self):
        self.run_case(2)

if __name__ == '__main__':
    unittest.main()
