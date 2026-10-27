#!/usr/bin/env python3
"""Compile launcher + SDL Java and run the three host suites, without building an APK.

Needs JDK17, Android platform jar and SDL Java sources. Override LETSGO_JDK,
LETSGO_ANDROID_JAR, LETSGO_SDL_JAVA for another host. No device/network access.
The evidence directory is printed and can be fixed with LETSGO_EVIDENCE_DIR.
"""
import os
from pathlib import Path
import subprocess
import tempfile

REPO = Path(__file__).resolve().parents[3]
EVIDENCE = Path(os.environ.get("LETSGO_EVIDENCE_DIR") or tempfile.mkdtemp(prefix="letsgo-java-"))
JDK = Path(os.environ.get("LETSGO_JDK", "/opt/data/cache/scratch/jdk/jdk-17.0.21+9")) / "bin"
ANDROID = os.environ.get("LETSGO_ANDROID_JAR", "/opt/data/cache/scratch/android-sdk/platforms/android-34/android.jar")
SDL = Path(os.environ.get("LETSGO_SDL_JAVA", "/opt/data/cache/scratch/lg-build/deps/SDL3/android-project/app/src/main/java"))
EVIDENCE.mkdir(parents=True, exist_ok=True)

def run(name, args, expected=0):
    result = subprocess.run(list(map(str, args)), cwd=REPO, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (EVIDENCE / (name + ".log")).write_text("COMMAND: " + " ".join(map(str, args)) + "\n" + result.stdout + f"\nrc={result.returncode}\n")
    print(name + ":", result.stdout, "rc=" + str(result.returncode), flush=True)
    if result.returncode != expected:
        raise SystemExit(f"{name}: expected rc {expected}, got {result.returncode}")
    return result.stdout

assert (JDK / "javac").is_file(), "Set LETSGO_JDK"
assert Path(ANDROID).is_file(), "Set LETSGO_ANDROID_JAR"
assert (SDL / "org/libsdl/app/SDLActivity.java").is_file(), "Set LETSGO_SDL_JAVA"
sources = sorted((REPO / "apk/java").rglob("*.java")) + sorted(SDL.rglob("*.java"))
print("Evidence:", EVIDENCE, "Java sources:", len(sources))
run("javac", [JDK / "javac", "-cp", ANDROID, "-d", EVIDENCE / "java", *sources])
run("unit-compile", [JDK / "javac", "-cp", str(EVIDENCE / "java") + os.pathsep + ANDROID,
    "-d", EVIDENCE / "unit", *sorted((REPO / "apk/test").glob("*.java"))])
for test in ("SettingsJumpTest", "GameFolderTest", "GyroAimTest"):
    args = [JDK / "java", "-Djava.io.tmpdir=" + str(EVIDENCE), "-cp",
        os.pathsep.join([str(EVIDENCE / "unit"), str(EVIDENCE / "java"), ANDROID]), "com.letsgorecomp." + test]
    if test == "SettingsJumpTest":
        args.append(REPO / "apk/java/com/letsgorecomp/LetsGoLauncherActivity.java")
    run(test, args)

# Compile the *original* helper with the current test; failure must be an assertion,
# not a missing method/compiler error. Both extracted files stay outside the checkout.
red = EVIDENCE / "red"
red.mkdir(exist_ok=True)
for name in ("SettingsJump.java", "LetsGoLauncherActivity.java"):
    data = subprocess.check_output(["git", "show", "4494622:apk/java/com/letsgorecomp/" + name], cwd=REPO)
    (red / name).write_bytes(data)
run("red-compile", [JDK / "javac", "-d", red, red / "SettingsJump.java", REPO / "apk/test/SettingsJumpTest.java"])
output = run("red-original", [JDK / "java", "-cp", red, "com.letsgorecomp.SettingsJumpTest", red / "LetsGoLauncherActivity.java"], expected=1)
assert "AssertionError: visible titled section folder" in output
print("PASS: three host suites green, regression assertion red on original 4494622")
