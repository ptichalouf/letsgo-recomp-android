# Roadmap — Let's Go Eevee, Android port

**State as of 29/10/2026.** This roadmap is for the static-recompiled Android
port in this repository. It separates historical device evidence, merged changes
with host tests, and work still requiring device verification. Raitchi is a
separate runtime: its results do not validate this port.

> For coding assistants, also read [`AGENTS.md`](../AGENTS.md).
> For the audit detail: [`ISSUES.md`](../ISSUES.md) and `docs/`.

---

## Published baseline and merged changes

Reference branch: `publication`. The original baseline was `adbf12dd`;
PRs #2, #3, #5 and #6 were subsequently merged, followed by APK guard PR #4
at `1b435ec8`. Device observations below are not a fresh qualification of that
combined build. The repository CI after #4 reports 57 root tests (6 explicit
skips) and 8 APK-side tests, with no failures.

| Area | State | Evidence |
|---|---|---|
| Boot, 3D world, map, battles, menus | working | Galaxy S23 (Snapdragon 8 Gen 2, Adreno 740) |
| On-device NSP/XCI import | working | `apk/importer/`, `importer/` — exefs/romfs extracted on the phone |
| Controls | touch + physical gamepad | `TouchControls`, `GamepadOverlay` |
| Resolution | 480p → 1080p, correct 16:9 ratio | `settings.c` |
| Mods | install, several at once, **user-chosen order**, conflicts shown | `src/runtime/mods.c`, `apk/java/.../ModsPanel.java` |
| Render profiles World / Combat-Menus | automatic per-scene switch | `src/runtime/nvn/output_scale.c` |
| Event loop | no longer wakes itself up | SDL sentinel fix |
| Black screen after rotation / fold | surface reacquisition + guard | `src/runtime/platform/android.c`, `device.c` |
| Android software keyboard | UTF-16 output path is present; this combined build still needs device qualification | `src/runtime/hle/swkbd.c`; do not substitute Raitchi's keyboard results |
| Building the library on the phone | an extracted-directory milestone was observed on Fold4; full NSP/XCI-to-library flow is not established | milestone A, not a complete end-user build recipe |

### Recently fixed (28–29/10)

- **Software keyboard**: this fork contains a UTF-16 output path. The separate
  Raitchi keyboard fix and its device results must not be reported as a fresh test
  of this port.
- **Resolution switch**: the World/Combat-Menus profile change landed ~14 s too late
  after a battle.
- **Background stop**: the engine kept rendering when not in the foreground
  (~10 400 frames in 6 min). Fixed with **three** stop points (presentation,
  `SleepThread`, host clocks) plus stopping the guest thread: **0.15 %** of a core
  instead of 67 %.
- **Black screen** after rotation or fold: Android destroys and recreates the Vulkan
  `Surface`; it must be reacquired and the image replayed.
- **APK archive guards (#4)**: failed ZIP commands, integrity-check failures,
  missing `classes.dex`, and missing required native libraries are rejected by
  `build-apk.sh`. Five packaging regression tests exercise these guards.
- **shaderInt64 fallback (#5)**: integrated and host-tested; see P7 for the device
  validation that is still required.

---

## Open — the real work remaining

Each line is a **bounded problem** with an acceptance criterion. This is the queue of
welcome contributions.

### P1 — Invisible text (the main one)

**Symptom**: no text at all in menus, dialogues, selection boxes or HUD. Dialogue boxes
open empty. The rest of the game works.

**Already eliminated by proof** (do not redo): culling, depth test, `write_mask`,
scissor/viewport, stale pipeline, truncated CB0, unbound `a0` attribute, `cb3`
matrix — see the full table in `ISSUES.md`.

**Current lead**: the dialogue produces **zero fragments**; the menu does. The
dialogue's vertex shader reads global memory (`gmem`/`gfind`); the menu's uses only
attributes and a matrix. The system Adreno driver **does not support**
`VK_EXT_external_memory_host`: the guest heap is never imported by the GPU. The
`gcopy` fallback therefore copies CPU ranges to the GPU before each draw.

**Acceptance criterion**: readable text in menus **and** dialogues, on at least one
Adreno device, with the command and exit status of the measurement in the pull
request.

**Pitfalls**: the Turnip driver **is** the path that supports host import, but it is
slower on this game (18–22 fps against 30) and its own activation is still open
(see P2). Do not confuse the two problems.

### P2 — The Turnip driver does not take over

**Symptom**: `adrenotools` finds the file (`turnip-8g2`), but the active GPU is still
the system Adreno driver.

**Hypothesis**: the `linkernsbypass` fails on Android 16.

**Acceptance criterion**: the loaded driver is **proven** (visible load chain) and its
effect on fps is **measured** — not assumed.

**Note**: the user's preference is that Turnip works, even if slower. That preference
outranks a performance gain.

### P3 — Performance: per-scene resolution profile

1080p tops out at **19–20 fps** against **25–30 fps** at 720p, for equal per-frame
work. The port now applies 720p by default in combat and switches automatically per
scene (World / Combat-Menus profile).

**Left to do**: measure the real cost of the switch (a measured transition, not an
assumption) and check that no scene stays stuck at 1080p.

### P4 — Shadows blocked in Viridian Forest

Partially handled: shadow samplers become LINEAR (PCF bilinear, 4 hardware taps). The
visual result is **not confirmed**. `LARECOMP_NO_SHADOW` exists (forces
`VK_COMPARE_OP_ALWAYS`) but total shadow removal is **not** implemented.

**Acceptance criterion**: a dated before/after capture, and an explicit decision on
the default mode.

### P5 — Performance in the forest

Only **~13–14 fps** in Viridian Forest at 720p, with ~17 000 vegetation draws.
`vk_wait_seq()` is a real GPU wait; uploads are proportional to the draws.

**Out of scope without profiling first**: instancing, merging. A "performance fix" that
adds a wait or degrades quality is not a fix.

### P6 — Reserved: Raitchi investigation removed from this port's backlog

The previous entry concerned a guest-heap crash in **Raitchi**, not this repository.
It did not establish a defect in this port. It has been removed to avoid sending
contributors to files and results from another runtime. A new issue here needs a
reproduction against this repository's exact commit.

### P7 — `shaderInt64` fallback merged; device verification still open

[PR #5](https://github.com/ptichalouf/letsgo-recomp-android/pull/5) rebased and
integrated the fallback. Host regression tests compare GLSL with the feature
against the baseline, exercise paired 32-bit arithmetic without it, and validate
SPIR-V when the required tools are installed. See
[`tests/README-shader-int64.md`](../tests/README-shader-int64.md).

**Still required**: readable menu and dialogue text on a real Adreno device running
this combined build. Do not equate the synthetic test with a rendering result.
No new rebase or duplicate implementation of the already merged fix is needed.

### P8 — 16 KiB alignment

Recent devices require 16 KiB-aligned `.so` files. The build sets
`-Wl,-z,max-page-size=16384` and the packaging **refuses** a non-conforming library.

**Left to do**: qualification on a real 16 KiB device. Build flags are not proof.

---

## Explicitly **out of scope**

- **Distributing the game**: the repository does not contain it and never will.
- **Multiple games**: the launcher is designed to isolate several titles, but that is
  not an acquired capability. The on-device recipe is missing.
- **Executable mods / cheats**: distinct from asset replacement. They need a separate
  contract; do not infer "mods" from a plain file replacement.
- **ROMM / Arcade support**: not started, out of mandate.

---

## How to pick a line

1. Look for what exists: `ISSUES.md`, the open issues, the open pull requests.
2. Comment the tracking issue: scope, files, dependencies, next update.
3. One branch per problem, based on an up-to-date `publication`.
4. A synthetic test where possible; the exact command and its exit status in the pull
   request.
5. Open it **as a draft**. A maintainer merges.

If a contribution needs a game file, it **cannot** be verified here — say so, do not
work around it.
