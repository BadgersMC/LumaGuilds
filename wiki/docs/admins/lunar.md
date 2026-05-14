---
title: Lunar Client integration
audience: admin
topic: lunar
summary: Drive Lunar Client features (teams, waypoints, notifications, rich presence) from LumaGuilds guild events.
keywords: [lunar, lunarclient, apollo, teams, waypoints, notifications]
related: [installation, rosechat]
updated: 2026-05-14
---

# Lunar Client integration

LumaGuilds integrates Lunar Client's Apollo API to enhance the guild experience for Lunar Client users with automatic teammate awareness, guild home waypoints, rich notifications, and server presence info.

## How it works

When a Lunar Client user joins, LumaGuilds detects them via the Apollo API and optionally enhances their experience with guild-focused features. Non-LC players are unaffected—all enhancements are opt-in and degraded gracefully if Apollo is unavailable.

**Detection:** LumaGuilds checks `Apollo.getPlayerManager().hasSupport(player.uuid)` on join. If available, the player's Apollo instance is cached for the session.

**Events:** Apollo features are triggered by guild lifecycle events (join, leave, war declaration, etc.) and refreshed by scheduled tasks (e.g., team member location updates every tick).

## Setup

### Dependencies

Add to `build.gradle.kts`:

```kotlin
repositories {
    maven("https://repo.lunarclient.dev/")
}

dependencies {
    compileOnly("com.lunarclient:apollo-api:1.2.3")
    compileOnly("com.lunarclient:apollo-common:1.2.3")
    compileOnly("com.lunarclient:apollo-bukkit:1.2.3")
}
```

### Installation

1. **Download Apollo-Bukkit** from [Modrinth](https://modrinth.com/plugin/lunar-client-apollo) (latest stable).

2. **Drop the JAR into `plugins/`.**

3. **Restart the server.** LumaGuilds will detect Apollo on startup:

   ```text
   ✓ Successfully initialized Apollo (Lunar Client) integration!
   Enhanced features available for Lunar Client users
   ```

   If Apollo is not found:

   ```text
   ⚠ Apollo-Bukkit plugin not found. Lunar Client features unavailable.
   Install Apollo-Bukkit from https://modrinth.com/plugin/lunar-client-apollo
   ```

### Configuration

All Apollo features are controlled via `config.yml`:

```yaml
apollo:
  enabled: true

  # Guild Teams — show guild members on minimap/HUD
  teams:
    enabled: true
    show_markers: true          # Overhead markers above teammates
    refresh_rate: 1             # Ticks between updates (20 ticks = 1 sec)
    rank_based_colors: true     # Color leaders/officers/members differently
    colors:
      leader: "255,215,0"       # Gold
      officer: "0,150,255"      # Blue
      member: "0,255,0"         # Green
      party: "255,0,255"        # Purple (for party members)
    default_guild_color: "0,255,0"
    glow:
      enabled: false            # Make teammates glow through walls (performance heavy)
      use_team_colors: true

  # Guild Home Waypoints — show homes on minimap
  waypoints:
    enabled: true
    show_guild_homes: true
    max_distance: 10000         # Blocks from home before waypoint hides
    colors:
      own_guild: "0,255,0"      # Green
      allied_guild: "0,100,255" # Blue
      neutral_guild: "255,255,0" # Yellow
      enemy_guild: "255,0,0"    # Red

  # Notifications — rich popups for guild events
  notifications:
    enabled: true
    guild_invites: true
    war_events: true
    member_events: true
    progression_events: true
    display_duration_seconds: 5
    icons:
      welcome: "minecraft:textures/item/banner.png"
      guild: "minecraft:textures/item/banner.png"
      invite: "minecraft:textures/item/writable_book.png"
      join: "minecraft:textures/item/green_banner.png"
      leave: "minecraft:textures/item/red_banner.png"
      promotion: "minecraft:textures/item/experience_bottle.png"
      war: "minecraft:textures/item/netherite_sword.png"
      peace: "minecraft:textures/item/golden_apple.png"
      neutral: "minecraft:textures/item/apple.png"
      koth: "minecraft:textures/item/golden_helmet.png"

  # Rich Presence — show guild info on Discord/LC launcher
  richpresence:
    enabled: true
    server_ip: "play.example.com"   # Public server IP (optional)
```

**Defaults:** All features are enabled by default. Set `enabled: false` under any section to disable that feature.

## What it does on guild events

### Teams (Automatic teammate awareness)

When enabled, guild members appear with:

- **Markers above their heads** — visible on LC minimap and direction HUD.
- **Colors by rank** — leaders glow gold, officers blue, members green.
- **Location updates every tick** — teammates you see are where they actually are (fresh data).
- **Automatic cleanup** — no action required from players; works in background.

**Triggered by:**

- Player joins the server (guild teams are shown).
- Player joins/leaves a guild (team membership updates).
- Player disconnects (team is cleared for others).
- Rank changes (marker color updates).

### Waypoints (Guild home navigation)

When a guild sets a home, all guild members who use Lunar Client see:

- **Waypoint on minimap** — permanently shows the guild home location.
- **Colorized by relationship** — own guild homes are green, allied blue, neutral yellow, enemy red.
- **Distance indicator** — LC displays distance from player to waypoint.
- **Removable by player** — LC users can click the waypoint to remove it (re-added on next home update).

**Triggered by:**

- Player joins the server (shown all guild home waypoints).
- Player joins/leaves a guild (waypoints update).
- Guild sets a home via `/guild sethome` (waypoint created; shown to all members).
- Guild renames a home (waypoint name updates).

### Notifications (Rich popup alerts)

LC players receive styled notifications for:

- **Guild invites** — "You've been invited to join [Guild]" (book icon, 5s display).
- **Member join** — "[Player] joined the guild" (green banner icon).
- **Member leave** — "[Player] left the guild" (red banner icon).
- **Promotions** — "[Player] promoted to [Rank]" (experience bottle icon).
- **War declarations** — "[Enemy Guild] has declared war!" (sword icon, 7s display).
- **Peace treaties** — "War with [Guild] has ended" (golden apple icon).
- **KOTH captures** — "[Guild] captured the KOTH!" (golden helmet icon).

All other players see the same info as in-game chat. Notifications are non-blocking and expire automatically.

**Triggered by:**

- Any guild event that sends a chat message (guild.*and war.* events).

### Rich Presence (Discord/LC launcher profile)

When enabled, an LC player's Discord/LC launcher profile shows:

- **Guild name** (game name field).
- **Guild tag** (variant field).
- **Online members count** (team size).
- **Server IP** (if configured).

Example: `"Guild: Mythic (MYTH) - 15/50 online on play.example.com"`

**Triggered by:**

- Player joins the server (presence set to their first guild).
- Player joins/leaves a guild (presence updates).
- Player disconnects (presence reset).

## Enabling Lunar integration

1. **Install Apollo-Bukkit** (see Setup section above).
2. **Restart the server.** LumaGuilds detects Apollo automatically.
3. **Verify:** Check console for `✓ Successfully initialized Apollo...`
4. **Test:** Join with a Lunar Client; use `/guild menu`. You should see:
   - Markers above guild members on your minimap.
   - Guild home waypoint on the minimap.
   - Notification popup for any guild event.

No admin config needed—Apollo features are enabled by default if Apollo is installed.

## Disabling features

To disable a specific feature, edit `config.yml` and set the feature's `enabled: false`:

```yaml
apollo:
  teams:
    enabled: false        # Disable teammate markers
  waypoints:
    enabled: false        # Disable guild home waypoints
  notifications:
    enabled: false        # Disable notification popups
  richpresence:
    enabled: false        # Disable Discord presence
```

Restart the server. LumaGuilds will log:

```text
Guild teams module disabled in config
```

To disable all Apollo features at once:

```yaml
apollo:
  enabled: false
```

## Gotchas

- **Lunar Client is optional.** Players on vanilla Java, Bedrock, or other clients are unaffected. They see standard in-game chat and inventory menus. Apollo features are pure opt-in enhancements for LC users only.

- **Apollo must be running on the server** for LC integration to work. If Apollo crashes or isn't loaded, LumaGuilds gracefully degrades to standard behavior (no errors, no spam).

- **Team markers refresh every tick by default.** If your server has 100+ LC players with large guilds, consider increasing `refresh_rate: 5` (5 ticks = 250ms) to reduce CPU overhead. Player locations will be less fresh but still smooth.

- **Waypoints are always-on** for guild members. If you want to hide guild home locations from enemies, don't set homes in sensitive bases, or use the waypoint color system to distinguish (red for enemy guilds).

- **Notifications fire once per event.** If a guild invite is sent to 10 players, each player sees one notification. There's no spam filtering; multiple rapid invites will show multiple notifications.

- **Rich Presence only shows the player's first guild.** If a player is in multiple guilds, presence displays the first one from their membership list.

## Performance notes

- **Teams refresh rate:** Default `1` tick (50ms) is optimal for real-time location updates. Higher rates reduce CPU but make markers lag behind player movement.
- **Glow effects:** The optional `glow.enabled: true` setting is performance-heavy. Only enable if you have <20 LC players. Each glow effect requires Apollo to render additional shader passes.
- **Waypoint count:** Displaying 100+ waypoints simultaneously is safe; Apollo caches them on the client side.

## Related

- [Installation & config.yml](installation.md) — configure Apollo in config
- [RoseChat integration](rosechat.md) — combine with custom chat formats
