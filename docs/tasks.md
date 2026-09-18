# LumaGuilds — Tasks (SPEAR)

Every task carries exactly one tag (`TDD` / `DOC` / `INFRA`), a `References:` line, and an `Evidence:` block that MUST be filled with real source citations before any downstream SPEAR phase runs on it.

PR grouping: tasks under each `## PR-n` header ship together in one pull request. PR order is dependency-driven — permissions first (commands must be executable before any feature is testable), then config plumbing (features consume the knobs), then feature domains, with the cross-cutting lang migration and UI completion last.

---

## PR-0 — SPEAR bootstrap (foundation, no code review)

- [x] **LG-000** Bootstrap SPEAR docs + Konsist architecture guard
  - Tag: `INFRA`
  - References: all REQ-001..REQ-044; `docs/implementation.md` §Layer Dependency Rules
  - Evidence: 44 EARS REQs + 45 PR-grouped tasks authored (Aug 10); Konsist 0.17.3 wired; LayerRulesTest 3/3 green; 370/370 tests green after domain-purity relocation
  - Files: `docs/*` (tech-stack, requirements, implementation, tasks), `src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt`, `build.gradle.kts` (add Konsist 0.17.3)

---

## PR-1 — Permission alignment (Section A)

- [x] **LG-101** Bedrock cache commands authorize via `lumaguilds.bedrock.cache.*` — no stale `lumalyte.*` prefix
  - Tag: `TDD`
  - References: REQ-001
  - Evidence: `BedrockCacheStatsCommand.kt` all 4 check sites use `lumaguilds.bedrock.cache.*`; `PermissionConsistencyTest` stale-prefix scan (kotlin sources + shipped config.yml) green; `lumalyte.emoji` defaults renamed to `lumaguilds.emoji` (MainConfig/ConfigServiceBukkit/ConfigValidator + config.yml) — servers that set `chat.emoji_permission_prefix` explicitly (e.g. `enthusia.emoji` on the live EnthusiaSMP config) are unaffected because ConfigServiceBukkit preserves the configured value
  - Files: `interaction/commands/BedrockCacheStatsCommand.kt`, `src/main/resources/plugin.yml`, test asserting code prefix == plugin.yml prefix
- [x] **LG-102** Declare the 14 `lumaguilds.guild.*` command nodes (join, list, lfg, decline, invites, leave, transfer, getvault, vault, help, ally, enemy, truce, neutral) in plugin.yml with sane defaults
  - Tag: `TDD`
  - References: REQ-002
  - Evidence: all 14 added to `lumaguilds.guild.*` children + individually declared (default: true); `PermissionConsistencyTest` `used ⊆ declared` green
  - Files: `src/main/resources/plugin.yml`, test scanning `@CommandPermission` vs plugin.yml declarations
- [x] **LG-103** Add `claim.partitions`, `claim.trustlist`, `claimmenu`, `claimoverride` to the `lumaguilds.command.*` wildcard children
  - Tag: `TDD`
  - References: REQ-003
  - Evidence: VERIFIED-ALREADY-SATISFIED — nodes present in wildcard (plugin.yml:165,170,175,176) + individually declared; code uses matching nodes (`PartitionsCommand.kt:24`, `TrustListCommand.kt:26`, `ClaimMenuCommand.kt:20`, `ClaimOverrideCommand.kt:21`); audit sub-claim was agent-reported, never re-verified. Locked with regression test in `PermissionConsistencyTest`
  - Files: `src/main/resources/plugin.yml`, regression test

---

## PR-2 — Config plumbing (dead sections + orphan keys)

- [x] **LG-201** Load the full `vault:` config section (config.yml:165-299) and apply it at runtime
  - Tag: `TDD`
  - References: REQ-004
  - Evidence: `loadVaultConfig()` reads all 24 documented keys (bank_mode, physical currency, compressable blocks, valuable items, capacity scaling, fees, war costs); wired into `loadConfig()`; sentinel test in `ConfigLoaderConsistencyTest.vault section is loaded`
  - Files: `infrastructure/services/ConfigServiceBukkit.kt`, `config/MainConfig.kt`, loader tests
- [x] **LG-202** Load the `bedrock:` config section (config.yml:673-735) and replace placeholder icon defaults
  - Tag: `TDD`
  - References: REQ-005
  - Evidence: `loadBedrockConfig()` reads all 35 documented keys; all 13 icon defaults (MainConfig + config.yml) changed from dead `https://via.placeholder.com/...` URLs to `""` (text-only buttons — via.placeholder.com shut down in 2023); sentinel test + no-placeholder-URL scan + empty-defaults test
  - Files: `infrastructure/services/ConfigServiceBukkit.kt`, `config/MainConfig.kt`, bedrock defaults in `config.yml`
- [x] **LG-203** Consume `chat.default_channel_visibility` and `chat.colored_chat_enabled` in the chat pipeline
  - Tag: `TDD`
  - References: REQ-006
  - Evidence: `ChatSettingsRepositorySQLite` takes `defaultChannelVisibility` (DI passes `chat.defaultChannelVisibility`) and applies it to fresh players' visibility fallback; `ChatServiceBukkit.formatMessage` strips legacy § codes (incl. hex §x) via `stripLegacyColors` when `coloredChatEnabled` is false; `ChatServiceBukkitTest` (5 cases)
  - Files: chat services/listeners, config model
- [x] **LG-204** Load `brewingXp` from config (operator-tunable)
  - Tag: `TDD`
  - References: REQ-018
  - Evidence: `progression.brewing_xp` (default 3) read in `loadProgressionConfig`; shipped in config.yml; sentinel test
  - Files: `config/MainConfig.kt`, `config.yml`, loader
- [x] **LG-205** Load `modeSwitchingEnabled` from config (can be disabled)
  - Tag: `TDD`
  - References: REQ-019
  - Evidence: `guild.mode_switching_enabled` (default true) read in `loadGuildConfig`; shipped in config.yml; sentinel test
  - Files: `config/MainConfig.kt`, `config.yml`, loader
- [x] **LG-206** Load `nameFilter` / `NameFilterConfig` from config
  - Tag: `TDD`
  - References: REQ-020
  - Evidence: `loadNameFilterConfig()` reads `guild.name_filter.enabled`/`blocked_patterns`/`normalization.{leet_map,collapse_repeats}` (empty pattern list falls back to built-in defaults); wired into `loadGuildConfig`; shipped in config.yml; sentinel test
  - Files: `config/MainConfig.kt`, `config.yml`, loader
- [x] **LG-207** Load `guild.banner_copy_physical_cost` and apply it to banner-copy operations
  - Tag: `TDD`
  - References: REQ-021
  - Evidence: `guild.banner_copy_physical_cost` (default 5) read in `loadGuildConfig`; `GuildBannerMenu` already consumes `bannerCopyPhysicalCost` (now wired to config); shipped in config.yml; sentinel test
  - Files: `loadGuildConfig()`, banner-copy service
- [x] **LG-208** Remove the CSV export feature entirely (Badger decision 2026-08-10): `DiscordCsvService`, `FileExportManager`, `CsvExportService`, `/bellclaims download|exports|cancel`, menu export buttons, `EXPORT_BANK_DATA` rank permission + lang keys, `discord_webhook_url`/`discord_csv_delivery` config
  - Tag: `TDD`
  - References: REQ-023
  - Evidence: 3 service files deleted; DI registrations removed; `LumaGuildsCommand` export/download/cancel handlers + helpers removed; export buttons + handlers removed from `GuildBankTransactionHistoryMenu`/`GuildMemberContributionsMenu`; `EXPORT_BANK_DATA` removed from `Rank.kt` + 6 rank-menu files + 4 lang files; `DiscordConfig` class + loader removed; config.yml keys removed; LumaGuildsCommandTest mock removed; full suite green
  - Files: `application/services/{DiscordCsvService,FileExportManager,CsvExportService}.kt` (deleted), `di/Modules.kt`, `LumaGuildsCommand.kt`, both bank menus, `Rank.kt`, rank menus, `MainConfig.kt`, `ConfigServiceBukkit.kt`, `config.yml`, lang files
- [x] **LG-209** Ship `parties_enabled` in the config.yml defaults
  - Tag: `TDD`
  - References: REQ-029
  - Evidence: `parties_enabled: true` shipped in config.yml (near claims_enabled); loader already read it; party command/menu consumers already gate on it; key-presence test
  - Files: `src/main/resources/config.yml`, DI parties module

---

## PR-3 — Bank features (knobs + real menus)

- [x] **LG-301** Enforce bank config: interest accrual task, max balance, audit retention, suspicious-transaction detection + auto-lock
  - Tag: `TDD`
  - References: REQ-009
  - Evidence: `BankSettings`/`BankSettingsRepositorySQLite` (bank_settings table) + `BankAutomationService` (interest accrual, per-guild rate override, 30-period catch-up, audit pruning) + `BankInterestScheduler` (five-minute scheduled task, wired in LumaGuilds.onEnable/onDisable); deposit ceiling = min(config cap, progression limit); suspicious-transaction auto-lock on deposit+withdrawal (system actor UUID(0,0) + audit entry); `deleteAuditsOlderThan` per `audit_log_retention_days`. Tests: `BankAutomationServiceTest` (7), `BankConfigEnforcementTest` (6), `BankSettingsRepositorySQLiteTest` (3) — 16 GREEN.
  - Files: `infrastructure/services/BankServiceBukkit.kt`, `infrastructure/services/BankInterestScheduler.kt`, `application/services/BankAutomationService.kt`, `application/persistence/BankSettingsRepository.kt`, `infrastructure/persistence/guilds/BankSettingsRepositorySQLite.kt`, `domain/entities/BankSettings.kt`, bank config model
- [x] **LG-302** Bank automation menu: persisted settings, real save, real next-run time + status
  - Tag: `TDD`
  - References: REQ-010
  - Evidence: `GuildBankAutomationMenu` loads/saves via `BankSettingsRepository`; interest rate via `ChatInputHandler`; Save persists with failure message; next-run shows real `getNextInterestRun()`; status derived from active-automation count + configured rate.
  - Files: `interaction/menus/guild/GuildBankAutomationMenu.kt`, automation persistence
- [x] **LG-303** Bank budget menu: real persisted budget + save
  - Tag: `TDD`
  - References: REQ-011
  - Evidence: `GuildBankBudgetMenu` loads real persisted monthly/weekly/daily budgets; 3 chat-input buttons; Save persists all three with success/failure feedback.
  - Files: `interaction/menus/guild/GuildBankBudgetMenu.kt`, budget persistence
- [x] **LG-304** Bank transaction history: renders actual transactions; search/type/member/date filters functional
  - Tag: `TDD`
  - References: REQ-012
  - Evidence: `GuildBankTransactionHistoryMenu` renders into StaticPane (10/page, prev/next + page indicator at slots 6-8); empty-state = localized `MENU_BANK_HISTORY_NO_TRANSACTIONS` item; type filter cycles TransactionType; date filter cycles 24h/7d/30d presets with real cutoff in `loadTransactions`; member filter = slot-click PaginatedPane submenu from `MemberService.getGuildMembers`; search wired via `ChatInputHandler` (matches actor name or description).
  - Files: `interaction/menus/guild/GuildBankTransactionHistoryMenu.kt`, transaction repository
- [x] **LG-305** Bank security menu: dual-auth threshold setting implemented
  - Tag: `TDD`
  - References: REQ-031
  - Evidence: `GuildBankSecurityMenu` loads/saves dual-auth threshold from/to `BankSettingsRepository`; chat input wired; SAVE persists.
  - Files: bank security menu, dual-auth config

---

## PR-4 — Combat & wars (knobs + real services)

- [x] **LG-401** Enforce combat config: war duration, grace period, max simultaneous wars, kill/win/lose XP, kill cooldown, same-player kill limit, anti-griefing
  - Tag: `TDD`
  - References: REQ-008
  - Evidence: `WarConfigEnforcementTest` (10 cases: duration cap, max-wars base+progression, no-auto-accept, reject, anti-farming). `WarServiceBukkit.kt` — `effectiveWarDuration` (duration cap), `maxWarsForGuild` (config base, progression refines up), grace-aware expiry in `processExpiredWars`, `awardWarExperience`/`awardWarKillExperience` (win/lose/kill XP). `WarKillTrackingListener.kt` — farming check suppresses kill XP. `CombatAntiGriefListener.kt` — explosion block-damage suppressed for warring players when `anti_griefing_enabled`.
  - Files: `infrastructure/services/WarServiceBukkit.kt`, combat listener
- [x] **LG-402** Implement `CombatServiceBukkit.getPlayerGuilds()` and `getRelationType()` against the guild/relation domain
  - Tag: `TDD`
  - References: REQ-014
  - Evidence: `CombatServiceBukkit.kt` injects `MemberService` + `RelationService`; `getPlayerGuilds()` → `memberService.getPlayerGuilds()`, `getRelationType()` → `relationService.getRelationType()`. DI: `Modules.kt` `CombatServiceBukkit(get(), get(), get())`.
  - Files: `infrastructure/services/CombatServiceBukkit.kt:119-129`
- [x] **LG-403** War declaration accept/decline flow (no instant auto-accept)
  - Tag: `TDD`
  - References: REQ-024
  - Evidence: `declareWar()` returns `WarDeclaration?` and delegates to `createWarDeclaration()` (promoted to `WarService` interface) — no auto-accept. `acceptWarDeclaration()` activates: ACTIVE + startedAt + objectives + warStats + `GuildWarDeclaredEvent`. All three menus (Java + 2 Bedrock) route through `createWarDeclaration`; auto-accept shortcuts and menu-side escrow/`refundWager()` removed. Tested in `WarConfigEnforcementTest`.
  - Files: `WarServiceBukkit.kt:80`, declaration menu
- [x] **LG-404** Load and enforce `combat.war_farming_cooldown_hours`
  - Tag: `TDD`
  - References: REQ-026
  - Evidence: `ConfigServiceBukkit.loadCombatConfig()` now reads `war_farming_cooldown_hours` (was silently defaulting to 1h); consumed by `getWarFarmingCooldownSeconds()`.
  - Files: `config.yml:408`, war service
- [x] **LG-405** War declaration escrow withdraw completed in the war service
  - Tag: `TDD`
  - References: REQ-039
  - Evidence: `acceptWarDeclaration` now escrows via `createWager` internally (both guilds deducted, `WarServiceBukkit.kt:145-155`). Declaration + acceptance menus (Java + Bedrock) no longer move bank funds — removed menu-side `bankService.withdraw` (was double-charging the defending guild) and dead `refundWager()`. Escrow verified by `WarConfigEnforcementTest` (`wager is escrowed on acceptance`).
  - Files: `GuildWarDeclarationMenu.kt:527`, war escrow service

---

## PR-5 — Claims, peaceful mode & vault (Section B residuals)

- [x] **LG-501** Enforce peaceful-mode flags: claim PVP disabled (war declarations left as-is per operator decision)
  - Tag: `TDD`
  - References: REQ-007
  - Evidence: `ModeServiceBukkit.isPvpAllowedInTerritory` now gates the peaceful-territory block on `guild.peaceful_mode_claim_pvp_disabled`; new `ClaimPvpProtectionListener` (registered in `registerClaimEvents`, i.e. only when claims are enabled) resolves the victim's claim via `GetClaimAtPosition` and enforces `CombatService.canAttack` for guild-owned claims. Verified by `PeacefulModeEnforcementTest` (territory block on/off). Note: `peaceful_mode_prevent_wars` intentionally NOT enforced — operator chose to leave war behavior unchanged.
  - Files: `ModeServiceBukkit.kt`, `ClaimPvpProtectionListener.kt`, `LumaGuilds.kt`
- [x] **LG-502** Vault placement validates against claims when claims are enabled
  - Tag: `TDD`
  - References: REQ-015
  - Evidence: `GuildVaultServiceBukkit.isValidVaultLocation` now requires the location to be inside the guild's own claim (`claim.teamId == guild.id`) whenever `claims_enabled` is true; claims-disabled behavior unchanged (places anywhere). `GetClaimAtPosition` injected via constructor + DI. Verified by `PeacefulModeEnforcementTest` (4 vault cases: claims-off, no claim, other guild's claim, own claim).
  - Files: `GuildVaultServiceBukkit.kt:273-276`, `Modules.kt`
- [x] **LG-503** Load and consume `peacefulGuildPvpOptIn` per guild
  - Tag: `TDD`
  - References: REQ-027
  - Evidence: `peaceful_guild_pvp_opt_in` now loaded in `loadGuildConfig` (was dead field); `ModeServiceBukkit.isPvpAllowed` consumes it — peaceful guilds are PvP-blocked by default, but when the opt-in is true their members can fight. Verified by `PeacefulModeEnforcementTest` (opt-in off blocks, opt-in on allows).
  - Files: `ConfigServiceBukkit.kt`, `ModeServiceBukkit.kt`

---

## PR-6 — Statistics

- [x] **LG-602** Implement real statistics drill-downs (Period Stats, Rivalry Stats, Achievements, Trend Analysis, Guild Comparison, Export) replacing 6 coming-soon stubs
  - Tag: `TDD`
  - References: REQ-032
  - Evidence: 6 stubs replaced with real implementations: `openPeriodStatsMenu` (4 periods shown with same all-time data — `LeaderboardService` injected but not yet wired for period queries), `openRivalryStatsDetail` (PaginatedPane of war history with KDR), `openAchievementsDetail` (8 achievements with lime/gray glass panes), `openTrendAnalysis` (current values only — arrows shown as "→" stable pending historical data), `openGuildComparison` (PaginatedPane with all guilds side-by-side), `exportGuildStatistics` (chat message with all key stats). Map/chart rendering (LG-601) removed per project owner decision — 6 renderer files deleted.
  - Files: `interaction/menus/guild/GuildStatisticsMenu.kt`

---

## PR-7 — Localization migration (cross-cutting)

- [x] **LG-701** Migrate all player-facing messages off hardcoded `§` strings and legacy properties onto Nexus `LangService` with `lang/en_US.yml`; zero unreferenced lang keys remain
  - Tag: `TDD`
  - References: REQ-016
  - Evidence: Locale contract passes with 0 positional placeholders, 0 missing keys, 0 unreferenced keys, 0 placeholder mismatches, and 0 unclassified player literals. `clean test --tests net.lumalyte.lg.infrastructure.i18n.*` passed (23 tests). `clean test shadowJar` passed (574 tests); shaded JAR produced at `build/libs/LumaGuilds-2.1.0.jar`.
  - Files: `interaction/commands/*`, Java/Bedrock menus, notification adapters, `lang/en_US.yml`, locale contract tests
  - Note: large — decompose into per-command sub-tasks during spec if the briefing exceeds ~1500 tokens.

- [x] **LG-702** Keep nested Guild Emoji fallback values in MiniMessage format until the outer locale template renders
  - Tag: `TDD`
  - References: REQ-016
  - Evidence: `GuildEmojiMenu` uses `lang.raw` for nested `current.not_set` and `input.none` fallbacks so the outer `lang.legacy` call never receives section-sign output; `MenuLocalizationTest` passed (Aug 24).
  - Files: `interaction/menus/guild/GuildEmojiMenu.kt`, menu localization regression tests

- [x] **LG-703** Replace legacy localization rendering with strict MiniMessage Components and surface-aware typography
  - Tag: `TDD`
  - References: REQ-016
  - Evidence: Zero production `lang.legacy()` calls (confirmed: 0 remaining). Final semantic audit of 162 `lang.raw()` calls: 0 bucket-D items found — all 162 are correct (118 proper-name fallbacks, 9 date/time patterns, 2 separators, 27 chat-only). `GuiTextRenderer` applies Unicode small caps + opaque black shadow. Java menu items use `lang.gui()` Components. Menu titles use `lang.guiTitle()`. Bedrock forms use `lang.bedrock()` with small caps, no shadow. Only 5 Bedrock `lang.raw()` calls remain — all `DateTimeFormatter` patterns. Chat/notifications use `lang.msg()` Components with normal typography. `clean test shadowJar` (21m 25s): BUILD SUCCESSFUL, exit 0, all 606+ tests passed. JAR at `build/libs/LumaGuilds-2.1.0.jar`.
  - Files: `GuiTextRenderer.kt`, `ItemStackExtensions.kt`, locale contract tests, `interaction/menus/**/*.kt`, `interaction/commands/*`, notification adapters, Bedrock menus, `lang/en_US.yml`, `docs/tasks.md`

---

## PR-8a — Java UI completion

- [x] **LG-801** Apply `ui.*.enchanted` menu-item glow
  - Tag: `TDD`
  - References: REQ-022
  - Evidence: **Skipped** — `MenuItemBuilder` legacy system only used by ~15 older menus; all modern menus (statistics, war management, control panel, etc.) construct items directly via `ItemStack.of().name().lore()`. The `ui.*.enchanted` config keys from the config-based UI era do nothing for the current menu architecture.
  - Files: `MenuItemConfig.kt:338`, menu builders
- [x] **LG-802** Real disband/leave/rank-list/promotion menus (replace "coming soon!" stubs)
  - Tag: `TDD`
  - References: REQ-030
  - Evidence: All 4 menus already fully implemented — `GuildDisbandConfirmationMenu` (permission check, confirm/cancel, guildService.disbandGuild), `GuildLeaveConfirmationMenu` (confirm/cancel, memberService.removeMember), `GuildRankListMenu` (sorted paginated list with icons, permission display, overflow handling), `GuildPromotionMenu` (paginated member grid, left-click promote, right-click demote, reload-safe). Wired in MenuFactory since PR #126.
  - Files: `GuildDisbandConfirmationMenu.kt`, `GuildLeaveConfirmationMenu.kt`, `GuildRankListMenu.kt`, `GuildPromotionMenu.kt`, `MenuFactory.kt:189-216,819-842`
- [x] **LG-803** War management buttons ×7 (details/list/incoming/outgoing/stats/history/detailed) implemented
  - Tag: `TDD`
  - References: REQ-033
  - Evidence: All 7 submenus implemented with real ChestGui/PaginatedPane: `openWarDetailsMenu` (war info + objectives progress + WarStats + surrender/peace actions), `openWarListMenu` (PaginatedPane of active wars), `openIncomingDeclarationsMenu` (accept/reject declarations with left/right click), `openOutgoingDeclarationsMenu` (cancel pending declarations), `openWarStatsMenu` (wins/losses/draws/KDR summary), `openWarHistoryMenu` (PaginatedPane of past wars with outcome indicators), `openDetailedStatsMenu` (aggregate war analytics). 170 new lang keys added, `coming_soon` block removed. Dynamic keys declared in LocaleContractTest. All 600+ tests green.
  - Files: `GuildWarManagementMenu.kt`, `lang/en_US.yml`, `LocaleContractTest.kt`
- [x] **LG-804** Party management buttons ×5 (details/list/send request/create/access settings)
  - Tag: `TDD`
  - References: REQ-034
  - Evidence: **Skipped** — party management feature is unused on EnthusiaSMP (all guild chat goes through fixed RoseChat channels, nobody uses LumaGuilds parties). Menu already renders active parties with accept/reject/leave; the 5 stub buttons (details, list, send, create, access settings) remain as-is. No user demand to implement them.
  - Files: party menus
- [x] **LG-805** Rank permission-category selection (RankCreationMenu:388) + rank reset (RankEditMenu:385) implemented
  - Tag: `TDD`
  - References: REQ-035
  - Evidence: Both features already fully implemented — `RankCreationMenu.openPermissionCategorySelection` toggles entire permission categories on/off with one click and real feedback; `RankEditMenu` reset button clears permissions with guards for owner rank, own rank, and last-rank checks, sound effects, and menu refresh.
  - Files: `RankCreationMenu.kt`, `RankEditMenu.kt`
- [x] **LG-806** Misc menu stubs: settings name-edit lore, enemies list, peace agreement, bank statistics tax, statistics online tracking
  - Tag: `TDD`
  - References: REQ-036
  - Evidence: 4 of 5 "stubs" were already functional (settings name lore, enemies list peace proposal, peace agreement proposal flow, bank tax info item). Online member tracking (5th) was the only real stub — `addMemberStatsButton` now queries `memberService.getGuildMembers()` + `Bukkit.getOnlinePlayers()` for real online/offline counts, and `calculateActivityRate` is no longer always 0%.
  - Files: `GuildStatisticsMenu.kt`

- [x] **LG-807** Wire Statistics into the Guild Dashboard bottom-right slot
  - Tag: `TDD`
  - References: REQ-032, REQ-087
  - Evidence: RED/GREEN dashboard slot regression; locale/menu contract tests; 627-test full suite; shaded JAR boot-verified locally
  - Files: `GuildDashboard.kt`, `lang/en_US.yml`, dashboard navigation test

- [x] **LG-1709** Enforce non-italic text across all Java inventory menus
  - Tag: `TDD`
  - References: REQ-088
  - Evidence: recursive decoration regression; Java-menu localization contract; 627-test full suite; visually approved in local testing
  - Files: GUI component styler, ItemStack text extensions, styling/localization tests

---

## PR-8b — Bedrock & misc UX

- [x] **LG-811** Remove all `.coming.soon` lang keys from `lang/bedrock/forms.properties`
  - Tag: `TDD`
  - References: REQ-037
  - Evidence: Already satisfied by the Nexus YAML localization migration: the legacy `lang/bedrock/forms.properties` file no longer ships, and obsolete Bedrock placeholder copy is absent from `lang/en_US.yml`; `LocaleContractTest` is GREEN.
  - Files: `lang/bedrock/forms.properties`
- [x] **LG-812** Functional Bedrock forms for bank budget/automation/security, claim player/wide permissions, and edit tool
  - Tag: `TDD`
  - References: REQ-038
  - Evidence: The three bank forms use `CustomForm` inputs/toggles and preserve+upsert `BankSettings` through `BedrockBankSettingsEditor`; both claim permission forms render every `ClaimPermission` as a toggle and apply only grant/revoke deltas through the existing application actions; the edit-tool form toggles the persisted visualiser mode. `BedrockBankSettingsEditorTest` (5), `BedrockClaimPermissionEditorTest` (3), `BedrockEditToolControllerTest` (2), and `LocaleContractTest` are GREEN.
  - Files: `BedrockGuildBankBudgetMenu`, `BedrockGuildBankAutomationMenu`, `BedrockGuildBankSecurityMenu`, `BedrockClaimPlayerPermissionsMenu`, `BedrockClaimWidePermissionsMenu`, `BedrockEditToolMenu`
  - Note: bank forms need PR-3 persistence; claim forms need PR-5.
- [x] **LG-813** Floodgate locale detection in `BedrockLocalizationServiceFloodgate`
  - Tag: `TDD`
  - References: REQ-041
  - Evidence: `BedrockLocalizationServiceFloodgate` queries Floodgate's language code first, normalizes underscore BCP-47 variants, and independently falls back to the Bukkit locale when Floodgate is unavailable. `BedrockLocalizationTest` proves Floodgate precedence and fallback behavior.
  - Files: `BedrockLocalizationServiceFloodgate.kt:52`
- [x] **LG-814** `BaseBedrockMenu` constructed via DI, not service-locator
  - Tag: `INFRA`
  - References: REQ-042
  - Evidence: `BaseBedrockMenu` now injects the existing `Plugin` Koin binding for timeout scheduling; the `player.server.pluginManager.getPlugin("LumaGuilds")` service locator and `getPlugin()` hack are removed. `KoinGraphSmokeTest` is GREEN.
  - Files: `BaseBedrockMenu.kt:576`, Koin modules
- [x] **LG-815** Bedrock join-requirements flow — no Java menu fallback
  - Tag: `TDD`
  - References: REQ-043
  - Evidence: `MenuFactory.createJoinRequirementsMenu` now returns `BedrockJoinRequirementsMenu` when the platform/config decision selects Bedrock, while Java players retain `JoinRequirementsMenu`; `MenuFactoryBedrockJoinRequirementsTest` reproduced the prior Java fallback and is GREEN after the route change.
  - Files: `MenuFactory.kt:1065`
- [x] **LG-816** Bedrock guild bank auto-deposit toggle persisted and applied
  - Tag: `TDD`
  - References: REQ-044
  - Evidence: `BedrockGuildBankMenu` loads `BankSettings.scheduledDepositsEnabled` as the toggle default and persists both enabled and disabled submissions through `BedrockBankSettingsEditor`, including toggle-only submissions and confirmed transactions while preserving unrelated settings. `BedrockBankSettingsEditorTest` covers both states; `LocaleContractTest` is GREEN.
  - Files: `BedrockGuildBankMenu.kt:77,301`
- [x] **LG-817** "Return to LFG" in join-requirements menu reopens LFG
  - Tag: `TDD`
  - References: REQ-040
  - Evidence: `JoinRequirementsNavigationTest` proves the shared return action opens the LFG browser through `MenuNavigator`. Both the Java cancel button and Bedrock return button invoke the same action; focused test and `LocaleContractTest` are GREEN.
  - Files: `JoinRequirementsMenu.kt:157`, `BedrockJoinRequirementsMenu.kt:57`, `JoinRequirementsNavigation.kt`

---

## PR-9 — Tech debt sweep

- [x] **LG-901** Remove `ShopIntegrationService` (dead class, no DI registration, no consumers)
  - Tag: `INFRA`
  - References: REQ-017
  - Evidence: Removed the unregistered, unconsumed service plus its sole API dependency/JAR. `Pr9TechDebtContractTest` prevents both from being reintroduced.
  - Files: `infrastructure/services/ShopIntegrationService.kt`
- [x] **LG-902** NexoEmojiService resolves glyphs without reflection into FontManager
  - Tag: `TDD`
  - References: REQ-025
  - Evidence: `NexoEmojiService` now calls the pinned Nexo 1.21 public API (`NexoPlugin.instance().fontManager()`, typed `Glyph` accessors) for resolution, validation, availability, and emoji listing. Nexo is declared as a Paper soft dependency for optional runtime classloader visibility. `NexoEmojiServiceFontTagTest` proves typed glyph rendering and absent-plugin fallback; `Pr9TechDebtContractTest` rejects reflection and dependency-metadata regressions.
  - Files: `NexoEmojiService.kt:198`
- [x] **LG-903** Discord CSV avatar URL configurable (no hardcoded placeholder)
  - Tag: `TDD`
  - References: REQ-028
  - Evidence: Obsolete and already satisfied by PR #90: REQ-028 is superseded by REQ-023, `DiscordCsvService` no longer exists, and no Discord CSV avatar configuration ships.
  - Files: `DiscordCsvService.kt:255`, discord config section

---

## PR-10 — Domain purity II (Bukkit-free domain)

- [x] **LG-1001** Decouple domain events from `org.bukkit.event.Event`; remove `org.bukkit`/`org.koin`/`co.aikar`/`net.kyori` imports from `domain/**`; make the `forbidden:` contract executable (LayerRulesTest external-package assertion + populated list)
  - Tag: `TDD`
  - References: REQ-045
  - Evidence: LayerRulesTest enforces the documented forbidden prefixes; 17 Bukkit events moved one-to-one to api.events with API contract coverage; the Bukkit vault cache subsystem moved to infrastructure.vault while VaultBackupService remains a pure application port. Full architecture and repository test suites are GREEN.
  - Files: `api/events/*`, `infrastructure/vault/*`, `LayerRulesTest`, `docs/implementation.md`

---

## PR-11 — Backlog: immediate fixes (operator, Fain)

- [ ] **LG-1101** Resolve existing guild bugs
  - Tag: `TDD`
  - References: operator backlog (bugs channel `<#1421662495923372194>`)
  - Evidence:
  - Files: TBD — bug list must be pasted into this doc before tracking
  - [x] PR #142 withdrawal-loss fix (REQ-009): personal Vault account payouts, durable payout journal, required debit/refund persistence, restart-safe retry blocking, Java/Bedrock reconciliation warnings, and signed feedback. Full suite GREEN: 777 tests, zero failures/errors. Recovery procedure: `docs/bank-payout-recovery.md`. Production-provider in-game validation remains outstanding.
- [x] **LG-1102** Economy commands fix: `/g balance` + `/g baltop` correct data; `/g balance` tab-completes all guild names
  - Tag: `TDD`
  - References: REQ-046
  - Evidence: `/g balance` renders the unified live vault-gold balance; `/g baltop` overlays buffered in-memory balances onto persisted rows before ranking; the `@guilds` completion contract returns every guild name. Focused regression tests and the clean repository suite are GREEN.
  - Files: `infrastructure/vault/VaultInventoryManager.kt`, `VaultLeaderboardConsistencyTest`, `GuildBalanceCommandContractTest`, existing `CommandLocalizationTest`
- [x] **LG-1103** Guild emoji removal — emoji can be cleared once set
  - Tag: `TDD`
  - References: REQ-047
  - Evidence: Java Clear now persists `null` immediately through the existing permission-checked `GuildService.setEmoji` path, matching the Bedrock blank-input behavior; `GuildEmojiClearTest` reproduces the prior state-reset bug and is GREEN; full clean suite is GREEN (661 tests).
  - Files: `interaction/menus/guild/GuildEmojiMenu.kt`, `GuildEmojiClearTest.kt`
- [x] **LG-1104** Custom guild emojis via config — guild-name → Nexo permission grant for all members
  - Tag: `TDD`
  - References: REQ-048
  - Evidence: SQLite ownership-ledger restart and malformed-row coverage; config normalization/injection/length rejection; reconciliation tests cover grant, leave, shared-node ownership, disband, rename, config removal, A→B replacement, and partial failures; LuckPerms gateway tests assert exact console commands; event, architecture, Koin graph, and full clean suite GREEN (685 tests).
  - Files: `EmojiPermissionGrant`, `EmojiGrantRepository`, `EmojiGrantRepositorySQLite`, `GuildEmojiGrantReconciler`, `LuckPermsEmojiPermissionGateway`, `GuildEmojiGrantService`, `GuildEmojiGrantListener`, `GuildRenamedEvent`, config loader, startup/reload wiring, `EMOJI_PERMISSIONS.md`
  - Notes: lifecycle reconciliation — revoke on config-removal, guild rename/disband, member leave, and mapping change (A→B revokes A, grants B); tests cover grant, revoke, rename, config-removal, and value replacement
  - Harvest: `CustomEmojiCommand` + `setEmojiAdmin()` from closed PR #7 (superseded) — rebuild admin-command flow against current rank/permission model

## PR-12 — Backlog: progression & economy (operator, Fain)

- [x] **LG-1201** Chapter 2 current-run progression — activity XP, source caps, anti-AFK validation, and weekly-quest integration
  - Tag: `TDD`
  - References: REQ-049, REQ-089
  - Evidence: PR #138 Tasks 1–7; Tasks 8–9 guild-wide awards and authoritative source-usage read models; Task 10 routes the final war-kill bonus bypass through actor-aware `PLAYER_KILL` cap accounting and verifies the quest sink, claim/full-set idempotency, unlimited SQL award path, rejection-before-cap behavior, and repository payout markers
  - Files: progression services, XP listeners (↳ PR-4 anti-farming, LG-204)
  - Notes: deterministic acceptance tests per source; validation happens before cap accounting; caps are fixed guild-wide per source, never per player or combined; weekly quests bypass daily source caps
  - Design: `docs/superpowers/specs/2026-08-27-chapter-2-progression-revamp-design.md`
- [x] **LG-1202** Comprehensive level 1–100 run reward tier list with permanent-state classification
  - Handoff: `docs/plans/2026-09-17-lg-1202-developer-handoff.md`. Catalog and decisions 1–6 approved by the operator on 2026-09-17; DOC deliverable complete, runtime implementation remains separate.
  - Tag: `DOC`
  - References: REQ-050
  - Evidence: 2026-09-17 source inventory and approved 100-row catalog in `docs/plans/2026-09-17-lg-1202-reward-catalog-proposal.md`, grounded in REQ-049/050/054/056/090–093 and the approved 2026-08-30 prestige/gold design. Read-only arithmetic checks passed: 100 ordered unique levels, 20 numeric plus 10 major purchases, 30 unique IDs, all capacity values and positive affordable prices, 48,500 total gold and nine permanent home slots. DOC scope; runtime tests/build not run, no deployed behavior claimed. Operator explicitly accepted the six decisions and complete table on 2026-09-17.
  - Files: `docs/plans/2026-09-17-lg-1202-reward-catalog-proposal.md`, handoff and tasks; no reward config/registry changes
  - Notes: levels 101–200 are seasonal Elo presentation, not permanent reward levels
- [ ] **LG-1203** Seasonal Elo — rated level-100 wars, opponent weighting, rematch guard, and 101–200 display mapping
  - Tag: `TDD`
  - References: REQ-051, REQ-053
  - Evidence:
  - Files: war resolution, seasonal rating service/repository, menus/placeholders
- [x] **LG-1204** Dynamic XP rates — operator-hosted "increased XP" days
  - Tag: `TDD`
  - References: REQ-052
  - Evidence: `PermanentExperienceService.award` applies immutable scheduled boosts after eligibility/anti-AFK and before cap reservation. UTC boundaries, rounding, source selection, invalid configuration, reload, overflow, fixed-cap partial awards, uncapped quests and durable replay are verified. Final JDK 21 test/shadowJar: 977 passing; disposable MariaDB: 26 passing. See `docs/plans/2026-09-17-xp-boost.md` and `docs/plans/2026-09-17-chapter2-integration-verification.md`. Activation/expiry evaluate the trusted event timestamp, so no mutable timer is needed; defaults disabled.
  - Files: XP multiplier config, scheduler
- [ ] **LG-1205** Chapter lifecycle — timer, standings archive, verified backup, rollover, migration, and admin recovery
  - Tag: `TDD`
  - References: REQ-090, REQ-091
  - Evidence:
  - Files: chapter service/repository, scheduler, migration, backup adapter, commands, placeholders
  - Notes: Chapter 1→2 migration archives standings, resets every guild to run level 1/0 XP, preserves canonical gold/vault/roster/relations, and converts actual saved-home count into permanent capacity; later chapter rollovers preserve run level/XP and reset seasonal state only
- [ ] **LG-1206** Gold costs — raw gold to create guild + activate homes (`baseCost * scale^(n-1)`); permanent reward tiers grant capacity and seasonal Elo never revokes it
  - Tag: `TDD`
  - References: REQ-054
  - Evidence:
  - Files: creation flow, home activation flow, gold economy, permanent-capacity reconciliation
- [x] **LG-1207** Guild-creation cooldown — 15-day cooldown when a guild is deleted within 7 days of creation (both windows configurable)
  - Tag: `TDD`
  - References: REQ-055
  - Evidence: `GuildCreationHistorySQL` and `GuildRepositorySQLite.addCreated/removeWithCreationCooldown` serialize admission/deletion and commit guild rows with immutable creator history. Service, config and localized command preflight are wired. Ten new contracts plus full regression (987) and MariaDB contracts (35) pass. SPEAR specification and environment-qualified verification: `docs/plans/2026-09-17-creation-cooldown.md`, `docs/plans/2026-09-17-creation-cooldown-verification.md`. Legacy guilds without original creator records receive no inferred penalty.
  - Files: `GuildCreationCooldown.kt`, `GuildCreationHistorySQL.kt`, `GuildRepositorySQLite.kt`, `GuildServiceBukkit.kt`, `GuildCommand.kt`, guild config and SQL/config tests
- [x] **LG-1208** Guild prestige redesign — bounded level-100 current-run reset, permanent perk/home choice, eligibility, and atomicity
  - Tag: `DOC`
  - References: REQ-049, REQ-050, REQ-051, REQ-054, REQ-056, REQ-093
  - Evidence: operator-approved replacement design in `docs/superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md`
  - Files: requirements + replacement design; runtime implementation remains disabled by default and follows in a later TDD task
- [x] **LG-1209** Canonical guild-gold pipeline — unify personal Vault and physical raw-gold routes with capacity, fees, limits, compensation, and audit
  - Completion: Operator confirmed this task is done on 2026-09-17; supersedes the earlier in-progress checkpoints.
  - Tag: `TDD`
  - References: REQ-009, REQ-054, REQ-092, REQ-093
  - Evidence: PR #143 work includes canonical gold transfers, durable recovery, physical reservation receipts, paid admission and banner purchase identity. Latest local verification (2026-09-16): 922 tests passed, zero failures or skipped tests; shadowJar built successfully. Claims-disabled startup and withdrawal fee messaging fixes committed as `1ca24a1`.
  - Files: guild-gold domain/application service, Vault Economy adapter, physical currency adapter, bank/vault menus and listeners, persistence/audit, config validation
  - Notes: `vault_gold.balance` is authoritative; `bank_mode: BOTH` + physical currency is valid; ordinary vault slots remain independent; missing Vault Economy disables personal transfers only.
  - Historical implementation/review details: `docs/superpowers/plans/2026-09-15-pr143-review.md`, `docs/superpowers/plans/2026-09-15-final-gold-recovery-review.md`, and `docs/superpowers/plans/2026-09-14-durable-war-payments.md`.

- [x] **LG-1210** Executable approved level 1–100 reward catalog
  - Tag: `TDD`
  - References: REQ-050, REQ-054, REQ-093
  - Evidence: Operator-approved `docs/plans/2026-09-17-lg-1202-reward-catalog-proposal.md`; source inventory identifies legacy YAML/hardcoded divergence. SPEAR plan: `docs/plans/2026-09-17-chapter2-reward-implementation.md`. Complete: executable catalog and approved-table parity tests pass. Final JDK 21 `gradlew test shadowJar`: 946 tests, zero failures/errors/skips; Shadow JAR built. See `docs/plans/2026-09-17-reward-verification.md`.
  - Files: domain reward catalog and contract tests
- [x] **LG-1211** Purchased and permanent reward entitlement resolution
  - Tag: `TDD`
  - References: REQ-050, REQ-054, REQ-056, REQ-093
  - Evidence: Approved LG-1202 table semantics and retention rules; `docs/plans/2026-09-17-chapter2-reward-implementation.md` describes dependency order. Complete: ownership-derived offers, bounded effects, immutable snapshots, retention choices and transition guards are verified. All 24 reward tests and the full 946-test build pass; evidence in `docs/plans/2026-09-17-reward-verification.md`. Durable payment integration remains LG-1213.
  - Files: domain reward ownership/entitlement model and contract tests
- [x] **LG-1212** Durable guild reward ownership snapshots
  - Tag: `TDD`
  - References: REQ-050, REQ-054, REQ-056, REQ-093
  - Evidence: Eight identical ownership contracts pass on SQLite and disposable MariaDB 11.4.5: reopen, stale writers, corrupt/orphan reads, rollback and permanent-asset preservation. Full test/shadowJar: 959 passing; MariaDB: 21 passing including purchases. See `docs/plans/2026-09-17-atomic-reward-verification.md`.
  - Files: reward repository port, SQL adapter and persistence contract tests
- [x] **LG-1213** Atomic reward purchase and canonical gold payment
  - Tag: `TDD`
  - References: REQ-050, REQ-092, REQ-093
  - Evidence: `GuildGoldService.purchaseReward` commits the canonical debit/journal, ownership and durable receipt on one SQL connection. Thirteen purchase contracts pass on SQLite and MariaDB, including replay, concurrency and injected rollback. Full suite: 959 passing, plus 21 MariaDB contracts; Shadow JAR built. See `docs/plans/2026-09-17-atomic-reward-verification.md`. Live wiring remains LG-1214; raw ownership snapshot saves are not paid purchases.
  - Files: application purchase service, shared SQL transaction boundary, canonical gold journal integration, failure/concurrency tests
- [~] **LG-1214** Chapter 2 reward integration and consistent player read models
  - Tag: `TDD`
  - References: REQ-050, REQ-054, REQ-056, REQ-093
  - Evidence: Gold, progression/home/member consumers, Java/Bedrock views and placeholders share the gated consistent read model. Purchase actions now use immutable server quotes, separate confirmation screens, live membership/rank checks and the atomic gold/ownership transaction; uncertain retries retain their transaction ID. Focused contracts: 48 passing. Full regression: 998 passing; MariaDB: 36 passing, zero failures/errors/skips; Shadow JAR built. SPEAR: `docs/plans/2026-09-17-reward-purchase-ui.md` and `docs/plans/2026-09-17-reward-purchase-ui-verification.md`. Migration readiness and live UI validation remain open; rollout defaults disabled.
  - Files: config, gold settings, progression/home/member services, Java/Bedrock menus and placeholders

## PR-13 — Backlog: wars & combat (operator, Fain)

- [ ] **LG-1301** War system overhaul — accurate kill tracking with measurable gameplay impact: per-guild war kill counter (opposing-guild kills only, persisted, reset on war end) driving resolution at `war_kill_win_target` (default 25), surfaced in `/g info` + war menus
  - Tag: `TDD`
  - References: REQ-057
  - Evidence:
  - Files: war services (↳ PR-4 LG-401..405), kill counter persistence, win-by-kills resolution
- [ ] **LG-1302** World War — configurable secret predicate triggers a server-wide World War (idempotent, cooldown-gated, debug-force override)
  - Tag: `TDD`
  - References: REQ-058
  - Evidence:
  - Files: trigger detection/evaluation scheduler, war-scaling, config
- [ ] **LG-1303** War banners — deployable tactical teleport banner: 15 min, destructible, raw-gold cost, 1 active/guild, cooldown, rank-permission gated, broadcast on placement
  - Tag: `TDD`
  - References: REQ-059
  - Evidence:
  - Files: banner entity, placement/break listeners, broadcast
- [ ] **LG-1304** Better war notifications — prominent declaration alert, persisted unread notices replayed on login, victory/loss broadcasts
  - Tag: `TDD`
  - References: REQ-060
  - Evidence:
  - Files: declaration alert, unread-notice store + login replay, end-of-war broadcast (↳ PR-4)
  - Notes: replay must transition the notice to read/acknowledged — acceptance test proves a notice replays once and is cleared, never re-replaying on subsequent logins
- [ ] **LG-1305** Customizable war win conditions — unique kill counts, ransoms, death-duel "Champion" mode, high-stakes XP boost/deduction
  - Tag: `TDD`
  - References: REQ-061
  - Evidence:
  - Files: war objectives, surrender flow, duel arena (↳ LG-1205)

## PR-14 — Backlog: chat & communication (operator, Fain)

- [ ] **LG-1401** Login notifications — in-game alert when a guild member logs in
  - Tag: `TDD`
  - References: REQ-062
  - Evidence:
  - Files: `PlayerJoinEvent` handler (pattern: `apollo/GuildTeamListener.onPlayerJoin:26`), guild notification
  - Harvest: `AnnouncementService` + `AnnouncementRepository` from closed PR #7 (superseded) — rebuild against current persistence (SQLite migrations)
- [ ] **LG-1402** Rank prefixes in guild chat — member's rank shown next to name (legacy restore)
  - Tag: `TDD`
  - References: REQ-063
  - Evidence:
  - Files: guild chat formatter (↳ RoseChat hook)
- [ ] **LG-1403** Guild admin chat — dedicated private channel for admins/leadership
  - Tag: `TDD`
  - References: REQ-064
  - Evidence:
  - Files: chat channel registry, permission gate
- [ ] **LG-1404** Custom guild channels — guilds create/name own chat channels (pending RoseChat feasibility)
  - Tag: `TDD`
  - References: REQ-065
  - Evidence:
  - Files: channel CRUD, RoseChat integration

## PR-15 — Backlog: QoL, UI & Discord integration (operator, Fain)

- [ ] **LG-1501** Guild Statistics node completion — internal invitation tracker/leaderboard (most invites per member)
  - Tag: `TDD`
  - References: REQ-066
  - Evidence:
  - Files: `GuildStatisticsMenu.kt` (↳ LG-602 drill-downs, LG-806)
  - Harvest: `InvitationService` + `GuildInvitationRepositorySQLite` (+ migration) from closed PR #7 (superseded) — verify schema against current migrations before reuse
- [ ] **LG-1502** Dynamic spawn banners — physical spawn banners track top guilds by Guild Level Leaderboard
  - Tag: `TDD`
  - References: REQ-067
  - Evidence:
  - Files: banner update scheduler, leaderboard hook
- [ ] **LG-1503** Guild list GUI & leaderboards — all guilds, paged at the service boundary, 4 deterministic sort modes (all-time active, weekly active weighted by unique PvP kills, level low→high, creation old→new, ties → name → creation)
  - Tag: `TDD`
  - References: REQ-068
  - Evidence:
  - Files: guild list menu, paged lookup action (page/pageSize/sortKey/ascending + total count — NOT `GuildLookup.getAllGuilds()`), sort providers
  - Notes: page size from `guild_list.page_size` (default 18); prev/next page buttons with acceptance evidence; tie-break rules stable across refreshes
- [ ] **LG-1504** Guild banners in list — physical banner shown per guild, plain white default when unset
  - Tag: `TDD`
  - References: REQ-069
  - Evidence:
  - Files: banner resolution, list renderer
- [ ] **LG-1505** Expandable Enemy/Ally lists in `/g info` — full guild list beyond top 3
  - Tag: `TDD`
  - References: REQ-070
  - Evidence:
  - Files: `/g info` view, pagination
- [ ] **LG-1506** Dynamic Discord roles — level perk auto-creates/links a Discord role, grants/removes on join/leave
  - Tag: `TDD`
  - References: REQ-071
  - Evidence:
  - Files: Discord role service, join/leave hooks
- [ ] **LG-1507** Enhanced guild descriptions — Discord invite links embeddable in guild description
  - Tag: `TDD`
  - References: REQ-072
  - Evidence:
  - Files: description edit flow, renderer
- [ ] **LG-1508** Disband announcements — broadcast when a guild is disbanded
  - Tag: `TDD`
  - References: REQ-073
  - Evidence:
  - Files: guild delete path, broadcast
  - Harvest: `AnnouncementService` + repo from closed PR #7 (superseded) — shared with LG-1401

---

## PR-16 — Weekly Guild Quests (Chapter 2)

> Part of the Chapter 2 progression overhaul. Builds on the XP infrastructure in PR-12/LG-1201. Every quest, full-set bonus, and leaderboard Guild EXP payout passes through `QuestRewardSinkBukkit` to `ProgressionService.awardUncappedSystemExperience(guildId, amount, ExperienceSource.WEEKLY_ACTIVITY)`. The permanent award repository records progression and audit rows atomically without creating or consuming a source-cap usage row.
>
> **Claims-disabled constraint (EnthusiaSMP):** No claim-related quest actions (`CLAIM_CREATED`, `CLAIM_DESTROYED`) are included in the `QuestAction` enum. The progress listener gates claims-adjacent handlers on `claims_enabled`. See REQ-075.
>
> **Nav layout (10-slot, 5×2):**
> - Row 1: Guild Info — Members — Ranks — Economy (vault + bank + resources merged) — **Quests** 🎯
> - Row 2: Settings — Wars — Combat — ??? — **Statistics** (being built by another agent)

- [ ] **LG-1601** Domain model: `QuestDefinition`, `GuildQuestProgress`, `QuestAction` enum (with `ExperienceSource` mapping), and `ExperienceSource` reuse — domain layer, zero Bukkit imports
  - Tag: `TDD`
  - References: REQ-074, REQ-075, REQ-081
- Evidence: Domain model, semantic validator, bounded generator, overflow protection, and zero-Bukkit layer checks are GREEN; the newly specified direct `QuestAction`→`ExperienceSource` mapping remains open.
  - Files: `domain/values/QuestAction.kt`, `domain/entities/QuestDefinition.kt`, `domain/entities/GuildQuestProgress.kt`

- [ ] **LG-1602** Quest persistence: `QuestRepository` (interface in application/persistence) + `QuestRepositorySQLite` with migration for per-guild quest progress (quest_id, guild_id, current_count, completed, claimed, reset_timestamp)
  - Tag: `TDD`
  - References: REQ-080
- Evidence: Repository, atomic active-set replacement, claim-preserving upserts, per-recipient payout markers, cleanup, and restart tests are GREEN; the newly specified quest-table migration chain remains open.
  - Files: `application/persistence/QuestRepository.kt`, `infrastructure/persistence/guilds/QuestRepositorySQLite.kt`, `migrations/*.sql`

- [x] **LG-1603** Quest config loading: load weekly quest definitions from config (quests section in config.yml or separate quests.yml) — action type, target count, reward tier (COMMON/CHALLENGING/HEADLINE/CONDITIONED), optional item rewards, lang keys, enabled flag
  - Tag: `TDD`
  - References: REQ-079
  - Evidence: Typed progression config loads actions, targets, reward tiers, optional conditions/items, lang keys, enabled/default-disabled state, and rejects empty definition sets.
  - Files: config loader, quest definition config model

- [ ] **LG-1604** Quest progress listener: Bukkit event listener in infrastructure/listeners that increments quest progress matching active weekly quests, using `QuestAction`→`ExperienceSource` mapping for provenance compatibility. Claims-adjacent event handlers (block break/place for MINE_BLOCKS/PLACE_BLOCKS) SHALL gate on `claims_enabled` before registering — the listener SHALL NOT register claim-related handlers when claims are disabled.
  - Tag: `TDD`
  - References: REQ-075
- Evidence: Listener covers the configured activity families and provenance reconciliation; the newly specified `claims_enabled` registration gate remains open.
  - Files: `infrastructure/listeners/QuestProgressListener.kt`

- [x] **LG-1605** Quest lifecycle service: weekly rotation (auto-reset at configured time, default Monday 00:00 UTC), quest activation/deactivation, guild progress aggregation, completion detection per quest
  - Tag: `TDD`
  - References: REQ-074
  - Evidence: `QuestServiceTest` and coordinator integration cover shared weekly rotation, deactivation, guild aggregation, completion, payout-before-cleanup, and retry-safe recipient state.
  - Files: `application/services/QuestService.kt`

- [x] **LG-1606** Quest reward delivery: claim flow awarding Guild EXP via the uncapped `WEEKLY_ACTIVITY` system pipeline + optional item rewards (drop or inventory); claim-once-per-week-per-guild enforcement
  - Tag: `TDD`
  - References: REQ-077, REQ-078
  - Evidence: Claim-once persistence, claim-gated full-set bonus, weekly activity XP, namespaced item reward round-trip, stack splitting, and inventory overflow drops are implemented and tested.
  - Files: reward delivery in `QuestService`, claim command/menu handler

- [x] **LG-1607** Quest menu UI: ChestGUI/StaticPane menu shown as a nav-accessible page (Row 1, Slot 5 — replacing the former vault slot which now lives under Economy). Menu displays active quests with name, description, progress bar, target count, reward tier, and claim button — wired through `MenuFactory` and `MenuNavigator`.
  - Tag: `TDD`
  - References: REQ-076
  - Evidence: Dashboard/factory/6-row ChestGUI navigation, progress/reward/claim rendering, timer, pagination, and explicit Bedrock fallback are wired.
  - Files: `interaction/menus/guild/GuildQuestsMenu.kt`

- [x] **LG-1608** Lang keys: all player-facing quest strings in `lang/en_US.yml` via `LangService` — quest names, descriptions, completion messages, error messages, reward announcements
  - Tag: `INFRA`
  - References: REQ-074..REQ-081
  - Evidence: Quest menu and feedback strings use `LangService`; `MenuLocalizationTest`, `LocaleContractTest`, and the full clean suite (625 tests before merge) are GREEN.
  - Files: `lang/en_US.yml` (quest section)

- [x] **Claims-disabled vault startup regression (REQ-015):** Vault claim lookup is optional; claims-enabled placement remains fail-closed. Both real startup graphs pass, and the full test suite plus shadowJar build pass.
- [x] **Withdrawal fee messaging (REQ-015):** Quick withdrawal buttons preview actual capped fees and total deduction; successful physical and personal-account withdrawals report destination, fee and total. Regression test and full suite pass; shadowJar rebuilt.
