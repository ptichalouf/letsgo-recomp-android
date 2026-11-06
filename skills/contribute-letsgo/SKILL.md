---
name: contribute-letsgo
description: Use when asked to fix a bug, add a feature or improve something in Let's Go Recomp (Android port of Pokemon Let's Go Eevee, static recompilation) and deliver a reviewable pull request. Covers the no-game-content rule, deps.lock and GAME=none builds, the CI test APK, synthetic tests, evidence and AI disclosure.
---

# Contribute to Let's Go Recomp

Goal: turn one bounded problem into a draft PR that a reviewer can check without asking you anything.

## Before touching code

1. Read `AGENTS.md` (rules, map, commands), `ISSUES.md` (what is already **eliminated by proof**: do not repeat
   it) and `docs/ROADMAP.md` (real state; a "proposed" item is not a feature).
2. Run `tools/setup.sh --check`. A `MISSING` or `WRONG-VERSION` line goes to the human with the printed
   command. Do not guess, do not half-install.
3. Find or open the issue and comment your scope (files, budget). For renderer hot spots
   (`src/runtime/nvn/maxwell/translate.c`, `src/runtime/nvn/vulkan/exec.c`, `.../textures.c`,
   `src/runtime/nvn/cmdbuf.c`) wait for a reply.

## Red lines

- Never add the game, keys, firmware, saves, `extracted/`, `generated*/`, `romfs/`, `exefs/`, `.bfttf` or
  anything derived from the game. If the task needs one, stop and say so. Guards:
  `tools/check_no_game_content.py` (kit) and `tools/check_letsgo_content.py` (stricter, repo-specific), plus
  `tools/check_no_secrets.py`.
- No PR/issue/push to upstreams (`CowKillers/letsgo-recomp-android`, `Nycolazs/*`). This is an autonomous fork.
- Do not merge your own PR. Never claim "tested on device" without a device report from a named tester.
- Do not touch `deps.lock` hashes or `LICENSES/` casually: every input is pinned and every licence text must match
  its archive (`python3 -I tools/ci/deps.py licenses`).

## Build facts

- The repo never builds a playable APK alone. CI (`.github/workflows/apk.yml`) builds `GAME=none` from the pinned
  sources of `deps.lock`; `release.yml` is gated by the repo variable `LETSGO_RELEASE_APPROVED` (agents never set it).
- `build-apk.sh` takes `GAME=none|with`, and optionally `LETSGO_APP_ID` / `LETSGO_LABEL_SUFFIX`. CI uses them to
  produce the side-by-side test APK `com.letsgorecomp.ci` ("Let's Go Recomp (CI)"). The release package is always
  `com.letsgorecomp`. The Java package, JNI symbols and resource lookups stay `com.letsgorecomp`: resource
  lookups use `Txt.RES_PKG`, never `getPackageName()` (it is `com.letsgorecomp.ci` on the test build).

## Work

1. `git switch -c fix/<short-topic>` from `master`. One problem per branch.
2. Write the failing synthetic test first when practical (dummy data only). Run it, see it fail.
3. Minimal change. Keep third-party notices (`LICENSE-LASRecomp`, `LICENSES/`, `THIRD_PARTY_NOTICES.md`).
4. Run and record the exact command and exit status:
   - `python3 -m unittest discover -s tests -v`
   - `python3 -m unittest discover -s apk/test -p 'test_*.py' -v`
   - `python3 tools/check_no_game_content.py . && python3 tools/check_letsgo_content.py && python3 tools/check_no_secrets.py`
   - `bash src/tools/teste60.sh` when you touched `src/runtime`
   A missing tool is a skip or a failure, never a pass. Write "not executed" for what you did not run.
5. Conventional Commits in English. Last body line: `Agent: <name> · Model: <id>`.

## Open the PR (draft)

Fill `.github/PULL_REQUEST_TEMPLATE.md`: problem, change, evidence (real output), limits, "Device tested on" (or
"not device-tested"), agent name + model. After CI is green, the CI test APK (artifact `letsgo-test-apk-ci-*`) is what a
device tester installs next to the release build.
