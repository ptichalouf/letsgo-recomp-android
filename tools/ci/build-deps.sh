#!/bin/bash
# Build the native dependencies of the runtime from the pinned sources of deps.lock (run tools/ci/fetch-deps.sh
# first), into the deps/ layout that build-runtime.sh, link-runtime.sh, build-modunpack.sh and build-apk.sh expect:
#   deps/SDL3/{include,android-project}/ + deps/SDL3/build-android/libSDL3.so       SDL 3 (zlib)
#   deps/ffmpeg-android/{include,lib/libav*.so,lib/libsw*.so}                        FFmpeg, LGPL-only, shared
#   deps/shaderc/libshaderc/include + deps/shaderc/build-android/**.a                shaderc + glslang + SPIRV-Tools
#   deps/adrenotools/include + build/apk-lib/{libadrenotools,libhook_impl,libmain_hook}.so
#                                                                                    libadrenotools + liblinkernsbypass
#   deps/libarchive-3.8.1.tar.xz, deps/xz-5.8.1.tar.xz                               (build-modunpack.sh builds them)
# Linux x86_64 host, Android NDK 28.2.13676358 ($ANDROID_NDK_HOME), cmake, ninja, make, python3.
# Each part is skipped when its output exists (delete it to redo). Unmodified upstream sources: no patch is applied.
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$PWD
DEPS=$ROOT/deps
NDK=${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME (NDK 28.2.13676358)}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
JOBS=${JOBS:-$(nproc)}
API=26  # = build-runtime.sh / link-runtime.sh (the APK's minSdk is 28)
path() { python3 -I tools/ci/deps.py path "$1"; }
unpack() {  # <dep> <dest>: the archive's single top folder becomes <dest>
    rm -rf "$2" && mkdir -p "$2" && tar -xf "$(path "$1")" -C "$2" --strip-components 1
}
CMAKE_ANDROID=(-G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a
               -DCMAKE_BUILD_TYPE=Release "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384")
python3 -I tools/ci/deps.py fetch --offline sdl3 ffmpeg shaderc glslang spirv-tools spirv-headers libadrenotools \
    liblinkernsbypass libarchive xz > /dev/null
mkdir -p "$DEPS" "$ROOT/build/apk-lib"

# --- SDL 3 -------------------------------------------------------------------------------------------------------
if [ ! -f "$DEPS/SDL3/build-android/libSDL3.so" ]; then
    echo "== SDL3 $(grep -m1 '^dep  | sdl3 ' deps.lock | cut -d'|' -f3)"
    unpack sdl3 "$DEPS/SDL3"
    cmake -S "$DEPS/SDL3" -B "$DEPS/SDL3/build-android" "${CMAKE_ANDROID[@]}" -DANDROID_PLATFORM=android-$API \
        -DANDROID_STL=c++_shared -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF -DSDL_TESTS=OFF \
        -DSDL_EXAMPLES=OFF -DSDL_INSTALL=OFF > "$DEPS/sdl3.log"
    cmake --build "$DEPS/SDL3/build-android" -j"$JOBS" >> "$DEPS/sdl3.log" || { tail -30 "$DEPS/sdl3.log"; exit 1; }
    [ -f "$DEPS/SDL3/build-android/libSDL3.so" ] || { echo "SDL3: no libSDL3.so"; exit 1; }
    [ -d "$DEPS/SDL3/android-project/app/src/main/java/org/libsdl/app" ] || { echo "SDL3: no Java glue"; exit 1; }
fi

# --- FFmpeg: shared, LGPL only (no --enable-gpl / --enable-nonfree), just what the game needs ---------------------
# Same component list as src/tools/build_deps_windows.sh (movies: WebM VP9/VP8 + Vorbis/Opus, MP4 H.264/AAC; hwopus:
# Opus, which needs swresample). Never strip libavcodec.so (BUILD.md): build-apk.sh copies it as built.
FF=$DEPS/ffmpeg-android
if [ ! -f "$FF/lib/libavcodec.so" ]; then
    echo "== FFmpeg"
    unpack ffmpeg "$DEPS/ffmpeg"
    (cd "$DEPS/ffmpeg" && ./configure --prefix="$FF" --enable-cross-compile --target-os=android --arch=aarch64 \
        --cpu=armv8-a --cc="$TC/bin/aarch64-linux-android$API-clang" --cxx="$TC/bin/aarch64-linux-android$API-clang++" \
        --nm="$TC/bin/llvm-nm" --ar="$TC/bin/llvm-ar" --ranlib="$TC/bin/llvm-ranlib" --strip="$TC/bin/llvm-strip" \
        --enable-shared --disable-static --enable-pic --extra-ldflags="-Wl,-z,max-page-size=16384" \
        --disable-autodetect --disable-programs --disable-doc --disable-network --disable-avdevice --disable-avfilter \
        --disable-everything \
        --enable-protocol=file --enable-demuxer=matroska,mov,ogg \
        --enable-decoder=vp9,vp8,vorbis,opus,h264,aac,mp3 --enable-parser=vp9,vp8,vorbis,opus,h264,aac \
        > "$DEPS/ffmpeg.log" && make -j"$JOBS" >> "$DEPS/ffmpeg.log" && make install >> "$DEPS/ffmpeg.log") \
        || { tail -30 "$DEPS/ffmpeg.log"; exit 1; }
fi
# config.h records the license configure settled on (FFMPEG_LICENSE) and the exact configuration (FFMPEG_CONFIGURATION).
grep -q '^#define FFMPEG_LICENSE "LGPL version 2.1 or later"$' "$DEPS/ffmpeg/config.h" || { echo "FFmpeg is not LGPL-2.1+"; exit 1; }
grep -q 'enable-gpl\|enable-nonfree\|enable-version3' "$DEPS/ffmpeg/config.h" && { echo "FFmpeg: GPL/nonfree option"; exit 1; }
for c in opus vorbis vp9 vp8 h264 aac; do  # (configure drops a decoder silently when a dependency is missing)
    C=$(echo "$c" | tr a-z A-Z)
    grep -q "^#define CONFIG_${C}_DECODER 1" "$DEPS/ffmpeg/config_components.h" || { echo "FFmpeg: no $c decoder"; exit 1; }
done

# --- shaderc (static; glslang, SPIRV-Tools, SPIRV-Headers at the revisions of shaderc's DEPS) ----------------------
SC=$DEPS/shaderc
if [ ! -f "$SC/build-android/libshaderc/libshaderc.a" ]; then
    echo "== shaderc"
    unpack shaderc "$SC"
    unpack glslang "$SC/third_party/glslang"
    unpack spirv-tools "$SC/third_party/spirv-tools"
    unpack spirv-headers "$SC/third_party/spirv-headers"
    cmake -S "$SC" -B "$SC/build-android" "${CMAKE_ANDROID[@]}" -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_shared \
        -DSHADERC_SKIP_TESTS=ON -DSHADERC_SKIP_EXAMPLES=ON -DSHADERC_SKIP_COPYRIGHT_CHECK=ON \
        -DSPIRV_SKIP_TESTS=ON -DSPIRV_SKIP_EXECUTABLES=ON -DSPIRV_WERROR=OFF -DENABLE_GLSLANG_BINARIES=OFF \
        -DGLSLANG_TESTS=OFF -DBUILD_SHARED_LIBS=OFF -DPython3_EXECUTABLE="$(command -v python3)" > "$DEPS/shaderc.log"
    cmake --build "$SC/build-android" --target shaderc -j"$JOBS" >> "$DEPS/shaderc.log" \
        || { tail -30 "$DEPS/shaderc.log"; exit 1; }
fi
[ -f "$SC/build-android/libshaderc_util/libshaderc_util.a" ] || { echo "shaderc: no libshaderc_util.a"; exit 1; }
find "$SC/build-android/third_party/spirv-tools" -name 'libSPIRV-Tools*.a' | grep -q . || { echo "shaderc: no SPIRV-Tools"; exit 1; }
find "$SC/build-android/third_party/glslang" -name '*.a' | grep -q . || { echo "shaderc: no glslang"; exit 1; }

# --- libadrenotools + liblinkernsbypass (shared adrenotools, its two hook libraries) -------------------------------
AT=$DEPS/adrenotools
if [ ! -f "$AT/build-android/libadrenotools.so" ]; then
    echo "== libadrenotools"
    unpack libadrenotools "$AT"
    unpack liblinkernsbypass "$AT/lib/linkernsbypass"
    cmake -S "$AT" -B "$AT/build-android" "${CMAKE_ANDROID[@]}" -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static \
        -DBUILD_SHARED_LIBS=ON > "$DEPS/adrenotools.log"
    cmake --build "$AT/build-android" --target adrenotools hook_impl main_hook -j"$JOBS" >> "$DEPS/adrenotools.log" \
        || { tail -30 "$DEPS/adrenotools.log"; exit 1; }
fi
for l in libadrenotools.so src/hook/libhook_impl.so src/hook/libmain_hook.so; do
    cp "$AT/build-android/$l" "$ROOT/build/apk-lib/"
done

# --- libarchive / xz: build-modunpack.sh wants the release tarballs in deps/ ----------------------------------------
cp "$(path libarchive)" "$DEPS/libarchive-3.8.1.tar.xz"
cp "$(path xz)" "$DEPS/xz-5.8.1.tar.xz"
echo "DEPS_OK"
