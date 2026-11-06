#!/usr/bin/env python3
"""deps.lock reader: fetch and verify pinned third-party inputs, check license texts, make the source tarball.

  deps.py list                              name, version, license of every entry
  deps.py fetch [--dir D] [--offline] [NAME...]
                                            download (or, --offline, only check) each `dep` into D (default
                                            $LETSGO_DEPS_DL or deps/dl) and check its sha256; exit 1 on any mismatch
  deps.py path NAME [--dir D]               the local file of one `dep` (after fetch)
  deps.py licenses [--dir D]                every `lic` text in LICENSES/ must equal the member of its pinned archive
  deps.py source-tarball OUT VERSION [--dir D]
                                            OUT = letsgo-<VERSION>-source.tar.gz: `git archive HEAD` + every
                                            "in source tarball = yes" archive under deps/dl/ + SOURCE-README.md

Run with `python3 -I`. Standard library only. Downloaded archives are treated as data: read with tarfile, never
executed or extracted here.
"""
import argparse
import hashlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LOCK = ROOT / 'deps.lock'


def parse(lock=LOCK):
    deps, tools, lics = {}, {}, []
    for n, line in enumerate(Path(lock).read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        f = [x.strip() for x in line.split('|')]
        kind = f[0]
        if kind in ('dep', 'tool'):
            if len(f) != 7:
                sys.exit(f'deps.lock:{n}: {kind} needs 7 fields, has {len(f)}')
            e = dict(kind=kind, name=f[1], version=f[2], license=f[3], source=f[4], sha256=f[5], url=f[6], line=n)
            (deps if kind == 'dep' else tools)[f[1]] = e
        elif kind == 'lic':
            if len(f) not in (4, 5):
                sys.exit(f'deps.lock:{n}: lic needs 4 or 5 fields')
            lics.append(dict(file=f[1], dep=f[2], member=f[3], lines=f[4] if len(f) == 5 else '', line=n))
        else:
            sys.exit(f'deps.lock:{n}: unknown kind {kind!r}')
    return deps, tools, lics


def filename(e):
    """Local file name: the URL's last path element, or <name>-<version>.txt for plain files (license texts)."""
    base = e['url'].rstrip('/').rsplit('/', 1)[-1]
    if not base.endswith(('.tar.gz', '.tar.xz')):
        return e['name'] + '.txt'
    if e['url'].startswith('https://github.com/') and '/archive/' in e['url']:
        # GitHub archives are named <ref>.tar.gz: prefix the project for readable names in the source tarball
        base = e['name'] + '-' + base.replace('refs/tags/', '')
    return base


def sha256(p):
    h = hashlib.sha256()
    with open(p, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def dl_dir(args):
    return Path(args.dir or os.environ.get('LETSGO_DEPS_DL') or ROOT / 'deps/dl')


def fetch(args):
    deps, _, _ = parse()
    d = dl_dir(args); d.mkdir(parents=True, exist_ok=True)
    names = args.names or list(deps)
    bad = 0
    for name in names:
        if name not in deps:
            sys.exit(f'unknown dep {name}')
        e = deps[name]; p = d / filename(e)
        if not p.is_file():
            if args.offline:
                print(f'MISSING {name}: {p}'); bad += 1; continue
            if not e['url'].startswith('https://'):
                sys.exit(f'{name}: not an https URL')
            tmp = p.with_suffix(p.suffix + '.part')
            for attempt in range(4):
                try:
                    req = urllib.request.Request(e['url'], headers={'User-Agent': 'letsgo-deps/1'})
                    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, 'wb') as out:
                        while b := r.read(1 << 20):
                            out.write(b)
                    break
                except OSError as err:
                    if attempt == 3:
                        sys.exit(f'{name}: download failed: {err}')
                    time.sleep(5 * (attempt + 1))
            os.replace(tmp, p)
        got = sha256(p)
        if got != e['sha256']:
            print(f'SHA256_MISMATCH {name}: {p} is {got}, deps.lock says {e["sha256"]}'); bad += 1
        else:
            print(f'OK {name} {e["version"]} {p.name}')
    if bad:
        sys.exit(f'{bad} dependency check(s) failed')


def member_bytes(archive, member):
    if member == '.':
        return Path(archive).read_bytes()
    with tarfile.open(archive) as t:
        f = t.extractfile(member)
        if f is None:
            raise KeyError(member)
        return f.read()


def licenses(args):
    deps, _, lics = parse()
    d = dl_dir(args); bad = 0
    for l in lics:
        e = deps[l['dep']]; p = d / filename(e)
        if not p.is_file() or sha256(p) != e['sha256']:
            print(f'UNVERIFIED {l["file"]}: fetch {l["dep"]} first'); bad += 1; continue
        want = member_bytes(p, l['member'])
        if l['lines']:
            a, b = (int(x) for x in l['lines'].split('-'))
            want = b''.join(want.splitlines(keepends=True)[a - 1:b])
        have = (ROOT / 'LICENSES' / l['file']).read_bytes() if (ROOT / 'LICENSES' / l['file']).is_file() else None
        if have != want:
            print(f'LICENSE_MISMATCH LICENSES/{l["file"]} != {l["dep"]}:{l["member"]}'); bad += 1
        else:
            print(f'OK LICENSES/{l["file"]} = {l["dep"]} {e["version"]}:{l["member"]}')
    if bad:
        sys.exit(f'{bad} license text(s) not verified')


def source_tarball(args):
    deps, tools, _ = parse()
    d = dl_dir(args)
    out = Path(args.out).resolve()
    top = f'letsgo-{args.version}'
    shipped = [e for e in deps.values() if e['source'] == 'yes']
    for e in shipped:
        p = d / filename(e)
        if not p.is_file() or sha256(p) != e['sha256']:
            sys.exit(f'{e["name"]}: {p} missing or not the pinned file (run fetch-deps.sh)')
    readme = [f'# {top} - corresponding source', '',
              'This archive is the complete source of the APK published with it:', '',
              f'- `{top}/` is this repository at the released commit (`git archive`), with the build scripts',
              '  (`tools/ci/fetch-deps.sh`, `tools/ci/build-deps.sh`, `tools/ci/build-buildkit.sh`, `build-*.sh`,',
              '  `link-runtime.sh`, `.github/workflows/apk.yml`) that produced it;',
              f'- `{top}/deps/dl/` holds the unmodified upstream source archives of the third-party parts that are',
              '  built from source (sha256 pinned in `deps.lock`):', '']
    for e in shipped:
        readme.append(f'  - `{filename(e)}`: {e["name"]} {e["version"]} ({e["license"]}), from {e["url"]}')
    readme += ['', 'Not included (binary releases, permissive licenses, no source obligation; fetched by',
               '`tools/ci/fetch-deps.sh` from the URLs in `deps.lock`):', '']
    for e in deps.values():
        if e['source'] != 'yes':
            readme.append(f'- {e["name"]} {e["version"]} ({e["license"]}): {e["url"]}')
    for e in tools.values():
        readme.append(f'- {e["name"]} {e["version"]} ({e["license"]}): {e["url"]}')
    readme += ['', 'Rebuild (Linux x86_64, JDK 17, Android SDK + NDK 28.2.13676358, see BUILD.md):', '',
               '```sh', 'LETSGO_DEPS_DL=$PWD/deps/dl tools/ci/fetch-deps.sh --offline',
               'tools/ci/build-deps.sh && tools/ci/build-buildkit.sh',
               'LETSGO_KIT_DIR=$PWD/build/kit python3 tools/stage-buildkit.py',
               'bash build-runtime.sh && bash link-runtime.sh && bash build-modunpack.sh',
               'GAME=none LETSGO_BUILD_ASSETS=$PWD/build/build-assets bash build-apk.sh', '```', '']
    git = subprocess.run(['git', '-C', str(ROOT), 'archive', '--format=tar', f'--prefix={top}/', 'HEAD'],
                         check=True, capture_output=True).stdout
    mtime = int(subprocess.run(['git', '-C', str(ROOT), 'log', '-1', '--format=%ct'], check=True,
                               capture_output=True, text=True).stdout.strip())
    def info(name, size, mode=0o644):
        ti = tarfile.TarInfo(name); ti.size = size; ti.mtime = mtime; ti.mode = mode
        ti.uid = ti.gid = 0; ti.uname = ti.gname = ''
        return ti
    with tempfile.NamedTemporaryFile(dir=out.parent, delete=False) as tmp:
        with tarfile.open(fileobj=tmp, mode='w:gz', format=tarfile.PAX_FORMAT) as t:
            with tarfile.open(fileobj=io.BytesIO(git)) as g:
                for m in g.getmembers():
                    t.addfile(m, g.extractfile(m) if m.isfile() else None)
            body = '\n'.join(readme).encode()
            t.addfile(info(f'{top}/SOURCE-README.md', len(body)), io.BytesIO(body))
            for e in shipped:
                p = d / filename(e)
                with open(p, 'rb') as f:
                    t.addfile(info(f'{top}/deps/dl/{p.name}', p.stat().st_size), f)
    os.replace(tmp.name, out)
    print(f'SOURCE_TARBALL_OK {out} {sha256(out)}')


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    sub.add_parser('list')
    f = sub.add_parser('fetch'); f.add_argument('names', nargs='*'); f.add_argument('--dir')
    f.add_argument('--offline', action='store_true')
    p = sub.add_parser('path'); p.add_argument('name'); p.add_argument('--dir')
    l = sub.add_parser('licenses'); l.add_argument('--dir')
    s = sub.add_parser('source-tarball'); s.add_argument('out'); s.add_argument('version'); s.add_argument('--dir')
    args = ap.parse_args()
    if args.cmd == 'list':
        deps, tools, _ = parse()
        for e in list(deps.values()) + list(tools.values()):
            print(f'{e["name"]}\t{e["version"]}\t{e["license"]}')
    elif args.cmd == 'fetch':
        fetch(args)
    elif args.cmd == 'path':
        deps, _, _ = parse()
        print(dl_dir(args) / filename(deps[args.name]))
    elif args.cmd == 'licenses':
        licenses(args)
    else:
        source_tarball(args)


if __name__ == '__main__':
    main()
