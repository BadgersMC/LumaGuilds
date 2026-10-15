# Modern guild menu presentation

Base: canonical `BadgersMC/LumaGuilds` main `283d75f1`, fetched 2026-10-14.
Continuing the clean isolated `codex/guild-recruitment` branch at `620c7153`.

## Spec

- REC-020: WHEN a supported Java client opens recruitment, THE SYSTEM SHALL show bounded native pages with real guild banners, Nexo item icons, visible descriptions, current search/filter state, category controls and explicit parent navigation. Older clients and Bedrock SHALL retain their supported inventory/form paths. Existing membership, fee quotation, payment recovery and review authorization SHALL remain authoritative.
- REC-021: WHEN a native menu action arrives, THE SYSTEM SHALL accept it only for its player, current navigation/page and lifetime, at most once, on the server thread. Invalid, expired or replaced actions SHALL not mutate state or reopen menus. Back and explicit Close SHALL remain distinct.
- REC-022: WHEN guild settings or progression opens, THE SYSTEM SHALL expose grouped settings and a service-backed progression overview while preserving access checks, existing editors, rewards, quests and source-cap details. Changing presentation SHALL not invent reward unlocks or change chat preference scope.
- REC-023: WHEN native dialog presentation is unavailable, THE SYSTEM SHALL retain the existing Nexo-themed inventory. Proprietary art SHALL remain outside the public repository. Native buttons/layout remain client-owned; no global Minecraft UI texture override or shader replacement is part of this change.

## Architecture and acceptance

The application services and persistence remain unchanged. The infrastructure dialog adapter renders bounded item/action pages and dispatches guarded callbacks. Interaction menus retain their controllers and existing mutation paths. Nexo supplies registered item models; no assumption is made that an HTML/CSS frame can be applied to a native dialog.

The browser concept is art direction, not a client screenshot. Exact frames, transparent stacking, font scaling and native Escape behavior need real Java/resource-pack acceptance. TEST staging is authorized; restart/reload and production changes are not part of this delivery.

Project-local EARS/state helpers are absent (searched `tools` and `docs`); manual requirements/tasks/evidence are used.

## Tasks

- [x] Prove callback ownership, expiry, duplicate and navigation rejection.
- [x] Implement native recruitment presentation and grouped settings/progression entry.
- [x] Compile and run focused/full regression checks.
- [ ] Update the private interactive preview to distinguish native behavior from art direction.
- [ ] Inspect exact-head PR checks and stage the combined unmerged TEST artifact with backup and byte verification.
- [ ] Deferred: real client acceptance and authorized activation.

## Local evidence

New callback ownership tests first failed compilation because the implementation was absent; this is missing-feature proof, not a historical behavioral red run. Four session tests cover wrong identity, duplicate/replaced pages, expiry, navigation revocation, closure and clock rollback. Three presentation tests cover bounded result pages, complete fee-confirmation text, delegated actions and distinct Back behavior. After refining the native presenter test seam, full `check shadowJar --offline` passes: 1,856 cases, zero failures/errors and 16 existing optional skips. Localization baselines and architecture contracts pass without loosening their assertions.

Native recruitment uses three entries per page, real banners and Nexo item bodies, with visible quick-filter state and a separate navigation dialog. Native settings routes to themed category editors with a right-hand category rail; personal chat remains separate from the guild-wide reconnect reset. Progression uses current reward-state counts and existing quest/source/reward/prestige services; quest rankings display real per-quest scores and the viewing guild's standing. Discord confirmation retains the menu after sending its validated clickable chat link.

The public dialog API exposes item/text bodies, inputs and button grids, not per-dialog HTML/CSS chrome. Custom pixel frames and the connected free-form graph remain art direction and are not claimed as implemented. No global GUI override, shader or proprietary artwork was added. The live client must still validate resource-pack models, native layout, hover/focus, Escape, reconnect and stale-session handling.
