# LumaGuilds PlaceholderAPI Expansion

## Overview

LumaGuilds now includes full PlaceholderAPI integration! This allows you to display guild information in chat, scoreboards, tab lists, and any other plugin that supports PlaceholderAPI.

## Installation

1. **Install PlaceholderAPI** on your server
2. **LumaGuilds automatically detects** PlaceholderAPI and registers its expansion
3. **No configuration required** - works out of the box!

## Available Placeholders

All placeholders use the `%lumaguilds_` prefix.

### Basic Guild Information

- `%lumaguilds_guild_name%` - Player's guild name
- `%lumaguilds_guild_tag%` - Player's guild tag (formatted)
- `%lumaguilds_guild_emoji%` - Player's guild emoji (Nexo format)
- `%lumaguilds_guild_level%` - Player's guild level
- `%lumaguilds_guild_balance%` - Player's guild bank balance
- `%lumaguilds_guild_mode%` - Player's guild mode (Peaceful/Hostile)

### Member Information

- `%lumaguilds_guild_members%` - Player's guild member count
- `%lumaguilds_guild_rank%` - Player's rank in guild
- `%lumaguilds_has_guild%` - Whether player has a guild (true/false)

### Combat Statistics

- `%lumaguilds_guild_kills%` - Player's guild total kills
- `%lumaguilds_guild_deaths%` - Player's guild total deaths
- `%lumaguilds_guild_kdr%` - Player's guild K/D ratio (formatted)
- `%lumaguilds_guild_efficiency%` - Player's guild efficiency percentage

### War Statistics

- `%lumaguilds_guild_wars_total%` - Total wars participated in
- `%lumaguilds_guild_wars_active%` - Currently active wars

### Relational Placeholders

- `%lumaguilds_rel_<player>_status%` - Relationship status with another player
  - Returns: `🔴` (enemy/at war), `🟢` (ally), `⚪` (truce), `` (neutral/blank)
  - Example: `%lumaguilds_rel_Steve_status%`

### Formatted Displays

- `%lumaguilds_guild_display%` - Complete display format: `[emoji] [tag] [level]`
- `%lumaguilds_guild_chat_format%` - Chat-friendly format: `emoji tag`

## Usage Examples

### Chat Plugins (EssentialsChat, VentureChat, etc.)

```
Format: [%bellclaims_guild_tag%] {DISPLAYNAME}: {MESSAGE}
Result: [⚔ Elite] Steve: Hello everyone!
```

### Scoreboards (FeatherBoard, Scoreboard-r6, etc.)

```
Line 1: Guild: %bellclaims_guild_name%
Line 2: Rank: %bellclaims_guild_rank%
Line 3: Level: %bellclaims_guild_level%
```

### Tab List (TAB, DeluxeMenus, etc.)

```
Header: Welcome to %bellclaims_guild_name%
Footer: Your rank: %bellclaims_guild_rank%
```

### Relational Status (Scoreboards, Chat, etc.)

```
Player List: %player_name%%bellclaims_rel_%player_name%_status%
Result: Steve🔴 (shows red circle if at war with Steve)
Result: Alex🟢 (shows green circle if allied with Alex)
Result: Bob⚪ (shows white circle if truced with Bob)
Result: John (shows nothing if neutral with John)
```

### Conditional Formatting

```
%bellclaims_has_guild% ? "Guild: %bellclaims_guild_name%" : "No Guild"
```

## Placeholder Values

### When Player Has a Guild

- All placeholders return relevant guild information
- Empty fields return empty strings `""`
- Numeric values are properly formatted

### When Player Has NO Guild

- `%bellclaims_has_guild%` returns `"false"`
- All other placeholders return empty strings `""`
- **Relational placeholders**: Return `""` (neutral) if either player has no guild
- No errors or null values

## Formatting Examples

### Raw Values
```
%bellclaims_guild_kills% → "1,250"
%bellclaims_guild_kdr% → "2.45"
%bellclaims_guild_balance% → "50000"
```

### With Formatting
```
Kills: %bellclaims_guild_kills%
K/D: %bellclaims_guild_kdr%
Balance: $%bellclaims_guild_balance%
```

### Combined Display
```
[%bellclaims_guild_emoji% %bellclaims_guild_tag%] %player_name%
[⚔ Elite] Steve
```

## Technical Details

- **Auto-registration**: Expansion registers automatically when PlaceholderAPI is detected
- **Error handling**: Graceful fallbacks for missing data or services
- **Performance**: Efficient caching and database queries
- **Compatibility**: Works with all PlaceholderAPI versions 2.10.0+

## Troubleshooting

### Placeholders not working?
1. Ensure PlaceholderAPI is installed and running
2. Check server logs for LumaGuilds expansion registration
3. Verify player is actually in a guild
4. Use `%bellclaims_has_guild%` to debug

### Relational placeholders not working?
1. Ensure the target player is **online** (relational placeholders only work with online players)
2. Verify both players are in different guilds (same guild = neutral)
3. Check if guilds have an actual relation set (war, ally, or truce)
4. Use format: `%bellclaims_rel_<playername>_status%`
5. Indicators: 🔴 (enemy/war), 🟢 (ally), ⚪ (truce), blank (neutral)

### Server Log Messages
```
[INFO] Successfully registered LumaGuilds PlaceholderAPI expansion!
[INFO] Available placeholders: %bellclaims_guild_name%, %bellclaims_guild_tag%, etc.
```

### Testing Placeholders
Use `/papi parse <player> %bellclaims_guild_name%` to test placeholders.

## Integration Examples

### Popular Plugins

#### TAB (Tab List)
```yaml
header:
- "Welcome to %bellclaims_guild_name%!"
- "Your rank: %bellclaims_guild_rank%"
```

#### DeluxeMenus
```yaml
menu_title: "%bellclaims_guild_name% Menu"
menu_rows: 3
items:
  '1':
    material: PLAYER_HEAD
    display_name: "&eYour Guild"
    lore:
    - "&7Name: %bellclaims_guild_name%"
    - "&7Level: %bellclaims_guild_level%"
    - "&7Members: %bellclaims_guild_members%"
```

#### TAB (Scoreboard)
```yaml
scoreboard:
  title: "%bellclaims_guild_name%"
  lines:
  - "&eRank: &f%bellclaims_guild_rank%"
  - "&eLevel: &f%bellclaims_guild_level%"
  - "&eBalance: &f$%bellclaims_guild_balance%"
```

## Advanced Usage

### Custom Chat Ranks
Create conditional formatting based on guild membership:

```
%bellclaims_has_guild% ? "[%bellclaims_guild_emoji%] %player_displayname%" : "[No Guild] %player_displayname%"
```

### Guild-Based Permissions
Use in permission plugins or custom commands:

```
/guild info %bellclaims_guild_name%
/guild deposit %bellclaims_guild_balance%
```

### Dynamic Scoreboards
Show different information based on guild status:

```
%bellclaims_has_guild% ? "Guild: %bellclaims_guild_name%" : "Join a guild to see stats!"
```

---

**LumaGuilds PlaceholderAPI expansion provides seamless integration with your server's chat and display plugins!** 🎉🏷️
