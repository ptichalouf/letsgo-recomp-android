#!/usr/bin/env python3
"""Refuse CRLF line endings in text files that must stay LF.

A CRLF inside a bash script breaks it. The repository policy is LF everywhere except
Windows-native files (.bat, .ps1). This guard runs in CI and can be run locally:

    python3 tools/check_line_endings.py

Exit status 0 = clean, 1 = refused (with the offending paths listed).
"""
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SKIP_DIRS = {".git", "node_modules", "build", "deps", "__pycache__", ".gradle"}

# Files allowed to carry CRLF.
CRLF_ALLOWED_SUFFIXES = {".bat", ".ps1"}

# Files that are binary regardless of extension.
BINARY_SUFFIXES = {
    ".ttf", ".otf", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".icns",
    ".pdf", ".zip", ".so", ".a", ".o", ".lib", ".exe", ".dll", ".mp3", ".ogg",
    ".wav", ".mp4", ".bin", ".dat",
}

TEXT_SUFFIXES = {
    ".sh", ".bash", ".py", ".c", ".h", ".cpp", ".hpp", ".java", ".md", ".txt",
    ".yml", ".yaml", ".json", ".xml", ".gradle", ".cmake", ".mk", ".in", ".ps1",
    ".bat", ".gitattributes", ".gitignore",
}


def is_text(path: Path) -> bool:
    suffix = path.suffix.lower()
    if suffix in BINARY_SUFFIXES:
        return False
    if suffix in TEXT_SUFFIXES or suffix == "":
        return True
    return path.name in {"Makefile", "Dockerfile", "CODEOWNERS"}


def main() -> int:
    findings = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            path = Path(dirpath) / name
            if not is_text(path):
                continue
            if path.suffix.lower() in CRLF_ALLOWED_SUFFIXES:
                continue
            try:
                data = path.read_bytes()
            except OSError:
                continue
            if b"\r\n" in data:
                rel = path.relative_to(ROOT).as_posix()
                count = data.count(b"\r\n")
                findings.append((rel, count))

    if findings:
        print("REFUSED: CRLF line endings in files that must stay LF:", file=sys.stderr)
        for rel, count in sorted(findings):
            print(f"  {rel} — {count} CRLF line(s)", file=sys.stderr)
        return 1

    print("OK: LF line endings everywhere except .bat/.ps1.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
