#!/bin/bash
# Build the on-device game builder kit from the pinned upstream releases of deps.lock (tools/ci/fetch-deps.sh first),
# into build/kit/ - the directory tools/stage-buildkit.py reads with LETSGO_KIT_DIR=build/kit:
#   jniLibs/arm64-v8a/libzig.so          zig 0.17.0's aarch64-linux release binary, unchanged (clang + lld, static)
#   jniLibs/arm64-v8a/liblaspython.so    the `python` program (tools/ci/kit/pylauncher.c) for CPython's Android build
#   jniLibs/arm64-v8a/libpython3.14.so   from python.org's official aarch64-linux-android release
#   jniLibs/arm64-v8a/libcapstone.so     Capstone (ARM64 only), built here from its PyPI sdist
#   jniLibs/arm64-v8a/libunicorn.so      Unicorn (aarch64 only, GPL-2.0), built here from its PyPI sdist
#   pyhome/lib/python3.14/               CPython's standard library (trimmed) + site-packages: capstone, unicorn,
#                                        lz4 (its C extensions built here)
#   cckit/clang/include/                 the compiler headers the generated C includes (zig's clang)
#   cckit/sysroot/usr/include/           the bionic headers it includes (NDK)
#   cckit/clang/lib/libclang_rt.builtins-aarch64-android.a   (NDK)
#   licenses/                            NDK NOTICE files (the other texts are in LICENSES/)
#   kit.json                             what was built, from which pinned inputs
# Same recipe as Nycolazs/LASRecomp-Android-Plus android-v1.2.0 tools/android/ondevice/build_{python,zig,cckit}.sh
# (MIT), whose prebuilt kit this replaces, with this repository's own headers for the probe. No game content.
# Linux x86_64 host; Android NDK 28.2.13676358 ($ANDROID_NDK_HOME); cmake, ninja; python3.14 for the .pyc (optional).
set -euo pipefail
cd "$(dirname "$0")/../.."
ROOT=$PWD
NDK=${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME (NDK 28.2.13676358)}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64
API=30  # = apk/gamebuild/ondevice.py (-target aarch64-linux-android30)
CC=$TC/bin/aarch64-linux-android$API-clang
STRIP=$TC/bin/llvm-strip
JOBS=${JOBS:-$(nproc)}
B=$ROOT/build/kit-work
O=$ROOT/build/kit
L=$O/jniLibs/arm64-v8a
path() { python3 -I tools/ci/deps.py path "$1"; }
ver() { python3 -I tools/ci/deps.py list | awk -F'\t' -v n="$1" '$1 == n { print $2 }'; }
python3 -I tools/ci/deps.py fetch --offline cpython-android capstone unicorn lz4 zig-aarch64-linux zig-x86_64-linux \
    > /dev/null
PYVER=$(ver cpython-android); PYMM=${PYVER%.*}
CS=$(ver capstone) UC=$(ver unicorn) LZ=$(ver lz4) ZIG=$(ver zig-aarch64-linux)
[ "$PYMM" = 3.14 ] || { echo "kit: python $PYMM, but ondevice.py / jni.c expect python3.14"; exit 1; }
rm -rf "$O" "$B"
mkdir -p "$B/src" "$L" "$O/licenses"

tar -xzf "$(path cpython-android)" -C "$B" ./prefix ./README.md
tar -xzf "$(path capstone)" -C "$B/src"
tar -xzf "$(path unicorn)" -C "$B/src"
tar -xzf "$(path lz4)" -C "$B/src"
mkdir -p "$B/zig" "$B/zig-host"
tar -xJf "$(path zig-aarch64-linux)" -C "$B/zig" --strip-components 1
tar -xJf "$(path zig-x86_64-linux)" -C "$B/zig-host" --strip-components 1
PY=$B/prefix
PYINC=$PY/include/python$PYMM

ANDROID_CMAKE=(-G Ninja -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a
               -DANDROID_PLATFORM=android-$API -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON
               "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,--gc-sections"
               "-DCMAKE_C_FLAGS=-ffunction-sections -fdata-sections")
echo "== libcapstone.so $CS (ARM64 only)"
cmake -S "$B/src/capstone-$CS/src" -B "$B/obj/capstone" "${ANDROID_CMAKE[@]}" -DCAPSTONE_ARCHITECTURE_DEFAULT=OFF \
      -DCAPSTONE_ARM64_SUPPORT=ON -DCAPSTONE_BUILD_TESTS=OFF -DCAPSTONE_BUILD_CSTOOL=OFF -DBUILD_STATIC_LIBS=OFF \
      -DCAPSTONE_INSTALL=OFF > "$B/capstone.log"
cmake --build "$B/obj/capstone" -j"$JOBS" >> "$B/capstone.log" || { tail -30 "$B/capstone.log"; exit 1; }
echo "== libunicorn.so $UC (aarch64 only)"
PKG_CONFIG=/usr/bin/false cmake -S "$B/src/unicorn-$UC/src" -B "$B/obj/unicorn" "${ANDROID_CMAKE[@]}" \
      -DUNICORN_ARCH=aarch64 -DUNICORN_BUILD_TESTS=OFF -DUNICORN_INSTALL=OFF -DUNICORN_LEGACY_STATIC_ARCHIVE=OFF \
      > "$B/unicorn.log"
PKG_CONFIG=/usr/bin/false cmake --build "$B/obj/unicorn" -j"$JOBS" >> "$B/unicorn.log" || { tail -30 "$B/unicorn.log"; exit 1; }
cp "$(find "$B/obj/capstone" -name 'libcapstone.so*' -type f | head -1)" "$L/libcapstone.so"
cp "$(find "$B/obj/unicorn" -name 'libunicorn.so*' -type f | head -1)" "$L/libunicorn.so"
cp "$PY/lib/libpython$PYMM.so" "$L/"

echo "== liblaspython.so"
"$CC" -O2 -fPIE -pie -I"$PYINC" tools/ci/kit/pylauncher.c -o "$L/liblaspython.so" -L"$L" -lpython$PYMM \
      -Wl,-z,max-page-size=16384 '-Wl,-rpath,$ORIGIN'

echo "== zig $ZIG"
cp "$B/zig/zig" "$L/libzig.so"
cmp "$B/zig/zig" "$L/libzig.so"

echo "== python standard library (trimmed) + site-packages"
H=$O/pyhome/lib/python$PYMM
mkdir -p "$H/site-packages"
(cd "$PY/lib/python$PYMM" && tar -cf - --exclude test --exclude tests --exclude idlelib --exclude tkinter \
    --exclude turtledemo --exclude ensurepip --exclude venv --exclude pydoc_data --exclude lib2to3 --exclude sqlite3 \
    --exclude '__pycache__' --exclude "config-$PYMM-*" --exclude site-packages \
    --exclude '_test*.so' --exclude 'xxlimited*.so' --exclude '_ssl.*' --exclude '_hashlib.*' --exclude '_sqlite3.*' \
    --exclude '_tkinter*' --exclude '_ctypes_test*' --exclude '_xxtestfuzz*' --exclude '_interp*' .) | (cd "$H" && tar -xf -)
LZS=$B/src/lz4-$LZ
EXT=cpython-${PYMM/./}-aarch64-linux-android.so
ext() {  # <module path> <sources...>
    local out=$H/site-packages/$1.$EXT; shift
    mkdir -p "$(dirname "$out")"
    "$CC" -O3 -shared -fPIC -I"$PYINC" -I"$LZS/lz4libs" "$@" -o "$out" -L"$L" -lpython$PYMM -Wl,-z,max-page-size=16384
}
ext lz4/_version "$LZS/lz4/_version.c" "$LZS/lz4libs/lz4.c"
ext lz4/block/_block "$LZS/lz4/block/_block.c" "$LZS/lz4libs/lz4.c" "$LZS/lz4libs/lz4hc.c"
ext lz4/frame/_frame "$LZS/lz4/frame/_frame.c" "$LZS/lz4libs/lz4.c" "$LZS/lz4libs/lz4hc.c" "$LZS/lz4libs/lz4frame.c" \
    "$LZS/lz4libs/xxhash.c"
ext lz4/stream/_stream "$LZS/lz4/stream/_stream.c" "$LZS/lz4libs/lz4.c" "$LZS/lz4libs/lz4hc.c"
(cd "$LZS" && find lz4 -name '*.py' -exec sh -c 'mkdir -p "$1/$(dirname "$2")" && cp "$2" "$1/$2"' _ "$H/site-packages" {} \;)
# the bindings, pure Python: they find the libraries above by name (LIBCAPSTONE_PATH / LIBUNICORN_PATH, ondevice.py)
cp -R "$B/src/capstone-$CS/capstone" "$H/site-packages/"
cp -R "$B/src/unicorn-$UC/unicorn" "$H/site-packages/"
HOSTPY=$(command -v "python$PYMM" || true)
if [ -n "$HOSTPY" ]; then "$HOSTPY" -I -m compileall -q -j0 --invalidation-mode unchecked-hash "$H" > /dev/null
else echo "(no python$PYMM on this machine: no .pyc, the phone compiles them on first use)"; fi
"$STRIP" --strip-unneeded "$L/libcapstone.so" "$L/libunicorn.so" "$L/libpython$PYMM.so" "$L/liblaspython.so" \
    $(find "$H" -name '*.so')

echo "== cckit (headers found by probing this repository's generated-code headers with zig's clang)"
SYS=$TC/sysroot
ZH=$B/zig-host/zig
ZINC=$B/zig-host/lib/include
W=$B/probe; mkdir -p "$W"
FLAGS=(-O1 -fno-strict-aliasing -Wno-return-type -Wno-unused-label -fvisibility=hidden)
PROBE=("$ZH" clang -target aarch64-linux-android$API -nostdinc -isystem "$ZINC" -isystem "$SYS/usr/include"
       -isystem "$SYS/usr/include/aarch64-linux-android" -I"$ROOT/src/runtime")
printf '#include "cpu.h"\n#include "simd.h"\n' > "$W/probe.c"
ZIG_GLOBAL_CACHE_DIR=$W/cache ZIG_LOCAL_CACHE_DIR=$W/cache "${PROBE[@]}" "${FLAGS[@]}" -c "$W/probe.c" -o "$W/probe.o" \
    -MD -MF "$W/probe.d"
ZIG_GLOBAL_CACHE_DIR=$W/cache ZIG_LOCAL_CACHE_DIR=$W/cache "${PROBE[@]}" "${FLAGS[@]}" '-DGAMELIB_ID="0"' \
    -c "$ROOT/src/tools/gamelib_entry.c" -o "$W/entry.o" -MD -MF "$W/entry.d"
n=0
for h in $(cat "$W/probe.d" "$W/entry.d" | tr ' ' '\n' | grep '\.h$' | sort -u); do
    case $h in
        "$SYS"/*) rel=cckit/sysroot/${h#"$SYS"/} ;;
        "$ZINC"/*) rel=cckit/clang/include/${h#"$ZINC"/} ;;
        *) continue ;;  # (this repository's own headers: stage-buildkit.py copies them into cckit/gamelib-src)
    esac
    mkdir -p "$O/$(dirname "$rel")"
    cp "$h" "$O/$rel"
    n=$((n + 1))
done
[ "$n" -gt 10 ] || { echo "kit: probe found only $n system headers"; exit 1; }
RT=$(ls "$TC"/lib/clang/*/lib/linux/libclang_rt.builtins-aarch64-android.a | head -1)
mkdir -p "$O/cckit/clang/lib"
cp "$RT" "$O/cckit/clang/lib/"

echo "== licenses (NDK NOTICE files; everything else is in LICENSES/)"
cp "$NDK/NOTICE" "$O/licenses/android-ndk-NOTICE.txt"
cp "$NDK/NOTICE.toolchain" "$O/licenses/android-ndk-toolchain-NOTICE.txt"
cp "$B/README.md" "$O/licenses/README.python-android.md"

NDKREV=$(sed -n 's/^Pkg.Revision *= *//p' "$NDK/source.properties")
python3 -I - "$O" "$NDKREV" <<'PY'
import hashlib, json, pathlib, sys
o = pathlib.Path(sys.argv[1]); lock = pathlib.Path('deps.lock').read_bytes()
files = {str(p.relative_to(o)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(o.rglob('*')) if p.is_file()}
(o / 'kit.json').write_text(json.dumps({'origin': 'source', 'deps_lock_sha256': hashlib.sha256(lock).hexdigest(),
    'ndk': sys.argv[2], 'files': files}, indent=1, sort_keys=True) + '\n')
PY
echo "BUILDKIT_SOURCE_OK $O: $n system headers, $(find "$O" -type f | wc -l) files, $(du -sh "$O" | cut -f1)"
