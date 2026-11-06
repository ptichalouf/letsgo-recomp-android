# Provenance of the license texts in `LICENSES/`

Each text below was copied verbatim (line endings normalised to LF) from the project's official repository at the
commit shown, on 2026-11-06.

| File | Repository | Commit | Path |
|---|---|---|---|
| `LASRecomp-MIT.txt` | peiikai/LASRecomp | `579fdad6d736` | [LICENSE](https://github.com/peiikai/LASRecomp/blob/579fdad6d736c8fbf1d21943593731eb926b6659/LICENSE) |
| `letsgo-recomp-MIT.txt` | raulzrrs/letsgo-recomp | `1a70f777c7ab` | [LICENSE](https://github.com/raulzrrs/letsgo-recomp/blob/1a70f777c7abac6834ca54bd69b1235471193e1e/LICENSE) |
| `p5r-android-MIT.txt` | Brandon04Gonzalez/p5r-android | `45c07a16c717` | [LICENSE](https://github.com/Brandon04Gonzalez/p5r-android/blob/45c07a16c717d7140f2e576c06f4048625a9bc3f/LICENSE) |
| `AMD-FidelityFX-FSR1-MIT.txt` | GPUOpen-Effects/FidelityFX-FSR | `a21ffb8f6c13` | [license.txt](https://github.com/GPUOpen-Effects/FidelityFX-FSR/blob/a21ffb8f6c13233ba336352bdff293894c706575/license.txt) |
| `VulkanMemoryAllocator-MIT.txt` | GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator | `3aa921224c15` | [LICENSE.txt](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator/blob/3aa921224c154a0d2c43912bc88e1c42ce1f7607/LICENSE.txt) |
| `DejaVu-Fonts-License.txt` | dejavu-fonts/dejavu-fonts | `0eda8a319c08` | [LICENSE](https://github.com/dejavu-fonts/dejavu-fonts/blob/0eda8a319c08835009849583cd090bb5b141ce25/LICENSE) |
| `libadrenotools-BSD-2-Clause.txt` | bylaws/libadrenotools | `8fae8ce254df` | [LICENSE](https://github.com/bylaws/libadrenotools/blob/8fae8ce254dfc1344527e05301e43f37dea2df80/LICENSE) |
| `Mesa-license.rst` | mesa/mesa (gitlab.freedesktop.org) | `cf98950a3f54` | [docs/license.rst](https://gitlab.freedesktop.org/mesa/mesa/-/blob/cf98950a3f548751301091ae4a224b8c8f59d988/docs/license.rst) |
| `Mesa-licenses-MIT.txt` | mesa/mesa (gitlab.freedesktop.org) | `cf98950a3f54` | [licenses/MIT](https://gitlab.freedesktop.org/mesa/mesa/-/blob/cf98950a3f548751301091ae4a224b8c8f59d988/licenses/MIT) |
| `LLVM-libcxx-Apache-2.0-WITH-LLVM-exception.txt` | llvm/llvm-project | `a4bf6cd7cfb1` | [libcxx/LICENSE.TXT](https://github.com/llvm/llvm-project/blob/a4bf6cd7cfb1a1421ba92bca9d017b49936c55e4/libcxx/LICENSE.TXT) |

Notes:

- `LASRecomp-MIT.txt` is identical to `LICENSE-LASRecomp` at the repository root.
- `VulkanMemoryAllocator-MIT.txt`: tag `v3.4.0`, the version in `src/third_party/vma/vk_mem_alloc.h`.
- `libadrenotools-BSD-2-Clause.txt`: commit `8fae8ce`, the one cited for `docs/performance/driver.cpp.orig`.
- `DejaVu-Fonts-License.txt`: tag `version_2_37`, the version of `apk/assets/fonts/DejaVuSans.ttf`.
- `Mesa-*`: Mesa commit `cf98950a`, the one the downloadable Turnip package is built from (not bundled, see
  `THIRD_PARTY_NOTICES.md` §2).
- `LLVM-libcxx-Apache-2.0-WITH-LLVM-exception.txt`: `llvmorg-19.1.0`. NDK 28.2.13676358 ships an Android build of
  clang/libc++ 19; the text is the LLVM project's license, unchanged across 19.x. The NDK's own `NOTICE` remains
  the authoritative copy for `libc++_shared.so`.
- `p5r-android-MIT.txt` carries BelmanteGu's copyright line as well; BelmanteGu's original SwitchGamesRecomp
  repository could not be found, so no separate text is stored for it.

## Texts checked against the pinned archives of `deps.lock`

The files below are byte-identical to a member of an upstream archive pinned (URL + SHA-256) in
[`deps.lock`](../deps.lock) (`lic` lines); CI job `deps-lock` downloads every archive, checks its SHA-256 and compares
each text again (`python3 -I tools/ci/deps.py licenses`).

| File | From (deps.lock entry, version) | Member |
|---|---|---|
| `SDL3-zlib.txt` | sdl3 3.4.18 (https://github.com/libsdl-org/SDL/releases/download/release-3.4.18/SDL3-3.4.18.tar.gz) | `SDL3-3.4.18/LICENSE.txt` |
| `FFmpeg-LGPL-2.1.txt` | ffmpeg 9.0.2 (https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz) | `ffmpeg-9.0.2/COPYING.LGPLv2.1` |
| `FFmpeg-LICENSE.md` | ffmpeg 9.0.2 (https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz) | `ffmpeg-9.0.2/LICENSE.md` |
| `shaderc-Apache-2.0.txt` | shaderc 2026.3 (https://github.com/google/shaderc/archive/refs/tags/v2026.3.tar.gz) | `shaderc-2026.3/LICENSE` |
| `glslang-LICENSE.txt` | glslang 168d452a4f46 (https://github.com/KhronosGroup/glslang/archive/168d452a4f460d24b588fed08477a81c44ee27a1.tar.gz) | `glslang-168d452a4f460d24b588fed08477a81c44ee27a1/LICENSE.txt` |
| `SPIRV-Tools-Apache-2.0.txt` | spirv-tools b707790a898e (https://github.com/KhronosGroup/SPIRV-Tools/archive/b707790a898e44038547df54580022fc1cf89c3d.tar.gz) | `SPIRV-Tools-b707790a898e44038547df54580022fc1cf89c3d/LICENSE` |
| `SPIRV-Headers-LICENSE.txt` | spirv-headers 29981f652416 (https://github.com/KhronosGroup/SPIRV-Headers/archive/29981f65241605e08b0ede4cfeb999fe3b723c6a.tar.gz) | `SPIRV-Headers-29981f65241605e08b0ede4cfeb999fe3b723c6a/LICENSE` |
| `libadrenotools-BSD-2-Clause.txt` | libadrenotools 8fae8ce254df (https://github.com/bylaws/libadrenotools/archive/8fae8ce254dfc1344527e05301e43f37dea2df80.tar.gz) | `libadrenotools-8fae8ce254dfc1344527e05301e43f37dea2df80/LICENSE` |
| `liblinkernsbypass-BSD-2-Clause.txt` | liblinkernsbypass aa3975893d83 (https://github.com/bylaws/liblinkernsbypass/archive/aa3975893d83ef1bc84c321ec60c65fbf1287887.tar.gz) | `liblinkernsbypass-aa3975893d83ef1bc84c321ec60c65fbf1287887/LICENSE` |
| `libarchive-BSD-2-Clause.txt` | libarchive 3.8.1 (https://github.com/libarchive/libarchive/releases/download/v3.8.1/libarchive-3.8.1.tar.xz) | `libarchive-3.8.1/COPYING` |
| `liblzma-0BSD.txt` | xz 5.8.1 (https://github.com/tukaani-project/xz/releases/download/v5.8.1/xz-5.8.1.tar.xz) | `xz-5.8.1/COPYING.0BSD` |
| `CPython-PSF-2.0.txt` | cpython-android 3.14.8 (https://www.python.org/ftp/python/3.14.8/python-3.14.8-aarch64-linux-android.tar.gz) | `./prefix/lib/python3.14/LICENSE.txt` |
| `capstone-BSD-3-Clause.txt` | capstone 5.0.9 (https://files.pythonhosted.org/packages/39/01/6516910f546fbb996068207b9dd0229b14bc8dae223114d5e0e27d3cad11/capstone-5.0.9.tar.gz) | `capstone-5.0.9/LICENSE.TXT` |
| `capstone-LICENSE_LLVM.txt` | capstone 5.0.9 (https://files.pythonhosted.org/packages/39/01/6516910f546fbb996068207b9dd0229b14bc8dae223114d5e0e27d3cad11/capstone-5.0.9.tar.gz) | `capstone-5.0.9/src/LICENSE_LLVM.TXT` |
| `Unicorn-GPL-2.0.txt` | unicorn 2.1.4 (https://files.pythonhosted.org/packages/b2/1b/b4248aa8422e86de690cf8e85cf8feae4c33405a097d1ebe71570bdaa6f5/unicorn-2.1.4.tar.gz) | `unicorn-2.1.4/src/qemu/COPYING` |
| `Unicorn-qemu-COPYING.LIB.txt` | unicorn 2.1.4 (https://files.pythonhosted.org/packages/b2/1b/b4248aa8422e86de690cf8e85cf8feae4c33405a097d1ebe71570bdaa6f5/unicorn-2.1.4.tar.gz) | `unicorn-2.1.4/src/qemu/COPYING.LIB` |
| `Unicorn-qemu-LICENSE.txt` | unicorn 2.1.4 (https://files.pythonhosted.org/packages/b2/1b/b4248aa8422e86de690cf8e85cf8feae4c33405a097d1ebe71570bdaa6f5/unicorn-2.1.4.tar.gz) | `unicorn-2.1.4/src/qemu/LICENSE` |
| `python-lz4-BSD-3-Clause.txt` | lz4 4.4.5 (https://files.pythonhosted.org/packages/57/51/f1b86d93029f418033dddf9b9f79c8d2641e7454080478ee2aab5123173e/lz4-4.4.5.tar.gz) | `lz4-4.4.5/LICENSE` |
| `lz4-BSD-2-Clause.txt` | lz4 4.4.5 (https://files.pythonhosted.org/packages/57/51/f1b86d93029f418033dddf9b9f79c8d2641e7454080478ee2aab5123173e/lz4-4.4.5.tar.gz) | `lz4-4.4.5/lz4libs/lz4.c` lines 1-33 |
| `zig-MIT.txt` | zig-aarch64-linux 0.17.0 (https://ziglang.org/download/0.17.0/zig-aarch64-linux-0.17.0.tar.xz) | `zig-aarch64-linux-0.17.0/LICENSE` |
| `musl-COPYRIGHT.txt` | zig-aarch64-linux 0.17.0 (https://ziglang.org/download/0.17.0/zig-aarch64-linux-0.17.0.tar.xz) | `zig-aarch64-linux-0.17.0/lib/libc/musl/COPYRIGHT` |
| `zig-MIT.txt` | zig-x86_64-linux 0.17.0 (https://ziglang.org/download/0.17.0/zig-x86_64-linux-0.17.0.tar.xz) | `zig-x86_64-linux-0.17.0/LICENSE` |
| `LLVM-Apache-2.0-WITH-LLVM-exception.txt` | llvm-license llvmorg-22.1.8 (https://raw.githubusercontent.com/llvm/llvm-project/llvmorg-22.1.8/llvm/LICENSE.TXT) | the downloaded file itself |
| `Ryujinx-MIT.txt` | ryujinx-license cd74ae1bbd49 (https://archive.softwareheritage.org/api/1/content/sha1_git:818ddd76058d49f42eed9c0ecb580777c7f87d7e/raw/) | the downloaded file itself |

Notes on these:

- `Ryujinx-MIT.txt`: github.com/Ryujinx/Ryujinx no longer exists. The text comes from the **Software Heritage**
  archive of that repository (origin `https://github.com/Ryujinx/Ryujinx`, visit of 2024-09-15, snapshot
  `7a99a06e4449d9f03e4d41f1b601ac08d6f40bd3`, `refs/heads/master` = `cd74ae1bbd491987d983bfff36545451e2e6764c`,
  `LICENSE.txt` = `swh:1:cnt:818ddd76058d49f42eed9c0ecb580777c7f87d7e`, SHA-256
  `781bbdf040b7d0286c47cca6bffdb9148dfe751c0c9cabdf1a1752412a2e56b4`).
- `Unicorn-GPL-2.0.txt`: the unicorn 2.1.4 sdist has no top-level `COPYING`; `src/qemu/COPYING` is byte-identical to
  `COPYING` of unicorn-engine/unicorn at tag `2.1.4` (SHA-256 `6f04ae8364d0079a192b14635f4b1da294ce18724c034c39a6a41d1b09df6100`).
- `lz4-BSD-2-Clause.txt`: the LZ4 library's license is the header of `lz4libs/lz4.c` (lines 1-33); python-lz4 ships
  no separate text for it.
- `zig-MIT.txt` is identical in the aarch64 and x86_64 zig 0.17.0 archives (both checked).
- `LLVM-Apache-2.0-WITH-LLVM-exception.txt`: `llvm/LICENSE.TXT` at `llvmorg-22.1.8`, the LLVM inside zig 0.17.0
  (`clang version 22.1.8` in the binary). `LLVM-libcxx-...` above stays the text for the NDK's libc++.
- `libarchive-BSD-2-Clause.txt`, `liblzma-0BSD.txt`, `libadrenotools-BSD-2-Clause.txt`: were already here; now also
  checked against the pinned archives.
- The Android NDK's own `NOTICE` / `NOTICE.toolchain` are copied from the installed NDK 28.2.13676358 at build time
  into `assets/buildkit/licenses/` (not stored here).
