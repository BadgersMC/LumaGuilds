# Guild History Feature Design

**Date:** 2026-03-11
**Feature:** `/guild history <player>` command
**Status:** Approved, ready for implementation

---

## Overview

Track every guild a player has ever belonged to and expose it via `/guild history <player>`. Disbanded guilds intentionally display as `[UNKNOWN]` — a deliberate mystery feature. The total guild count only ever increases, serving as a lifetime stat.

---

## Data Model

### Entity: `MembershipHistory`

```kotlin
data class MembershipHistory(
    val id: UUID,
    val playerId: UUID,
    val guildId: UUID,       // stored without name snapshot; UNKNOWN derived at display time
    val joinedAt: Instant,
    val departedAt: Instant?,          // null = open stint (currently in guild)
    val departureReason: DepartureReason?  // null if open
)

enum class DepartureReason { LEFT, KICKED, DISBANDED }
```

**Design note:** `guildId` is stored without a name snapshot so that when a guild is disbanded and later looked up as `null`, the display intentionally shows `§8[UNKNOWN]` — this is the feature, not a bug.

### SQLite Schema

```sql
CREATE TABLE IF NOT EXISTS membership_history (
    id TEXT PRIMARY KEY,
    player_id TEXT NOT NULL,
    guild_id TEXT NOT NULL,
    joined_at TEXT NOT NULL,
    departed_at TEXT,
    departure_reason TEXT
);
CREATE INDEX IF NOT EXISTS idx_membership_history_player ON membership_history(player_id);
```

No in-memory cache — queried only on command invocation, not on the hot path.

---

## Repository Interface

```kotlin
interface MembershipHistoryRepository {
    fun openStint(playerId: UUID, guildId: UUID): Boolean
    fun closeStint(playerId: UUID, guildId: UUID, reason: DepartureReason): Boolean
    fun getByPlayer(playerId: UUID): List<MembershipHistory>  // ordered by joinedAt ASC
}
```

SQLite implementation: `MembershipHistoryRepositorySQLite` at `infrastructure/persistence/guilds/`.

---

## Recording Hooks

### On JOIN — `MemberServiceBukkit.addMember()`

After `memberRepository.add(member)` succeeds:

```kotlin
historyRepository.openStint(playerId, guildId)
```

### On LEAVE or KICK — `MemberServiceBukkit.removeMember()`

After `memberRepository.remove(playerId, guildId)` succeeds:

```kotlin
val reason = if (actorId == playerId) DepartureReason.LEFT else DepartureReason.KICKED
historyRepository.closeStint(playerId, guildId, reason)
```

### On DISBAND — `GuildServiceBukkit.disbandGuild()`

After `memberRepository.removeByGuild(guildId)`, using the already-captured `memberIds` set:

```kotlin
memberIds.forEach { memberId ->
    historyRepository.closeStint(memberId, guildId, DepartureReason.DISBANDED)
}
```

`MembershipHistoryRepository` is injected into both `MemberServiceBukkit` and `GuildServiceBukkit` as an additional constructor parameter. The Koin `guildsModule()` registers the singleton and passes it via `get()`.

---

## Command

```text
/guild history <player>
Permission: lumaguilds.guild.history
Tab-complete: @players (online players)
```

Registered as `@Subcommand("history")` in `GuildCommand.kt`.

Player lookup uses `Bukkit.getOfflinePlayer(name)` so offline players can be queried. If the UUID has never played (never stored), return the "no history" message.

---

## Display Format

Entries ordered oldest → newest (so the index numbers match "total guilds joined" naturally).

```text
§6§l╔══ Guild History: Fain ══╗
§7Total guilds joined: §e4

§f1. §aPhoenix Rising §7• Joined §e2024-01-15 §7• §cKicked
§f2. §8[UNKNOWN] §7• Joined §e2024-03-02 §7• §8Guild Disbanded
§f3. §aShadow Legion §7• Joined §e2024-06-10 §7• §7Left
§f4. §aIronclad §7• Joined §e2024-09-22 §a(current)
§6§l╚═══════════════════════╝
```

**Color key:**

- `§a` — active guild name
- `§8[UNKNOWN]` — disbanded guild (intentional mystery)
- `§c` Kicked / `§7` Left / `§8` Guild Disbanded — departure reasons
- `§a(current)` — open stint (no `departedAt`)

**Edge cases:**

- Player has never been in a guild → `§7<player> has no guild history.`
- Player name not found → `§cPlayer '<name>' has never played on this server.`
- Total count = `history.size` (includes the open/current entry if present)

---

## Files to Create

| File | Purpose |
|------|---------|
| `domain/entities/MembershipHistory.kt` | Data class + `DepartureReason` enum |
| `application/persistence/MembershipHistoryRepository.kt` | Repository interface |
| `infrastructure/persistence/guilds/MembershipHistoryRepositorySQLite.kt` | SQLite implementation |

## Files to Modify

| File | Change |
|------|--------|
| `infrastructure/services/MemberServiceBukkit.kt` | Add `MembershipHistoryRepository` param; call `openStint`/`closeStint` |
| `infrastructure/services/GuildServiceBukkit.kt` | Add `MembershipHistoryRepository` param; call `closeStint` × members on disband |
| `di/Modules.kt` | Register `MembershipHistoryRepository` singleton; update `get()` counts for both services |
| `interaction/commands/GuildCommand.kt` | Add `@Subcommand("history")` handler |
