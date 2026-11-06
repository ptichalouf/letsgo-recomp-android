#!/bin/bash
# Source this file to get the toolchain the Android tests expect.
#
#   source tools/env.sh
#
# It only sets variables; it installs nothing and downloads nothing. Adjust the
# paths to your own machine if your SDK lives elsewhere.

# --- JDK ---------------------------------------------------------------------
# Any JDK 17 works. On a Debian/Ubuntu machine: `apt install openjdk-17-jdk`.
if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
fi

# --- Android SDK -------------------------------------------------------------
# Needs: platform-tools, platforms;android-34, build-tools;34.0.0 (or newer),
# and the NDK (r28c or newer).
if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -d "${HOME}/Android/Sdk" ]; then
    export ANDROID_SDK_ROOT="${HOME}/Android/Sdk"
fi

# --- NDK ---------------------------------------------------------------------
# The NDK is installed inside the SDK by default.
if [ -z "${ANDROID_NDK_HOME:-}" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    for candidate in "${ANDROID_SDK_ROOT}"/ndk/*; do
        if [ -d "$candidate" ]; then
            export ANDROID_NDK_HOME="$candidate"
            break
        fi
    done
fi

if [ -n "${JAVA_HOME:-}" ]; then
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi
if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    export PATH="${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"
fi

# --- On-device builder kit (optional) ---------------------------------------
# Built from the pinned sources of deps.lock: tools/ci/fetch-deps.sh, tools/ci/build-deps.sh,
# tools/ci/build-buildkit.sh, then LETSGO_KIT_DIR=$PWD/build/kit (what apk.yml / release.yml do).
# LETSGO_KIT_SOURCE (a directory holding the Nycolazs `upstream.apk`) still works for local comparison only;
# never distribute an APK built that way.
if [ -z "${LETSGO_KIT_DIR:-}" ] && [ -f "$(dirname "${BASH_SOURCE[0]}")/../build/kit/kit.json" ]; then
    LETSGO_KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../build/kit" && pwd)"
    export LETSGO_KIT_DIR
fi

cat <<EOF
Environment:
  JAVA_HOME        = ${JAVA_HOME:-<not set>}
  ANDROID_SDK_ROOT = ${ANDROID_SDK_ROOT:-<not set>}
  ANDROID_NDK_HOME = ${ANDROID_NDK_HOME:-<not set>}
  LETSGO_KIT_DIR   = ${LETSGO_KIT_DIR:-<not set>}

Then run:
  python3 -m unittest discover -s tests -v
  python3 -m unittest discover -s apk/test -p 'test_*.py' -v
EOF
