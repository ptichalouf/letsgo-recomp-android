# AGENTS.md — instructions for coding agents

This repository is an **Android port of Pokémon Let's Go, Eevee** (static
recompilation, Switch → Android). This file is for coding assistants (Codex, Claude
Code, Cursor, Copilot, Aider, Zed…) **and for the humans who drive them**.

> `AGENTS.md` is read natively by most modern agents. If your tool does not read it,
> read it yourself and pass it in your prompt.

## The rule that never changes: nothing from Nintendo

**Never** put any of these into the repository, an issue, a pull request or a comment:

- the game in any form (`.xci`, `.nsp`, `.nca`, `.exefs`, `.romfs`…)
- keys (`prod.keys`, `title.keys`)
- extracted files (`extracted/`), generated code (`generated/`, `generated-aarch64/`)
- any game asset: textures, models, music, text, system fonts (`.bfttf`)
- saves (`save/`) and mods containing game files (`mods/`)

The repository **does not contain the game and does not build alone**: it needs the
user's own legally obtained dump. Screenshots and short videos are **welcome** in bug
reports.

If a task requires a game file, it **cannot** be done here — say so explicitly
instead of working around it.

## Before you start

1. **Look for what already exists**: `ISSUES.md` (known problems, including what has
   already been **eliminated by proof** — do not repeat a closed investigation),
   `docs/` (render, performance and text audits), and the open issues and pull
   requests.
2. **One pull request = one bounded problem.** Split refactorings from behaviour
   changes. Do not rewrite the renderer hot spots at the same time as someone else
   (`src/runtime/nvn/maxwell/translate.c`, `src/runtime/nvn/vulkan/exec.c`,
   `src/runtime/nvn/cmdbuf.c`, the texture cache) — comment the tracking issue first.
3. **Announce your scope** in an issue comment: expected files, dependencies, time or
   cost budget, next update. Independent tasks can proceed in parallel on separate
   branches.
4. **For a first contribution**: a documentation correction or a **game-free**
   synthetic test. That is the fastest path to acceptance.

## Layout at a glance

| Path | Role |
|---|---|
| `src/runtime/` | The engine: HLE, kernel, NVN→Vulkan, hooks. The core. |
| `src/runtime/nvn/maxwell/translate.c` | Maxwell→GLSL translator. Hot spot. |
| `src/runtime/nvn/vulkan/exec.c` | Vulkan submissions, buffer copies, sync. Hot spot. |
| `src/runtime/hle/` | HLE services (software keyboard, I/O…). |
| `src/runtime/platform/` | OS layer (Android, Linux, Windows, macOS). |
| `src/tools/` | Code generator, `gen.py`, `lifter.py`, debug tools. |
| `apk/java/com/letsgorecomp/` | Android launcher, control overlay, screens. |
| `apk/importer/`, `importer/` | On-device NSP/XCI import. |
| `tests/`, `apk/test/`, `src/tools/test_*.c` | Tests. |
| `docs/` | Audits and reports (often in Portuguese). |

The runtime comes from **LAS Recomp** (made for Zelda). Before blaming the game,
check that one of the runtime's assumptions holds for Let's Go (formats, controller
styles, return codes, reported resolution).

## Building

> A **full** build needs the user's personal dump: it cannot be reproduced by a
> contributor who does not own the game, nor by an AI. That is by design, not a bug.

What **can** be built and tested without the game or a dump:

```sh
# Build and packaging tests (SDK/NDK/javac required, synthetic fixtures)
python3 -m unittest discover -s tests -p test_build_apk.py -v

# The other synthetic tests at the root
python3 -m unittest discover -s tests -v

# APK-side tests (JVM, no device)
python3 -m unittest discover -s apk/test -p 'test_*.py' -v
```

```sh
# C runtime tests (host, no game) — see src/tools/teste60.sh
bash src/tools/teste60.sh
```

**Toolchain** (JDK 17, Android SDK, NDK r28c or newer):

```sh
source tools/env.sh    # prints what it found; sets variables, installs nothing
```

The rest of the build is described in [`BUILD.md`](BUILD.md) (Portuguese) and
[`docs/HOWTO-BUILD.md`](docs/HOWTO-BUILD.md).

**Environment**: `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME` (NDK r27c),
`ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES` for 16 KiB devices.

## Style

- **C**: 4 spaces, braces on the same line, no tabs. Comments explain **why**, not
  **what**.
- **Java**: same. The launcher uses `Txt.t("key", "fallback")` for every string —
  **every** user-visible string goes through it, and a locale test checks it.
- **Python**: 4 spaces, `unittest` (not pytest) to stay dependency-free.
- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/), in
  **English**, imperative, lowercase, no trailing period:
  `feat(launcher): …`, `fix(android): …`, `docs: …`, `perf: …`, `test: …`.
- **Line endings**: LF everywhere except `.bat` and `.ps1`. A CRLF inside a bash
  script breaks the script — that is a bug, not a style detail.

## Tests: what an agent has to prove

- A code fix **comes with** a reproducible test whenever that is practical.
- The test must be **synthetic**: never a game file, never an APK signed with a real
  key.
- Report **the exact command, its exit status, its duration, and its limits**. A
  missing tool is a *skip* or a *failure*, never a substitute success.
- Never copy a **predicted** result into the evidence: paste the real output.

## What you may not do

- **Do not merge your own pull request.** Open it as a draft; a maintainer decides.
- **Do not touch** game files, keys, saves, or mods containing game files.
- **Do not rewrite** a renderer hot spot without announcing it in the issue.
- **Do not claim** a feature exists because it is documented as *proposed*: read
  `docs/ROADMAP.md` and check the real state in the code.
- **Do not rewrite** Git history on the shared branches (`publication`, `main`).

## What a pull request must contain

1. **The problem**: symptom, version, scene, reproduction steps.
2. **The evidence**: before / after, with the exact command and its exit status.
3. **The fix**: minimal, and why this approach rather than another.
4. **The limits**: what you did **not** verify (device, other GPU, other version).

Describe assistance plainly, in the pull request, without naming the model or the
tool. **Assistance is not independent approval** — the review stays human.

## Repository guard rails

- **16 KiB**: recent devices require 16 KiB-aligned `.so` files. The Android build
  sets `-Wl,-z,max-page-size=16384` and the packaging step **refuses** a
  non-conforming library. Do not remove that guard.
- **Android software keyboard**: the typed text travels IME → JNI → applet → buffer.
  A wrong encoding (UTF-8 instead of UTF-16LE) corrupts what is displayed.
- **GPU writes**: without `VK_EXT_external_memory_host` (Adreno, system Qualcomm
  driver) the guest heap is never imported by the GPU. Buffer→buffer copies must
  wait for the GPU **only** when the memory is genuinely imported.
- **Background**: the engine must stop when it is not in the foreground
  (presentation, `SleepThread`, host clocks, guest thread). A background run burns a
  whole core.
- **Vulkan surface**: after a rotation or a fold, Android destroys and recreates the
  `Surface` — it must be reacquired and the image replayed, or the screen stays
  black.
- **No game content**: `tools/check_no_game_content.py` refuses game data, keys and
  signing material. `tools/check_no_secrets.py` refuses secrets.

## License

MIT for the port, plus the third-party licenses (LAS Recomp, imgui, FFmpeg, SDL3,
Vulkan-ValidationLayers…). See `LICENSE`, `LICENSE-LASRecomp`, `LICENSES/` and
`THIRD_PARTY_NOTICES.md`. **Every contribution must stay compatible with those
licenses** and preserve their notices.

See also: [`CONTRIBUTING.md`](CONTRIBUTING.md) (human version),
[`docs/ROADMAP.md`](docs/ROADMAP.md) (real state and priorities) and
[`docs/INDEX-FOR-LLMS.md`](docs/INDEX-FOR-LLMS.md) (a map of the repository).
