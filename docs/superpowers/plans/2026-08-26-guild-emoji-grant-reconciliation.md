# Guild Emoji Grant Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make configured guild emoji permissions grant and revoke correctly across startup, reload, membership changes, rename, disband, and restart without touching permissions LumaGuilds does not own.

**Architecture:** Persist every LumaGuilds-owned grant in a SQLite ledger behind an application repository port. A Bukkit-free application reconciler compares desired membership/config state with that ledger and applies ordered revoke/grant operations through a permission gateway; infrastructure adapters provide SQLite, LuckPerms command dispatch, config access, and Bukkit lifecycle events.

**Tech Stack:** Kotlin 2.2, Paper 1.21.11, SQLite via Aikar IDB, Koin 4.0.2, MockBukkit 4.107.0, JUnit 5, MockK

**Spec:** `docs/superpowers/specs/2026-08-26-guild-emoji-grant-reconciliation-design.md`

## Global Constraints

- Follow SPEAR for every behavior: requirement → failing test → minimal code → layer check → refactor and re-run.
- Domain imports no Bukkit, LuckPerms, SQL, Koin, Adventure, or config types.
- Guild names are matched case-insensitively with `Locale.ROOT`.
- Permission nodes are trimmed, lowercase, and match `[a-z0-9][a-z0-9_.-]*`.
- Each `(playerId, guildId)` has at most one LumaGuilds-owned permission.
- Never revoke an unrecorded permission.
- Replace A with B in order: revoke A, delete A's ledger row, grant B, record B.
- A failed revoke retains A; a failed grant records nothing.
- No admin command, GUI, Nexo glyph creation, or multi-permission mapping is included.

---

## File Map

New production files:

- `domain/entities/EmojiPermissionGrant.kt` — immutable owned grant.
- `application/persistence/EmojiGrantRepository.kt` — ownership ledger port.
- `application/services/EmojiPermissionGateway.kt` — external side-effect port.
- `application/services/GuildEmojiGrantReconciler.kt` — reconciliation algorithm.
- `infrastructure/persistence/guilds/EmojiGrantRepositorySQLite.kt` — SQLite ledger.
- `infrastructure/services/LuckPermsEmojiPermissionGateway.kt` — Bukkit command adapter.
- `api/events/GuildRenamedEvent.kt` — committed rename event.

Modified production files:

- `ConfigServiceBukkit.kt`, `config.yml` — mapping validation and operator copy.
- `GuildEmojiGrantService.kt` — config-aware façade.
- `GuildEmojiGrantListener.kt` — lifecycle routing.
- `GuildServiceBukkit.kt` — rename event emission.
- `LumaGuildsCommand.kt`, `LumaGuilds.kt`, `Modules.kt` — reload/startup/DI wiring.
- `docs/EMOJI_PERMISSIONS.md`, `docs/tasks.md` — operator and SPEAR evidence.

---

### Task 1: Persist LumaGuilds-owned grants

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/domain/entities/EmojiPermissionGrant.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/application/persistence/EmojiGrantRepository.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/EmojiGrantRepositorySQLite.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/EmojiGrantRepositorySQLiteTest.kt`

**Interfaces:**
- Produces: `EmojiPermissionGrant(playerId: UUID, guildId: UUID, permission: String)`.
- Produces: `getAll`, `getForGuild`, `getForPlayerAndGuild`, `upsert`, and `delete` ledger operations.
- Consumes: `Storage<Database>` and `VirtualThreadSQLiteStorage`.

- [ ] **Step 1: Write the failing repository tests**

Use `@TempDir` and real SQLite. Add these literal behaviors:

```kotlin
@Test
fun `owned grant survives repository restart`() {
    val grant = EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.badger")
    assertTrue(repository.upsert(grant))
    val secondStorage = VirtualThreadSQLiteStorage(tempDir.toFile())
    try {
        assertEquals(
            grant,
            EmojiGrantRepositorySQLite(secondStorage)
                .getForPlayerAndGuild(playerId, guildId),
        )
    } finally {
        secondStorage.connection.close()
    }
}

@Test
fun `upsert replaces one memberships permission`() {
    repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.old"))
    repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.new"))
    assertEquals("enthusia.emoji.new", repository.getForPlayerAndGuild(playerId, guildId)?.permission)
    assertEquals(1, repository.getAll().size)
}

@Test
fun `delete preserves other guild members`() {
    repository.upsert(EmojiPermissionGrant(playerId, guildId, "enthusia.emoji.badger"))
    repository.upsert(EmojiPermissionGrant(otherPlayerId, guildId, "enthusia.emoji.badger"))
    assertTrue(repository.delete(playerId, guildId))
    assertNull(repository.getForPlayerAndGuild(playerId, guildId))
    assertNotNull(repository.getForPlayerAndGuild(otherPlayerId, guildId))
}
```

- [ ] **Step 2: Run RED**

Run `./gradlew test --tests net.lumalyte.lg.infrastructure.persistence.guilds.EmojiGrantRepositorySQLiteTest`.

Expected: compilation fails because the entity, port, and adapter do not exist.

- [ ] **Step 3: Add the domain value and port**

```kotlin
data class EmojiPermissionGrant(
    val playerId: UUID,
    val guildId: UUID,
    val permission: String,
)

interface EmojiGrantRepository {
    fun getAll(): List<EmojiPermissionGrant>
    fun getForGuild(guildId: UUID): List<EmojiPermissionGrant>
    fun getForPlayerAndGuild(playerId: UUID, guildId: UUID): EmojiPermissionGrant?
    fun upsert(grant: EmojiPermissionGrant): Boolean
    fun delete(playerId: UUID, guildId: UUID): Boolean
}
```

- [ ] **Step 4: Add the SQLite adapter**

Create `guild_emoji_grants_applied(player_id TEXT, guild_id TEXT, permission TEXT, PRIMARY KEY(player_id, guild_id))` and an index on `guild_id`. Preload a map keyed by `playerId to guildId`. Use parameterized `INSERT OR REPLACE` and `DELETE`; mutate the cache only after a successful database write. Throw `DatabaseOperationException` for schema/preload failure and return `false` with logging for mutation failure.

- [ ] **Step 5: Run GREEN and layer checks**

Run `./gradlew test --tests net.lumalyte.lg.infrastructure.persistence.guilds.EmojiGrantRepositorySQLiteTest --tests net.lumalyte.lg.architecture.LayerRulesTest`.

- [ ] **Step 6: Commit**

```text
feat(emoji): persist managed grants
```

---

### Task 2: Validate the config mapping

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/ConfigServiceBukkit.kt`
- Modify: `src/main/resources/config.yml`
- Modify: `src/test/kotlin/net/lumalyte/lg/config/ConfigLoaderConsistencyTest.kt`

**Interfaces:**
- Produces: normalized `MainConfig.guild.emojiGrants: Map<String, String>`.
- Consumes: existing `loadEmojiGrantsConfig()`.

- [ ] **Step 1: Add failing loader cases**

```kotlin
config.set("guild.emoji_grants.Badgers", "  Enthusia.Emoji.Badger  ")
config.set("guild.emoji_grants.Blank", "   ")
config.set("guild.emoji_grants.Injected", "permission true\nlp user attacker permission set * true")

val grants = service.loadConfig().guild.emojiGrants
assertEquals("enthusia.emoji.badger", grants["badgers"])
assertFalse(grants.containsKey("blank"))
assertFalse(grants.containsKey("injected"))
```

- [ ] **Step 2: Run RED**

Run `./gradlew test --tests net.lumalyte.lg.config.ConfigLoaderConsistencyTest`.

Expected: mixed-case values are not normalized and the injected value is accepted.

- [ ] **Step 3: Implement minimal validation**

In `loadEmojiGrantsConfig`, trim and lowercase keys/values using `Locale.ROOT`; accept only values matching `Regex("^[a-z0-9][a-z0-9_.-]*$")`. Ignore blank/invalid entries and log their guild key without logging executable text. Let the last duplicate normalized guild key win and log the collision.

- [ ] **Step 4: Update `config.yml` comments**

Document exact validation, case-insensitive names, one node per guild, and that `/lumaguilds reload` applies changes.

- [ ] **Step 5: Run GREEN**

Run `./gradlew test --tests net.lumalyte.lg.config.ConfigLoaderConsistencyTest --tests net.lumalyte.lg.infrastructure.i18n.LocaleContractTest`.

- [ ] **Step 6: Commit**

```text
fix(config): validate emoji grants
```

---

### Task 3: Build the reconciliation engine

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/EmojiPermissionGateway.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/application/services/GuildEmojiGrantReconciler.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/application/services/GuildEmojiGrantReconcilerTest.kt`

**Interfaces:**
- Consumes: `GuildService`, `MemberService`, `EmojiGrantRepository`.
- Produces: `EmojiPermissionGateway.grant(UUID, String): Boolean` and `revoke(UUID, String): Boolean`.
- Produces: `EmojiGrantReconciliationResult(granted: Int, revoked: Int, failed: Int)` with `successful = failed == 0`.
- Produces: `reconcileAll(Map<String, String>)`, `reconcileMembership`, `removeMembership`, `reconcileGuild`, and `removeGuild`.

- [ ] **Step 1: Write RED initial-state tests using real fakes**

The in-memory gateway records ordered strings; the in-memory ledger stores real `EmojiPermissionGrant` values.

```kotlin
@Test
fun `initial mapping grants every current member and records ownership`() {
    val result = reconciler.reconcileAll(mapOf("badgers" to "enthusia.emoji.badger"))
    assertEquals(setOf(playerOne, playerTwo), ledger.getForGuild(guildId).map { it.playerId }.toSet())
    assertEquals(2, result.granted)
    assertEquals(0, result.failed)
}

@Test
fun `unrecorded matching permission is never revoked`() {
    gateway.externallyOwned += playerOne to "enthusia.emoji.badger"
    reconciler.reconcileAll(emptyMap())
    assertEquals(emptyList<String>(), gateway.operations)
}
```

- [ ] **Step 2: Run RED**

Run `./gradlew test --tests net.lumalyte.lg.application.services.GuildEmojiGrantReconcilerTest`.

Expected: compilation fails because the gateway, result, and reconciler do not exist.

- [ ] **Step 3: Implement desired-state addition/removal**

Build desired entries from current guilds and members with normalized guild names. Compare only against repository rows. Revoke recorded entries absent from desired; grant desired entries absent from recorded. Continue after per-player failures and return exact counters. Serialize all public operations with one `ReentrantLock`.

- [ ] **Step 4: Add RED replacement tests**

```kotlin
@Test
fun `replacement revokes A before granting B`() {
    ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.a"))
    reconciler.reconcileAll(mapOf("badgers" to "enthusia.emoji.b"))
    assertEquals(
        listOf("revoke:$playerOne:enthusia.emoji.a", "grant:$playerOne:enthusia.emoji.b"),
        gateway.operations,
    )
    assertEquals("enthusia.emoji.b", ledger.getForPlayerAndGuild(playerOne, guildId)?.permission)
}

@Test
fun `config removal revokes and deletes ownership`() {
    ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.badger"))
    reconciler.reconcileAll(emptyMap())
    assertNull(ledger.getForPlayerAndGuild(playerOne, guildId))
}
```

- [ ] **Step 5: Run RED, then implement ordered replacement**

For a differing pair: revoke recorded permission; on success delete its row; grant desired permission; on success upsert desired. If ledger upsert fails after a grant, immediately compensate with a revoke. Do not add background retries.

- [ ] **Step 6: Add RED failure/retry tests**

Prove revoke failure retains A, grant failure leaves no B row, ledger-upsert failure triggers compensating revoke, and a second reconciliation retries retained work.

- [ ] **Step 7: Implement only those failure paths and run GREEN**

Run `./gradlew test --tests net.lumalyte.lg.application.services.GuildEmojiGrantReconcilerTest --tests net.lumalyte.lg.architecture.LayerRulesTest`.

- [ ] **Step 8: Add RED narrow lifecycle tests**

Prove `reconcileMembership` grants on join; `removeMembership` revokes from the ledger without config; `reconcileGuild` replaces permissions for every current member and removes former-member rows; `removeGuild` revokes ledger rows after the guild record is gone.

- [ ] **Step 9: Reuse the single-entry transition to implement lifecycle methods, then run GREEN**

Do not duplicate replacement logic. Callers pass a resolved permission or `null`; the application reconciler never loads Bukkit config.

- [ ] **Step 10: Commit**

```text
feat(emoji): reconcile managed grants
```

---

### Task 4: Wire Bukkit lifecycle and permission dispatch

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/LuckPermsEmojiPermissionGateway.kt`
- Create: `src/main/kotlin/net/lumalyte/lg/api/events/GuildRenamedEvent.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/LuckPermsEmojiPermissionGatewayTest.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/infrastructure/listeners/GuildEmojiGrantListenerTest.kt`
- Create: `src/test/kotlin/net/lumalyte/lg/api/events/GuildRenamedEventTest.kt`
- Modify: `GuildEmojiGrantService.kt`, `GuildEmojiGrantListener.kt`, `GuildServiceBukkit.kt`, `LumaGuildsCommand.kt`, `Modules.kt`, and `LumaGuilds.kt`.
- Modify: `GuildEventApiContractTest.kt` and `KoinGraphSmokeTest.kt`.

**Interfaces:**
- Consumes: Tasks 1–3 repository, gateway port, and reconciler.
- Produces: config-aware façade methods `reconcileAll`, `onMemberJoined`, `onMemberRemoved`, `onGuildCreated`, `onGuildRenamed`, and `onGuildDisbanded`.
- Produces: `GuildRenamedEvent(guildId: UUID, oldName: String, newName: String)`.

- [ ] **Step 1: Write RED gateway safety tests**

Using MockBukkit command capture, assert exact valid commands:

```text
lp user <uuid> permission set enthusia.emoji.badger true
lp user <uuid> permission unset enthusia.emoji.badger
```

Invalid nodes return false and dispatch nothing.

- [ ] **Step 2: Implement gateway and run GREEN**

Dispatch directly when `Bukkit.isPrimaryThread()`. Off-thread, submit to the scheduler and wait at most five seconds for the Boolean result; timeout/interruption returns false. Validate UUID and permission before constructing commands.

- [ ] **Step 3: Write RED façade/listener tests**

Assert join resolves the current mapping and reconciles one membership; removal calls `removeMembership` without config; creation reconciles the guild; rename resolves the new name and calls `reconcileGuild`; disband calls `removeGuild`; global reload passes the freshly loaded map to `reconcileAll`.

- [ ] **Step 4: Replace ad-hoc grant/revoke code with the façade**

Delete direct command construction from `GuildEmojiGrantService`. Keep all config lookup in this infrastructure façade and route every listener event to one narrow reconciler method.

- [ ] **Step 5: Write RED rename-event tests**

Add the event to `GuildEventApiContractTest`. Prove `renameGuild` emits exactly once after successful repository update and never on validation, permission, collision, or repository failure.

- [ ] **Step 6: Implement `GuildRenamedEvent` and emission**

Follow the existing `GuildCreatedEvent` HandlerList structure. Carry literal old/new names and guild ID.

- [ ] **Step 7: Wire Koin, startup, and reload**

Bind `EmojiGrantRepository`, `EmojiPermissionGateway`, `GuildEmojiGrantReconciler`, façade, and listener. Replace startup `grantAll()` with `reconcileAll()`. After `reloadConfig()` and `initConfig()`, invoke reconciliation and report/log an unsuccessful result.

- [ ] **Step 8: Run focused integration GREEN**

Run `./gradlew test --tests net.lumalyte.lg.infrastructure.services.LuckPermsEmojiPermissionGatewayTest --tests net.lumalyte.lg.infrastructure.listeners.GuildEmojiGrantListenerTest --tests net.lumalyte.lg.api.events.GuildRenamedEventTest --tests net.lumalyte.lg.api.events.GuildEventApiContractTest --tests net.lumalyte.lg.di.KoinGraphSmokeTest`.

- [ ] **Step 9: Commit**

```text
feat(emoji): wire grant reconciliation
```

---

### Task 5: Documentation, evidence, and final gates

**Files:**
- Modify: `docs/EMOJI_PERMISSIONS.md`
- Modify: `docs/tasks.md`

- [ ] **Step 1: Update operator documentation**

Document exact YAML, validation, case-insensitive matching, startup/reload reconciliation, join/leave/rename/disband behavior, mapping removal, A→B replacement, and the difference between displayed emoji selection (LG-1103) and permission ownership (LG-1104).

- [ ] **Step 2: Run the focused acceptance suite**

Run all new repository, reconciler, gateway, listener, rename-event, config, architecture, and Koin tests in one Gradle invocation. Expected: zero failures/errors.

- [ ] **Step 3: Run the complete clean suite**

Run `./gradlew clean test`. Record the exact test/failure/error counts from XML.

- [ ] **Step 4: Complete SPEAR tracking**

Change LG-1104 from `[~]` to `[x]`. Record the exact count and name the reconciler, ledger, gateway, lifecycle integration, config validation, and regression tests.

- [ ] **Step 5: Verify the diff**

Run `git diff --check`, `git status --short`, and `git diff --stat origin/main...HEAD`. Expected: no whitespace errors and only LG-1104 files.

- [ ] **Step 6: Commit documentation**

```text
docs(emoji): document managed grants
```

- [ ] **Step 7: Request independent review**

Review `origin/main...HEAD`. Resolve every Critical and Important finding through a new red-green cycle, then rerun `./gradlew clean test` after the final code change.
