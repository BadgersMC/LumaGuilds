#!/usr/bin/env python3
"""Verify every HelpTopics.kt slug has a matching wiki/docs/players/<slug>.md
(and every wiki/docs/players/<slug>.md other than `how-do-i.md` has a topic
entry in HelpTopics.kt).

Exit code 1 on mismatch; prints all mismatches before exiting.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
HELP_TOPICS_KT = REPO_ROOT / "src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopics.kt"
PLAYERS_DIR = REPO_ROOT / "wiki/docs/players"

# Pages that exist in wiki/docs/players/ but intentionally have no HelpTopics entry:
WIKI_ONLY = {"how-do-i", "shop"}

SLUG_RE = re.compile(r'slug\s*=\s*"([a-z0-9][a-z0-9-]*)"')


def kotlin_slugs() -> set[str]:
    src = HELP_TOPICS_KT.read_text(encoding="utf-8")
    return set(SLUG_RE.findall(src))


def wiki_slugs() -> set[str]:
    return {p.stem for p in PLAYERS_DIR.glob("*.md")} - WIKI_ONLY


def main() -> int:
    kt = kotlin_slugs()
    wiki = wiki_slugs()

    missing_wiki = sorted(kt - wiki)
    missing_kt = sorted(wiki - kt)

    if missing_wiki:
        print("Slugs in HelpTopics.kt missing a wiki page in wiki/docs/players/:", file=sys.stderr)
        for s in missing_wiki:
            print(f"  - {s} (expected wiki/docs/players/{s}.md)", file=sys.stderr)

    if missing_kt:
        print("Wiki pages in wiki/docs/players/ missing a HelpTopics entry:", file=sys.stderr)
        for s in missing_kt:
            print(f"  - {s} (add to HelpTopics.all or to WIKI_ONLY in this script)", file=sys.stderr)

    if missing_wiki or missing_kt:
        print(
            "\nFix: add the missing page OR add the missing HelpTopics entry. "
            "See CONTRIBUTING.md -> 'Keeping /g help and the wiki in sync'.",
            file=sys.stderr,
        )
        return 1

    print(f"OK -- {len(kt)} topics in parity with wiki.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
