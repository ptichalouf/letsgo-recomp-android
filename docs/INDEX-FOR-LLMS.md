# Index for coding assistants

This file is an **orientation table**: it says where to find what, without repeating
the content. For the rules, the commands and the boundaries, read
[`AGENTS.md`](../AGENTS.md) — that is the file agents load natively.

## Map of the repository

| I am looking for… | It is here |
|---|---|
| What the project is | [`README.md`](../README.md) |
| The rules for an agent | [`AGENTS.md`](../AGENTS.md) |
| The rules for a human | [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
| What works and what is left | [`docs/ROADMAP.md`](ROADMAP.md) |
| Known problems and what is ruled out | [`ISSUES.md`](../ISSUES.md) |
| How to build | [`BUILD.md`](../BUILD.md), [`docs/HOWTO-BUILD.md`](HOWTO-BUILD.md) |
| Render audits | [`TEXT_RENDER_AUDIT.md`](TEXT_RENDER_AUDIT.md), [`TEXT_RENDER_DEEP_REVIEW.md`](TEXT_RENDER_DEEP_REVIEW.md) |
| The graphics audit | [`AUDITORIA-GRAFICA-COMPLETA.md`](AUDITORIA-GRAFICA-COMPLETA.md) |
| Performance | [`PERF_AUDIT.md`](PERF_AUDIT.md), [`RESOLUTION_SCALING.md`](RESOLUTION_SCALING.md) |
| Mods | [`MODS.md`](MODS.md) |
| The merge / publication circuit | [`CIRCUIT-FUSION.md`](CIRCUIT-FUSION.md) |
| Image formats | [`FORMATS_IMAGE.md`](FORMATS_IMAGE.md) |
| Licenses and third parties | [`LICENSE`](../LICENSE), [`LICENSE-LASRecomp`](../LICENSE-LASRecomp), [`LICENSES/`](../LICENSES/), [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) |

## The engine, by area

| Area | Files | Nature |
|---|---|---|
| Main loop, dispatch | `src/runtime/dispatch.c`, `main.c` | core |
| HLE (keyboard, services) | `src/runtime/hle/` | editable, low contention |
| Kernel, guest threads | `src/runtime/kernel.c`, `lifecycle.c` | **hot spot** |
| Maxwell→GLSL translator | `src/runtime/nvn/maxwell/translate.c` | **hot spot, conflict prone** |
| Vulkan (submissions, copies) | `src/runtime/nvn/vulkan/exec.c` | **hot spot, conflict prone** |
| Texture cache | `src/runtime/nvn/vulkan/textures.c` | **hot spot, conflict prone** |
| OS layer | `src/runtime/platform/android.c`, `linux.c`, `windows.c` | per platform |
| Code generator | `src/tools/gen.py`, `lifter.py` | outside the runtime |

## The Android application

| Area | Files |
|---|---|
| Launcher (home, settings) | `apk/java/com/letsgorecomp/LetsGoLauncherActivity.java` |
| Game activity | `apk/java/com/letsgorecomp/LetsGoActivity.java` |
| Touch / gamepad controls | `TouchControls.java`, `GamepadOverlay.java` |
| Mods | `ModsPanel.java`, `ModStore.java`, `ModUnpack.java` |
| GPU drivers | `GpuDrivers.java`, `DriverCatalog.java` |
| Strings | `Txt.java` + `apk/res/values*/strings.xml` |
| On-device import | `apk/importer/`, `importer/` |

## The tests

| File | What it checks | Needs |
|---|---|---|
| `tests/test_build_apk.py` | APK packaging chain | SDK + NDK |
| `tests/test_build_gamelib.py` | game library build | NDK |
| `tests/test_event_wait.py` | event loop | host |
| `tests/test_surface_recovery.py` | surface recovery after rotation | host |
| `tests/test_display_stall_injection.py` | display stall injection | host |
| `tests/test_gamebuild.py`, `test_gamebuilder_jni.py` | game construction | host |
| `tests/test_ondevice.py` | on-device preparation | host |
| `tests/test_stage_buildkit.py` | buildkit staging | host |
| `apk/test/test_locale.py` | string consistency | host |
| `apk/test/test_theme.py` | theme | host |
| `apk/test/*.java` | JVM tests | JDK |
| `src/tools/test_*.c` | runtime (mods, output scale, profiles) | C compiler |

General commands:

```sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s apk/test -p 'test_*.py' -v
```

## The build scripts

| Script | Role |
|---|---|
| `build-runtime.sh` | compiles the runtime library |
| `link-runtime.sh` | links the runtime |
| `build-gamelib.sh` | compiles the game library (**needs the dump**) |
| `build-importer.sh` | compiles the NSP/XCI importer |
| `build-modunpack.sh` | compiles the mod unpacker |
| `build-apk.sh` | packages the Android APK |
| `tools/stage-buildkit.py` | stages the buildkit |

## The repository guards

These rules are **deliberate**: do not work around them, they protect against real
regressions.

| Guard | Where | Why |
|---|---|---|
| No game content | `tools/check_no_game_content.py` | the repository must never hold the game |
| No secrets | `tools/check_no_secrets.py` | no private key, token or console key |
| LF except `.bat`/`.ps1` | `.gitattributes`, `tools/check_line_endings.py` | a CRLF breaks bash scripts |
| 16 KiB alignment | `package_shared_android.py` | refuses to package a non-conforming `.so` |
| Final ZIP/DEX/libs | `build-apk.sh` | refuses an incomplete archive |
| Keyboard in UTF-16LE | `src/runtime/hle/swkbd.c` | a wrong encoding corrupts the text |
| Conditional GPU wait | `src/runtime/nvn/vulkan/exec.c` | the heap is not imported on Adreno |

## What an agent should do before proposing anything

1. Read `AGENTS.md` and `docs/ROADMAP.md`.
2. Check `ISSUES.md` — the subject may already be ruled out.
3. Check the real state in the code: something listed in the roadmap is **not**
   implemented.
4. Write a synthetic test if possible.
5. Report the exact command, its exit status and the limits.

## How to contribute

See [`CONTRIBUTING.md`](../CONTRIBUTING.md). In short: one branch per problem, a
synthetic test, a **draft** pull request, and a maintainer merges.

Never post a game file, a key, a save or a game asset — not in the repository, not in
an issue, not in a pull request.
