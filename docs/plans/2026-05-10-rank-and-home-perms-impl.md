# Rank Priority & Per-Home Access Permissions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three bundled guild features: (1) per-home rank-whitelist gating teleport access, (2) ally-home permissions with per-rank outbound and per-ally-guild inbound gating, (3) rank priority reordering via ▲/▼ buttons in RankEditMenu.

**Architecture:** Schema migration v20 adds `allowed_ranks` to the existing `guild_homes` table and `ally_home_allowed_guilds` to the `guilds` table. Domain entities `GuildHome` and `Guild` gain matching fields. A new `RankPermission.USE_ALLY_HOMES` governs outbound ally-home use. Access checks live in `GuildService`; priority swap atomicity in `RankService` via a temporary-priority three-step UPDATE. UI additions to `RankEditMenu` (priority buttons) and `GuildHomeMenu` (Access buttons opening new `HomeAccessMenu` / `AllyHomeAccessMenu`).

**Tech Stack:** Kotlin, Paper/Bukkit 1.21, ACF (commands), Koin (DI), aikar/idb SQLite via `storage.connection.executeUpdate` / `getResults`, InventoryFramework `ChestGui`/`StaticPane`, JUnit5 + MockK + MockBukkit.

**Spec reference:** [`docs/superpowers/specs/2026-05-10-rank-and-home-perms-design.md`](../superpowers/specs/2026-05-10-rank-and-home-perms-design.md)

**Layer rules reminder:** Domain imports nothing from application/infrastructure. Application imports only domain. Infrastructure imports all three. See `docs/architecture.md`.

---

## Task 1: Add `USE_ALLY_HOMES` to `RankPermission` enum

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/domain/entities/Rank.kt`

- [ ] **Step 1: Add the enum value**

Insert under the existing `// Relations & Diplomacy` block (after `ACCEPT_PARTY_INVITES`):

```kotlin
    ACCEPT_PARTY_INVITES,
    USE_ALLY_HOMES,
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/domain/entities/Rank.kt
git commit -m "feat(perms): add USE_ALLY_HOMES rank permission"
```

---

## Task 2: Extend `GuildHome` with `allowedRankIds`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/domain/entities/Guild.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/domain/entities/GuildHomeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/net/lumalyte/lg/domain/entities/GuildHomeTest.kt`:

```kotlin
package net.lumalyte.lg.domain.entities

import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class GuildHomeTest {

    @Test
    fun `GuildHome defaults allowedRankIds to empty set`() {
        val home = GuildHome(UUID.randomUUID(), Position3D(0, 64, 0))
        assertEquals(emptySet<UUID>(), home.allowedRankIds)
    }

    @Test
    fun `GuildHome stores supplied allowedRankIds`() {
        val rankA = UUID.randomUUID()
        val rankB = UUID.randomUUID()
        val home = GuildHome(
            worldId = UUID.randomUUID(),
            position = Position3D(0, 64, 0),
            allowedRankIds = setOf(rankA, rankB)
        )
        assertEquals(setOf(rankA, rankB), home.allowedRankIds)
    }
}
```

- [ ] **Step 2: Run the test, confirm it fails**

Run: `./gradlew test --tests "net.lumalyte.lg.domain.entities.GuildHomeTest"`
Expected: FAIL (compile error: no `allowedRankIds` parameter).

- [ ] **Step 3: Add field to `GuildHome`**

Find the `data class GuildHome(...)` declaration in [`Guild.kt`](../../src/main/kotlin/net/lumalyte/lg/domain/entities/Guild.kt) (around line 88). Replace:

```kotlin
data class GuildHome(
    val worldId: UUID,
    val position: Position3D
)
```

with:

```kotlin
data class GuildHome(
    val worldId: UUID,
    val position: Position3D,
    val allowedRankIds: Set<UUID> = emptySet()
)
```

- [ ] **Step 4: Re-run test, confirm pass**

Run: `./gradlew test --tests "net.lumalyte.lg.domain.entities.GuildHomeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/domain/entities/Guild.kt src/test/kotlin/net/lumalyte/lg/domain/entities/GuildHomeTest.kt
git commit -m "feat(home): add allowedRankIds whitelist to GuildHome"
```

---

## Task 3: Extend `Guild` with `allyHomeAllowedGuilds`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/domain/entities/Guild.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/domain/entities/GuildTest.kt`

- [ ] **Step 1: Append failing test to `GuildTest.kt`**

Add inside the existing `class GuildTest`:

```kotlin
    @Test
    fun `Guild allyHomeAllowedGuilds defaults to empty`() {
        val guild = Guild(id = UUID.randomUUID(), name = "X", createdAt = Instant.now())
        assertEquals(emptySet<UUID>(), guild.allyHomeAllowedGuilds)
    }

    @Test
    fun `Guild stores supplied allyHomeAllowedGuilds`() {
        val allyId = UUID.randomUUID()
        val guild = Guild(
            id = UUID.randomUUID(),
            name = "X",
            createdAt = Instant.now(),
            allyHomeAllowedGuilds = setOf(allyId)
        )
        assertEquals(setOf(allyId), guild.allyHomeAllowedGuilds)
    }
```

- [ ] **Step 2: Run, confirm fail**

Run: `./gradlew test --tests "net.lumalyte.lg.domain.entities.GuildTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Add field to `Guild`**

In `Guild.kt`, add `val allyHomeAllowedGuilds: Set<UUID> = emptySet()` as the last constructor parameter of `data class Guild(...)` (after `allyHome`).

```kotlin
data class Guild(
    // ... existing fields ...
    val allyHome: GuildHome? = null,
    val allyHomeAllowedGuilds: Set<UUID> = emptySet()
) {
```

- [ ] **Step 4: Run, confirm pass**

Run: `./gradlew test --tests "net.lumalyte.lg.domain.entities.GuildTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/domain/entities/Guild.kt src/test/kotlin/net/lumalyte/lg/domain/entities/GuildTest.kt
git commit -m "feat(ally-home): add allyHomeAllowedGuilds whitelist to Guild"
```

---

## Task 4: Schema migration v20

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt`

- [ ] **Step 1: Add the v20 gate in `migrate()`**

After the v19 block (around line 115), insert:

```kotlin
            if (dbVersion < 20) {
                migrateToVersion20()
                updateDatabaseVersion(20)
                dbVersion = 20
            }
```

- [ ] **Step 2: Implement `migrateToVersion20()`**

Add new private function (place after `migrateToVersion19()` around line 1281):

```kotlin
    /**
     * v20: per-home rank whitelist + per-ally-guild inbound ally-home whitelist + USE_ALLY_HOMES permission backfill.
     *
     * Adds `guild_homes.allowed_ranks` (TEXT, CSV of rank UUIDs) and
     * `guilds.ally_home_allowed_guilds` (TEXT, CSV of guild UUIDs).
     * Backfills existing homes with all current ranks of the owning guild (policy B,
     * see 2026-05-10-rank-and-home-perms-design.md §2.3), and adds USE_ALLY_HOMES
     * to every existing rank's permissions (§3.1).
     *
     * Idempotent: ALTER TABLE wrapped in try/catch for "duplicate column", and
     * backfill UPDATE only writes rows where the new columns are NULL.
     */
    private fun migrateToVersion20() {
        componentLogger.info(Component.text("Migrating to version 20: per-home access perms..."))

        // 1. Add columns (idempotent)
        for (alter in listOf(
            "ALTER TABLE guild_homes ADD COLUMN allowed_ranks TEXT",
            "ALTER TABLE guilds ADD COLUMN ally_home_allowed_guilds TEXT"
        )) {
            try {
                connection.createStatement().use { it.executeUpdate(alter) }
            } catch (e: SQLException) {
                if (!e.message.orEmpty().contains("duplicate column", ignoreCase = true)) throw e
            }
        }

        // 2. Backfill guild_homes.allowed_ranks = CSV of rank IDs for each guild
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT gh.guild_id, GROUP_CONCAT(r.id, ',') AS rank_csv " +
                "FROM guild_homes gh JOIN ranks r ON r.guild_id = gh.guild_id " +
                "WHERE gh.allowed_ranks IS NULL GROUP BY gh.guild_id"
            )
            val updates = mutableListOf<Pair<String, String>>()
            while (rs.next()) {
                updates.add(rs.getString("guild_id") to (rs.getString("rank_csv") ?: ""))
            }
            rs.close()
            val updateStmt = connection.prepareStatement(
                "UPDATE guild_homes SET allowed_ranks = ? WHERE guild_id = ? AND allowed_ranks IS NULL"
            )
            for ((guildId, csv) in updates) {
                updateStmt.setString(1, csv)
                updateStmt.setString(2, guildId)
                updateStmt.executeUpdate()
            }
            updateStmt.close()
            componentLogger.info(Component.text("✓ Backfilled ${updates.size} guild(s) of home rank whitelists"))
        }

        // 3. Backfill guilds.ally_home_allowed_guilds = CSV of current allies
        //    Relations table uses (guild_a, guild_b, type='ALLY') — collect both directions.
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT id FROM guilds WHERE ally_home_allowed_guilds IS NULL"
            )
            val guildIds = mutableListOf<String>()
            while (rs.next()) guildIds.add(rs.getString("id"))
            rs.close()

            val updateStmt = connection.prepareStatement(
                "UPDATE guilds SET ally_home_allowed_guilds = ? WHERE id = ?"
            )
            for (gid in guildIds) {
                val allies = mutableSetOf<String>()
                connection.prepareStatement(
                    "SELECT guild_a, guild_b FROM relations WHERE type = 'ALLY' AND (guild_a = ? OR guild_b = ?)"
                ).use { ps ->
                    ps.setString(1, gid); ps.setString(2, gid)
                    val r2 = ps.executeQuery()
                    while (r2.next()) {
                        val a = r2.getString("guild_a"); val b = r2.getString("guild_b")
                        if (a != gid) allies.add(a); if (b != gid) allies.add(b)
                    }
                }
                updateStmt.setString(1, allies.joinToString(","))
                updateStmt.setString(2, gid)
                updateStmt.executeUpdate()
            }
            updateStmt.close()
            componentLogger.info(Component.text("✓ Backfilled ${guildIds.size} guild(s) ally-home allow-lists"))
        }

        // 4. Add USE_ALLY_HOMES to every existing rank (idempotent — skips if already present)
        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT id, permissions FROM ranks")
            val updates = mutableListOf<Pair<String, String>>()
            while (rs.next()) {
                val id = rs.getString("id")
                val perms = rs.getString("permissions").orEmpty()
                val parts = perms.split(",").filter { it.isNotBlank() }.toMutableSet()
                if (parts.add("USE_ALLY_HOMES")) {
                    updates.add(id to parts.joinToString(","))
                }
            }
            rs.close()
            val updateStmt = connection.prepareStatement(
                "UPDATE ranks SET permissions = ? WHERE id = ?"
            )
            for ((id, perms) in updates) {
                updateStmt.setString(1, perms); updateStmt.setString(2, id)
                updateStmt.executeUpdate()
            }
            updateStmt.close()
            componentLogger.info(Component.text("✓ Added USE_ALLY_HOMES to ${updates.size} ranks"))
        }
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/migrations/SQLiteMigrations.kt
git commit -m "feat(db): v20 migration — home/ally access columns + USE_ALLY_HOMES backfill"
```

---

## Task 5: GuildRepository — persist `allowed_ranks` for homes

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildRepositorySQLite.kt`

- [ ] **Step 1: Update `createGuildHomesTable()` (around line 128)**

Change the `CREATE TABLE IF NOT EXISTS guild_homes` block to include the new column (for fresh installs):

```kotlin
        val sql = """
            CREATE TABLE IF NOT EXISTS guild_homes (
                guild_id TEXT NOT NULL,
                name TEXT NOT NULL,
                world_id TEXT NOT NULL,
                x INTEGER NOT NULL,
                y INTEGER NOT NULL,
                z INTEGER NOT NULL,
                allowed_ranks TEXT,
                PRIMARY KEY (guild_id, name),
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
        """.trimIndent()
```

- [ ] **Step 2: Update `loadAllGuildHomes()` (around line 225) to read `allowed_ranks`**

Replace the SELECT and home-construction block:

```kotlin
            val rows = storage.connection.getResults(
                "SELECT guild_id, name, world_id, x, y, z, allowed_ranks FROM guild_homes"
            )
            for (row in rows) {
                val guildId = UUID.fromString(row.getString("guild_id"))
                val homeName = row.getString("name")
                val worldId = UUID.fromString(row.getString("world_id"))
                val x = row.getInt("x")
                val y = row.getInt("y")
                val z = row.getInt("z")
                val allowedCsv = row.getString("allowed_ranks").orEmpty()
                val allowedRankIds = allowedCsv.split(",")
                    .filter { it.isNotBlank() }
                    .map { UUID.fromString(it.trim()) }
                    .toSet()
                val home = GuildHome(worldId, net.lumalyte.lg.domain.values.Position3D(x, y, z), allowedRankIds)
                byGuild.getOrPut(guildId) { mutableMapOf() }[homeName] = home
            }
```

- [ ] **Step 3: Update `writeGuildHomes()` (around line 256) to write `allowed_ranks`**

Replace the INSERT block:

```kotlin
            for ((homeName, home) in guild.homes.homes) {
                storage.connection.executeUpdate(
                    """
                    INSERT INTO guild_homes (guild_id, name, world_id, x, y, z, allowed_ranks)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    guild.id.toString(),
                    homeName,
                    home.worldId.toString(),
                    home.position.x,
                    home.position.y,
                    home.position.z,
                    home.allowedRankIds.joinToString(",") { it.toString() }
                )
            }
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildRepositorySQLite.kt
git commit -m "feat(db): persist GuildHome.allowedRankIds via guild_homes.allowed_ranks"
```

---

## Task 6: GuildRepository — persist `ally_home_allowed_guilds`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildRepositorySQLite.kt`

- [ ] **Step 1: Add fresh-install column to `migrateAllyHomeColumns()` (around line 182)**

Replace the `columns` list with:

```kotlin
        val columns = listOf(
            "ally_home_world TEXT",
            "ally_home_x INTEGER",
            "ally_home_y INTEGER",
            "ally_home_z INTEGER",
            "ally_home_allowed_guilds TEXT"
        )
```

- [ ] **Step 2: Update `mapResultSetToGuild` to read the column**

Find the line that constructs `Guild(...)` (around line 416 — the one with `allyHome = allyHome`). Locate it by searching `allyHome = allyHome` in the file. Above that constructor call, add:

```kotlin
        val allyHomeAllowedGuildsCsv = try { rs.getString("ally_home_allowed_guilds").orEmpty() } catch (_: SQLException) { "" }
        val allyHomeAllowedGuilds = allyHomeAllowedGuildsCsv.split(",")
            .filter { it.isNotBlank() }
            .map { UUID.fromString(it.trim()) }
            .toSet()
```

Then add `allyHomeAllowedGuilds = allyHomeAllowedGuilds` to the `Guild(...)` constructor argument list (after `allyHome = allyHome`).

- [ ] **Step 3: Update every INSERT/UPDATE SQL that writes guild rows**

Search the file for `ally_home_z` in column lists. For each INSERT/UPDATE that includes ally_home columns, append `ally_home_allowed_guilds` to the column list and a corresponding `?` placeholder, then pass `guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() }` as the last argument.

Example for the UPDATE (typically around line 540):

```kotlin
            UPDATE guilds SET name = ?, ..., ally_home_world = ?, ally_home_x = ?, ally_home_y = ?, ally_home_z = ?, ally_home_allowed_guilds = ?
            WHERE id = ?
```

And in the argument list, after the existing ally_home position args:

```kotlin
                    guild.allyHomeAllowedGuilds.joinToString(",") { it.toString() },
                    guild.id.toString()
```

Apply the same pattern to all 5 INSERT branches (lines ~447, ~452, ~457, ~462, ~467) and the UPDATE branch.

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildRepositorySQLite.kt
git commit -m "feat(db): persist Guild.allyHomeAllowedGuilds"
```

---

## Task 7: New-home defaults — Owner-only whitelist

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt`

- [ ] **Step 1: Locate `setHome` (around line 314), find where it constructs the new GuildHome to insert**

The current code likely accepts `home: GuildHome` parameter directly from the caller. New homes created via `/guild sethome` should default `allowedRankIds = setOf(ownerRankId)` per spec §2.3.

- [ ] **Step 2: Inject default Owner-only allowed-rank when adding a new home**

In `setHome(guildId, homeName, home, actorId)`, immediately after the existing permission check and before persisting, locate `guild.copy(homes = guild.homes.withHome(homeName, ...))`. Wrap with logic that determines the effective `allowedRankIds`:

```kotlin
        val existing = guild.homes.getHome(homeName)
        val effectiveAllowed = if (existing != null) {
            // Updating an existing home — preserve its whitelist
            existing.allowedRankIds
        } else {
            // New home — Owner-only by default (policy B for new homes, see spec §2.3)
            val ownerRank = rankRepository.getHighestRank(guildId)
            if (ownerRank != null) setOf(ownerRank.id) else emptySet()
        }
        val effectiveHome = home.copy(allowedRankIds = effectiveAllowed)
        val updatedGuild = guild.copy(homes = guild.homes.withHome(homeName, effectiveHome))
```

Then continue with the existing repository.update(updatedGuild) call.

If the existing code uses different variable names, adapt accordingly — preserve the **intent**: existing home keeps whitelist, brand-new home gets Owner-only.

Confirm `rankRepository` is already injected; if not, add: `private val rankRepository: RankRepository` constructor parameter and wire via Koin module (see `KoinModule.kt` for pattern).

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt
git commit -m "feat(home): default new homes to Owner-only whitelist"
```

---

## Task 8: `canUseHome` service method

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceHomeAccessTest.kt`

- [ ] **Step 1: Write failing test**

Create `src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceHomeAccessTest.kt`:

```kotlin
package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildServiceHomeAccessTest {

    private val guildId = UUID.randomUUID()
    private val ownerRankId = UUID.randomUUID()
    private val memberRankId = UUID.randomUUID()
    private val ownerPlayerId = UUID.randomUUID()
    private val memberPlayerId = UUID.randomUUID()
    private val homeName = "main"
    private val worldId = UUID.randomUUID()

    private fun homeWith(allowed: Set<UUID>) = GuildHome(worldId, Position3D(0, 64, 0), allowed)

    private fun setup(homeAllowed: Set<UUID>): GuildServiceBukkit {
        val ownerRank = Rank(ownerRankId, guildId, "Owner", 0, RankPermission.values().toSet())
        val memberRank = Rank(memberRankId, guildId, "Member", 10, emptySet())
        val guild = Guild(
            id = guildId, name = "G", createdAt = Instant.now(),
            homes = GuildHomes(mapOf(homeName to homeWith(homeAllowed)))
        )
        val guildRepo = mockk<GuildRepository>(relaxed = true)
        val memberRepo = mockk<MemberRepository>(relaxed = true)
        val rankRepo = mockk<RankRepository>(relaxed = true)
        every { guildRepo.getById(guildId) } returns guild
        every { rankRepo.getById(ownerRankId) } returns ownerRank
        every { rankRepo.getById(memberRankId) } returns memberRank
        every { rankRepo.getHighestRank(guildId) } returns ownerRank
        every { memberRepo.getByPlayerAndGuild(ownerPlayerId, guildId) } returns
            Member(ownerPlayerId, guildId, ownerRankId, Instant.now())
        every { memberRepo.getByPlayerAndGuild(memberPlayerId, guildId) } returns
            Member(memberPlayerId, guildId, memberRankId, Instant.now())
        // Construct via reflection / direct constructor with required deps — adapt to project's
        // actual GuildServiceBukkit constructor signature when implementing.
        return mockk(relaxed = true) // placeholder — replace once constructor known
    }

    @Test
    fun `owner can use home regardless of whitelist`() {
        // TODO: replace placeholder setup with real GuildServiceBukkit construction once deps mapped
        // For now this test documents intended behavior; will be made executable in Step 3.
    }
}
```

Note: this test stub will be made executable when you know the real constructor signature (Step 3). Run it now to confirm it compiles but fails meaningfully:

Run: `./gradlew test --tests "GuildServiceHomeAccessTest" --info`
Expected: tests pass trivially (no real asserts yet) — refine in Step 3.

- [ ] **Step 2: Add interface method**

In `src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt`, add to the interface:

```kotlin
    /**
     * Checks whether a player may teleport to the named home. Returns true when:
     * - the player is in the guild, AND
     * - the player's rank is the highest-priority (Owner) rank, OR
     * - the player's rank id is in `home.allowedRankIds`.
     */
    fun canUseHome(playerId: UUID, guildId: UUID, homeName: String): Boolean
```

- [ ] **Step 3: Implement in `GuildServiceBukkit.kt`**

Add to the class:

```kotlin
    override fun canUseHome(playerId: UUID, guildId: UUID, homeName: String): Boolean {
        val guild = guildRepository.getById(guildId) ?: return false
        val home = guild.homes.getHome(homeName) ?: return false
        val member = memberRepository.getByPlayerAndGuild(playerId, guildId) ?: return false
        val ownerRank = rankRepository.getHighestRank(guildId)
        if (ownerRank != null && member.rankId == ownerRank.id) return true
        return member.rankId in home.allowedRankIds
    }
```

Now go back to the test from Step 1, replace the placeholder `setup` with real construction matching `GuildServiceBukkit`'s actual constructor (check the existing file for the dep list — likely `guildRepository`, `memberRepository`, `rankRepository`, `logger`, `configService`, etc.). Replace the trivial test bodies with real asserts:

```kotlin
    @Test
    fun `owner can use home regardless of whitelist`() {
        val service = setup(homeAllowed = emptySet())
        assertTrue(service.canUseHome(ownerPlayerId, guildId, homeName))
    }

    @Test
    fun `member cannot use home when not whitelisted`() {
        val service = setup(homeAllowed = emptySet())
        assertFalse(service.canUseHome(memberPlayerId, guildId, homeName))
    }

    @Test
    fun `member can use home when whitelisted`() {
        val service = setup(homeAllowed = setOf(memberRankId))
        assertTrue(service.canUseHome(memberPlayerId, guildId, homeName))
    }

    @Test
    fun `non-member cannot use home`() {
        val service = setup(homeAllowed = setOf(memberRankId))
        val outsider = UUID.randomUUID()
        // memberRepository returns null for outsider — already default via relaxed mock
        assertFalse(service.canUseHome(outsider, guildId, homeName))
    }
```

- [ ] **Step 4: Run test, confirm pass**

Run: `./gradlew test --tests "GuildServiceHomeAccessTest"`
Expected: PASS (all 4 cases).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt \
        src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt \
        src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceHomeAccessTest.kt
git commit -m "feat(home): GuildService.canUseHome with owner bypass + whitelist"
```

---

## Task 9: `canUseAllyHome` service method

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceAllyHomeAccessTest.kt`

- [ ] **Step 1: Write failing tests**

Create test file following the same structural pattern as Task 8 step 3. Cover:
- Owner of source guild bypasses outbound `USE_ALLY_HOMES` rank check (still requires inbound whitelist).
- Non-owner with `USE_ALLY_HOMES` + source guild on target's inbound whitelist → allowed.
- Non-owner without `USE_ALLY_HOMES` → denied.
- Source guild not on target's inbound whitelist → denied.
- Target guild has no `allyHome` → denied.

Use this signature in tests:

```kotlin
service.canUseAllyHome(playerId, sourceGuildId, targetGuildId)
```

- [ ] **Step 2: Run, confirm fail (compile error: method missing)**

Run: `./gradlew test --tests "GuildServiceAllyHomeAccessTest"`
Expected: FAIL

- [ ] **Step 3: Add to interface and implement**

`GuildService.kt`:

```kotlin
    /**
     * Checks whether a player may teleport to another guild's ally-home. Returns true when:
     * - player is in `sourceGuildId`, AND
     * - target guild has an ally-home set, AND
     * - source guild is in target.allyHomeAllowedGuilds (inbound), AND
     * - (player's rank in source is Owner) OR (rank has USE_ALLY_HOMES permission).
     */
    fun canUseAllyHome(playerId: UUID, sourceGuildId: UUID, targetGuildId: UUID): Boolean
```

`GuildServiceBukkit.kt`:

```kotlin
    override fun canUseAllyHome(playerId: UUID, sourceGuildId: UUID, targetGuildId: UUID): Boolean {
        val sourceGuild = guildRepository.getById(sourceGuildId) ?: return false
        val targetGuild = guildRepository.getById(targetGuildId) ?: return false
        if (targetGuild.allyHome == null) return false
        if (sourceGuildId !in targetGuild.allyHomeAllowedGuilds) return false
        val member = memberRepository.getByPlayerAndGuild(playerId, sourceGuildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        val ownerRank = rankRepository.getHighestRank(sourceGuildId)
        if (ownerRank != null && rank.id == ownerRank.id) return true
        return RankPermission.USE_ALLY_HOMES in rank.permissions
    }
```

- [ ] **Step 4: Run tests, confirm pass**

Run: `./gradlew test --tests "GuildServiceAllyHomeAccessTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt \
        src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt \
        src/test/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceAllyHomeAccessTest.kt
git commit -m "feat(ally-home): GuildService.canUseAllyHome with owner bypass + USE_ALLY_HOMES gate"
```

---

## Task 10: Mutator methods — `setHomeAllowedRanks` / `setAllyHomeAllowedGuilds`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt`

- [ ] **Step 1: Add interface methods**

```kotlin
    /**
     * Updates the rank whitelist for a named home. Owner rank is always implicitly allowed
     * (lockout-safe) — implementations must not require the owner rank ID to be in the set.
     * Caller must have MANAGE_HOME permission; returns false otherwise.
     */
    fun setHomeAllowedRanks(guildId: UUID, homeName: String, allowedRankIds: Set<UUID>, actorId: UUID): Boolean

    /**
     * Updates the inbound allow-list of guilds whose members may teleport to this guild's
     * ally-home. Caller must have MANAGE_HOME permission.
     */
    fun setAllyHomeAllowedGuilds(guildId: UUID, allowedGuildIds: Set<UUID>, actorId: UUID): Boolean
```

- [ ] **Step 2: Implement**

```kotlin
    override fun setHomeAllowedRanks(
        guildId: UUID, homeName: String, allowedRankIds: Set<UUID>, actorId: UUID
    ): Boolean {
        if (!hasManageHome(actorId, guildId)) return false
        val guild = guildRepository.getById(guildId) ?: return false
        val home = guild.homes.getHome(homeName) ?: return false
        val updatedHome = home.copy(allowedRankIds = allowedRankIds)
        val updatedGuild = guild.copy(homes = guild.homes.withHome(homeName, updatedHome))
        return guildRepository.update(updatedGuild)
    }

    override fun setAllyHomeAllowedGuilds(
        guildId: UUID, allowedGuildIds: Set<UUID>, actorId: UUID
    ): Boolean {
        if (!hasManageHome(actorId, guildId)) return false
        val guild = guildRepository.getById(guildId) ?: return false
        val updatedGuild = guild.copy(allyHomeAllowedGuilds = allowedGuildIds)
        return guildRepository.update(updatedGuild)
    }

    private fun hasManageHome(actorId: UUID, guildId: UUID): Boolean {
        val member = memberRepository.getByPlayerAndGuild(actorId, guildId) ?: return false
        val rank = rankRepository.getById(member.rankId) ?: return false
        return RankPermission.MANAGE_HOME in rank.permissions
    }
```

If `hasManageHome`-style helper already exists, reuse it instead.

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/services/GuildService.kt \
        src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt
git commit -m "feat(home): mutators for home allowedRanks and allyHome allowedGuilds"
```

---

## Task 11: New ally added → auto-update inbound allow-list

**Files:**
- Modify: wherever alliance acceptance is handled (likely `src/main/kotlin/net/lumalyte/lg/infrastructure/services/RelationServiceBukkit.kt` or `GuildRelationService` — locate by searching `RelationType.ALLY` or `acceptAlliance`).

- [ ] **Step 1: Locate alliance-acceptance code**

Run: `grep -rn "ALLY\|acceptAlliance\|createAlliance" src/main/kotlin/net/lumalyte/lg/infrastructure/services/ src/main/kotlin/net/lumalyte/lg/application/services/ | grep -v "import" | head -20`

Find the code path that transitions a relation to `ALLY` state.

- [ ] **Step 2: After alliance commit, update both guilds' `allyHomeAllowedGuilds`**

Add the following after the relation is persisted as ALLY (replace the placeholder ids/services with what's actually in scope):

```kotlin
        // Auto-add new ally to both guilds' inbound ally-home whitelists (policy B spirit, spec §3.2).
        val gA = guildRepository.getById(guildAId)
        val gB = guildRepository.getById(guildBId)
        if (gA != null) {
            guildRepository.update(gA.copy(allyHomeAllowedGuilds = gA.allyHomeAllowedGuilds + guildBId))
        }
        if (gB != null) {
            guildRepository.update(gB.copy(allyHomeAllowedGuilds = gB.allyHomeAllowedGuilds + guildAId))
        }
```

- [ ] **Step 3: When alliance is broken, remove from both whitelists**

In the corresponding break-alliance code path:

```kotlin
        val gA = guildRepository.getById(guildAId)
        val gB = guildRepository.getById(guildBId)
        if (gA != null) {
            guildRepository.update(gA.copy(allyHomeAllowedGuilds = gA.allyHomeAllowedGuilds - guildBId))
        }
        if (gB != null) {
            guildRepository.update(gB.copy(allyHomeAllowedGuilds = gB.allyHomeAllowedGuilds - guildAId))
        }
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat(ally-home): auto-add/remove inbound whitelist on alliance change"
```

---

## Task 12: Enforce home access in `/guild home` command

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt`

- [ ] **Step 1: Add access check in `onHome` (line 314)**

After resolving `targetHomeName` and verifying the home exists (around line 343, before the `activeTeleports` check), insert:

```kotlin
        if (!guildService.canUseHome(playerId, guild.id, targetHomeName)) {
            player.sendMessage("§c❌ You don't have permission to use the home '$targetHomeName'.")
            player.sendMessage("§7Ask a guild manager to grant your rank access.")
            return
        }
```

- [ ] **Step 2: Compile, run plugin smoke (optional manual)**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt
git commit -m "feat(home): enforce per-home whitelist on /guild home"
```

---

## Task 13: Enforce ally-home access in ally-home teleport path

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt` (and `GuildHomeMenu.kt`'s ally-home button)

- [ ] **Step 1: Locate ally-home teleport command/button**

Run: `grep -n "allyHome\|ally_home\|ALLY_HOME" src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt`

Find the handler.

- [ ] **Step 2: Wrap teleport with `canUseAllyHome` check**

Insert before the actual teleport invocation:

```kotlin
        if (!guildService.canUseAllyHome(player.uniqueId, sourceGuild.id, targetGuild.id)) {
            player.sendMessage("§c❌ You cannot use that ally's home.")
            player.sendMessage("§7Either your rank lacks USE_ALLY_HOMES, or that guild has not allowed your guild.")
            return
        }
```

Apply the same check in `GuildHomeMenu.addAllyHomeButtons` (line 68): grey out the button (BARRIER + denial lore) when `canUseAllyHome` returns false, instead of letting click proceed.

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt \
        src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildHomeMenu.kt
git commit -m "feat(ally-home): enforce canUseAllyHome on command and menu button"
```

---

## Task 14: `RankRepository.swapPriorities` + `RankService.moveRankPriority`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/application/persistence/RankRepository.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/RankRepositorySQLite.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/application/services/RankService.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/RankServiceBukkit.kt`
- Test: `src/test/kotlin/net/lumalyte/lg/infrastructure/services/RankServicePriorityTest.kt`

- [ ] **Step 1: Write failing test for `moveRankPriority`**

Create `src/test/kotlin/net/lumalyte/lg/infrastructure/services/RankServicePriorityTest.kt`. Cover:
- UP succeeds and swaps with neighbor at priority - 1.
- DOWN succeeds and swaps with neighbor at priority + 1.
- Fails when actor's priority >= target's priority.
- Fails when target is Owner (priority 0) attempting UP.
- Fails when target is last (no DOWN neighbor).
- Fails when actor lacks MANAGE_RANKS.

Use MockK to stub `RankRepository.getById`, `getByGuild`, `swapPriorities`, and `MemberRepository.getByPlayerAndGuild`. Sample test:

```kotlin
@Test
fun `moveRankPriority UP swaps with adjacent higher rank`() {
    val guildId = UUID.randomUUID()
    val actorId = UUID.randomUUID()
    val actorRank = Rank(UUID.randomUUID(), guildId, "Owner", 0, setOf(RankPermission.MANAGE_RANKS))
    val target = Rank(UUID.randomUUID(), guildId, "Member", 5, emptySet())
    val neighbor = Rank(UUID.randomUUID(), guildId, "Trusted", 4, emptySet())
    // ... wire mocks ...
    assertTrue(service.moveRankPriority(target.id, PriorityDirection.UP, actorId))
    verify { rankRepository.swapPriorities(target.id, neighbor.id) }
}
```

Run: `./gradlew test --tests "RankServicePriorityTest"`
Expected: FAIL (method missing).

- [ ] **Step 2: Add `PriorityDirection` enum + interface methods**

In `src/main/kotlin/net/lumalyte/lg/application/services/RankService.kt`:

```kotlin
enum class PriorityDirection { UP, DOWN }
```

Add to the interface:

```kotlin
    /**
     * Moves a rank up (lower priority number, higher in hierarchy) or down by one slot,
     * swapping priorities with the adjacent rank. Returns false if:
     * - actor lacks MANAGE_RANKS, OR
     * - actor's rank priority >= target rank priority, OR
     * - target is the highest-priority rank and direction is UP, OR
     * - target is the lowest-priority rank and direction is DOWN.
     */
    fun moveRankPriority(rankId: UUID, direction: PriorityDirection, actorId: UUID): Boolean
```

In `src/main/kotlin/net/lumalyte/lg/application/persistence/RankRepository.kt`:

```kotlin
    /**
     * Atomically swaps the `priority` column of two ranks. The implementation must
     * survive a unique-index on (guild_id, priority) if one is added later — use a
     * temporary out-of-range value during the swap.
     */
    fun swapPriorities(rankAId: UUID, rankBId: UUID): Boolean
```

- [ ] **Step 3: Implement `swapPriorities` in `RankRepositorySQLite.kt`**

Add to the class (after `getNextPriority`):

```kotlin
    override fun swapPriorities(rankAId: UUID, rankBId: UUID): Boolean {
        val rankA = ranks[rankAId] ?: return false
        val rankB = ranks[rankBId] ?: return false
        if (rankA.guildId != rankB.guildId) return false
        // Three-step swap to avoid future unique-constraint issues on (guild_id, priority)
        val tempPriority = Int.MAX_VALUE
        val sql = "UPDATE ranks SET priority = ? WHERE id = ?"
        return try {
            storage.connection.executeUpdate(sql, tempPriority, rankAId.toString())
            storage.connection.executeUpdate(sql, rankA.priority, rankBId.toString())
            storage.connection.executeUpdate(sql, rankB.priority, rankAId.toString())
            ranks[rankAId] = rankA.copy(priority = rankB.priority)
            ranks[rankBId] = rankB.copy(priority = rankA.priority)
            true
        } catch (e: SQLException) {
            false
        }
    }
```

- [ ] **Step 4: Implement `moveRankPriority` in `RankServiceBukkit.kt`**

```kotlin
    override fun moveRankPriority(rankId: UUID, direction: PriorityDirection, actorId: UUID): Boolean {
        val target = rankRepository.getById(rankId) ?: return false
        if (!hasPermission(actorId, target.guildId, RankPermission.MANAGE_RANKS)) {
            logger.warn("Player $actorId attempted to reorder rank $rankId without MANAGE_RANKS")
            return false
        }
        val actorRank = getPlayerRank(actorId, target.guildId) ?: return false
        if (actorRank.priority >= target.priority) {
            logger.warn("Player $actorId cannot reorder rank at or above their own priority")
            return false
        }
        val siblings = rankRepository.getByGuild(target.guildId).sortedBy { it.priority }
        val idx = siblings.indexOfFirst { it.id == target.id }
        val neighborIdx = when (direction) {
            PriorityDirection.UP -> idx - 1
            PriorityDirection.DOWN -> idx + 1
        }
        if (neighborIdx !in siblings.indices) return false
        val neighbor = siblings[neighborIdx]
        // Owner protection: can't displace highest-priority rank
        if (direction == PriorityDirection.UP && neighbor == siblings.first()) return false
        return rankRepository.swapPriorities(target.id, neighbor.id)
    }
```

`getPlayerRank` exists already; verify by grepping. If absent, implement inline.

- [ ] **Step 5: Run tests, confirm pass**

Run: `./gradlew test --tests "RankServicePriorityTest"`
Expected: PASS (all cases).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/persistence/RankRepository.kt \
        src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/RankRepositorySQLite.kt \
        src/main/kotlin/net/lumalyte/lg/application/services/RankService.kt \
        src/main/kotlin/net/lumalyte/lg/infrastructure/services/RankServiceBukkit.kt \
        src/test/kotlin/net/lumalyte/lg/infrastructure/services/RankServicePriorityTest.kt
git commit -m "feat(ranks): swapPriorities + moveRankPriority with actor-priority guard"
```

---

## Task 15: Add ▲/▼ priority buttons to `RankEditMenu`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankEditMenu.kt`

- [ ] **Step 1: Add helper for current actor rank**

After `isEditingOwnOwnerRank()` (line 57), add:

```kotlin
    private fun canActorReorder(): Boolean {
        val actorRank = rankService.getPlayerRank(player.uniqueId, guild.id) ?: return false
        return actorRank.priority < rank.priority
    }

    private fun siblingAt(direction: net.lumalyte.lg.application.services.PriorityDirection): Rank? {
        val siblings = rankService.listRanks(guild.id).sortedBy { it.priority }
        val idx = siblings.indexOfFirst { it.id == rank.id }
        val neighborIdx = when (direction) {
            net.lumalyte.lg.application.services.PriorityDirection.UP -> idx - 1
            net.lumalyte.lg.application.services.PriorityDirection.DOWN -> idx + 1
        }
        return siblings.getOrNull(neighborIdx)
    }
```

- [ ] **Step 2: Add `addPriorityButtons(pane)` and call from `open()`**

Insert at the top of `open()` (after `gui.addPane(pane)`, before `addRankInfoSection(pane)`):

```kotlin
        addPriorityButtons(pane)
```

Add the method:

```kotlin
    private fun addPriorityButtons(pane: StaticPane) {
        val upNeighbor = siblingAt(net.lumalyte.lg.application.services.PriorityDirection.UP)
        val downNeighbor = siblingAt(net.lumalyte.lg.application.services.PriorityDirection.DOWN)
        val canUp = canActorReorder() && upNeighbor != null && upNeighbor != rankService.getHighestRank(guild.id) && !isOwnerRank()
        val canDown = canActorReorder() && downNeighbor != null && !isOwnerRank()

        val upItem = ItemStack.of(if (canUp) Material.SPECTRAL_ARROW else Material.BARRIER)
            .name(if (canUp) "§a▲ Move Up" else "§7▲ Move Up (locked)")
            .lore("§7Current priority: §f${rank.priority}")
            .lore(if (upNeighbor != null) "§7Above: §f${upNeighbor.name}" else "§7No rank above")
            .lore("§7")
            .lore(if (canUp) "§eClick to raise rank" else "§cCannot reorder")
        pane.addItem(GuiItem(upItem) {
            if (!canUp) return@GuiItem
            val ok = rankService.moveRankPriority(rank.id, net.lumalyte.lg.application.services.PriorityDirection.UP, player.uniqueId)
            if (ok) {
                player.sendMessage("§a✅ Rank moved up.")
                rank = rankService.getRank(rank.id) ?: rank
                open()
            } else {
                player.sendMessage("§c❌ Could not move rank.")
            }
        }, 0, 0)

        val downItem = ItemStack.of(if (canDown) Material.SPECTRAL_ARROW else Material.BARRIER)
            .name(if (canDown) "§a▼ Move Down" else "§7▼ Move Down (locked)")
            .lore("§7Current priority: §f${rank.priority}")
            .lore(if (downNeighbor != null) "§7Below: §f${downNeighbor.name}" else "§7No rank below")
            .lore("§7")
            .lore(if (canDown) "§eClick to lower rank" else "§cCannot reorder")
        pane.addItem(GuiItem(downItem) {
            if (!canDown) return@GuiItem
            val ok = rankService.moveRankPriority(rank.id, net.lumalyte.lg.application.services.PriorityDirection.DOWN, player.uniqueId)
            if (ok) {
                player.sendMessage("§a✅ Rank moved down.")
                rank = rankService.getRank(rank.id) ?: rank
                open()
            } else {
                player.sendMessage("§c❌ Could not move rank.")
            }
        }, 8, 0)
    }
```

If `rankService.getRank(id)` does not exist, use `rankService.listRanks(guild.id).find { it.id == rank.id }`.

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankEditMenu.kt
git commit -m "feat(ui): add ▲/▼ priority reorder buttons to RankEditMenu"
```

---

## Task 16: Surface `USE_ALLY_HOMES` in `RankEditMenu` Diplomacy category

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankEditMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildRankManagementMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankCreationMenu.kt`

- [ ] **Step 1: Add `USE_ALLY_HOMES` to Diplomacy permission list in `RankEditMenu.addPermissionCategories` (line 212)**

Replace the Diplomacy entry:

```kotlin
            "Diplomacy" to listOf(
                RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR,
                RankPermission.ACCEPT_ALLIANCES, RankPermission.MANAGE_PARTIES,
                RankPermission.SEND_PARTY_REQUESTS, RankPermission.ACCEPT_PARTY_INVITES,
                RankPermission.USE_ALLY_HOMES
            ),
```

- [ ] **Step 2: Same edit in `GuildRankManagementMenu.groupPermissionsByCategory` (line 157-162)**

Add `USE_ALLY_HOMES` to the Diplomacy branch.

- [ ] **Step 3: Same edit in `RankCreationMenu`'s permission categories (search for `MANAGE_RELATIONS`)**

- [ ] **Step 4: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankEditMenu.kt \
        src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildRankManagementMenu.kt \
        src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/RankCreationMenu.kt
git commit -m "feat(ui): expose USE_ALLY_HOMES in Diplomacy permission category"
```

---

## Task 17: `HomeAccessMenu` — per-home rank whitelist editor

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/HomeAccessMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt`

- [ ] **Step 1: Create the menu**

```kotlin
package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val homeName: String
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage("§c❌ You need MANAGE_HOME to configure home access.")
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val home = guildService.getHome(guild.id, homeName)
        if (home == null) {
            player.sendMessage("§cHome '$homeName' no longer exists.")
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(4, "§6Access: $homeName")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(pane)

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val ownerRank = rankService.getHighestRank(guild.id)
        var allowed = home.allowedRankIds.toMutableSet()

        ranks.take(8).forEachIndexed { idx, r ->
            val row = idx / 4
            val col = idx % 4
            val isOwner = r.id == ownerRank?.id
            val on = isOwner || r.id in allowed
            val item = ItemStack.of(
                if (isOwner) Material.NETHER_STAR
                else if (on) Material.LIME_DYE
                else Material.GRAY_DYE
            ).name(if (on) "§a✓ ${r.name}" else "§c✗ ${r.name}")
                .lore("§7Priority: §f${r.priority}")
                .lore("§7")
                .lore(
                    if (isOwner) "§eOwner — always allowed"
                    else if (on) "§eClick to revoke" else "§eClick to grant"
                )
            pane.addItem(GuiItem(item) {
                if (isOwner) return@GuiItem
                if (r.id in allowed) allowed.remove(r.id) else allowed.add(r.id)
                guildService.setHomeAllowedRanks(guild.id, homeName, allowed.toSet(), player.uniqueId)
                open()
            }, col, row)
        }

        val backItem = ItemStack.of(Material.ARROW).name("§7← Back to Homes")
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 8, 3)

        gui.show(player)
    }
}
```

- [ ] **Step 2: Register in `MenuFactory`**

Add to `MenuFactory.kt`:

```kotlin
    fun createHomeAccessMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild,
        homeName: String
    ): Menu = net.lumalyte.lg.interaction.menus.guild.HomeAccessMenu(menuNavigator, player, guild, homeName)
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/HomeAccessMenu.kt \
        src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt
git commit -m "feat(ui): HomeAccessMenu — per-home rank whitelist editor"
```

---

## Task 18: `AllyHomeAccessMenu` — inbound ally guild whitelist

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/AllyHomeAccessMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt`

- [ ] **Step 1: Create the menu**

Pattern matches `HomeAccessMenu` but iterates over current allies of this guild. Inject `RelationService` (or whatever lists allies — check `GuildRelationsMenu.kt` for precedent).

```kotlin
package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AllyHomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage("§c❌ You need MANAGE_HOME to configure ally-home access.")
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val current = guildService.getGuild(guild.id) ?: return
        val allies = relationService.listAllies(guild.id) // returns Set<Guild> or Set<UUID> — adapt
        val allowed = current.allyHomeAllowedGuilds.toMutableSet()

        val gui = ChestGui(4, "§6Ally-home Access")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(pane)

        allies.take(8).forEachIndexed { idx, ally ->
            val row = idx / 4
            val col = idx % 4
            val allyId = ally.id // assuming Set<Guild>; if UUID, use directly
            val on = allyId in allowed
            val item = ItemStack.of(if (on) Material.LIME_DYE else Material.GRAY_DYE)
                .name(if (on) "§a✓ ${ally.name}" else "§c✗ ${ally.name}")
                .lore("§7Click to toggle inbound access")
            pane.addItem(GuiItem(item) {
                if (allyId in allowed) allowed.remove(allyId) else allowed.add(allyId)
                guildService.setAllyHomeAllowedGuilds(guild.id, allowed.toSet(), player.uniqueId)
                open()
            }, col, row)
        }

        val info = ItemStack.of(Material.BOOK)
            .name("§eOutbound access (read-only)")
            .lore("§7To control which of YOUR ranks can use ally homes,")
            .lore("§7edit the §fUSE_ALLY_HOMES§7 permission in rank settings.")
        pane.addItem(GuiItem(info), 4, 3)

        val backItem = ItemStack.of(Material.ARROW).name("§7← Back to Homes")
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 8, 3)

        gui.show(player)
    }
}
```

- [ ] **Step 2: Register in `MenuFactory`**

```kotlin
    fun createAllyHomeAccessMenu(
        menuNavigator: MenuNavigator,
        player: Player,
        guild: net.lumalyte.lg.domain.entities.Guild
    ): Menu = net.lumalyte.lg.interaction.menus.guild.AllyHomeAccessMenu(menuNavigator, player, guild)
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL — if `RelationService.listAllies` signature differs, adapt the call.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/AllyHomeAccessMenu.kt \
        src/main/kotlin/net/lumalyte/lg/interaction/menus/MenuFactory.kt
git commit -m "feat(ui): AllyHomeAccessMenu — per-ally inbound whitelist editor"
```

---

## Task 19: Wire 🔒 Access buttons into `GuildHomeMenu`

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildHomeMenu.kt`

- [ ] **Step 1: Add per-home access buttons in row 1**

In `addHomeSlotsDisplay` (line 78) or as a new method, iterate over current homes and place a button at row 1 underneath each home slot:

```kotlin
    private fun addHomeAccessButtons(pane: StaticPane) {
        val rankService: RankService by inject()
        val canManage = rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)
        if (!canManage) return
        val homes = guildService.getHomes(guild.id).homes.entries.toList()
        homes.take(9).forEachIndexed { idx, (homeName, _) ->
            val item = ItemStack.of(Material.IRON_DOOR)
                .name("§6🔒 Access: $homeName")
                .lore("§7Configure which ranks can use this home")
                .lore("§eClick to manage")
            pane.addItem(GuiItem(item) {
                menuNavigator.openMenu(menuFactory.createHomeAccessMenu(menuNavigator, player, guild, homeName))
            }, idx, 1)
        }
    }
```

Add the `RankService` import and add `addHomeAccessButtons(pane)` to `open()` after `addHomeSlotsDisplay(pane, 0, 0)`.

- [ ] **Step 2: Add Ally Access button**

In the existing ally-home row (row 5, after the ally-home teleport button), if `ALLY_HOME_ACCESS` perk is unlocked, add:

```kotlin
        if (progressionService.hasPerkUnlocked(guild.id, net.lumalyte.lg.application.services.PerkType.ALLY_HOME_ACCESS)
            && rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            val allyAccessItem = ItemStack.of(Material.IRON_DOOR)
                .name("§6🔒 Ally-home Access")
                .lore("§7Configure which allied guilds can use your ally-home")
            pane.addItem(GuiItem(allyAccessItem) {
                menuNavigator.openMenu(menuFactory.createAllyHomeAccessMenu(menuNavigator, player, guild))
            }, 7, 5)
        }
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/menus/guild/GuildHomeMenu.kt
git commit -m "feat(ui): Access buttons in GuildHomeMenu for per-home + ally-home"
```

---

## Task 20: Bedrock variants

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockGuildHomeMenu.kt`
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/BedrockRankEditMenu.kt`

- [ ] **Step 1: Add denial feedback in `BedrockGuildHomeMenu` teleport handler**

Locate where teleport is invoked and gate it with `guildService.canUseHome(...)`. On denial, show form text: `"You don't have permission to use this home."`. Don't add new sub-forms for Bedrock — instead, when manager clicks Access, show a simple `SimpleForm` listing ranks; toggle via repeated form opens. Skip if cost/complexity too high for first pass — fall back to a chat message: `"Use the Java client to configure home access."`.

Actual code:

```kotlin
        if (!guildService.canUseHome(player.uniqueId, guild.id, targetHomeName)) {
            player.sendMessage("§cYou don't have permission to use that home.")
            return
        }
```

- [ ] **Step 2: Add priority controls (read-only) in `BedrockRankEditMenu`**

Bedrock can't easily do click-to-reorder. Surface priority as a chat-confirmable "Move Up / Move Down" form choice, calling `rankService.moveRankPriority(...)`. Simplest: add two buttons to the existing form labeled `▲ Move Up`, `▼ Move Down` and route to the service.

- [ ] **Step 3: Compile**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/menus/bedrock/
git commit -m "feat(bedrock): home access gating + rank priority controls"
```

---

## Task 21: Final verification

- [ ] **Step 1: Full test suite**

Run: `./gradlew test`
Expected: All tests pass.

- [ ] **Step 2: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, jar produced under `build/libs/`.

- [ ] **Step 3: Manual smoke test on dev server (paper-mcp)**

Use `mcp__papermcp__minecraft_execute_command` to:
1. Form a test guild, set a home, set another rank, log in as a member of that rank → `/guild home` should be denied (after granting MANAGE_HOME to founder and using `HomeAccessMenu` to revoke).
2. Test rank priority: as founder, edit a member-tier rank, click ▲, verify priority change persists across `/reload`.
3. Test ally home: ally two guilds, verify auto-add to `allyHomeAllowedGuilds`, revoke one side via `AllyHomeAccessMenu`, verify cross-guild teleport denied.

- [ ] **Step 4: Final commit (if any cleanup)**

```bash
git status
# If clean, no commit needed.
```

---

## Self-Review Notes

- **Spec coverage:** §2 (per-home) → Tasks 2, 5, 7, 8, 10 (mutator), 12 (enforce), 17 (UI). §3 (ally-home) → Tasks 1 (perm), 3 (field), 6 (persist), 9 (check), 10 (mutator), 11 (auto-update), 13 (enforce), 18 (UI), 19 (button). §4 (priority) → Task 14 (service), 15 (UI), plus Bedrock in Task 20. §5 (migration) → Task 4. §6 (testing) → Tests embedded in Tasks 2, 3, 8, 9, 14.
- **GuildTeamService bug (§4.3)** is out of scope, flagged in spec — no task.
- **Test gaps:** Migration tests (in-memory SQLite) and Konsist layer tests are not split into separate tasks — recommend adding if SPEAR cycle requires. Existing test conventions don't appear to use Konsist.
- **Bedrock parity** is rough (Task 20) — acceptable per project precedent where Bedrock menus lag behind Java.

---

## Execution Handoff

**Plan complete and saved to `docs/plans/2026-05-10-rank-and-home-perms-impl.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
