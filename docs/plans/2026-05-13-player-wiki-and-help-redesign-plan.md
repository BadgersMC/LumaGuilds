# Player Wiki + `/g help` Redesign — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Phase 1 of the wiki redesign — MkDocs scaffold on GitHub Pages, Getting Started + all Players section content, and a fully redesigned topic-based clickable `/g help`, with a topic-slug source of truth enforced by CI.

**Architecture:** MkDocs Material site lives under `wiki/` at the repo root, built and deployed to GitHub Pages by Actions. In-game `/g help` is refactored to render from a single `HelpTopics` Kotlin data structure shared with the wiki via stable topic slugs; CI fails if a slug doesn't have a matching wiki page. Every wiki page carries YAML front-matter (`audience`, `topic`, `summary`, `keywords`, `related`) so Hermes Agent can chunk and filter pages reliably. `llms.txt` and `llms-full.txt` are generated at build time.

**Tech Stack:** MkDocs + Material theme, mkdocs-llmstxt, mkdocs-awesome-pages-plugin, mkdocs-redirects. Kotlin 2.0, JUnit Jupiter 5, MockK, MockBukkit for Kotlin tests. Adventure API for clickable/hover chat components. Python 3.11 in CI for the parity/front-matter scripts. GitHub Actions for build + deploy.

**Design source:** [`docs/plans/2026-05-13-player-wiki-and-help-redesign-design.md`](2026-05-13-player-wiki-and-help-redesign-design.md)

**Out of scope (deferred to Phase 2/3):** Admins section content, Developers section migration of `docs/`, fold-in of loose top-level `.md` files.

---

## File Structure

**New files:**

- `mkdocs.yml` — site config at repo root
- `wiki/requirements.txt` — Python deps for MkDocs build
- `wiki/docs/index.md` — landing page
- `wiki/docs/.pages` — root nav ordering
- `wiki/docs/getting-started/` — Welcome, walkthrough (7 pages), FAQ
- `wiki/docs/players/` — How-do-I index + 13 feature pages
- `wiki/docs/admins/` — stubbed pages (front-matter only)
- `wiki/docs/developers/` — stubbed pages (front-matter only)
- `wiki/docs/assets/` — screenshot/image root
- `.github/workflows/wiki-deploy.yml` — GitHub Pages deploy on push to main
- `.github/workflows/wiki-checks.yml` — front-matter lint + topic parity on every PR
- `tools/wiki/lint_frontmatter.py` — front-matter schema validation
- `tools/wiki/check_topic_parity.py` — HelpTopics.kt ↔ wiki page slug parity
- `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopic.kt` — data class
- `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpCommandEntry.kt` — data class
- `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopics.kt` — registry
- `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRenderer.kt` — Adventure component builder
- `src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsTest.kt`
- `src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRendererTest.kt`

**Modified files:**

- `src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt` — `onHelp` refactored to delegate to `HelpTopicsRenderer`
- `CONTRIBUTING.md` — adds wiki/help sync rule
- `.gitignore` — exclude MkDocs build output `site/`
- `README.md` — minor link to wiki (deferred until site is live)

---

## Conventions Used Throughout the Plan

**Wiki page template** (every wiki page Phase 1 creates uses this exact shape):

```markdown
---
title: <Display Title>
audience: <player|admin|dev>
topic: <stable-slug>
summary: <one-sentence summary, ≤140 chars>
keywords: [<comma>, <separated>, <list>]
related: [<sibling-topic-slugs>]
updated: 2026-05-13
---

# <Display Title>

<One-sentence summary — same prose as `summary:` above.>

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g foo` | `lumaguilds.guild.foo` | One-line. |

## How it works

<Narrative paragraph(s). The canonical explanation.>

## <Common task 1>

<Step-by-step.>

## <Common task 2>

<Step-by-step.>

## Gotchas

- Bullet.
- Bullet.

## Related

- [Sibling page](../<other-topic>.md)
```

**Topic slug rule:** lowercase, hyphenated, matches `HelpTopics.kt` key exactly. Used as the file name (`<slug>.md`), the URL segment, and the `topic:` field. Stable forever — renaming requires a `mkdocs-redirects` entry.

**Commit cadence:** every task ends with a commit. Squash on merge is fine, but per-task commits keep PR review legible.

---

## Task 1: Add MkDocs scaffold (`mkdocs.yml`, `requirements.txt`, landing page)

**Files:**

- Create: `mkdocs.yml`
- Create: `wiki/requirements.txt`
- Create: `wiki/docs/index.md`
- Create: `wiki/docs/.pages`
- Modify: `.gitignore` (add `site/`)

- [ ] **Step 1: Create `wiki/requirements.txt`**

```text
mkdocs==1.6.1
mkdocs-material==9.5.49
mkdocs-awesome-pages-plugin==2.9.3
mkdocs-redirects==1.2.2
mkdocs-llmstxt==0.2.0
```

- [ ] **Step 2: Create `mkdocs.yml`**

```yaml
site_name: LumaGuilds
site_url: https://badgersmc.github.io/LumaGuilds/
site_description: Player wiki and admin/developer reference for the LumaGuilds plugin.
repo_url: https://github.com/BadgersMC/LumaGuilds
repo_name: BadgersMC/LumaGuilds
edit_uri: edit/main/wiki/docs/

docs_dir: wiki/docs

theme:
  name: material
  features:
    - navigation.tabs
    - navigation.tabs.sticky
    - navigation.sections
    - navigation.top
    - navigation.tracking
    - search.suggest
    - search.highlight
    - content.code.copy
    - content.action.edit
  palette:
    - media: "(prefers-color-scheme: light)"
      scheme: default
      primary: indigo
      accent: indigo
      toggle:
        icon: material/brightness-7
        name: Switch to dark mode
    - media: "(prefers-color-scheme: dark)"
      scheme: slate
      primary: indigo
      accent: indigo
      toggle:
        icon: material/brightness-4
        name: Switch to light mode

plugins:
  - search
  - awesome-pages
  - redirects:
      redirect_maps: {}
  - llmstxt:
      full_output: llms-full.txt
      sections:
        Getting Started:
          - getting-started/**/*.md
        Players:
          - players/**/*.md
        Admins:
          - admins/**/*.md
        Developers:
          - developers/**/*.md

markdown_extensions:
  - admonition
  - attr_list
  - md_in_html
  - tables
  - pymdownx.details
  - pymdownx.superfences
  - pymdownx.tabbed:
      alternate_style: true
  - toc:
      permalink: true

strict: true
```

- [ ] **Step 3: Create `wiki/docs/index.md`**

```markdown
---
title: LumaGuilds
audience: player
topic: home
summary: Player wiki and admin/developer reference for the LumaGuilds plugin on EnthusiaSMP.
keywords: [home, overview]
related: []
updated: 2026-05-13
---

# LumaGuilds

LumaGuilds is the guild plugin powering [EnthusiaSMP](https://enthusiasmp.com). Pick where you want to go:

- **[Getting Started](getting-started/welcome.md)** — New to the server? Start here.
- **[Players](players/how-do-i.md)** — How everything works, organized by feature.
- **[Admins](admins/installation.md)** — Install, configure, and integrate.
- **[Developers](developers/architecture.md)** — Code-level reference.

In-game, type `/g help` to get a clickable topic menu that mirrors this wiki.
```

- [ ] **Step 4: Create `wiki/docs/.pages`**

```yaml
nav:
  - index.md
  - Getting Started: getting-started
  - Players: players
  - Admins: admins
  - Developers: developers
```

- [ ] **Step 5: Append to `.gitignore`**

Add this line if not already present:

```text
site/
```

- [ ] **Step 6: Verify MkDocs builds locally**

Run:

```bash
python -m venv .venv
. .venv/Scripts/activate   # Windows
pip install -r wiki/requirements.txt
mkdocs build --strict
```

Expected: build succeeds. Will warn about missing `getting-started/`, `players/`, etc. — that's fine for this step (those folders are created in Task 4). If `--strict` errors on missing pages from nav, temporarily comment the nav lines in `.pages` and re-run. They get uncommented in Task 4.

- [ ] **Step 7: Commit**

```bash
git add mkdocs.yml wiki/requirements.txt wiki/docs/index.md wiki/docs/.pages .gitignore
git commit -m "docs(wiki): scaffold MkDocs Material site config"
```

---

## Task 2: GitHub Action — wiki deploy to GitHub Pages

**Files:**

- Create: `.github/workflows/wiki-deploy.yml`

- [ ] **Step 1: Create the workflow**

```yaml
name: Deploy Wiki

on:
  push:
    branches: [main]
    paths:
      - 'wiki/**'
      - 'mkdocs.yml'
      - '.github/workflows/wiki-deploy.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
          cache: 'pip'
          cache-dependency-path: wiki/requirements.txt
      - run: pip install -r wiki/requirements.txt
      - run: mkdocs build --strict
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: site

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/wiki-deploy.yml
git commit -m "ci(wiki): deploy MkDocs site to GitHub Pages on push to main"
```

- [ ] **Step 3: Note for human reviewer**

After merge, a maintainer must go to **GitHub Settings → Pages → Source: GitHub Actions** once to enable Pages. The workflow itself doesn't need that — but the site won't be visible until the toggle is flipped. Add this to the PR description.

---

## Task 3: Front-matter schema lint script

**Files:**

- Create: `tools/wiki/lint_frontmatter.py`
- Create: `tools/wiki/__init__.py` (empty)

- [ ] **Step 1: Create the lint script**

```python
#!/usr/bin/env python3
"""Validate YAML front-matter on every wiki/docs/**/*.md page.

Required fields: title, audience, topic, summary, keywords, related, updated.
audience must be one of: player, admin, dev.
topic must be a lowercase-hyphenated slug matching the filename stem.
summary must be ≤140 chars.

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

# Files that don't follow the slug-matches-filename rule:
SLUG_EXEMPT = {Path("index.md")}


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
    print(f"OK — {len(pages)} pages validated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Test it locally on `wiki/docs/index.md` only**

Run:

```bash
pip install pyyaml
python tools/wiki/lint_frontmatter.py
```

Expected output: `OK — 1 pages validated.` (index.md is the only file with front-matter at this point).

- [ ] **Step 3: Negative test — corrupt the file briefly to prove the lint catches errors**

Temporarily remove the `topic:` line from `wiki/docs/index.md`. Re-run the lint. Expected: exit 1, prints `index.md: missing required fields: ['topic']`. Restore the line.

- [ ] **Step 4: Create `tools/wiki/__init__.py` (empty)**

```python
```

- [ ] **Step 5: Commit**

```bash
git add tools/wiki/__init__.py tools/wiki/lint_frontmatter.py
git commit -m "ci(wiki): add front-matter schema lint script"
```

---

## Task 4: Build out nav skeleton — stub pages (front-matter only)

Goal: every page referenced in nav exists with valid front-matter, so `mkdocs build --strict` passes. Content for Getting Started and Players is filled in later tasks; Admins and Developers stay stubbed.

**Files:** all created under `wiki/docs/`. Each stub uses this body:

```markdown
---
title: <Title>
audience: <player|admin|dev>
topic: <slug>
summary: <one-line>
keywords: []
related: []
updated: 2026-05-13
---

# <Title>

*This page is a placeholder. Content coming in Phase 2/3.*
```

- [ ] **Step 1: Getting Started stubs**

Create these with front-matter only (one H1, the placeholder body — content lands in Task 6/7):

- `wiki/docs/getting-started/welcome.md` — `topic: welcome`, `audience: player`
- `wiki/docs/getting-started/walkthrough.md` — `topic: walkthrough`, `audience: player`
- `wiki/docs/getting-started/faq.md` — `topic: faq`, `audience: player`
- `wiki/docs/getting-started/.pages`:

```yaml
nav:
  - Welcome: welcome.md
  - Your first 30 minutes: walkthrough.md
  - FAQ: faq.md
```

- [ ] **Step 2: Players stubs (one per feature page in the design § 3)**

Front-matter only, `audience: player`. Topic slugs and titles:

| File | topic | title |
|---|---|---|
| `wiki/docs/players/how-do-i.md` | `how-do-i` | How do I…? |
| `wiki/docs/players/guilds.md` | `guilds` | Guilds |
| `wiki/docs/players/ranks.md` | `ranks` | Ranks & Permissions |
| `wiki/docs/players/homes.md` | `homes` | Homes |
| `wiki/docs/players/alliances.md` | `alliances` | Alliances & Diplomacy |
| `wiki/docs/players/war.md` | `war` | War |
| `wiki/docs/players/chat.md` | `chat` | Chat |
| `wiki/docs/players/identity.md` | `identity` | Tags, Banners & Identity |
| `wiki/docs/players/progression.md` | `progression` | Progression & Levels |
| `wiki/docs/players/vault.md` | `vault` | Vault |
| `wiki/docs/players/mode.md` | `mode` | Mode (Peaceful / Hostile) |
| `wiki/docs/players/shop.md` | `shop` | Shop integration |
| `wiki/docs/players/lfg.md` | `lfg` | LFG & Invites |
| `wiki/docs/players/bedrock.md` | `bedrock` | Bedrock differences |

`wiki/docs/players/.pages`:

```yaml
nav:
  - How do I…?: how-do-i.md
  - Guilds: guilds.md
  - Ranks & Permissions: ranks.md
  - Homes: homes.md
  - Alliances & Diplomacy: alliances.md
  - War: war.md
  - Chat: chat.md
  - Tags, Banners & Identity: identity.md
  - Progression & Levels: progression.md
  - Vault: vault.md
  - Mode: mode.md
  - Shop integration: shop.md
  - LFG & Invites: lfg.md
  - Bedrock differences: bedrock.md
```

- [ ] **Step 3: Admins stubs**

`audience: admin`. Slugs/titles:

| File | topic | title |
|---|---|---|
| `wiki/docs/admins/installation.md` | `installation` | Installation & config.yml |
| `wiki/docs/admins/permissions.md` | `permissions` | Permission nodes reference |
| `wiki/docs/admins/override.md` | `override` | `/lumaguilds override` and recovery |
| `wiki/docs/admins/troubleshooting.md` | `troubleshooting` | Troubleshooting |
| `wiki/docs/admins/placeholderapi.md` | `placeholderapi` | PlaceholderAPI placeholders |
| `wiki/docs/admins/rosechat.md` | `rosechat` | RoseChat integration |
| `wiki/docs/admins/geyser.md` | `geyser` | Geyser/Floodgate behavior |
| `wiki/docs/admins/lunar.md` | `lunar` | Lunar Client integration |
| `wiki/docs/admins/leaderboard-api.md` | `leaderboard-api` | Web leaderboard API |
| `wiki/docs/admins/claims.md` | `claims` | Claims integration |

`wiki/docs/admins/.pages`:

```yaml
nav:
  - Installation: installation.md
  - Permission nodes: permissions.md
  - Override & recovery: override.md
  - Troubleshooting: troubleshooting.md
  - PlaceholderAPI: placeholderapi.md
  - RoseChat: rosechat.md
  - Geyser/Floodgate: geyser.md
  - Lunar Client: lunar.md
  - Web leaderboard API: leaderboard-api.md
  - Claims: claims.md
```

- [ ] **Step 4: Developers stubs**

`audience: dev`. Slugs/titles:

| File | topic | title |
|---|---|---|
| `wiki/docs/developers/getting-started.md` | `dev-getting-started` | Developer getting started |
| `wiki/docs/developers/architecture.md` | `architecture` | Architecture overview |
| `wiki/docs/developers/domain.md` | `domain` | Domain layer |
| `wiki/docs/developers/application.md` | `application` | Application layer |
| `wiki/docs/developers/infrastructure.md` | `infrastructure` | Infrastructure layer |
| `wiki/docs/developers/interaction.md` | `interaction` | Interaction layer |
| `wiki/docs/developers/master-diagram.md` | `master-diagram` | Master diagram |
| `wiki/docs/developers/api-reference.md` | `api-reference` | API reference |
| `wiki/docs/developers/placeholders.md` | `placeholders-internal` | Placeholders (internal) |
| `wiki/docs/developers/emoji-permissions.md` | `emoji-permissions` | Emoji permissions |
| `wiki/docs/developers/migration-safety.md` | `migration-safety` | Migration safety |
| `wiki/docs/developers/schema-setup.md` | `schema-setup` | Schema setup |

`wiki/docs/developers/.pages`:

```yaml
nav:
  - Getting started: getting-started.md
  - Architecture: architecture.md
  - Domain layer: domain.md
  - Application layer: application.md
  - Infrastructure layer: infrastructure.md
  - Interaction layer: interaction.md
  - Master diagram: master-diagram.md
  - API reference: api-reference.md
  - Placeholders (internal): placeholders.md
  - Emoji permissions: emoji-permissions.md
  - Migration safety: migration-safety.md
  - Schema setup: schema-setup.md
```

- [ ] **Step 5: Note about slug ↔ filename mismatch**

The Developers section has two slug-vs-filename mismatches:

- `developers/getting-started.md` uses `topic: dev-getting-started` (because `getting-started` is also the folder name for the Getting Started section; we want a unique slug).
- `developers/placeholders.md` uses `topic: placeholders-internal` (because `players/chat.md` references player-facing placeholders; we want the dev page slug to be unambiguous).

Update `tools/wiki/lint_frontmatter.py` to handle this — add to `SLUG_EXEMPT`:

```python
SLUG_EXEMPT = {
    Path("index.md"),
    Path("developers/getting-started.md"),
    Path("developers/placeholders.md"),
}
```

- [ ] **Step 6: Run lint, fix any failures**

```bash
python tools/wiki/lint_frontmatter.py
```

Expected: all ~40 pages pass.

- [ ] **Step 7: Run `mkdocs build --strict`**

```bash
mkdocs build --strict
```

Expected: build succeeds with no warnings.

- [ ] **Step 8: Commit**

```bash
git add wiki/docs/getting-started wiki/docs/players wiki/docs/admins wiki/docs/developers tools/wiki/lint_frontmatter.py
git commit -m "docs(wiki): scaffold nav skeleton with stubbed front-matter pages"
```

---

## Task 5: CI workflow for front-matter lint + (placeholder) parity check

**Files:**

- Create: `.github/workflows/wiki-checks.yml`

The parity-check script is added in Task 11 (after `HelpTopics.kt` exists). For now, the workflow runs the front-matter lint only.

- [ ] **Step 1: Create the workflow**

```yaml
name: Wiki Checks

on:
  pull_request:
    paths:
      - 'wiki/**'
      - 'mkdocs.yml'
      - 'tools/wiki/**'
      - 'src/main/kotlin/net/lumalyte/lg/interaction/help/**'
      - '.github/workflows/wiki-checks.yml'

jobs:
  frontmatter-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - run: pip install pyyaml
      - run: python tools/wiki/lint_frontmatter.py

  mkdocs-strict-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
          cache: 'pip'
          cache-dependency-path: wiki/requirements.txt
      - run: pip install -r wiki/requirements.txt
      - run: mkdocs build --strict
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/wiki-checks.yml
git commit -m "ci(wiki): run front-matter lint and strict MkDocs build on PRs"
```

---

## Task 6: Write Getting Started — Welcome page

**Files:**

- Modify: `wiki/docs/getting-started/welcome.md`

The audience for this page is a brand-new player who just joined EnthusiaSMP.

- [ ] **Step 1: Replace the stub with full content**

Required structure (preserve front-matter; replace body):

```markdown
---
title: Welcome to EnthusiaSMP
audience: player
topic: welcome
summary: Orientation for new players — what EnthusiaSMP is, what LumaGuilds adds, and where to go next.
keywords: [welcome, new player, onboarding, enthusiasmp]
related: [walkthrough, faq]
updated: 2026-05-13
---

# Welcome to EnthusiaSMP

This page orients you to the server and the guild system in about two minutes. When you're ready for the hands-on tour, head to **[Your first 30 minutes](walkthrough.md)**.

## What is EnthusiaSMP?

<!-- 2–3 sentence pitch: what kind of server (semi-anarchy SMP), the vibe, who runs it (FainNoir owns; BadgersMC dev). -->

## What does LumaGuilds add?

LumaGuilds is the guild plugin. It lets you:

- Form a guild with friends and share a tag, banner, and identity.
- Set named guild homes that any member (or specific ranks) can teleport to.
- Form alliances, declare wars, sign truces.
- Earn guild XP together as you play, unlocking perks and bigger member caps.
- Run a shared vault.

## What this wiki is for

- **[Getting Started → Your first 30 minutes](walkthrough.md)** — guided tour.
- **[Players](../players/how-do-i.md)** — every feature explained, organized by topic.
- **[Admins](../admins/installation.md)** and **[Developers](../developers/architecture.md)** — if you run a server or work on the plugin.

In-game, type `/g help` for the same content as a clickable menu.

## What this wiki is *not*

- Not a list of rules. Server rules live elsewhere (ask staff).
- Not a release log. See [`CHANGELOG.md`](https://github.com/BadgersMC/LumaGuilds/blob/main/CHANGELOG.md) on GitHub.

Ready? **[Start the 30-minute walkthrough →](walkthrough.md)**
```

The comment block `<!-- ... -->` is a placeholder for content you'll want a human to write — the server pitch. The plan ships with a TODO marker visible in the rendered page only via comment (so MkDocs strict mode still passes). Leave it for the maintainer to fill on first PR review pass; it's not blocking.

- [ ] **Step 2: Run lint + build**

```bash
python tools/wiki/lint_frontmatter.py
mkdocs build --strict
```

Expected: both pass.

- [ ] **Step 3: Commit**

```bash
git add wiki/docs/getting-started/welcome.md
git commit -m "docs(wiki): write Getting Started welcome page"
```

---

## Task 7: Write Getting Started — Walkthrough (the 7-step tour)

**Files:**

- Modify: `wiki/docs/getting-started/walkthrough.md`

This is the "first 30 minutes" page. Single page, seven H2 sections (each H2 is one step). Keep it scannable — short paragraphs, code blocks for commands, no walls of text.

- [ ] **Step 1: Replace the stub with the full walkthrough**

```markdown
---
title: Your first 30 minutes
audience: player
topic: walkthrough
summary: A guided tour for new players — spawn to working guild in seven steps.
keywords: [walkthrough, tutorial, onboarding, first time, getting started]
related: [welcome, guilds, homes, chat]
updated: 2026-05-13
---

# Your first 30 minutes

A guided tour for new players. Follow it top-to-bottom and you'll end up in a working guild with a tag, a home, chat configured, and a clear next move.

## 1. Spawn and orientation

When you first join, you'll spawn at the world spawn. Look around. Note:

- Chat is global by default. Anything you type goes to everyone.
- You don't need a guild to play, but a lot of the social and PvE/PvP features run through guilds.
- Type `/g help` any time to get a clickable in-game help menu.

## 2. Joining or creating a guild

You have three options:

**Join an existing guild** — ask in chat, or browse leaderboards with `/g list`. If a guild is open, you can join directly. If it's invite-only, ask a member to `/g invite` you.

**Create your own** — pick a name (plain text, max 32 chars, letters/numbers/spaces/`'`/`&`/`-`) and run:

```

/g create <name>

```text

Example: `/g create White Lotus`

**Hold off for now** — you don't have to. You can come back later.

See [Players → Guilds](../players/guilds.md) for the full reference.

## 3. Setting your tag and home

Once you're in a guild, two things make it feel like home.

**Tag** — fancy formatting that appears next to your name in chat. Use MiniMessage:

```

/g tag <gradient:#FF6A00:#FF1F00>Lotus</gradient>

```text

Or run `/g tag` with no arguments to open a visual editor.

**Home** — a teleport point your guild can share. Stand where you want it and run:

```

/g sethome

```text

That sets the default home. You can have multiple — `/g sethome shop`, `/g sethome mine`. Teleport with `/g home` or `/g home <name>`. See [Players → Homes](../players/homes.md).

## 4. Inviting a friend

```

/g invite <player>

```text

They run `/g accept <yourguild>` (or click the chat prompt). Done.

Need more control? You can lock invites to specific ranks, or set up "open" mode so anyone can join. See [Players → LFG & Invites](../players/lfg.md).

## 5. Chat basics

Two toggles you should know:

- `/g chat` — flips your messages into guild chat. Type it again to switch back to global. Same word, same key, easy to remember.
- `/g allychat` — same idea, but to all allied guilds.

Tags and colors only appear in chat if the chat plugin renders them — they do on this server. See [Players → Chat](../players/chat.md) for the details.

## 6. What XP and levels do

Your guild earns XP from what its members do: mining, killing mobs, exploring. XP turns into levels. Levels unlock:

- Bigger member caps.
- More named home slots.
- Access to advanced features (alliances, war declarations).
- Bragging rights on the leaderboard.

You don't have to micromanage it — just play. See [Players → Progression & Levels](../players/progression.md).

## 7. Where to go next

If you got this far you're functional. Pick one:

- **Decorate your guild** — set a description (`/g desc`), pick a banner (`/g menu` → Banner).
- **Make a friend or enemy** — `/g info <other guild>`, then `/g ally <them>` or `/g enemy <them>`. See [Players → Alliances & Diplomacy](../players/alliances.md).
- **Read the [How do I…? index](../players/how-do-i.md)** — every common task with a deep link to the right page.

That's the tour. The rest of the wiki is reference — come back as needed.
```

- [ ] **Step 2: Lint + build**

```bash
python tools/wiki/lint_frontmatter.py
mkdocs build --strict
```

- [ ] **Step 3: Commit**

```bash
git add wiki/docs/getting-started/walkthrough.md
git commit -m "docs(wiki): write 7-step Getting Started walkthrough"
```

---

## Task 8: Write Getting Started — FAQ

**Files:**

- Modify: `wiki/docs/getting-started/faq.md`

Short FAQ. Each Q is an H2 (stable anchor — players link friends to specific entries).

- [ ] **Step 1: Replace the stub**

```markdown
---
title: FAQ
audience: player
topic: faq
summary: Common questions from new players about LumaGuilds.
keywords: [faq, common questions, help, troubleshooting]
related: [walkthrough, chat, homes]
updated: 2026-05-13
---

# FAQ

## Do I have to be in a guild?

No. You can play the entire server solo. Joining a guild just unlocks the guild-specific features (shared homes, alliance/war, guild chat, progression).

## How do I leave my guild?

`/g leave`. If you're the owner, you must transfer ownership first (`/g transfer <player>`) or disband (`/g disband`).

## My guild tag isn't showing colors in chat

The colors are stored as MiniMessage, which the chat plugin converts to display colors. If they're not rendering, you may be in a chat channel that doesn't format them. Try `/g chat` to switch to guild chat — colors render there.

## Why does `/g home` say "teleport failed"?

The destination is unsafe (lava/fire/cactus right where the home was set), or a protection plugin blocked the teleport. Move the home (`/g sethome` somewhere safe) and try again.

## Can I have more than one home?

Yes. `/g sethome <name>` creates additional named homes (`shop`, `mine`, etc.). The number of slots scales with your guild level. See [Players → Homes](../players/homes.md).

## How do alliances work?

`/g ally <other guild>` sends a request. They run `/g ally <you>` back to accept. Until both have done it, you're not actually allied. See [Players → Alliances & Diplomacy](../players/alliances.md).

## I'm on Bedrock — does this all work?

Mostly. A few menus open as chat-based forms instead of inventory GUIs, and clickable chat buttons map to taps. See [Players → Bedrock differences](../players/bedrock.md).

## Where do I report a bug?

Tell staff in-game or open an issue at <https://github.com/BadgersMC/LumaGuilds/issues>.
```

- [ ] **Step 2: Lint + build, commit**

```bash
python tools/wiki/lint_frontmatter.py
mkdocs build --strict
git add wiki/docs/getting-started/faq.md
git commit -m "docs(wiki): write Getting Started FAQ"
```

---

## Task 9: `HelpTopic` and `HelpCommandEntry` data classes (TDD)

**Files:**

- Create: `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpCommandEntry.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopic.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicTest.kt`:

```kotlin
package net.lumalyte.lg.interaction.help

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HelpTopicTest {

    @Test
    fun `HelpCommandEntry stores syntax, blurb and prefill`() {
        val entry = HelpCommandEntry(
            syntax = "/g sethome [name]",
            blurb = "Set a guild home at your current location.",
            prefill = "/g sethome ",
        )
        assertEquals("/g sethome [name]", entry.syntax)
        assertEquals("Set a guild home at your current location.", entry.blurb)
        assertEquals("/g sethome ", entry.prefill)
    }

    @Test
    fun `HelpTopic exposes slug, display name, summary, commands`() {
        val topic = HelpTopic(
            slug = "homes",
            displayName = "Homes",
            summary = "Set and visit guild homes.",
            commands = listOf(
                HelpCommandEntry("/g sethome [name]", "Set a home.", "/g sethome "),
                HelpCommandEntry("/g home [name]", "Visit a home.", "/g home "),
            ),
        )
        assertEquals("homes", topic.slug)
        assertEquals("Homes", topic.displayName)
        assertEquals(2, topic.commands.size)
    }

    @Test
    fun `HelpTopic rejects an invalid slug`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            HelpTopic(
                slug = "Bad Slug!",
                displayName = "Bad",
                summary = "x",
                commands = emptyList(),
            )
        }
        assertTrue("slug" in ex.message!!.lowercase())
    }

    @Test
    fun `HelpTopic rejects an empty display name`() {
        assertFailsWith<IllegalArgumentException> {
            HelpTopic(slug = "x", displayName = "", summary = "y", commands = emptyList())
        }
    }
}
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicTest"
```

Expected: compile error — classes don't exist yet.

- [ ] **Step 3: Create `HelpCommandEntry.kt`**

```kotlin
package net.lumalyte.lg.interaction.help

/**
 * One command line in a [HelpTopic]'s command list, shown both in-game and
 * (via the parity check) referenced by the corresponding wiki page.
 */
data class HelpCommandEntry(
    val syntax: String,
    val blurb: String,
    val prefill: String,
)
```

- [ ] **Step 4: Create `HelpTopic.kt`**

```kotlin
package net.lumalyte.lg.interaction.help

/**
 * A help topic surfaced by `/g help` and mirrored to a wiki page under
 * `wiki/docs/players/<slug>.md`. The [slug] is the stable identifier and
 * must match the wiki page's `topic:` front-matter field.
 */
data class HelpTopic(
    val slug: String,
    val displayName: String,
    val summary: String,
    val commands: List<HelpCommandEntry>,
) {
    init {
        require(SLUG_REGEX.matches(slug)) { "Invalid slug: $slug (must be lowercase-hyphenated)" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(summary.isNotBlank()) { "summary must not be blank" }
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    }
}
```

- [ ] **Step 5: Run test — expect pass**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/help/HelpCommandEntry.kt \
        src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopic.kt \
        src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicTest.kt
git commit -m "feat(help): add HelpTopic and HelpCommandEntry data classes"
```

---

## Task 10: `HelpTopics` registry (TDD)

**Files:**

- Create: `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopics.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsTest.kt`

This is the source of truth. Every player-facing topic appears here exactly once. CI will cross-check against `wiki/docs/players/<slug>.md`.

- [ ] **Step 1: Write the failing test**

```kotlin
package net.lumalyte.lg.interaction.help

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpTopicsTest {

    @Test
    fun `all 13 player topics are registered`() {
        val expected = setOf(
            "guilds", "ranks", "homes", "alliances", "war",
            "chat", "identity", "progression", "vault", "mode",
            "shop", "lfg", "bedrock",
        )
        assertEquals(expected, HelpTopics.all.map { it.slug }.toSet())
    }

    @Test
    fun `topics are returned in display order`() {
        val firstFour = HelpTopics.all.take(4).map { it.slug }
        assertEquals(listOf("guilds", "homes", "ranks", "chat"), firstFour)
    }

    @Test
    fun `bySlug returns the matching topic`() {
        val homes = HelpTopics.bySlug("homes")
        assertNotNull(homes)
        assertEquals("Homes", homes.displayName)
    }

    @Test
    fun `bySlug is case-insensitive`() {
        assertNotNull(HelpTopics.bySlug("HOMES"))
        assertNotNull(HelpTopics.bySlug("Homes"))
    }

    @Test
    fun `bySlug returns null for unknown slug`() {
        assertNull(HelpTopics.bySlug("nonexistent"))
    }

    @Test
    fun `every topic has at least one command`() {
        HelpTopics.all.forEach { topic ->
            assertTrue(topic.commands.isNotEmpty(), "Topic ${topic.slug} has no commands")
        }
    }

    @Test
    fun `every topic summary fits the 140-char wiki front-matter limit`() {
        HelpTopics.all.forEach { topic ->
            assertTrue(
                topic.summary.length <= 140,
                "Topic ${topic.slug} summary is ${topic.summary.length} chars",
            )
        }
    }
}
```

- [ ] **Step 2: Run test — expect failure (HelpTopics doesn't exist)**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicsTest"
```

Expected: compile error.

- [ ] **Step 3: Create `HelpTopics.kt`**

Ordering note: the in-game menu order is **Guilds, Homes, Ranks, Chat, Alliances, War, Progression, Vault, Identity, Mode, Shop, LFG, Bedrock**. Homes deliberately sits second because it's one of the most-asked-about topics.

```kotlin
package net.lumalyte.lg.interaction.help

/**
 * Source of truth for player-facing help topics.
 *
 * Every entry has a matching wiki page at `wiki/docs/players/<slug>.md`.
 * CI fails the build if any slug here lacks a wiki page, or vice versa.
 */
object HelpTopics {

    /** Base URL for wiki deep-links surfaced in the in-game help. */
    const val WIKI_BASE_URL = "https://badgersmc.github.io/LumaGuilds/players"

    val all: List<HelpTopic> = listOf(
        HelpTopic(
            slug = "guilds",
            displayName = "Guilds",
            summary = "Create, join, leave, transfer, and disband guilds.",
            commands = listOf(
                HelpCommandEntry("/g create <name>", "Create a new guild.", "/g create "),
                HelpCommandEntry("/g join <guild>", "Request to join a guild.", "/g join "),
                HelpCommandEntry("/g leave", "Leave your current guild.", "/g leave"),
                HelpCommandEntry("/g disband", "Disband your guild (owner only).", "/g disband"),
                HelpCommandEntry("/g transfer <player>", "Transfer ownership.", "/g transfer "),
                HelpCommandEntry("/g info [guild]", "View guild information.", "/g info "),
                HelpCommandEntry("/g list", "Browse all guilds.", "/g list"),
            ),
        ),
        HelpTopic(
            slug = "homes",
            displayName = "Homes",
            summary = "Set, name, visit, and restrict access to guild homes.",
            commands = listOf(
                HelpCommandEntry("/g sethome [name]", "Set a home at your current location.", "/g sethome "),
                HelpCommandEntry("/g home [name]", "Teleport to a guild home.", "/g home "),
                HelpCommandEntry("/g homes", "List your guild's homes.", "/g homes"),
                HelpCommandEntry("/g removehome <name>", "Remove a named home.", "/g removehome "),
                HelpCommandEntry("/g setallyhome", "Set your guild's ally-home.", "/g setallyhome"),
                HelpCommandEntry("/g removeallyhome", "Remove your guild's ally-home.", "/g removeallyhome"),
            ),
        ),
        HelpTopic(
            slug = "ranks",
            displayName = "Ranks & Permissions",
            summary = "Create ranks, set permissions, and manage member rank assignments.",
            commands = listOf(
                HelpCommandEntry("/g ranks", "Open the rank management menu.", "/g ranks"),
                HelpCommandEntry("/g menu", "Open the guild control panel.", "/g menu"),
            ),
        ),
        HelpTopic(
            slug = "chat",
            displayName = "Chat",
            summary = "Toggle guild and ally chat, and customize how your tag appears in messages.",
            commands = listOf(
                HelpCommandEntry("/g chat", "Toggle guild chat on/off.", "/g chat"),
                HelpCommandEntry("/g allychat", "Toggle ally chat on/off.", "/g allychat"),
            ),
        ),
        HelpTopic(
            slug = "alliances",
            displayName = "Alliances & Diplomacy",
            summary = "Form alliances, declare enemies, sign truces, and manage ally-home access.",
            commands = listOf(
                HelpCommandEntry("/g ally <guild>", "Request or accept an alliance.", "/g ally "),
                HelpCommandEntry("/g enemy <guild>", "Mark a guild as enemy.", "/g enemy "),
                HelpCommandEntry("/g truce <guild>", "Sign a truce.", "/g truce "),
                HelpCommandEntry("/g neutral <guild>", "Clear a relation.", "/g neutral "),
            ),
        ),
        HelpTopic(
            slug = "war",
            displayName = "War",
            summary = "Declare and fight wars between guilds.",
            commands = listOf(
                HelpCommandEntry("/g war <guild>", "Open the war control flow.", "/g war "),
            ),
        ),
        HelpTopic(
            slug = "progression",
            displayName = "Progression & Levels",
            summary = "Earn guild XP from member activity, level up, and unlock perks.",
            commands = listOf(
                HelpCommandEntry("/g info", "See your guild's level and XP.", "/g info"),
            ),
        ),
        HelpTopic(
            slug = "vault",
            displayName = "Vault",
            summary = "Use the shared guild vault for storing items.",
            commands = listOf(
                HelpCommandEntry("/g vault", "Open the guild vault.", "/g vault"),
                HelpCommandEntry("/g getvault", "Get a vault chest item.", "/g getvault"),
            ),
        ),
        HelpTopic(
            slug = "identity",
            displayName = "Tags, Banners & Identity",
            summary = "Set your guild's tag, description, and banner.",
            commands = listOf(
                HelpCommandEntry("/g tag [text]", "Set or edit your guild tag.", "/g tag "),
                HelpCommandEntry("/g desc <text>", "Set your guild description.", "/g desc "),
                HelpCommandEntry("/g rename <name>", "Rename your guild.", "/g rename "),
                HelpCommandEntry("/g emoji", "Pick a guild emoji.", "/g emoji"),
            ),
        ),
        HelpTopic(
            slug = "mode",
            displayName = "Mode (Peaceful / Hostile)",
            summary = "Switch your guild between peaceful and hostile mode.",
            commands = listOf(
                HelpCommandEntry("/g mode", "Open the mode selection menu.", "/g mode"),
            ),
        ),
        HelpTopic(
            slug = "shop",
            displayName = "Shop integration",
            summary = "Link guild-owned shops.",
            commands = listOf(
                HelpCommandEntry("/g setshop", "Mark your current shop as guild-owned.", "/g setshop"),
            ),
        ),
        HelpTopic(
            slug = "lfg",
            displayName = "LFG & Invites",
            summary = "Manage invites, decline requests, and use LFG to find a guild.",
            commands = listOf(
                HelpCommandEntry("/g invite <player>", "Invite a player to your guild.", "/g invite "),
                HelpCommandEntry("/g invites", "List your pending invites.", "/g invites"),
                HelpCommandEntry("/g decline <guild>", "Decline an invite.", "/g decline "),
                HelpCommandEntry("/g lfg", "Toggle LFG (looking-for-guild).", "/g lfg"),
                HelpCommandEntry("/g kick <player>", "Kick a member from your guild.", "/g kick "),
            ),
        ),
        HelpTopic(
            slug = "bedrock",
            displayName = "Bedrock differences",
            summary = "Where LumaGuilds behaves differently on Bedrock/Geyser.",
            commands = emptyList<HelpCommandEntry>().let {
                listOf(
                    HelpCommandEntry(
                        syntax = "(see wiki)",
                        blurb = "Bedrock menus open as forms; clickable chat maps to taps.",
                        prefill = "",
                    ),
                )
            },
        ),
    )

    private val bySlugMap: Map<String, HelpTopic> = all.associateBy { it.slug }

    fun bySlug(slug: String): HelpTopic? = bySlugMap[slug.lowercase()]
}
```

- [ ] **Step 4: Run test — expect failures on ordering**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicsTest"
```

Expected: passes. If the ordering test fails, the first-four order must be `guilds, homes, ranks, chat` — that's the order in `all` above.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopics.kt \
        src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsTest.kt
git commit -m "feat(help): add HelpTopics registry (source of truth for help + wiki)"
```

---

## Task 11: Topic-parity CI check

**Files:**

- Create: `tools/wiki/check_topic_parity.py`
- Modify: `.github/workflows/wiki-checks.yml`

The script reads `HelpTopics.kt`, extracts each `slug = "..."` literal, and asserts each has a matching `wiki/docs/players/<slug>.md` (and vice versa).

- [ ] **Step 1: Create `tools/wiki/check_topic_parity.py`**

```python
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
WIKI_ONLY = {"how-do-i"}

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
            "See CONTRIBUTING.md → 'Keeping /g help and the wiki in sync'.",
            file=sys.stderr,
        )
        return 1

    print(f"OK — {len(kt)} topics in parity with wiki.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Run locally**

```bash
python tools/wiki/check_topic_parity.py
```

Expected: `OK — 13 topics in parity with wiki.` (all 13 stub pages exist from Task 4; all 13 are registered from Task 10).

- [ ] **Step 3: Negative test**

Temporarily comment out the `homes` entry in `HelpTopics.kt`. Re-run the script. Expected: exit 1, prints `homes` under "Wiki pages missing a HelpTopics entry". Restore.

- [ ] **Step 4: Add the check to `.github/workflows/wiki-checks.yml`**

Add a new job to the existing workflow:

```yaml
  topic-parity:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.11'
      - run: python tools/wiki/check_topic_parity.py
```

Also expand the workflow's `paths:` filter to trigger when `HelpTopics.kt` changes — already covered by `src/main/kotlin/net/lumalyte/lg/interaction/help/**` in Task 5.

- [ ] **Step 5: Commit**

```bash
git add tools/wiki/check_topic_parity.py .github/workflows/wiki-checks.yml
git commit -m "ci(wiki): enforce HelpTopics.kt <-> wiki/docs/players/ slug parity"
```

---

## Task 12: `HelpTopicsRenderer` — topic menu (TDD)

**Files:**

- Create: `src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRenderer.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRendererTest.kt`

The renderer builds Adventure `Component`s. Tests assert structural properties (children count, click event present, command suggestion is correct) using `Component.children()`.

- [ ] **Step 1: Write the failing test for the topic-menu render**

```kotlin
package net.lumalyte.lg.interaction.help

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HelpTopicsRendererTest {

    private val renderer = HelpTopicsRenderer

    @Test
    fun `topic menu renders a non-empty component`() {
        val component = renderer.renderTopicMenu()
        assertTrue(component != Component.empty())
    }

    @Test
    fun `topic menu lists every topic in HelpTopics`() {
        val rendered = renderer.renderTopicMenu().toPlainText()
        HelpTopics.all.forEach { topic ->
            assertTrue(
                topic.displayName in rendered,
                "Topic menu is missing display name '${topic.displayName}'",
            )
        }
    }

    @Test
    fun `each topic entry has a click event running the help command`() {
        val rendered = renderer.renderTopicMenu()
        HelpTopics.all.forEach { topic ->
            val match = rendered.findRunCommandClick("/g help ${topic.slug}")
            assertNotNull(match, "No RUN_COMMAND click for /g help ${topic.slug}")
        }
    }

    @Test
    fun `topic menu includes a wiki link at the bottom`() {
        val rendered = renderer.renderTopicMenu().toPlainText()
        assertTrue(HelpTopics.WIKI_BASE_URL in rendered)
    }
}

private fun Component.toPlainText(): String =
    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(this)

private fun Component.allComponents(): List<Component> =
    listOf(this) + children().flatMap { it.allComponents() }

private fun Component.findRunCommandClick(command: String): Component? =
    allComponents().firstOrNull {
        val ce = it.clickEvent()
        ce?.action() == ClickEvent.Action.RUN_COMMAND && ce.value() == command
    }

private fun Component.findSuggestCommandClick(value: String): Component? =
    allComponents().firstOrNull {
        val ce = it.clickEvent()
        ce?.action() == ClickEvent.Action.SUGGEST_COMMAND && ce.value() == value
    }
```

- [ ] **Step 2: Run test — expect compile failure**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicsRendererTest"
```

Expected: compile error.

- [ ] **Step 3: Create `HelpTopicsRenderer.kt`**

```kotlin
package net.lumalyte.lg.interaction.help

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * Builds Adventure components for the in-game `/g help` UI.
 *
 * Two public surfaces:
 *  - [renderTopicMenu] — the top-level topic picker (no argument).
 *  - [renderTopicPage] — one topic's command list (`/g help <topic>`).
 *
 * All click events use `RUN_COMMAND` (for topic switches) or
 * `SUGGEST_COMMAND` (for command prefill) so Geyser maps them to taps.
 */
object HelpTopicsRenderer {

    private const val ACCENT = "§6"
    private const val DIM = "§7"
    private const val HIGHLIGHT = "§e"

    fun renderTopicMenu(): Component {
        val header = Component.text("─── LumaGuilds Help ───", NamedTextColor.GOLD, TextDecoration.BOLD)
        val intro = Component.text("Pick a topic (click or type /g help <topic>):", NamedTextColor.GRAY)

        val entries = HelpTopics.all.map { topic ->
            Component.text()
                .append(Component.text("  [", NamedTextColor.DARK_GRAY))
                .append(Component.text(topic.displayName, NamedTextColor.YELLOW))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(topic.summary, NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand("/g help ${topic.slug}"))
                .hoverEvent(HoverEvent.showText(Component.text("Open ${topic.displayName} help", NamedTextColor.GOLD)))
                .build()
        }

        val wikiLink = Component.text()
            .append(Component.text("Full wiki: ", NamedTextColor.GRAY))
            .append(
                Component.text(HelpTopics.WIKI_BASE_URL, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(HelpTopics.WIKI_BASE_URL))
                    .hoverEvent(HoverEvent.showText(Component.text("Open in browser", NamedTextColor.GOLD))),
            )
            .build()

        val out = Component.text()
            .append(header).append(Component.newline())
            .append(intro).append(Component.newline())
        entries.forEach { out.append(it).append(Component.newline()) }
        out.append(wikiLink)
        return out.build()
    }

    fun renderTopicPage(topic: HelpTopic): Component {
        val header = Component.text("─── Help · ${topic.displayName} ───", NamedTextColor.GOLD, TextDecoration.BOLD)
        val summary = Component.text(topic.summary, NamedTextColor.GRAY)

        val commandLines = topic.commands.map { entry ->
            val line = Component.text()
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(entry.syntax, NamedTextColor.WHITE))
            if (entry.prefill.isNotEmpty()) {
                line.clickEvent(ClickEvent.suggestCommand(entry.prefill))
                    .hoverEvent(
                        HoverEvent.showText(
                            Component.text()
                                .append(Component.text(entry.blurb, NamedTextColor.GOLD))
                                .append(Component.newline())
                                .append(Component.text("Click to prefill in chat", NamedTextColor.GRAY))
                                .build(),
                        ),
                    )
            } else {
                line.hoverEvent(HoverEvent.showText(Component.text(entry.blurb, NamedTextColor.GOLD)))
            }
            line.build()
        }

        val wikiUrl = "${HelpTopics.WIKI_BASE_URL}/${topic.slug}/"
        val wikiLine = Component.text()
            .append(Component.text("Read more: ", NamedTextColor.GRAY))
            .append(
                Component.text(wikiUrl, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(wikiUrl))
                    .hoverEvent(HoverEvent.showText(Component.text("Open in browser", NamedTextColor.GOLD))),
            )
            .build()

        val back = Component.text("[Back to topics]", NamedTextColor.YELLOW)
            .clickEvent(ClickEvent.runCommand("/g help"))
            .hoverEvent(HoverEvent.showText(Component.text("Open the topic menu", NamedTextColor.GOLD)))

        val out = Component.text()
            .append(header).append(Component.newline())
            .append(summary).append(Component.newline())
            .append(Component.text("Commands:", NamedTextColor.YELLOW)).append(Component.newline())
        commandLines.forEach { out.append(it).append(Component.newline()) }
        out.append(Component.newline())
            .append(wikiLine).append(Component.newline())
            .append(back)
        return out.build()
    }
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicsRendererTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRenderer.kt \
        src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRendererTest.kt
git commit -m "feat(help): render topic menu via Adventure components"
```

---

## Task 13: Renderer tests for topic-page output

**Files:**

- Modify: `src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRendererTest.kt`

- [ ] **Step 1: Add tests for `renderTopicPage`**

Append to the existing test class (above the private extension functions at the bottom of the file):

```kotlin
    @Test
    fun `topic page header includes the topic display name`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes).toPlainText()
        assertTrue("Help · Homes" in rendered)
    }

    @Test
    fun `topic page lists every command syntax for that topic`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes).toPlainText()
        homes.commands.forEach { entry ->
            assertTrue(entry.syntax in rendered, "Missing syntax '${entry.syntax}'")
        }
    }

    @Test
    fun `command entries with a prefill use SUGGEST_COMMAND click`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes)
        val sethomeClick = rendered.findSuggestCommandClick("/g sethome ")
        assertNotNull(sethomeClick, "No SUGGEST_COMMAND click prefilling '/g sethome '")
    }

    @Test
    fun `topic page includes deep link to matching wiki URL`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes).toPlainText()
        assertTrue("${HelpTopics.WIKI_BASE_URL}/homes/" in rendered)
    }

    @Test
    fun `topic page has a Back to topics action`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes)
        val back = rendered.findRunCommandClick("/g help")
        assertNotNull(back, "No RUN_COMMAND click for '/g help' (Back action)")
    }
```

- [ ] **Step 2: Run — expect pass**

```bash
./gradlew test --tests "net.lumalyte.lg.interaction.help.HelpTopicsRendererTest"
```

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/lumalyte/lg/interaction/help/HelpTopicsRendererTest.kt
git commit -m "test(help): verify topic-page rendering (click events, wiki link, back action)"
```

---

## Task 14: Refactor `GuildCommand.onHelp` to delegate to the renderer

**Files:**

- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt` (lines 1905–1967)

- [ ] **Step 1: Replace the existing `onHelp` body**

Locate the method at line 1905. Replace from the `@Subcommand("help")` annotation through the closing `}` of the function (lines 1905–1967 inclusive) with:

```kotlin
    @Subcommand("help")
    @CommandPermission("lumaguilds.guild.help")
    fun onHelp(player: Player, @Optional topic: String?) {
        val renderer = HelpTopicsRenderer
        if (topic.isNullOrBlank()) {
            player.sendMessage(renderer.renderTopicMenu())
            return
        }
        val found = HelpTopics.bySlug(topic)
        if (found == null) {
            player.sendMessage(
                Component.text()
                    .append(Component.text("Unknown help topic '", NamedTextColor.RED))
                    .append(Component.text(topic, NamedTextColor.YELLOW))
                    .append(Component.text("'. Type ", NamedTextColor.RED))
                    .append(
                        Component.text("/g help", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/g help")),
                    )
                    .append(Component.text(" to see all topics.", NamedTextColor.RED))
                    .build(),
            )
            return
        }
        player.sendMessage(renderer.renderTopicPage(found))
    }
```

- [ ] **Step 2: Add the imports near the top of the file**

In the import block at the top of `GuildCommand.kt`, add (alphabetically placed):

```kotlin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.interaction.help.HelpTopics
import net.lumalyte.lg.interaction.help.HelpTopicsRenderer
```

Skip any that are already imported.

- [ ] **Step 3: Build to verify**

```bash
./gradlew build -x test
```

Expected: success.

- [ ] **Step 4: Run all tests**

```bash
./gradlew test
```

Expected: all pass, including the new HelpTopics tests.

- [ ] **Step 5: Manual smoke test (optional but recommended)**

Build the shadow jar (`./gradlew shadowJar`), drop into a local Paper test server, type `/g help`, then `/g help homes`, then click around. Confirm the topic menu renders, topic page renders, click-prefill works, wiki link is present.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt
git commit -m "refactor(help): /g help renders from HelpTopicsRenderer (clickable, topic-based)"
```

---

## Task 15: Write Players — Guilds page

**Files:**

- Modify: `wiki/docs/players/guilds.md`

Use the page template from "Conventions" above. Content checklist:

- One-sentence summary repeats `summary:`.
- Quick reference table covers: `/g create`, `/g join`, `/g leave`, `/g disband`, `/g transfer`, `/g info`, `/g list`. Permission column = the `@CommandPermission` string from `GuildCommand.kt`.
- "How it works" paragraph: a guild is a named group with a tag, members, ranks, homes, a vault, and relations to other guilds. One owner, transferable. Levels via XP.
- H2 "Creating a guild" — name rules (max 32, letters/numbers/spaces/`'`/`&`/`-`, no formatting tags), example.
- H2 "Joining a guild" — invitation flow, open vs invite-only.
- H2 "Leaving and transferring" — owner can't leave without transfer or disband.
- H2 "Browsing guilds" — `/g list`, `/g info <name>`.
- H2 "Gotchas" — one-guild-at-a-time, can't rejoin during a war cooldown, etc.
- "Related" links to: ranks, homes, alliances.

- [ ] **Step 1: Write the page**

Use the template. Example skeleton (fill in narrative text):

```markdown
---
title: Guilds
audience: player
topic: guilds
summary: How to create, join, leave, transfer, and disband guilds.
keywords: [guilds, create, join, leave, disband, transfer]
related: [ranks, homes, alliances]
updated: 2026-05-13
---

# Guilds

How to create, join, leave, transfer, and disband guilds.

## Quick reference

| Command | Permission | Description |
|---------|------------|-------------|
| `/g create <name>` | `lumaguilds.guild.create` | Create a new guild. |
| `/g join <guild>` | `lumaguilds.guild.join` | Request to join. |
| `/g leave` | `lumaguilds.guild.leave` | Leave your guild. |
| `/g disband` | `lumaguilds.guild.disband` | Disband (owner only). |
| `/g transfer <player>` | `lumaguilds.guild.transfer` | Transfer ownership. |
| `/g info [guild]` | `lumaguilds.guild.info` | View guild info. |
| `/g list` | `lumaguilds.guild.list` | Browse all guilds. |

## How it works

[Narrative — one paragraph. What a guild is, single ownership, members & ranks, relation to homes/vault/war.]

## Creating a guild

[Step-by-step with examples.]

## Joining a guild

[Open vs invite-only, the `/g join` flow.]

## Leaving, transferring, and disbanding

[Owner constraints, transfer flow.]

## Browsing guilds

[`/g list` and `/g info` usage.]

## Gotchas

- You can only be in one guild at a time.
- Disbanding is irreversible.
- [More — check the actual GuildCommand.kt behavior for surprises worth surfacing.]

## Related

- [Ranks & Permissions](ranks.md)
- [Homes](homes.md)
- [Alliances & Diplomacy](alliances.md)
```

Source-of-truth check: cross-reference `GuildCommand.kt` lines 61 (`@Subcommand("create")`), 988 (`join`), 1269 (`leave`), 867 (`disband`), 1352 (`transfer`), 837 (`info`), 1103 (`list`) for exact behaviors and permissions.

- [ ] **Step 2: Lint, build, commit**

```bash
python tools/wiki/lint_frontmatter.py
mkdocs build --strict
git add wiki/docs/players/guilds.md
git commit -m "docs(wiki): write Players/Guilds page"
```

---

## Tasks 16–27: Write the remaining 12 Players feature pages

Each task follows the same shape as Task 15: a single Players feature page using the template, with content sourced from the corresponding `@Subcommand` blocks in `GuildCommand.kt` and the existing top-level `.md` files where relevant. One task per page. Each task's "Step 1: Write the page" provides the front-matter (already in the stub) plus a content checklist; "Step 2" is lint + build + commit.

For each, the content checklist:

1. Replace stub body with template-shaped content.
2. Pull commands/permissions from `GuildCommand.kt` lines listed below.
3. List "Common tasks" as H2s — each is a player-recognizable verb ("Setting a named home", "Restricting a home to a rank").
4. Include gotchas from real recent bug fixes (see CHANGELOG.md for material — e.g. "/g home from the nether used to fail silently, now uses async teleport").
5. "Related" links to sibling pages.
6. Lint + `mkdocs build --strict` must pass.
7. Commit with `docs(wiki): write Players/<topic> page`.

### Task 16: Homes page

- **File:** `wiki/docs/players/homes.md`
- **GuildCommand.kt refs:** lines 209 (sethome), 302 (home), 402 (homes), 440 (removehome), 470 (setallyhome), 499 (removeallyhome)
- **Common tasks (H2s):** Setting your default home, Adding a named home, Visiting a home, Removing a home, Setting up your ally-home, Restricting a home to specific ranks
- **Gotchas to include:** Nether ↔ overworld teleport now reliable (used to fail silently); confirm flow for replacing homes; safety check no longer blocks ladders/slabs/water; named homes persist across restart (fixed in v33)
- **Related:** alliances (ally-home access), ranks (rank-based access)

### Task 17: Ranks & Permissions page

- **File:** `wiki/docs/players/ranks.md`
- **GuildCommand.kt refs:** lines 518 (ranks)
- **Common tasks:** Opening the rank menu, Creating a rank, Setting permissions on a rank, Reordering rank priority, Promoting & demoting members
- **Gotchas:** priority hierarchy (mods can't promote above their own rank), offline kick respects priority, owner self-protection
- **Related:** guilds, homes (per-home rank access)

### Task 18: Alliances & Diplomacy page

- **File:** `wiki/docs/players/alliances.md`
- **GuildCommand.kt refs:** lines 1969 (ally), 2042 (enemy), 2110 (truce), 2178 (neutral)
- **Common tasks:** Requesting an alliance, Accepting an alliance, Declaring an enemy, Signing a truce, Going neutral, Letting allies into your ally-home (with whitelist)
- **Gotchas:** Pending alliances no longer display as accepted, both sides must run `/g ally` to confirm, ally-home requires both `USE_ALLY_HOMES` permission and an inbound whitelist entry
- **Related:** war, homes

### Task 19: War page

- **File:** `wiki/docs/players/war.md`
- **GuildCommand.kt refs:** lines 1535 (war)
- **Common tasks:** Declaring war, Ending a war, How war kills are tracked
- **Gotchas:** kills against guildmates don't grant XP (anti-farm), war affects relation display
- **Related:** alliances, progression

### Task 20: Chat page

- **File:** `wiki/docs/players/chat.md`
- **GuildCommand.kt refs:** lines 797 (chat), 817 (allychat)
- **Common tasks:** Toggling guild chat, Toggling ally chat, How tag formatting renders in chat, Where messages go when RoseChat is installed
- **Gotchas:** First message after toggle-off is no longer dropped; switching guild↔ally chat restores the previous channel correctly; MiniMessage gradients render via the `%lumaguilds_guild_tag%` placeholder
- **Related:** identity (tags)

### Task 21: Tags, Banners & Identity page

- **File:** `wiki/docs/players/identity.md`
- **GuildCommand.kt refs:** lines 1417 (tag), 1489 (description), 550 (emoji), 137 (rename)
- **Common tasks:** Setting a guild tag (CLI), Setting a guild tag (menu), Setting a description, Choosing an emoji, Renaming your guild, Setting a banner
- **Gotchas:** Tag CLI and menu now use the same validation (MiniMessage allowed, 32 visible chars); clear button in tag editor actually clears now; banner menu actually renders contents (was empty)
- **Related:** chat, guilds

### Task 22: Progression & Levels page

- **File:** `wiki/docs/players/progression.md`
- **GuildCommand.kt refs:** lines 837 (info shows progression)
- **Common tasks:** Earning XP (sources), What levels unlock, Checking your guild's level
- **Gotchas:** No XP from killing guildmates; XP earned shortly before quit/shutdown no longer lost; very large mining runs no longer lag the server (block XP batching); old guilds stuck at "Lvl 1" had their levels backfilled in the 2026-05-04 patch
- **Related:** war

### Task 23: Vault page

- **File:** `wiki/docs/players/vault.md`
- **GuildCommand.kt refs:** lines 1638 (getvault), 1751 (vault)
- **Common tasks:** Opening the vault, Getting a vault chest item, Sharing the vault across members
- **Gotchas:** Vault chest item cannot be used in crafting or as furnace fuel; permissions gate access
- **Related:** ranks

### Task 24: Mode page

- **File:** `wiki/docs/players/mode.md`
- **GuildCommand.kt refs:** lines 626 (mode)
- **Common tasks:** Switching to Peaceful, Switching to Hostile, What each mode does
- **Gotchas:** Mode menu options work after the recent fix; mode affects PvP and relation interactions
- **Related:** war, alliances

### Task 25: Shop integration page

- **File:** `wiki/docs/players/shop.md`
- **GuildCommand.kt refs:** lines 1817 (setshop)
- **Common tasks:** Marking a shop as guild-owned, How guild shops appear to other players
- **Gotchas:** Shop ownership transfers with the guild; only works with the supported shop plugin
- **Related:** guilds

### Task 26: LFG & Invites page

- **File:** `wiki/docs/players/lfg.md`
- **GuildCommand.kt refs:** lines 940 (invite), 1143 (lfg), 1153 (decline), 1177 (invites), 1201 (kick)
- **Common tasks:** Inviting a player, Listing your invites, Declining an invite, Toggling LFG, Kicking a member (online and offline)
- **Gotchas:** Offline kick respects rank priority; promote/demote shows real name even when target is offline
- **Related:** ranks, guilds

### Task 27: Bedrock differences page

- **File:** `wiki/docs/players/bedrock.md`
- **Content sources:** code paths in `BedrockGuildHomeMenu.kt`, `BedrockGuildRelationsMenu.kt`, `BedrockGuildMemberRankConfirmationMenu.kt`
- **Common tasks:** What looks different in menus, How clickable chat works on Bedrock, Known limitations
- **Gotchas:** Some menus are chat-based forms; commands work identically; teleports are dispatched on the main thread for safety
- **Related:** chat, homes, ranks

Each task ends with:

```bash
python tools/wiki/lint_frontmatter.py
mkdocs build --strict
git add wiki/docs/players/<topic>.md
git commit -m "docs(wiki): write Players/<topic> page"
```

---

## Task 28: Write Players — How do I…? index

**Files:**

- Modify: `wiki/docs/players/how-do-i.md`

This is the human-friendly entry point. A flat list of common player tasks, each linking to a specific anchor on a feature page.

- [ ] **Step 1: Replace the stub**

```markdown
---
title: How do I…?
audience: player
topic: how-do-i
summary: Index of common player tasks with deep links to the right feature page.
keywords: [index, how to, tasks, find]
related: [walkthrough]
updated: 2026-05-13
---

# How do I…?

Quick index. Scan for your task; click through to the page that answers it.

## Guild basics

- [Create a guild](guilds.md#creating-a-guild)
- [Join an existing guild](guilds.md#joining-a-guild)
- [Leave my guild](guilds.md#leaving-transferring-and-disbanding)
- [Transfer ownership](guilds.md#leaving-transferring-and-disbanding)
- [Disband my guild](guilds.md#leaving-transferring-and-disbanding)
- [Find a guild to join (LFG)](lfg.md#toggling-lfg)

## Homes

- [Set my guild's main home](homes.md#setting-your-default-home)
- [Add another named home](homes.md#adding-a-named-home)
- [Teleport to a specific home](homes.md#visiting-a-home)
- [Restrict a home to certain ranks](homes.md#restricting-a-home-to-specific-ranks)
- [Set up an ally-home](homes.md#setting-up-your-ally-home)

## Identity

- [Set a colored guild tag](identity.md#setting-a-guild-tag-cli)
- [Pick a guild banner](identity.md#setting-a-banner)
- [Change my guild description](identity.md#setting-a-description)
- [Rename my guild](identity.md#renaming-your-guild)

## People

- [Invite a player](lfg.md#inviting-a-player)
- [Kick a member](lfg.md#kicking-a-member-online-and-offline)
- [Promote / demote](ranks.md#promoting--demoting-members)
- [Create a new rank](ranks.md#creating-a-rank)
- [Reorder rank priority](ranks.md#reordering-rank-priority)

## Diplomacy & war

- [Request an alliance](alliances.md#requesting-an-alliance)
- [Accept an alliance](alliances.md#accepting-an-alliance)
- [Sign a truce](alliances.md#signing-a-truce)
- [Declare a war](war.md#declaring-war)
- [Mark a guild as enemy](alliances.md#declaring-an-enemy)

## Chat

- [Toggle guild chat](chat.md#toggling-guild-chat)
- [Toggle ally chat](chat.md#toggling-ally-chat)
- [Why aren't colors showing in my tag?](../getting-started/faq.md#my-guild-tag-isnt-showing-colors-in-chat)

## Other

- [Open the guild vault](vault.md#opening-the-vault)
- [Switch between Peaceful and Hostile mode](mode.md#switching-to-peaceful)
- [Check my guild level / XP](progression.md#checking-your-guilds-level)
- [Mark a shop as guild-owned](shop.md#marking-a-shop-as-guild-owned)
- [What's different on Bedrock?](bedrock.md)
```

- [ ] **Step 2: Verify all anchor links resolve**

```bash
mkdocs build --strict
```

Expected: passes. `--strict` will fail on any broken anchor (`#…`) link. If a link points to an H2 that doesn't exist yet, either fix the link (matching the actual H2 in the target page) or add the H2 to the target page.

- [ ] **Step 3: Commit**

```bash
git add wiki/docs/players/how-do-i.md
git commit -m "docs(wiki): write Players/How-do-I index"
```

---

## Task 29: Update `CONTRIBUTING.md` with the wiki/help sync rule

**Files:**

- Modify: `CONTRIBUTING.md`

- [ ] **Step 1: Append a section to `CONTRIBUTING.md`**

```markdown
## Keeping `/g help` and the wiki in sync

LumaGuilds enforces parity between the in-game help system and the player wiki. **Any PR that adds, removes, or renames a player-facing command must touch both `HelpTopics.kt` and the matching wiki page in the same PR.** CI rejects PRs that break this rule.

### What CI checks

1. **Front-matter lint** (`tools/wiki/lint_frontmatter.py`): every `wiki/docs/**/*.md` must have a valid front-matter block with `title`, `audience`, `topic`, `summary`, `keywords`, `related`, `updated`.
2. **Topic parity** (`tools/wiki/check_topic_parity.py`): every `slug = "..."` in [`HelpTopics.kt`](src/main/kotlin/net/lumalyte/lg/interaction/help/HelpTopics.kt) must have a matching `wiki/docs/players/<slug>.md` and vice versa.
3. **Strict MkDocs build** (`mkdocs build --strict`): no broken internal links, missing nav entries, or malformed markdown.

### Adding a new player-facing command

1. Decide which existing topic in `HelpTopics.kt` the command belongs to. If none fits, you're adding a new topic (rare).
2. Add a `HelpCommandEntry(syntax, blurb, prefill)` to the topic's `commands` list.
3. Update the matching `wiki/docs/players/<slug>.md`: add a row to the Quick Reference table, add or expand a "Common task" H2 if needed.
4. Run the checks locally before opening the PR:

```

python tools/wiki/lint_frontmatter.py
python tools/wiki/check_topic_parity.py
mkdocs build --strict
./gradlew test

```text

### Adding a new topic

1. Add a new `HelpTopic(...)` block to `HelpTopics.all` in `HelpTopics.kt`. Pick a stable lowercase-hyphenated slug — it becomes a URL forever.
2. Create `wiki/docs/players/<slug>.md` using the wiki page template (see other pages in that folder for the canonical shape).
3. Add the topic to `wiki/docs/players/.pages` so it appears in the nav.
4. Update `wiki/docs/players/how-do-i.md` with at least one entry deep-linking into the new page.
5. Run the checks above.

### Renaming or removing a topic

- **Removing:** delete the `HelpTopic` entry, delete the wiki page, and add a `mkdocs-redirects` entry to `mkdocs.yml` mapping the old URL to wherever the content moved (or to the topic index).
- **Renaming the slug:** treat as remove + add. The old slug is permanently retired (Hermes Agent and any cached links may still reference it via the redirect).

Slugs are forever. The display name, summary, and command list inside a topic are not — those evolve freely.
```

- [ ] **Step 2: Commit**

```bash
git add CONTRIBUTING.md
git commit -m "docs(contributing): add wiki/help sync rule and CI check overview"
```

---

## Task 30: Smoke-test the full Phase 1 deliverable

**Files:** none (verification only).

- [ ] **Step 1: Run the full local check sequence**

```bash
python tools/wiki/lint_frontmatter.py
python tools/wiki/check_topic_parity.py
mkdocs build --strict
./gradlew test
./gradlew build -x test
```

Expected: every step passes.

- [ ] **Step 2: Verify generated agent artifacts**

After `mkdocs build`, inspect:

```bash
ls site/llms.txt site/llms-full.txt
head -40 site/llms.txt
```

Expected: both files exist; `llms.txt` lists every page with summaries, organized by the section headers configured in `mkdocs.yml`.

- [ ] **Step 3: Local serve and click-through**

```bash
mkdocs serve
```

Open <http://127.0.0.1:8000/> in a browser. Click through:

- Home → Getting Started → Walkthrough (verify all section anchors).
- Players → How do I…? → click 3–4 random deep links, verify each lands on the correct anchor.
- Header nav: confirm Admins and Developers sections show their stubbed pages without errors.

- [ ] **Step 4: In-game smoke test (if a test server is available)**

Build the plugin:

```bash
./gradlew shadowJar
```

Drop the jar in a local Paper server. Test:

- `/g help` — topic menu renders with all 13 topics; clicking [Homes] runs `/g help homes`.
- `/g help homes` — topic page renders with all home commands; hovering shows blurb; clicking `/g sethome` prefills the chat box; clicking the wiki URL opens the browser; clicking `[Back to topics]` returns to the menu.
- `/g help unknown` — friendly error with a clickable `/g help` link.

- [ ] **Step 5: Open the Phase 1 PR**

Aggregate Tasks 1–29 into a single PR (or a small series). Include in the PR description:

- A reminder that **GitHub Pages must be enabled** in repo settings (Settings → Pages → Source: GitHub Actions) before the deploy is visible.
- A link to the design doc: [`docs/plans/2026-05-13-player-wiki-and-help-redesign-design.md`](docs/plans/2026-05-13-player-wiki-and-help-redesign-design.md).
- A note that Phases 2 (Admins content) and 3 (Developers migration) follow as separate plans.

---

## Self-Review Notes

- **Spec coverage:** every spec section is mapped to tasks. §2 stack → Task 1; §3 site structure → Task 4; §4 page format → Tasks 6–8 + 15–28 (template applied consistently); §5 `/g help` → Tasks 9–14; §6 migration → deferred to Phase 3 (out of scope, called out); §7 phased rollout → this plan IS Phase 1; §8 maintenance → Tasks 3, 5, 11, 29.
- **Source-of-truth invariant:** topic slug appears identically in `HelpTopics.kt` (Task 10), `wiki/docs/players/<slug>.md` (Task 4 stubs, Tasks 15–27 content), wiki URL via MkDocs page slug, and CI parity check (Task 11).
- **No placeholders in code tasks:** every Kotlin task has complete source. The wiki content tasks (15–28) provide a template and a content checklist rather than full prose — that's intentional because per-page prose is human-authored and would bloat the plan to unreviewability; the template + checklist + source-line refs is what a skilled writer needs.
- **Anti-pattern guard:** the `<!-- placeholder -->` block in Task 6's Welcome page is a deliberate one-line marker for human follow-up (the server pitch), not a TODO that blocks execution.
