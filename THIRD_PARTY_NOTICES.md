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
| **On-device NSP/XCI importer** from SwitchGamesRecomp / *Super Mario Bros. Wonder Recomp* by BelmanteGu, as found in **p5r-android** (https://github.com/Brandon04Gonzalez/p5r-android, `android/wonder/importer/`) | `importer/nximport.cpp`, `importer/nximport.h`, `importer/nximport_jni.cpp`, `importer/nxcrypto.cpp`, `importer/nxcrypto.h`, `apk/importer/wonder_nximport.cpp`, `apk/importer/nxcrypto.cpp`, `apk/importer/nxcrypto.h`, parts of `apk/importer/lgimport.cpp` | MIT | Copyright (c) 2026 BelmanteGu; p5r-android also lists Copyright (c) 2026 Brandon | `LICENSES/SwitchGamesRecomp-BelmanteGu-MIT.txt`, `LICENSES/p5r-android-MIT.txt` |
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
| **Mesa Turnip** Vulkan driver (`libvulkan_freedreno.so` + `meta.json`), packaged for AdrenoTools by The412Banner | `apk/assets/turnip-8g2.zip` | Byte-identical to the release asset `Turnip-v26.3.0-20261015-r5-8G2-OneUI.zip` of https://github.com/The412Banner/Banners-Turnip/releases/tag/v26.3.0-20261015-r5 (SHA-256 `043d22df9176f9a05aee0ebebfe8b55909f1db617406c4dbc40617b201a52ab3`). Built from Mesa commit `cf98950a3f548751301091ae4a224b8c8f59d988` (https://gitlab.freedesktop.org/mesa/mesa/-/commit/cf98950a3f548751301091ae4a224b8c8f59d988) with the packager's `patches/8g2_oneui.py` (A740 `TP_UBWC_FLAG_HINT`). | Mesa: MIT for the core and the freedreno/turnip driver; individual files may carry other licenses (see Mesa `docs/license.rst` and `licenses/`) | `LICENSES/Mesa-license.rst`, `LICENSES/Mesa-licenses-MIT.txt` |
| **DejaVu Sans** font | `apk/assets/fonts/DejaVuSans.ttf` | 2.37 (https://github.com/dejavu-fonts/dejavu-fonts/releases/tag/version_2_37) | Bitstream Vera Fonts license; DejaVu changes in the public domain; Arev glyphs (c) Tavmjong Bah | `LICENSES/DejaVu-Fonts-License.txt` |

## 3. Libraries linked into the Android build (not stored in this repository)

The build scripts (`build-runtime.sh`, `link-runtime.sh`, `build-apk.sh`, `rebuild-ffmpeg-opus.sh`) fetch nothing:
they expect these under `deps/`. Builds that ship them must carry these notices and, for FFmpeg, its source.

| Component | How it is used | License | License text |
|---|---|---|---|
| **SDL 3** (incl. its Android Java glue) | `libSDL3.so` (shared) + Java classes | zlib | `LICENSES/SDL3-zlib.txt` |
| **FFmpeg** (`libavformat`, `libavcodec`, `libavutil`, `libswscale`, `libswresample`) | shared libraries, configured without `--enable-gpl` / `--enable-nonfree` (`rebuild-ffmpeg-opus.sh`) | LGPL 2.1 or later | `LICENSES/FFmpeg-LGPL-2.1.txt`, `LICENSES/FFmpeg-LICENSE.md` |
| **shaderc**, **SPIRV-Tools** | static, inside `liblasrecomp.so` | Apache 2.0 | `LICENSES/shaderc-Apache-2.0.txt`, `LICENSES/SPIRV-Tools-Apache-2.0.txt` |
| **glslang** | static, inside `liblasrecomp.so` | BSD-3-Clause / BSD-2-Clause / MIT / Apache 2.0 (see file) | `LICENSES/glslang-LICENSE.txt` |
| **libadrenotools** + **liblinkernsbypass** | `libadrenotools.so` (shared) | BSD-2-Clause | `LICENSES/libadrenotools-BSD-2-Clause.txt`, `LICENSES/liblinkernsbypass-BSD-2-Clause.txt` |
| **LLVM libc++** (`libc++_shared.so` from the Android NDK 28.2.13676358) | shared | Apache 2.0 with LLVM Exceptions | `LICENSES/LLVM-libcxx-Apache-2.0-WITH-LLVM-exception.txt` |

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
