#!/bin/bash
# Build libmodunpack.so (on-device mod unpacking: apk/modunpack/modunpack.c) for Android arm64, into build/apk-lib/
# (build-apk.sh packs every .so there).
# libarchive 3.8.1 (zip, rar, rar5, 7zip readers) and liblzma 5.8.1 (7z's LZMA/LZMA2) are built from their release
# tarballs in deps/ (not in this repo, like SDL3 and FFmpeg: see BUILD.md); zlib is the system's (NDK libz.so).
# No network: put the two tarballs in deps/ first; their SHA-256 are checked.
set -eu
cd "$(dirname "$0")"
ROOT="$PWD"
NDK=${ANDROID_NDK_HOME:-~/Android/Sdk/ndk/28.2.13676358}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
TCF=$NDK/build/cmake/android.toolchain.cmake
DEPS=$ROOT/deps
B=$ROOT/build/modunpack
LA=libarchive-3.8.1 XZ=xz-5.8.1
declare -A SHA=([$LA.tar.xz]=19f917d42d530f98815ac824d90c7eaf648e9d9a50e4f309c812457ffa5496b5
                [$XZ.tar.xz]=0b54f79df85912504de0b14aec7971e3f964491af1812d83447005807513cd9e)
mkdir -p $B $ROOT/build/apk-lib
for t in "${!SHA[@]}"; do
    [ -f $DEPS/$t ] || { echo "missing $DEPS/$t (https://github.com/libarchive/libarchive/releases, https://github.com/tukaani-project/xz/releases)"; exit 1; }
    echo "${SHA[$t]}  $DEPS/$t" | sha256sum -c --quiet || { echo "SHA-256 mismatch: $DEPS/$t"; exit 1; }
    [ -d $B/${t%.tar.xz} ] || tar -C $B -xf $DEPS/$t
done
A=(-DCMAKE_TOOLCHAIN_FILE=$TCF -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DCMAKE_BUILD_TYPE=MinSizeRel
   -DBUILD_SHARED_LIBS=OFF "-DCMAKE_C_FLAGS=-ffunction-sections -fdata-sections -fPIC")
if [ ! -f $B/inst/lib/liblzma.a ]; then
    cmake -S $B/$XZ -B $B/b-xz "${A[@]}" -DCMAKE_INSTALL_PREFIX=$B/inst -DXZ_THREADS=no -DXZ_NLS=OFF -DXZ_DOC=OFF \
        -DXZ_TOOL_XZ=OFF -DXZ_TOOL_XZDEC=OFF -DXZ_TOOL_LZMADEC=OFF -DXZ_TOOL_LZMAINFO=OFF -DXZ_TOOL_SCRIPTS=OFF \
        -DXZ_TOOL_SYMLINKS=OFF -DXZ_TOOL_SYMLINKS_LZMA=OFF -DXZ_DOXYGEN=OFF -DBUILD_TESTING=OFF > $B/xz.log 2>&1
    cmake --build $B/b-xz -j${JOBS:-4} >> $B/xz.log 2>&1 && cmake --install $B/b-xz >> $B/xz.log 2>&1 \
        || { echo "liblzma build failed: $B/xz.log"; exit 1; }
fi
if [ ! -f $B/b-la/libarchive/libarchive.a ]; then
    OFF=""
    for o in OPENSSL MBEDTLS NETTLE LIBB2 LZ4 LZO ZSTD BZip2 LIBXML2 EXPAT PCREPOSIX PCRE2POSIX LIBGCC CNG TAR CPIO CAT \
             UNZIP XATTR ACL ICONV TEST WERROR; do OFF="$OFF -DENABLE_$o=OFF"; done
    cmake -S $B/$LA -B $B/b-la "${A[@]}" $OFF -DENABLE_ZLIB=ON -DENABLE_LZMA=ON -DCMAKE_PREFIX_PATH=$B/inst \
        -DCMAKE_FIND_ROOT_PATH=$B/inst > $B/la.log 2>&1
    grep -q "Found LibLZMA" $B/la.log || { echo "libarchive: liblzma not found ($B/la.log)"; exit 1; }
    cmake --build $B/b-la -j${JOBS:-4} --target archive_static >> $B/la.log 2>&1 || { echo "libarchive build failed: $B/la.log"; exit 1; }
fi
$TC/aarch64-linux-android28-clang -Os -fPIC -ffunction-sections -fdata-sections -Wall -Wextra -Wno-format-truncation \
    -DMODUNPACK_JNI -I$ROOT/apk/modunpack -I$B/$LA/libarchive -shared -o $ROOT/build/apk-lib/libmodunpack.so \
    $ROOT/apk/modunpack/modunpack.c $B/b-la/libarchive/libarchive.a $B/inst/lib/liblzma.a -lz \
    -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384 \
    -Wl,--version-script=<(printf '{ global: Java_*; local: *; };')
$TC/llvm-strip --strip-all $ROOT/build/apk-lib/libmodunpack.so
ls -l $ROOT/build/apk-lib/libmodunpack.so
echo "MODUNPACK_OK"
