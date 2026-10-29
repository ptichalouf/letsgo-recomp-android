# AI-assisted contributions

This repository accepts contributions prepared with a coding assistant. Here is what
we expect, and what we will not do.

## What we expect

- **The evidence comes from a real run.** The exact command, its exit status, its
  duration, and the limits. A missing tool is a *skip* or a *failure*, never a
  substitute success.
- **A synthetic test** where possible: never a game file, never an APK signed with a
  real key.
- **One bounded problem per pull request.** The diff reads in one sitting.
- **The subject is on the list.** `docs/ROADMAP.md` lists the open problems with their
  acceptance criteria. A proposal that is not listed is welcome, but say so in the
  issue before writing the code.

## What is not acceptable

- **Game content.** Never: not in the repository, not in an issue, not in a pull
  request, not "just one file to test".
- **A predicted result presented as a measurement.** If you did not run the command,
  say "not executed".
- **Rewriting a renderer hot spot** without announcing the scope in the tracking
  issue first (`src/runtime/nvn/maxwell/translate.c`,
  `src/runtime/nvn/vulkan/exec.c`, `src/runtime/nvn/vulkan/textures.c`,
  `src/runtime/nvn/cmdbuf.c`).
- **A "performance fix" that degrades quality** or adds a wait to make a number go
  down. Displayed fps is not game speed: tell the two apart in the evidence.
- **Merging your own pull request.** Open it as a draft; a maintainer merges.
- **Naming the model or the tool in the pull request.** Simply mention that you used
  an assistant. **Assistance is not independent approval** — the review stays human.

## What the agent must read before acting

| Document | What it brings |
|---|---|
| [`AGENTS.md`](../AGENTS.md) | The commands, the boundaries, the evidence rules |
| [`docs/ROADMAP.md`](../docs/ROADMAP.md) | What works, what is missing, the acceptance criteria |
| [`ISSUES.md`](../ISSUES.md) | What has already been **eliminated by proof** |
| [`docs/INDEX-FOR-LLMS.md`](../docs/INDEX-FOR-LLMS.md) | The map of the repository |
| [`CONTRIBUTING.md`](../CONTRIBUTING.md) | The human contract, equivalent |

`AGENTS.md` is read natively by Codex, Claude Code (since v2.1.277), Cursor, Copilot,
Aider, Zed and others. If your tool does not read it, provide it in the prompt.

## The shape of a good AI-assisted issue

```markdown
## Problem
The dialogue produces no fragment; the menu does. (see ISSUES.md § 1)

## Scope
Files: src/runtime/nvn/vulkan/exec.c, src/runtime/nvn/vulkan/textures.c
Out of scope: the rest of translate.c

## Evidence
Command: python3 -m unittest discover -s tests -p test_surface_recovery.py -v
Before: 1 test fails (AssertionError: expected 'surface recreated')
After: 1 test passes
Duration: 0.4 s

## Limits
Not verified on a real device: no dump, no signed APK available here.
```

## What the maintainer checks

1. Is the subject on the list, and is the announced scope respected?
2. Does the test fail **before** the fix? (A test that already passes is not
   evidence.)
3. Does it pass **after**, on the exact head of the pull request?
4. Is the diff minimal and readable?
5. Are the limits stated, including what was not verified?

If the verdict is negative, the assistant is not the problem: the code does not prove
what it claims. Fix the evidence, not the wording.
