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
| `libarchive-BSD-2-Clause.txt`, `liblzma-0BSD.txt` | (added earlier, from the 3.8.1 / 5.8.1 release tarballs checked by `build-modunpack.sh`) | | |

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

## Still missing (cannot be verified yet)

| Expected file | Why |
|---|---|
| `SDL3-zlib.txt` | the SDL3 version used for `deps/SDL3` is not pinned anywhere in this repository |
| `FFmpeg-LGPL-2.1.txt`, `FFmpeg-LICENSE.md` | the FFmpeg source version built by `rebuild-ffmpeg-opus.sh` is not pinned; LGPL also requires the exact corresponding source to be offered |
| `shaderc-Apache-2.0.txt`, `SPIRV-Tools-Apache-2.0.txt`, `glslang-LICENSE.txt` | shaderc / SPIRV-Tools / glslang versions not pinned |
| `liblinkernsbypass-BSD-2-Clause.txt` | the libadrenotools/liblinkernsbypass revision linked into builds is not pinned |
| `Ryujinx-MIT.txt` | the official Ryujinx repository (github.com/Ryujinx/Ryujinx) no longer exists; no official copy to fetch |

Until these are resolved, no APK built from this repository should be distributed.
