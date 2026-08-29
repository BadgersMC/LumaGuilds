# Chapter 2 Permanent Progression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement permanent guild levels 1–100, configurable guild-wide per-source XP caps, validation-before-cap anti-abuse, and uncapped weekly-quest rewards under REQ-049 and REQ-089.

**Architecture:** Pure domain types calculate levels, source policies, periods, and award decisions. An application service owns the validation and atomic award workflow through ports; SQL infrastructure commits cap reservation, progression mutation, and the audit transaction together. Paper listeners translate events into typed activity requests and perform platform-only eligibility/provenance checks before calling the application service.

**Tech Stack:** Kotlin 2, JDK 21, Gradle, JUnit 5, MockK, Paper 1.21.11, Aikar IDB, SQLite, MariaDB, Koin.

**Spec:** `docs/superpowers/specs/2026-08-27-chapter-2-progression-revamp-design.md`

## Global Constraints

- Follow SPEAR for every task: requirement, failing test, minimum implementation, architecture test, refactor, green verification.
- Permanent progression is level 1–100; reaching target level `L` costs `floor(500 * L^1.15 + 150 * L)` XP and level 100 begins at exactly 5,446,893 XP.
- Permanent XP and achieved level never decrease in this feature. Strike penalties remain outside this plan and must not call the new permanent award path with negative XP.
- Caps are fixed, guild-wide, and independent per source; there is no per-player cap and no combined guild cap.
- Rejected activity consumes zero cap. Weekly quest XP uses `WEEKLY_ACTIVITY` and bypasses source caps.
- Creative, spectator, cancelled, AFK, suspicious, and player-placed natural-mining activity awards zero XP.
- Claims remain disabled and no claim-based source is added.
- Domain imports no Bukkit, Koin, ACF, Adventure, persistence, scheduler, or integration type.
- Existing unrelated behavior and operator changes in the working tree must be preserved.

## File Structure

### New files

- `src/main/kotlin/net/lumalyte/lg/domain/values/ExperiencePolicy.kt` — cap period, source-pool identity, configured policy, and period-window calculation.
- `src/main/kotlin/net/lumalyte/lg/domain/entities/ExperienceAward.kt` — typed request/result/rejection data.
- `src/main/kotlin/net/lumalyte/lg/application/persistence/ExperienceAwardRepository.kt` — atomic award and usage-read port.
- `src/main/kotlin/net/lumalyte/lg/application/services/PermanentExperienceService.kt` — validation-before-cap orchestration.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/ExperienceAwardRepositorySQL.kt` — SQLite/MariaDB atomic adapter.
- `src/test/kotlin/net/lumalyte/lg/domain/values/ChapterTwoProgressionCurveTest.kt` — exact curve contract.
- `src/test/kotlin/net/lumalyte/lg/domain/values/ExperiencePolicyTest.kt` — period/pool/config invariants.
- `src/test/kotlin/net/lumalyte/lg/application/services/PermanentExperienceServiceTest.kt` — orchestration acceptance matrix.
- `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/ExperienceAwardRepositorySQLTest.kt` — atomic SQL contract.
- `src/test/kotlin/net/lumalyte/lg/infrastructure/listeners/ProgressionEventListenerTest.kt` — Paper translation and rejection tests.

### Modified files

- `src/main/kotlin/net/lumalyte/lg/domain/values/ProgressionCurve.kt` — hard cap at 100 and stable level-100 progress behavior.
- `src/main/kotlin/net/lumalyte/lg/domain/values/ExperienceSource.kt` — explicit Chapter 2 sources and shared pool keys.
- `src/main/kotlin/net/lumalyte/lg/application/services/PlaytimeActivityService.kt` — accept `UUID`, removing Bukkit from the application package.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/PlaytimeActivityServiceBukkit.kt` — adapt UUID to EnthusiaPlaytime.
- `src/main/kotlin/net/lumalyte/lg/application/services/ProgressionService.kt` — retain query/perk API; route positive awards to the new service through the infrastructure facade.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkit.kt` — delegate positive awards and prevent values above level 100.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/ProgressionEventListener.kt` — translate supported Paper events and stop calculating final XP locally.
- `src/main/kotlin/net/lumalyte/lg/config/MainConfig.kt` — immutable Chapter 2 source policies and target pools.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt` — validated config loading.
- `src/main/resources/config.yml` — shipped source values/caps and vanilla target pools.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt` — source-period usage table.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/MariaDBMigrations.kt` — matching table and indexes.
- `src/main/kotlin/net/lumalyte/lg/di/Modules.kt` — bind repository/service and constructor-inject the listener.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/services/QuestRewardSinkBukkit.kt` — explicit uncapped system award.
- `src/main/kotlin/net/lumalyte/lg/infrastructure/placeholders/LumaGuildsExpansion.kt` — permanent progress and source-cap read-only placeholders.
- `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildProgressionMenu.kt` — display accepted usage against configured per-source caps.
- `src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildProgressionInfoMenu.kt` — matching Bedrock read model.
- `src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt` — application-package Bukkit prohibition if not already covered.
- `docs/tasks.md` — record evidence and complete LG-1201 only after full verification.

---

### Task 1: Lock the Permanent Level Curve

**Requirements:** REQ-049

**Files:**
- Create: `src/test/kotlin/net/lumalyte/lg/domain/values/ChapterTwoProgressionCurveTest.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/domain/values/ProgressionCurve.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/config/MainConfig.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Produces: `ProgressionCurve.experienceForNextLevel(currentLevel: Int): Int`, `totalExperienceForLevel(targetLevel: Int): Int`, `levelFromExperience(totalExperience: Int): Int`, all capped at permanent level 100.
- Produces: shipped curve values `baseXp=500.0`, `levelExponent=1.15`, `linearBonusPerLevel=150`, `maxLevel=100`.

- [ ] **Step 1: Write the failing curve contract**

```kotlin
class ChapterTwoProgressionCurveTest {
    private val curve = ProgressionCurve(500.0, 1.15, 150, maxLevel = 100)

    @Test fun `chapter two anchors are exact`() {
        assertEquals(1_409, curve.experienceForNextLevel(1))
        assertEquals(114_763, curve.experienceForNextLevel(99))
        assertEquals(5_446_893, curve.totalExperienceForLevel(100))
    }

    @Test fun `permanent level never exceeds one hundred`() {
        assertEquals(100, curve.levelFromExperience(5_446_893))
        assertEquals(100, curve.levelFromExperience(Int.MAX_VALUE))
        assertEquals(0, curve.experienceInCurrentLevel(5_446_893))
    }
}
```

- [ ] **Step 2: Run the focused test and confirm red**

Run: `./gradlew test --tests '*ChapterTwoProgressionCurveTest'`

Expected: compilation fails because `ProgressionCurve` has no `maxLevel` parameter, or the old level-101 behavior fails.

- [ ] **Step 3: Implement the minimum capped curve**

```kotlin
class ProgressionCurve(
    private val baseXp: Double,
    private val exponent: Double,
    private val linearBonusPerLevel: Int,
    private val maxLevel: Int = 100,
) {
    fun experienceForNextLevel(currentLevel: Int): Int {
        if (currentLevel >= maxLevel) return 0
        val targetLevel = currentLevel + 1
        return floor(baseXp * targetLevel.toDouble().pow(exponent) + targetLevel * linearBonusPerLevel).toInt()
    }
}
```

Update `levelFromExperience` to stop at `maxLevel`, and return zero current-level progress at the cap. Add `maxLevel` to `ProgressionConfig`, load it from `progression.max_level`, and set the four shipped defaults above.

- [ ] **Step 4: Run focused and existing curve tests**

Run: `./gradlew test --tests '*ProgressionCurveTest' --tests '*ChapterTwoProgressionCurveTest'`

Expected: PASS after updating old assertions from level 101 to the permanent cap.

- [ ] **Step 5: Commit**

```text
test(progression): lock level 100 curve
```

---

### Task 2: Define Typed Source Policies and Awards

**Requirements:** REQ-049, REQ-089

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/domain/values/ExperiencePolicy.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/domain/entities/ExperienceAward.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/domain/values/ExperiencePolicyTest.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/domain/values/ExperienceSource.kt`

**Interfaces:**
- Produces: `enum class CapPeriod { DAILY, WEEKLY, UNLIMITED }`.
- Produces: `data class ExperiencePolicy(val source, val pool, val awardXp, val capXp, val period, val enabled)` with constructor invariants.
- Produces: `data class ExperienceAwardRequest(val guildId, val actorId, val source, val units, val occurredAt, val eligible)`.
- Produces: `sealed interface ExperienceAwardResult` with `Awarded`, `Rejected`, and `NoAllowance`.

- [ ] **Step 1: Write failing policy tests**

```kotlin
@Test fun `shared ore sources consume one pool`() {
    assertEquals("ORE", ExperienceSource.DIAMOND_ORE.defaultPool)
    assertEquals("ORE", ExperienceSource.ANCIENT_DEBRIS.defaultPool)
}

@Test fun `unlimited sources require no cap`() {
    val weekly = ExperiencePolicy(ExperienceSource.WEEKLY_ACTIVITY, "WEEKLY_ACTIVITY", 1, 0, CapPeriod.UNLIMITED, true)
    assertFalse(weekly.isCapped)
}

@Test fun `invalid capped policy is rejected`() {
    assertThrows<IllegalArgumentException> {
        ExperiencePolicy(ExperienceSource.MOB_KILL, "MOB_KILL", 2, -1, CapPeriod.DAILY, true)
    }
}
```

- [ ] **Step 2: Run the test and confirm red**

Run: `./gradlew test --tests '*ExperiencePolicyTest'`

Expected: compilation fails because the new domain types and expanded sources do not exist.

- [ ] **Step 3: Add the domain model**

```kotlin
enum class CapPeriod { DAILY, WEEKLY, UNLIMITED }

data class ExperiencePolicy(
    val source: ExperienceSource,
    val pool: String,
    val awardXp: Int,
    val capXp: Int,
    val period: CapPeriod,
    val enabled: Boolean,
) {
    init {
        require(pool.isNotBlank())
        require(awardXp >= 0)
        require(capXp >= 0)
        require(period == CapPeriod.UNLIMITED || capXp > 0)
    }
    val isCapped: Boolean get() = period != CapPeriod.UNLIMITED
}
```

Expand `ExperienceSource` with distinct boss, ore-tier, brewing, exploration, recruit, and pre-cap-war sources. Give coal/copper/iron/lapis/redstone/gold/quartz/diamond/emerald/ancient-debris the `ORE` pool, craft tiers the `CRAFTING` pool, and `WEEKLY_ACTIVITY`/`ADMIN_BONUS` unlimited pools.

- [ ] **Step 4: Run domain and architecture tests**

Run: `./gradlew test --tests '*ExperiencePolicyTest' --tests '*LayerRulesTest'`

Expected: PASS; new domain files have no forbidden imports.

- [ ] **Step 5: Commit**

```text
feat(progression): define XP source policies
```

---

### Task 3: Build the Validation-Before-Cap Application Service

**Requirements:** REQ-049, REQ-089

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/persistence/ExperienceAwardRepository.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/PermanentExperienceService.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/application/services/PermanentExperienceServiceTest.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/PlaytimeActivityService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/PlaytimeActivityServiceBukkit.kt`
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/PlaytimeActivityServiceBukkitTest.kt`

**Interfaces:**
- Consumes: `ExperiencePolicy`, `ExperienceAwardRequest`, `CapPeriod` from Task 2.
- Produces: `ExperienceAwardRepository.awardAtomically(request, policy, requestedXp, window): ExperienceAwardResult`.
- Produces: `PermanentExperienceService.award(request, policy): ExperienceAwardResult`.
- Produces: `PlaytimeActivityService.isXpBlocked(playerId: UUID): Boolean`.

- [ ] **Step 1: Write the failing orchestration matrix**

```kotlin
@Test fun `ineligible activity never calls repository`() {
    val result = service.award(request.copy(eligible = false), mobPolicy)
    assertEquals(ExperienceAwardResult.Rejected(AwardRejection.INELIGIBLE), result)
    verify(exactly = 0) { repository.awardAtomically(any(), any(), any(), any()) }
}

@Test fun `blocked actor never consumes cap`() {
    every { activity.isXpBlocked(actorId) } returns true
    val result = service.award(request, mobPolicy)
    assertEquals(ExperienceAwardResult.Rejected(AwardRejection.SUSPICIOUS_OR_AFK), result)
    verify(exactly = 0) { repository.awardAtomically(any(), any(), any(), any()) }
}

@Test fun `weekly activity bypasses activity gate and cap`() {
    val result = service.award(request.copy(actorId = null, source = ExperienceSource.WEEKLY_ACTIVITY), weeklyPolicy)
    assertEquals(ExperienceAwardResult.Awarded(25_000, 25_000, false), result)
}
```

- [ ] **Step 2: Run the focused test and confirm red**

Run: `./gradlew test --tests '*PermanentExperienceServiceTest'`

Expected: compilation fails because the service and repository port do not exist.

- [ ] **Step 3: Implement orchestration in the required order**

```kotlin
class PermanentExperienceService(
    private val repository: ExperienceAwardRepository,
    private val activity: PlaytimeActivityService,
    private val clock: Clock,
) {
    fun award(request: ExperienceAwardRequest, policy: ExperiencePolicy): ExperienceAwardResult {
        if (!policy.enabled) return ExperienceAwardResult.Rejected(AwardRejection.SOURCE_DISABLED)
        if (!request.eligible) return ExperienceAwardResult.Rejected(AwardRejection.INELIGIBLE)
        if (request.units <= 0) return ExperienceAwardResult.Rejected(AwardRejection.INVALID_UNITS)
        if (request.actorId != null && activity.isXpBlocked(request.actorId)) {
            return ExperienceAwardResult.Rejected(AwardRejection.SUSPICIOUS_OR_AFK)
        }
        val requestedXp = Math.multiplyExact(policy.awardXp, request.units)
        return repository.awardAtomically(request, policy, requestedXp, policy.windowContaining(request.occurredAt))
    }
}
```

Use UTC-aligned daily/weekly windows. Unlimited policies still write an audit transaction but do not write cap usage. Change the playtime port to UUID so `application` no longer imports Bukkit; the Bukkit adapter calls `PlaytimeService.getLiveState(playerId)`.

- [ ] **Step 4: Run application, playtime, and architecture tests**

Run: `./gradlew test --tests '*PermanentExperienceServiceTest' --tests '*PlaytimeActivityServiceBukkitTest' --tests '*LayerRulesTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
feat(progression): validate XP before caps
```

---

### Task 4: Persist Atomic Awards and Source Usage

**Requirements:** REQ-049, REQ-089

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/ExperienceAwardRepositorySQL.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/ExperienceAwardRepositorySQLTest.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/MariaDBMigrations.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/DatabaseMigrationUtility.kt`

**Interfaces:**
- Implements: `ExperienceAwardRepository` from Task 3.
- Produces table: `guild_experience_source_usage(guild_id, source_pool, period_start, period_end, awarded_xp, PRIMARY KEY(guild_id, source_pool, period_start))`.
- Uses existing `guild_progression` and `experience_transactions` tables in the same transaction.

- [ ] **Step 1: Write failing SQL contract tests**

```kotlin
@Test fun `award progression usage and audit commit together`() {
    val result = repository.awardAtomically(request, mobPolicy, 2, dayWindow)
    assertEquals(ExperienceAwardResult.Awarded(2, 2, true), result)
    assertEquals(2, progression(guildId).totalExperience)
    assertEquals(2, usage(guildId, "MOB_KILL", dayWindow.start))
    assertEquals(2, transactions(guildId).single().amount)
}

@Test fun `award is clipped at remaining cap`() {
    seedUsage(guildId, "MOB_KILL", dayWindow, 5_999)
    val result = repository.awardAtomically(request, mobPolicy, 2, dayWindow)
    assertEquals(ExperienceAwardResult.Awarded(1, 6_000, true), result)
}

@Test fun `exhausted cap changes no progression row`() {
    seedUsage(guildId, "MOB_KILL", dayWindow, 6_000)
    assertIs<ExperienceAwardResult.NoAllowance>(repository.awardAtomically(request, mobPolicy, 2, dayWindow))
    assertEquals(0, progression(guildId).totalExperience)
}
```

- [ ] **Step 2: Run the repository test and confirm red**

Run: `./gradlew test --tests '*ExperienceAwardRepositorySQLTest'`

Expected: failure because the table and adapter do not exist.

- [ ] **Step 3: Add matching migrations and one transactional adapter**

```sql
CREATE TABLE IF NOT EXISTS guild_experience_source_usage (
    guild_id VARCHAR(36) NOT NULL,
    source_pool VARCHAR(64) NOT NULL,
    period_start BIGINT NOT NULL,
    period_end BIGINT NOT NULL,
    awarded_xp INT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, source_pool, period_start)
)
```

Within one database transaction: seed the usage row idempotently, lock/read it, calculate `accepted = min(requested, cap - used)`, return `NoAllowance` when zero, and conditionally increment usage only when the result remains within the cap. Then update/create `guild_progression` using the current reload-aware `ProgressionCurve`, insert `experience_transactions`, and synchronize the guild level field. Use SQLite transaction serialization and MariaDB `SELECT ... FOR UPDATE` plus conditional increment syntax through dialect-specific SQL selected once at adapter construction.

- [ ] **Step 4: Prove rollback and dialect parity**

Add a forced transaction-failure test after usage reservation and assert progression, usage, and audit all remain unchanged. Run:

`./gradlew test --tests '*ExperienceAwardRepositorySQLTest' --tests '*ProgressionRepositorySQLiteHealTest' --tests '*Migration*Test'`

Expected: PASS for SQLite fixtures and migration verification; MariaDB SQL is covered by migration/schema assertions if CI has no live MariaDB service.

- [ ] **Step 5: Commit**

```text
feat(progression): persist atomic source caps
```

---

### Task 5: Load and Validate Chapter 2 Source Configuration

**Requirements:** REQ-049, REQ-089

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/config/MainConfig.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/utils/ConfigValidator.kt`
- Modify: `src/main/resources/config.yml`
- Modify: `src/test/kotlin/net/lumalyte/lg/config/ConfigLoaderConsistencyTest.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/config/ProgressionSourceConfigTest.kt`

**Interfaces:**
- Produces: `ProgressionConfig.sourcePolicies: Map<ExperienceSource, ExperiencePolicy>`.
- Produces: `ProgressionConfig.materialPools: Map<String, Set<Material>>` and `entityPools: Map<String, Set<EntityType>>` at the infrastructure/config boundary only.
- Consumes: source policy types from Task 2.

- [ ] **Step 1: Write failing config tests**

```kotlin
@Test fun `shipped source values match chapter two model`() {
    val config = loadBundledConfig().progression
    assertEquals(ExperiencePolicy(ExperienceSource.MOB_KILL, "MOB_KILL", 2, 6_000, CapPeriod.DAILY, true), config.sourcePolicies.getValue(ExperienceSource.MOB_KILL))
    assertEquals(18_000, config.sourcePolicies.getValue(ExperienceSource.DIAMOND_ORE).capXp)
    assertEquals("ORE", config.sourcePolicies.getValue(ExperienceSource.ANCIENT_DEBRIS).pool)
    assertEquals(CapPeriod.UNLIMITED, config.sourcePolicies.getValue(ExperienceSource.WEEKLY_ACTIVITY).period)
}

@Test fun `invalid material and negative cap reject the whole snapshot`() {
    assertThrows<ConfigValidationException> { loadConfig("material: NOT_A_BLOCK\ncap_xp: -1") }
}
```

- [ ] **Step 2: Run config tests and confirm red**

Run: `./gradlew test --tests '*ProgressionSourceConfigTest' --tests '*ConfigLoaderConsistencyTest'`

Expected: failure because map-based source policies and validation do not exist.

- [ ] **Step 3: Add immutable validated config and shipped defaults**

Use this YAML shape for every source:

```yaml
progression:
  max_level: 100
  base_xp: 500.0
  level_exponent: 1.15
  linear_bonus_per_level: 150
  sources:
    mob_kill: { enabled: true, award_xp: 2, cap_xp: 6000, period: DAILY, pool: MOB_KILL }
    diamond_ore: { enabled: true, award_xp: 20, cap_xp: 18000, period: DAILY, pool: ORE }
    weekly_activity: { enabled: true, award_xp: 1, cap_xp: 0, period: UNLIMITED, pool: WEEKLY_ACTIVITY }
```

Encode every default from design section 4, including all boss/recruit/pre-cap-war weekly caps. Load arbitrary supported vanilla material/entity identifiers into immutable sets. Reject a reload before replacing the current snapshot when any identifier, source, period, pool, award, or cap is invalid.

- [ ] **Step 4: Run config tests**

Run: `./gradlew test --tests '*ProgressionSourceConfigTest' --tests '*ConfigLoaderConsistencyTest'`

Expected: PASS and bundled defaults load without warnings.

- [ ] **Step 5: Commit**

```text
feat(config): ship chapter 2 XP sources
```

---

### Task 6: Route Existing Awards Through the Atomic Service

**Requirements:** REQ-049, REQ-089

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/ProgressionService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/QuestRewardSinkBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/di/Modules.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkitAwardTest.kt`

**Interfaces:**
- Consumes: `PermanentExperienceService.award` and config policy lookup.
- Produces: `ProgressionService.awardExperience(...)` compatibility facade for existing callers.
- Produces: `ProgressionService.awardUncappedSystemExperience(guildId, amount, source)` limited to `WEEKLY_ACTIVITY` and `ADMIN_BONUS`.

- [ ] **Step 1: Write failing facade tests**

```kotlin
@Test fun `positive source award delegates once to permanent service`() {
    service.awardExperience(guildId, 2, ExperienceSource.MOB_KILL)
    verify(exactly = 1) { permanent.award(match { it.guildId == guildId && it.units == 1 }, mobPolicy) }
}

@Test fun `weekly quest reward uses uncapped policy`() {
    sink.awardExperience(guildId, 25_000)
    verify { progression.awardUncappedSystemExperience(guildId, 25_000, ExperienceSource.WEEKLY_ACTIVITY) }
}
```

- [ ] **Step 2: Run the facade test and confirm red**

Run: `./gradlew test --tests '*ProgressionServiceBukkitAwardTest'`

Expected: failure because the uncapped method and delegate are absent.

- [ ] **Step 3: Delegate positive awards and wire dependencies**

Bind `ExperienceAwardRepositorySQL`, `PermanentExperienceService`, UUID-based `PlaytimeActivityService`, and an injected `ProgressionEventListener`. Keep query/perk methods on `ProgressionServiceBukkit`. Reject negative values in the positive award method. Preserve legacy removal methods only for the existing strike subsystem, clearly separating them from Chapter 2 ordinary progression.

- [ ] **Step 4: Run service, quest, DI, and architecture tests**

Run: `./gradlew test --tests '*ProgressionServiceBukkitAwardTest' --tests '*QuestServiceTest' --tests '*LayerRulesTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
refactor(progression): route awards atomically
```

---

### Task 7: Translate Player Activity and Enforce Provenance

**Requirements:** REQ-049, REQ-089

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/ProgressionEventListener.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/persistence/BlockProvenanceRepository.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/BlockProvenanceRepositorySQLite.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/listeners/ProgressionEventListenerTest.kt`
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/BlockProvenanceRepositorySQLiteTest.kt`

**Interfaces:**
- Consumes: validated config snapshot and `PermanentExperienceService`.
- Produces typed award requests for PvP, mobs/bosses, natural blocks/ores, placement, mature crops, crafting tiers, smelting, brewing, fishing, enchanting, and exploration milestones.
- Uses persisted `BlockPosition` provenance rather than Bukkit metadata.

- [ ] **Step 1: Write failing listener acceptance tests**

```kotlin
@Test fun `creative and spectator events never reach award service`() { /* mock both game modes; verify zero calls */ }
@Test fun `placed diamond ore never earns natural ore XP`() { /* provenance=true; verify zero calls */ }
@Test fun `natural diamond ore maps to diamond policy`() { /* provenance=false; verify source DIAMOND_ORE and units 1 */ }
@Test fun `ender dragon maps to boss weekly source without dimension rule`() { /* verify ENDER_DRAGON_KILL */ }
@Test fun `shift crafting uses actual produced item count`() { /* verify tier source and exact units */ }
@Test fun `immature crop earns nothing`() { /* age below maximum; verify zero calls */ }
```

The test bodies construct MockK Paper events/blocks/entities and verify the exact `ExperienceAwardRequest`; they must not start a server.

- [ ] **Step 2: Run listener and provenance tests and confirm red**

Run: `./gradlew test --tests '*ProgressionEventListenerTest' --tests '*BlockProvenanceRepositorySQLiteTest'`

Expected: at least the persistent-provenance and new-source cases fail.

- [ ] **Step 3: Replace metadata checks with persistent provenance and typed translation**

Constructor-inject dependencies. At `MONITOR` with `ignoreCancelled=true`, return immediately for creative/spectator. On placement, persist `BlockPosition` before requesting placement XP. On break, query provenance; player-placed blocks never map to natural mining and their provenance row is removed only after the break is accepted. Map `Material`/`EntityType` through validated pools; unknown targets produce no request.

Retain batching only as a transport optimization. Batch keys must include guild and `ExperienceSource`; the atomic repository remains the authority on remaining allowance. Do not apply any hidden mob location condition.

- [ ] **Step 4: Run listener, provenance, playtime, quest-listener, and architecture tests**

Run: `./gradlew test --tests '*ProgressionEventListenerTest' --tests '*BlockProvenanceRepositorySQLiteTest' --tests '*PlaytimeActivityServiceBukkitTest' --tests '*QuestProgressListener*' --tests '*LayerRulesTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
feat(progression): translate eligible activity
```

---

### Task 8: Add Net-New Bank, Qualified Recruit, and Pre-Cap War Awards

**Requirements:** REQ-049, REQ-089

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/listeners/ProgressionEventListener.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/BankServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/WarServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/persistence/MembershipHistoryRepository.kt`
- Modify: the matching SQLite repository and migrations selected by the existing membership-history implementation.
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/ChapterTwoGuildAwardTest.kt`

**Interfaces:**
- Consumes: `PermanentExperienceService` and policies from prior tasks.
- Produces: bank net-new-value unit calculation, seven-day retained recruit qualification, and level-below-100 war-win award.

- [ ] **Step 1: Write failing guild-award tests**

```kotlin
@Test fun `withdraw and redeposit does not create net-new bank XP`() { /* 10k deposit, 10k withdraw, 10k redeposit => only first net increase */ }
@Test fun `recruit awards after seven retained days exactly once`() { /* advance fake clock; assert 1000 XP once */ }
@Test fun `level one hundred war win does not award permanent war XP`() { /* permanent level=100; verify zero PRE_CAP_WAR_WIN */ }
@Test fun `level ninety nine war win requests ten thousand XP`() { /* verify PRE_CAP_WAR_WIN policy */ }
```

- [ ] **Step 2: Run the guild-award test and confirm red**

Run: `./gradlew test --tests '*ChapterTwoGuildAwardTest'`

Expected: the old deposit-event-volume, immediate-member-join, and unconditional war paths fail.

- [ ] **Step 3: Implement the three eligibility rules**

Track each period's bank high-water net value, not deposit volume. Store a recruit qualification timestamp and awarded marker tied to `(guild_id, player_id, joined_at)`; a scheduled/application check awards only after seven continuous days. At war resolution, query permanent level and request `PRE_CAP_WAR_WIN` only below 100. Boss awards remain player-activity requests from Task 7.

- [ ] **Step 4: Run guild services and regression tests**

Run: `./gradlew test --tests '*ChapterTwoGuildAwardTest' --tests '*BankConfigEnforcementTest' --tests '*WarConfigEnforcementTest' --tests '*MembershipHistory*'`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
feat(progression): qualify guild-wide awards
```

---

### Task 9: Expose Accurate Cap and Progress Read Models

**Requirements:** REQ-049, REQ-089

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/ProgressionService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ProgressionServiceBukkit.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/placeholders/LumaGuildsExpansion.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildProgressionMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildProgressionInfoMenu.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/application/services/ProgressionReadModelTest.kt`

**Interfaces:**
- Produces: `SourceUsageView(source, pool, period, awardedXp, capXp, remainingXp, resetsAt)`.
- Produces: `ProgressionService.getSourceUsage(guildId, at): List<SourceUsageView>`.
- Produces placeholders `permanent_level`, `permanent_xp`, `permanent_xp_to_next`, `source_<name>_used`, `source_<name>_remaining` with safe zero/empty fallback.

- [ ] **Step 1: Write failing read-model tests**

```kotlin
@Test fun `shared ore sources show one shared allowance`() {
    val views = service.getSourceUsage(guildId, instant)
    assertEquals(18_000, views.single { it.pool == "ORE" }.capXp)
    assertEquals(17_950, views.single { it.pool == "ORE" }.remainingXp)
}

@Test fun `weekly activity is shown as unlimited`() {
    assertEquals(null, views.single { it.source == ExperienceSource.WEEKLY_ACTIVITY }.remainingXp)
}
```

- [ ] **Step 2: Run read-model tests and confirm red**

Run: `./gradlew test --tests '*ProgressionReadModelTest'`

Expected: failure because current methods only expose hard-coded daily source maps.

- [ ] **Step 3: Implement one read model for Java, Bedrock, and placeholders**

Read usage by pool/window from `ExperienceAwardRepository`; do not reconstruct it by summing prunable audit transactions. Replace `getDailySourceXp`/`getDailyCap` consumers with the new view while preserving deprecated facade methods until all internal callers migrate. Placeholder evaluation performs reads only and returns safe fallback for missing guild/context.

- [ ] **Step 4: Run menu, placeholder, and read-model tests**

Run: `./gradlew test --tests '*ProgressionReadModelTest' --tests '*GuildProgressionMenu*' --tests '*LumaGuildsExpansion*'`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
feat(progression): expose source allowances
```

---

### Task 10: Verify Weekly Quests, Full Regression, and Task Evidence

**Requirements:** REQ-049, REQ-089

**Files:**
- Modify: `src/test/kotlin/net/lumalyte/lg/application/services/QuestServiceTest.kt`
- Modify: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/QuestRewardSinkBukkitTest.kt` if present; otherwise create it.
- Modify: `docs/tasks.md`

**Interfaces:**
- Verifies: weekly milestone, leaderboard, and complete-all XP use `WEEKLY_ACTIVITY`, do not reserve daily cap, and remain idempotent.
- Completes: LG-1201 only; LG-1202, LG-1203, and LG-1205 remain open.

- [ ] **Step 1: Add the final acceptance tests**

```kotlin
@Test fun `weekly quest reward bypasses all source caps`() {
    exhaustEveryCappedPool(guildId)
    sink.awardExperience(guildId, 50_000)
    assertEquals(50_000, progression(guildId).totalExperience)
    assertTrue(sourceUsage(guildId).all { it.awardedXp == it.seededXp })
}

@Test fun `replaying claimed quest does not duplicate XP`() {
    claimSameQuestTwice()
    assertEquals(25_000, weeklyTransactions(guildId).sumOf { it.amount })
}
```

- [ ] **Step 2: Run focused acceptance tests**

Run: `./gradlew test --tests '*QuestServiceTest' --tests '*QuestRewardSinkBukkitTest' --tests '*PermanentExperienceServiceTest' --tests '*ExperienceAwardRepositorySQLTest'`

Expected: PASS.

- [ ] **Step 3: Run full SPEAR verification**

Run: `./gradlew clean test`

Expected: BUILD SUCCESSFUL with all unit, repository, migration, configuration, listener, and architecture tests green.

- [ ] **Step 4: Inspect the final diff and configuration**

Run: `git diff --check && git status --short && git diff --stat`

Expected: no whitespace errors, no generated build output, and changes limited to LG-1201 files/tests/docs.

- [ ] **Step 5: Record evidence and complete the task**

Change LG-1201 from `[~]` to `[x]` and record:

```text
Evidence: Chapter Two curve/source/application/SQL/listener/config/read-model tests; LayerRulesTest; full `./gradlew clean test` BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```text
test(progression): prove chapter 2 XP model
```

## Self-Review Record

- Spec coverage: permanent curve, independent guild-wide source caps, no per-player/combined cap, weekly bypass, AFK/suspicious ordering, creative/spectator/cancelled rejection, placed-block provenance, configurable vanilla pools, all shipped default source families, read models, and task evidence are assigned to Tasks 1–10.
- Deferred by deliberate plan split: permanent reward catalog (LG-1202), seasonal Elo (LG-1203), and chapter migration/rollover (LG-1205).
- Type consistency: `ExperiencePolicy`, `ExperienceAwardRequest`, `ExperienceAwardResult`, `ExperienceAwardRepository`, `PermanentExperienceService`, and `SourceUsageView` retain the same names/signatures across producing and consuming tasks.
- Placeholder scan: no unresolved implementation markers or unspecified error-handling steps remain.
