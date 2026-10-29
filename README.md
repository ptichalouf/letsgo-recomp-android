# Let's Go Eevee · Android

[![CI](https://github.com/ptichalouf/letsgo-recomp-android/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/ptichalouf/letsgo-recomp-android/actions/workflows/ci.yml)

An experimental, unofficial Android port of **Pokémon: Let's Go, Eevee!**, based on
static recompilation. This fork brings together the runtime, Android application,
tests, and documentation so the community can continue the work.

**This is not a general-purpose emulator or a ready-to-play APK.** A full build
requires your own legally obtained copy of the game and your own keys. Do not
request or upload those files here.

[Status and next steps](docs/ROADMAP.md) · [Build](BUILD.md) ·
[Contribute](CONTRIBUTING.md) · [Guide for AI coding assistants](AGENTS.md) ·
[Repository index for LLMs](docs/INDEX-FOR-LLMS.md)

## Automated setup with LLMs · AC-Création

The setup of this fork and its contribution workflow was automated using
AI assistants (LLMs) by **[AC-Création — ac-creation.com](https://ac-creation.com)**:
documentation, instructions for coding assistants, environment preparation, tests,
and automated contribution checks.

Coding assistants can read the instructions in [`AGENTS.md`](AGENTS.md), locate
components through the [repository index](docs/INDEX-FOR-LLMS.md), and propose a PR
with reproducible commands and results. If a tool does not load `AGENTS.md`
automatically, provide the file as context.

**Automation is not proof that something works.** AI assistance must be disclosed;
each fix must include tests, a review, and the limits of its verification.
Approving a PR is not the same as testing the game on a phone.

## Fork status · October 29, 2026

| Area | What is established |
|---|---|
| Game execution | The upstream project documents boot, the 3D world, battles, menus, and saves on the Galaxy S23. The screenshots below come from the historical v0090 build, not a validation of the current version. |
| Android application | This fork includes code for the importer, controls, mod installation and ordering, resolution settings, and World / Combat-Menus profiles. |
| Performance | PRs [#2](https://github.com/ptichalouf/letsgo-recomp-android/pull/2) and [#3](https://github.com/ptichalouf/letsgo-recomp-android/pull/3) are merged: buffer copies, background suspension, and rendering-profile regressions. CI does not measure FPS. |
| Shader compatibility | The fallback for GPUs without `shaderInt64` is merged in [PR #5](https://github.com/ptichalouf/letsgo-recomp-android/pull/5), with synthetic regression tests. Text readability on a phone still needs confirmation. |
| APK packaging | [PR #4](https://github.com/ptichalouf/letsgo-recomp-android/pull/4) is merged: ZIP failures, a missing DEX, and missing required native libraries are rejected before the output file is published. |
| Automated tests | CI reference after #4: **57 root-level tests, with 6 explicitly skipped, and 8 application-side tests**, with no failures. These do not include running the game on a device. |

**Invisible text remains an issue that needs device verification.** The shader
fallback is merged, but a host test does not prove the visual result.
[`ISSUES.md`](ISSUES.md) preserves the historical investigations;
[`docs/ROADMAP.md`](docs/ROADMAP.md) separates merged work from pending validation.

This repository contains the **statically recompiled port**. Raitchi is a separate
project with a different runtime: results, defects, and fixes in one do not
automatically validate the other.

## Getting started

### Help without owning the game

You can contribute documentation, synthetic tests, or isolated fixes. The tests use
small libraries and dummy files, without game code or game data.

On Linux x86_64, the CI environment uses:

- Python 3, C/C++ compilers, `make`, `zip`, and `unzip`;
- JDK 17;
- Android SDK: platform `android-34` and build-tools `35.0.0`;
- Android NDK `28.2.13676358`.

```bash
# From the root of a full Git clone, with the toolchain already installed:
source tools/env.sh
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s apk/test -p 'test_*.py' -v

# Repository content and documentation checks:
python3 tools/check_no_game_content.py
python3 tools/check_no_secrets.py --history
python3 tools/check_line_endings.py
python3 tools/check_doc_links.py
```

`tools/env.sh` locates installed tools and sets environment variables; **it does not
install or download anything**. See the [CI workflow](.github/workflows/ci.yml) for
the reproducible environment. Some tests need Git history to compare the code before
and after a fix: use a full clone, not just the repository ZIP.

The SDL wait tests, private-buildkit test, and SPIR-V validation test are skipped
when their specific prerequisites are missing. A skipped test is not a passed test;
each run reports what was left unverified.

### Build for personal use

Follow the [build guide](BUILD.md), using your own dump, your own keys, and the
required Android dependencies. The guide covers the runtime, importer, game
library, and APK build steps.

`GAME=none ./build-apk.sh` packages the application without the game library;
`GAME=with ./build-apk.sh` requires a compatible, privately built game library.
An APK containing code or data derived from the game **must not be published**.

## Contribute with or without AI

1. Read the [roadmap](docs/ROADMAP.md) and [known issues](ISSUES.md).
2. Open an [issue](https://github.com/ptichalouf/letsgo-recomp-android/issues) to agree
   on the scope, especially if you plan to change the renderer.
3. Work on a branch based on `publication`: **one problem per PR**.
4. Include reproduction steps, before/after tests, and limitations. Do not present
   predicted results as measurements.
5. Follow [`CONTRIBUTING.md`](CONTRIBUTING.md) and, for AI-assisted work,
   [`AGENTS.md`](AGENTS.md) and the [AI contribution policy](.github/AI_CONTRIBUTING.md).

The `master` and `publication` branches are protected: changes must go through a PR
with both CI checks passing and an up-to-date base. Direct pushes, branch deletion,
and history rewrites are blocked. The protection grants no owner exception.

## Repository contents

| Path | Contents |
|---|---|
| `src/runtime/` | HLE runtime, NVN→Vulkan translation, and platform integration. |
| `src/tools/` | Code-generation and diagnostic tools. |
| `apk/` | Android application, launcher, and controls. |
| `importer/` | Import of the user's own dump. |
| `tests/`, `apk/test/` | Synthetic tests and JVM tests. |
| `tools/` | Environment detection and repository checks. |
| `docs/` | Roadmap, audits, reports, and navigation index. |

**Never upload** copies of the game (`.xci`, `.nsp`, `.nca`, `.exefs`, `.romfs`…), keys
(`prod.keys`, `title.keys`), extracted files, generated code, Switch system fonts
(`.bfttf`), textures, models, music, game text, saves, or mods containing game files.
This also applies to issues, comments, and PRs.

## Historical screenshots · Galaxy S23, v0090

<details>
<summary>View upstream screenshots taken before the current fixes</summary>

These images record the older state, including dialogues and menus without text.
The FPS numbers in these screenshots are not a benchmark of the current version.

| World and dialogue | Battle |
|---|---|
| ![Historical world screenshot with a dialogue missing its text](screenshots/01-mundo-casa-dialogo.jpg) | ![Historical battle screenshot](screenshots/06-batalha.jpg) |

More screenshots are available in [`screenshots/`](screenshots/).

</details>

## Origins, credits, and licenses

This fork continues the work of
[CowKillers / letsgo-recomp-android](https://github.com/CowKillers/letsgo-recomp-android).
The original authors' credits and licenses are preserved.

- [raulzrrs / letsgo-recomp](https://github.com/raulzrrs/letsgo-recomp) — original static recompilation.
- [peiikai / LASRecomp](https://github.com/peiikai/LASRecomp) — runtime foundation.
- [Nycolazs / LASRecomp-Android-Plus](https://github.com/Nycolazs/LASRecomp-Android-Plus) — Android port reference.
- Channel linked by the upstream project: [emuucheck](https://youtube.com/@emuucheck).

See [`LICENSE`](LICENSE), [`LICENSE-LASRecomp`](LICENSE-LASRecomp),
[`LICENSES/`](LICENSES/), and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
The AC-Création attribution refers to work on this fork, not authorship of the game
or the entire original runtime.

This is an unofficial project with no affiliation with Nintendo, The Pokémon Company,
or Game Freak. All trademarks belong to their respective owners.
