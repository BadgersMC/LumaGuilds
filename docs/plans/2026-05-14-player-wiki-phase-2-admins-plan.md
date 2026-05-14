# Player Wiki — Phase 2 (Admins) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill in every Admins-section stub page on the LumaGuilds wiki with operator-facing content sourced from the existing top-level `.md` files, the plugin's `config.yml`, and recent bug-fix history.

**Architecture:** Phase 1's scaffold (MkDocs Material, GitHub Pages deploy, front-matter lint, topic-parity check) is already merged on `main`. Phase 2 only edits existing stub pages under `wiki/docs/admins/` — no new infrastructure, no new code. Each page follows the same template as the Phase 1 player pages.

**Tech Stack:** MkDocs Material (already configured), Python 3.11 with PyYAML (already used by lint), no Kotlin changes.

**Design source:** [`docs/plans/2026-05-13-player-wiki-and-help-redesign-design.md`](2026-05-13-player-wiki-and-help-redesign-design.md) §3 (Site Structure: Admins) and §6 (Migration of Existing Docs).

**Phase 1 plan (for shape reference):** [`docs/plans/2026-05-13-player-wiki-and-help-redesign-plan.md`](2026-05-13-player-wiki-and-help-redesign-plan.md)

**Out of scope (deferred to Phase 3):** migrating the Developers section, removing the now-redundant top-level `.md` files from the repo root. Those happen in Phase 3.

---

## File Structure

**Modified files (existing stubs — body rewritten to full content):**
- `wiki/docs/admins/installation.md`
- `wiki/docs/admins/permissions.md`
- `wiki/docs/admins/override.md`
- `wiki/docs/admins/troubleshooting.md`
- `wiki/docs/admins/placeholderapi.md`
- `wiki/docs/admins/rosechat.md`
- `wiki/docs/admins/geyser.md`
- `wiki/docs/admins/lunar.md`
- `wiki/docs/admins/leaderboard-api.md`
- `wiki/docs/admins/claims.md`

**Read-only content sources (still living at repo root until Phase 3):**
- `PERMISSIONS.md`
- `PLACEHOLDERAPI_README.md`
- `PROGRESSION_DESIGN.md` / `PROGRESSION_IMPLEMENTATION_SUMMARY.md`
- `LUNAR_CLIENT_INTEGRATION_PLAN.md`
- `SCHEMA_SETUP.md`
- `src/main/resources/config.yml`
- `src/main/resources/plugin.yml`
- Recent commits + `CHANGELOG.md` for troubleshooting material

---

## Conventions (same as Phase 1)

**Page template:**

```markdown
---
title: <Title>
audience: admin
topic: <slug>
summary: <≤140 chars>
keywords: [...]
related: [<sibling-slugs>]
updated: 2026-05-14
---

# <Title>

<One-sentence summary, same prose as `summary:`.>

## Quick reference

<Table OR a short list, whichever fits the topic.>

## How it works

<2–4 sentence canonical explanation.>

## <Common task / section 1>

<Steps / config snippets.>

## <Common task / section 2>

...

## Gotchas

- ...

## Related

- [Sibling page](other.md)
```

**Voice:** Admins, not players. Drop the "ask staff" tone. Assume the reader runs a Paper server, knows what a YAML file is, and can read log lines. Concrete config snippets are good; long prose is bad.

**Commits:** one commit per page (small) or one per task batch (still small). Either is fine — match Phase 1 cadence.

**Lint/build before every commit:**
```
cd D:/BadgersMC-Dev/LumaGuilds/.claude/worktrees/wiki-phase2
python tools/wiki/lint_frontmatter.py
python tools/wiki/check_topic_parity.py
python -m mkdocs build --strict
```

---

## Task 1: Gather and inventory content sources

This is a **read-only orientation task**. You produce no commits — just a short summary that informs all later tasks.

- [ ] **Step 1: Read each top-level content source**

Open and skim each of these files. Note for each: what it actually contains, what's outdated, what's load-bearing for which Admin page.

- `PERMISSIONS.md`
- `PLACEHOLDERAPI_README.md`
- `PROGRESSION_DESIGN.md`
- `PROGRESSION_IMPLEMENTATION_SUMMARY.md`
- `LUNAR_CLIENT_INTEGRATION_PLAN.md`
- `SCHEMA_SETUP.md`
- `src/main/resources/config.yml`
- `src/main/resources/plugin.yml`
- `CHANGELOG.md` (most recent 2-3 entries are enough)

- [ ] **Step 2: Cross-reference with the plugin.yml permissions block**

The canonical list of permissions lives in `src/main/resources/plugin.yml`. If `PERMISSIONS.md` lists permissions that don't exist in plugin.yml, those go in a "removed/deprecated" note on the Admins/Permissions page. If plugin.yml has permissions PERMISSIONS.md doesn't document, those need new entries.

- [ ] **Step 3: Output a short inventory note in your reply**

A 5–10 line summary, one bullet per Admin page, of which content sources feed it. This is just for your own use across later tasks — no file commit.

---

## Task 2: Installation & config.yml

**File:** `wiki/docs/admins/installation.md`

**Front-matter values:**
- title: `Installation & config.yml`
- audience: `admin`
- topic: `installation`
- summary: `Install LumaGuilds on Paper 1.21.x, drop in dependencies, and walk through every config.yml block.`
- keywords: `[installation, install, setup, config, config.yml, paper, dependencies]`
- related: `[permissions, claims, rosechat]`

**Content checklist:**

- **One-line summary** repeats the front-matter `summary:` field.
- **Quick reference table:** required dependencies (Paper version, Vault, PlaceholderAPI), optional dependencies (RoseChat, Geyser/Floodgate, Lunar SDK, claims plugin) — with one-line "needed for X" descriptions.
- **How it works:** LumaGuilds drops into `plugins/`, generates a default `config.yml` on first start, and starts SQLite-backed. To upgrade or change storage, edit config.yml and restart.
- **Install steps:**
  1. Verify Paper 1.21.x (the plugin.yml api-version is the authority — check `src/main/resources/plugin.yml`).
  2. Drop the jar into `plugins/`. Start the server.
  3. Confirm the plugin loaded by checking the log and running `/version LumaGuilds`.
  4. Open the generated `plugins/LumaGuilds/config.yml`.
- **config.yml walkthrough:** Read `src/main/resources/config.yml` and document every top-level block. Include a short code block per block showing the actual default values and a sentence on what it controls. Don't paraphrase keys you don't see in the file — copy them verbatim. The blocks typically present: storage / database, progression rates, claims integration toggle, RoseChat hook toggle, Lunar Client integration toggle, leaderboard web API, mode defaults, perks/levels table. Confirm against the live file before writing.
- **Optional dependencies:** what each one unlocks. For each, link to the matching Admin page (rosechat.md, geyser.md, lunar.md, claims.md, leaderboard-api.md).
- **Gotchas:** First-start creates the schema — DON'T pre-create the database file manually. Reloading the plugin (PlugMan, /reload) is unsupported — restart instead. Mixing claims-enabled and claims-disabled servers from the same database is undefined behavior.
- **Related:** permissions, claims, rosechat.

- [ ] **Step 1:** Read `src/main/resources/config.yml` and `src/main/resources/plugin.yml`.
- [ ] **Step 2:** Read the existing stub at `wiki/docs/admins/installation.md`, then Write the full content following the checklist above.
- [ ] **Step 3:** Lint + build:
  ```
  python tools/wiki/lint_frontmatter.py
  python -m mkdocs build --strict
  ```
- [ ] **Step 4:** Commit: `docs(wiki): write Admins/Installation page`.

---

## Task 3: Permission nodes reference

**File:** `wiki/docs/admins/permissions.md`

**Front-matter:**
- title: `Permission nodes reference`
- topic: `permissions`
- summary: `Every permission node LumaGuilds defines, what it does, and its default state.`
- keywords: `[permissions, permission nodes, lumaguilds, vault, luckperms]`
- related: `[installation, override]`

**Content checklist:**

- **Summary** repeats front-matter.
- **How it works:** LumaGuilds permissions follow the `lumaguilds.guild.<action>` pattern for player commands and `lumaguilds.admin.<action>` for admin commands. Defaults come from plugin.yml. To grant or revoke, use your permission plugin (LuckPerms typically). Some permissions are gated additionally by *rank* (in-game guild-rank permissions) — that's a separate system; see [Ranks & Permissions](../players/ranks.md).
- **Player-command permissions:** one row per node. Source: `src/main/resources/plugin.yml` and the existing `PERMISSIONS.md` file. Format as a single table with columns: `Permission`, `Default` (op / true / false), `Grants`.
- **Admin-command permissions:** same table format. Includes `lumaguilds.admin.*` (the override toggle, `/lumaguilds override`, `/lg disband`, `/vaultremove`, `/vaultrollback`, etc.).
- **Rank permissions vs node permissions:** short paragraph distinguishing the two systems. Rank permissions (`USE_VAULT`, `MANAGE_RANKS`, `USE_ALLY_HOMES`, etc.) live in the guild itself and are managed via `/g menu` → Ranks. Node permissions are LuckPerms-style and gate the command itself.
- **Recommended permission setups:** small section with two example LuckPerms snippets — "default player" (basic /g commands) and "staff" (everything + admin override).
- **Gotchas:** If `lumaguilds.guild.create` is denied for a player, they can't make a guild even if they have other guild permissions. The override permission grants extensive reach — restrict it to trusted staff only. Removing a node permission doesn't kick anyone — they just can't run that command.
- **Related:** installation, override.

- [ ] **Step 1:** Read `src/main/resources/plugin.yml` and `PERMISSIONS.md` and the existing stub.
- [ ] **Step 2:** Reconcile any drift between plugin.yml and PERMISSIONS.md — plugin.yml is the source of truth; note any documented-but-missing or missing-but-documented permissions in a small "discrepancies" callout in the page itself (or as a footnote).
- [ ] **Step 3:** Write the page.
- [ ] **Step 4:** Lint + build + commit.

---

## Task 4: `/lumaguilds override` and recovery

**File:** `wiki/docs/admins/override.md`

**Front-matter:**
- title: `/lumaguilds override and recovery`
- topic: `override`
- summary: `Use admin override to unstick broken guilds — owner-less, locked-out, or corrupted state.`
- keywords: `[override, recovery, admin, lumaguilds, broken guild, owner-less]`
- related: `[permissions, troubleshooting]`

**Content checklist:**

- **How it works:** `/lumaguilds override` toggles a per-session admin override flag. While ON, the admin bypasses: claims permission checks (if claims integration is on), guild join requirements (closed guilds), guild-menu access, and rank-priority gates on member management. The flag is per-player and per-session — relogging clears it. Source: recent PR #17 in CHANGELOG.md and commits `b51587d` / `a8afe0d`.
- **Quick reference table:** the relevant commands and what override unlocks for each (`/g join` → bypasses invitation requirement; `/g menu` → bypasses management permission; rank changes → bypasses priority hierarchy; etc.).
- **Common task: Toggling override on.** `/lumaguilds override`. Run again to toggle off.
- **Common task: Recovering an owner-less guild.** Step-by-step: toggle override on → `/g join <guild>` → `/g menu` → Ranks → assign yourself the owner rank → toggle override off. Note that this is the only built-in path to restore an orphaned guild; database edits are not needed.
- **Common task: Kicking a stuck member.** Override → `/g menu` → Members → kick. Bypasses rank priority.
- **Common task: Auditing override use.** Override events are logged. Point to the relevant log location (the plugin writes to the server log; if there's a separate audit channel, document it — verify against the code).
- **Gotchas:** Override does NOT bypass the "single-owner" invariant on `/g transfer` — owner-rank changes still go through the proper flow. Override is per-session — restarting the server clears it. The override permission (`lumaguilds.admin.override`, or whatever plugin.yml declares — verify) is required just to toggle it.
- **Related:** permissions, troubleshooting.

- [ ] **Step 1:** Read the existing stub + relevant code in `src/main/kotlin/net/lumalyte/lg/interaction/commands/LumaGuildsCommand.kt` and `src/main/kotlin/net/lumalyte/lg/application/services/AdminOverrideService.kt` (or whichever file exposes the override toggle — use Grep for "AdminOverride" to find it).
- [ ] **Step 2:** Write the page.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 5: Troubleshooting

**File:** `wiki/docs/admins/troubleshooting.md`

**Front-matter:**
- title: `Troubleshooting`
- topic: `troubleshooting`
- summary: `Operator-facing fixes for common LumaGuilds issues — stuck guilds, broken homes, schema drift, chat leaks.`
- keywords: `[troubleshooting, issues, fixes, recovery, stuck, broken]`
- related: `[override, installation, rosechat]`

**Content checklist:**

- **How it works:** LumaGuilds is mostly self-healing — schemas backfill, caches refresh — but some operator paths exist for the edge cases. This page lists known scenarios and the fix.
- **Each scenario is its own H2.** Each scenario has: (1) symptom (what the player or admin reports), (2) cause (one sentence — the actual code path), (3) fix (steps).
- **Required scenarios to cover** (pull from `CHANGELOG.md` and the last 30 commits — every bug fix worth surfacing as a troubleshooting entry):
  - Owner-less guild (cause: previous owner left without transferring; fix: admin override + re-assign owner).
  - Guild stuck at "Level 1" despite high XP (cause: pre-May-2026 bug; fix: should self-heal on startup via `ProgressionService.syncGuildLevels()` — check version, restart if needed).
  - Player can't teleport to home from Nether (cause: was a real bug, fixed via `teleportAsync`; if still happening, suspect another teleport-blocker plugin or anticheat — check logs for cancelled teleports).
  - Guild chat leaking to global with RoseChat installed (cause: RoseChat channel hook misconfigured; fix: see [RoseChat integration](rosechat.md)).
  - Guild banner appears blank in Diplomatic Relations menu (cause: corrupted banner data in the database; fix: a placeholder banner now renders automatically — no operator action needed; underlying row can be cleaned if desired with `/lg disband` or direct DB).
  - First message after `/g chat` toggle drops on Java (cause: was a stale-marker bug, fixed in 2026 — if still happening, check version).
  - Schema migration warnings in startup log (cause: in-place migration in progress; fix: usually self-resolves; back up before upgrading major versions).
  - Vault chest exploits (placing in crafting, burning as fuel) (cause: was real, fixed; if a player reports a way to dupe via vault chests, treat as a new bug to file).
- **General debugging tips:** where the plugin logs to, how to set DEBUG level, how to inspect the SQLite database directly (read-only) for state.
- **When to escalate to GitHub Issues:** any reproducible bug, with reproduction steps. Link to the issues page.
- **Related:** override, installation, rosechat.

- [ ] **Step 1:** Read the most recent 20 commits via `git log --oneline -20` and the existing top-level `CHANGELOG.md`. Pick out the scenarios listed above and any others that look operator-relevant.
- [ ] **Step 2:** Write the page.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 6: PlaceholderAPI placeholders

**File:** `wiki/docs/admins/placeholderapi.md`

**Front-matter:**
- title: `PlaceholderAPI placeholders`
- topic: `placeholderapi`
- summary: `Every %lumaguilds_…% PAPI placeholder, what it returns, and where to use it.`
- keywords: `[placeholderapi, papi, placeholders, tab, chat]`
- related: `[installation, rosechat]`

**Content checklist:**

- **How it works:** LumaGuilds registers a PlaceholderAPI expansion exposing player-context and guild-context placeholders. The expansion identifier is `lumaguilds`. All placeholders return MiniMessage *and* legacy-`§` forms where applicable (some have explicit `_raw` and `_plain` variants).
- **Quick reference:** a single table listing every placeholder, what it returns, and the value type. Source the list from `PLACEHOLDERAPI_README.md` AND verify against `src/main/kotlin/net/lumalyte/lg/infrastructure/placeholders/LumaGuildsExpansion.kt`. If they disagree, the code is the source of truth.
- **Tag-rendering placeholders:** dedicated subsection covering `%lumaguilds_guild_tag%` (legacy-rendered, default), `%lumaguilds_guild_tag_raw%` (MiniMessage), `%lumaguilds_guild_tag_plain%` (stripped). Same for `guild_display` and `guild_chat_format` if those have variants.
- **Leaderboard placeholders:** `%lumaguilds_top_level_<N>%`, `%lumaguilds_top_xp_<N>%`, etc. — document the pattern and the maximum N supported (verify).
- **Relation indicator placeholder:** the icon next to player names showing ally/enemy/truce. Note the pending-vs-active fix (PR #26).
- **Where to use them:** tab list (placeholders extension), chat format (RoseChat or other chat plugins), holographic plugins, etc. Short examples for each.
- **Gotchas:** Placeholders that return MiniMessage will leak raw tags in chat plugins that don't parse MiniMessage — use the legacy variant in those contexts. Relation indicators only show for ACTIVE relations (fixed in 2026 — used to show for PENDING).
- **Related:** installation, rosechat.

- [ ] **Step 1:** Read `PLACEHOLDERAPI_README.md` and the placeholder expansion source.
- [ ] **Step 2:** Reconcile drift between the doc and the code — code wins.
- [ ] **Step 3:** Write the page.
- [ ] **Step 4:** Lint + build + commit.

---

## Task 7: RoseChat integration

**File:** `wiki/docs/admins/rosechat.md`

**Front-matter:**
- title: `RoseChat integration`
- topic: `rosechat`
- summary: `Hook guild chat into RoseChat as a managed channel — formatting, recipients, and toggle behavior.`
- keywords: `[rosechat, chat, channel, integration]`
- related: `[installation, placeholderapi, troubleshooting]`

**Content checklist:**

- **How it works:** When RoseChat is installed, LumaGuilds hands guild chat off to RoseChat via a channel hook. RoseChat owns formatting, delivery, and recipient resolution. The player-visible `/g chat` toggle switches the player's active RoseChat channel between their normal channel and the guild channel. Source: commit `046f8bf` and PR #11.
- **Why we hand off:** RoseChat is opinionated about chat lifecycle (events, recipients, formatting). Two plugins trying to own the same `AsyncChatEvent` causes duplicate messages and dropped messages. Handing off keeps one owner.
- **Setup:** make sure RoseChat is installed. Configure the LumaGuilds channel in RoseChat's `channels.yml` if you want to customize the formatting; the default works out of the box. Confirm the `lumaguilds_chat_format` placeholder is resolving (test via `/papi parse <player> %lumaguilds_chat_format%`).
- **Tag rendering in RoseChat:** RoseChat parses MiniMessage in its format strings. LumaGuilds renders guild tags via `%lumaguilds_guild_tag%` (legacy `§` codes) for default RoseChat formats, with `_raw` and `_plain` variants for advanced users. See [PlaceholderAPI](placeholderapi.md) for the full list.
- **Common task: Verifying the integration.** Steps to confirm guild chat works: enable RoseChat → restart → `/g chat` → type a message → confirm only guild members see it. If the message leaks to global, see Troubleshooting.
- **Common task: Customizing the guild channel format.** Snippet showing a sample RoseChat channel config with LumaGuilds placeholders.
- **Gotchas:** Ally chat (`/g allychat`) is also hooked but uses a different RoseChat channel — both must be configured. Party chat is not RoseChat-hooked (still uses the legacy chat-claim system). Without RoseChat installed, LumaGuilds falls back to direct `AsyncChatEvent` interception (the legacy path).
- **Related:** installation, placeholderapi, troubleshooting.

- [ ] **Step 1:** Read `src/main/kotlin/net/lumalyte/lg/interaction/listeners/GuildChatListener.kt` to confirm current hook behavior.
- [ ] **Step 2:** Write the page.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 8: Geyser/Floodgate behavior

**File:** `wiki/docs/admins/geyser.md`

**Front-matter:**
- title: `Geyser/Floodgate behavior`
- topic: `geyser`
- summary: `How LumaGuilds renders menus and dispatches teleports for Bedrock players via Geyser/Floodgate.`
- keywords: `[geyser, floodgate, bedrock, forms, cross-play]`
- related: `[installation, troubleshooting]`

**Content checklist:**

- **How it works:** Geyser translates Bedrock protocol to Java. Floodgate provides the form API. LumaGuilds detects Bedrock players via Floodgate's API and routes them through Bedrock-flavored menus (chat-based forms) instead of inventory GUIs. Commands work identically.
- **What renders differently on Bedrock:** the home selection menu, the ally home access editor, the rank-change confirmation, and a few other multi-page selectors. The home-teleport flow is dispatched on the main thread for safety. (See player-facing [Bedrock differences](../players/bedrock.md) for the user-visible breakdown.)
- **Setup:** install Geyser and Floodgate per the standard Geyser docs. LumaGuilds requires no Geyser-specific config. Confirm Bedrock players load by checking the player join log for a Floodgate-prefixed username.
- **Common task: Confirming the integration works.** A Bedrock player joins → runs `/g menu` → sees a Floodgate form instead of an inventory.
- **Gotchas:** Teleport callbacks for Bedrock players were not always dispatched on the main thread — that's fixed (see PR #39 / commit `5b8bf3a`). If you see Bukkit-API-on-wrong-thread errors after teleport for Bedrock players, check the plugin version. Floodgate must be installed even on Bedrock-only servers — LumaGuilds uses it to detect the Bedrock client.
- **Related:** installation, troubleshooting.

- [ ] **Step 1:** Read the existing stub. Skim `BedrockGuildHomeMenu.kt`, `BedrockGuildRelationsMenu.kt`, and `BedrockGuildMemberRankConfirmationMenu.kt` to confirm what's rendered as Floodgate forms.
- [ ] **Step 2:** Write the page.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 9: Lunar Client integration

**File:** `wiki/docs/admins/lunar.md`

**Front-matter:**
- title: `Lunar Client integration`
- topic: `lunar`
- summary: `Drive Lunar Client features (cooldowns, mods, server props) from LumaGuilds events.`
- keywords: `[lunar, lunarclient, integration]`
- related: `[installation, rosechat]`

**Content checklist:**

- **How it works:** Source: `LUNAR_CLIENT_INTEGRATION_PLAN.md` (currently a plan; rewrite as user-facing docs). Verify against the actual integration code under `src/main/kotlin/net/lumalyte/lg/infrastructure/lunar/` or wherever Lunar lives. If the integration isn't actually wired yet (the file is `*PLAN.md` after all), say so explicitly: "planned, not yet shipped" — and what to expect.
- **Setup:** what dependencies are needed (Lunar SDK), what config keys enable it.
- **What it does on guild events:** which guild events trigger Lunar features (cooldowns surfaced, mods enabled/disabled per faction, etc.). Drive this from the actual code, not the plan doc.
- **Common task: Enabling Lunar integration.** Steps.
- **Common task: Disabling it.** Single config flag.
- **Gotchas:** Only affects players on Lunar Client; Java vanilla and Bedrock players are unaffected. If the feature isn't shipped yet, note that calling out the planned-but-not-yet status is the point of this page until it ships.
- **Related:** installation, rosechat.

- [ ] **Step 1:** Read `LUNAR_CLIENT_INTEGRATION_PLAN.md`. Grep for `lunar` (case-insensitive) in `src/main/kotlin/` to see if anything is shipping vs purely planned.
- [ ] **Step 2:** If the integration is purely a plan with no shipped code, write the page as "Status: planned (not yet shipped)" with a brief outline of the design and a link to the plan doc. If it IS shipped, write the full user-facing reference.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 10: Web leaderboard API

**File:** `wiki/docs/admins/leaderboard-api.md`

**Front-matter:**
- title: `Web leaderboard API`
- topic: `leaderboard-api`
- summary: `Public HTTP API exposing guild leaderboard data for external dashboards.`
- keywords: `[leaderboard, api, web, http, json]`
- related: `[installation]`

**Content checklist:**

- **How it works:** LumaGuilds runs an embedded HTTP server (or routes through Paper's web facility — verify) exposing JSON endpoints with current guild leaderboard data. Source: commit `ef18a7a` ("feat: web API for public guild leaderboard") and follow-up `8faab78` ("feat: extend leaderboards with live data + PAPI placeholders"). Verify against `src/main/kotlin/net/lumalyte/lg/infrastructure/web/` or similar.
- **Setup:** what config block enables it (port, bind address, auth?). Default off vs default on — check `config.yml`.
- **Endpoints reference:** every JSON endpoint with: path, query params, sample response body. Source the list from the controller code.
- **Common task: Enabling the API.** Config snippet.
- **Common task: Putting it behind nginx.** Sample reverse-proxy snippet so the API is served at e.g. `wiki.example.com/api/...`.
- **Common task: Authenticating requests.** If there's an API key system, document it. If not, say so and recommend nginx-level IP allowlisting.
- **Gotchas:** Endpoint data is cached for performance — refresh interval is configurable. Don't poll faster than once per minute. The API exposes public guild info only — no member UUIDs unless configured.
- **Related:** installation.

- [ ] **Step 1:** Grep for the leaderboard web API code: try `grep -r "leaderboard" src/main/kotlin/net/lumalyte/lg/infrastructure/` and look for HTTP-related classes. Read the controller(s) you find.
- [ ] **Step 2:** Write the page from what the code actually exposes — don't speculate. If a feature on the checklist (auth, etc.) doesn't exist yet, note that.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 11: Claims integration

**File:** `wiki/docs/admins/claims.md`

**Front-matter:**
- title: `Claims integration`
- topic: `claims`
- summary: `Hook LumaGuilds into the server's claims plugin so guild membership grants claim permissions.`
- keywords: `[claims, integration, permissions]`
- related: `[installation, override, permissions]`

**Content checklist:**

- **How it works:** LumaGuilds optionally integrates with a claims plugin. Guild membership maps to a permission set on claims owned by guild members. The integration is *optional* and gated by a config flag — servers without claims can disable it entirely (and should, per the SPEAR layer rules — see the `appModule` toggle behavior referenced in commit `10357f3`).
- **Setup:** which claims plugin is supported (verify against the integration code), the config flag that enables it.
- **What gets granted:** when a player joins a guild, which permissions on each guild member's claims they automatically get. This is driven by a `GuildRolePermissionResolver` (referenced in commits; verify against the actual class).
- **Common task: Enabling claims integration.** Config snippet.
- **Common task: Disabling it.** Single config flag.
- **Common task: Recovering from "Koin crash on /lumaguilds override" if claims are disabled.** Was a real bug — when claims integration is off, the `GuildRolePermissionResolver` isn't in the Koin module, and `/lumaguilds override` used to crash trying to inject it (commit `10357f3`). Fixed; resolver is now resolved lazily. If you see "NoDefinitionFoundException" on `/lumaguilds override` after upgrading, the plugin version is too old — upgrade.
- **Gotchas:** Disabling claims integration after enabling it doesn't revoke already-granted claim permissions — those are managed by the claims plugin's own data. Switching claims plugins requires a clean migration; document a fresh start as the supported path. Admin override bypasses claim permission checks while it's on (see [`/lumaguilds override`](override.md)).
- **Related:** installation, override, permissions.

- [ ] **Step 1:** Grep for `claims` in `src/main/kotlin/net/lumalyte/lg/infrastructure/` and read the integration code. Read `GuildRolePermissionResolver.kt`.
- [ ] **Step 2:** Write the page.
- [ ] **Step 3:** Lint + build + commit.

---

## Task 12: Final smoke test

**Files:** none (verification only).

- [ ] **Step 1:** Full check sequence:
  ```
  cd D:/BadgersMC-Dev/LumaGuilds/.claude/worktrees/wiki-phase2
  python tools/wiki/lint_frontmatter.py
  python tools/wiki/check_topic_parity.py
  python -m mkdocs build --strict
  ```
  All three must pass.

- [ ] **Step 2:** Verify `site/llms.txt` Admins section is now populated:
  ```
  python -c "print(open('site/llms.txt').read())" | grep -A 12 '^## Admins'
  ```
  Expected: 10 entries under `## Admins`, one per page.

- [ ] **Step 3:** Spot-check three pages in `mkdocs serve` at <http://127.0.0.1:8000/admins/installation/>, ensuring rendering, code blocks, and cross-links all work.

- [ ] **Step 4:** Push the branch and open the Phase 2 PR. Include in the PR description:
  - A reminder that Phase 3 (Developers migration) follows.
  - A note that the top-level loose `.md` files (PERMISSIONS.md, PLACEHOLDERAPI_README.md, etc.) STAY at repo root for now — they're removed in Phase 3 after their content has been confirmed migrated.

---

## Self-Review Notes

- **Spec coverage:** every Admins page in the design doc §3 has a task. 10 pages → 10 content tasks + 1 orientation + 1 smoke test = 12 tasks total.
- **No placeholders in mechanical tasks:** front-matter values, file paths, and CI commands are all specified verbatim. The content tasks deliberately use content *checklists* rather than full pre-written prose — same trade-off as Phase 1's player pages, and for the same reason (the prose is human/Haiku-authored against real code, not pre-canned).
- **Source-of-truth discipline:** every Admin page is told to verify against the code (plugin.yml, the expansion class, the integration listener) rather than blindly copying from the existing loose `.md` files. Drift gets called out in the page itself.
- **Anti-patterns avoided:** no new CI checks (Phase 1 ones cover the wiki side), no Kotlin changes, no new tools. Pure content.
