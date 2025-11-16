# API Reference - Complete Catalog

This is the **complete phone book** for LumaGuilds. Use this to quickly find any action, result type, entity, or component in the codebase.

## Quick Stats

- **66 Actions** across all domains
- **59 Result Types** for exhaustive error handling
- **22 Repository Interfaces** for data access
- **21 Domain Entities** representing core concepts
- **10 Value Objects** for type safety
- **20+ Commands** for player interaction
- **90+ GUI Menus** for visual interfaces

---

## Table of Contents

1. [Application Actions](#application-actions)
2. [Result Types](#result-types)
3. [Repository Interfaces](#repository-interfaces)
4. [Domain Entities](#domain-entities)
5. [Value Objects & Enums](#value-objects--enums)
6. [Commands](#commands)
7. [GUI Menus](#gui-menus)
8. [Quick Lookup Tables](#quick-lookup-tables)

---

## Application Actions

### Claim Actions (7)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `ConvertClaimToGuild` | Convert personal claim to guild ownership | `claimId, guildId` | `ConvertClaimToGuildResult` |
| `CreateClaim` | Create new claim at position | `playerId, name, position, worldId` | `CreateClaimResult` |
| `GetClaimAtPosition` | Find claim at position | `position, worldId` | `GetClaimAtPositionResult` |
| `IsNewClaimLocationValid` | Validate claim location | `position, worldId` | `IsNewClaimLocationValidResult` |
| `IsPlayerActionAllowed` | Check player permission | `playerId, claimId, action` | `IsPlayerActionAllowedResult` |
| `IsWorldActionAllowed` | Check world event permission | `claimId, action` | `IsWorldActionAllowedResult` |
| `ListPlayerClaims` | Get all player's claims | `playerId` | `Set<Claim>` |

```kotlin
// Example: Creating a claim
val result = createClaim.execute(
    playerId = player.uniqueId,
    name = "MyBase",
    position3D = Position3D(x, y, z),
    worldId = world.uid
)

when (result) {
    is CreateClaimResult.Success -> println("Created: ${result.claim.id}")
    CreateClaimResult.LimitExceeded -> println("Limit exceeded")
    // ... handle other cases
}
```

### Claim Anchor Actions (3)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `BreakClaimAnchor` | Remove claim anchor | `claimId` | `BreakClaimAnchorResult` |
| `GetClaimAnchorAtPosition` | Find anchor at position | `position, worldId` | `GetClaimAnchorAtPositionResult` |
| `MoveClaimAnchor` | Relocate claim anchor | `claimId, newPosition` | `MoveClaimAnchorResult` |

### Claim Flag Actions (6)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `DisableAllClaimFlags` | Remove all flags | `claimId` | `DisableAllClaimFlagsResult` |
| `DisableClaimFlag` | Remove specific flag | `claimId, flag` | `DisableClaimFlagResult` |
| `DoesClaimHaveFlag` | Check flag status | `claimId, flag` | `DoesClaimHaveFlagResult` |
| `EnableAllClaimFlags` | Set all flags | `claimId` | `EnableAllClaimFlagsResult` |
| `EnableClaimFlag` | Set specific flag | `claimId, flag` | `EnableClaimFlagResult` |
| `GetClaimFlags` | Get all flags | `claimId` | `Set<Flag>` |

**Available Flags:**
- `FIRE` - Fire spread/damage
- `MOB` - Mob spawning
- `EXPLOSION` - Explosion damage
- `PISTON` - Piston movement
- `FLUID` - Fluid flow
- `TREE` - Tree growth
- `SCULK` - Sculk spreading
- `DISPENSER` - Dispenser activation
- `SPONGE` - Sponge absorption
- `LIGHTNING` - Lightning strikes
- `FALLING_BLOCK` - Falling block landing
- `PASSIVE_ENTITY_VEHICLE` - Passive mob vehicles

### Claim Metadata Actions (5)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `GetClaimBlockCount` | Total blocks in claim | `claimId` | `Int` |
| `GetClaimDetails` | Get full claim data | `claimId` | `Claim?` |
| `UpdateClaimDescription` | Change description | `claimId, description` | `UpdateClaimAttributeResult` |
| `UpdateClaimIcon` | Change GUI icon | `claimId, iconMaterial` | `UpdateClaimIconResult` |
| `UpdateClaimName` | Rename claim | `claimId, newName` | `UpdateClaimNameResult` |

### Claim Partition Actions (6)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `CanRemovePartition` | Check if removable | `partitionId` | `CanRemovePartitionResult` |
| `CreatePartition` | Expand claim area | `claimId, area` | `CreatePartitionResult` |
| `GetClaimPartitions` | List all partitions | `claimId` | `Set<Partition>` |
| `GetPartitionByPosition` | Find partition at pos | `position, worldId` | `Partition?` |
| `RemovePartition` | Delete partition | `partitionId` | `RemovePartitionResult` |
| `ResizePartition` | Change partition size | `partitionId, newArea` | `ResizePartitionResult` |

### Claim Permission Actions (10)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `GetClaimPermissions` | Get claim-wide perms | `claimId` | `Set<ClaimPermission>` |
| `GetClaimPlayerPermissions` | Get player's perms | `claimId, playerId` | `Set<ClaimPermission>` |
| `GetPlayersWithPermissionInClaim` | List trusted players | `claimId` | `Set<UUID>` |
| `GrantAllClaimWidePermissions` | Give everyone all perms | `claimId` | `GrantAllClaimWidePermissionsResult` |
| `GrantAllPlayerClaimPermissions` | Give player all perms | `claimId, playerId` | `GrantAllPlayerClaimPermissionResult` |
| `GrantClaimWidePermission` | Give everyone one perm | `claimId, permission` | `GrantClaimWidePermissionResult` |
| `GrantGuildMembersClaimPermissions` | Give guild members perms | `claimId, permissions` | `GrantGuildMembersClaimPermissionsResult` |
| `GrantPlayerClaimPermission` | Give player one perm | `claimId, playerId, permission` | `GrantPlayerClaimPermissionResult` |
| `RevokeAllClaimWidePermissions` | Remove all claim-wide | `claimId` | `RevokeAllClaimWidePermissionsResult` |
| `RevokeAllPlayerClaimPermissions` | Remove all player perms | `claimId, playerId` | `RevokeAllPlayerClaimPermissionsResult` |

**Available Permissions:**
- `BUILD` - Place blocks
- `BREAK` - Break blocks (HARVEST in code)
- `CONTAINER` - Open chests/furnaces
- `DISPLAY` - Modify item frames/armor stands
- `VEHICLE` - Place/destroy vehicles
- `SIGN` - Edit signs
- `REDSTONE` - Use redstone components
- `DOOR` - Open doors/trapdoors
- `TRADE` - Trade with villagers
- `HUSBANDRY` - Interact with animals
- `DETONATE` - Use TNT/explosives
- `EVENT` - Trigger raid events
- `SLEEP` - Use beds
- `VIEW` - View lecterns

### Claim Transfer Actions (5)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `AcceptTransferRequest` | Accept transfer | `claimId, playerId` | `AcceptTransferRequestResult` |
| `DoesPlayerHaveTransferRequest` | Check pending | `claimId, playerId` | `DoesPlayerHaveTransferRequestResult` |
| `CanPlayerReceiveTransferRequest` | Validate recipient | `playerId` | `CanPlayerReceiveTransferRequestResult` |
| `OfferPlayerTransferRequest` | Send transfer request | `claimId, playerId` | `OfferPlayerTransferRequestResult` |
| `WithdrawPlayerTransferRequest` | Cancel request | `claimId, playerId` | `WithdrawPlayerTransferRequestResult` |

### Player Actions (6)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `DoesPlayerHaveClaimOverride` | Check admin bypass | `playerId` | `DoesPlayerHaveClaimOverrideResult` |
| `GetRemainingClaimBlockCount` | Get available blocks | `playerId` | `Int` |
| `IsPlayerInClaimMenu` | Check if in menu | `playerId` | `IsPlayerInClaimMenuResult` |
| `RegisterClaimMenuOpening` | Track menu open | `playerId, claimId` | `RegisterClaimMenuOpeningResult` |
| `ToggleClaimOverride` | Toggle admin bypass | `playerId` | `ToggleClaimOverrideResult` |
| `UnregisterClaimMenuOpening` | Track menu close | `playerId` | `UnregisterClaimMenuOpeningResult` |

### Player Tool Actions (6)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `GetClaimIdFromMoveTool` | Read tool data | `playerId` | `GetClaimIdFromMoveToolResult` |
| `GivePlayerClaimTool` | Give creation tool | `playerId` | `GivePlayerClaimToolResult` |
| `GivePlayerMoveTool` | Give move tool | `playerId, claimId` | `GivePlayerMoveToolResult` |
| `IsItemClaimTool` | Check if claim tool | `itemStack` | `Boolean` |
| `IsItemMoveTool` | Check if move tool | `itemStack` | `Boolean` |
| `SyncToolVisualization` | Update visualization | `playerId` | `Unit` |

### Player Visualisation Actions (10)

| Action | Purpose | Key Parameters | Returns |
|--------|---------|----------------|---------|
| `ClearSelectionVisualisation` | Clear selection | `playerId` | `Unit` |
| `DisplaySelectionVisualisation` | Show selection | `playerId, positions` | `Unit` |
| `ClearVisualisation` | Clear all visuals | `playerId` | `Unit` |
| `DisplayVisualisation` | Show claim borders | `playerId, claimId` | `Unit` |
| `GetVisualisedClaimBlocks` | Get shown blocks | `playerId` | `GetVisualisedClaimBlocksResult` |
| `IsPlayerVisualising` | Check if active | `playerId` | `IsPlayerVisualisingResult` |
| `GetVisualiserMode` | Get current mode | `playerId` | `GetVisualiserModeResult` |
| `RefreshVisualisation` | Update display | `playerId` | `Unit` |
| `ScheduleClearVisualisation` | Auto-clear later | `playerId, delayTicks` | `ScheduleClearVisualisationResult` |
| `ToggleVisualiserMode` | Switch mode | `playerId` | `ToggleVisualiserModeResult` |

---

## Result Types

All actions return sealed classes for exhaustive error handling. Here are the result types:

### Common Result Patterns

```kotlin
// Success with data
data class Success(val data: T) : Result()

// Success without data
object Success : Result()

// Not found
object NotFound : Result()

// Permission denied
object PermissionDenied : Result()

// Already exists
object AlreadyExists : Result()

// Storage error
object StorageError : Result()
```

### Claim Results

| Result Class | Variants |
|--------------|----------|
| `CreateClaimResult` | `Success(claim)`, `NameCannotBeBlank`, `LimitExceeded`, `NameAlreadyExists`, `TooCloseToWorldBorder` |
| `GetClaimAtPositionResult` | `Success(claim)`, `NoClaimFound`, `StorageError` |
| `IsPlayerActionAllowedResult` | `Allowed`, `Denied(reason)`, `ClaimNotFound` |
| `UpdateClaimNameResult` | `Success`, `ClaimNotFound`, `NameCannotBeBlank`, `NameAlreadyExists`, `TooLong` |
| `ConvertClaimToGuildResult` | `Success(claim)`, `ClaimNotFound`, `GuildNotFound`, `ClaimAlreadyGuildOwned` |

### Permission Results

| Result Class | Variants |
|--------------|----------|
| `GrantPlayerClaimPermissionResult` | `Success`, `ClaimNotFound`, `AlreadyExists`, `StorageError` |
| `RevokePlayerClaimPermissionResult` | `Success`, `ClaimNotFound`, `PermissionNotSet` |

### Partition Results

| Result Class | Variants |
|--------------|----------|
| `CreatePartitionResult` | `Success(partition)`, `ClaimNotFound`, `OverlapsExistingClaim`, `NotEnoughClaimBlocks`, `OutsideWorldBorder` |
| `RemovePartitionResult` | `Success`, `PartitionNotFound`, `LastPartition`, `WouldIsolatePartitions` |
| `CanRemovePartitionResult` | `CanRemove`, `WouldIsolatePartitions`, `LastPartition`, `PartitionNotFound` |

---

## Repository Interfaces

### Claim Repositories

```kotlin
// ClaimRepository
interface ClaimRepository {
    fun getAll(): Set<Claim>
    fun getById(id: UUID): Claim?
    fun getByPlayer(playerId: UUID): Set<Claim>
    fun getByTeam(teamId: UUID): Set<Claim>
    fun getByName(playerId: UUID, name: String): Claim?
    fun getByPosition(position: Position3D, worldId: UUID): Claim?
    fun add(claim: Claim): Boolean
    fun update(claim: Claim): Boolean
    fun remove(claimId: UUID): Boolean
}

// PartitionRepository
interface PartitionRepository {
    fun getAll(): Set<Partition>
    fun getById(id: UUID): Partition?
    fun getByClaim(claimId: UUID): Set<Partition>
    fun getByPosition(position: Position, worldId: UUID): Partition?
    fun getByChunk(chunkPos: Position2D, worldId: UUID): Set<Partition>
    fun add(partition: Partition): Boolean
    fun update(partition: Partition): Boolean
    fun remove(partitionId: UUID): Boolean
    fun removeByClaim(claimId: UUID): Boolean
}

// ClaimPermissionRepository
interface ClaimPermissionRepository {
    fun doesClaimHavePermission(claimId: UUID, permission: ClaimPermission): Boolean
    fun getByClaim(claimId: UUID): Set<ClaimPermission>
    fun add(claimId: UUID, permission: ClaimPermission): Boolean
    fun remove(claimId: UUID, permission: ClaimPermission): Boolean
    fun removeByClaim(claimId: UUID): Boolean
}

// PlayerAccessRepository
interface PlayerAccessRepository {
    fun doesPlayerHavePermission(claimId: UUID, playerId: UUID, permission: ClaimPermission): Boolean
    fun getForPlayerInClaim(claimId: UUID, playerId: UUID): Set<ClaimPermission>
    fun getPlayersWithPermissionInClaim(claimId: UUID): Set<UUID>
    fun add(claimId: UUID, playerId: UUID, permission: ClaimPermission): Boolean
    fun remove(claimId: UUID, playerId: UUID, permission: ClaimPermission): Boolean
    fun removeByClaim(claimId: UUID): Boolean
}

// ClaimFlagRepository
interface ClaimFlagRepository {
    fun doesClaimHaveFlag(claimId: UUID, flag: Flag): Boolean
    fun getByClaim(claimId: UUID): Set<Flag>
    fun add(claimId: UUID, flag: Flag): Boolean
    fun remove(claimId: UUID, flag: Flag): Boolean
    fun removeByClaim(claimId: UUID): Boolean
}
```

### Guild Repositories

```kotlin
// GuildRepository
interface GuildRepository {
    fun getAll(): Set<Guild>
    fun getById(id: UUID): Guild?
    fun getByName(name: String): Guild?
    fun getByPlayer(playerId: UUID): Guild?
    fun add(guild: Guild): Boolean
    fun update(guild: Guild): Boolean
    fun remove(guildId: UUID): Boolean
    fun isNameTaken(name: String): Boolean
}

// MemberRepository
interface MemberRepository {
    fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): Member?
    fun getByGuild(guildId: UUID): Set<Member>
    fun getGuildsByPlayer(playerId: UUID): Set<UUID>
    fun add(member: Member): Boolean
    fun update(member: Member): Boolean
    fun remove(playerId: UUID, guildId: UUID): Boolean
}
```

### Player Repositories

```kotlin
// PlayerStateRepository
interface PlayerStateRepository {
    fun get(playerId: UUID): PlayerState?
    fun add(state: PlayerState): Boolean
    fun update(state: PlayerState): Boolean
    fun remove(playerId: UUID): Boolean
}
```

---

## Domain Entities

### Core Entities

```kotlin
// Claim - Protected land area
data class Claim(
    val id: UUID,
    val worldId: UUID,
    val playerId: UUID,        // Owner
    val teamId: UUID?,         // Guild owner (null = personal)
    val creationTime: Instant,
    val name: String,          // 1-50 chars
    val description: String,   // 0-300 chars
    val position: Position3D,  // Bell anchor position
    val icon: String           // Material name
)

// Partition - Rectangular claim area
data class Partition(
    val id: UUID,
    val claimId: UUID,
    val area: Area             // Value object
)

// Guild - Player organization
data class Guild(
    val id: UUID,
    val name: String,          // 1-32 chars
    val banner: String?,       // Serialized ItemStack
    val emoji: String?,        // ":name:" format
    val tag: String?,          // MiniMessage formatted
    val description: String?,  // 0-100 chars
    val level: Int,            // > 0
    val bankBalance: Int,      // >= 0
    val mode: GuildMode,       // PEACEFUL or HOSTILE
    val createdAt: Instant
)

// Member - Guild membership
data class Member(
    val playerId: UUID,
    val guildId: UUID,
    val rankId: UUID,
    val joinedAt: Instant
)

// War - Guild conflict
data class War(
    val id: UUID,
    val declaringGuildId: UUID,
    val defendingGuildId: UUID,
    val declaredAt: Instant,
    val status: WarStatus,
    val objectives: WarObjectives
)

// PlayerState - Player session data
data class PlayerState(
    val playerId: UUID,
    val claimOverride: Boolean,
    val isHoldingClaimTool: Boolean,
    val isVisualisingClaims: Boolean,
    val isInClaimMenu: Boolean
)
```

### Entity Properties Quick Reference

| Entity | Key Properties | Max Size |
|--------|---------------|----------|
| `Claim` | name, description, icon | name: 50, desc: 300 |
| `Guild` | name, description, emoji, tag | name: 32, desc: 100 |
| `Partition` | area (Position2D bounds) | N/A |
| `Member` | playerId, guildId, rankId | N/A |
| `War` | guilds, status, objectives | N/A |

---

## Value Objects & Enums

### Position Types

```kotlin
// 3D position (x, y, z)
data class Position3D(val x: Int, val y: Int, val z: Int)

// 2D position (x, z) - for areas
data class Position2D(val x: Int, val z: Int) {
    fun getChunk(): Position2D  // Converts to chunk coords
}

// Rectangular area
data class Area(
    val lowerPosition2D: Position2D,
    val upperPosition2D: Position2D
) {
    fun isPositionInArea(pos: Position): Boolean
    fun isAreaOverlap(other: Area): Boolean
    fun getBlockCount(): Int
    fun getChunks(): List<Position2D>
}
```

### Enums

#### ClaimPermission (14 values)

```kotlin
enum class ClaimPermission {
    BUILD,      // Place blocks
    HARVEST,    // Break blocks
    CONTAINER,  // Open containers
    DISPLAY,    // Modify displays
    VEHICLE,    // Place/destroy vehicles
    SIGN,       // Edit signs
    REDSTONE,   // Use redstone
    DOOR,       // Open doors
    TRADE,      // Villager trading
    HUSBANDRY,  // Animal interaction
    DETONATE,   // Use explosives
    EVENT,      // Trigger events
    SLEEP,      // Use beds
    VIEW        // View lecterns
}
```

#### Flag (12 values)

```kotlin
enum class Flag {
    FIRE,                    // Fire spread
    MOB,                     // Mob spawning
    EXPLOSION,               // Explosions
    PISTON,                  // Pistons
    FLUID,                   // Fluid flow
    TREE,                    // Tree growth
    SCULK,                   // Sculk spread
    DISPENSER,               // Dispensers
    SPONGE,                  // Sponges
    LIGHTNING,               // Lightning
    FALLING_BLOCK,           // Falling blocks
    PASSIVE_ENTITY_VEHICLE   // Passive mobs in vehicles
}
```

#### GuildMode (2 values)

```kotlin
enum class GuildMode {
    PEACEFUL,  // Cannot declare war
    HOSTILE    // Can participate in wars
}
```

---

## Commands

### Claim Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/claim` | `lumaguilds.command.claim` | Give claim tool |
| `/claim info` | `lumaguilds.command.claim.info` | Show claim info |
| `/claim rename <name>` | `lumaguilds.command.claim.rename` | Rename claim |
| `/claim description <text>` | `lumaguilds.command.claim.description` | Set description |
| `/claim addflag <flag>` | `lumaguilds.command.claim.addflag` | Add flag |
| `/claim removeflag <flag>` | `lumaguilds.command.claim.removeflag` | Remove flag |
| `/claim trust <player> <perm>` | `lumaguilds.command.claim.trust` | Grant permission |
| `/claim untrust <player> <perm>` | `lumaguilds.command.claim.untrust` | Revoke permission |
| `/claim trustall <perm>` | `lumaguilds.command.claim.trustall` | Grant to all |
| `/claim untrustall <perm>` | `lumaguilds.command.claim.untrustall` | Revoke from all |
| `/claim trustlist` | `lumaguilds.command.claim.trustlist` | List trusted |
| `/claim partitions` | `lumaguilds.command.claim.partitions` | List partitions |
| `/claim remove` | `lumaguilds.command.claim.remove` | Delete claim |
| `/claimlist` | `lumaguilds.command.claimlist` | List claims |
| `/claimmenu` | `lumaguilds.command.claimmenu` | Open GUI |

### Guild Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/guild` | `lumaguilds.command.guild` | Main guild command |
| `/guild create <name>` | `lumaguilds.command.guild.create` | Create guild |
| `/guild disband` | `lumaguilds.command.guild.disband` | Disband guild |
| `/guild invite <player>` | `lumaguilds.command.guild.invite` | Invite player |
| `/guild kick <player>` | `lumaguilds.command.guild.kick` | Kick member |
| `/guild leave` | `lumaguilds.command.guild.leave` | Leave guild |
| `/guild promote <player>` | `lumaguilds.command.guild.promote` | Promote member |
| `/guild demote <player>` | `lumaguilds.command.guild.demote` | Demote member |

### Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/claimoverride` | `lumaguilds.command.claimoverride` | Toggle bypass |
| `/lumaguilds reload` | `lumaguilds.admin.reload` | Reload config |

---

## GUI Menus

### Claim Menus

- **ClaimCreationMenu** - Create new claim
- **ClaimManagementMenu** - Main claim control panel
- **ClaimFlagMenu** - Toggle protection flags
- **ClaimPlayerPermissionsMenu** - Manage player permissions
- **ClaimWidePermissionsMenu** - Manage default permissions
- **ClaimTrustMenu** - Trust management
- **ClaimTransferMenu** - Transfer ownership
- **ClaimListMenu** - View all claims
- **ClaimNamingMenu** - Name/rename claim
- **ClaimIconMenu** - Change icon

### Guild Menus

- **GuildControlPanelMenu** - Main guild dashboard
- **GuildBankMenu** - Bank operations
- **GuildMemberListMenu** - View members
- **GuildMemberManagementMenu** - Manage members
- **GuildRankListMenu** - View ranks
- **GuildRankManagementMenu** - Edit ranks
- **GuildInviteMenu** - Invite players
- **GuildKickMenu** - Kick members
- **GuildWarManagementMenu** - War management
- **GuildRelationsMenu** - Guild relations
- **GuildSettingsMenu** - Guild settings
- **GuildStatisticsMenu** - Guild stats
- **GuildProgressionInfoMenu** - Level/XP info

---

## Quick Lookup Tables

### "Can I...?" Quick Reference

| Question | Action to Use | Permission Check |
|----------|---------------|------------------|
| Create a claim? | `CreateClaim` | Check `claimLimit` first |
| Rename a claim? | `UpdateClaimName` | Must be owner |
| Break blocks? | `IsPlayerActionAllowed` with `HARVEST` | Check permissions |
| Trust a player? | `GrantPlayerClaimPermission` | Must be owner |
| Toggle PvP? | `EnableClaimFlag` / `DisableClaimFlag` with `Flag.PVP` | Must be owner |
| Expand claim? | `CreatePartition` | Check `claimBlockLimit` |
| Transfer claim? | `OfferPlayerTransferRequest` | Must be owner |
| Create guild? | `CreateGuild` | Check guild limit |
| Declare war? | `DeclareWar` | Guild must be HOSTILE mode |

### "What permission allows...?" Quick Reference

| Player Action | ClaimPermission Needed |
|---------------|------------------------|
| Place blocks | `BUILD` |
| Break blocks | `HARVEST` |
| Open chests | `CONTAINER` |
| Modify item frames | `DISPLAY` |
| Place minecarts | `VEHICLE` |
| Edit signs | `SIGN` |
| Use buttons/levers | `REDSTONE` |
| Open doors | `DOOR` |
| Trade with villagers | `TRADE` |
| Breed animals | `HUSBANDRY` |
| Use TNT | `DETONATE` |
| Sleep in beds | `SLEEP` |
| Read lecterns | `VIEW` |

### "What flag controls...?" Quick Reference

| World Event | Flag Needed |
|-------------|-------------|
| Fire spread | `FIRE` |
| Mob spawning | `MOB` |
| Creeper explosions | `EXPLOSION` |
| Piston pushing | `PISTON` |
| Water flowing | `FLUID` |
| Sapling growing | `TREE` |
| Sculk spreading | `SCULK` |
| Dispenser firing | `DISPENSER` |
| Sponge absorbing | `SPONGE` |
| Lightning strikes | `LIGHTNING` |
| Anvils falling | `FALLING_BLOCK` |

---

## Usage Examples

### Example 1: Check if player can build

```kotlin
val result = isPlayerActionAllowed.execute(
    playerId = player.uniqueId,
    position = blockPosition,
    worldId = world.uid,
    permission = ClaimPermission.BUILD
)

when (result) {
    IsPlayerActionAllowedResult.Allowed -> {
        // Allow building
    }
    is IsPlayerActionAllowedResult.Denied -> {
        player.sendMessage("Cannot build in ${result.claim.name}")
    }
}
```

### Example 2: Grant permission to player

```kotlin
val result = grantPlayerClaimPermission.execute(
    claimId = claim.id,
    playerId = trustedPlayer.uniqueId,
    permission = ClaimPermission.CONTAINER
)

when (result) {
    GrantPlayerClaimPermissionResult.Success ->
        owner.sendMessage("Granted container access")
    GrantPlayerClaimPermissionResult.AlreadyExists ->
        owner.sendMessage("Player already has this permission")
    // ... handle other cases
}
```

### Example 3: Create partition to expand claim

```kotlin
val result = createPartition.execute(
    claimId = claim.id,
    area = Area(
        Position2D(x1, z1),
        Position2D(x2, z2)
    )
)

when (result) {
    is CreatePartitionResult.Success ->
        player.sendMessage("Claim expanded!")
    CreatePartitionResult.NotEnoughClaimBlocks ->
        player.sendMessage("Not enough claim blocks!")
    CreatePartitionResult.OverlapsExistingClaim ->
        player.sendMessage("Overlaps another claim!")
    // ... handle other cases
}
```

---

## Related Documentation

- [Architecture Overview](./architecture.md) - System design
- [Getting Started](./getting-started.md) - Development guide
- [Application Layer](./application.md) - Detailed action documentation
- [Domain Layer](./domain.md) - Entity and value object details
- [Integration Guide](./integration.md) - External plugin integration

---

*This phone book is auto-generated from the codebase. Last updated: 2025*
