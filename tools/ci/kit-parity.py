#!/usr/bin/env python3
"""Compare the kit built from source (build/kit) with the prebuilt kit it replaces - for review only.

  python3 -I tools/ci/kit-parity.py <reference.apk> [build/kit]

<reference.apk> = Nycolazs/LASRecomp-Android-Plus android-v1.2.0 (public, game-free). It is read here and nothing of it
is copied anywhere. Prints the files only one side has, and the files both have with different contents (expected:
the NDK version, a newer header set probed from this repository's own headers, binaries built by another machine).
Always exits 0: a difference is information for a reviewer, not a failure.
"""
import hashlib
import io
from pathlib import Path
import sys
import zipfile

PROGRAMS = ['libzig.so', 'liblaspython.so', 'libpython3.14.so', 'libunicorn.so', 'libcapstone.so']


def wanted(name):
    return name.startswith(('pyhome/', 'cckit/clang/include/', 'cckit/sysroot/usr/include/')) or \
        name == 'cckit/clang/lib/libclang_rt.builtins-aarch64-android.a'


def main():
    ref, kit = Path(sys.argv[1]), Path(sys.argv[2] if len(sys.argv) > 2 else 'build/kit')
    theirs, ours = {}, {}
    with zipfile.ZipFile(ref) as z:
        for n in PROGRAMS:
            theirs['jniLibs/arm64-v8a/' + n] = hashlib.sha256(z.read('lib/arm64-v8a/' + n)).hexdigest()
        with zipfile.ZipFile(io.BytesIO(z.read('assets/ondevice.zip'))) as k:
            for n in k.namelist():
                if not n.endswith('/') and wanted(n) and '/__pycache__/' not in n:
                    theirs[n] = hashlib.sha256(k.read(n)).hexdigest()
    for p in sorted(kit.rglob('*')):
        rel = p.relative_to(kit).as_posix()
        if p.is_file() and (wanted(rel) or rel.startswith('jniLibs/')) and '/__pycache__/' not in rel:
            ours[rel] = hashlib.sha256(p.read_bytes()).hexdigest()
    only_ref = sorted(set(theirs) - set(ours))
    only_src = sorted(set(ours) - set(theirs))
    differ = sorted(n for n in set(theirs) & set(ours) if theirs[n] != ours[n])
    same = len(set(theirs) & set(ours)) - len(differ)
    print(f'KIT_PARITY reference={len(theirs)} source={len(ours)} identical={same} differ={len(differ)} '
          f'only_reference={len(only_ref)} only_source={len(only_src)}')
    for title, names in (('only in the reference kit', only_ref), ('only in the source-built kit', only_src),
                         ('in both, different contents', differ)):
        print(f'== {title}: {len(names)}')
        for n in names[:200]:
            print('  ' + n)


if __name__ == '__main__':
    main()
