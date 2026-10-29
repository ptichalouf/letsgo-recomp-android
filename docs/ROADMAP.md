# Roadmap — Let's Go Eevee, Android port

**State as of 29/10/2026.** This document describes **what is true today**, verified
on a device, and what is still open. It is not a wish list: a line that is not backed
by a measurement is not in here.

> For coding assistants, also read [`AGENTS.md`](../AGENTS.md).
> For the audit detail: [`ISSUES.md`](../ISSUES.md) and `docs/`.

---

## Verified and published

Reference branch: `publication`. Reference version: `adbf12dd`.

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
| Android software keyboard | **fixed**: typed text displays intact | UTF-16LE fix |
| Building the library on the phone | the phone produces `liblasgame.so` and launches the game | milestone A, Fold4 |

### Recently fixed (28–29/10)

- **Software keyboard**: typed text arrived corrupted (`汁???e`). The defect was in the
  **transport** (encoding), not in the game file. Fixed and verified on a real
  screen.
- **Resolution switch**: the World/Combat-Menus profile change landed ~14 s too late
  after a battle.
- **Background stop**: the engine kept rendering when not in the foreground
  (~10 400 frames in 6 min). Fixed with **three** stop points (presentation,
  `SleepThread`, host clocks) plus stopping the guest thread: **0.15 %** of a core
  instead of 67 %.
- **Black screen** after rotation or fold: Android destroys and recreates the Vulkan
  `Surface`; it must be reacquired and the image replayed.
- **Atomic publication**: on certified Android, `link()` is refused
  (`neverallow AOSP all_untrusted_apps file_type:file link`). The native manifest is
  published with `renameat2(RENAME_NOREPLACE)` and **fails closed**: a conflict never
  overwrites an existing generation.

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

### P6 — The second guest-heap SIGSEGV (Raitchi runtime)

**Localised to the line**: `engine/ports/shared-android/runtime/platform/android.c`,
in `plat_written_pages()`. The `mprotect(PROT_READ)` protects a page of the guest heap
that **has no physical page yet** (the reservation is `MAP_NORESERVE`, materialised on
demand): the page becomes unmapped for writing → `SEGV_MAPERR`, which the fault
handler cannot catch.

**Acceptance criterion**: the defect no longer reproduces on the reference device, and
the fix is upstreamable (no fork divergence).

**Method note**: establishing the root cause required a **trace that names the
caller** (`__builtin_return_address` + `dladdr`) and the **memory map of the process
alive at the moment of the crash** (`/proc/<pid>/maps`). A trace that does not say
*where* proves nothing.

### P7 — The `shaderInt64` fix, not merged

An existing branch (`fix/texto-invisivel-sem-int64`) fixes 64-bit shader arithmetic
when the device **lacks** the `shaderInt64` feature — which is the case for the system
Qualcomm driver. The SPIR-V then declares `OpCapability Int64` without the feature
existing: undefined behaviour, and the text vertex shaders produce no fragment.

**Why it is in the backlog**: the fix exists but is not rebased onto `publication`.
Rebase it, check that the GLSL **with** the feature stays identical byte for byte,
then open a pull request.

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
