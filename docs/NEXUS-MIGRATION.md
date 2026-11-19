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
- `NexusGuildCoreBindings` maps guild repositories, member/rank/guild/relations services, invitations, cosmetics, Bannerman, and strikes onto lazy Nexus factories. Claim-role permission resolver registration is conditional; invalidation callbacks resolve it lazily to avoid constructor cycles. The legacy InviteBlock implementation is registered once, and Nexus indexes both service interfaces to that same singleton.
- `GuildServiceBukkit` now receives lazy config/reward/progression providers and a Bannerman callback, avoiding the guild-to-listener constructor cycle without global Koin access. `MemberServiceBukkit` uses optional late-bound Apollo providers, and `GuildInvitationManager` accepts its notification provider during initialization. Legacy Koin wiring supplies those providers until cutover; the Nexus guild graph supplies equivalent lazy lookups. `AuditServiceBukkit` and `GuildBannerServiceBukkit` already use constructor injection.
- `NexusSocialBindings` maps 39 of the 41 socialModule registrations as lazy singleton factories; the remaining two DiscordSRV subscriptions are mapped by `NexusDiscordBindings`. `ChatInputListener` and `GuildChatListener` use constructor injection in both the transitional Koin wiring and the Nexus graph, keeping their shared per-player state in one singleton.
- `NexusProgressionBindings` maps rewards/prestige/XP and repositories as 36 lazy Nexus beans, retaining shared concrete/interface singletons and deferred callbacks. `NexusWarBindings` adds eight war/notification beans; `NexusQuestBindings` adds eleven quest, completion-notification and leaderboard beans. `NexusWebBindings` adds seven web/directory/projection beans, including `GuildListService` on the Nexus-owned virtual executor.
- `NexusEconomyBindings` maps the economy/guild-gold graph as 18 lazy Nexus beans, collapsing the legacy GuildGoldRepository alias into its concrete singleton to preserve per-guild lock ownership. War-banner raw gold uses a distinct holder bean so the two physical-gold adapters cannot be confused.
- `NexusVaultBindings` maps all eight vault-module contracts with claims lookup optional, preserving vault configuration snapshots and deferring task startup until the runtime owns its lifecycle. Its VaultInventoryListener remains KoinComponent internally and cannot handle Nexus-only events until converted.
- `NexusIntegrationBindings` reproduces optional Apollo/Lunar Client gates and registers PlaceholderAPI expansion lazily; `NexusDiscordBindings` registers the two DiscordSRV subscriptions with disabled-plugin fallbacks. They are mapped but their Koin-dependent internals (`LumaGuildsExpansion`, some Apollo listeners) still require conversion before cutover. The live plugin lifecycle remains Koin-owned.
- `AsyncTaskService` no longer implements KoinComponent or creates detached scopes. Its scope, virtual-thread dispatcher and Bukkit plugin are injected; the transitional Koin module supplies its preexisting equivalents until the final cutover. `PlayerMetadataServiceVault` now accepts an injected I/O dispatcher: Nexus supplies its managed virtual-thread dispatcher, while the legacy module supplies the existing named virtual dispatcher.
- `NexusProgressionListenerBindings` registers the progression listener and shared provenance FIFO lazily. The queue, `GuildListService`, and DiscordSRV consumers use `nexusVirtualExecutor()`, which borrows the Nexus-owned virtual-thread executor and rejects submissions after parent cancellation. There is no additional executor to close. Live listener startup awaits the final cutover.
- `GuildHelpCommands`, `NexusClaimCommands`, `NexusQuickChatCommands`, and `NexusAnnouncementCommands` provide inactive ACF-free `/guild help`, `/claim`, `/claimmenu`, `/gc`, `/gac`, `/gmc`, `/ga`, and `/gfa` handlers. They are **not registered** until the all-at-once command cutover. `ConfirmationMenu`, `GuildGettingStartedMenu`, and three simple Bedrock claim forms receive `LangService` through constructors. `BedrockMenuNavigator` also receives it explicitly and no longer implements `KoinComponent`; shared `BaseBedrockMenu` still has seven Koin lookup sites across its 71 subclasses, requiring a separate dependency-boundary migration.
- The new Nexus context is **not called** by `LumaGuilds.onEnable` yet. Existing Koin and ACF remain authoritative until cutover; no dual live containers.

Remaining migration work is primarily converting the many KoinComponent menus/listeners, eliminating ACF command handlers, wiring a single Nexus-only startup/shutdown lifecycle and preserving integrations at activation. Most legacy DI singleton groups now have inert Nexus registrations, including optional Apollo, Discord and web services. Runtime acceptance must still prove that the full graph resolves uniquely, payment semantics are unchanged, commands preserve ACF behavior, and scheduler/DB lifecycles close exactly once.

## Concurrency audit — November 18, 2026

The final framework cutover must use the **single NexusContext scope and virtual-thread dispatcher** for LumaGuilds-owned coroutine and blocking-I/O work. Do not switch Bukkit main-thread gameplay operations to virtual threads.

- **Already adapted:** `AsyncTaskService` uses an injected scope/dispatcher; `PlayerMetadataServiceVault` uses an injected dispatcher for its asynchronous Vault metadata queries. The new Nexus bootstrap supplies `context.scope` and `context.dispatchers.virtualThread` and cancels/terminates them on close.
- **Still legacy-owned:** `LumaGuilds.kt` initializes `CoroutineScope(Dispatchers.IO + SupervisorJob())`; `di/Modules.kt` creates `Executors.newVirtualThreadPerTaskExecutor()`, converts it to a coroutine dispatcher, and closes that executor on disable.
- **Managed progression XP flush (implemented):** `ProgressionEventListener` receives an injected parent `CoroutineScope` and creates only a child flush job on the injected virtual dispatcher. `shutdown()` cancels/joins that job, force-drains player counters, then flushes pending guild XP; it never cancels the parent scope. The transitional Koin binding supplies `pluginScope`, and the inert Nexus listener factory supplies `NexusContext.scope` with its managed virtual-thread dispatcher. Tests cover flush after parent cancellation and leaving the parent active after explicit shutdown.
- **Managed claim-role cache cleanup (implemented):** `GuildRolePermissionResolverBukkit` no longer allocates `newSingleThreadScheduledExecutor()`. The legacy Koin and future Nexus bindings both inject their parent scope and virtual-thread dispatcher; an hourly child job preserves the 10,000-entry cache cap. `shutdown()` cancels/joins only its child job (bounded to one second) and clears caches, leaving unrelated jobs alive.
- **Managed Bedrock form state cleanup (implemented):** `FormStateManager` no longer allocates a scheduled executor at class init. `start(ownerScope, cleanupDispatcher)` launches one idempotent child job (10-minute initial delay and cadence; 30-minute default expiry; lazy expiry on restore still applies before start). Transitional Koin `onEnable` starts it with `pluginScope` and `named("VirtualDispatcher")` right after `startKoin`; `shutdown()` cancels/joins only that child (bounded to five seconds), clears state, and permits restart. At cutover, call `start(context.scope, context.dispatchers.virtualThread)` from the Nexus lifecycle instead.
- **Bukkit-owned async schedules:** `GuildLeaderboardProjectionService`, `EnthusiaStaffStrikeFeed`, administrative commands and some bootstrap tasks use Bukkit async scheduling. Review one by one: keep Bukkit operations on their required server threads, and migrate only safe blocking I/O to Nexus virtual threads.
- **Release gate:** tests must confirm no lost XP, no orphan worker after disable, no double-close of storage/executors, no async Bukkit entity/world mutation, and no database writes beyond configured MariaDB pool capacity.
