#!/usr/bin/env python3
"""Stage a provenance-pinned, game-free kit; no downloads, no generated game C.
KIT_SOURCE is a private, audited local kit directory. Never publish its APK.
"""
from pathlib import Path
import hashlib
import io
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'src'
source = Path(os.environ['LETSGO_KIT_SOURCE'])
EXPECTED = '2dc418400274994108f6ffdc8fe8a2c015c27cade2c0d17722b61dedb66a8b2c'

def sha(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for b in iter(lambda: f.read(1048576), b''): h.update(b)
    return h.hexdigest()

assert sha(source / 'upstream.apk') == EXPECTED, 'unverified upstream kit'
out = ROOT / 'build/build-assets/buildkit'; out.mkdir(parents=True, exist_ok=True)
lib = ROOT / 'build/apk-lib'; lib.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source / 'upstream.apk') as z:
    for n in ['liblaspython.so', 'libpython3.14.so', 'libunicorn.so', 'libcapstone.so']:
        (lib / n).write_bytes(z.read('lib/arm64-v8a/' + n))
    for n in z.namelist():
        if n.startswith('assets/licenses/') and not n.endswith('/'):
            p = out / n.removeprefix('assets/'); p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(z.read(n))
    # Extract from the authenticated archive, not a mutable unpacked side copy.
    (lib / 'libzig.so').write_bytes(z.read('lib/arm64-v8a/libzig.so'))
    with zipfile.ZipFile(io.BytesIO(z.read('assets/ondevice.zip'))) as kit:
        for name in kit.namelist():
            if not name.endswith('/') and (name.startswith(('pyhome/', 'cckit/clang/include/',
                    'cckit/sysroot/usr/include/')) or name == 'cckit/clang/lib/libclang_rt.builtins-aarch64-android.a'):
                dest = out / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(kit.read(name))
cc = out / 'cckit'
inc = cc / 'gamelib-src'; inc.mkdir(parents=True, exist_ok=True)
for n in ['cpu.h', 'simd.h', 'simd_fp.h', 'gamelib.h']:
    shutil.copy2(SRC / 'runtime' / n, inc / n)
shutil.copy2(SRC / 'tools/gamelib_entry.c', inc / 'gamelib_entry.c')
tools = out / 'tools'; tools.mkdir(exist_ok=True)
for n in ['gen.py','analyze.py','nso.py','lifter.py','simd_c.py','passes.py','patches.py']:
    shutil.copy2(SRC / 'tools' / n, tools / n)
shutil.copy2(SRC / 'runtime/hle/hle_symbols.txt', tools / 'hle_symbols.txt')
shutil.copy2(ROOT / 'apk/gamebuild/ondevice.py', out / 'ondevice.py')
files = ['tools/gen.py','tools/lifter.py','tools/analyze.py','tools/nso.py','tools/passes.py','tools/patches.py',
         'tools/simd_c.py','tools/gamelib_entry.c','runtime/hle/hle_symbols.txt','runtime/cpu.h',
         'runtime/simd.h','runtime/simd_fp.h','runtime/gamelib.h']
flags = '-O1 -fno-strict-aliasing -Wno-return-type -Wno-unused-label -fvisibility=hidden -Iruntime'
h = hashlib.sha256()
for n in files: h.update((SRC / n).read_bytes())
h.update((flags + '\n').encode()); gid = h.hexdigest()[:16]
# Everything used to generate/compile (not just mtime) invalidates cache after an APK update.
manifest = {str(p.relative_to(out)): sha(p) for p in sorted(out.rglob('*')) if p.is_file() and p.name != 'config.json'}
manifest.update({n: sha(lib / n) for n in ['libzig.so','liblaspython.so','libpython3.14.so','libunicorn.so','libcapstone.so']})
recipe = hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()
(out / 'config.json').write_text(json.dumps({'gamelib_id': gid, 'recipe': recipe,
    'main_ids': ['0a3f0d21e67dad80'], 'upstream_apk_sha256': EXPECTED, 'manifest': manifest}, sort_keys=True))
(ROOT / 'GAMELIB_ID.txt').write_text(gid + '\n')
ndk = Path(os.environ['ANDROID_NDK_HOME']) / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
subprocess.run([str(ndk / 'aarch64-linux-android30-clang'), '-shared', '-fPIC', '-O1', '-Wall', '-Wextra',
    str(ROOT / 'apk/gamebuild/jni.c'), '-o', str(lib / 'libgamebuilder.so'), '-Wl,-z,max-page-size=16384'], check=True)
print('BUILD_KIT_OK', 'GAMELIB_ID=' + gid, 'recipe=' + recipe)
