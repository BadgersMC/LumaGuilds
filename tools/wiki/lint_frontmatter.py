#!/usr/bin/env python3
"""Validate YAML front-matter on every wiki/docs/**/*.md page.

Required fields: title, audience, topic, summary, keywords, related, updated.
audience must be one of: player, admin, dev.
topic must be a lowercase-hyphenated slug matching the filename stem.
summary must be <=140 chars.

Exit code 1 on any failure; prints all failures before exiting.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML is required. Install with: pip install pyyaml", file=sys.stderr)
    sys.exit(2)

REPO_ROOT = Path(__file__).resolve().parents[2]
DOCS_ROOT = REPO_ROOT / "wiki" / "docs"

REQUIRED_FIELDS = {"title", "audience", "topic", "summary", "keywords", "related", "updated"}
VALID_AUDIENCES = {"player", "admin", "dev"}
SLUG_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
SUMMARY_MAX = 140

# Files whose `topic` slug intentionally does not match the filename stem.
SLUG_EXEMPT = {
    Path("index.md"),
    Path("developers/getting-started.md"),
    Path("developers/placeholders.md"),
}


def parse_frontmatter(text: str) -> dict | None:
    if not text.startswith("---\n"):
        return None
    end = text.find("\n---", 4)
    if end == -1:
        return None
    return yaml.safe_load(text[4:end])


def lint(path: Path) -> list[str]:
    rel = path.relative_to(DOCS_ROOT)
    errors: list[str] = []
    text = path.read_text(encoding="utf-8")
    fm = parse_frontmatter(text)
    if fm is None:
        return [f"{rel}: missing or malformed YAML front-matter"]

    missing = REQUIRED_FIELDS - fm.keys()
    if missing:
        errors.append(f"{rel}: missing required fields: {sorted(missing)}")

    if "audience" in fm and fm["audience"] not in VALID_AUDIENCES:
        errors.append(f"{rel}: audience={fm['audience']!r} not in {VALID_AUDIENCES}")

    if "topic" in fm:
        slug = str(fm["topic"])
        if not SLUG_RE.match(slug):
            errors.append(f"{rel}: topic={slug!r} not a lowercase-hyphenated slug")
        if rel not in SLUG_EXEMPT and path.stem != slug:
            errors.append(f"{rel}: topic={slug!r} does not match filename stem {path.stem!r}")

    if "summary" in fm and isinstance(fm["summary"], str) and len(fm["summary"]) > SUMMARY_MAX:
        errors.append(f"{rel}: summary is {len(fm['summary'])} chars (max {SUMMARY_MAX})")

    if "keywords" in fm and not isinstance(fm["keywords"], list):
        errors.append(f"{rel}: keywords must be a list")

    if "related" in fm and not isinstance(fm["related"], list):
        errors.append(f"{rel}: related must be a list")

    return errors


def main() -> int:
    failures: list[str] = []
    pages = sorted(DOCS_ROOT.rglob("*.md"))
    if not pages:
        print(f"No markdown pages found under {DOCS_ROOT}", file=sys.stderr)
        return 1
    for page in pages:
        failures.extend(lint(page))
    if failures:
        for line in failures:
            print(line, file=sys.stderr)
        print(f"\n{len(failures)} front-matter problem(s) across {len(pages)} pages.", file=sys.stderr)
        return 1
    print(f"OK -- {len(pages)} pages validated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
