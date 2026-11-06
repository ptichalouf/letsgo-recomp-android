# AGENTS.md - instructions for coding agents

This repository is an **Android port of Pokémon Let's Go, Eevee** (static recompilation, Switch → Android),
an autonomous fork maintained by [AC-Création](https://ac-creation.com) (credits: CowKillers, raulzito12345rrs,
Peiikai; see `README.md`). This file is for coding agents (Claude Code, Hermes Agent, Codex, Cursor, Copilot,
Aider...) **and for the people who drive them**. Most agents read `AGENTS.md` natively; if yours does not, paste
it into your prompt. Humans: start with [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Red lines (never, no exception)

Never put any of this into the repo, a PR, an issue, a comment, a log or a CI artifact:

- the game in any form: `.xci .nsp .nsz .xcz .nca .tik .exefs .romfs`, `extracted/`, `romfs/`, `exefs/`
- keys: `prod.keys`, `title.keys`, any `*.keys`; signing material (`.jks .keystore .p12 .pem`)
- generated code (`generated/`, `generated-aarch64/`), game assets (textures, models, music, text, system fonts
  `.bfttf`), saves (`save/`), mods containing game files (`mods/`), shader dumps, any file bigger than 50 MB

Users bring their own dump. If a task needs a game file, it cannot be done here: say so instead of working
around it. Screenshots and short videos are welcome in bug reports. CI enforces this with
`tools/check_no_game_content.py` (`.github/workflows/guard.yml`) and `tools/check_letsgo_content.py`
(`ci.yml`); do not weaken them. `tools/check_no_secrets.py` refuses secrets.

Also:

- **No PR, issue or push to upstreams** (`CowKillers/letsgo-recomp-android`, `Nycolazs/*`). This is an autonomous fork.
- **Licences**: MIT for the port, plus third-party licences (LAS Recomp, imgui, FFmpeg, SDL3, ...) kept verbatim in
  `LICENSE`, `LICENSE-LASRecomp`, `LICENSES/`, `THIRD_PARTY_NOTICES.md`. Every contribution must stay compatible.
- **Do not merge your own PR.** Open it as a draft; a different identity reviews it. `master` requires a PR, green
  CI and linear history (squash or rebase).
- **Never claim "tested on device"** unless a human or a named device tester gave you a device report (device,
  SoC/GPU, driver, build SHA). Passing CI is not a device test.
- **Never publish a release, tag, or set `LETSGO_RELEASE_APPROVED`.** That is the owner's decision.

## AI disclosure (required)

AI work is disclosed, never disguised. Every commit and PR written by an agent says who:

```
Agent: <agent name> · Model: <model id>
```

Put that line in the PR description (the template has a field) and as the last line of the commit message body.
Example: `Agent: Hermes · Model: claude-sonnet-5-5`. Assistance is not independent approval: review stays human.

## Four ways to contribute

1. **Report an issue** (no build tools): skill `report-device-issue` or the Bug report form.
2. **Fix a bug**: claim the issue, branch from `master`, synthetic test, draft PR with evidence.
3. **Add a feature**: Feature request issue first, agree the scope (renderer hot spots especially), then as 2.
4. **Help performance**: skill `perf-report` or the Performance report form; same scene, same protocol.

Skill `contribute-letsgo` walks through paths 2 and 3.

## Tools to install

Check what you have (installs nothing):

```sh
tools/setup.sh --check
```

One line per tool: `OK <tool>`, `MISSING <tool> -- <how to install>` or `WRONG-VERSION <tool>`. Exit 0 means the
toolchain is complete. Without `--check` it installs only the Android SDK/NDK pieces through `sdkmanager`
(needs `--accept-android-licenses`); system tools are never installed silently. `source tools/env.sh` sets the
variables for the Android tests.

| Tool | Version | Needed for |
|---|---|---|
| Python | 3.11+ (CI builds with 3.14) | tests, tools |
| git | recent | everything |
| JDK | 17 | Android build, APK tests |
| Android SDK | platform `android-34`, build-tools `35.0.0` | APK packaging |
| Android NDK | `28.2.13676358` | native build |
| CMake + Ninja | recent | native dependencies |

Linux and macOS; Windows through **WSL2** only (`tools/setup.ps1` explains). A full build also needs your own game
dump and cannot be done by an AI or a contributor without the game: that is by design.

## Skills to install for your agent

The folder [`skills/`](skills/) holds task skills in the standard `SKILL.md` format (`name`, `description`):

| Skill | Use it to |
|---|---|
| `contribute-letsgo` | go from idea to a reviewable PR (this repo's build and CI facts) |
| `report-device-issue` | file a reproducible bug (no build tools needed) |
| `perf-report` | produce a comparable performance report (no build tools needed) |

- **Claude Code**: `mkdir -p .claude && cp -r skills .claude/skills`, or `cp -r skills/* ~/.claude/skills/`.
- **Hermes Agent**: `mkdir -p .agents && cp -r skills .agents/skills`, then `hermes skills trust` once in the
  repo; or add `<path-to-repo>/skills` to `skills.external_dirs` in `~/.hermes/config.yaml`.
- **Other agents** (Codex, Cursor...): point the agent at `skills/<name>/SKILL.md`.

The AI kit (`skills/`, templates, guards, `tools/setup.sh`) is synced from the sibling project AC-NativeNX
(`docs/ai-kit/README.md`); `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md` and `contribute-letsgo` are written by hand here.

## Before you start

1. Look at what exists: `ISSUES.md` (known problems, and what is already **eliminated by proof**), `docs/`
   (render, performance and text audits, often in Portuguese), open issues and PRs.
2. One PR = one bounded problem. Do not rewrite renderer hot spots (`src/runtime/nvn/maxwell/translate.c`,
   `src/runtime/nvn/vulkan/exec.c`, `src/runtime/nvn/cmdbuf.c`, the texture cache) in parallel with someone else:
   comment the tracking issue first.
3. First contribution: a documentation fix or a **game-free** synthetic test.

## Layout at a glance

| Path | Role |
|---|---|
| `src/runtime/` | The engine: HLE, kernel, NVN→Vulkan, hooks. `nvn/maxwell/translate.c` and `nvn/vulkan/exec.c` are hot spots |
| `src/runtime/hle/`, `src/runtime/platform/` | HLE services; OS layer (Android, Linux, Windows, macOS) |
| `src/tools/` | Code generator, `gen.py`, `lifter.py`, debug tools, `teste60.sh` |
| `apk/` | Android launcher (`java/com/letsgorecomp/`), manifest, resources, importer, on-device game builder |
| `build-*.sh`, `link-runtime.sh` | Build pipeline; `build-apk.sh` packages the APK |
| `deps.lock`, `tools/ci/` | Every third-party input pinned by sha256; scripts that fetch/build them |
| `.github/workflows/` | `ci.yml` (synthetic tests), `apk.yml` (GAME=none APK + CI test APK), `release.yml` (gated), `guard.yml` |
| `tests/`, `apk/test/` | Tests (synthetic, no game) |
| `skills/`, `docs/ai-kit/` | The AI kit |
| `docs/` | Audits, ROADMAP, INDEX-FOR-LLMS (a map of the repo) |

The runtime comes from **LAS Recomp** (made for Zelda). Before blaming the game, check that one of the runtime's
assumptions holds for Let's Go (formats, controller styles, return codes, reported resolution).

## Commands

Run from the repo root and report the exact command with its real exit status.

```sh
# Guards
python3 tools/check_no_game_content.py . && python3 tools/check_letsgo_content.py && python3 tools/check_no_secrets.py

# Synthetic tests (no game, no device)
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s apk/test -p 'test_*.py' -v
bash tests/test_setup_check.sh
bash src/tools/teste60.sh                       # C runtime tests (host)

# Packaging tests (need SDK/NDK/javac: `source tools/env.sh`)
python3 -m unittest discover -s tests -p test_build_apk.py -v
```

Build facts (kept from the pipeline, see `BUILD.md`, `docs/HOWTO-BUILD.md`, `apk.yml`):

- **`deps.lock`** pins every input (URL + sha256 + licence text). Fetch with `tools/ci/fetch-deps.sh`, build with
  `tools/ci/build-deps.sh` and `tools/ci/build-buildkit.sh`. Never add an unpinned download or a prebuilt APK.
- **`GAME=none`** (default) builds the generic APK; `GAME=with` needs a privately built `build/game/liblasgame.so`
  and its APK must never be published. `bash build-apk.sh | grep -x APK_NO_GAME_OK` is the CI check.
- **`apk.yml`** runs on every PR and push to `master`, signs with a throwaway key, and uploads `letsgo-apk-<ver>`
  (APK + source tarball + `SHA256SUMS`) and the **side-by-side test APK** `letsgo-test-apk-ci-<ver>`
  (`LETSGO_APP_ID=com.letsgorecomp.ci`, label suffix ` (CI)`; installs next to the release). The release APK stays
  `com.letsgorecomp`. aapt2 checks of both package ids run in CI and in `tests/test_build_apk.py`.
- **`release.yml`** only runs when the repo variable `LETSGO_RELEASE_APPROVED` is `true` (owner decision after a
  real-device report) and signs with the release key; agents never publish a release.
- Toolchain: JDK 17, SDK platform `android-34`, build-tools `35.0.0`, NDK `28.2.13676358`;
  `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES` for 16 KiB devices.

## Style

- **C**: 4 spaces, braces on the same line, no tabs; comments explain **why**.
- **Java**: same. Every user-visible string goes through `Txt.t("key", "fallback")` (a locale test checks it).
- **Python**: 4 spaces, `unittest` (not pytest).
- **Commits**: Conventional Commits in English, imperative, lowercase, no trailing period.
- **Line endings**: LF everywhere except `.bat` and `.ps1`; a CRLF in a bash script is a bug.

## Rules for a good PR

1. **The problem**: symptom, version, scene, reproduction steps. One bounded problem, linked to an issue.
2. **The evidence**: before / after with the exact command and exit status; real output, never a predicted result.
   A missing tool is a skip or a failure, never a pass; write "not executed" for what you did not run.
3. **The fix**: minimal, and why this approach. A synthetic test whenever practical.
4. **The limits**: what you did not verify (device, other GPU, other version).
5. Displayed FPS is not game speed: a change that lowers quality or adds a wait to improve a number is not a fix.
6. Every fix gets a line in `docs/HISTORICO.md`; new debug variables go in `docs/DEPURACAO.md`.

## Repository guard rails

- **16 KiB**: recent devices need 16 KiB-aligned `.so`. The build sets `-Wl,-z,max-page-size=16384` and packaging
  refuses a non-conforming library. Do not remove that guard.
- **Software keyboard**: IME → JNI → applet → buffer; a wrong encoding (UTF-8 instead of UTF-16LE) corrupts text.
- **GPU writes**: without `VK_EXT_external_memory_host` (Adreno, system Qualcomm driver) the guest heap is never
  imported by the GPU; buffer→buffer copies wait for the GPU **only** when the memory is genuinely imported.
- **Background**: the engine must stop when not in the foreground; a background run burns a whole core.
- **Vulkan surface**: after a rotation or a fold Android recreates the `Surface`; reacquire it and replay the image.
- **Application id**: the Java package, JNI symbols and resource package are `com.letsgorecomp` whatever the
  applicationId (release or `.ci`): resource lookups use `Txt.RES_PKG`, never `getPackageName()`.
