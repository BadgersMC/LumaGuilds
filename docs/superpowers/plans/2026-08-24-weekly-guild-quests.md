# Weekly Guild Quests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship one automatically refreshed weekly quest set shared by all guilds, with independent progress, claimable Guild EXP/item milestones, leaderboard payouts, a full-set bonus, menu access, placeholders, and block-place exploit protection.

**Architecture:** Pure domain types validate and generate typed action/target/condition combinations. Application services coordinate persisted weekly lifecycle, progress, claims, and payouts through ports; Bukkit/SQLite/PlaceholderAPI/ChestGUI remain infrastructure or interaction adapters.

**Tech Stack:** Kotlin 2.0, Paper 1.21.11, Koin, co.aikar IDB/SQLite, ChestGUI, Nexus LangService, PlaceholderAPI, JUnit 5/MockK.

**Spec:** `docs/requirements.md` REQ-074..REQ-079 and `docs/implementation.md` §Weekly Guild Quests (PR-16)

## Global Constraints

- Follow SPEAR: spec → failing test → minimal implementation → layer check → refactor/green.
- Domain has no Bukkit, Koin, IDB, Adventure, or framework imports.
- One shared quest set per reset period; progress/reward state remains per guild.
- No implicit location checks; location metadata validates explicit conditions only.
- Reward, reset, and leaderboard payout paths are idempotent.
- Claims are disabled on EnthusiaSMP; do not add claim actions.
- Placeholder evaluation is read-only.

---

### Task 1: Typed quest model and semantic generation

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/domain/values/QuestAction.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/domain/entities/QuestDefinition.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/domain/entities/GuildQuestProgress.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/domain/services/QuestGenerationValidator.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/domain/services/QuestGenerator.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/domain/services/QuestGenerationValidatorTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/domain/services/QuestGeneratorTest.kt`

**Interfaces:**
- Produces typed `QuestDefinition`, `QuestAction`, optional `QuestCondition`, `BlockProvenancePolicy`, structured `QuestValidationFailure`, and bounded seeded `QuestGenerator.generate()`.

- [ ] Write validator tests proving action/target incompatibility, impossible and redundant explicit locations, amount bounds, and acceptance of `Kill 3 Ender Dragons` with no hidden condition.
- [ ] Run the focused tests and verify RED because the domain API does not exist.
- [ ] Implement the minimum pure domain model and validator.
- [ ] Add generator tests for shared-set size, duplicates, deterministic seed, bounded retries, and fallback.
- [ ] Run focused tests GREEN and the architecture guard.
- [ ] Mark LG-1601 complete with test/file evidence.

### Task 2: Weekly persistence, lifecycle, claims, and payouts

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/persistence/QuestRepository.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/QuestRepositorySQLite.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/QuestService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/config/ProgressionConfig.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionConfigService.kt`
- Modify: `src/main/resources/progression.yml`
- Test: `src/test/kotlin/net/lumalyte/lg/application/services/QuestServiceTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/QuestRepositorySQLiteTest.kt`

**Interfaces:**
- Consumes `QuestDefinition` and `QuestGenerator` from Task 1 plus existing `ProgressionService.awardExperience(..., WEEKLY_ACTIVITY)`.
- Produces read APIs for active set/progress/rank/time, mutation APIs for increment/claim/reset, and atomic persisted claimed/bonus/payout state.

- [ ] Write repository contract tests for one active shared set, independent guild progress, overflow counts, rank ordering, claimed/bonus flags, and restart persistence.
- [ ] Verify RED, then implement schema creation and parameterized IDB queries.
- [ ] Write service tests for startup catch-up, restart stability, milestone claim-once, full-set bonus-once, leaderboard-before-clear, and reset idempotency.
- [ ] Verify RED, then implement the smallest lifecycle service and configuration loader.
- [ ] Ship disabled-safe/default quest configuration with generous configurable Guild EXP tiers and empty operator definitions.
- [ ] Run focused tests GREEN and mark LG-1602 complete with evidence.

### Task 3: Event progress and block provenance

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/persistence/BlockProvenanceRepository.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/BlockProvenanceRepositorySQLite.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/QuestProgressListener.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/BlockProvenanceRepositorySQLiteTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/listeners/QuestProgressListenerTest.kt`

**Interfaces:**
- Consumes `QuestService.incrementProgress(guildId, action, target, amount, context)`.
- Produces normalized progress from kill, break/place, craft, smelt, fish, enchant, bank-deposit, and war-win events; provenance lookup keyed by world UUID/x/y/z.

- [ ] Write provenance tests proving player-placed NATURAL_ONLY blocks never count, ANY crops count, and records survive/reconcile break/explosion/piston operations.
- [ ] Verify RED, then implement the coordinate ledger and conservative piston behavior.
- [ ] Write listener tests for guild resolution, cancellation/game-mode gates, target quantities, and exception containment.
- [ ] Verify RED, then implement MONITOR/ignoreCancelled handlers using existing service/event patterns.
- [ ] Run focused tests GREEN and mark LG-1603 complete with evidence.

### Task 4: Menu, placeholders, DI, startup, and localization

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildQuestsMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildDashboard.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/placeholders/LumaGuildsExpansion.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/di/Modules.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/LumaGuilds.kt`
- Modify: `src/main/resources/lang/en_US.yml`
- Modify: `docs/placeholders.md`
- Test: `src/test/kotlin/net/lumalyte/lg/interaction/menus/MenuLocalizationTest.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/placeholders/WeeklyQuestPlaceholderTest.kt`

**Interfaces:**
- Consumes QuestService read/claim APIs from Task 2.
- Produces main-dashboard navigation, six-row paginated quest display, localized claim feedback, and read-only indexed weekly placeholders.

- [ ] Add failing localization/placeholder tests for every new key, safe fallback, timer floor, guild-specific progress, and mutation-free evaluation.
- [ ] Implement the dashboard/factory/menu using existing ChestGUI, MenuNavigator, Nexo fallback, and LangService conventions.
- [ ] Add timer, quest, progress, reward, completion, and bonus placeholders to the existing expansion.
- [ ] Wire repositories/service/listener/reset startup through Koin and plugin lifecycle.
- [ ] Run focused tests, architecture tests, `compileKotlin`, full `test`, and resource/localization contracts.
- [ ] Mark LG-1604 complete with exact evidence and review the diff for scope/layer violations.

### Task 5: Final verification

**Files:**
- Modify only files required to resolve failures introduced by PR-16.

- [ ] Run `./gradlew clean test` and record the result.
- [ ] Run `./gradlew clean compileKotlin` and record the result.
- [ ] Confirm domain imports remain framework-free.
- [ ] Confirm `git status` contains only PR-16/spec/plan changes.
- [ ] Review reward/reset/provenance concurrency and idempotency paths.
- [ ] Update task evidence, summarize known environment limitations, and stop.
