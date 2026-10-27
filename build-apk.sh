#!/bin/bash
# Assemble the Let's Go Recomp Android APK manually (no Gradle):
# javac -> d8 -> aapt2 -> zipalign -> apksigner (CaseWest debug keystore).
set -u
cd "$(dirname "$0")"
ROOT="$PWD"
SDK=${ANDROID_SDK_ROOT:-~/Android/Sdk}
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
KS=${LETSGO_KEYSTORE:-~/workspace/mvc2-xbox/apk-test/debug.keystore}

PKG=com.letsgorecomp
OUT=$ROOT/build/apk
rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/lib/arm64-v8a

# 1. java -> classes (our activity + SDL3's java)
find apk/java deps/SDL3/android-project/app/src/main/java -name "*.java" > $OUT/sources.txt
# (-classpath, not -bootclasspath: with JDK 17 the lambdas of the launcher need the JDK's LambdaMetafactory; d8 then
# turns the Java 8 bytecode into dex against android.jar)
javac -encoding UTF-8 -source 8 -target 8 -classpath $PLATFORM -d $OUT/classes \
    @$OUT/sources.txt 2>$OUT/javac.log || { echo JAVAC_FAIL; tail -5 $OUT/javac.log; exit 1; }

# 2. classes -> dex
$BT/d8 --min-api 28 --lib $PLATFORM --output $OUT/dex \
    $(find $OUT/classes -name "*.class") 2>$OUT/d8.log || { echo D8_FAIL; tail -5 $OUT/d8.log; exit 1; }

# 3. native libs (unversioned copies: the APK lib dir wants plain lib*.so names)
cp $ROOT/build/apk-lib/*.so $OUT/lib/arm64-v8a/ 2>/dev/null
# libmodunpack.so (build-modunpack.sh): the mod screen's unpacker (zip, rar, 7z). Without it, installing a mod fails.
[ -f $OUT/lib/arm64-v8a/libmodunpack.so ] || echo "WARNING: build/apk-lib/libmodunpack.so missing (./build-modunpack.sh): mods cannot be installed"
cp $ROOT/deps/SDL3/build-android/libSDL3.so $OUT/lib/arm64-v8a/
cp $ROOT/build/game/liblasgame.so $OUT/lib/arm64-v8a/ 2>/dev/null
# The runtime and the game library are one pair: the runtime refuses a liblasgame built from another generator
# ("was built for another version"). Both must come from this tree, with the same GAMELIB_ID.
GID=$(cat $ROOT/build/game/liblasgame.so.id 2>/dev/null)
[ -n "$GID" ] && grep -qaF "$GID" $OUT/lib/arm64-v8a/liblasrecomp.so && grep -qaF "$GID" $OUT/lib/arm64-v8a/liblasgame.so \
    || { echo "GAMELIB_ID_MISMATCH: liblasgame.so.id=$GID is not in both liblasrecomp.so and liblasgame.so"; exit 1; }
echo "GAMELIB_ID=$GID (runtime and game library)"
# Debug info out (the APK carries ~150 MB less); the symbols the runtime and the backtraces use stay.
STRIP=${ANDROID_NDK_HOME:-~/Android/Sdk/ndk/28.2.13676358}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
$STRIP --strip-debug $OUT/lib/arm64-v8a/liblasrecomp.so $OUT/lib/arm64-v8a/liblasgame.so || { echo STRIP_FAIL; exit 1; }
# FFmpeg shared libs: copy the .so files the runtime's DT_NEEDED references
# (libav* AND libsw*: libswscale/libswresample don't match libav*)
for f in $ROOT/deps/ffmpeg-android/lib/libav*.so $ROOT/deps/ffmpeg-android/lib/libsw*.so; do
    [ -f "$f" ] && [ ! -L "$f" ] && cp $f $OUT/lib/arm64-v8a/ 2>/dev/null
done
# C++ runtime (DT_NEEDED by the C++ parts: imgui, glslang remnants, game code)
cp ${ANDROID_NDK_HOME:-~/Android/Sdk/ndk/28.2.13676358}/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so $OUT/lib/arm64-v8a/ 2>/dev/null
ls $OUT/lib/arm64-v8a/

# 4. aapt2 link (manifest + res; dex + libs are added with zip below)
$BT/aapt2 compile --dir apk/res -o $OUT/res.zip 2>>$OUT/aapt.log \
    || { echo AAPT_COMPILE_FAIL; tail -10 $OUT/aapt.log; exit 1; }
$BT/aapt2 link -o $OUT/base.apk \
    --manifest apk/AndroidManifest.xml \
    --java $OUT/gen \
    -I $PLATFORM \
    -A apk/assets \
    --min-sdk-version 28 --target-sdk-version 34 \
    ${LETSGO_VERSION_CODE:+--version-code $LETSGO_VERSION_CODE --replace-version} \
    $OUT/res.zip \
    2>$OUT/aapt.log || { echo AAPT_FAIL; tail -10 $OUT/aapt.log; exit 1; }
cp $OUT/base.apk $OUT/unsigned.apk
# classes.dex at the apk root; .so files stored uncompressed under lib/arm64-v8a
cp $OUT/dex/classes.dex $OUT/classes.dex
cd $OUT && zip -q unsigned.apk classes.dex && zip -q -0 unsigned.apk lib/arm64-v8a/*.so && cd $ROOT

# 5. align (16KB page alignment for native libs, required on new Android) + sign
$BT/zipalign -f -P 16 4 $OUT/unsigned.apk $OUT/aligned.apk 2>>$OUT/aapt.log || { echo ALIGN_FAIL; exit 1; }
$BT/apksigner sign --ks $KS --ks-pass ${LETSGO_KS_PASS:-pass:android} --key-pass ${LETSGO_KEY_PASS:-${LETSGO_KS_PASS:-pass:android}} \
    --out $OUT/LetsGoRecomp-android.apk $OUT/aligned.apk 2>>$OUT/aapt.log || { echo SIGN_FAIL; exit 1; }
$BT/apksigner verify --print-certs $OUT/LetsGoRecomp-android.apk | grep -m1 SHA-256

cp $OUT/LetsGoRecomp-android.apk $ROOT/
ls -la $ROOT/LetsGoRecomp-android.apk
echo APK_OK
