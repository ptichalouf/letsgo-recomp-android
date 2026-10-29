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
    ".key", ".keys", ".prod.keys", ".title.keys",
    ".bfttf", ".bfs",  # Switch system fonts
    ".jks", ".keystore", ".p12", ".pfx",
    ".pem", ".der",
}

# Directory names that hold game data when present.
FORBIDDEN_DIRS = {
    "extracted", "generated", "generated-aarch64", "romfs", "exefs",
    "prod.keys", "title.keys", "save",
}

# Text files that legitimately use these words in their content.
NAME_ALLOWLIST = {
    "src/tools/gen.py", "src/tools/lifter.py",
}


def is_forbidden_path(path: Path) -> str:
    """Return a reason if the path is forbidden, else an empty string."""
    rel = path.relative_to(ROOT).as_posix()
    name = path.name.lower()

    if rel in NAME_ALLOWLIST:
        return ""

    if name in FORBIDDEN_DIRS:
        return "directory holding game data"

    suffix = path.suffix.lower()
    if suffix in FORBIDDEN_SUFFIXES:
        return f"game/key/signing material ({suffix})"

    if name in {"prod.keys", "title.keys"}:
        return "console keys"

    return ""


def main() -> int:
    findings = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in list(dirnames):
            reason = is_forbidden_path(Path(dirpath) / name / "x")
            if reason:
                rel = (Path(dirpath) / name).relative_to(ROOT).as_posix()
                findings.append((rel, reason))
                dirnames.remove(name)
        for name in filenames:
            reason = is_forbidden_path(Path(dirpath) / name)
            if reason:
                rel = (Path(dirpath) / name).relative_to(ROOT).as_posix()
                findings.append((rel, reason))

    if findings:
        print("REFUSED: game content, keys or signing material found:", file=sys.stderr)
        for rel, reason in sorted(findings):
            print(f"  {rel} — {reason}", file=sys.stderr)
        return 1

    print("OK: no game content, key or signing material in the working tree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
