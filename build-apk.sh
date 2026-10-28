#!/bin/bash
# Assemble the Android APK manually. GAME=none is generic; GAME=with requires a matching game library.
set -euo pipefail
cd "$(dirname "$0")"
ROOT="$PWD"
if [ -n "${LETSGO_KIT_SOURCE:-}" ]; then
    python3 tools/stage-buildkit.py
    LETSGO_BUILD_ASSETS="$ROOT/build/build-assets"
fi
SDK=${ANDROID_SDK_ROOT:-~/Android/Sdk}
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
KS=${LETSGO_KEYSTORE:-~/workspace/mvc2-xbox/apk-test/debug.keystore}
GAME=${GAME:-none}
case "$GAME" in
    none|with) ;;
    *) echo "GAME_MODE_INVALID: expected GAME=none or GAME=with"; exit 1 ;;
esac
if [ "$GAME" = with ] && [ ! -s "$ROOT/build/game/liblasgame.so" ]; then
    echo "GAME_LIBRARY_MISSING: GAME=with requires build/game/liblasgame.so"; exit 1
fi

if [ -f "$ROOT/apk/java/com/letsgorecomp/GameBuild.java" ]; then
    [ -f "${LETSGO_BUILD_ASSETS:-}/buildkit/config.json" ] && [ -s "$ROOT/build/apk-lib/libgamebuilder.so" ] || {
        echo BUILD_KIT_MISSING; exit 1;
    }
    kit_id=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["gamelib_id"])' "$LETSGO_BUILD_ASSETS/buildkit/config.json")
    grep -qaF "$kit_id" "$ROOT/build/apk-lib/liblasrecomp.so" || { echo BUILD_KIT_RUNTIME_ID_MISMATCH; exit 1; }
fi

# Reject game/secret/cache payloads before aapt2 or signing. This is a deny-list,
# not a legal audit: shipped tools still require provenance review.
if [ "$GAME" = none ]; then
    python3 - "$ROOT/apk/assets" "${LETSGO_BUILD_ASSETS:-}" <<'PY'
import pathlib, re, sys
for asset_root in filter(None, sys.argv[1:]):
 root = pathlib.Path(asset_root)
 for p in root.rglob('*'):
    name = p.name.lower()
    parts = {x.lower() for x in p.relative_to(root).parts}
    if (p.is_symlink() or parts & {'exefs', 'romfs', 'generated-aarch64', 'shader-cache'}
        or name in {'romfs.bin', 'prod.keys', 'title.keys', 'liblasgame.so', 'lasgame.so'}
        or p.suffix.lower() in {'.nsp', '.xci', '.nca', '.nso', '.spv', '.o'}
        or re.fullmatch(r'(main|sdk|subsdk[01])_\d+\.c', name)):
        sys.exit('APK_GAME_PAYLOAD_FORBIDDEN: ' + str(p.relative_to(root)))
PY
fi

OUT=$ROOT/build/apk
# NFS over Unraid FUSE may retain hidden open files after unlink. Keep the
# previous staging directory as build evidence instead of a destructive rm -rf.
if [ -e "$OUT" ]; then
    history=$(mktemp -d "$ROOT/build/apk-previous.XXXXXX")
    mv "$OUT" "$history/staging"
fi
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/lib/arm64-v8a"
echo "APK_GAME_MODE=$GAME"

# 1. java -> classes (our activity + SDL3's java)
find apk/java deps/SDL3/android-project/app/src/main/java -name '*.java' > "$OUT/sources.txt"
# Use Java 8's platform signatures for invokedynamic/lambda bootstrap methods;
# Android's stub bootclasspath does not define LambdaMetafactory.metafactory.
javac -encoding UTF-8 --release 8 -classpath "$PLATFORM" -d "$OUT/classes" \
    @"$OUT/sources.txt" 2>"$OUT/javac.log" || { echo JAVAC_FAIL; tail -5 "$OUT/javac.log"; exit 1; }

# 2. classes -> dex
mapfile -d '' -t classes < <(find "$OUT/classes" -name '*.class' -print0)
"$BT/d8" --min-api 28 --lib "$PLATFORM" --output "$OUT/dex" \
    "${classes[@]}" 2>"$OUT/d8.log" || { echo D8_FAIL; tail -5 "$OUT/d8.log"; exit 1; }

# 3. native libs. Never let a stale game library in apk-lib override the declared mode.
for f in "$ROOT"/build/apk-lib/*.so; do
    [ "${f##*/}" = liblasgame.so ] && continue
    cp "$f" "$OUT/lib/arm64-v8a/"
done
cp "$ROOT/deps/SDL3/build-android/libSDL3.so" "$OUT/lib/arm64-v8a/"
strip_libs=("$OUT/lib/arm64-v8a/liblasrecomp.so")
if [ "$GAME" = with ]; then
    cp "$ROOT/build/game/liblasgame.so" "$OUT/lib/arm64-v8a/"
    # Runtime and game are one pair; none mode has no game ID to check.
    GID=$(cat "$ROOT/build/game/liblasgame.so.id" 2>/dev/null) || GID=''
    if ! [[ "$GID" =~ ^[0-9a-f]{16}$ ]] ||
        ! grep -qaF "$GID" "$OUT/lib/arm64-v8a/liblasrecomp.so" ||
        ! grep -qaF "$GID" "$OUT/lib/arm64-v8a/liblasgame.so"; then
        echo "GAMELIB_ID_MISMATCH: ID must be present in runtime and game library"; exit 1
    fi
    echo "GAMELIB_ID=$GID (runtime and game library)"
    strip_libs+=("$OUT/lib/arm64-v8a/liblasgame.so")
fi
STRIP=${ANDROID_NDK_HOME:-~/Android/Sdk/ndk/28.2.13676358}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
"$STRIP" --strip-debug "${strip_libs[@]}" || { echo STRIP_FAIL; exit 1; }
for f in "$ROOT"/deps/ffmpeg-android/lib/libav*.so "$ROOT"/deps/ffmpeg-android/lib/libsw*.so; do
    if [ -f "$f" ] && [ ! -L "$f" ]; then cp "$f" "$OUT/lib/arm64-v8a/"; fi
done
cp "${ANDROID_NDK_HOME:-~/Android/Sdk/ndk/28.2.13676358}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" "$OUT/lib/arm64-v8a/"

# 4. manifest + resources; dex + native libraries are added below.
"$BT/aapt2" compile --dir apk/res -o "$OUT/res.zip" 2>>"$OUT/aapt.log" \
    || { echo AAPT_COMPILE_FAIL; tail -10 "$OUT/aapt.log"; exit 1; }
version=()
if [ "${LETSGO_DEBUG:-0}" = 1 ]; then version+=(--debug-mode); fi
if [ -n "${LETSGO_VERSION_CODE:-}" ]; then version+=(--version-code "$LETSGO_VERSION_CODE" --replace-version); fi
"$BT/aapt2" link -o "$OUT/base.apk" --manifest apk/AndroidManifest.xml \
    --java "$OUT/gen" -I "$PLATFORM" -A apk/assets ${LETSGO_BUILD_ASSETS:+-A "$LETSGO_BUILD_ASSETS"} \
    --min-sdk-version 28 --target-sdk-version 34 "${version[@]}" "$OUT/res.zip" \
    2>"$OUT/aapt.log" || { echo AAPT_FAIL; tail -10 "$OUT/aapt.log"; exit 1; }
cp "$OUT/base.apk" "$OUT/unsigned.apk"
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
(cd "$OUT"; zip -q unsigned.apk classes.dex; zip -q -0 unsigned.apk lib/arm64-v8a/*.so)

# 5. align + sign, then check the FINAL archive, not just the staging directory.
"$BT/zipalign" -f -P 16 4 "$OUT/unsigned.apk" "$OUT/aligned.apk" 2>>"$OUT/aapt.log" || { echo ALIGN_FAIL; exit 1; }
"$BT/apksigner" sign --ks "$KS" --ks-pass "${LETSGO_KS_PASS:-pass:android}" \
    --key-pass "${LETSGO_KEY_PASS:-${LETSGO_KS_PASS:-pass:android}}" \
    --out "$OUT/LetsGoRecomp-android.apk" "$OUT/aligned.apk" 2>>"$OUT/aapt.log" || { echo SIGN_FAIL; exit 1; }
"$BT/apksigner" verify --print-certs "$OUT/LetsGoRecomp-android.apk" > "$OUT/signature.txt"
grep -m1 SHA-256 "$OUT/signature.txt"
unzip -Z1 "$OUT/LetsGoRecomp-android.apk" > "$OUT/entries.txt"
if [ "$GAME" = none ]; then
    if grep -qE '(^|/)liblasgame\.so$' "$OUT/entries.txt"; then
        echo APK_NO_GAME_FAIL; exit 1
    fi
    echo APK_NO_GAME_OK
else
    grep -qx 'lib/arm64-v8a/liblasgame.so' "$OUT/entries.txt" || { echo APK_WITH_GAME_FAIL; exit 1; }
    echo APK_WITH_GAME_OK
fi
APK_DEST=${LETSGO_APK_DEST:-$ROOT/LetsGoRecomp-android.apk}
cp "$OUT/LetsGoRecomp-android.apk" "$APK_DEST"
ls -la "$APK_DEST"
echo APK_OK
