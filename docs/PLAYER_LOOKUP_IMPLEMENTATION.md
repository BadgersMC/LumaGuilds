# Player Lookup Implementation - Complete

## Problem Solved

The `/guild invite` command previously required a player's UUID instead of their username, making it difficult to use:
```
/guild invite 550e8400-e29b-41d4-a716-446655440000  ❌ Hard to remember
```

## Solution Implemented

Players can now use usernames directly:
```
/guild invite PlayerName  ✅ Easy to use
```

---

## Implementation Details

### API Discovery

Found the correct Hytale API through exploration of decompiled server code:

**Class**: `com.hypixel.hytale.server.core.universe.Universe`

**Method**:
```kotlin
fun getPlayerByUsername(
    playerName: String,
    nameMatching: com.hypixel.hytale.server.core.NameMatching
): PlayerRef?
```

**NameMatching Options**:
- `EXACT_IGNORE_CASE` - Case-insensitive exact match (used in implementation)

### Code Changes

**File**: `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/commands/GuildCommand.kt`

**Lines**: 311-332

**Implementation**:
```kotlin
// Look up target player by username
val targetPlayerRef = universe.getPlayerByUsername(
    targetPlayerName,
    com.hypixel.hytale.server.core.NameMatching.EXACT_IGNORE_CASE
)

if (targetPlayerRef == null) {
    context.sendMessage(
        Message.raw("Player '$targetPlayerName' not found or is not online!")
            .color("red")
    )
    return
}

val targetPlayerId = targetPlayerRef.getUuid()!!  // Safe: PlayerRef always has UUID
```

---

## Universe API Reference

### Player Lookup Methods

```kotlin
// Get player by UUID
fun getPlayer(playerId: UUID): PlayerRef?

// Get player by username
fun getPlayerByUsername(
    playerName: String,
    nameMatching: NameMatching
): PlayerRef?

// Get all online players
val players: List<PlayerRef>

// Get world by UUID
fun getWorld(worldUuid: UUID): World?
```

### PlayerRef Properties

```kotlin
val username: String              // Player's username
val uuid: UUID                    // Player's UUID
val language: String?             // Player's language preference
val reference: Ref<EntityStore>?  // Entity reference in ECS
```

### PlayerRef Methods

```kotlin
fun getUuid(): UUID?              // Get player UUID (always non-null)
fun getWorldUuid(): UUID?         // Get current world UUID
fun getTransform(): Transform     // Get player's position/rotation
fun sendMessage(message: Message) // Send message to player
fun getPacketHandler(): PacketHandler  // Get packet handler for custom packets
```

---

## External Player Lookup Options (Not Used)

During research, several external APIs were found but not needed:

### 1. PlayerDB API (HytaleID.com)
```
GET https://playerdb.co/api/player/minecraft/username
```
Returns player info including UUID and avatar.

**Not Used**: Requires external HTTP requests, adds latency, requires internet connection.

### 2. Nitrado Query Plugin
Provides HTTP endpoint for connected players.

**Not Used**: Only returns currently connected players, requires additional plugin.

### 3. Official Authentication APIs
Server provider authentication endpoints.

**Not Used**: Requires authentication setup, designed for server hosting providers.

**Decision**: Use Hytale's built-in `Universe` API - faster, more reliable, no external dependencies.

---

## Testing Checklist

Once the plugin is deployed to a Hytale server:

### Basic Functionality
- [ ] `/guild invite ValidUsername` - Should work
- [ ] `/guild invite InvalidUsername` - Should show error
- [ ] `/guild invite UPPERCASE` - Should work (case-insensitive)
- [ ] `/guild invite lowercase` - Should work (case-insensitive)
- [ ] `/guild invite OfflinePlayer` - Should show "not online" error

### Edge Cases
- [ ] Invite player already in guild - Should fail gracefully
- [ ] Invite self - Should handle appropriately
- [ ] Invite with duplicate pending invitation - Should detect and warn
- [ ] Invite from non-guild member - Should reject

### Sound Effects
- [ ] Successful invitation - Should play "invitation sent" sound
- [ ] Failed invitation - Should not play sound

---

## Related Files

### Modified Files
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/commands/GuildCommand.kt`
  - Lines 311-332: Player lookup implementation
  - Lines 324-331: Error handling for not found/offline players

### Reference Files (Existing Usage)
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/commands/ClaimCommand.kt`
  - Lines 457, 575: Uses `getPlayerByUsername()` for trust/untrust commands

- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/services/HytalePlayerService.kt`
  - Line 38: Uses `getPlayer(UUID)` for UUID lookup
  - Line 77: Uses `universe.players` for online player list

---

## Benefits

### User Experience
- ✅ Easier to remember player names than UUIDs
- ✅ Consistent with other commands (`/claim trust PlayerName`)
- ✅ Natural command syntax
- ✅ Clear error messages when player not found

### Technical
- ✅ No external API dependencies
- ✅ Fast local lookup
- ✅ Works offline (server-local)
- ✅ Consistent with Hytale's API patterns
- ✅ Case-insensitive matching for convenience

---

## Future Enhancements

### Autocomplete Support
Consider adding tab completion for player names:
```kotlin
override fun getSuggestions(context: CommandContext): List<String> {
    val universe = Universe.get()
    return universe.players.map { it.username }
}
```

### Offline Player Support
Currently only works for online players. To support offline players:
- Option 1: Maintain player name cache in database
- Option 2: Use external PlayerDB API as fallback
- Option 3: Accept UUIDs as fallback (current behavior if name parsing fails)

**Decision**: Current online-only approach is acceptable for invitations (inviter and invitee both need to be online for real-time interaction).

---

## API Documentation Sources

- **Web Search**: Found community resources at HytaleID.com, Nitrado Query Plugin
- **Hytale Docs**: https://hytale-docs.com/docs (limited API coverage)
- **Decompiled Source**: Located methods in `com.hypixel.hytale.server.core.universe.Universe`
- **Existing Usage**: Found usage examples in ClaimCommand.kt and HytalePlayerService.kt

### Sources Referenced
- [Lookup Hytale Player UUIDs with HytaleID.com](https://nodecraft.com/blog/service-updates/lookup-hytale-player-uuids-with-hytaleid-com)
- [Hytale Server Manual](https://support.hytale.com/hc/en-us/articles/45326769420827-Hytale-Server-Manual)
- [GitHub - nitrado/hytale-plugin-query](https://github.com/nitrado/hytale-plugin-query)

---

## Conclusion

The player lookup feature is now fully implemented using Hytale's native `Universe.getPlayerByUsername()` API. This provides a seamless user experience for guild invitations without requiring external dependencies or complex workarounds.

**Status**: ✅ Complete and ready for testing
**Build**: ✅ Compiles successfully
**Next Step**: Test in-game with real players
