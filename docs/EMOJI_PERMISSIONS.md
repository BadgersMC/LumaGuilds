# Emoji Permission System

## Overview

LumaGuilds uses a configurable permission system for Nexo emojis. This allows server administrators to control which emojis players can use for their guild emojis.

## Configuration

The emoji permission prefix is configurable in `config.yml`:

```yaml
chat:
  # Emojis and Formatting
  enable_emojis: true
  emoji_permission_prefix: "lumalyte.emoji"  # Customize this prefix
  max_emojis_per_message: 5
```

## Permission Format

**Format:** `<prefix>.<emojiname>`

Where:
- `<prefix>` = The configured value from `config.yml` (default: `lumalyte.emoji`)
- `<emojiname>` = The emoji name without colons (e.g., `cat`, `diamond`, `heart`)

### Examples

**With default prefix (`lumalyte.emoji`):**
| Emoji Placeholder | Permission Required |
|-------------------|-------------------|
| `:cat:` | `lumalyte.emoji.cat` |
| `:diamond:` | `lumalyte.emoji.diamond` |
| `:heart:` | `lumalyte.emoji.heart` |

**With custom prefix (`myserver.emotes`):**
| Emoji Placeholder | Permission Required |
|-------------------|-------------------|
| `:cat:` | `myserver.emotes.cat` |
| `:diamond:` | `myserver.emotes.diamond` |
| `:heart:` | `myserver.emotes.heart` |

## Granting Permissions

### Grant All Emojis
```yaml
# In your permissions plugin (LuckPerms, etc.)
<prefix>.*
```

### Grant Specific Emojis
```yaml
<prefix>.cat
<prefix>.diamond
<prefix>.heart
```

### Examples with LuckPerms

**Using default prefix:**
```bash
/lp group default permission set lumalyte.emoji.* true
/lp user Steve permission set lumalyte.emoji.cat true
```

**Using custom prefix (`nexo.emoji`):**
```yaml
# First, change config.yml:
chat:
  emoji_permission_prefix: "nexo.emoji"

# Then grant permissions:
/lp group vip permission set nexo.emoji.* true
/lp user Steve permission set nexo.emoji.diamond true
```

## Use Cases

### Scenario 1: Match Nexo's Native Permissions
If Nexo uses `nexo.emoji.*` for its own permissions:
```yaml
chat:
  emoji_permission_prefix: "nexo.emoji"
```

Now LumaGuilds will check the same permissions as Nexo.

### Scenario 2: Separate Guild Emoji Permissions
Keep guild emojis separate from chat emojis:
```yaml
chat:
  emoji_permission_prefix: "guild.emoji"
```

Grant players different permissions for chat vs guild emojis.

### Scenario 3: Role-Based Emoji Access
```yaml
chat:
  emoji_permission_prefix: "rank.emoji"
```

Then set up role-based permissions:
```bash
/lp group vip permission set rank.emoji.* true
/lp group default permission set rank.emoji.basic.* true
```

## How It Works

When a player tries to set a guild emoji:

1. Player runs `/guild emoji :cat:`
2. LumaGuilds extracts the emoji name: `cat`
3. Checks config for prefix: `lumalyte.emoji` (or your custom prefix)
4. Builds permission: `lumalyte.emoji.cat`
5. Checks if player has this permission
6. If yes → emoji is set
7. If no → error message shown

## Migration Guide

### Changing the Prefix

If you change the emoji permission prefix, you'll need to update all existing permissions:

**Before (default):**
```
lumalyte.emoji.*
lumalyte.emoji.cat
lumalyte.emoji.diamond
```

**After (custom prefix `nexo.emoji`):**
```yaml
# 1. Update config.yml
chat:
  emoji_permission_prefix: "nexo.emoji"

# 2. Update permissions
/lp group default permission unset lumalyte.emoji.*
/lp group default permission set nexo.emoji.* true
```

## Debugging

### Check Current Prefix
Look at `config.yml` under `chat.emoji_permission_prefix`

### Check Player Permissions
```bash
/lp user <player> permission check <prefix>.<emojiname>
```

Example:
```bash
/lp user Steve permission check lumalyte.emoji.cat
```

### Enable Debug Logging
LumaGuilds logs permission checks at DEBUG level:
```
[LumaGuilds] Player Steve does not have permission for emoji: lumalyte.emoji.cat
```

## Integration with Nexo

This permission system is **separate** from Nexo's built-in permissions (if any exist). It's specifically for controlling which emojis can be used as **guild emojis**.

If you want to sync with Nexo's permissions, set the prefix to match whatever Nexo uses:
```yaml
chat:
  emoji_permission_prefix: "nexo.emoji"  # Or whatever Nexo uses
```

---

*Last updated: December 15, 2025*
*Plugin Version: 0.5.3*
