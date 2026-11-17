# LumaGuilds migration to Nexus (Paper 26.2 only)

Baseline: LumaGuilds `33d6300c` (merged 26.2 quest craft event).
Framework pin: Nexus `5ae8b6424f39d3de8098cdbd46c178d3f8700205` (PR #13 merged).
This branch is a migration workspace, **not a deployable release**.

## Target architecture

- **Nexus DI and lifecycle**, not Koin. Register interface bindings explicitly in domain-scoped binding groups, with constructor injection and no global service lookups.
- **Nexus Brigadier commands**, not ACF. Group handlers by function (founding, homes, membership, relations, economy, moderation, chat, help) under the shared `guild` root and `g` alias.
- **One Nexus-owned coroutine scope** and virtual-thread dispatcher. Remove LumaGuilds' manual supervisor scope, custom virtual-thread executor, and dispatcher aliases after consumers are migrated.
- **Storage bootstrap before service activation**, preserving idempotent SQLite/MariaDB migrations. The Aikar `idb-core` database dependency is separate from ACF and must not be removed until its own replacement is ready.
- Preserve externally published Bukkit ServicesManager APIs (GuildLookup, GuildVisualLookup, GuildShopXpApi, GuildInviteBlocks, etc.) and the existing plugin dependency integration behavior.

## Strict startup/shutdown ordering

1. Initialize PluginKeys, load configuration, determine claims mode.
2. Construct the configured Storage and finish database migrations.
3. Create NexusContext and register core external dependencies.
4. Validate/reconcile reward accounts, then register external Bukkit service APIs.
5. Register optional integrations and event listeners; register **only** Nexus commands.
6. Start schedulers after service registration and dependencies are healthy.
7. On disable: stop schedules/listeners and integrations, close the Nexus context, then close storage/DB exactly once.

## Migration sequence

1. **Foundation (this PR)**: pin same Nexus commit for core, Paper and i18n; add an inert Nexus foundation and an initial converted `guild help` command; test registration/annotations without activating two DI containers.
2. **DI graph**: replace all Koin module groups in `di/Modules.kt` with explicit Nexus registrations. Keep claims bindings conditional and preserve precise singleton ownership. Migrate KoinComponent constructor call sites into constructor injection.
3. **Commands**: convert remaining `GuildCommand.kt` and other ACF handlers to the Nexus API. Preserve permissions, alias, completion providers, default routes, optional arguments and player-only rules. Verify nested literals and shared roots in Paper 26.2.
4. **Bootstrap/lifecycle**: split `LumaGuilds.kt` responsibilities into coordinated storage, integrations, event, command and scheduler registration. Activate Nexus once. No dual live contexts.
5. **Cutover**: remove the Koin and ACF dependencies/imports/ACF shading, verify no forbidden references remain, and delete obsolete command injection utilities. Retain IDB until a separately tested persistence migration.
6. **Acceptance**: full unit/SQLite and opt-in MariaDB coverage, Paper 26.2 runtime tests, guild commands/GUI/regression coverage, guild-service consumers, and Geyser/Floodgate compatibility.

## Non-negotiable release gates

- No production deployment or merge of a partially migrated plugin.
- No gameplay changes mixed into framework migration.
- No Koin/ACF references in the final production source/packaging.
- No semantic change in guild quest accounting, homes, bank, market APIs, guild rank permissions, raids, or Chapter 2 state.
- Preserve startup fail-closed behavior and database ownership.
- The command-composition support is merged into Nexus, but its live Paper 26.2 integration test remains required before command cutover.

## Progress on the migration branch

- `GuildNexusFoundation` registers plugin/config/storage, lazy i18n and **both utility services** (async tasks and teleportation) in an isolated NexusContext.
- `NexusCoreServicesBindings` registers platform detection, Bedrock localization, form caching/validation, GUI construction, and optional Vault Chat metadata without creating nullable Nexus beans.
- `NexusClaimsBindings` registers all **80 claimsModule bindings** by their original contracts, with a matching count and no names missing. Claims registration remains optional, based on the `claimsEnabled` flag.
- `AsyncTaskService` no longer implements KoinComponent or creates detached scopes. Its scope, virtual-thread dispatcher and Bukkit plugin are injected; the transitional Koin module supplies its preexisting equivalents until the final cutover.
- `GuildHelpCommands` is the first Nexus-only command implementation, **not registered** until the full command tree is migrated.
- The new Nexus context is **not called** by `LumaGuilds.onEnable` yet. Existing Koin and ACF remain authoritative until cutover; no dual live containers.

Remaining DI groups: Guild core, guild-claim permissions integration, social, progression, economy, vault, core auxiliary services, and optional integrations. Runtime acceptance must establish that shared interfaces resolve uniquely and the scheduler/DB lifecycle closes exactly once.
