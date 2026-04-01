# Guild Invitations System - Implementation Complete

## Summary

The guild invitations system has been fully implemented with commands, database persistence, and sound effects.

## What Was Implemented

### 1. Repository Layer (Already Existed)
- **`GuildInvitationRepository`** interface - defines invitation persistence operations
- **`GuildInvitationRepositorySQLite`** - SQLite implementation with in-memory caching
- **Database Schema** - `guild_invitations` table in `schema-sqlite.sql`

### 2. Service Layer (Already Existed)
- **`InvitationService`** interface - defines invitation business logic
- **`HytaleInvitationService`** - Hytale implementation with full validation

### 3. Commands (NEW - Implemented Now)
All commands are subcommands of `/guild`:

#### `/guild invite <player>`
- Sends an invitation to a player to join your guild
- Validates that inviter is in a guild
- Checks for duplicate invitations
- Plays "invitation sent" sound effect
- **Note**: Currently requires UUID format (player lookup by name not yet implemented)

#### `/guild accept <guild>`
- Accepts a pending guild invitation by guild name
- Adds player to guild with default rank
- Removes the invitation after acceptance
- Plays "invitation accepted" sound effect

#### `/guild decline <guild>`
- Declines a pending guild invitation by guild name
- Removes the invitation
- Plays "invitation declined" sound effect

#### `/guild invites`
- Lists all pending guild invitations for the player
- Shows guild name and inviter name for each invitation
- Provides helpful usage hints for accept/decline commands

### 4. Sound Effects (NEW - Implemented Now)
Created **`GuildSounds.kt`** with the following sounds:

- **INVITATION_SENT**: `SFX/UI/Discovery/SFX_Discovery_Z1_Medium` (volume 0.7, pitch 1.0)
- **INVITATION_RECEIVED**: `SFX/UI/Interactions/Benches/SFX_Workbench_Craft` (volume 0.8, pitch 1.2)
- **INVITATION_ACCEPTED**: `SFX/Crafting/SFX_Workbench_Upgrade_Complete_Default` (volume 0.8, pitch 1.0)
- **INVITATION_DECLINED**: `SFX/UI/Interactions/Benches/SFX_Furnace_Bench_Processing_Failed` (volume 0.5, pitch 0.9)
- **GUILD_CREATED**: `SFX/UI/Discovery/SFX_Discovery_Z1_Medium` (volume 0.9, pitch 1.0)

All sounds are initialized on plugin startup via `GuildSounds.initialize()`.

### 5. Plugin Integration
- **`LumaGuildsHytale.kt`** - Added `GuildSounds.initialize()` in `start()` method
- **`GuildCommand.kt`** - Registered all 4 new invitation commands
- **Koin DI** - `InvitationService` already registered in `HytaleModules.kt`

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS guild_invitations (
    guild_id TEXT NOT NULL,
    guild_name TEXT NOT NULL,
    invited_player_id TEXT NOT NULL,
    inviter_player_id TEXT NOT NULL,
    inviter_name TEXT NOT NULL,
    invited_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, invited_player_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
```

## Service Logic Highlights

### sendInvitation()
1. Validates inviter is a guild member
2. Checks target player is not already in the guild
3. Checks for existing invitation (prevents duplicates)
4. Retrieves inviter name from PlayerService
5. Creates and stores invitation with timestamp

### acceptInvitation()
1. Retrieves the invitation
2. Validates player is not already in another guild
3. Gets default rank for new members
4. Adds player to guild as a member
5. Removes the invitation

### declineInvitation()
1. Simply removes the invitation from storage

### getPlayerInvitations()
1. Returns all pending invitations for a player
2. Results are sorted by timestamp (oldest first)

## Files Modified/Created

### New Files
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/sounds/GuildSounds.kt`
- `docs/GUILD_INVITATIONS_COMPLETE.md` (this file)

### Modified Files
- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/commands/GuildCommand.kt`
  - Added import for `GuildSounds`
  - Added import for `InvitationService`
  - Registered 4 new invitation commands
  - Added sound playback to `/guild create`
  - Created `GuildInviteCommand` class
  - Created `GuildAcceptCommand` class
  - Created `GuildDeclineCommand` class
  - Created `GuildInvitesCommand` class

- `src/main/kotlin/net/lumalyte/lg/infrastructure/hytale/LumaGuildsHytale.kt`
  - Added import for `GuildSounds`
  - Added `GuildSounds.initialize()` call in `start()`

- `src/main/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildInvitationRepositorySQLite.kt`
  - Fixed field name from `timestamp` to `invited_at` to match schema
  - Fixed SQL INSERT statement column name
  - Fixed SQL DELETE WHERE clause column name

## Known Limitations

### Player Lookup by Name
Currently, `/guild invite` requires a UUID instead of a player name:
```
/guild invite 550e8400-e29b-41d4-a716-446655440000
```

This is because we don't yet have a player name → UUID lookup service. Future enhancement:
- Add `PlayerService.getPlayerIdByName(name: String): UUID?` method
- Update `GuildInviteCommand` to use name lookup

### Permission System
Currently, any guild member can send invitations. Future enhancement:
- Check rank permissions before allowing invites
- Add `CAN_INVITE` flag to rank permissions
- Validate inviter has permission in `sendInvitation()`

### Invitation Expiration
The system supports expiration via `removeOlderThan()` but it's not automatically scheduled. Future enhancement:
- Schedule a task to clean up old invitations (e.g., every hour)
- Make expiration time configurable (default 24 hours)

### Notification System
When a player receives an invitation, they are not notified in real-time. Future enhancement:
- Send a notification message when online player receives invite
- Show pending invitation count on join
- Add configurable notification sounds for recipients

## Testing Checklist

Once the plugin is running in Hytale, test these scenarios:

- [ ] Create a guild with `/guild create TestGuild`
- [ ] Get your UUID and a friend's UUID
- [ ] Send invitation: `/guild invite <friend-uuid>`
- [ ] Check invitations: `/guild invites`
- [ ] Accept invitation: `/guild accept TestGuild`
- [ ] Verify player joined guild with `/guild info`
- [ ] Send another invite, then decline: `/guild decline TestGuild`
- [ ] Verify sound effects play for each action
- [ ] Test duplicate invitation prevention
- [ ] Test accepting invite when already in guild (should fail)

## Integration with Existing Systems

### Dependencies
The invitation system properly integrates with:
- **GuildRepository** - Validates guild exists, gets guild name
- **MemberRepository** - Checks membership, adds new members
- **RankRepository** - Gets default rank for new members
- **PlayerService** - Gets player names for display

### Sound System
Follows the same pattern as `ClaimSounds.kt`:
- Static object with lazy initialization
- `SoundEvent.getAssetMap()` for ID resolution
- `PlaySoundEvent2D` for UI feedback
- `PlayerRef.getPacketHandler().write()` for playback

## Next Steps

As mentioned in the user's request, the next action is:
> "check the main branch features to see what else we need to have complete feature parity with the minecraft version of the plugin"

The guild invitations system is now complete and ready for testing in-game!
