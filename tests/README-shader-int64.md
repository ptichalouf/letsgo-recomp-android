# Host regression: shaderInt64 fallback

`test_shader_int64.py` builds the Maxwell translator from the local tree and
from an explicit git base. It generates synthetic instructions; it does not
read, copy or require any game data, keys, GPU or Android device.

```sh
# cc, c++, Python 3, glslangValidator and spirv-val must be installed.
# If not on PATH, set GLSLANG_VALIDATOR and SPIRV_VAL to their executable paths.
python3 tests/test_shader_int64.py --base adbf12dd
```

Six checks:

- Int64 present: compare **complete GLSL bytes** against the publication base
  for vertex, fragment and compute stages, with both direct and mapped memory.
- Int64 absent: no 64-bit types/literals in complete generated GLSL.
- Compile fallback compute GLSL to SPIR-V, validate it, parse `OpCapability`
  instructions and reject Int64 (11). This check explicitly skips if tools
  are missing; a full acceptance run must have **no skips**.
- Verify Vulkan enables/records the queried feature and sets the fallback
  before both graphics and compute translation.
- MSL output remains byte-identical even when the fallback flag is set.
- Extract the **actual instruction bodies emitted by the translator**, compile
  the old and new arithmetic as C++ with UBSan, compare their outputs over
  deterministic boundary/random inputs: IADD32I, IADD3, SHF and LDG/STG
  address arithmetic, bounds and word indices (including RZ, signed 24-bit
  offsets, carry/borrow, zero/high bases and sizes beyond 4 GiB).

The corpus includes CC/X/negation, immediate boundaries, shift directions,
32/64-bit widths, signed and wrapped/clamped shifts, loads/stores of all eight
size encodings and both address widths. It is a finite regression corpus,
not a formal proof over all possible programs. The C++ arithmetic execution
is not GPU execution; SPIR-V validation separately checks GLSL typing and
capabilities. No claim of device text rendering is made.

## Reproduce the red test on the base

In a disposable worktree checked out at `adbf12dd`, copy only
`tests/test_shader_int64.py` from this PR, then run:

```sh
python3 tests/test_shader_int64.py --base adbf12dd Int64Tests.test_absent_no_64bit_math
```

Both translators compile using only base APIs. The assertion fails because the
base emits `uint64_t` even when the harness requests the fallback; this is a
functional red result, not a missing symbol/build error.
