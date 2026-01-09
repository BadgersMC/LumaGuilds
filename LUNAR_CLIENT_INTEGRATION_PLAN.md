# Lunar Client Apollo API Integration Plan
## LumaGuilds Enhancement Proposal

---

## Table of Contents
1. [Overview](#overview)
2. [Setup & Dependencies](#setup--dependencies)
3. [Feature Integration Strategy](#feature-integration-strategy)
4. [Implementation Phases](#implementation-phases)
5. [Technical Architecture](#technical-architecture)
6. [Configuration](#configuration)
7. [Testing Strategy](#testing-strategy)

---

## Overview

This document outlines a comprehensive plan to integrate Lunar Client's Apollo API into LumaGuilds, enhancing the player experience for Lunar Client users with visual enhancements and quality-of-life features.

### Why Apollo Integration?

Apollo provides **70+ modules** for enhanced gameplay features that go beyond vanilla Minecraft:
- **Visual Enhancements**: Waypoints, beams, borders, holograms
- **Communication**: Rich notifications, enhanced nametags
- **UX Improvements**: Client-side optimization and rendering

### Goals

1. **Seamless Integration**: Optional enhancement for Lunar Client users without affecting non-LC players
2. **Guild-Focused Features**: Leverage Apollo to enhance guild gameplay mechanics
3. **Performance**: Lightweight integration that doesn't impact server performance
4. **Extensibility**: Modular design allowing future Apollo features to be easily added

---

## Setup & Dependencies

### Maven Repository

Add to `build.gradle.kts`:

```kotlin
repositories {
    maven("https://repo.lunarclient.dev/")
}

dependencies {
    // Apollo Bukkit Platform
    compileOnly("com.lunarclient:apollo-api:1.0.9")
    compileOnly("com.lunarclient:apollo-common:1.0.9")
    compileOnly("com.lunarclient:apollo-bukkit:1.0.9")
}
```

### Version Information
- **Minimum Lunar Client Version**: 1.8+
- **Apollo API Version**: 1.0.9 (latest stable as of 2026)
- **Paper Version**: 1.21.8+ (current LumaGuilds target)

---

## Feature Integration Strategy

### Phase 1: Core Detection & Infrastructure (FOUNDATION - REQUIRED)

#### 1.1 Apollo Detection Service
**Location**: `infrastructure/services/LunarClientService.kt`

**Purpose**: Detect Lunar Client players and manage Apollo player instances

**Features**:
- Detect if a player is using Lunar Client
- Cache Apollo player instances for performance
- Handle player join/quit events
- Provide utility methods for other services

**Example API**:
```kotlin
interface LunarClientService {
    fun isLunarClient(player: Player): Boolean
    fun getApolloPlayer(player: Player): Optional<ApolloPlayer>
    fun getLunarClientPlayers(): Collection<ApolloPlayer>
    fun isApolloAvailable(): Boolean
}
```

**Implementation Notes**:
- Use `Apollo.getPlayerManager().hasSupport(player.uniqueId)` for detection
- Implement caching with `ConcurrentHashMap` for player lookups
- Register as Koin singleton in dependency injection

---

### Phase 2: Guild Teams System (TOP PRIORITY - MASSIVE QOL)

#### 2.1 Guild Team Integration
**Location**: `infrastructure/services/apollo/GuildTeamService.kt`

**Integration Points**: `MemberService.kt`, `GuildService.kt`, player join/leave events

**Why This is Priority #1**:
This is the **biggest QoL improvement** for guild gameplay because it provides:
- **Instant visual identification** of guild members (glow effects)
- **Teammate markers** above heads showing on minimap and direction HUD
- **Always-on awareness** of where your guild members are
- **No configuration needed** - works automatically for all guild members
- **Combat advantage** - easily identify allies vs enemies in PvP
- **Base building coordination** - see where teammates are working

**Features**:
- **Auto-create Apollo teams** for each guild when members join
- **Real-time team updates** when members join/leave guilds
- **Colored markers** above guild member heads (customizable per guild)
- **Minimap integration** showing teammate locations
- **Glow effects** making guild members glow with guild colors (optional)
- **Rank-based colors** (optional) - leaders glow gold, officers glow blue, members glow green
- **Party integration** - different colors for party members vs guild members

**Visual Benefits**:
```
Guild Member Visualization:
┌─────────────────────────────────┐
│  👤 [GOLD] GuildLeader          │ ← Leader (Gold marker)
│  👤 [BLUE] Officer123           │ ← Officer (Blue marker)
│  👤 [GREEN] Member456           │ ← Member (Green marker)
│  👤 [PURPLE] PartyMember789     │ ← In your party (Purple)
└─────────────────────────────────┘
      ↓ Visible on:
   • Minimap (LC mod)
   • Direction HUD (LC mod)
   • Overhead markers (through walls!)
   • Optional glow effects
```

**Implementation Strategy**:

**Step 1: Team Manager**
```kotlin
class GuildTeamService(
    private val plugin: Plugin,
    private val lunarClientService: LunarClientService,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val rankService: RankService
) {
    private val teamModule: TeamModule = Apollo.getModuleManager().getModule(TeamModule::class.java)
    private val glowModule: GlowModule = Apollo.getModuleManager().getModule(GlowModule::class.java)

    // Track which guilds have active teams
    private val activeTeams = ConcurrentHashMap<UUID, MutableSet<UUID>>() // guildId -> set of playerIds

    // Refresh task for updating team member locations
    private var refreshTaskId: Int? = null

    /**
     * Initialize the team service and start refresh task
     */
    fun start() {
        // Create teams for all existing guilds
        initializeExistingGuilds()

        // Start refresh task (runs every tick - 50ms)
        refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, {
            refreshAllTeams()
        }, 1L, 1L) // Every tick

        logger.info("Guild team service started - teams refresh every 50ms")
    }

    /**
     * Create Apollo teams for all existing guilds on startup
     */
    private fun initializeExistingGuilds() {
        val allGuilds = guildService.getAllGuilds()
        var teamCount = 0

        allGuilds.forEach { guild ->
            val members = memberService.getGuildMembers(guild.id)
            if (members.isNotEmpty()) {
                createGuildTeam(guild.id)
                teamCount++
            }
        }

        logger.info("Initialized $teamCount guild teams")
    }

    /**
     * Create an Apollo team for a guild
     */
    fun createGuildTeam(guildId: UUID) {
        val members = memberService.getGuildMembers(guildId)
        if (members.isEmpty()) return

        val onlineMembers = members.mapNotNull { member ->
            Bukkit.getPlayer(member.playerId)
        }

        if (onlineMembers.isEmpty()) return

        // Show this team to all Lunar Client players in the guild
        onlineMembers.forEach { viewer ->
            if (!lunarClientService.isLunarClient(viewer)) return@forEach
            val apolloPlayer = lunarClientService.getApolloPlayer(viewer) ?: return@forEach

            // Create team members list (excluding self)
            val teamMembers = onlineMembers
                .filter { it.uniqueId != viewer.uniqueId }
                .map { teammate ->
                    createTeamMember(teammate, guildId)
                }

            if (teamMembers.isNotEmpty()) {
                teamModule.updateTeamMembers(apolloPlayer, TeamMembers.builder()
                    .members(teamMembers)
                    .build()
                )
            }
        }

        // Track this guild as having an active team
        activeTeams[guildId] = onlineMembers.map { it.uniqueId }.toMutableSet()
    }

    /**
     * Create a team member representation for Apollo
     */
    private fun createTeamMember(player: Player, guildId: UUID): TeamMember {
        val markerColor = getMarkerColor(player, guildId)
        val location = player.location

        return TeamMember.builder()
            .playerUuid(player.uniqueId)
            .displayName(Component.text(player.name))
            .markerColor(markerColor)
            .location(ApolloLocation.builder()
                .world(location.world.name)
                .x(location.x)
                .y(location.y)
                .z(location.z)
                .build()
            )
            .build()
    }

    /**
     * Get the marker color for a player based on their rank
     */
    private fun getMarkerColor(player: Player, guildId: UUID): Color {
        val config = plugin.config

        // Check if rank-based colors are enabled
        if (!config.getBoolean("apollo.teams.rank_based_colors", false)) {
            // Use guild default color
            return getGuildColor(guildId)
        }

        // Get player's rank
        val member = memberService.getMember(player.uniqueId, guildId) ?: return Color.GREEN
        val rank = rankService.getRankById(member.rankId) ?: return Color.GREEN

        // Color by rank level
        return when {
            rank.level >= 90 -> Color(255, 215, 0)  // Gold - Guild Leader
            rank.level >= 50 -> Color(0, 150, 255)  // Blue - Officer
            else -> Color(0, 255, 0)                 // Green - Member
        }
    }

    /**
     * Get guild's team color from banner or config
     */
    private fun getGuildColor(guildId: UUID): Color {
        // TODO: Extract from guild banner if available
        // For now, return green
        return Color(0, 255, 0)
    }

    /**
     * Refresh all team locations (called every tick)
     */
    private fun refreshAllTeams() {
        activeTeams.keys.forEach { guildId ->
            refreshGuildTeam(guildId)
        }
    }

    /**
     * Refresh a specific guild's team
     */
    private fun refreshGuildTeam(guildId: UUID) {
        val members = memberService.getGuildMembers(guildId)
        val onlineMembers = members.mapNotNull { member ->
            Bukkit.getPlayer(member.playerId)
        }

        if (onlineMembers.isEmpty()) {
            // No online members - remove team
            deleteGuildTeam(guildId)
            return
        }

        // Update each viewer's team view
        onlineMembers.forEach { viewer ->
            if (!lunarClientService.isLunarClient(viewer)) return@forEach
            val apolloPlayer = lunarClientService.getApolloPlayer(viewer) ?: return@forEach

            val teamMembers = onlineMembers
                .filter { it.uniqueId != viewer.uniqueId }
                .map { teammate -> createTeamMember(teammate, guildId) }

            if (teamMembers.isNotEmpty()) {
                teamModule.updateTeamMembers(apolloPlayer, TeamMembers.builder()
                    .members(teamMembers)
                    .build()
                )
            }
        }

        // Update tracked members
        activeTeams[guildId] = onlineMembers.map { it.uniqueId }.toMutableSet()
    }

    /**
     * Handle player joining a guild
     */
    fun onPlayerJoinGuild(playerId: UUID, guildId: UUID) {
        // Refresh the guild's team to include new member
        refreshGuildTeam(guildId)

        // If player is on LC, show them their team
        val player = Bukkit.getPlayer(playerId) ?: return
        if (!lunarClientService.isLunarClient(player)) return

        createGuildTeam(guildId)
    }

    /**
     * Handle player leaving a guild
     */
    fun onPlayerLeaveGuild(playerId: UUID, guildId: UUID) {
        // Remove player from tracked members
        activeTeams[guildId]?.remove(playerId)

        // Clear team for leaving player
        val player = Bukkit.getPlayer(playerId)
        if (player != null && lunarClientService.isLunarClient(player)) {
            val apolloPlayer = lunarClientService.getApolloPlayer(player)
            apolloPlayer?.let {
                teamModule.resetTeamMembers(it)
            }
        }

        // Refresh team for remaining members
        refreshGuildTeam(guildId)
    }

    /**
     * Handle player disconnect
     */
    fun onPlayerQuit(playerId: UUID) {
        // Remove from all tracked teams
        activeTeams.values.forEach { members ->
            members.remove(playerId)
        }

        // Refresh all teams this player was in
        val playerGuilds = memberService.getPlayerGuilds(playerId)
        playerGuilds.forEach { guildId ->
            refreshGuildTeam(guildId)
        }
    }

    /**
     * Delete a guild's team
     */
    fun deleteGuildTeam(guildId: UUID) {
        val members = activeTeams.remove(guildId) ?: return

        // Clear teams for all members
        members.forEach { memberId ->
            val player = Bukkit.getPlayer(memberId)
            if (player != null && lunarClientService.isLunarClient(player)) {
                val apolloPlayer = lunarClientService.getApolloPlayer(player)
                apolloPlayer?.let {
                    teamModule.resetTeamMembers(it)
                }
            }
        }
    }

    /**
     * Stop the team service and cleanup
     */
    fun stop() {
        refreshTaskId?.let { Bukkit.getScheduler().cancelTask(it) }

        // Clear all teams
        activeTeams.keys.toList().forEach { guildId ->
            deleteGuildTeam(guildId)
        }

        logger.info("Guild team service stopped")
    }

    /**
     * Apply glow effect to guild members (optional feature)
     */
    fun enableGuildGlow(guildId: UUID) {
        val members = memberService.getGuildMembers(guildId)
        val onlineMembers = members.mapNotNull { Bukkit.getPlayer(it.playerId) }

        onlineMembers.forEach { viewer ->
            if (!lunarClientService.isLunarClient(viewer)) return@forEach

            // Make other guild members glow for this viewer
            onlineMembers.forEach { target ->
                if (target.uniqueId != viewer.uniqueId) {
                    val color = getMarkerColor(target, guildId)
                    glowModule.overrideGlow(viewer, target.uniqueId, color)
                }
            }
        }
    }

    /**
     * Disable glow effects
     */
    fun disableGuildGlow(guildId: UUID) {
        val members = memberService.getGuildMembers(guildId)
        val onlineMembers = members.mapNotNull { Bukkit.getPlayer(it.playerId) }

        onlineMembers.forEach { viewer ->
            if (!lunarClientService.isLunarClient(viewer)) return@forEach

            onlineMembers.forEach { target ->
                if (target.uniqueId != viewer.uniqueId) {
                    glowModule.resetGlow(viewer, target.uniqueId)
                }
            }
        }
    }
}
```

**Step 2: Event Listeners**

Create listener to handle guild join/leave:
```kotlin
class GuildTeamListener(
    private val guildTeamService: GuildTeamService
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)

        // Create/refresh teams for player's guilds
        playerGuilds.forEach { guildId ->
            guildTeamService.refreshGuildTeam(guildId)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        guildTeamService.onPlayerQuit(event.player.uniqueId)
    }
}
```

**Step 3: Integration with MemberService**

Modify existing guild join/leave events to trigger team updates:
```kotlin
// In MemberServiceBukkit.kt - after successful member addition
override fun addMember(playerId: UUID, guildId: UUID, rankId: UUID): Member? {
    val member = repository.addMember(playerId, guildId, rankId)

    if (member != null) {
        // Existing logic...

        // NEW: Update Apollo team
        val guildTeamService = get().getOrNull<GuildTeamService>()
        guildTeamService?.onPlayerJoinGuild(playerId, guildId)
    }

    return member
}

// In removeMember - after successful removal
override fun removeMember(playerId: UUID, guildId: UUID, actorId: UUID): Boolean {
    val success = repository.removeMember(playerId, guildId)

    if (success) {
        // Existing logic...

        // NEW: Update Apollo team
        val guildTeamService = get().getOrNull<GuildTeamService>()
        guildTeamService?.onPlayerLeaveGuild(playerId, guildId)
    }

    return success
}
```

**Configuration Options**:
```yaml
apollo:
  teams:
    enabled: true

    # Show markers above teammate heads
    show_markers: true

    # Refresh rate (in ticks, 1 = 50ms)
    refresh_rate: 1

    # Rank-based colors (true = leaders/officers different colors)
    rank_based_colors: true

    # Color scheme when rank_based_colors: true
    colors:
      leader: "255,215,0"    # Gold
      officer: "0,150,255"   # Blue
      member: "0,255,0"      # Green
      party: "255,0,255"     # Purple (for party members - future)

    # Default guild color (when rank_based_colors: false)
    default_guild_color: "0,255,0"  # Green

    # Glow effects (makes teammates glow through walls)
    glow:
      enabled: false  # Can be performance heavy
      use_team_colors: true
```

**Benefits**:
1. ✅ **Instant teammate awareness** - always know where guild members are
2. ✅ **Minimap integration** - see teammates on LC minimap
3. ✅ **Direction HUD** - arrows pointing to teammates
4. ✅ **PvP advantage** - quickly identify allies in combat
5. ✅ **Base coordination** - see who's building where
6. ✅ **No player action needed** - works automatically
7. ✅ **Performance optimized** - updates every 50ms (1 tick)

---

### Phase 3: Guild Home Waypoints (HIGH VALUE)

#### 2.1 Guild Home Waypoint System
**Location**: `application/services/GuildWaypointService.kt`

**Integration Point**: Existing `GuildHomeMenu.kt` and `TeleportationService.kt`

**Features**:
- **Auto-create waypoint** when guild sets home location
- **Show waypoint** to guild members when viewing guild home menu
- **Distance indicator** showing how far from guild home
- **Custom colors** based on guild relationship:
  - 🟢 Green: Own guild
  - 🔵 Blue: Allied guild
  - 🟡 Yellow: Neutral guild
  - 🔴 Red: Enemy guild (war)

**When to Show**:
1. Player opens Guild Home menu (`GuildHomeMenu.kt:80`)
2. Player uses `/guild home` command
3. Player starts teleport countdown (integration with `TeleportationService.kt:38`)

**Code Example**:
```kotlin
class GuildWaypointService(
    private val lunarClientService: LunarClientService,
    private val guildService: GuildService,
    private val relationService: RelationService
) {
    fun displayGuildHomeWaypoint(player: Player, guild: Guild) {
        if (!lunarClientService.isLunarClient(player)) return

        val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
        val homeLocation = guild.homeLocation ?: return

        val color = determineWaypointColor(player, guild)
        val waypoint = Waypoint.builder()
            .name("${guild.name} Home")
            .location(ApolloBlockLocation(
                world = homeLocation.worldId.toString(),
                x = homeLocation.x,
                y = homeLocation.y,
                z = homeLocation.z
            ))
            .color(color)
            .preventRemoval(false) // Allow players to remove
            .hidden(false)
            .build()

        waypointModule.displayWaypoint(apolloPlayer, waypoint)
    }

    private fun determineWaypointColor(player: Player, guild: Guild): Color {
        val playerGuilds = guildService.getPlayerGuilds(player.uniqueId)
        return when {
            playerGuilds.any { it.id == guild.id } -> Color.GREEN // Own guild
            relationService.areAllied(playerGuilds.first().id, guild.id) -> Color.BLUE
            relationService.areAtWar(playerGuilds.first().id, guild.id) -> Color.RED
            else -> Color.YELLOW // Neutral
        }
    }
}
```

**Integration Points**:
- Modify `GuildHomeMenu.kt` to call waypoint service when menu opens
- Modify `BedrockGuildHomeMenu.kt` for Bedrock players using LC
- Cleanup waypoints when menu closes or player disconnects

---

### Phase 3: Guild Vault Beams (HIGH VALUE)

#### 3.1 Vault Location Beams
**Location**: Enhance existing `VaultHologramService.kt`

**Integration Strategy**:
Replace or supplement existing hologram system with Apollo beams for Lunar Client users

**Features**:
- **Colored beams** showing vault locations (similar to beacon beams)
- **Guild-colored beams**: Use guild banner color or configurable color
- **Visibility control**: Only show to guild members
- **Distance-based rendering**: Visible from configurable distance (default: 64 blocks)

**Why This is Valuable**:
- Beams are visible through walls (unlike holograms)
- Easier to locate vaults in large guild bases
- More visually appealing than text holograms
- Works on older LC versions (1.7+)

**Code Integration**:
```kotlin
// Enhance VaultHologramService.kt
class VaultHologramService(
    private val plugin: Plugin,
    private val lunarClientService: LunarClientService // NEW
) : KoinComponent {

    // Add beam support alongside holograms
    private val vaultBeams = ConcurrentHashMap<String, String>() // location -> beam ID

    fun createHologramAndBeam(location: Location, guild: Guild) {
        // Existing hologram code...
        createHologram(location, guild)

        // NEW: Create beam for Lunar Client users
        createVaultBeam(location, guild)
    }

    private fun createVaultBeam(location: Location, guild: Guild) {
        val beamId = "vault_${guild.id}"
        val color = getGuildColor(guild) // From banner or config

        // Show beam to all guild members
        val guildMembers = memberService.getGuildMembers(guild.id)
        guildMembers.forEach { member ->
            val player = Bukkit.getPlayer(member.playerId) ?: return@forEach
            if (!lunarClientService.isLunarClient(player)) return@forEach

            val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return@forEach
            val beam = Beam.builder()
                .id(beamId)
                .color(color)
                .location(ApolloBlockLocation(
                    world = location.world?.uid.toString(),
                    x = location.blockX,
                    y = location.blockY,
                    z = location.blockZ
                ))
                .build()

            beamModule.displayBeam(apolloPlayer, beam)
        }
    }
}
```

---

### Phase 4: War System Enhancements (MEDIUM VALUE)

#### 4.1 War Border Visualization
**Location**: New `WarBorderService.kt`

**Integration Point**: Existing `WarService.kt`

**Features**:
- **Territory borders**: Show guild territory boundaries during wars
- **Combat zones**: Highlight active war zones with red borders
- **Dynamic updates**: Borders update as war progresses
- **Multiple borders**: Each guild can have distinct colored borders

**Use Cases**:
1. When war starts, show enemy guild territory
2. When player enters war zone, show combat boundary
3. When capturing territory (if you add territory mechanics)

**Code Example**:
```kotlin
class WarBorderService(
    private val lunarClientService: LunarClientService,
    private val warService: WarService
) {
    fun showWarBorder(player: Player, guildId: Int) {
        if (!lunarClientService.isLunarClient(player)) return

        val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
        val claimBoundary = getGuildTerritory(guildId) // Get from ClaimRepository

        val border = Border.builder()
            .id("war_border_$guildId")
            .color(Color.RED)
            .bounds(claimBoundary.minX, claimBoundary.maxX,
                    claimBoundary.minZ, claimBoundary.maxZ)
            .world(claimBoundary.world)
            .cancellable(true) // Don't prevent movement
            .build()

        borderModule.displayBorder(apolloPlayer, border)
    }
}
```

---

### Phase 5: Notification System (LOW PRIORITY)

#### 5.1 Rich Guild Notifications
**Location**: New `ApolloNotificationService.kt`

**Integration Points**: Multiple existing notification points

**Features**:
- **Guild invites**: Prettier notifications for guild invitations
- **War declarations**: Dramatic war start notifications
- **Member events**: Join/leave notifications with custom icons
- **Achievement notifications**: Progression unlocks, level ups

**Notification Types**:
1. **Guild Invite** (🏰): "You've been invited to join [Guild Name]"
2. **War Started** (⚔️): "[Guild] has declared war on your guild!"
3. **War Victory** (🏆): "Your guild won the war against [Guild]!"
4. **Vault Unlocked** (💰): "Guild vault is now available!"
5. **Progression** (📈): "Guild reached level [X]!"

**Code Example**:
```kotlin
class ApolloNotificationService(
    private val lunarClientService: LunarClientService
) {
    fun sendGuildInvite(player: Player, guildName: String) {
        if (!lunarClientService.isLunarClient(player)) return

        val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
        notificationModule.displayNotification(apolloPlayer,
            Notification.builder()
                .titleComponent(Component.text("Guild Invite", NamedTextColor.GOLD))
                .descriptionComponent(Component.text("$guildName invited you!"))
                .resourceLocation("textures/items/totem_of_undying.png")
                .displayTime(Duration.ofSeconds(5))
                .build()
        )
    }

    fun sendWarDeclaration(player: Player, enemyGuild: String) {
        if (!lunarClientService.isLunarClient(player)) return

        val apolloPlayer = lunarClientService.getApolloPlayer(player) ?: return
        notificationModule.displayNotification(apolloPlayer,
            Notification.builder()
                .titleComponent(Component.text("WAR!", NamedTextColor.RED))
                .descriptionComponent(Component.text("$enemyGuild declared war!"))
                .resourceLocation("textures/items/iron_sword.png")
                .displayTime(Duration.ofSeconds(7))
                .build()
        )
    }
}
```

**Integration Strategy**:
- Conditionally send Apollo notification if Lunar Client detected
- Fall back to existing chat messages for non-LC players
- Don't duplicate messages - send one or the other

---

### Phase 6: Nametag Enhancements (FUTURE)

#### 6.1 Guild Member Nametags
**Features**:
- Show guild tag above player names
- Color-code by guild relationship (ally/enemy/neutral)
- Show rank/role badges for officers/leaders

**Note**: This phase is lower priority as it requires more complex implementation and may conflict with other nametag plugins.

---

## Technical Architecture

### Service Layer Structure

```
infrastructure/services/apollo/
├── LunarClientService.kt           (Core detection & management)
├── GuildWaypointService.kt         (Waypoint integration)
├── VaultBeamService.kt             (Beam integration for vaults)
├── WarBorderService.kt             (Border system for wars)
└── ApolloNotificationService.kt    (Rich notifications)
```

### Dependency Injection (Koin)

Add to `di/Modules.kt`:

```kotlin
// Apollo Integration (optional - only if plugin detected)
val apolloAvailable = Bukkit.getPluginManager().getPlugin("Apollo-Bukkit") != null
if (apolloAvailable) {
    single<LunarClientService> { LunarClientServiceBukkit(get()) }
    single<GuildWaypointService> { GuildWaypointService(get(), get(), get()) }
    single<VaultBeamService> { VaultBeamService(get(), get()) }
    single<WarBorderService> { WarBorderService(get(), get()) }
    single<ApolloNotificationService> { ApolloNotificationService(get()) }
}
```

### Integration Pattern

**Soft Dependency Approach**:
- Check for Apollo at runtime, don't require it
- Gracefully degrade if Apollo not available
- All Apollo features are opt-in enhancements

**Example Pattern**:
```kotlin
// In any service that wants to use Apollo
private val waypointService: GuildWaypointService? by inject(
    parameters = { parametersOf(null) }
)

fun someMethod(player: Player, guild: Guild) {
    // Normal guild logic...

    // Optional: Show waypoint if Apollo available
    waypointService?.displayGuildHomeWaypoint(player, guild)
}
```

---

## Configuration

### config.yml Additions

```yaml
# Lunar Client Apollo Integration
apollo:
  enabled: true

  # Waypoint Settings
  waypoints:
    enabled: true
    show_guild_homes: true
    show_vault_locations: false # Privacy concern
    max_distance: 10000 # Max distance to show waypoints

    # Waypoint Colors (java.awt.Color format)
    colors:
      own_guild: "0,255,0"      # Green
      allied_guild: "0,100,255" # Blue
      neutral_guild: "255,255,0" # Yellow
      enemy_guild: "255,0,0"    # Red

  # Beam Settings
  beams:
    enabled: true
    vaults:
      enabled: true
      use_guild_banner_color: true
      fallback_color: "255,215,0" # Gold
      max_distance: 64

  # Border Settings
  borders:
    enabled: true
    show_war_borders: true
    show_territory_borders: true

  # Notification Settings
  notifications:
    enabled: true
    guild_invites: true
    war_events: true
    member_events: true
    progression_events: true
```

---

## Implementation Phases

### Phase 1: Foundation (Week 1)
- [ ] Add Apollo dependencies to `build.gradle.kts`
- [ ] Create `LunarClientService` interface and implementation
- [ ] Add Apollo detection on player join
- [ ] Register Apollo services in Koin DI
- [ ] Add configuration section to `config.yml`
- [ ] Add initialization logging in `LumaGuilds.kt`

**Deliverable**: Apollo detection working, no features yet

**Time Estimate**: 2-3 days

---

### Phase 2: Guild Teams System (Week 2) ⭐ TOP PRIORITY
- [ ] Create `GuildTeamService` with team management
- [ ] Implement team refresh task (every tick)
- [ ] Add `GuildTeamListener` for player join/quit
- [ ] Integrate with `MemberServiceBukkit` for guild join/leave
- [ ] Add rank-based color system
- [ ] Implement optional glow effects
- [ ] Test with multiple guilds and 20+ players
- [ ] Verify minimap and direction HUD integration
- [ ] Performance test refresh rate impact

**Deliverable**: Guild members see teammates with markers on minimap/HUD

**Why This First**: Provides the most immediate value - players can always see where their guild members are. No player action required, works automatically. Huge QoL improvement for coordination and PvP.

**Time Estimate**: 4-5 days

---

### Phase 3: Guild Home Waypoints (Week 3)
- [ ] Create `GuildWaypointService`
- [ ] Integrate with `GuildHomeMenu`
- [ ] Integrate with `BedrockGuildHomeMenu`
- [ ] Add waypoint cleanup on player disconnect
- [ ] Test with multiple guilds and relationships
- [ ] Add guild relationship color coding

**Deliverable**: Guild home waypoints working for Lunar Client players

**Time Estimate**: 3-4 days

---

### Phase 4: Vault Beams (Week 4)
- [ ] Enhance `VaultHologramService` with beam support
- [ ] Add guild banner color extraction
- [ ] Implement beam visibility based on guild membership
- [ ] Add distance-based rendering
- [ ] Test beam persistence across server restarts
- [ ] Add toggle in config

**Deliverable**: Vault beams visible to guild members

**Time Estimate**: 3-4 days

---

### Phase 5: War Borders (Week 5)
- [ ] Create `WarBorderService`
- [ ] Integrate with `WarService` for war start/end
- [ ] Add territory boundary calculation from claims
- [ ] Implement dynamic border updates
- [ ] Test with multiple simultaneous wars
- [ ] Add border cleanup on war end

**Deliverable**: War borders showing territory during conflicts

**Time Estimate**: 4-5 days

---

### Phase 6: Notifications (Week 6)
- [ ] Create `ApolloNotificationService`
- [ ] Replace key chat messages with Apollo notifications
- [ ] Add notification icons from resource packs
- [ ] Test notification timing and duration
- [ ] Ensure graceful fallback for non-LC players
- [ ] Add per-notification-type config toggles

**Deliverable**: Rich notifications for guild events

**Time Estimate**: 2-3 days

---

### Phase 7: Polish & Documentation (Week 7)
- [ ] Add `/lumaguilds apollo` admin command to show stats
- [ ] Add `/lumaguilds apollo stats` to show LC player count
- [ ] Add per-guild team toggle command
- [ ] Write user documentation for Apollo features
- [ ] Create wiki page explaining Lunar Client benefits
- [ ] Performance testing with 100+ players
- [ ] Add metrics tracking for Apollo feature usage
- [ ] Create demo video for features
- [ ] Optimize refresh rates based on testing

**Deliverable**: Production-ready Apollo integration

**Time Estimate**: 3-4 days

---

## Total Development Time

**Estimated**: 6-7 weeks (Phases 1-7)
**Minimum Viable Product**: 2 weeks (Phases 1-2 only - Foundation + Teams)

---

## Testing Strategy

### Unit Tests
```kotlin
// Test Lunar Client detection
class LunarClientServiceTest {
    @Test
    fun `should detect Lunar Client players`() {
        // Mock Apollo API
        // Test detection logic
    }

    @Test
    fun `should cache Apollo player instances`() {
        // Test caching mechanism
    }
}
```

### Integration Tests
- Test waypoints show at correct guild homes
- Test beams only visible to guild members
- Test borders update during war state changes
- Test notifications send to correct players

### Manual Testing Checklist
- [ ] Join server with Lunar Client - detection works
- [ ] Join server without Lunar Client - no errors
- [ ] Open guild home menu - waypoint appears
- [ ] View vault location - beam appears (if enabled)
- [ ] Start war - borders appear
- [ ] Receive guild invite - notification shows
- [ ] Restart server - features persist correctly

### Performance Testing
- [ ] Test with 50 concurrent Lunar Client players
- [ ] Test with 100 active waypoints
- [ ] Test with 10 simultaneous wars (borders)
- [ ] Monitor memory usage with Apollo enabled
- [ ] Test cleanup on player disconnect

---

## Version Strategy

Following the project's semantic versioning guidelines:

### Patch Version (1.0.8)
- Bug fixes to Apollo integration
- Config value adjustments
- Minor performance improvements

### Minor Version (1.1.0)
- Initial Apollo integration (Phases 1-3)
- Guild home waypoints
- Vault beams
- New configuration options
- Backward compatible with existing installations

### Minor Version (1.2.0)
- War borders (Phase 4)
- Notification system (Phase 5)
- Additional Apollo modules

Update locations:
- `build.gradle.kts` line 7: `version = "1.1.0"`
- `build.gradle.kts` shadowJar block: `archiveVersion.set("1.1.0")`

---

## Risks & Mitigation

### Risk 1: Apollo Plugin Not Installed
**Impact**: Features won't work
**Mitigation**:
- Soft dependency - plugin works without Apollo
- Clear logging on startup showing Apollo status
- Documentation explaining Apollo is optional

### Risk 2: Performance Impact
**Impact**: Server lag with many Apollo features
**Mitigation**:
- Implement distance-based rendering
- Cache Apollo player lookups
- Add per-feature toggle in config
- Async operations where possible

### Risk 3: Player Privacy Concerns
**Impact**: Players don't want locations revealed
**Mitigation**:
- Vault beam toggle defaults to false
- Only show waypoints when player explicitly views menu
- Allow players to remove waypoints (preventRemoval: false)

### Risk 4: Apollo API Changes
**Impact**: Breaking changes in Apollo updates
**Mitigation**:
- Use stable Apollo API version (1.0.9)
- Extensive error handling around Apollo calls
- Monitor Apollo changelog for deprecations

---

## Success Metrics

### Adoption Metrics
- % of players using Lunar Client on server
- % of LC players using guild waypoint feature
- % of LC players with vault beams enabled

### Engagement Metrics
- Average time saved finding guild homes (via waypoints)
- Number of vaults found using beams
- War engagement increase (via border visibility)

### Performance Metrics
- Server TPS with Apollo enabled vs disabled
- Memory usage impact
- Player client FPS impact (if measurable)

---

## Future Enhancements

### Additional Apollo Modules to Consider

1. **Glow Module**: Highlight guild members in combat
2. **Hologram Module**: Replace current hologram system with Apollo's
3. **Team Module**: Integrate with war teams for colored indicators
4. **Title Module**: Dramatic titles for war events
5. **Transfer Module**: Cooldown indicators for teleports
6. **Vignette Module**: Screen effects during combat/events

### Advanced Features

1. **Territory Heatmaps**: Show guild claim density with colored borders
2. **War Replay System**: Recorded waypoints showing combat positions
3. **Party Waypoint Sharing**: Share coordinates with party members
4. **Dynamic Quest Waypoints**: Integration with future quest system
5. **Guild Leaderboard Beams**: Show top guild locations on map

---

## Resources

### Documentation Links
- [Apollo Developer Docs](https://lunarclient.dev/apollo/developers/general)
- [Apollo GitHub Repository](https://github.com/LunarClient/Apollo)
- [Waypoint Module Docs](https://lunarclient.dev/apollo/developers/modules/waypoint)
- [Beam Module Docs](https://lunarclient.dev/apollo/developers/modules/beam)
- [Notification Module Docs](https://lunarclient.dev/apollo/developers/modules/notification)

### Example Projects
- [Apollo Example Plugins](https://github.com/LunarClient/Apollo/tree/master/examples)
- [Community Apollo Integrations](https://github.com/topics/lunar-client-apollo)

---

## Conclusion

Integrating Lunar Client's Apollo API into LumaGuilds provides significant value for players using Lunar Client while maintaining full compatibility for players who don't. The phased approach allows for incremental development and testing, with each phase delivering tangible player-facing improvements.

**Recommended Priority**:
1. ✅ **Phase 1** (Foundation) - Required for everything else [2-3 days]
2. ⭐⭐⭐ **Phase 2** (Guild Teams) - **HIGHEST VALUE** - Automatic teammate awareness [4-5 days]
3. ⭐⭐ **Phase 3** (Waypoints) - High player value, enhances navigation [3-4 days]
4. ⭐ **Phase 4** (Beams) - High visual impact, enhances existing vaults [3-4 days]
5. ✨ **Phase 5** (Borders) - Medium value, enhances war system [4-5 days]
6. 💡 **Phase 6** (Notifications) - Nice-to-have polish [2-3 days]
7. 🎯 **Phase 7** (Polish) - Production readiness [3-4 days]

**Development Timeline**:
- **MVP (Phases 1-2)**: 2 weeks - Gets you the biggest QoL improvement
- **Feature Complete (Phases 1-6)**: 6 weeks - All major features
- **Production Ready (Phases 1-7)**: 7 weeks - Polished and documented

**Why Teams Module is Priority #1**:
The Guild Teams system provides **automatic, passive awareness** of all guild members at all times. Unlike waypoints (require menu interaction) or beams (specific locations), teams work constantly in the background. Players see teammates on minimap, direction HUD, and with overhead markers - perfect for:
- PvP coordination (know where allies are in combat)
- Base building (see who's working where)
- Exploration (find lost guild members)
- Events (coordinate for KOTH, raids, etc.)

This integration positions LumaGuilds as a **premium guild plugin with best-in-class support for Lunar Client users** while maintaining excellent backward compatibility. The Teams module alone provides more QoL value than most standalone plugins.
