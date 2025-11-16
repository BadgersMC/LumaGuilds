# Changelog

## [Unreleased] - 2025-11-15

### ✨ Features

- **MariaDB/MySQL Production Database Support**
  - Added full MariaDB/MySQL database support for production deployments
  - Created MariaDBStorage with HikariCP connection pooling
  - Implemented MariaDBMigrations system with version tracking
  - Added database configuration to config.yml (database_type, mariadb settings)
  - Supports connection pool tuning (max pool size, connection timeout, etc.)
  - Optimized for MariaDB with proper character encoding (utf8mb4)

- **Database Migration Utility**
  - New `/bellclaims migrate confirm` command to transfer SQLite → MariaDB
  - Migrates all guild data, claims, progression, and audit logs
  - Handles datetime format conversions automatically (ISO-8601 → MariaDB DATETIME)
  - Transaction-based with automatic rollback on failure
  - Preserves original SQLite database as backup
  - Detects and migrates only common columns between schemas
  - Provides detailed migration report with row counts per table

- **Guild Invitation Persistence**
  - Guild invitations now stored in database (previously in-memory)
  - Invitations persist across server restarts
  - Created GuildInvitation domain entity
  - Created GuildInvitationRepository interface and SQLite implementation
  - Added SQLite migration v10 → v11 for guild_invitations table
  - Integrated with existing GuildInvitationManager

### 🏗️ Architecture

- **Database-Agnostic Repository Pattern**
  - Refactored 21 repositories to accept `Storage<Database>` instead of `SQLiteStorage`
  - Repositories now work seamlessly with both SQLite and MariaDB
  - Updated dependency injection (Modules.kt) to properly handle generic storage
  - Removed unsafe type casts with proper generic type handling
  - Files affected: All guild, claim, partition, and audit repositories

### 🐛 Bug Fixes

- **Plugin Shutdown Crash**
  - Fixed UninitializedPropertyAccessException when plugin fails to start
  - Added initialization check for pluginScope before cancellation in onDisable()
  - Prevents secondary crash when database connection fails

- **MariaDB Connection Parameter Compatibility**
  - Fixed Property cacheResultSetMetadata does not exist error
  - Removed MySQL Connector/J-specific parameters incompatible with MariaDB JDBC driver
  - Kept only MariaDB-compatible optimizations (useServerPrepStmts, rewriteBatchedStatements)

### 🔧 Technical Changes

- **Dependencies**
  - Added org.mariadb.jdbc:mariadb-java-client:3.3.2 for MariaDB support

- **Configuration Updates**
  - Added MainConfig.MariaDBConfig data class
  - Added MainConfig.MariaDBPoolConfig data class
  - ConfigServiceBukkit now loads MariaDB configuration from config.yml

- **Migration System**
  - SQLite uses PRAGMA user_version for schema versioning
  - MariaDB uses schema_version table for version tracking
  - Both systems support incremental migrations

### 📊 Migration Statistics

The migration utility successfully handles:
- 21 tables across guild, claim, and audit systems
- Automatic datetime format conversion
- Foreign key dependency ordering
- Schema compatibility checking
- Column mismatch detection and reporting

### 🔒 Security Fixes



- **Critical: Unauthorized Guild Access Exploit** (ae06eeb, eab6cd2)
  - Fixed exploit allowing players to access other guilds' control panels and home locations
  - Exploit path: `/guild info <other_guild>` → Members → Control Panel (no membership check)
  - Added membership checks to GuildMemberListMenu, GuildControlPanelMenu, and GuildHomeMenu
  - Implemented defense-in-depth security across all three access points

- **Permission Bypass Vulnerability** (e6e4455) - GitHub Issue #1
  - Fixed vulnerability where guild members without MANAGE_GUILD_SETTINGS permission could access control panel through `/guild info` → member list
  - Added proper permission checks using `memberService.hasPermission()` API
  - Implemented defense-in-depth checks in both GuildMemberListMenu and GuildControlPanelMenu
  - Credit: @damlano for the fix recommendation

- **Owner Self-Demotion Prevention** (f03aaa9)
  - Prevent guild owner from demoting themselves (requires ownership transfer)
  - Prevent promoting anyone to owner rank directly (priority 0)
  - Add proper ownership succession handling via `transferOwnership` method

### ✨ Features

- **AxKoth Integration** (52a42ba)
  - Guilds can now participate in King of the Hill (KOTH) events as teams
  - Implemented TeamHook interface for AxKoth compatibility
  - Automatic registration when AxKoth plugin is detected
  - Added AxKoth to softdepend in plugin.yml

- **Guild Leave with Automatic Succession** (f03aaa9)
  - New `/guild leave` command with automatic owner succession
  - When owner leaves with other members present, ownership transfers to next highest rank
  - When owner is sole member, requires `/guild disband` instead
  - Prevents players from being stuck in ownerless guilds

- **Admin Emergency Disband** (f03aaa9)
  - New `/bellclaims disband <guild> confirm` command for OP/admin use
  - Forcefully disband any guild in emergency situations
  - Includes tab completion for guild names

- **CombatLogX Integration** (ae14aa8, 40d5580, 24f4cf2) - PR #5
  - Added combat mode detection before guild home teleportation
  - Players in combat cannot teleport to prevent combat logging
  - Integrated CombatLogX API for combat status checking
  - Fixed crash when CombatLogX plugin is unavailable

### 🐛 Bug Fixes

- **Bedrock Player Invite Issues** (7586f2a) - GitHub Issue #2
  - Fixed: Bedrock → Java invites failed
  - Fixed: Bedrock → Bedrock invites failed
  - Root cause: Floodgate prefix (typically `.`) wasn't handled in player lookups
  - Added `findPlayerByName()` helper method with Floodgate prefix fallback
  - Applied fix to `/guild invite`, `/guild kick`, and `/guild transfer` commands
  - Join message formatting differences are client-side rendering (not a bug)

- **"Unknown Player" Display Issue** (99b186d) - GitHub Issue #4
  - Removed unnecessary safe call operator (`?.`) on `Bukkit.getOfflinePlayer()`
  - Fixed display in 5 menu files: GuildInfoMenu, GuildKickMenu, GuildMemberManagementMenu, GuildKickConfirmationMenu, GuildMemberRankMenu
  - `getOfflinePlayer()` never returns null, only the `.name` property can be null

- **Guild Invites Not Working** (e6e4455)
  - Fixed invite storage - invites now properly stored via GuildInvitationManager
  - Players can now accept invites using `/guild join <guild>`
  - Removed obsolete TODO comments from BedrockGuildInviteConfirmationMenu

- **Guild Description Setting Broken** (e6e4455)
  - Fixed chat input callbacks not scheduled on main thread
  - GUI operations now properly scheduled via ChatInputListener

- **War System Not Functioning** (e6e4455)
  - Wars now properly activate and track kills
  - WarServiceBukkit sets wars to ACTIVE status and initializes stats
  - WarKillTrackingListener now properly registered in LumaGuilds main class

- **Guild Mode Cooldown Not Enforced** (e6e4455)
  - Command now enforces 7-day cooldown matching GUI behavior
  - Added war check - cannot switch to peaceful during active war
  - Added helper methods: `canSwitchToPeaceful()`, `canSwitchToHostile()`, `getCooldownMessage()`, `getHostileLockMessage()`

- **Guild Home Teleportation Issues** (1682fb9)
  - Fixed `/guild home confirm` parameter parsing (keyword treated as home name)
  - Fixed instant teleports with no countdown
  - Created TeleportationService to centralize teleport logic
  - Prevents conflicts between command-based and menu-based teleports
  - Added proper teleportation cleanup on player disconnect via PlayerSessionListener

- **Unsafe Home Detection Too Strict** (1682fb9)
  - Standing on decorative blocks (torches, carpets, etc.) no longer flagged as unsafe
  - Only true air drops checked, not passable decorative blocks

- **Multiple Guild Membership Loophole** (5317924)
  - Fixed exploit allowing players to be in multiple guilds simultaneously

- **Player Heads Not Displaying** (ae14aa8)
  - Fixed player heads and names not showing in several UI elements
  - Added missing `.build()` calls on ResolvableProfile instances (5 files)

- **Tag Formatting Issues** (ae14aa8) - GitHub Issue #3
  - Fixed guild tags not properly formatting with color codes
  - Implemented ColorCodeUtils.renderTagForDisplay() for proper rendering

- **Emoji Display Issues** (e6e4455)
  - Removed VS16 (U+FE0F) emoji variation selectors from 23 menu files
  - GUI emoji characters now display correctly for all clients
  - Changes: 778 insertions, 365 deletions

- **Guild Banner Menu Navigation** (30651cc)
  - Fixed back button to return to control panel instead of settings menu
  - Updated button label from "Return to settings menu" to "Return to control panel"

### 🔧 Technical Changes

- **Vault System Registration** (52a42ba)
  - Fixed missing vault system in Koin dependency injection
  - Registered GuildVaultRepository, GuildVaultService, VaultHologramService
  - Registered VaultProtectionListener for vault interactions
  - Fixed PlayerSessionListener to cleanup vault hologram tracking
  - Resolves NoDefinitionFoundException on player quit events

- **CSV Export Changes** (e6e4455) - BREAKING CHANGE
  - CSV exports now require Discord configuration
  - Removed local file download fallback
  - FileExportManager returns DiscordSuccess only
  - Players no longer see `/bellclaims download` command

- **Architecture Improvements** (1682fb9)
  - Centralized teleportation state management via TeleportationService
  - Thread-safe implementation using ConcurrentHashMap
  - All services registered in Koin DI for dependency injection

- **Code Quality Improvements** (24f4cf2)
  - Fixed force unwrap crash in CombatUtil (replaced `!!` with safe null check)
  - Completed ResolvableProfile builder pattern with `.build()` calls
  - Removed unused imports (ILoggerFactory, Console)
  - Removed unnecessary trailing semicolons
  - Version bump: 0.4.0 → 0.5.0

- **Domain Model Updates** (ae06eeb)
  - Added VaultConfig, GuildVaultLocation, VaultStatus to Guild entity
  - Added vault permissions to Rank entity
  - Updated menu files to handle new vault permissions

### 📊 Statistics

- **Total Commits**: 14
- **Files Changed**: 60+ files across all commits
- **Critical Security Fixes**: 3
- **GitHub Issues Resolved**: #1, #2, #3, #4
- **Pull Requests Merged**: #5 (CombatLogX Integration)

### 🙏 Credits

- @damlano - Permission bypass fix recommendation (Issue #1)
- @Claude - AI pair programming assistant
- Community bug reporters via Discord and GitHub Issues

---

## Version History

- **0.5.0** - Current development version with all above changes
- **0.4.0** - Previous stable version
