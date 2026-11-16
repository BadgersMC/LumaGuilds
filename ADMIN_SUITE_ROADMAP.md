# LumaGuilds Administration Suite

Owner: BadgersMC. Target: `BadgersMC/LumaGuilds`, current v3 / Minecraft 26.2.

## Contract

Authorized staff bypass gameplay checks but **not** data-integrity checks. A temporary guild inspection context must not join the guild, consume a member slot, replace the administrator's real guild, or expose them to player kick/demotion. Owner transfers remain atomic and cannot be approximated by rank changes.

Admin operations must work for offline UUIDs and console where appropriate. Every mutation needs an auditable actor, target, before/after and timestamp. Sensitive actions need preview and confirmation; repairs must be idempotent.

## Phase 1 — Authority (P0)

- `/lumaguilds bypass` with legacy `override` alias; `lumaguilds.admin.bypass` permission.
- `/lumaguilds admin view <guild>` and `admin exit`: transient non-member inspection (Java and Bedrock entry point).
- Protect staff with active bypass from ordinary kick and demotion paths.
- Centralize authorization so a bypassed, non-member administrator can perform any owner action.
- Audit direct rank/membership checks in commands, services, asynchronous callbacks, menus and Bedrock forms.
- Verify logout cleanup even when claims are disabled.
- Regression tests: normal rank limits preserved, non-member bypass, staff protection, owner-transfer invariant, session cleanup.

**Current implementation is a first slice.** The entire command/menu authorization audit is not complete; do not deploy solely on the basis of the first tests.

## Phase 2 — Administrative actions (P1)

Add a reusable `AdminActionService` whose operations are shared by commands and GUI:
guild lifecycle/ownership, members/ranks, XP/prestige/rewards, bank/transactions, homes/outposts, vault items/capacity, upgrades, quests, wars, relations, season recovery. Do not perform raw SQL or expensive I/O from command handlers.

Preserve EnthusiaSMP rules:
- Guild homes can be anywhere; **no claims requirement**.
- Six base guild homes are available before prestige; extra outpost unlocks are at most one per prestige, toward the designed maximum of sixteen.
- Excess previously unlocked homes should be locked, not deleted, until legitimately repurchased.
- Purchased vault storage capacity is permanent across prestige.

## Phase 3 — Diagnosis and repair (P1)

Introduce `/lumaguilds admin diagnose <guild>` and preview-first reconciliations for entitlements, homes, vault storage, member/rank consistency, progression/rewards, stuck wars and seasons. Each mutation records an immutable audit entry and returns a stable operation ID; support safe undo only when state preconditions still match.

Use transaction boundaries, verified backups before destructive repair, and live cache/waypoint/leaderboard invalidation after commit.

## Phase 4 — Admin GUI (P2)

Searchable guild inspector with no-membership browsing, category pages, action previews and confirmations. Java/Bedrock parity. Commands and GUI must use identical server-side authorization and mutation services.

## Release gates

1. Java 21 build and focused unit tests.
2. Full suite and GitHub CI on the isolated branch.
3. Replica of a consistent production DB, with migration and repair rehearsals.
4. Staff acceptance tests (member, non-member, offline, console, Java, Bedrock).
5. Separate, explicitly approved production rollout with verified backup and rollback path.
