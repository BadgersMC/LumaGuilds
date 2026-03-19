# Guild History Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement `/guild history <player>` — tracks every guild a player has belonged to, showing guild names for active guilds and `[UNKNOWN]` for disbanded ones.

**Architecture:** New `MembershipHistory` entity persisted in SQLite (no in-memory cache). History rows are opened on join and closed on leave/kick/disband. The command renders history as chat messages oldest → newest.

**Tech Stack:** Kotlin, Paper/Bukkit API, ACF (commands), Koin (DI), aikar/idb SQLite (`storage.connection.executeUpdate` / `getResults`), existing `getInstant`/`getInstantNotNull` extension functions from `DatabaseUtils.kt`.

---

## Task 1: Domain entity

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/domain/entities/MembershipHistory.kt`

**Step 1: Create the file**

```kotlin
package net.lumalyte.lg.domain.entities

import java.time.Instant
import java.util.UUID

enum class DepartureReason { LEFT, KICKED, DISBANDED }

data class MembershipHistory(
    val id: UUID,
    val playerId: UUID,
    val guildId: UUID,
    val joinedAt: Instant,
    val departedAt: Instant? = null,
    val departureReason: DepartureReason? = null
) {
    val isOpen: Boolean get() = departedAt == null
}
```

**Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/domain/entities/MembershipHistory.kt
git commit -m "feat: add MembershipHistory entity and DepartureReason enum"
```

---

## Task 2: Repository interface

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/application/persistence/MembershipHistoryRepository.kt`

**Step 1: Create the file**

```kotlin
package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.domain.entities.MembershipHistory
import java.util.UUID

interface MembershipHistoryRepository {

    /**
     * Opens a new history stint when a player joins a guild.
     * departedAt and departureReason are left null (open stint).
     */
    fun openStint(playerId: UUID, guildId: UUID): Boolean

    /**
     * Closes the most recent open stint for a player in a guild.
     * Sets departedAt = now and departureReason = reason.
     * Safe to call even if no open stint exists (returns false).
     */
    fun closeStint(playerId: UUID, guildId: UUID, reason: DepartureReason): Boolean

    /**
     * Returns all history entries for a player, ordered by joinedAt ASC
     * (oldest first, so index numbers match "total guilds joined").
     */
    fun getByPlayer(playerId: UUID): List<MembershipHistory>
}
```

**Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/application/persistence/MembershipHistoryRepository.kt
git commit -m "feat: add MembershipHistoryRepository interface"
```

---

## Task 3: SQLite implementation

**Files:**
- Create: `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/MembershipHistoryRepositorySQLite.kt`

**Reference pattern:** `RelationRepositorySQLite.kt` in the same package — use the same `Storage<Database>`, `ensureInitialized()`, `createTable()` + `preload()` pattern, and the same `getInstant`/`getInstantNotNull` extension functions.

**Step 1: Create the file**

```kotlin
package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.errors.DatabaseOperationException
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.domain.entities.DepartureReason
import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.infrastructure.persistence.getInstant
import net.lumalyte.lg.infrastructure.persistence.getInstantNotNull
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class MembershipHistoryRepositorySQLite(
    private val storage: Storage<Database>
) : MembershipHistoryRepository {

    private val logger = LoggerFactory.getLogger(MembershipHistoryRepositorySQLite::class.java)
    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized) {
            createTable()
            isInitialized = true
        }
    }

    private fun createTable() {
        val sql = """
            CREATE TABLE IF NOT EXISTS membership_history (
                id TEXT PRIMARY KEY,
                player_id TEXT NOT NULL,
                guild_id TEXT NOT NULL,
                joined_at TEXT NOT NULL,
                departed_at TEXT,
                departure_reason TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_membership_history_player
                ON membership_history(player_id);
        """.trimIndent()
        try {
            storage.connection.executeUpdate(sql)
            logger.info("membership_history table ready")
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to create membership_history table", e)
        }
    }

    override fun openStint(playerId: UUID, guildId: UUID): Boolean {
        ensureInitialized()
        val sql = """
            INSERT INTO membership_history (id, player_id, guild_id, joined_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        return try {
            val rows = storage.connection.executeUpdate(
                sql,
                UUID.randomUUID().toString(),
                playerId.toString(),
                guildId.toString(),
                Instant.now().toString()
            )
            rows > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to open membership stint for $playerId in $guildId", e)
        }
    }

    override fun closeStint(playerId: UUID, guildId: UUID, reason: DepartureReason): Boolean {
        ensureInitialized()
        val sql = """
            UPDATE membership_history
            SET departed_at = ?, departure_reason = ?
            WHERE player_id = ? AND guild_id = ? AND departed_at IS NULL
        """.trimIndent()
        return try {
            val rows = storage.connection.executeUpdate(
                sql,
                Instant.now().toString(),
                reason.name,
                playerId.toString(),
                guildId.toString()
            )
            rows > 0
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to close membership stint for $playerId in $guildId", e)
        }
    }

    override fun getByPlayer(playerId: UUID): List<MembershipHistory> {
        ensureInitialized()
        val sql = """
            SELECT id, player_id, guild_id, joined_at, departed_at, departure_reason
            FROM membership_history
            WHERE player_id = ?
            ORDER BY joined_at ASC
        """.trimIndent()
        return try {
            storage.connection.getResults(sql, playerId.toString()).map { row ->
                MembershipHistory(
                    id = UUID.fromString(row.getString("id")),
                    playerId = UUID.fromString(row.getString("player_id")),
                    guildId = UUID.fromString(row.getString("guild_id")),
                    joinedAt = row.getInstantNotNull("joined_at"),
                    departedAt = row.getInstant("departed_at"),
                    departureReason = row.getString("departure_reason")
                        ?.let { DepartureReason.valueOf(it) }
                )
            }
        } catch (e: SQLException) {
            throw DatabaseOperationException("Failed to fetch membership history for $playerId", e)
        }
    }
}
```

**Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/MembershipHistoryRepositorySQLite.kt
git commit -m "feat: add MembershipHistoryRepositorySQLite implementation"
```

---

## Task 4: Register in Koin DI module

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/di/Modules.kt`

**Context:** The `guildsModule()` function contains all guild-related DI. Find the `// Repositories` block (around line 385) and the `MemberService` and `GuildService` single<> registrations.

**Step 1: Add the import** (with the other persistence imports at the top of the file)

```kotlin
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.infrastructure.persistence.guilds.MembershipHistoryRepositorySQLite
```

**Step 2: Register the singleton** — add after `AuditRepository` registration in the `// Repositories` block:

```kotlin
single<MembershipHistoryRepository> { MembershipHistoryRepositorySQLite(get()) }
```

**Step 3: Update `MemberService` registration** — add one more `get()` (6 total):

```kotlin
// Before:
single<MemberService> { MemberServiceBukkit(get(), get(), get(), get(), get()) }
// After:
single<MemberService> { MemberServiceBukkit(get(), get(), get(), get(), get(), get()) }
```

**Step 4: Update `GuildService` registration** — already has 9 `get()` after the RelationRepository change — no change needed here. Verify the current count is 9.

**Step 5: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (will fail after this step because MemberServiceBukkit doesn't have the new param yet — that's fixed in Task 5)

**Step 6: Commit** (after Task 5 compiles cleanly — commit both together)

---

## Task 5: Hook history recording into MemberServiceBukkit

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/MemberServiceBukkit.kt`

**Step 1: Add import and constructor parameter**

Add to imports:
```kotlin
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.domain.entities.DepartureReason
```

Add `historyRepository` as the last constructor parameter:
```kotlin
class MemberServiceBukkit(
    private val memberRepository: MemberRepository,
    private val rankRepository: RankRepository,
    private val guildRepository: GuildRepository,
    private val progressionRepository: net.lumalyte.lg.application.persistence.ProgressionRepository,
    private val progressionConfigService: ProgressionConfigService,
    private val historyRepository: MembershipHistoryRepository   // ADD THIS
) : MemberService {
```

**Step 2: Record history on join** — in `addMember()`, after the `if (result) {` block that calls `Bukkit.getPluginManager().callEvent(GuildMemberJoinEvent(...))`, add:

```kotlin
// Record membership history
historyRepository.openStint(playerId, guildId)
```

Place it just after the `GuildMemberJoinEvent` call, before the Apollo try-catch block.

**Step 3: Record history on leave/kick** — in `removeMember()`, find the `val result = memberRepository.remove(playerId, guildId)` line. After the `if (result) {` block, in the existing logger section (lines ~143-147), add the history recording BEFORE the logger.info calls:

```kotlin
// Record membership history (LEFT = voluntary, KICKED = by another player)
val reason = if (actorId == playerId) DepartureReason.LEFT else DepartureReason.KICKED
historyRepository.closeStint(playerId, guildId, reason)
```

**Step 4: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit Tasks 4 + 5 together**

```bash
git add src/main/kotlin/net/lumalyte/lg/di/Modules.kt
git add src/main/kotlin/net/lumalyte/lg/infrastructure/services/MemberServiceBukkit.kt
git commit -m "feat: wire MembershipHistoryRepository into DI and hook join/leave/kick recording"
```

---

## Task 6: Hook DISBANDED recording into GuildServiceBukkit

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt`

**Context:** `GuildServiceBukkit` already has `RelationRepository` injected (added in the previous bug fix). `MembershipHistoryRepository` goes in right after it.

**Step 1: Add import and constructor parameter**

Add to imports:
```kotlin
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.domain.entities.DepartureReason
```

Add `historyRepository` as the last constructor parameter (after `relationRepository`):
```kotlin
class GuildServiceBukkit(
    private val guildRepository: GuildRepository,
    private val rankRepository: RankRepository,
    private val memberRepository: MemberRepository,
    private val rankService: net.lumalyte.lg.application.services.RankService,
    private val memberService: net.lumalyte.lg.application.services.MemberService,
    private val nexoEmojiService: NexoEmojiService,
    private val vaultService: net.lumalyte.lg.application.services.GuildVaultService,
    private val hologramService: net.lumalyte.lg.infrastructure.services.VaultHologramService,
    private val relationRepository: RelationRepository,
    private val historyRepository: MembershipHistoryRepository   // ADD THIS
) : GuildService {
```

**Step 2: Update Koin registration** — `GuildService` in `Modules.kt` now needs 10 `get()` calls (was 9 after RelationRepository was added):

```kotlin
single<GuildService> { GuildServiceBukkit(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
```

**Step 3: Record DISBANDED for all members** — in `disbandGuild()`, find the existing block that logs and fires the event. Add history recording RIGHT AFTER `memberRepository.removeByGuild(guildId)` and BEFORE `rankRepository.removeByGuild(guildId)` (members are already captured in `memberIds`):

```kotlin
// Close membership history stints for all members (guild disbanded)
memberIds.forEach { memberId ->
    historyRepository.closeStint(memberId, guildId, DepartureReason.DISBANDED)
}
```

The `memberIds` set is already populated just above:
```kotlin
// Capture member IDs before removal so the disbandment event carries them
val memberIds = memberService.getGuildMembers(guildId).map { it.playerId }.toSet()

// Remove all members
memberRepository.removeByGuild(guildId)

// <-- insert the closeStint loop here
```

**Step 4: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildServiceBukkit.kt
git add src/main/kotlin/net/lumalyte/lg/di/Modules.kt
git commit -m "feat: hook DISBANDED membership history recording into guild disband flow"
```

---

## Task 7: Implement /guild history command

**Files:**
- Modify: `src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt`

**Step 1: Add imports** (near the top with other imports in GuildCommand.kt)

```kotlin
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.domain.entities.DepartureReason
```

`MembershipHistoryRepository` is already registered in Koin, so inject it alongside the other repositories already in `GuildCommand`:

```kotlin
private val historyRepository: MembershipHistoryRepository by inject()
```

Find where the other `by inject()` lines are in `GuildCommand` and add it there.

**Step 2: Add the subcommand handler** — find a logical place near other info-reading subcommands (e.g., near `@Subcommand("info")`):

```kotlin
@Subcommand("history")
@CommandPermission("lumaguilds.guild.history")
@CommandCompletion("@players")
fun onHistory(player: Player, targetPlayerName: String) {
    // Look up the target — try online first, then offline cache
    val onlineTarget = Bukkit.getPlayerExact(targetPlayerName)
    val targetId: UUID
    val displayName: String

    if (onlineTarget != null) {
        targetId = onlineTarget.uniqueId
        displayName = onlineTarget.name
    } else {
        @Suppress("DEPRECATION")
        val offlineTarget = Bukkit.getOfflinePlayer(targetPlayerName)
        if (!offlineTarget.hasPlayedBefore()) {
            player.sendMessage("§cPlayer '§6$targetPlayerName§c' has never played on this server.")
            return
        }
        targetId = offlineTarget.uniqueId
        displayName = offlineTarget.name ?: targetPlayerName
    }

    val history = historyRepository.getByPlayer(targetId)

    if (history.isEmpty()) {
        player.sendMessage("§7$displayName has no guild history.")
        return
    }

    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(java.time.ZoneId.systemDefault())

    player.sendMessage("§6§l╔══ Guild History: $displayName ══╗")
    player.sendMessage("§7Total guilds joined: §e${history.size}")
    player.sendMessage("")

    history.forEachIndexed { index, entry ->
        val guildName = guildService.getGuild(entry.guildId)?.name
        val guildDisplay = if (guildName != null) "§a$guildName" else "§8[UNKNOWN]"
        val joinDate = formatter.format(entry.joinedAt)

        val suffix = when {
            entry.isOpen -> "§a(current)"
            entry.departureReason == DepartureReason.LEFT -> "§7Left"
            entry.departureReason == DepartureReason.KICKED -> "§cKicked"
            entry.departureReason == DepartureReason.DISBANDED -> "§8Guild Disbanded"
            else -> ""
        }

        player.sendMessage("§f${index + 1}. $guildDisplay §7• Joined §e$joinDate §7• $suffix")
    }

    player.sendMessage("§6§l╚${"═".repeat(20 + displayName.length)}╝")
}
```

**Step 3: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/net/lumalyte/lg/interaction/commands/GuildCommand.kt
git commit -m "feat: implement /guild history <player> command"
```

---

## Task 8: Manual verification

Since this is a Minecraft plugin, test by running the server and executing these scenarios in order:

**Test 1 — No history**
```
/guild history <new_player>
Expected: "<player> has no guild history."
```

**Test 2 — Join records history (open stint)**
```
1. Have a test player join a guild (/guild join <name> or accept invite)
2. /guild history <test_player>
Expected: "1. §a<GuildName> • Joined <today> • §a(current)"
   Total guilds joined: 1
```

**Test 3 — Leave records reason**
```
1. Have the test player /guild leave
2. /guild history <test_player>
Expected: "1. §a<GuildName> • Joined <date> • §7Left"
```

**Test 4 — Kick records reason**
```
1. Re-join a guild, then have an officer /guild kick <test_player>
2. /guild history <test_player>
Expected: "2. §a<GuildName> • Joined <date> • §cKicked"
   Total guilds joined: 2
```

**Test 5 — Disband shows [UNKNOWN]**
```
1. Re-join a guild (or create one)
2. Disband the guild (/guild disband)
3. /guild history <test_player>
Expected: last entry shows "§8[UNKNOWN] • Joined <date> • §8Guild Disbanded"
```

**Test 6 — Offline player lookup**
```
/guild history <offline_player_who_has_history>
Expected: history displays correctly without that player being online
```

---

## Summary of all files changed

| Action | File |
|--------|------|
| Create | `domain/entities/MembershipHistory.kt` |
| Create | `application/persistence/MembershipHistoryRepository.kt` |
| Create | `infrastructure/persistence/guilds/MembershipHistoryRepositorySQLite.kt` |
| Modify | `infrastructure/services/MemberServiceBukkit.kt` |
| Modify | `infrastructure/services/GuildServiceBukkit.kt` |
| Modify | `di/Modules.kt` |
| Modify | `interaction/commands/GuildCommand.kt` |
