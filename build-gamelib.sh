#!/bin/bash
# Build generated Android arm64 C. Every child must succeed before linking/activation.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"
SRC="$ROOT/src"
NDK=${ANDROID_NDK_HOME:-~/Android/Sdk/ndk/28.2.13676358}
TC=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
CC=$TC/aarch64-linux-android26-clang
GEN=$SRC/generated-aarch64
[ -d "$GEN/main" ] || { echo 'run make gen first'; exit 1; }
JOBS=${JOBS:-2}
[[ "$JOBS" =~ ^[1-9][0-9]*$ ]] && [ "$JOBS" -le 64 ] || { echo JOBS_INVALID; exit 1; }

# Exact Makefile identity recipe: file concatenation in this order + flags + LF.
GENFLAGS='-O1 -fno-strict-aliasing -Wno-return-type -Wno-unused-label -fvisibility=hidden -Iruntime'
GAMELIB_SRCS=(tools/gen.py tools/lifter.py tools/analyze.py tools/nso.py tools/passes.py tools/patches.py tools/simd_c.py tools/gamelib_entry.c runtime/hle/hle_symbols.txt runtime/cpu.h runtime/simd.h runtime/simd_fp.h runtime/gamelib.h)
for f in "${GAMELIB_SRCS[@]}"; do
    [ -r "$SRC/$f" ] || { echo "GAMELIB_ID_INPUT_MISSING: $f"; exit 1; }
done
GAMELIB_ID=$({ (cd "$SRC"; cat "${GAMELIB_SRCS[@]}") || exit 1; printf '%s\n' "$GENFLAGS"; } | sha256sum)
GAMELIB_ID=${GAMELIB_ID:0:16}
echo "GAMELIB_ID=$GAMELIB_ID"
read -r -a flags <<< "$GENFLAGS"
# Cache identity includes compiler contents/version-independent binary hash, flags, ID and source contents.
compiler_hash=$(sha256sum "$CC")
OUTDIR=$ROOT/build/game
mkdir -p "$OUTDIR/obj"
: > build-gamelib-errors.log
mapfile -d '' -t sources < <(find "$GEN" -name '*.c' -print0 | sort -z)
sources+=("$SRC/tools/gamelib_entry.c")
objects=()
pids=()
fail=0
wait_batch() {
    local pid
    for pid in "${pids[@]}"; do
        if ! wait "$pid"; then fail=1; fi
    done
    pids=()
}
# No background compiler may outlive an interrupted build script.
cleanup() {
    local pid
    for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
    for pid in "${pids[@]}"; do wait "$pid" 2>/dev/null || true; done
    rm -f "$OUTDIR/liblasgame.so.part" "$OUTDIR/liblasgame.so.id.part"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
for f in "${sources[@]}"; do
    key=$({ printf '%s\n' "$compiler_hash" "$GENFLAGS" "$GAMELIB_ID" "$f"; cat "$f"; } | sha256sum)
    obj=$OUTDIR/obj/${key:0:64}.o
    objects+=("$obj")
    [ -s "$obj" ] && continue
    (
        child=''
        trap 'if [ -n "$child" ]; then kill "$child" 2>/dev/null || true; wait "$child" 2>/dev/null || true; fi; exit 143' TERM INT
        "$CC" "${flags[@]}" "-DGAMELIB_ID=\"$GAMELIB_ID\"" -I"$SRC/runtime" -c "$f" -o "$obj.part" 2>>build-gamelib-errors.log &
        child=$!
        if wait "$child"; then
            mv "$obj.part" "$obj"
        else
            rm -f "$obj.part"
            echo "FAIL: $f"
            exit 1
        fi
    ) &
    pids+=("$!")
    if [ "${#pids[@]}" -ge "$JOBS" ]; then wait_batch; fi
    [ "$fail" -eq 0 ] || break
done
wait_batch
if [ "$fail" -ne 0 ]; then
    echo GAMELIB_BUILD_FAIL
    grep -m5 'error:' build-gamelib-errors.log || true
    exit 1
fi
for obj in "${objects[@]}"; do
    [ -s "$obj" ] || { echo "GAMELIB_OBJECT_MISSING: $obj"; exit 1; }
done
echo "objects: ${#objects[@]}/${#sources[@]}"
# Link only this build's exact object list, never stale *.o files. Dependency required by RTLD_LOCAL.
"$CC" -shared -o "$OUTDIR/liblasgame.so.part" "${objects[@]}" \
    -L"$ROOT/build/apk-lib" -l:liblasrecomp.so 2>>build-gamelib-errors.log || { echo LINK_FAIL; exit 1; }
printf '%s' "$GAMELIB_ID" > "$OUTDIR/liblasgame.so.id.part"
mv "$OUTDIR/liblasgame.so.part" "$OUTDIR/liblasgame.so"
mv "$OUTDIR/liblasgame.so.id.part" "$OUTDIR/liblasgame.so.id"
printf '%s\n' "$GAMELIB_ID" > "$ROOT/GAMELIB_ID.txt"
ls -la "$OUTDIR/liblasgame.so"
echo GAMELIB_OK
