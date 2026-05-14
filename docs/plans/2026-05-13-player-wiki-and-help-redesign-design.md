# LumaGuilds Player Wiki + `/g help` Redesign — Design

**Date:** 2026-05-13
**Status:** Draft for review
**Author:** Brainstorm session (Noah + Claude)

---

## 1. Goals & Non-Goals

### Goals

- Reduce player confusion about how the plugin works. Establish a single canonical place for "how does X work" answers, accessible both in-game (`/g help`) and in-browser (the wiki).
- Make `/g help` actually useful: topic-organized, clickable, mirrors the wiki's structure 1:1.
- Make wiki content retrievable by Hermes Agent with high precision (audience-tagged, well-chunked, predictable structure).
- Consolidate the scattered top-level `.md` files and the existing `docs/` folder into one source of truth.
- Phased delivery: Players + `/g help` first, Admins later, Devs last.

### Non-Goals

- Versioned docs. The wiki tracks `main`. Revisit if a multi-version need emerges.
- Self-hosting on the VPS. GitHub Pages only.
- A GUI inventory-menu help system. Clickable chat covers Java, Bedrock/Geyser, and console without an extra UI surface to maintain.
- Rewriting existing dev docs from scratch. We migrate; we do not redo accurate content.
- Tracking plugin XP/curve tuning in docs. Changes per-update, ages badly.
- Permission-gated `/g help` filtering. Confusion comes from not knowing things exist, not from seeing one a player can't use.
- In-game help search. The wiki has search.
- Localization. Defer.

---

## 2. Stack & Hosting

- **Generator:** MkDocs + Material theme.
- **Hosting:** GitHub Pages, deployed from `main` via GitHub Actions.
- **Source location:** `wiki/` at repo root, separate from the existing `docs/` during migration; `docs/` then folds in (see §6).
  - Config: `mkdocs.yml` at repo root.
  - Content: `wiki/docs/`.
  - Assets: `wiki/docs/assets/<topic>/`.
- **Public URL:** `https://badgersmc.github.io/LumaGuilds/` (custom domain optional, deferred).
- **Key plugins:**
  - `material` theme (search, nav, dark mode).
  - `mkdocs-material` (search, nav, dark mode — social cards deferred).
  - `mkdocs-llmstxt` (auto-generates `/llms.txt` and `/llms-full.txt` for Hermes).
  - `mkdocs-awesome-pages-plugin` (per-folder `.pages` ordering files; keeps `mkdocs.yml` clean).
  - `mkdocs-redirects` (rename pages without breaking external links or Hermes-cached URLs).
- **Build trigger:** GitHub Action on push to `main` touching `wiki/**` or `mkdocs.yml`. Builds, publishes to `gh-pages` branch.

---

## 3. Site Structure

Top-level nav: **Home / Getting Started / Players / Admins / Developers.**

```text
Home — landing page: "What is LumaGuilds?" + links to the 4 sections.

Getting Started
  ├── Welcome to EnthusiaSMP
  ├── Your first 30 minutes (walkthrough)
  │     ├── 1. Spawn and orientation
  │     ├── 2. Joining or creating a guild
  │     ├── 3. Setting your tag and home
  │     ├── 4. Inviting a friend
  │     ├── 5. Chat basics (/g chat)
  │     ├── 6. What XP and levels do
  │     └── 7. Where to go next
  └── FAQ

Players
  ├── How do I…?           (index page — deep-links into feature pages)
  ├── Guilds               (create, join, leave, disband, transfer)
  ├── Ranks & Permissions
  ├── Homes                (set, name, access control)
  ├── Alliances & Diplomacy (ally, truce, enemy, neutral, ally homes)
  ├── War
  ├── Chat                 (/g chat, /g allychat, tags)
  ├── Tags, Banners & Identity
  ├── Progression & Levels
  ├── Vault
  ├── Mode                 (Peaceful / Hostile)
  ├── Shop integration
  ├── LFG & Invites
  └── Bedrock differences

Admins
  ├── Installation & config.yml
  ├── Permission nodes reference
  ├── /lumaguilds override and recovery
  ├── Troubleshooting
  ├── PlaceholderAPI placeholders
  ├── RoseChat integration
  ├── Geyser/Floodgate behavior
  ├── Lunar Client integration
  ├── Web leaderboard API
  └── Claims integration

Developers
  ├── Getting started (dev)
  ├── Architecture overview
  ├── Domain layer
  ├── Application layer
  ├── Infrastructure layer
  ├── Interaction layer
  ├── Master diagram
  ├── API reference
  ├── Placeholders (internal)
  ├── Emoji permissions
  ├── Migration safety
  ├── Schema setup
  └── Archive (old plans, refactoring docs — not in nav)
```

Notes:

- "How do I…?" is an index page; every entry deep-links to a feature page anchor.
- "Bedrock differences" exists because Geyser/Floodgate behavior diverges in a few places and players hit those walls.
- Developers section is largely migration of existing `docs/` content.

---

## 4. Page Format & Metadata

Every page follows this structure so Hermes gets predictable chunks.

### Front-matter (YAML)

```yaml
---
title: Homes
audience: player          # player | admin | dev
topic: homes              # stable slug — must match HelpTopics.kt key
summary: How to set, name, and control access to guild homes.
keywords: [homes, sethome, removehome, ally home, teleport]
related: [alliances, ranks]
updated: 2026-05-13
---
```

### Body convention

1. **One-sentence summary** — repeats `summary` (gives Hermes the lede in the chunk body too).
2. **Quick reference** — a small command/permission table at the top so scanners can leave fast.
3. **How it works** — narrative, the canonical explanation.
4. **Common tasks** — H2 per task; each H2 is a self-contained chunk (e.g. `## Setting a named home`).
5. **Gotchas / known issues** — bullets.
6. **Related** — links to sibling pages.

### Heading discipline

- One H1 per page (the title).
- H2s are stable anchors. Never rename casually — Hermes and `/g help` links depend on them.
- Avoid burying critical info below H3 — agents chunk best at H2 boundaries.

### Asset conventions

- Screenshots in `wiki/docs/assets/<topic>/`, named `<page-slug>-<scene>.png`.
- Code/command blocks always fenced with a language tag.

### Generated artifacts at build time

- `/llms.txt` — one-line-per-page index (title + url + summary), audience-filterable.
- `/llms-full.txt` — concatenated markdown of every page, audience tags preserved in headers.

---

## 5. `/g help` Redesign

### Entry point: `/g help`

Shows a topic menu, not a flat command list.

```text
─── LumaGuilds Help ───
Pick a topic:
  [Guilds]        Create, join, leave, disband
  [Homes]         Set and visit guild homes
  [Ranks]         Permissions and rank management
  [Chat]          /g chat, ally chat, tags
  [Alliances]     Ally, truce, enemy, neutral
  [War]           Declaring and fighting wars
  [Progression]   XP, levels, perks
  [Vault]         Guild storage
  [Identity]      Tags, banners, descriptions
  [Mode]          Peaceful / Hostile
  [Other]         LFG, info, history, leaderboards

Type /g help <topic>  or click a topic above.
Full wiki: https://badgersmc.github.io/LumaGuilds/
```

### Topic page: `/g help homes`

Clickable, one screen.

```text
─── Help · Homes ───
Guild homes are teleport points your guild can share. Each guild can
have multiple named homes. Access can be restricted per-rank.

Commands:
  /g sethome [name]            [click to prefill]  [?]
  /g home [name]               [click to prefill]  [?]
  /g homes                     [click to run]
  /g removehome <name>         [click to prefill]  [?]
  /g setallyhome               [click to prefill]  [?]

Hover a [?] for details. Click a command to drop it into chat.

Read more: https://badgersmc.github.io/LumaGuilds/players/homes/
                  Page 1/1  [Back to topics]
```

### Mechanics

- Built with Adventure `Component` API: each command line is a `Component` with `ClickEvent.suggestCommand` (prefill) or `ClickEvent.runCommand`, plus `HoverEvent.showText` for the per-command blurb.
- Works in Java client, Geyser/Bedrock (Geyser maps click events to taps), and console (clicks degrade to plain text — commands still readable).
- The wiki URL on every topic page maps to the matching wiki page by stable topic slug — same slug as the front-matter `topic:` field.

### Source of truth

- A single `HelpTopics.kt` (or YAML resource under `src/main/resources/`) holds: topic slug, display name, one-sentence summary, ordered list of `(command, syntax, blurb)` tuples.
- `/g help` and `/g help <topic>` both render from that one data structure.
- The same data file is consumed by a CI check that fails the build if a topic slug doesn't have a matching wiki page (see §8).

### Out of scope for the redesign

- Permission-gated filtering.
- In-game search.
- Localization.

---

## 6. Migration of Existing Docs

### Folds-in mapping

| Existing file | New home |
|---|---|
| `docs/getting-started.md` | Developers/Getting started (dev) |
| `docs/architecture.md` | Developers/Architecture overview |
| `docs/domain.md` | Developers/Domain layer |
| `docs/application.md` | Developers/Application layer |
| `docs/infrastructure.md` | Developers/Infrastructure layer |
| `docs/interaction.md` | Developers/Interaction layer |
| `docs/master-diagram.md` | Developers/Master diagram |
| `docs/api-reference.md` | Developers/API reference |
| `docs/placeholders.md` | Developers/Placeholders (internal) — *and* split player-facing tokens into Admins/PlaceholderAPI |
| `docs/EMOJI_PERMISSIONS.md` | Developers/Emoji permissions |
| `docs/MIGRATION_SAFETY.md` | Developers/Migration safety |
| `docs/README.md` | Delete (Home page supersedes) |
| `docs/images/` | `wiki/docs/assets/dev/` |
| `docs/plans/*` | `wiki/docs/developers/archive/` (not in nav, retained for history) |
| `CHANGELOG.md` | Stays at repo root (release tooling depends on it) |
| `CHANGELOG_BETA.md` | Stays at repo root |
| `COMMANDS.md` | Delete — replaced by `HelpTopics.kt` source + auto-generated Admins/Command reference |
| `PERMISSIONS.md` | Admins/Permission nodes reference |
| `PROGRESSION_DESIGN.md` | Developers/Progression (design notes section) |
| `PROGRESSION_IMPLEMENTATION_SUMMARY.md` | Merge into above, then delete |
| `PLACEHOLDERAPI_README.md` | Admins/PlaceholderAPI placeholders |
| `SCHEMA_SETUP.md` | Developers/Schema setup |
| `REFACTORING_PLAN.md` | Developers/Archive (historical) |
| `LUNAR_CLIENT_INTEGRATION_PLAN.md` | Admins/Lunar Client integration (rewrite as docs, not a plan) |
| `README.md` | Stays at repo root; trimmed to: what + install + link to wiki |
| `CONTRIBUTING.md` | Stays at repo root |

### Migration rules

1. Add front-matter to every migrated file.
2. Fix headings to follow §4 discipline (one H1, stable H2 anchors).
3. Update internal links to new paths; `mkdocs-redirects` handles externally-cached URLs.
4. Worktree copies under `.claude/worktrees/` are ignored — only canonical paths migrate.
5. Don't rewrite content during migration. Move first; edit in a later pass if needed.

---

## 7. Phased Rollout

### Phase 1 — Foundation + Players + `/g help`

1. Scaffold MkDocs at `wiki/`, `mkdocs.yml`, GitHub Action publishing to `gh-pages`.
2. Home page + nav skeleton (all 4 sections present; non-Player pages stubbed with front-matter only).
3. Getting Started — full content: Welcome, the 7-step walkthrough, FAQ.
4. Players — full content for all feature pages + How-do-I index.
5. `HelpTopics.kt` (or YAML resource) data source + refactor `onHelp` in `GuildCommand.kt` to render topic menu + topic pages from it.
6. Adventure clickable/hover components wired into `/g help`.
7. CI: topic-slug ↔ wiki-page parity check.
8. `llms.txt`/`llms-full.txt` generation verified on the deployed site.

**Exit criteria:** site live, `/g help` redesigned, Hermes can ingest. Players section complete enough to direct confused players today.

### Phase 2 — Admins

1. Installation + `config.yml` walkthrough.
2. Permission nodes reference (port from `PERMISSIONS.md`, expand).
3. `/lumaguilds override` + recovery flows.
4. Troubleshooting page (corrupted banners, stuck guilds, owner-less guilds — pulled from recent bug-fix history).
5. PlaceholderAPI placeholders (port from `PLACEHOLDERAPI_README.md`).
6. RoseChat integration.
7. Geyser/Floodgate behavior.
8. Lunar Client integration (rewrite from plan doc as user-facing docs).
9. Web leaderboard API.
10. Claims integration.

**Exit criteria:** server operators can install and integrate without DMing the dev.

### Phase 3 — Developers (migration)

1. Move `docs/*` into `wiki/docs/developers/` per the §6 table.
2. Add front-matter to each.
3. Fix headings + internal links.
4. Fold in scattered top-level `.md` files per the §6 table.
5. Configure `mkdocs-redirects` for any URLs that shipped externally.
6. Delete originals.
7. Archive `docs/plans/` and `REFACTORING_PLAN.md` (not in nav).
8. Trim repo-root `README.md` to install + wiki link.

**Exit criteria:** one source of truth; no duplicate `.md` files at repo root beyond `README.md`, `CHANGELOG.md`, `CHANGELOG_BETA.md`, `CONTRIBUTING.md`.

### Sequencing notes

- Each phase is a separate PR series. Phase 1 ships before Phase 2 starts.
- Phase 1 has the most work and the most player impact.
- Phase 3 is mostly mechanical; could be one focused day.

---

## 8. Maintenance — Keeping Wiki, `/g help`, and Code in Sync

This is the section that decides whether the wiki rots.

### Single source of truth: topic slugs

Every player-facing feature has one `topic` slug. That slug appears in:

- The wiki page's front-matter `topic:` field.
- The wiki URL segment: `/players/<topic>/`.
- The key in `HelpTopics.kt`.
- The argument to `/g help <topic>`.

If any of those four drift, CI fails.

### CI checks (run on every PR)

1. **Topic parity check.** Script reads `HelpTopics.kt`, asserts every topic slug has a matching `wiki/docs/players/<slug>.md` and vice versa. Fails the build on mismatch.
2. **Front-matter lint.** Every wiki page must have `title`, `audience`, `topic`, `summary`. Missing fields fail the build.
3. **Internal link check.** `mkdocs build --strict` already does this; fails on broken cross-links.
4. **Wiki URL check.** `HelpTopics.kt` references a base wiki URL; CI does a HEAD request on each topic URL post-deploy and warns on 404s.

### Developer workflow (the rule)

> **Any PR that adds, removes, or renames a command must touch both `HelpTopics.kt` and the matching wiki page in the same PR.** CI enforces it.

This lives in `CONTRIBUTING.md`. The CI failure message points at that section.

### Changelog → wiki feedback loop

- When cutting a changelog, cross-check it against the wiki. New feature → does it have a wiki page or section? Behavior change → does the existing page still match?
- Not automated. A step in the changelog process: "scan changelog entries, update wiki where needed." Added to the changelog template.

### Hermes index refresh

- `llms.txt` and `llms-full.txt` regenerate on every build automatically. No manual step.
- Hermes-side: whatever pull/scrape cadence Hermes uses picks up the new index on its next cycle. If Hermes needs a webhook later, the GitHub Pages deploy can fire one — deferred until Hermes integration is concrete.

### Explicit anti-patterns

- **Auto-generating wiki pages from code annotations.** Generated docs read like generated docs. Humans write the prose; CI keeps the structure honest.
- **Version-locking the wiki to plugin releases.** Wiki tracks `main`. If a release briefly ships behavior that doesn't match `main` yet, the wiki is briefly wrong — acceptable.

---

## 9. Open Questions

- Custom domain for the wiki (e.g. `wiki.enthusiasmp.com` redirecting to GitHub Pages). Deferred; default `badgersmc.github.io/LumaGuilds/` until decided.
- Exact Hermes ingestion path (HTTP scrape of public site vs. git pull from VPS). Both supported by §4 design; pick when Hermes integration is built.
- Webhook on `gh-pages` deploy → Hermes refresh. Deferred until Hermes integration is real.
- Screenshot capture pass — separate effort, slot into Phase 1 if time allows; otherwise text-first launch and add screenshots iteratively.

---

## 10. Success Criteria

- A confused player can be answered with a single link (or `/g help <topic>`) in chat instead of a paragraph of explanation.
- A new player completing the "first 30 minutes" walkthrough ends up in a working guild with a tag, a home, chat toggled correctly, and knows where to look next.
- A server admin installing the plugin can configure permissions, PAPI, and RoseChat without contacting the dev.
- Hermes Agent returns audience-correct chunks for guild-feature questions, with stable URLs to cite.
- Six months from now, the wiki still matches the code — because CI won't let it drift.
