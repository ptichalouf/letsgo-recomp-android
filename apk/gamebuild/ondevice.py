"""Private, restartable Android builder. Only user's extracted files are translated.
No shell, no network, no keys. All executables come from nativeLibraryDir.
"""
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

MODULES = ('main', 'sdk', 'subsdk0', 'subsdk1')
BASES = ('0x7000000000', '0x7004000000', '0x7006000000', '0x7008000000')


def digest(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for b in iter(lambda: f.read(1024 * 1024), b''):
            h.update(b)
    return h.hexdigest()


def checked_file(p):
    p = Path(p)
    if p.is_symlink() or not p.is_file():
        raise ValueError('missing file or symbolic link')
    return p


def atomic_json(p, value):
    p = Path(p)
    tmp = p.with_name(p.name + '.part')
    with open(tmp, 'w') as f:
        json.dump(value, f, sort_keys=True)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, p)


def read_json(p):
    try:
        return json.loads(Path(p).read_text())
    except (OSError, ValueError):
        return {}


def validate_main(p, allowed):
    with open(checked_file(p), 'rb') as f:
        h = f.read(0x100)
    if h[:4] != b'NSO0' or h[0x40:0x48].hex() not in allowed:
        raise ValueError('unsupported game version')


def unit_key(args, source, recipe):
    # PackageManager relocates nativeLibraryDir on every install. The immutable
    # recipe already hashes compiler bytes; relocation alone must not discard work.
    logical_args = [Path(args[0]).name] + args[1:]
    return hashlib.sha256(json.dumps([recipe, logical_args, digest(checked_file(source))]).encode()).hexdigest()


def object_valid(args, source, obj, recipe):
    meta = read_json(str(obj) + '.json')
    return (Path(obj).is_file() and meta.get('key') == unit_key(args, source, recipe)
            and meta.get('sha256') == digest(obj))


def execute(args, log):
    with open(log, 'wb') as f:
        rc = subprocess.call(args, stdout=f, stderr=f)
    if rc:
        raise RuntimeError('tool failed (exit %d), see private build log' % rc)


def compile_unit(args, source, obj, recipe):
    obj = Path(obj)
    if object_valid(args, source, obj, recipe):
        return True
    key = unit_key(args, source, recipe)
    tmp = str(obj) + '.part'
    execute(args + ['-o', tmp], str(obj) + '.log')
    if not Path(tmp).is_file() or not Path(tmp).stat().st_size:
        raise RuntimeError('compiler returned no object')
    os.replace(tmp, obj)
    atomic_json(str(obj) + '.json', {'key': key, 'sha256': digest(obj)})
    return False


def link_command(native, cc, pending, rsp):
    # Direct ld.lld does not add Clang's outlined-atomic runtime automatically.
    # Use the pinned Android archive after the objects; its CPU probe needs libc.
    return [str(native / 'libzig.so'), 'ld.lld', '-shared', '-z', 'max-page-size=16384',
            '-soname', 'liblasgame.so', '-o', str(pending), '@' + str(rsp),
            '-L' + str(native), '-l:liblasrecomp.so',
            str(cc / 'clang/lib/libclang_rt.builtins-aarch64-android.a'),
            '-L/system/lib64', '-l:libc.so']


def build(root, private, native):
    start = time.monotonic()
    root, private, native = Path(root), Path(private), Path(native)
    kit = private / 'kit'
    cfg = read_json(kit / 'config.json')
    exefs = root / 'extracted/exefs'
    validate_main(exefs / 'main', cfg['main_ids'])
    # Snapshot exefs before generation. A modified/removable input cannot mix builds.
    hashes = {m: digest(checked_file(exefs / m)) for m in MODULES}
    recipe = cfg['recipe']
    key = hashlib.sha256(json.dumps([str(root), recipe, hashes], sort_keys=True).encode()).hexdigest()
    work = private / key
    work.mkdir(mode=0o700, parents=True, exist_ok=True)
    def progress(phase, done, total, **extra):
        atomic_json(private / 'state.json', dict(state=phase, done=done, total=total,
                    elapsed_s=round(time.monotonic() - start, 3), root=str(root), **extra))
    progress('preparing', 0, 4)
    snap = work / 'exefs'; snap.mkdir(exist_ok=True)
    for m in MODULES:
        dest = snap / m
        if not dest.exists() or digest(dest) != hashes[m]:
            with open(exefs / m, 'rb') as src, open(str(dest) + '.part', 'wb') as dst:
                while b := src.read(1024 * 1024):
                    dst.write(b)
            if digest(str(dest) + '.part') != hashes[m]:
                raise RuntimeError('input changed during import; retry')
            os.replace(str(dest) + '.part', dest)
    os.environ.update(LASPY_HOME=str(kit / 'pyhome'),
        LASPY_PATH=str(kit / 'tools') + ':' + str(kit / 'pyhome/lib/python3.14/site-packages'),
        LIBUNICORN_PATH=str(native), LIBCAPSTONE_PATH=str(native), TMPDIR=str(work))
    generated = work / 'generated'; generated.mkdir(exist_ok=True)
    gen_manifest = work / 'generated.json'
    previous = read_json(gen_manifest)
    valid = previous.get('recipe') == recipe and previous.get('inputs') == hashes
    sources = [Path(p) for p in previous.get('files', {})]
    valid = valid and bool(sources) and all(p.is_file() and digest(p) == previous['files'][str(p)] for p in sources)
    if not valid:
        sources = []
        for i, m in enumerate(MODULES):
            out = generated / m
            # A prior interrupted generator may have left more chunks. Remove only
            # our own generated C; no recursive deletion of the user's folder.
            out.mkdir(exist_ok=True)
            for p in out.glob('*.c'):
                p.unlink()
            args = [str(native / 'liblaspython.so'), str(kit / 'tools/gen.py'), m,
                    str(snap / m), BASES[i], str(out), '--simd', 'asm']
            if m == 'sdk':
                args += ['--hle', str(kit / 'tools/hle_symbols.txt')]
            execute(args, work / ('gen-' + m + '.log'))
            found = sorted(out.glob('*.c'))
            if len(found) < 2 or not (out / (m + '_table.c')).is_file():
                raise RuntimeError('incomplete generated module')
            sources.extend(found)
            progress('generating', i + 1, 4)
        atomic_json(gen_manifest, {'recipe': recipe, 'inputs': hashes,
                    'files': {str(p): digest(p) for p in sources}})
    sources.append(kit / 'cckit/gamelib-src/gamelib_entry.c')
    objects = work / 'objects'; objects.mkdir(exist_ok=True)
    cc = kit / 'cckit'
    common = [str(native / 'libzig.so'), 'clang', '-target', 'aarch64-linux-android30',
              '-O1', '-fno-strict-aliasing', '-Wno-return-type', '-Wno-unused-label',
              '-fvisibility=hidden', '-nostdinc', '-isystem', str(cc / 'clang/include'),
              '-isystem', str(cc / 'sysroot/usr/include'), '-isystem',
              str(cc / 'sysroot/usr/include/aarch64-linux-android'),
              '-I' + str(cc / 'gamelib-src'), '-DGAMELIB_ID="' + cfg['gamelib_id'] + '"', '-c']
    def compile_one(source):
        obj = objects / (source.parent.name + '_' + source.stem + '.o')
        reused = compile_unit(common + [str(source)], source, obj, recipe)
        return obj, reused
    results, reused = [], 0
    progress('compiling', 0, len(sources))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(compile_one, p) for p in sources]
        for f in concurrent.futures.as_completed(futures):
            obj, reuse = f.result(); results.append(obj); reused += reuse
            progress('compiling', len(results), len(sources), reused=reused)
    if {m: digest(checked_file(exefs / m)) for m in MODULES} != hashes:
        raise RuntimeError('input changed during build; retry')
    progress('linking', len(results), len(sources), reused=reused)
    rsp = work / 'link.rsp'
    rsp.write_text(''.join('"' + str(p) + '"\n' for p in sorted(results)))
    pending = work / 'liblasgame.so.part'
    execute(link_command(native, cc, pending, rsp), work / 'link.log')
    os.chmod(pending, 0o400)
    lib = work / ('liblasgame-' + digest(pending) + '.so')
    os.replace(pending, lib)
    # ready.json is the commit point. Never overwrite an earlier ready library.
    atomic_json(private / 'ready.json', dict(root=str(root), lib=str(lib), sha256=digest(lib),
                gamelib_id=cfg['gamelib_id'], recipe=recipe, inputs=hashes))
    progress('complete', len(results), len(sources), reused=reused)


if __name__ == '__main__':
    try:
        build(*sys.argv[1:4])
    except Exception as e:
        atomic_json(Path(sys.argv[2]) / 'state.json', {'state': 'failed', 'error': str(e)})
        raise
