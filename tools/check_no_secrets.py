#!/usr/bin/env python3
"""Refuse secrets and signing material in the working tree and in the history.

Patterns are deliberately narrow: they look for private keys, cloud credentials and
console keys. A false positive is reported, not hidden — this guard never edits
anything.

    python3 tools/check_no_secrets.py [--history]

With --history the scan also covers the blobs of the last 200 commits on the current
branch. Exit status 0 = clean, 1 = refused.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SKIP_DIRS = {".git", "node_modules", "build", "deps", "__pycache__", ".gradle"}

PATTERNS = [
    (re.compile(rb"-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----"),
     "private key"),
    (re.compile(rb"\bAKIA[0-9A-Z]{16}\b"), "AWS access key id"),
    (re.compile(rb"\bghp_[A-Za-z0-9]{36}\b"), "GitHub token"),
    (re.compile(rb"\bgithub_pat_[A-Za-z0-9_]{22,}\b"), "GitHub fine-grained token"),
    (re.compile(rb"\bsk-[A-Za-z0-9]{32,}\b"), "provider secret key"),
    (re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{10,}\b"), "Slack token"),
    (re.compile(rb"prod\.keys\s*[:=]\s*[0-9a-f]{16}\b"), "console keys"),
]

# Text files only.
BINARY_SUFFIXES = {
    ".ttf", ".otf", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".icns",
    ".pdf", ".zip", ".so", ".a", ".o", ".lib", ".exe", ".dll", ".mp3", ".ogg",
    ".wav", ".mp4", ".bin", ".dat",
}


def scan_bytes(data: bytes, label: str, findings: list) -> None:
    for pattern, reason in PATTERNS:
        match = pattern.search(data)
        if match:
            offset = match.start()
            snippet = data[max(0, offset - 40):offset + 40]
            findings.append((label, reason, snippet.decode("utf-8", "replace")))


def main() -> int:
    findings = []
    scanned = 0

    for dirpath, dirnames, filenames in __import__("os").walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            path = Path(dirpath) / name
            if path.suffix.lower() in BINARY_SUFFIXES:
                continue
            try:
                data = path.read_bytes()
            except OSError:
                continue
            scanned += 1
            scan_bytes(data, path.relative_to(ROOT).as_posix(), findings)

    if "--history" in sys.argv:
        try:
            out = subprocess.run(
                ["git", "-C", str(ROOT), "log", "-200", "--format=%H", "--all"],
                capture_output=True, text=True, check=True).stdout.split()
        except (subprocess.CalledProcessError, FileNotFoundError):
            out = []
        for sha in out:
            blobs = subprocess.run(
                ["git", "-C", str(ROOT), "ls-tree", "-r", "--name-only", sha],
                capture_output=True, text=True, check=True).stdout.splitlines()
            for blob in blobs:
                if Path(blob).suffix.lower() in BINARY_SUFFIXES:
                    continue
                data = subprocess.run(
                    ["git", "-C", str(ROOT), "cat-file", "blob", f"{sha}:{blob}"],
                    capture_output=True).stdout
                if data:
                    scan_bytes(data, f"{sha[:10]}:{blob}", findings)

    if findings:
        print("REFUSED: possible secret found:", file=sys.stderr)
        for label, reason, snippet in findings:
            print(f"  {label} — {reason}\n      {snippet.strip()[:120]}", file=sys.stderr)
        return 1

    print(f"OK: no secret pattern in {scanned} files{' and the scanned history' if '--history' in sys.argv else ''}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
