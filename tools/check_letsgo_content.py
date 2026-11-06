#!/usr/bin/env python3
"""Refuse game content, keys, saves and signing material in the working tree.

The repository must never contain the game, in any form. This guard runs in CI and
can be run locally:

    python3 tools/check_no_game_content.py

Exit status 0 = clean, 1 = refused (with the offending paths listed).
"""
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Directories that are build outputs or vendored code, never scanned.
SKIP_DIRS = {".git", "node_modules", "build", "deps", "__pycache__", ".gradle"}

# Extensions that only ever hold game data, keys or signing material.
FORBIDDEN_SUFFIXES = {
    ".nsp", ".xci", ".nca", ".ncz", ".nsz", ".xcz",
    ".key", ".keys",
    ".bfttf", ".bfs",  # Switch system fonts
    ".jks", ".keystore", ".p12", ".pfx",
    ".pem", ".der",
}

# Names that hold game data whatever their extension.
FORBIDDEN_NAMES = {"prod.keys", "title.keys"}

# Directory names that hold game data when present.
FORBIDDEN_DIRS = {
    "extracted", "generated", "generated-aarch64", "romfs", "exefs",
    "save", "mods",
}

# Files that legitimately mention these words in their content.
NAME_ALLOWLIST = {
    "src/tools/gen.py", "src/tools/lifter.py",
}


def reason_for(path: Path) -> str:
    """Return why the path is forbidden, or an empty string if it is fine."""
    rel = path.relative_to(ROOT).as_posix()
    if rel in NAME_ALLOWLIST:
        return ""

    name = path.name.lower()
    if name in FORBIDDEN_NAMES:
        return "console keys"
    if name in FORBIDDEN_DIRS:
        return "directory holding game data"

    suffix = path.suffix.lower()
    if suffix in FORBIDDEN_SUFFIXES:
        return f"game/key/signing material ({suffix})"
    return ""


def main() -> int:
    findings = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]

        for name in list(dirnames):
            path = Path(dirpath) / name
            reason = reason_for(path)
            if reason:
                findings.append((path.relative_to(ROOT).as_posix(), reason))
                dirnames.remove(name)  # do not descend into it

        for name in filenames:
            path = Path(dirpath) / name
            reason = reason_for(path)
            if reason:
                findings.append((path.relative_to(ROOT).as_posix(), reason))

    if findings:
        print("REFUSED: game content, keys or signing material found:", file=sys.stderr)
        for rel, reason in sorted(findings):
            print(f"  {rel} — {reason}", file=sys.stderr)
        return 1

    print("OK: no game content, key or signing material in the working tree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
