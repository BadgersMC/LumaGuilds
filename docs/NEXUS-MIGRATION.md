# LumaGuilds migration to Nexus (Paper 26.2 only)

Baseline: LumaGuilds `33d6300c` (merged 26.2 quest craft event).
The local cutover uses Nexus `eb55c12a4547f07a9553ecd522989e046c093dda`. Restore its Git bundle from the handoff and use a clean checkout. With the two repositories alongside each other, run `./gradlew -PnexusSourceDir=../nexus test verifyRuntimePackaging verifyAcceptanceProbePackaging` on JDK 25. The same pin covers core, Paper and i18n. GitHub CI needs a published dependency before it can build this revision without the local source.
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

## Complete consumer dependency migration — November 21, 2026

The initial inventory contained 320 `KoinComponent` occurrences in 181 production Kotlin files.
The broader audit covered 211 files, including inherited Bedrock delegates and hidden lookups.
Four disjoint worker manifests converted 206 files: 39 Java menus, 45 Java claim/guild menus,
69 Bedrock forms/helpers, and 53 commands/listeners/services. Shared factories, the Bedrock base,
and three previously converted claim forms were completed centrally.

Production source now contains zero component declarations/imports, legacy injection delegates,
or per-consumer container lookups. The retained Koin/ACF composition roots still own production
startup; the Nexus foundation remains inert. Menus use enumerated typed lazy collaborators;
other consumers use constructor dependencies or typed deferred providers. Claims-disabled and
optional integration paths preserve nullable and first-use behavior. No generic resolver was
introduced in feature classes.

The four isolated focused regressions passed (215, 211, 78 and 22 tests). The source guard was
observed failing before integration; it also checks that Bedrock forms do not inherit a legacy
fallback. A source-pattern scan covered 1,054 Kotlin files with zero findings and zero scan errors.
Command/event annotations and checked localization keys were unchanged across all 206 manifests.
Combined and full regression evidence is recorded in the implementation plan. Fresh hosted CI,
Paper command parity, and real Java/Bedrock acceptance remain separate release gates.

## Progress on the migration branch

- `GuildNexusFoundation` registers plugin/config/storage, lazy i18n and **both utility services** (async tasks and teleportation) in an isolated NexusContext.
- `NexusCoreServicesBindings` registers platform detection, Bedrock localization, form caching/validation, GUI construction, and optional Vault Chat metadata without creating nullable Nexus beans.
- `NexusClaimsBindings` registers all **80 claimsModule bindings** by their original contracts, with a matching count and no names missing. Claims registration remains optional, based on the `claimsEnabled` flag.
- `NexusGuildCoreBindings` maps guild repositories, member/rank/guild/relations services, invitations, cosmetics, Bannerman, and strikes onto lazy Nexus factories. Claim-role permission resolver registration is conditional; invalidation callbacks resolve it lazily to avoid constructor cycles. The legacy InviteBlock implementation is registered once, and Nexus indexes both service interfaces to that same singleton.
- `GuildServiceBukkit` now receives lazy config/reward/progression providers and a Bannerman callback, avoiding the guild-to-listener constructor cycle without global Koin access. `MemberServiceBukkit` uses optional late-bound Apollo providers, and `GuildInvitationManager` accepts its notification provider during initialization. Legacy Koin wiring supplies those providers until cutover; the Nexus guild graph supplies equivalent lazy lookups. `AuditServiceBukkit` and `GuildBannerServiceBukkit` already use constructor injection.
- `NexusSocialBindings` maps 39 of the 41 socialModule registrations as lazy singleton factories; the remaining two DiscordSRV subscriptions are mapped by `NexusDiscordBindings`. `ChatInputListener` and `GuildChatListener` use constructor injection in both the transitional Koin wiring and the Nexus graph, keeping their shared per-player state in one singleton.
- `NexusProgressionBindings` maps rewards/prestige/XP and repositories as 36 lazy Nexus beans, retaining shared concrete/interface singletons and deferred callbacks. `NexusWarBindings` adds eight war/notification beans; `NexusQuestBindings` adds eleven quest, completion-notification and leaderboard beans. `NexusWebBindings` adds seven web/directory/projection beans, including `GuildListService` on the Nexus-owned virtual executor.
- `NexusEconomyBindings` maps the economy/guild-gold graph as 18 lazy Nexus beans, collapsing the legacy GuildGoldRepository alias into its concrete singleton to preserve per-guild lock ownership. War-banner raw gold uses a distinct holder bean so the two physical-gold adapters cannot be confused.
- `NexusVaultBindings` maps all eight vault-module contracts with claims lookup optional, preserving vault configuration snapshots and deferring task startup until the runtime owns its lifecycle. VaultInventoryListener now receives bank, localization and deferred child-menu collaborators explicitly in both graphs.
- `NexusIntegrationBindings` reproduces optional Apollo/Lunar Client gates and registers PlaceholderAPI expansion lazily; `NexusDiscordBindings` registers the two DiscordSRV subscriptions with disabled-plugin fallbacks. Their consumers now receive explicit dependencies, including nullable per-event Apollo providers; the PlaceholderAPI expansion uses an explicit lazy factory in each graph. The live plugin lifecycle remains Koin-owned.
- `AsyncTaskService` no longer implements KoinComponent or creates detached scopes. Its scope, virtual-thread dispatcher and Bukkit plugin are injected; the transitional Koin module supplies its preexisting equivalents until the final cutover. `PlayerMetadataServiceVault` now accepts an injected I/O dispatcher: Nexus supplies its managed virtual-thread dispatcher, while the legacy module supplies the existing named virtual dispatcher.
- `NexusProgressionListenerBindings` registers the progression listener and shared provenance FIFO lazily. The queue, `GuildListService`, and DiscordSRV consumers use `nexusVirtualExecutor()`, which borrows the Nexus-owned virtual-thread executor and rejects submissions after parent cancellation. There is no additional executor to close. Live listener startup awaits the final cutover.
- `GuildHelpCommands`, `NexusClaimCommands`, `NexusQuickChatCommands`, and `NexusAnnouncementCommands` provide inactive ACF-free `/guild help`, `/claim`, `/claimmenu`, `/gc`, `/gac`, `/gmc`, `/ga`, and `/gfa` handlers. They are **not registered** until the all-at-once command cutover. `ConfirmationMenu`, `GuildGettingStartedMenu`, and three simple Bedrock claim forms receive `LangService` through constructors. `BedrockMenuNavigator` also receives it explicitly and no longer implements `KoinComponent`. The shared `BaseBedrockMenu` and all its forms now require explicit `BedrockMenuDependencies`; the inherited legacy fallback has been removed. `NexusCoreServicesBindings` supplies it lazily through `MenuFactory` for the three converted claim forms, both generic confirmation variants, player chat settings, and the guild directory. Chat and directory services use lazy typed providers; the directory provider is bound internally to avoid exposing its internal service type in `MenuFactory`'s public constructor. Focused tests construct and render the newly wired screens without starting Koin. All remaining Bedrock forms and their direct child paths use the explicit bundle. The compiled inheritance guard verifies that the base no longer implements the legacy component interface.
- The claim-command swarm added inactive `/claim remove`, `/claimoverride`, `/claim trustall`, and `/claim untrustall` handlers. `NexusClaimAccess` enforces live `DELETE_CLAIMS` or `MANAGE_PERMISSIONS` as appropriate. All result branches, legacy permission nodes, and locale placeholders are tested. `/claimoverride` revealed two existing missing English locale keys; both are now declared in `lang/en_US.yml`. The claim-wide permission handlers use a supported string-to-enum bridge; tab suggestions and exact ACF invalid-argument feedback still need Paper 26.2 parity verification. These handlers are not registered in production.
- `NexusClaimWriteCommands` provides inactive `/claim description`, `/claim addflag`, and `/claim removeflag` handlers. Each checks the shared `NexusClaimAccess` at invocation time (MANAGE_CLAIMS for description; MANAGE_FLAGS for flags) before mutation, maps all legacy action results and localization placeholders, and keeps the description a required greedy argument. Nexus 26.2 does not provide an automatic `Flag` enum argument resolver, so the flag handlers use a supported `String` argument and case-insensitive finite-enum parsing; invalid flags currently receive the generic localized error rather than ACF's enum-parser feedback. Typed flag-name localization uses `ClaimInfoLocalizedValues` without changing the established locale scanner baseline. Add tab suggestions and confirm invalid-argument/greedy-parser parity on Paper 26.2 before the single cutover.
- `NexusClaimAccess` is an explicit, non-Koin shared location and authorization adapter for claim commands. It resolves player position/world per invocation, checks override privilege before fetching a claim, denies storage-error results without mutation, and delegates permission decisions to `ClaimManagementAuthorizer` without caching them. `NexusClaimMetadataCommands` adds inactive `/claim info` and `/claim rename` handlers, retaining distinct read-only info permissions and management authorization for rename. Both match legacy localization and result mapping; tests exercise scanner metadata, info output, and authorization/rename failure paths. Neither command is registered until the single cutover.
- `NexusClaimPaginationCommands` adds inactive ACF-free `/claimlist [page]` and `/claim partitions [page]` handlers. Both use the legacy `pageBounds()` and ten-item page size; their permission nodes, player-only gates, claim/partition lookups, language keys, and invalid-page behavior remain unchanged. Tests verify scanner metadata and pagination behavior. Do not register these alongside ACF until the single command cutover.
- Shared `GuildGettingStartedNavigation` no longer implements `KoinComponent`; it receives the same explicit `GuildGettingStartedNavigationDependencies` for Java and Bedrock onboarding. Both composition roots now bind providers: legacy `coreModule()` resolves them from Koin only when creating a menu, while `NexusCoreServicesBindings` resolves them through Nexus. Existing permission-revocation, server-tick response, and rendering tests run without starting Koin; a separate factory test verifies Koin-free Java/Bedrock construction. The shared Bedrock base still retains compatibility delegates for other forms.
- The new Nexus context is **not called** by `LumaGuilds.onEnable` yet. Existing Koin and ACF remain authoritative until cutover; no dual live containers.

Remaining migration work is eliminating ACF command handlers after command parity, wiring a single Nexus-owned startup/shutdown lifecycle, and proving integrations at activation. Consumer constructor migration is complete in source; no startup cutover is authorized by that source audit alone. Most legacy DI singleton groups now have inert Nexus registrations, including optional Apollo, Discord and web services. Runtime acceptance must still prove that the full graph resolves uniquely, payment semantics are unchanged, commands preserve ACF behavior, and scheduler/DB lifecycles close exactly once.

## Concurrency audit — November 18, 2026

The final framework cutover must use the **single NexusContext scope and virtual-thread dispatcher** for LumaGuilds-owned coroutine and blocking-I/O work. Do not switch Bukkit main-thread gameplay operations to virtual threads.

- **Already adapted:** `AsyncTaskService` uses an injected scope/dispatcher; `PlayerMetadataServiceVault` uses an injected dispatcher for its asynchronous Vault metadata queries. The new Nexus bootstrap supplies `context.scope` and `context.dispatchers.virtualThread` and cancels/terminates them on close.
- **Still legacy-owned:** `LumaGuilds.kt` initializes `CoroutineScope(Dispatchers.IO + SupervisorJob())`; `di/Modules.kt` creates `Executors.newVirtualThreadPerTaskExecutor()`, converts it to a coroutine dispatcher, and closes that executor on disable.
- **Managed progression XP flush (implemented):** `ProgressionEventListener` receives an injected parent `CoroutineScope` and creates only a child flush job on the injected virtual dispatcher. `shutdown()` cancels/joins that job, force-drains player counters, then flushes pending guild XP; it never cancels the parent scope. The transitional Koin binding supplies `pluginScope`, and the inert Nexus listener factory supplies `NexusContext.scope` with its managed virtual-thread dispatcher. Tests cover flush after parent cancellation and leaving the parent active after explicit shutdown.
- **Managed claim-role cache cleanup (implemented):** `GuildRolePermissionResolverBukkit` no longer allocates `newSingleThreadScheduledExecutor()`. The legacy Koin and future Nexus bindings both inject their parent scope and virtual-thread dispatcher; an hourly child job preserves the 10,000-entry cache cap. `shutdown()` cancels/joins only its child job (bounded to one second) and clears caches, leaving unrelated jobs alive.
- **Managed Bedrock form state cleanup (implemented):** `FormStateManager` no longer allocates a scheduled executor at class init. `start(ownerScope, cleanupDispatcher)` launches one idempotent child job (10-minute initial delay and cadence; 30-minute default expiry; lazy expiry on restore still applies before start). Transitional Koin `onEnable` starts it with `pluginScope` and `named("VirtualDispatcher")` right after `startKoin`; `shutdown()` cancels/joins only that child (bounded to five seconds), clears state, and permits restart. At cutover, call `start(context.scope, context.dispatchers.virtualThread)` from the Nexus lifecycle instead.
- **Bukkit-owned async schedules:** `GuildLeaderboardProjectionService`, `EnthusiaStaffStrikeFeed`, administrative commands and some bootstrap tasks use Bukkit async scheduling. Review one by one: keep Bukkit operations on their required server threads, and migrate only safe blocking I/O to Nexus virtual threads.
- **Release gate:** tests must confirm no lost XP, no orphan worker after disable, no double-close of storage/executors, no async Bukkit entity/world mutation, and no database writes beyond configured MariaDB pool capacity.
