# Contributing

Bug reports, test results from other PCs and code are all welcome. Issues and pull
requests can be written in English, Portuguese, French or Spanish.

> **If you use a coding assistant**, read [`AGENTS.md`](AGENTS.md) too — it holds the
> repository-specific commands, boundaries and evidence rules.

## The rule that never changes: nothing from Nintendo

Never post any of these in the repository, issues or pull requests:

- copies of the game in any format (`.xci`, `.nsp`, `.nca`, `.exefs`, `.romfs`...)
- keys (`prod.keys`, `title.keys`)
- extracted files (`extracted/`), generated code (`generated/`, `generated-aarch64/`)
- the Switch's system fonts (`.bfttf`)
- any game asset: textures, models, music, text, romfs files, not even "just one file to test"
- saves (`save/`) and mods containing game files (`mods/`)

Screenshots and short videos of the game in bug reports are welcome.

**The repository does not contain the game and does not build alone.** A full build
needs the user's own legally obtained dump. This is by design, not a bug — and it
means some tasks simply cannot be verified here. Say so instead of working around it.

## Reporting problems

Open an issue with:

- your device: CPU, GPU, driver version, and the resolution you chose
- what you did, what happened, and what you expected
- a screenshot or a short video, if it's visual
- if the game closed or froze: the `lasrecomp.log` file next to the app
- if you were using a translation or mod, which one

Check `ISSUES.md` first — it records what has **already been eliminated by proof**, so
you do not repeat an investigation that is already closed.

## Code

- Build and test as in [`BUILD.md`](BUILD.md) and [`docs/HOWTO-BUILD.md`](docs/HOWTO-BUILD.md).
- Every fix gets a line in `docs/HISTORICO.md` (symptom, diagnosis, cause, fix), and
  `docs/PROGRESSO.md` tracks the current state.
- New debugging variables and tools go in `docs/DEPURACAO.md`.
- The runtime came from LAS Recomp, made for Zelda. Before blaming the game, check
  whether one of the runtime's assumptions holds for Let's Go (formats, controller
  styles, return codes, reported resolution).
- To change the game's behavior without rebuilding the game code library, use the
  function hooks (`runtime/fnhook.c`).

### One problem per pull request

Split refactorings from behavior changes. A pull request that mixes a renderer rewrite
with a feature cannot be reviewed, and the renderer files
(`src/runtime/nvn/maxwell/translate.c`, `src/runtime/nvn/vulkan/exec.c`,
`src/runtime/nvn/cmdbuf.c`, the texture cache) change often — comment the tracking
issue before touching them so two people do not rewrite the same code.

Independent work can proceed in parallel on separate branches. There is no blanket
limit of one open pull request per person; keep it within review capacity.

### Tests

A fix comes with a reproducible test whenever that is practical. Tests must be
**synthetic**: no game file, no signed APK with a real key. Report the exact command,
its exit status, its duration and its limits. A missing tool is a skip or a failure,
never a substitute success.

```sh
# Synthetic tests that need no game and no device
python3 -m unittest discover -s tests -v
python3 -m unittest discover -s apk/test -p 'test_*.py' -v
bash src/tools/teste60.sh
```

### Pull request contents

1. **The problem**: symptom, version, scene, reproduction steps.
2. **The evidence**: before / after, with the exact command and its exit status.
3. **The fix**: minimal, and why this approach rather than another.
4. **The limits**: what you did **not** verify (device, other GPU, other version).

If a coding assistant helped, say so plainly in the pull request, without model or
tool names and without signature trailers. **Assistance is not independent approval**;
the review stays human.

### Do not merge your own pull request

Open it as a draft. A maintainer decides readiness and merging. Never force-push the
shared branches (`publication`, `main`).

## Commit messages

Commits are written in **English** and follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary in the imperative, lowercase, no period>

<optional body: what changed and why, wrapped at ~100 columns>
```

- **type:** `feat` (a new feature or option), `fix` (a bug fix), `docs` (documentation only), `perf`, `refactor`,
  `test`, `build` (Makefile, release packaging), `chore` (versions, housekeeping); research tools that only help
  debugging are `chore(debug)` or `feat(debug)` when they add a new tool.

## License

By contributing you agree that your contribution is licensed under the repository's
MIT license, and that you preserve the third-party notices (`LICENSE-LASRecomp`,
`LICENSES/`, `THIRD_PARTY_NOTICES.md`). Do not add code whose license conflicts with
those terms.

## Where to look

| File | What it tells you |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Commands, boundaries and evidence rules (also for AI assistants) |
| `ISSUES.md` | Known problems, and what is already ruled out |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | What actually works today, and the priorities |
| `BUILD.md` | Build tutorial |
| `docs/HOWTO-BUILD.md` | Build details |
| `docs/MODS.md` | How mods work |
| `docs/PERF_AUDIT.md` | Performance measurements |
