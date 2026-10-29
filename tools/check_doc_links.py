#!/usr/bin/env python3
"""Check that relative links in the Markdown documents resolve.

A broken link in a contribution guide is a broken first contact for a newcomer or an
agent. This guard runs in CI and can be run locally:

    python3 tools/check_doc_links.py

Exit status 0 = clean, 1 = broken links found.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Documents scanned. Kept explicit: a scan of every file would follow vendored docs.
DOCS = [
    "README.md", "AGENTS.md", "CONTRIBUTING.md", "ISSUES.md", "BUILD.md",
    "docs/ROADMAP.md", "docs/INDEX-FOR-LLMS.md", "docs/MODS.md",
    "docs/HOWTO-BUILD.md", "docs/CIRCUIT-FUSION.md", "docs/PERF_AUDIT.md",
    "docs/RESOLUTION_SCALING.md", "docs/FORMATS_IMAGE.md",
    "docs/TEXT_RENDER_AUDIT.md", "docs/TEXT_RENDER_DEEP_REVIEW.md",
    "docs/AUDITORIA-GRAFICA-COMPLETA.md",
    ".github/AI_CONTRIBUTING.md", ".github/PULL_REQUEST_TEMPLATE.md",
    ".github/ISSUE_TEMPLATE/bug_report.md",
    ".github/ISSUE_TEMPLATE/feature_request.md",
    ".github/ISSUE_TEMPLATE/question.md",
]

# A markdown link: [text](target) — the target may carry a #anchor.
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")

# Links that are not local files and are not checked.
EXTERNAL = re.compile(r"^(https?:|mailto:|ftp:|#)")


def main() -> int:
    broken = []
    checked = 0
    for rel in DOCS:
        doc = ROOT / rel
        if not doc.is_file():
            continue
        text = doc.read_text(encoding="utf-8", errors="replace")
        for target in LINK.findall(text):
            if EXTERNAL.match(target):
                continue
            path_part = target.split("#", 1)[0]
            if not path_part:
                continue
            checked += 1
            resolved = (doc.parent / path_part).resolve()
            if not resolved.exists():
                broken.append((rel, target))

    if broken:
        print("REFUSED: broken relative links:", file=sys.stderr)
        for rel, target in sorted(broken):
            print(f"  {rel} -> {target}", file=sys.stderr)
        return 1

    print(f"OK: {checked} relative links checked, all resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
