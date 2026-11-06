# Third-party notices

This project is a derivative of **letsgo-recomp-android** (CowKillers), itself built on **letsgo-recomp** (raulzrrs)
and **LAS Recomp** (Peiikai). This file lists the third-party material contained in this repository or in builds made
from it, with its license. Full license texts are in [`LICENSES/`](LICENSES/).

> Not affiliated with, endorsed or sponsored by Nintendo, The Pokémon Company, Game Freak or Creatures.
> No game files, game code, keys, graphics, text, music or fonts belonging to them are included.

<!-- Base audited: CowKillers/letsgo-recomp-android @ c20762fadbcc246e0dee470cf6c38a0e324ce4bd (2026-10-26).
     Update the "Files" columns if the derived tree moves or drops files. -->

## 1. Source code included in this repository

| Component | Files in this repository | License | Copyright | License text |
|---|---|---|---|---|
| **LAS Recomp** (https://github.com/peiikai/LASRecomp) - static recompiler and runtime | most of `src/runtime/`, `src/tools/`, `src/Makefile`, `importer/romfs_build.*`, `src/runtime/romfs_build.*` | MIT | Copyright (c) 2026 Peiikai | `LICENSES/LASRecomp-MIT.txt` (also `LICENSE-LASRecomp`) |
| **letsgo-recomp** (https://github.com/raulzrrs/letsgo-recomp) - Let's Go port of LAS Recomp | `src/` (changes on top of LAS Recomp) | MIT | Copyright (c) 2026 raulzito12345rrs | `LICENSES/letsgo-recomp-MIT.txt` |
| **letsgo-recomp-android** (https://github.com/CowKillers/letsgo-recomp-android) - Android layer | `apk/`, `importer/` integration, root build scripts | MIT (inherited, see question Q5 in the publication notes) | CowKillers | - |
| **On-device NSP/XCI importer** from SwitchGamesRecomp / *Super Mario Bros. Wonder Recomp* by BelmanteGu, as found in **p5r-android** (https://github.com/Brandon04Gonzalez/p5r-android, `android/wonder/importer/`) | `importer/nximport.cpp`, `importer/nximport.h`, `importer/nximport_jni.cpp`, `importer/nxcrypto.cpp`, `importer/nxcrypto.h`, `apk/importer/wonder_nximport.cpp`, `apk/importer/nxcrypto.cpp`, `apk/importer/nxcrypto.h`, parts of `apk/importer/lgimport.cpp` | MIT | Copyright (c) 2026 BelmanteGu; p5r-android also lists Copyright (c) 2026 Brandon | `LICENSES/p5r-android-MIT.txt` (carries BelmanteGu's copyright line; see `LICENSES/SOURCES.md`) |
| **Ryujinx** (MIT) - audio renderer (the reverb is a port, the mixer is modelled on it) and shader instruction decoders (bit-layout reference) | `src/runtime/services/audio.c`, parts of `src/runtime/nvn/maxwell/translate.c` | MIT | Copyright (c) Ryujinx Team and Contributors | `LICENSES/Ryujinx-MIT.txt` |
| **LASRecomp-Android-Plus** (https://github.com/Nycolazs/LASRecomp-Android-Plus) - Android platform layer reference | parts of `src/runtime/platform/android.c` | MIT | Copyright (c) 2026 Peiikai (as stated in that repository's LICENSE); Nycolazs | `LICENSES/LASRecomp-MIT.txt` |
| **AMD FidelityFX Super Resolution 1** - EASU/RCAS, ported to GLSL | `src/runtime/nvn/vulkan/shaders.c` | MIT | Copyright (c) 2021 Advanced Micro Devices, Inc. | `LICENSES/AMD-FidelityFX-FSR1-MIT.txt` |
| **SMAA** - shader and lookup textures | `src/third_party/smaa/`, `src/runtime/nvn/smaa_data.c` | MIT | Copyright (C) 2013 Jorge Jimenez, Jose I. Echevarria, Belen Masia, Fernando Navarro, Diego Gutierrez | `src/third_party/smaa/LICENSE.txt` |
| **Dear ImGui** | `src/third_party/imgui/` | MIT | Copyright (c) 2014-2025 Omar Cornut | `src/third_party/imgui/LICENSE.txt` |
| **Vulkan Memory Allocator** | `src/third_party/vma/vk_mem_alloc.h` | MIT | Copyright (c) 2017-2026 Advanced Micro Devices, Inc. | header of the file; `LICENSES/VulkanMemoryAllocator-MIT.txt` |
| **Baloo 2** font | `src/third_party/fonts/Baloo2/` | SIL OFL 1.1 | Copyright 2019 The Baloo 2 Project Authors | `src/third_party/fonts/Baloo2/OFL.txt` |
| **libadrenotools** `src/driver.cpp` (kept for reference, unmodified, commit 8fae8ce) | `docs/performance/driver.cpp.orig` | BSD-2-Clause | Copyright (c) 2021 Billy Laws | `LICENSES/libadrenotools-BSD-2-Clause.txt` |

## 2. Binary files included in this repository

| Component | File | Version / provenance | License | License text |
|---|---|---|---|---|
| **DejaVu Sans** font | `apk/assets/fonts/DejaVuSans.ttf` | 2.37 (https://github.com/dejavu-fonts/dejavu-fonts/releases/tag/version_2_37) | Bitstream Vera Fonts license; DejaVu changes in the public domain; Arev glyphs (c) Tavmjong Bah | `LICENSES/DejaVu-Fonts-License.txt` |

## 2b. Downloaded on the user's request (not in this repository nor in the APK)

`apk/assets/turnip-8g2.zip` was removed in `0cfb554` (GPU driver only by import or verified download). The launcher
(`DriverCatalog.java`) can download a Turnip package from the packager's GitHub releases when the user asks, and checks
its SHA-256. The package below is the one previously bundled, kept for reference:

| Component | Where | Version / provenance | License | License text |
|---|---|---|---|---|
| **Mesa Turnip** Vulkan driver (`libvulkan_freedreno.so` + `meta.json`), packaged for AdrenoTools by The412Banner | not bundled (downloaded on request) | Byte-identical to the release asset `Turnip-v26.3.0-20261015-r5-8G2-OneUI.zip` of https://github.com/The412Banner/Banners-Turnip/releases/tag/v26.3.0-20261015-r5 (SHA-256 `043d22df9176f9a05aee0ebebfe8b55909f1db617406c4dbc40617b201a52ab3`). Built from Mesa commit `cf98950a3f548751301091ae4a224b8c8f59d988` (https://gitlab.freedesktop.org/mesa/mesa/-/commit/cf98950a3f548751301091ae4a224b8c8f59d988) with the packager's `patches/8g2_oneui.py` (A740 `TP_UBWC_FLAG_HINT`). | Mesa: MIT for the core and the freedreno/turnip driver; individual files may carry other licenses (see Mesa `docs/license.rst` and `licenses/`) | `LICENSES/Mesa-license.rst`, `LICENSES/Mesa-licenses-MIT.txt` |

## 3. Libraries linked into the Android build (not stored in this repository)

Built from the upstream sources pinned in [`deps.lock`](deps.lock) (version, URL, SHA-256) by `tools/ci/fetch-deps.sh`
and `tools/ci/build-deps.sh`, unmodified, into `deps/`. The unmodified source archives of all of them are in the
`letsgo-<version>-source.tar.gz` published with every APK (`deps/dl/`), with the scripts that built them.

| Component | Version | How it is used | License | License text |
|---|---|---|---|---|
| **SDL 3** (incl. its Android Java glue) | 3.4.18 | `libSDL3.so` (shared) + Java classes | zlib | `LICENSES/SDL3-zlib.txt` |
| **FFmpeg** (`libavformat`, `libavcodec`, `libavutil`, `libswscale`, `libswresample`) | 9.0.2 | shared libraries, configured without `--enable-gpl` / `--enable-nonfree` (checked by `tools/ci/build-deps.sh`); replaceable by the user (separate `.so`) | LGPL 2.1 or later | `LICENSES/FFmpeg-LGPL-2.1.txt`, `LICENSES/FFmpeg-LICENSE.md` |
| **shaderc** | 2026.3 | static, inside `liblasrecomp.so` | Apache 2.0 | `LICENSES/shaderc-Apache-2.0.txt` |
| **SPIRV-Tools** | `b707790` (shaderc 2026.3 DEPS) | static, inside `liblasrecomp.so` | Apache 2.0 | `LICENSES/SPIRV-Tools-Apache-2.0.txt` |
| **SPIRV-Headers** | `29981f6` (shaderc 2026.3 DEPS) | headers compiled into SPIRV-Tools / glslang | MIT (Khronos) / CC-BY-4.0 (see file) | `LICENSES/SPIRV-Headers-LICENSE.txt` |
| **glslang** | `168d452` (shaderc 2026.3 DEPS) | static, inside `liblasrecomp.so` | BSD-3-Clause / BSD-2-Clause / MIT / Apache 2.0 / GPL-3.0-or-later WITH Bison-exception-2.2 (generated parser; see file) | `LICENSES/glslang-LICENSE.txt` |
| **libadrenotools** | `8fae8ce` | `libadrenotools.so`, `libhook_impl.so`, `libmain_hook.so` (shared) | BSD-2-Clause | `LICENSES/libadrenotools-BSD-2-Clause.txt` |
| **liblinkernsbypass** | `aa39758` (libadrenotools' submodule) | static, inside the libadrenotools libraries | BSD-2-Clause | `LICENSES/liblinkernsbypass-BSD-2-Clause.txt` |
| **LLVM libc++** (`libc++_shared.so` from the Android NDK 28.2.13676358) | NDK 28.2 | shared | Apache 2.0 with LLVM Exceptions | `LICENSES/LLVM-libcxx-Apache-2.0-WITH-LLVM-exception.txt` |
| **libarchive** (zip, rar, rar5 and 7zip readers; `build-modunpack.sh`) | 3.8.1 | static, inside `libmodunpack.so` | BSD-2-Clause (see file) | `LICENSES/libarchive-BSD-2-Clause.txt` |
| **liblzma** (XZ Utils; `build-modunpack.sh`) | 5.8.1 | static, inside `libmodunpack.so` | 0BSD | `LICENSES/liblzma-0BSD.txt` |

## 3b. On-device game builder kit (`assets/buildkit/` and programs in `lib/arm64-v8a/`)

The kit that compiles the player's own game code on the phone is built by `tools/ci/build-buildkit.sh` from the
releases pinned in `deps.lock` (same recipe as Nycolazs/LASRecomp-Android-Plus `android-v1.2.0`,
`tools/android/ondevice/*.sh`, MIT; its prebuilt APK is no longer used for anything distributed) and staged by
`tools/stage-buildkit.py` (`config.json`: `"kit_origin": "source"`). It contains no game code or data.

| Component | Version | In the APK | License | License text |
|---|---|---|---|---|
| **CPython** (python.org's official Android release) | 3.14.8 | `libpython3.14.so`, trimmed standard library in `assets/buildkit/pyhome/`; `liblaspython.so` is its launcher (`tools/ci/kit/pylauncher.c`, from LASRecomp-Android-Plus, MIT) | PSF-2.0 (the file also lists the libraries CPython's modules use) | `LICENSES/CPython-PSF-2.0.txt`, `LICENSES/LASRecomp-MIT.txt` |
| **Capstone** (ARM64 only) + Python bindings | 5.0.9 | `libcapstone.so`, `pyhome/.../site-packages/capstone/` | BSD-3-Clause; LLVM-derived tables: LLVM license | `LICENSES/capstone-BSD-3-Clause.txt`, `LICENSES/capstone-LICENSE_LLVM.txt` |
| **Unicorn** (aarch64 only, includes QEMU code) + Python bindings | 2.1.4 | `libunicorn.so`, `pyhome/.../site-packages/unicorn/` | **GPL-2.0** (QEMU parts: GPL-2.0 / LGPL-2.1, see its LICENSE); bindings: BSD | `LICENSES/Unicorn-GPL-2.0.txt`, `LICENSES/Unicorn-qemu-COPYING.LIB.txt`, `LICENSES/Unicorn-qemu-LICENSE.txt` |
| **python-lz4** with the **LZ4** library | 4.4.5 | `pyhome/.../site-packages/lz4/` (C extensions built here) | BSD-3-Clause (bindings), BSD-2-Clause (LZ4) | `LICENSES/python-lz4-BSD-3-Clause.txt`, `LICENSES/lz4-BSD-2-Clause.txt` |
| **zig** (aarch64-linux release binary, unchanged; its clang and lld) | 0.17.0 (LLVM 22.1.8) | `libzig.so` | MIT; LLVM: Apache 2.0 with LLVM Exceptions; musl (linked in): MIT | `LICENSES/zig-MIT.txt`, `LICENSES/LLVM-Apache-2.0-WITH-LLVM-exception.txt`, `LICENSES/musl-COPYRIGHT.txt` |
| **clang** compiler headers (from zig 0.17.0 x86_64-linux `lib/include`) | LLVM 22.1.8 | `assets/buildkit/cckit/clang/include/` | Apache 2.0 with LLVM Exceptions | `LICENSES/LLVM-Apache-2.0-WITH-LLVM-exception.txt` |
| **Android NDK** pieces: bionic headers, `libclang_rt.builtins-aarch64-android.a` | NDK 28.2.13676358 | `assets/buildkit/cckit/` | Apache 2.0 / BSD / MIT (see NOTICE) | `assets/buildkit/licenses/android-ndk-NOTICE.txt`, `android-ndk-toolchain-NOTICE.txt` (copied from the NDK at build time) |

**Source for the GPL / LGPL parts.** Unicorn (GPL-2.0) and FFmpeg (LGPL-2.1+) are distributed in binary form in the
APK. Their complete corresponding source - the unmodified upstream archives `unicorn-2.1.4.tar.gz` and
`ffmpeg-9.0.2.tar.xz`, plus the scripts that configured and built them (`tools/ci/build-buildkit.sh`,
`tools/ci/build-deps.sh`, `deps.lock`) - is published next to every APK as `letsgo-<version>-source.tar.gz`, on the same
release page.

Every APK built by `build-apk.sh` also carries this file and `LICENSES/` as `assets/licenses/`.

## 4. Documentation and references

Consulted as documentation of file formats, services or GPU instructions. For **Atmosphère** and **hactool**, a
line-by-line and token-level comparison of this repository against their sources found no copied code (only
standard constants such as the AES S-box, the SHA-256 round constants and FIPS test vectors, which both also contain):

- **Atmosphère** (https://github.com/Atmosphere-NX/Atmosphere, GPLv2) and **hactool** (https://github.com/SciresM/hactool,
  ISC): descriptions of the NSP/XCI/NCA/BKTR/IVFC/NSO formats and of the key derivation order cited in comments of the
  importer.

Other references (inherited from LAS Recomp; not compared with this repository):

- **Switchbrew** (https://switchbrew.org): system service documentation;
- **envytools** (https://github.com/envytools/envytools): NVIDIA GPU documentation;
- **Khronos Data Format specification** and the behaviour of ARM **astcenc**: the ASTC decoder (`src/runtime/nvn/astc.c`).

*Pokémon*, *Pokémon: Let's Go, Eevee!* and *Pokémon: Let's Go, Pikachu!* are trademarks of their respective owners.
