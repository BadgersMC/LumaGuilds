# LumaGuilds

<!-- Banner placeholder - uncomment when image is available
![Banner](docs/images/banner.png)
-->

> A comprehensive guild management and land claiming plugin for Minecraft servers running Paper 1.21+

[![Version](https://img.shields.io/badge/version-0.5.0-blue)](https://github.com/BadgersMC/LumaGuilds/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21+-green)](https://papermc.io/)
[![Java](https://img.shields.io/badge/java-21+-orange)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-purple)](https://kotlinlang.org/)

**LumaGuilds** (formerly Bell Claims) combines an intuitive bell-based land claiming system with full-featured guild management, wars, parties, and cross-platform support for both Java and Bedrock players.

---

## Highlights

| Feature | Description |
|---------|-------------|
| **Bell-Based Claiming** | Place a bell to anchor your claim - simple and intuitive |
| **Guild System** | Full guild management with ranks, banks, and shared territory |
| **War System** | Declare wars with objectives, wagers, and kill tracking |
| **Cross-Platform** | Native support for Java Edition GUIs and Bedrock Edition forms |
| **Economy Integration** | Vault support for claim costs and guild banking |
| **Extensible** | PlaceholderAPI, WorldGuard, and developer API |

---

## Table of Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Placeholders](#placeholders)
- [For Developers](#for-developers)
- [Support](#support)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### Land Claiming

Protect your builds with an intuitive bell-based claiming system.

- **Bell Anchors** - Place a bell to create a claim; shift+right-click to open management
- **Partitions** - Expand claims in any direction with multiple rectangular areas
- **14 Permissions** - Granular control: BUILD, BREAK, CONTAINER, DISPLAY, VEHICLE, SIGN, REDSTONE, DOOR, TRADE, HUSBANDRY, DETONATE, EVENT, SLEEP, VIEW
- **12 Protection Flags** - Control: fire spread, mob spawning, explosions, pistons, fluid flow, tree growth, sculk, dispensers, sponges, lightning, falling blocks, passive entity vehicles
- **Visual Borders** - See claim boundaries with particle highlighting
- **Claim Transfer** - Send and accept ownership transfer requests
- **Admin Override** - Staff can bypass protection when needed

### Guild System

Form communities with comprehensive guild management.

- **Guild Creation** - Create guilds with custom names, tags, and banners
- **Rank System** - Hierarchical ranks with customizable permissions
- **Member Management** - Invite, kick, promote, demote members
- **Contribution Tracking** - Track member contributions over time
- **Guild Bank** - Shared treasury with deposit/withdrawal controls
- **Guild Vaults** - Shared storage chests accessible by members
- **Guild Claims** - Convert personal claims to guild territory
- **Guild Levels** - Progression system with unlockable benefits

### War System

Engage in structured PvP conflicts between guilds.

- **War Declarations** - Formally declare war on rival guilds
- **Objectives** - Set kill counts and victory conditions
- **Wagers** - Bet guild bank funds on war outcomes
- **Kill Tracking** - Real-time war statistics and leaderboards
- **Peace Negotiations** - End wars through diplomacy
- **War History** - Track past conflicts and outcomes

### Party System

Form temporary groups for activities.

- **Quick Parties** - Create parties for dungeons, raids, or events
- **Public/Private** - Open parties or invite-only
- **Guild-Only** - Restrict parties to guild members
- **Party Chat** - Dedicated communication channel

### Cross-Platform Support

Full feature parity for all players.

- **Java Edition** - Rich inventory-based GUI menus (90+ menus)
- **Bedrock Edition** - Native form-based UI via Floodgate/Geyser
- **Seamless Experience** - Same features regardless of client

### Integrations

Works with popular plugins out of the box.

| Plugin | Integration |
|--------|-------------|
| **Vault** | Economy for claim costs, guild banks |
| **PlaceholderAPI** | 20+ placeholders for scoreboards, chat |
| **WorldGuard** | Respects WorldGuard regions |
| **CombatLogX** | Combat tagging compatibility |
| **AxKoth** | Guild/team King of the Hill events |
| **Geyser/Floodgate** | Bedrock Edition support |

---

## Screenshots

<details>
<summary>Click to view screenshots</summary>

### Claiming System
<!-- ![Claim Creation](docs/images/screenshots/claim-creation.png) -->
*Place a bell to create your claim, then expand with partitions*

### Guild Control Panel
<!-- ![Guild Panel](docs/images/screenshots/guild-panel.png) -->
*Manage your guild from the central control panel*

### Permission Management
<!-- ![Permissions](docs/images/screenshots/permissions.png) -->
*Grant granular permissions to trusted players*

### Bedrock Support
<!-- ![Bedrock Forms](docs/images/screenshots/bedrock-forms.png) -->
*Full feature access via Bedrock Edition forms*

</details>

---

## Installation

### Requirements

- **Server**: Paper 1.21+ (or forks like Purpur)
- **Java**: 21 or higher
- **Database**: SQLite (default) or MariaDB

### Dependencies

| Plugin | Required | Purpose |
|--------|----------|---------|
| Vault | Optional | Economy integration |
| PlaceholderAPI | Optional | Placeholder support |
| Floodgate | Optional | Bedrock player detection |
| Geyser | Optional | Bedrock Edition support |

### Installation Steps

1. Download `LumaGuilds-0.5.0.jar` from [releases](https://github.com/BadgersMC/LumaGuilds/releases)
2. Place in your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/LumaGuilds/config.yml` as needed
5. Run `/lumaguilds reload` to apply changes

---

## Quick Start

### Creating Your First Claim

1. Obtain a claim tool: `/claim`
2. Select two corners by right-clicking blocks
3. Place a **bell** inside your selection
4. Your claim is created! Shift+right-click the bell to manage it

### Creating a Guild

1. Run `/guild create <name>`
2. Customize with `/guild banner`, `/guild tag`, `/guild description`
3. Invite members with `/guild invite <player>`
4. Open the guild panel: `/guild` or shift+right-click a guild bell

### Managing Permissions

1. Shift+right-click your claim's bell
2. Navigate to **Manage Permissions**
3. Grant permissions to specific players or everyone

---

## Commands

### Claim Commands

| Command | Description |
|---------|-------------|
| `/claim` | Get the claim creation tool |
| `/claim info` | Show info about current claim |
| `/claim rename <name>` | Rename your claim |
| `/claim description <text>` | Set claim description |
| `/claim trust <player> <perm>` | Grant permission to player |
| `/claim untrust <player> <perm>` | Revoke permission from player |
| `/claim trustall <perm>` | Grant permission to everyone |
| `/claim untrustall <perm>` | Revoke permission from everyone |
| `/claim trustlist` | List trusted players |
| `/claim addflag <flag>` | Enable a protection flag |
| `/claim removeflag <flag>` | Disable a protection flag |
| `/claim partitions` | List claim partitions |
| `/claim remove` | Delete your claim |
| `/claimlist` | List all your claims |
| `/claimmenu` | Open claim management GUI |

### Guild Commands

| Command | Description |
|---------|-------------|
| `/guild` | Open guild panel |
| `/guild create <name>` | Create a new guild |
| `/guild disband` | Disband your guild |
| `/guild invite <player>` | Invite a player |
| `/guild kick <player>` | Kick a member |
| `/guild leave` | Leave your guild |
| `/guild promote <player>` | Promote a member |
| `/guild demote <player>` | Demote a member |
| `/guild bank deposit <amount>` | Deposit to guild bank |
| `/guild bank withdraw <amount>` | Withdraw from guild bank |
| `/guild war declare <guild>` | Declare war |
| `/guild war peace <guild>` | Offer peace |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/claimoverride` | Toggle admin bypass mode |
| `/lumaguilds reload` | Reload configuration |

---

## Permissions

### User Permissions

| Permission | Description |
|------------|-------------|
| `lumaguilds.command.claim` | Use `/claim` command |
| `lumaguilds.command.claim.info` | View claim information |
| `lumaguilds.command.claim.rename` | Rename claims |
| `lumaguilds.command.claim.description` | Set claim descriptions |
| `lumaguilds.command.claim.trust` | Trust/untrust players |
| `lumaguilds.command.claim.trustall` | Set default permissions |
| `lumaguilds.command.claim.trustlist` | View trusted players |
| `lumaguilds.command.claim.addflag` | Add protection flags |
| `lumaguilds.command.claim.removeflag` | Remove protection flags |
| `lumaguilds.command.claim.partitions` | View claim partitions |
| `lumaguilds.command.claim.remove` | Delete claims |
| `lumaguilds.command.claimlist` | List claims |
| `lumaguilds.command.claimmenu` | Open claim menu |
| `lumaguilds.command.guild` | Use guild commands |
| `lumaguilds.command.guild.create` | Create guilds |
| `lumaguilds.command.guild.invite` | Invite players |
| `lumaguilds.command.guild.kick` | Kick members |
| `lumaguilds.command.guild.war` | War commands |

### Admin Permissions

| Permission | Description |
|------------|-------------|
| `lumaguilds.command.claimoverride` | Toggle admin bypass |
| `lumaguilds.admin.reload` | Reload configuration |

### Per-Player Limits (via Vault/LuckPerms)

Set metadata values to customize limits per player or group:

```bash
# Set claim limit
/lp user <player> meta set lumaguilds.claim_limit 10

# Set total claim block limit
/lp user <player> meta set lumaguilds.claim_block_limit 50000

# For groups
/lp group <group> meta set lumaguilds.claim_limit 5
```

---

## Configuration

Key configuration options in `config.yml`:

```yaml
# Database configuration
database:
  type: sqlite  # sqlite or mariadb

# Claim settings
claims:
  default-size: 15           # Default claim radius
  max-claims-per-player: 5   # Default claim limit
  claim-block-limit: 10000   # Default max blocks per player

# Guild settings
guilds:
  creation-cost: 1000        # Cost to create guild
  max-members: 50            # Member limit
  bank-interest-rate: 0.01   # Daily interest rate

# War settings
wars:
  enabled: true
  minimum-wager: 100
  default-kill-objective: 10
```

---

## Placeholders

All placeholders use the `lumaguilds` prefix. Requires PlaceholderAPI.

| Placeholder | Description |
|-------------|-------------|
| `%lumaguilds_claim_count%` | Number of claims owned |
| `%lumaguilds_in_claim%` | Whether player is in a claim |
| `%lumaguilds_claim_name%` | Name of current claim |
| `%lumaguilds_claim_owner%` | Owner of current claim |
| `%lumaguilds_guild_name%` | Player's guild name |
| `%lumaguilds_guild_tag%` | Player's guild tag |
| `%lumaguilds_guild_level%` | Player's guild level |
| `%lumaguilds_guild_bank%` | Guild bank balance |
| `%lumaguilds_guild_member_count%` | Guild member count |
| `%lumaguilds_guild_rank%` | Player's rank in guild |

---

## For Developers

LumaGuilds is built with clean architecture principles, making it easy to extend and integrate.

### Architecture

The plugin follows **hexagonal (clean) architecture**:

```
net.lumalyte.lg
├── domain/          # Core entities (Claim, Guild, War, etc.)
├── application/     # Use cases and business logic
├── infrastructure/  # Database, Bukkit adapters
└── interaction/     # Commands, menus, listeners
```

### Tech Stack

- **Language**: Kotlin 2.0
- **DI Framework**: Koin 4.0
- **Commands**: ACF (Annotation Command Framework)
- **Database**: SQLite/MariaDB via HikariCP
- **GUI**: InventoryFramework + Cumulus (Bedrock)

### Accessing the API

```kotlin
// Via Koin dependency injection
class YourFeature : KoinComponent {
    private val getClaimAtPosition: GetClaimAtPosition by inject()

    fun checkClaim(position: Position3D, worldId: UUID): Claim? {
        return when (val result = getClaimAtPosition.execute(position, worldId)) {
            is GetClaimAtPositionResult.Success -> result.claim
            else -> null
        }
    }
}
```

### Building from Source

```bash
git clone https://github.com/BadgersMC/LumaGuilds.git
cd LumaGuilds
./gradlew shadowJar
# Output: build/libs/LumaGuilds-0.5.0.jar
```

### Running Tests

```bash
./gradlew test
```

### Documentation

Detailed developer documentation is available in the [`/docs`](docs/) folder:

- [Architecture Overview](docs/architecture.md) - Hexagonal architecture principles
- [Domain Layer](docs/domain.md) - Core entities and value objects
- [Application Layer](docs/application.md) - Use cases and actions
- [Infrastructure Layer](docs/infrastructure.md) - Database and Bukkit integration
- [Interaction Layer](docs/interaction.md) - Commands and menus
- [API Reference](docs/api-reference.md) - Complete action catalog
- [Integration Guide](docs/integration.md) - External plugin integration
- [Getting Started](docs/getting-started.md) - Development guide

---

## Support

### Getting Help

- **Issues**: [GitHub Issues](https://github.com/BadgersMC/LumaGuilds/issues)
- **Discord**: [@mizarc](https://discord.com/users/97295777734332416)

### Reporting Bugs

Please include:
1. Server version (`/version`)
2. Plugin version
3. Steps to reproduce
4. Error logs (if any)

---

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Quick start:
1. Fork the repository
2. Create a feature branch (`feature/my-feature`)
3. Make your changes
4. Submit a pull request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Credits

- **Original Author**: [Mizarc](https://github.com/mizarc) - Creator of Bell Claims
- **Contributors**: See [contributors](https://github.com/BadgersMC/LumaGuilds/graphs/contributors)

LumaGuilds is a fork of [Bell Claims](https://github.com/mizarc/bell-claims), extending the original land-claiming system with comprehensive guild management features.

---

<p align="center">
  Made with Kotlin for the Minecraft community
</p>
