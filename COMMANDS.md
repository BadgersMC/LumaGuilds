# LumaGuilds Commands & Permissions Documentation

## 📋 Overview
LumaGuilds is a comprehensive guild management plugin with party chat, claim management, and war systems. This document provides a complete reference for all commands and their required permissions.

## 🔧 Plugin Configuration

### Default Permissions
- **Guild Creation**: `lumaguilds.guild.create` - Default: `true` (anyone can create guilds)
- **Guild Info**: `lumaguilds.guild.info` - Default: `true` (anyone can view guild info)
- **Guild Management**: `lumaguilds.guild.*` - Default: `true` (guild members can manage their guilds)
- **War Declarations**: `lumaguilds.guild.war` - Default: `true` (guild members can wage war)
- **Claim Management**: `lumaguilds.command.*` - Default: `op` (operators only)
- **Party Chat**: `lumaguilds.partychat.*` - Default: `op` (operators only)
- **Admin Commands**: `lumaguilds.admin` - Default: `op` (operators only)

---

## 🏰 Guild Management Commands

### Core Guild Commands
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/guild create <name> [banner]` | `lumaguilds.guild.create` | Create a new guild | `/guild create MyGuild diamond_banner` |
| `/guild rename <name>` | `lumaguilds.guild.rename` | Rename your guild | `/guild rename NewName` |
| `/guild disband` | `lumaguilds.guild.disband` | Disband your guild | `/guild disband` |
| `/guild menu` | `lumaguilds.guild.menu` | Open guild management menu | `/guild menu` |
| `/guild info [guild]` | `lumaguilds.guild.info` | View guild information | `/guild info` or `/guild info OtherGuild` |

### Home & Teleportation
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/guild sethome [confirm]` | `lumaguilds.guild.sethome` | Set guild home location | `/guild sethome` or `/guild sethome confirm` |
| `/guild home` | `lumaguilds.guild.home` | Teleport to guild home | `/guild home` |

### Member Management
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/guild invite <player>` | `lumaguilds.guild.invite` | Invite player to guild | `/guild invite Notch` |
| `/guild kick <player>` | `lumaguilds.guild.kick` | Kick player from guild | `/guild kick BadPlayer` |

### Guild Customization
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/guild emoji <emoji>` | `lumaguilds.guild.emoji` | Change guild emoji | `/guild emoji ⚔️` |
| `/guild tag <tag>` | `lumaguilds.guild.tag` | Change guild tag | `/guild tag [TAG]` |
| `/guild description <desc>` | `lumaguilds.guild.description` | Change guild description | `/guild description Welcome to our guild!` |
| `/guild mode <peaceful/hostile>` | `lumaguilds.guild.mode` | Change guild mode | `/guild mode peaceful` |
| `/guild ranks` | `lumaguilds.guild.ranks` | Manage guild ranks | `/guild ranks` |

### War & Diplomacy
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/guild war <guild>` | `lumaguilds.guild.war` | Declare war on another guild | `/guild war EnemyGuild` |

### Command Aliases
- `/g` - Alias for `/guild`
- `/guild` - Full command
- All subcommands support case-insensitive input

---

## 🏞️ Claim Management Commands

### Basic Claim Commands
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/claim` | `lumaguilds.command.claim` | Create claim at current location | `/claim` |
| `/claim info` | `lumaguilds.command.claim.info` | View claim information | `/claim info` |
| `/claim remove` | `lumaguilds.command.claim.remove` | Remove claim at location | `/claim remove` |
| `/claim rename <name>` | `lumaguilds.command.claim.rename` | Rename claim | `/claim rename MyHouse` |

### Trust Management
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/claim trust <player>` | `lumaguilds.command.claim.trust` | Trust player in claim | `/claim trust Friend` |
| `/claim trustall` | `lumaguilds.command.claim.trustall` | Trust all online players | `/claim trustall` |
| `/claim untrust <player>` | `lumaguilds.command.claim.untrust` | Untrust player | `/claim untrust Enemy` |
| `/claim untrustall` | `lumaguilds.command.claim.untrustall` | Untrust all players | `/claim untrustall` |
| `/claim trustlist` | `lumaguilds.command.claim.trustlist` | List trusted players | `/claim trustlist` |

### Claim Customization
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/claim description <desc>` | `lumaguilds.command.claim.description` | Set claim description | `/claim description My beautiful home` |
| `/claim addflag <flag>` | `lumaguilds.command.claim.addflag` | Add flag to claim | `/claim addflag keep_inventory` |
| `/claim removeflag <flag>` | `lumaguilds.command.claim.removeflag` | Remove flag from claim | `/claim removeflag keep_inventory` |

### Advanced Claim Management
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/claim partitions` | `lumaguilds.command.claim.partitions` | Manage claim partitions | `/claim partitions` |
| `/claimlist` | `lumaguilds.command.claimlist` | List all your claims | `/claimlist` |
| `/claimmenu` | `lumaguilds.command.claimmenu` | Open claim management menu | `/claimmenu` |
| `/claimoverride` | `lumaguilds.command.claimoverride` | Override claim permissions | `/claimoverride` |

---

## 💬 Party Chat Commands

### Basic Party Chat
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/pc` | `lumaguilds.partychat` | Send message to party | `/pc Hello party!` |
| `/pc toggle` | `lumaguilds.partychat` | Toggle party chat mode | `/pc toggle` |
| `/pc help` | `lumaguilds.partychat` | Show party chat help | `/pc help` |

### Party Management
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/pc clear` | `lumaguilds.partychat` | Clear party chat | `/pc clear` |

### Command Aliases
- `/pc` - Primary command
- `/pchat` - Alias
- `/partychat` - Full alias

---

## ⚙️ Administrative Commands

### LumaGuilds Admin
| Command | Permission | Description | Usage |
|---------|------------|-------------|-------|
| `/lumaguilds download <exportId>` | `lumaguilds.admin` | Download exported data | `/lumaguilds download abc123` |
| `/lumaguilds exports` | `lumaguilds.admin` | List available exports | `/lumaguilds exports` |
| `/lumaguilds cancel <exportId>` | `lumaguilds.admin` | Cancel export | `/lumaguilds cancel abc123` |
| `/lumaguilds override` | `bellclaims.admin` | Toggle admin override mode | `/lumaguilds override` |
| `/lumaguilds help` | `lumaguilds.admin` | Show admin help | `/lumaguilds help` |

**Admin Override Mode**: When enabled, grants the administrator owner-level permissions in all guild claims, bypassing normal membership and rank checks. Override mode is automatically cleared on logout.

### Command Aliases
- `/lumaguilds` - Primary command
- `/bellclaims` - Legacy alias (for backward compatibility)

---

## 🔐 Permission Hierarchy

### Guild Permissions
```
lumaguilds.guild.*
├── lumaguilds.guild.create      (default: true)
├── lumaguilds.guild.rename      (default: op)
├── lumaguilds.guild.sethome     (default: op)
├── lumaguilds.guild.home        (default: op)
├── lumaguilds.guild.ranks       (default: op)
├── lumaguilds.guild.emoji       (default: op)
├── lumaguilds.guild.mode        (default: op)
├── lumaguilds.guild.disband     (default: op)
├── lumaguilds.guild.menu        (default: op)
├── lumaguilds.guild.invite      (default: op)
├── lumaguilds.guild.kick        (default: op)
├── lumaguilds.guild.tag         (default: op)
├── lumaguilds.guild.description (default: op)
├── lumaguilds.guild.war         (default: op)
└── lumaguilds.guild.info        (default: true)
```

### Claim Permissions
```
lumaguilds.command.*
├── lumaguilds.command.claim
├── lumaguilds.command.claim.addflag
├── lumaguilds.command.claim.description
├── lumaguilds.command.claim.info
├── lumaguilds.command.claim.partitions
├── lumaguilds.command.claim.remove
├── lumaguilds.command.claim.rename
├── lumaguilds.command.claim.trust
├── lumaguilds.command.claim.trustall
├── lumaguilds.command.claim.trustlist
├── lumaguilds.command.claim.untrust
├── lumaguilds.command.claim.untrustall
├── lumaguilds.command.claim.removeflag
├── lumaguilds.command.claimlist
├── lumaguilds.command.claimmenu
└── lumaguilds.command.claimoverride
```

### Other Permissions
```
lumaguilds.partychat.*     (default: op)
lumaguilds.admin          (default: op)
```

---

## 🎯 Quick Permission Setup

### For Regular Players (Allow Guild Creation & Info)
```yaml
lumaguilds.guild.create: true
lumaguilds.guild.info: true
```

### For Guild Members (Basic Guild Management)
```yaml
lumaguilds.guild.*: true
```

### For Server Operators (Full Access)
```yaml
lumaguilds.*: true
```

### For Minimal Setup (Claims Disabled)
```yaml
lumaguilds.guild.*: true
lumaguilds.partychat.*: true
lumaguilds.admin: true
```

---

## 📝 Configuration Notes

### Claims System
- All claim commands require `lumaguilds.command.*` permissions
- Claims can be disabled in config without affecting guild functionality
- Claim-related progression messages are automatically hidden when claims disabled

### Party System
- Party chat requires `lumaguilds.partychat` permission
- Parties can be disabled in config
- Party invitations show in dedicated GUI menu

### War System
- Wars require `lumaguilds.guild.war` permission
- Peace agreements replace instant war ending
- Daily war costs configurable (EXP and money loss)
- War farming prevention via cooldown system

### Permission Defaults
- **Guild creation and info viewing**: Available to all players by default
- **Guild management**: OP-only by default for security
- **Claim management**: OP-only by default (resource intensive)
- **Party chat**: OP-only by default (spam prevention)
- **Admin commands**: OP-only by default (server management)

---

## 🚀 Getting Started

1. **Install Plugin**: Place LumaGuilds.jar in your plugins folder
2. **Configure Permissions**: Use the permissions above based on your server needs
3. **Set Defaults**: Guild creation is enabled by default for all players
4. **Test Commands**: Use `/lumaguilds help` for admin commands
5. **GUI Access**: Most features available through `/guild menu`

---

## ❓ Troubleshooting

### "You don't have permission" Errors
- Check if claims are enabled in config (affects claim permissions)
- Verify permission nodes are correctly set in your permission plugin
- Some features require OP status by default

### Commands Not Working
- Ensure ACF (Aikar Commands Framework) is properly loaded
- Check server logs for any plugin loading errors
- Verify command aliases aren't conflicting with other plugins

### Tab Completion Not Working
- ACF provides built-in tab completion for all registered commands
- Some completions require specific permissions
- Case-insensitive completion is enabled by default

---

*Last updated: September 11, 2025*
*Plugin Version: 0.4.0*
