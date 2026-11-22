# Nexus runtime cutover implementation plan

> **For agentic workers:** Execute the independent command and startup investigations in isolated worktrees; central integration owns shared wiring and the final verification. Steps use checkbox syntax for tracking.

**Goal:** Complete the source migration to a single Nexus runtime without changing Guilds gameplay or claiming unperformed release acceptance.

**Architecture:** Keep explicit typed lazy dependencies and domain/application boundaries. Port command metadata around preserved handler logic, then replace the production composition root only after route and graph parity. Storage initializes before Nexus and closes after explicit persistence flushes and context cancellation.

**Tech Stack:** Kotlin 2.3.20, Java 25, Paper 26.2, Nexus initially pinned to `5ae8b642`, JUnit/MockK/MockBukkit, SQLite/MariaDB.

**Spec:** `docs/requirements.md`, REQ-NEXUS-CUTOVER-1 through 4; `docs/NEXUS-MIGRATION.md` strict startup ordering.

## Global constraints

- No gameplay changes mixed into framework migration.
- Preserve externally published Bukkit service contracts and claims-disabled/optional integration behavior.
- One runtime context; retain Aikar IDB until a separate persistence migration.
- No production deployment or merge of a partially migrated plugin.
- No domain/application implementation changes without an observed failing test.

## Review focus

- Root commands with optional/greedy arguments must execute through the actual Brigadier tree, not merely direct handler calls.
- Permissions and player-only restrictions must hold on bare roots, descendants and completion requests.
- First startup with PlaceholderAPI installed must register after dependencies exist.
- Failed initialization and repeated disable must release exactly the resources successfully acquired.
- Claims disabled and missing optional plugins must not force unavailable dependencies.

## Task 1: Command and lifecycle parity inventory

- [~] Compare legacy annotations, aliases, defaults, parameter cardinality and completions with Nexus scanner/tree definitions; include manually registered Bukkit administrative commands.
- [~] Compare Market's lifecycle ownership with Guilds' exact Nexus pin, not an unrelated local checkout or newer module API.
- [ ] Record route ownership and missing behavior; every missing route blocks final activation.

## Task 2: First-start registration repair

**Files:** `LumaGuilds.kt`; a focused startup ordering regression in `src/test/kotlin/net/lumalyte/lg/`.

- [ ] Observe a failing first-start PlaceholderAPI ordering test.
- [ ] Move registration after the active dependency context exists; retain optional-plugin gates.
- [ ] Run focused tests, record evidence, and commit the isolated repair.

## Task 3: Party-chat command port

**Files:** `interaction/commands/PartyChatCommand.kt`, shared framework-free party handlers, `interaction/commands/nexus/NexusPartyChatCommand.kt`, focused Nexus command tests.

**Interface:** Preserve `PartyChatCommandDependencies`; expose one optional greedy default route and `switch`, `toggle`, `help`, `clear` under `pc` with aliases `pchat`/`partychat`. Reuse handler bodies between transitional ACF and Nexus adapters until contraction.

- [ ] Write failing scanner and behavior tests for all routes, optional switch target, disabled-party guard, preference clearing and single-name/message routing.
- [ ] Extract existing behavior without changing authorization, locale keys or persistence decisions; add Nexus metadata.
- [ ] Prove injected services, state changes and failure responses; add live completion providers when central command registration is assembled.

## Task 4: Guild and claim command parity

**Files:** `interaction/commands/GuildCommand.kt`, Nexus guild/claim command groups and focused tests. Exact partition follows inventory to avoid overlapping edits.

- [ ] Write failing dispatcher tests for greedy rename and missing guild routes before conversion.
- [ ] Preserve existing business handlers and current-state checks; convert metadata, custom arguments and permission-filtered suggestions.
- [ ] Verify nested/default/optional routes against pinned registry behavior; record framework limitations rather than hiding them with direct-call tests.

## Task 5: Nexus root-argument dependency defect

**Files:** Separate Nexus worktree, `nexus-paper/.../PaperCommandRegistry.kt` and actual Brigadier tests.

- [ ] Prove required/optional/greedy default-root arguments currently fail to parse.
- [ ] Reuse `attachArguments` for default roots; verify permissions, player context and descendant composition.
- [ ] Run Nexus checks and review the isolated fix before deciding publication/pinning. Guilds must consume a reproducible reviewed artifact; no local-only dependency for release.

## Task 6: Single lifecycle and legacy contraction

**Files:** `LumaGuilds.kt`, coordinated lifecycle adapters, `di/nexus/`, obsolete `di/` legacy factories, command adapters, build packaging, ownership tests.

- [ ] First prove graph singleton identity, claims-disabled behavior, startup order, rollback and exactly-once shutdown with failing tests.
- [ ] Replace startup with one Nexus owner; preserve explicit flushes before closing context/storage and existing Bukkit API publication.
- [ ] Register the complete command tree once, then remove legacy Koin/ACF source, dependencies, shading and utilities while retaining IDB.
- [ ] Run architecture/packaging checks and relevant SQL/integration regressions.

## Task 7: Candidate verification and release gates

- [ ] Freeze source; run full Java 25 regression and shadow artifact validation.
- [ ] Push safe updates to existing draft PR #254 and inspect exact-head GitHub Gradle/MariaDB checks.
- [ ] Review Codacy findings with explicit blocking disposition; previous full-PR summary is not a source-completion verdict.
- [ ] Record live Paper Java/Bedrock command, claims, bank, chat, external API and restart acceptance separately; do not claim release readiness without this evidence.

## Execution ledger

- Baseline `5359deb5`: consumer conversion complete, full regression and hosted Gradle/MariaDB passed. Production still Koin/ACF.
- User authorized continuing toward full cutover and a 48-hour release candidate. Work proceeds without an additional plan approval checkpoint; deployment/merge remain outside authorization.
- Investigations found a first-start PlaceholderAPI ordering defect, missing party-chat port, greedy claim-rename gap and a pinned Nexus default-root argument defect. Fixes require tests before implementation.
