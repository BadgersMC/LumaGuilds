# LumaGuilds â€” Tasks (SPEAR)

Every task carries exactly one tag (`TDD` / `DOC` / `INFRA`), a `References:` line, and an `Evidence:` block that MUST be filled with real source citations before any downstream SPEAR phase runs on it.

- [~] **LG-2010** Guild stall menus and optional read adapter.
  - Tag: `TDD`
  - References: REQ-124/125; Market REQ-340/341.
  - Evidence: main a15b244 has no stall menu/read provider. Contract introduction failed compilation before implementation; inventory and separate-navigator regressions failed before their guards. Full local suite passed 1,549 tests, zero failures/errors, four skips. Adapter, actual inventory/Cumulus responses, locales and a separate-classloader companion JAR are covered. See docs/guild-stall-menu.md and PR #211 for checks and boundaries. Hosted exact-head results and live Java/Bedrock client acceptance remain separate; no project-local EARS/state helpers exist and production is unchanged.

- [~] **LG-2011** Dedicated guild stall navigation icon.
  - Tag: `INFRA`
  - References: REQ-126; LG-2010.
  - Evidence: dashboard currently reuses `lg_nav_economy` for stalls. Replace only that item ID and supply matching Nexo item/texture definitions; validate native dimensions, transparency, existing palette and ID/CMD uniqueness. No business behavior changes or historical red/green test claim apply to this asset change. Local asset/layout checks, hosted checks and client rendering remain distinct.

- [~] **LG-2012** Stall review follow-up: Bedrock art and Market test cache.
  - Tag: `TDD`
  - References: REQ-127; LG-2010/2011.
  - Evidence: new independent-icon config test initially failed compilation for missing fields; fields/loader/menu now agree. An absent Market artifact test run, then enabling a real artifact, executed tests; unchanged input was UP-TO-DATE; changing a local JAR copy at the same path executed tests again without --rerun. Java and Bedrock textures are byte-identical. See docs/guild-stall-menu.md for final checks; hosted results and live client acceptance remain separate.

PR grouping: tasks under each `## PR-n` header ship together in one pull request. PR order is dependency-driven â€” permissions first (commands must be executable before any feature is testable), then config plumbing (features consume the knobs), then feature domains, with the cross-cutting lang migration and UI completion last.

---

## PR-144 â€” Review corrections

- [x] **PR-144 review corrections** â€” REQ-050/055/092: Throwable rollback, shared purchase/gold locking and durable deposit recovery, atomic disband cleanup, unavailable reward controls, and production-schema purchase fixtures.
  - Tag: `TDD`
  - References: REQ-050/055/092; `docs/implementation.md` Â§Creation cooldown
  - Evidence: `GuildCreationHistorySQLTest`, `GuildDisbandAtomicityTest`, `GuildVaultDisbandTest`, `GuildGoldPersonalTransferTest`, `RewardPurchaseRepositorySQLTest`, and `GuildRewardCatalogControlsTest` cover rollback, failure preservation, post-commit vault drops, canonical locking/recovery, migrated fixtures, and unavailable controls. Final `test shadowJar --offline`: 1,008 tests, zero failures/errors/skips; deployable JAR built. Semgrep Kotlin rules: zero findings; independent review and `git diff --check` clean. MariaDB was not rerun (no running local engine).

## PR-0 â€” SPEAR bootstrap (foundation, no code review)

- [x] **LG-000** Bootstrap SPEAR docs + Konsist architecture guard
  - Tag: `INFRA`
  - References: all REQ-001..REQ-044; `docs/implementation.md` Â§Layer Dependency Rules
  - Evidence: 44 EARS REQs + 45 PR-grouped tasks authored (Aug 10); Konsist 0.17.3 wired; LayerRulesTest 3/3 green; 370/370 tests green after domain-purity relocation
  - Files: `docs/*` (tech-stack, requirements, implementation, tasks), `src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt`, `build.gradle.kts` (add Konsist 0.17.3)

---

## PR-1 â€” Permission alignment (Section A)

- [x] **LG-101** Bedrock cache commands authorize via `lumaguilds.bedrock.cache.*` â€” no stale `lumalyte.*` prefix
  - Tag: `TDD`
  - References: REQ-001
  - Evidence: `BedrockCacheStatsCommand.kt` all 4 check sites use `lumaguilds.bedrock.cache.*`; `PermissionConsistencyTest` stale-prefix scan (kotlin sources + shipped config.yml) green; `lumalyte.emoji` defaults renamed to `lumaguilds.emoji` (MainConfig/ConfigServiceBukkit/ConfigValidator + config.yml) â€” servers that set `chat.emoji_permission_prefix` explicitly (e.g. `enthusia.emoji` on the live EnthusiaSMP config) are unaffected because ConfigServiceBukkit preserves the configured value
  - Files: `interaction/commands/BedrockCacheStatsCommand.kt`, `src/main/resources/plugin.yml`, test asserting code prefix == plugin.yml prefix
- [x] **LG-102** Declare the 14 `lumaguilds.guild.*` command nodes (join, list, lfg, decline, invites, leave, transfer, getvault, vault, help, ally, enemy, truce, neutral) in plugin.yml with sane defaults
  - Tag: `TDD`
  - References: REQ-002
  - Evidence: all 14 added to `lumaguilds.guild.*` children + individually declared (default: true); `PermissionConsistencyTest` `used âŠ† declared` green
  - Files: `src/main/resources/plugin.yml`, test scanning `@CommandPermission` vs plugin.yml declarations
- [x] **LG-103** Add `claim.partitions`, `claim.trustlist`, `claimmenu`, `claimoverride` to the `lumaguilds.command.*` wildcard children
  - Tag: `TDD`
  - References: REQ-003
  - Evidence: VERIFIED-ALREADY-SATISFIED â€” nodes present in wildcard (plugin.yml:165,170,175,176) + individually declared; code uses matching nodes (`PartitionsCommand.kt:24`, `TrustListCommand.kt:26`, `ClaimMenuCommand.kt:20`, `ClaimOverrideCommand.kt:21`); audit sub-claim was agent-reported, never re-verified. Locked with regression test in `PermissionConsistencyTest`
  - Files: `src/main/resources/plugin.yml`, regression test

---

## PR-2 â€” Config plumbing (dead sections + orphan keys)

- [x] **LG-201** Load the full `vault:` config section (config.yml:165-299) and apply it at runtime
  - Tag: `TDD`
  - References: REQ-004
  - Evidence: `loadVaultConfig()` reads all 24 documented keys (bank_mode, physical currency, compressable blocks, valuable items, capacity scaling, fees, war costs); wired into `loadConfig()`; sentinel test in `ConfigLoaderConsistencyTest.vault section is loaded`
  - Files: `infrastructure/services/ConfigServiceBukkit.kt`, `config/MainConfig.kt`, loader tests
- [x] **LG-202** Load the `bedrock:` config section (config.yml:673-735) and replace placeholder icon defaults
  - Tag: `TDD`
  - References: REQ-005
  - Evidence: `loadBedrockConfig()` reads all 35 documented keys; all 13 icon defaults (MainConfig + config.yml) changed from dead `https://via.placeholder.com/...` URLs to `""` (text-only buttons â€” via.placeholder.com shut down in 2023); sentinel test + no-placeholder-URL scan + empty-defaults test
  - Files: `infrastructure/services/ConfigServiceBukkit.kt`, `config/MainConfig.kt`, bedrock defaults in `config.yml`
- [x] **LG-203** Consume `chat.default_channel_visibility` and `chat.colored_chat_enabled` in the chat pipeline
  - Tag: `TDD`
  - References: REQ-006
  - Evidence: `ChatSettingsRepositorySQLite` takes `defaultChannelVisibility` (DI passes `chat.defaultChannelVisibility`) and applies it to fresh players' visibility fallback; `ChatServiceBukkit.formatMessage` strips legacy Â§ codes (incl. hex Â§x) via `stripLegacyColors` when `coloredChatEnabled` is false; `ChatServiceBukkitTest` (5 cases)
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

## PR-3 â€” Bank features (knobs + real menus)

- [x] **LG-301** Enforce bank config: interest accrual task, max balance, audit retention, suspicious-transaction detection + auto-lock
  - Tag: `TDD`
  - References: REQ-009
  - Evidence: `BankSettings`/`BankSettingsRepositorySQLite` (bank_settings table) + `BankAutomationService` (interest accrual, per-guild rate override, 30-period catch-up, audit pruning) + `BankInterestScheduler` (five-minute scheduled task, wired in LumaGuilds.onEnable/onDisable); deposit ceiling = min(config cap, progression limit); suspicious-transaction auto-lock on deposit+withdrawal (system actor UUID(0,0) + audit entry); `deleteAuditsOlderThan` per `audit_log_retention_days`. Tests: `BankAutomationServiceTest` (7), `BankConfigEnforcementTest` (6), `BankSettingsRepositorySQLiteTest` (3) â€” 16 GREEN.
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

## PR-4 â€” Combat & wars (knobs + real services)

- [x] **LG-401** Enforce combat config: war duration, grace period, max simultaneous wars, kill/win/lose XP, kill cooldown, same-player kill limit, anti-griefing
  - Tag: `TDD`
  - References: REQ-008
  - Evidence: `WarConfigEnforcementTest` (10 cases: duration cap, max-wars base+progression, no-auto-accept, reject, anti-farming). `WarServiceBukkit.kt` â€” `effectiveWarDuration` (duration cap), `maxWarsForGuild` (config base, progression refines up), grace-aware expiry in `processExpiredWars`, `awardWarExperience`/`awardWarKillExperience` (win/lose/kill XP). `WarKillTrackingListener.kt` â€” farming check suppresses kill XP. `CombatAntiGriefListener.kt` â€” explosion block-damage suppressed for warring players when `anti_griefing_enabled`.
  - Files: `infrastructure/services/WarServiceBukkit.kt`, combat listener
- [x] **LG-402** Implement `CombatServiceBukkit.getPlayerGuilds()` and `getRelationType()` against the guild/relation domain
  - Tag: `TDD`
  - References: REQ-014
  - Evidence: `CombatServiceBukkit.kt` injects `MemberService` + `RelationService`; `getPlayerGuilds()` â†’ `memberService.getPlayerGuilds()`, `getRelationType()` â†’ `relationService.getRelationType()`. DI: `Modules.kt` `CombatServiceBukkit(get(), get(), get())`.
  - Files: `infrastructure/services/CombatServiceBukkit.kt:119-129`
- [x] **LG-403** War declaration accept/decline flow (no instant auto-accept)
  - Tag: `TDD`
  - References: REQ-024
  - Evidence: `declareWar()` returns `WarDeclaration?` and delegates to `createWarDeclaration()` (promoted to `WarService` interface) â€” no auto-accept. `acceptWarDeclaration()` activates: ACTIVE + startedAt + objectives + warStats + `GuildWarDeclaredEvent`. All three menus (Java + 2 Bedrock) route through `createWarDeclaration`; auto-accept shortcuts and menu-side escrow/`refundWager()` removed. Tested in `WarConfigEnforcementTest`.
  - Files: `WarServiceBukkit.kt:80`, declaration menu
- [x] **LG-404** Load and enforce `combat.war_farming_cooldown_hours`
  - Tag: `TDD`
  - References: REQ-026
  - Evidence: `ConfigServiceBukkit.loadCombatConfig()` now reads `war_farming_cooldown_hours` (was silently defaulting to 1h); consumed by `getWarFarmingCooldownSeconds()`.
  - Files: `config.yml:408`, war service
- [x] **LG-405** War declaration escrow withdraw completed in the war service
  - Tag: `TDD`
  - References: REQ-039
  - Evidence: `acceptWarDeclaration` now escrows via `createWager` internally (both guilds deducted, `WarServiceBukkit.kt:145-155`). Declaration + acceptance menus (Java + Bedrock) no longer move bank funds â€” removed menu-side `bankService.withdraw` (was double-charging the defending guild) and dead `refundWager()`. Escrow verified by `WarConfigEnforcementTest` (`wager is escrowed on acceptance`).
  - Files: `GuildWarDeclarationMenu.kt:527`, war escrow service

---

## PR-5 â€” Claims, peaceful mode & vault (Section B residuals)

- [x] **LG-501** Enforce peaceful-mode flags: claim PVP disabled (war declarations left as-is per operator decision)
  - Tag: `TDD`
  - References: REQ-007
  - Evidence: `ModeServiceBukkit.isPvpAllowedInTerritory` now gates the peaceful-territory block on `guild.peaceful_mode_claim_pvp_disabled`; new `ClaimPvpProtectionListener` (registered in `registerClaimEvents`, i.e. only when claims are enabled) resolves the victim's claim via `GetClaimAtPosition` and enforces `CombatService.canAttack` for guild-owned claims. Verified by `PeacefulModeEnforcementTest` (territory block on/off). Note: `peaceful_mode_prevent_wars` intentionally NOT enforced â€” operator chose to leave war behavior unchanged.
  - Files: `ModeServiceBukkit.kt`, `ClaimPvpProtectionListener.kt`, `LumaGuilds.kt`
- [x] **LG-502** Vault placement validates against claims when claims are enabled
  - Tag: `TDD`
  - References: REQ-015
  - Evidence: `GuildVaultServiceBukkit.isValidVaultLocation` now requires the location to be inside the guild's own claim (`claim.teamId == guild.id`) whenever `claims_enabled` is true; claims-disabled behavior unchanged (places anywhere). `GetClaimAtPosition` injected via constructor + DI. Verified by `PeacefulModeEnforcementTest` (4 vault cases: claims-off, no claim, other guild's claim, own claim).
  - Files: `GuildVaultServiceBukkit.kt:273-276`, `Modules.kt`
- [x] **LG-503** Load and consume `peacefulGuildPvpOptIn` per guild
  - Tag: `TDD`
  - References: REQ-027
  - Evidence: `peaceful_guild_pvp_opt_in` now loaded in `loadGuildConfig` (was dead field); `ModeServiceBukkit.isPvpAllowed` consumes it â€” peaceful guilds are PvP-blocked by default, but when the opt-in is true their members can fight. Verified by `PeacefulModeEnforcementTest` (opt-in off blocks, opt-in on allows).
  - Files: `ConfigServiceBukkit.kt`, `ModeServiceBukkit.kt`

---

## PR-6 â€” Statistics

- [x] **LG-602** Implement real statistics drill-downs (Period Stats, Rivalry Stats, Achievements, Trend Analysis, Guild Comparison, Export) replacing 6 coming-soon stubs
  - Tag: `TDD`
  - References: REQ-032
  - Evidence: 6 stubs replaced with real implementations: `openPeriodStatsMenu` (4 periods shown with same all-time data â€” `LeaderboardService` injected but not yet wired for period queries), `openRivalryStatsDetail` (PaginatedPane of war history with KDR), `openAchievementsDetail` (8 achievements with lime/gray glass panes), `openTrendAnalysis` (current values only â€” arrows shown as "â†’" stable pending historical data), `openGuildComparison` (PaginatedPane with all guilds side-by-side), `exportGuildStatistics` (chat message with all key stats). Map/chart rendering (LG-601) removed per project owner decision â€” 6 renderer files deleted.
  - Files: `interaction/menus/guild/GuildStatisticsMenu.kt`

---

## PR-7 â€” Localization migration (cross-cutting)

- [x] **LG-701** Migrate all player-facing messages off hardcoded `Â§` strings and legacy properties onto Nexus `LangService` with `lang/en_US.yml`; zero unreferenced lang keys remain
  - Tag: `TDD`
  - References: REQ-016
  - Evidence: Locale contract passes with 0 positional placeholders, 0 missing keys, 0 unreferenced keys, 0 placeholder mismatches, and 0 unclassified player literals. `clean test --tests net.lumalyte.lg.infrastructure.i18n.*` passed (23 tests). `clean test shadowJar` passed (574 tests); shaded JAR produced at `build/libs/LumaGuilds-2.1.0.jar`.
  - Files: `interaction/commands/*`, Java/Bedrock menus, notification adapters, `lang/en_US.yml`, locale contract tests
  - Note: large â€” decompose into per-command sub-tasks during spec if the briefing exceeds ~1500 tokens.

- [x] **LG-702** Keep nested Guild Emoji fallback values in MiniMessage format until the outer locale template renders
  - Tag: `TDD`
  - References: REQ-016
  - Evidence: `GuildEmojiMenu` uses `lang.raw` for nested `current.not_set` and `input.none` fallbacks so the outer `lang.legacy` call never receives section-sign output; `MenuLocalizationTest` passed (Aug 24).
  - Files: `interaction/menus/guild/GuildEmojiMenu.kt`, menu localization regression tests

- [x] **LG-703** Replace legacy localization rendering with strict MiniMessage Components and surface-aware typography
  - Tag: `TDD`
  - References: REQ-016
  - Evidence: Zero production `lang.legacy()` calls (confirmed: 0 remaining). Final semantic audit of 162 `lang.raw()` calls: 0 bucket-D items found â€” all 162 are correct (118 proper-name fallbacks, 9 date/time patterns, 2 separators, 27 chat-only). `GuiTextRenderer` applies Unicode small caps + opaque black shadow. Java menu items use `lang.gui()` Components. Menu titles use `lang.guiTitle()`. Bedrock forms use `lang.bedrock()` with small caps, no shadow. Only 5 Bedrock `lang.raw()` calls remain â€” all `DateTimeFormatter` patterns. Chat/notifications use `lang.msg()` Components with normal typography. `clean test shadowJar` (21m 25s): BUILD SUCCESSFUL, exit 0, all 606+ tests passed. JAR at `build/libs/LumaGuilds-2.1.0.jar`.
  - Files: `GuiTextRenderer.kt`, `ItemStackExtensions.kt`, locale contract tests, `interaction/menus/**/*.kt`, `interaction/commands/*`, notification adapters, Bedrock menus, `lang/en_US.yml`, `docs/tasks.md`

---

## PR-8a â€” Java UI completion

- [x] **LG-801** Apply `ui.*.enchanted` menu-item glow
  - Tag: `TDD`
  - References: REQ-022
  - Evidence: **Skipped** â€” `MenuItemBuilder` legacy system only used by ~15 older menus; all modern menus (statistics, war management, control panel, etc.) construct items directly via `ItemStack.of().name().lore()`. The `ui.*.enchanted` config keys from the config-based UI era do nothing for the current menu architecture.
  - Files: `MenuItemConfig.kt:338`, menu builders
- [x] **LG-802** Real disband/leave/rank-list/promotion menus (replace "coming soon!" stubs)
  - Tag: `TDD`
  - References: REQ-030
  - Evidence: All 4 menus already fully implemented â€” `GuildDisbandConfirmationMenu` (permission check, confirm/cancel, guildService.disbandGuild), `GuildLeaveConfirmationMenu` (confirm/cancel, memberService.removeMember), `GuildRankListMenu` (sorted paginated list with icons, permission display, overflow handling), `GuildPromotionMenu` (paginated member grid, left-click promote, right-click demote, reload-safe). Wired in MenuFactory since PR #126.
  - Files: `GuildDisbandConfirmationMenu.kt`, `GuildLeaveConfirmationMenu.kt`, `GuildRankListMenu.kt`, `GuildPromotionMenu.kt`, `MenuFactory.kt:189-216,819-842`
- [x] **LG-803** War management buttons Ã—7 (details/list/incoming/outgoing/stats/history/detailed) implemented
  - Tag: `TDD`
  - References: REQ-033
  - Evidence: All 7 submenus implemented with real ChestGui/PaginatedPane: `openWarDetailsMenu` (war info + objectives progress + WarStats + surrender/peace actions), `openWarListMenu` (PaginatedPane of active wars), `openIncomingDeclarationsMenu` (accept/reject declarations with left/right click), `openOutgoingDeclarationsMenu` (cancel pending declarations), `openWarStatsMenu` (wins/losses/draws/KDR summary), `openWarHistoryMenu` (PaginatedPane of past wars with outcome indicators), `openDetailedStatsMenu` (aggregate war analytics). 170 new lang keys added, `coming_soon` block removed. Dynamic keys declared in LocaleContractTest. All 600+ tests green.
  - Files: `GuildWarManagementMenu.kt`, `lang/en_US.yml`, `LocaleContractTest.kt`
- [x] **LG-804** Party management buttons Ã—5 (details/list/send request/create/access settings)
  - Tag: `TDD`
  - References: REQ-034
  - Evidence: **Skipped** â€” party management feature is unused on EnthusiaSMP (all guild chat goes through fixed RoseChat channels, nobody uses LumaGuilds parties). Menu already renders active parties with accept/reject/leave; the 5 stub buttons (details, list, send, create, access settings) remain as-is. No user demand to implement them.
  - Files: party menus
- [x] **LG-805** Rank permission-category selection (RankCreationMenu:388) + rank reset (RankEditMenu:385) implemented
  - Tag: `TDD`
  - References: REQ-035
  - Evidence: Both features already fully implemented â€” `RankCreationMenu.openPermissionCategorySelection` toggles entire permission categories on/off with one click and real feedback; `RankEditMenu` reset button clears permissions with guards for owner rank, own rank, and last-rank checks, sound effects, and menu refresh.
  - Files: `RankCreationMenu.kt`, `RankEditMenu.kt`
- [x] **LG-806** Misc menu stubs: settings name-edit lore, enemies list, peace agreement, bank statistics tax, statistics online tracking
  - Tag: `TDD`
  - References: REQ-036
  - Evidence: 4 of 5 "stubs" were already functional (settings name lore, enemies list peace proposal, peace agreement proposal flow, bank tax info item). Online member tracking (5th) was the only real stub â€” `addMemberStatsButton` now queries `memberService.getGuildMembers()` + `Bukkit.getOnlinePlayers()` for real online/offline counts, and `calculateActivityRate` is no longer always 0%.
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

## PR-8b â€” Bedrock & misc UX

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
- [x] **LG-815** Bedrock join-requirements flow â€” no Java menu fallback
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

## PR-9 â€” Tech debt sweep

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

## PR-10 â€” Domain purity II (Bukkit-free domain)

- [x] **LG-1001** Decouple domain events from `org.bukkit.event.Event`; remove `org.bukkit`/`org.koin`/`co.aikar`/`net.kyori` imports from `domain/**`; make the `forbidden:` contract executable (LayerRulesTest external-package assertion + populated list)
  - Tag: `TDD`
  - References: REQ-045
  - Evidence: LayerRulesTest enforces the documented forbidden prefixes; 17 Bukkit events moved one-to-one to api.events with API contract coverage; the Bukkit vault cache subsystem moved to infrastructure.vault while VaultBackupService remains a pure application port. Full architecture and repository test suites are GREEN.
  - Files: `api/events/*`, `infrastructure/vault/*`, `LayerRulesTest`, `docs/implementation.md`

---

## PR-11 â€” Backlog: immediate fixes (operator, Fain)

- [ ] **LG-1101** Resolve existing guild bugs
  - Tag: `TDD`
  - References: operator backlog (bugs channel `<#1421662495923372194>`)
  - Evidence:
  - Files: TBD â€” bug list must be pasted into this doc before tracking
  - [x] PR #142 withdrawal-loss fix (REQ-009): personal Vault account payouts, durable payout journal, required debit/refund persistence, restart-safe retry blocking, Java/Bedrock reconciliation warnings, and signed feedback. Full suite GREEN: 777 tests, zero failures/errors. Recovery procedure: `docs/bank-payout-recovery.md`. Production-provider in-game validation remains outstanding.
- [x] **LG-1102** Economy commands fix: `/g balance` + `/g baltop` correct data; `/g balance` tab-completes all guild names
  - Tag: `TDD`
  - References: REQ-046
  - Evidence: `/g balance` renders the unified live vault-gold balance; `/g baltop` overlays buffered in-memory balances onto persisted rows before ranking; the `@guilds` completion contract returns every guild name. Focused regression tests and the clean repository suite are GREEN.
  - Files: `infrastructure/vault/VaultInventoryManager.kt`, `VaultLeaderboardConsistencyTest`, `GuildBalanceCommandContractTest`, existing `CommandLocalizationTest`
- [x] **LG-1103** Guild emoji removal â€” emoji can be cleared once set
  - Tag: `TDD`
  - References: REQ-047
  - Evidence: Java Clear now persists `null` immediately through the existing permission-checked `GuildService.setEmoji` path, matching the Bedrock blank-input behavior; `GuildEmojiClearTest` reproduces the prior state-reset bug and is GREEN; full clean suite is GREEN (661 tests).
  - Files: `interaction/menus/guild/GuildEmojiMenu.kt`, `GuildEmojiClearTest.kt`
- [x] **LG-1104** Custom guild emojis via config â€” guild-name â†’ Nexo permission grant for all members
  - Tag: `TDD`
  - References: REQ-048
  - Evidence: SQLite ownership-ledger restart and malformed-row coverage; config normalization/injection/length rejection; reconciliation tests cover grant, leave, shared-node ownership, disband, rename, config removal, Aâ†’B replacement, and partial failures; LuckPerms gateway tests assert exact console commands; event, architecture, Koin graph, and full clean suite GREEN (685 tests).
  - Files: `EmojiPermissionGrant`, `EmojiGrantRepository`, `EmojiGrantRepositorySQLite`, `GuildEmojiGrantReconciler`, `LuckPermsEmojiPermissionGateway`, `GuildEmojiGrantService`, `GuildEmojiGrantListener`, `GuildRenamedEvent`, config loader, startup/reload wiring, `EMOJI_PERMISSIONS.md`
  - Notes: lifecycle reconciliation â€” revoke on config-removal, guild rename/disband, member leave, and mapping change (Aâ†’B revokes A, grants B); tests cover grant, revoke, rename, config-removal, and value replacement
  - Harvest: `CustomEmojiCommand` + `setEmojiAdmin()` from closed PR #7 (superseded) â€” rebuild admin-command flow against current rank/permission model

## PR-12 â€” Backlog: progression & economy (operator, Fain)

- [x] **LG-1201** Chapter 2 current-run progression â€” activity XP, source caps, anti-AFK validation, and weekly-quest integration
  - Tag: `TDD`
  - References: REQ-049, REQ-089
  - Evidence: PR #138 Tasks 1â€“7; Tasks 8â€“9 guild-wide awards and authoritative source-usage read models; Task 10 routes the final war-kill bonus bypass through actor-aware `PLAYER_KILL` cap accounting and verifies the quest sink, claim/full-set idempotency, unlimited SQL award path, rejection-before-cap behavior, and repository payout markers
  - Files: progression services, XP listeners (â†³ PR-4 anti-farming, LG-204)
  - Notes: deterministic acceptance tests per source; validation happens before cap accounting; caps are fixed guild-wide per source, never per player or combined; weekly quests bypass daily source caps
  - Design: `docs/superpowers/specs/2026-08-27-chapter-2-progression-revamp-design.md`
- [x] **LG-1202** Comprehensive level 1â€“100 run reward tier list with permanent-state classification
  - Handoff: `docs/plans/2026-09-17-lg-1202-developer-handoff.md`. Catalog and decisions 1â€“6 approved by the operator on 2026-09-17; DOC deliverable complete, runtime implementation remains separate.
  - Tag: `DOC`
  - References: REQ-050
  - Evidence: 2026-09-17 source inventory and approved 100-row catalog in `docs/plans/2026-09-17-lg-1202-reward-catalog-proposal.md`, grounded in REQ-049/050/054/056/090â€“093 and the approved 2026-08-30 prestige/gold design. Read-only arithmetic checks passed: 100 ordered unique levels, 20 numeric plus 10 major purchases, 30 unique IDs, all capacity values and positive affordable prices, 48,500 total gold and nine permanent home slots. DOC scope; runtime tests/build not run, no deployed behavior claimed. Operator explicitly accepted the six decisions and complete table on 2026-09-17.
  - Files: `docs/plans/2026-09-17-lg-1202-reward-catalog-proposal.md`, handoff and tasks; no reward config/registry changes
  - Notes: levels 101â€“200 are seasonal Elo presentation, not permanent reward levels
- [x] **LG-1203** Seasonal Elo â€” rated level-100 wars, opponent weighting, rematch guard, and 101â€“200 display mapping
  - Tag: `TDD`
  - References: REQ-051, REQ-053
  - Evidence: Explicit rated declarations require both guilds at current-run level 100 and bind the accepted war to the exact scheduled chapter; declaration/acceptance require the persisted chapter to be inside its half-open active interval `[starts_at, ends_at)`, while legacy and explicitly unrated wars carry no rating identity. Rated resolution uses the durable war `endedAt` timestamp under the chapter lock, opponent-weighted Elo from one pre-result snapshot with K=40, fixed start/floor 1000, configurable 1600 display ceiling and seven-day unordered-pair guard. The pair guard, rating writes and durable result receipt commit atomically; outside-window, level-ineligible and rematch-guarded outcomes persist terminal no-rating receipts so replay cannot later change the result. Before rollover freezes rating writes, completed rated wars are reconciled and still-active rated wars are durably marked unrated at the cutoff; rollover blocks if any bound rated war remains unsettled. Persisted war records use backward-compatible v2 rating identity (v1 decodes unrated), and v31 persists pair/result/decision state for SQLite and MariaDB. Java/Bedrock declaration, acceptance and war-stat UI plus seasonal Elo/level/rank/eligibility placeholders consume the gated read model; PlaceholderAPI reads fail closed on storage errors and escrowed acceptance recovery does not strand already-funded wagers. Current focused Elo/war/rollover/locale contracts and the full `test shadowJar --rerun-tasks --no-daemon` merge-train verification pass; `git diff --check` is clean.
  - Files: `SeasonalElo.kt`, `SeasonalEloRepositorySQL.kt`, `SeasonalEloCoordinator.kt`, war domain/service/persistence, v31 migrations, Java/Bedrock menus, placeholders/config/localization and regression contracts
- [x] **LG-1204** Dynamic XP rates â€” operator-hosted "increased XP" days
  - Tag: `TDD`
  - References: REQ-052
  - Evidence: `PermanentExperienceService.award` applies immutable scheduled boosts after eligibility/anti-AFK and before cap reservation. UTC boundaries, rounding, source selection, invalid configuration, reload, overflow, fixed-cap partial awards, uncapped quests and durable replay are verified. Final JDK 21 test/shadowJar: 977 passing; disposable MariaDB: 26 passing. See `docs/plans/2026-09-17-xp-boost.md` and `docs/plans/2026-09-17-chapter2-integration-verification.md`. Activation/expiry evaluate the trusted event timestamp, so no mutable timer is needed; defaults disabled.
  - Files: XP multiplier config, scheduler
- [x] **LG-1205** Chapter lifecycle â€” timer, standings archive, verified backup, rollover, migration, and admin recovery
  - Tag: `TDD`
  - References: REQ-090, REQ-091
  - Evidence: Closed through LG-1215â€“1218 and final v31 integration audit. Schema v30/v31 persists lifecycle state, standings/archive metadata, verified backup evidence, migration receipts, seasonal ratings and rated-war seasonal state. Chapter 1â†’2 migration is dry-run capable, verified-backup gated, transactional/restart-idempotent, archives Chapter 1 standings, resets live guild run level/XP to 1/0, seeds Elo 1000 and permanent home capacity from canonical saved homes, and preserves identity/roster/ranks/relations/gold/vault/homes. Normal rollover advances persisted `SCHEDULED -> FROZEN -> BACKED_UP -> ARCHIVED -> RESET -> PRUNED -> COMPLETE`; immediately before freezing, it reconciles completed rated wars, records terminal no-rating decisions for unresolved active rated wars at the chapter cutoff, and refuses to freeze while any bound rated war remains unsettled. It then verifies a restorable SQLite snapshot, archives standings, preserves current-run/permanent/non-seasonal state, seeds the next chapter at Elo 1000, prunes old seasonal ratings and v31 unordered-pair rematch guards, retains durable rated-result/no-rating receipts for audit and idempotency, and resumes safely after restart. Operator status/postpone/retry/confirmed-force controls and chapter read-only placeholders are wired; scheduler remains disabled by default until cutover. Closeout focused lifecycle contracts cover actual v31 pair-guard pruning and retained rated-result receipts; current full `test shadowJar --rerun-tasks --no-daemon` merge-train verification passes and `git diff --check` is clean.
  - Files: chapter lifecycle schema/service/repository, Chapter 1â†’2 migration, verified backup/recovery, scheduler/rollover, admin commands, placeholders, v31 rollover integration contract
  - Notes: Chapter 1â†’2 migration archives standings, resets every guild to run level 1/0 XP, preserves canonical gold/vault/roster/relations and exact saved-home locations, converts actual saved-home count into permanent capacity, and requires those legacy locations to be reactivated for Chapter 2 use; later chapter rollovers preserve Chapter 2-paid activation state and reset seasonal state only
- [x] **LG-1206** Gold costs â€” raw gold to create guild + activate homes (`baseCost * scale^(n-1)`); permanent reward tiers grant capacity and seasonal Elo never revokes it
  - Tag: `TDD`
  - References: REQ-054
  - Evidence: `GuildCostService` implements the Chapter 2 cost boundary without changing Chapter 1 behavior while the independent `chapter_two_gold_costs_enabled` rollout gate is disabled; reward purchasing and gold charges can therefore be enabled/validated separately. Guild creation reserves exact configured physical raw-gold value from the founder before creation, restores the reservation on definitive creation failure, commits after successful creation, and surfaces uncertain finalization instead of claiming/refunding blindly. New home activation debits canonical guild gold via `GuildGoldService.debitSystem`; a persistence failure attempts a deterministic canonical-gold compensation credit. Existing **active** named-home relocation is never charged again. Preserved Chapter 1 locations migrate inactive for Chapter 2 and must be reactivated once; historical uncompensated Chapter 2 activation payments are credited so cutover testing cannot double-charge a guild. Home #1..N uses configurable `baseCost * scale^(n-1)` with ceiling-to-whole-gold arithmetic; because the approved design supplies no numeric prices, non-positive Chapter 2 prices fail closed rather than inventing gameplay values. `GuildServiceBukkit.getAvailableHomeSlots` now consumes `GuildRewardService` entitlement capacity when Chapter 2 is enabled, preserves legacy level slots only while disabled, and returns zero on unavailable Chapter 2 state instead of granting legacy fallback. Command/localization wiring reports insufficient, unavailable, rejected, compensated, and uncertain payment outcomes. Focused cost/command/locale contracts pass; final offline `test shadowJar`: 1,028 tests, zero failures/errors/skips; Shadow JAR built.
  - Files: `GuildCostService.kt`, `GuildServiceBukkit.kt`, `GuildCommand.kt`, DI/config/localization, cost contracts
- [x] **LG-1207** Guild-creation cooldown â€” 15-day cooldown when a guild is deleted within 7 days of creation (both windows configurable)
  - Tag: `TDD`
  - References: REQ-055
  - Evidence: `GuildCreationHistorySQL` and `GuildRepositorySQLite.addCreated/removeWithCreationCooldown` serialize admission/deletion and commit guild rows with immutable creator history. Service, config and localized command preflight are wired. Ten new contracts plus full regression (987) and MariaDB contracts (35) pass. SPEAR specification and environment-qualified verification: `docs/plans/2026-09-17-creation-cooldown.md`, `docs/plans/2026-09-17-creation-cooldown-verification.md`. Legacy guilds without original creator records receive no inferred penalty.
  - Files: `GuildCreationCooldown.kt`, `GuildCreationHistorySQL.kt`, `GuildRepositorySQLite.kt`, `GuildServiceBukkit.kt`, `GuildCommand.kt`, guild config and SQL/config tests
- [x] **LG-1208** Guild prestige redesign â€” bounded level-100 current-run reset, permanent perk/home choice, eligibility, and atomicity
  - Tag: `DOC`
  - References: REQ-049, REQ-050, REQ-051, REQ-054, REQ-056, REQ-093
  - Evidence: operator-approved replacement design in `docs/superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md`
  - Files: requirements + replacement design; runtime implementation remains disabled by default and follows in a later TDD task
- [x] **LG-1209** Canonical guild-gold pipeline â€” unify personal Vault and physical raw-gold routes with capacity, fees, limits, compensation, and audit
  - Completion: Operator confirmed this task is done on 2026-09-17; supersedes the earlier in-progress checkpoints.
  - Tag: `TDD`
  - References: REQ-009, REQ-054, REQ-092, REQ-093
  - Evidence: PR #143 work includes canonical gold transfers, durable recovery, physical reservation receipts, paid admission and banner purchase identity. Latest local verification (2026-09-16): 922 tests passed, zero failures or skipped tests; shadowJar built successfully. Claims-disabled startup and withdrawal fee messaging fixes committed as `1ca24a1`.
  - Files: guild-gold domain/application service, Vault Economy adapter, physical currency adapter, bank/vault menus and listeners, persistence/audit, config validation
  - Notes: `vault_gold.balance` is authoritative; `bank_mode: BOTH` + physical currency is valid; ordinary vault slots remain independent; missing Vault Economy disables personal transfers only.
  - Historical implementation/review details: `docs/superpowers/plans/2026-09-15-pr143-review.md`, `docs/superpowers/plans/2026-09-15-final-gold-recovery-review.md`, and `docs/superpowers/plans/2026-09-14-durable-war-payments.md`.

- [x] **LG-1210** Executable approved level 1â€“100 reward catalog
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
- [x] **LG-1214** Chapter 2 reward integration and consistent player read models
  - Tag: `TDD`
  - References: REQ-050, REQ-054, REQ-056, REQ-093
  - Evidence: Gold, progression/home/member consumers, Java/Bedrock views and placeholders share the gated consistent read model. Purchase actions use immutable server quotes, separate confirmation screens, live membership/rank checks and the atomic gold/ownership transaction; uncertain retries retain their transaction ID. Migration readiness is now covered end-to-end: actual `ChapterOneToTwoMigrationSQL` output reopens through production pooled SQLite storage and immediately resolves as `GuildRewardRead.Available` with run level 1, migrated permanent home capacity from canonical saved homes, ownership version 0, and no fabricated Chapter 1 purchases/permanent rewards. Missing state still never initializes accounts or falls back to legacy benefits. Rollout defaults disabled. Focused migration/read-model contracts pass; final offline `test shadowJar`: 1,055 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean. Live Paper/Java/Bedrock client validation remains a Sep 28 deployment gate rather than unfinished code.
  - Files: config, gold settings, progression/home/member services, Java/Bedrock menus/placeholders, `ChapterRewardMigrationReadinessTest.kt`
- [x] **LG-1215** Chapter lifecycle persistence foundation â€” durable lifecycle state, standings archive metadata, backup evidence, and migration receipts
  - Tag: `TDD`
  - References: REQ-090, REQ-091; `docs/implementation.md` Â§Chapter 2 Progression
  - Evidence: Schema v30 now creates `chapter_lifecycle`, `chapter_standings_archive`, `chapter_backup_evidence`, `chapter_migrations`, and chapter-scoped `chapter_migration_receipts` through one dialect-aware infrastructure helper used by both `SQLiteMigrations.kt` and `MariaDBMigrations.kt`. `ChapterLifecycleMigrationTest` proved the missing-v30 state red before implementation and green afterward using the established `GuildGoldMigrationTest.kt` pattern (`io.mockk`, `net.kyori.adventure.text.logger.slf4j`, `org.bukkit`, `org.junit.jupiter.api`, `java.nio.file`, `java.sql`). Final offline `test shadowJar`: 1,009 tests, zero failures; Shadow JAR built. REQ-090/091 lifecycle/backup/migration execution remains split into LG-1216..1218.
  - Files: migration v30 schema and SQLite persistence contract
- [x] **LG-1216** Chapter 1â†’2 migration transaction â€” archive standings, initialize Chapter 2 progression/rewards/Elo/home capacity, preserve permanent guild assets
  - Tag: `TDD`
  - References: REQ-091; `docs/implementation.md` Â§Chapter 2 Progression
  - Evidence: `ChapterOneToTwoMigrationSQL` now provides read-only preview plus one atomic cutover transaction. It scopes migration to live `guilds`, reports orphan progression/home rows and level drift, archives deterministic Chapter 1 standings, requires a verified restorable backup and BACKED_UP lifecycle state, resets both `guild_progression.current_level` and `guilds.level`, clears legacy perk cache, initializes `guild_reward_accounts` from saved-home count (minimum 1), seeds target-chapter Elo at 1000, persists per-guild receipts, preserves canonical `vault_gold` and unrelated membership/relation rows, replays completed migration IDs idempotently, and rolls back all guild changes on conflicts. The supplied read-only `D:\\lumaguilds(10).db` is schema v25 with 164 live guilds, 350 progression rows (186 orphaned), 260 home rows (47 orphaned), and home counts 0..6. The copied file was taken without its live WAL/cache state, so the observed `database disk image is malformed` result is treated as an artifact of an incomplete SQLite snapshot rather than evidence of production corruption. The migration intentionally never resurrects orphan child rows and still fails closed on genuinely unreadable source state. `ExperienceAwardRepositorySQL` confirms both live level stores; `net.lumalyte.lg.domain.values.ProgressionCurve` supplies the Chapter 2 level-1 threshold. Focused contracts cover dry-run, atomic apply/replay, backup gating, rollback, zero-guild completion replay, replay after later guild population changes, exact `(migrationId, sourceChapterId, targetChapterId)` identity, and BIGINT historical XP preservation. CodeRabbit hardening moved replay authority to a migration-level completion marker while retaining per-guild receipts as audit detail and fixed operator error interpolation. Final offline `test shadowJar`: 1,017 tests, zero failures/errors/skips; Shadow JAR built.
  - Files: `ChapterOneToTwoMigrationSQL.kt`, lifecycle/reward/rating schema, migration contracts
- [x] **LG-1217** Verified backup and admin recovery â€” backup adapter, status/postpone/retry/force controls, recovery evidence
  - Tag: `TDD`
  - References: REQ-090, REQ-091
  - Evidence: `SQLiteChapterBackupService` creates a consistent live SQLite snapshot with `VACUUM INTO` (including committed WAL state), computes SHA-256/size evidence, copies the snapshot to a restore candidate, runs `PRAGMA integrity_check`, verifies required chapter/guild tables, then atomically records `chapter_backup_evidence` and advances only `FROZEN -> BACKED_UP`. Existing verified backup IDs replay only when the recorded file, size, hash, and restore verification still pass. `ChapterAdminRecoverySQL` provides durable status, SCHEDULED-only postpone, retry metadata clearing without state skipping, and literal-`CONFIRM` force-due semantics that never bypass the persisted lifecycle or verified-backup gate. `/lumaguilds chapter status|backup|postpone|retry|force` is available to console/OP/admin; backup runs asynchronously and stores snapshots under the plugin chapter-backups directory. MariaDB deliberately fails closed for in-plugin backup until a separately verified MariaDB backup adapter exists. Focused contracts cover WAL capture, restore verification, phase gating, idempotent evidence, true-forward-only postpone semantics, retry, force confirmation, durable failure metadata, and concurrent same-ID backup serialization across independent service instances. CodeRabbit hardening adds a shared per-backup-path lock, prevents failed requests from deleting another request's valid snapshot, preserves verification exceptions when restore-copy cleanup also fails, and persists backup command failures to lifecycle `last_error`/`transition_token`. Final offline `test shadowJar`: 1,027 tests, zero failures/errors/skips; Shadow JAR built; locale contracts remain green.
  - Files: `SQLiteChapterBackupService.kt`, `ChapterAdminRecoverySQL.kt`, admin command/localization wiring, backup/recovery contracts
- [x] **LG-1218** Chapter scheduler, rollover, and placeholders â€” timed transition, seasonal reset/prune, chapter name/time remaining
  - Tag: `TDD`
  - References: REQ-090
  - Evidence: `ChapterRolloverCoordinatorSQL` advances one persisted lifecycle phase at a time and `catchUp` resumes from the stored phase after restart. Normal rollover archives standings including seasonal Elo, preserves current-run guild level/XP and permanent state, initializes next-chapter Elo at 1000, prunes old seasonal ratings (and pair guards when present), marks the old chapter COMPLETE, and schedules the next chapter without bypassing the verified-backup gate. Failures persist `last_error` and pause in the current phase. `ChapterRolloverScheduler` is config-gated and disabled by default, bootstraps only from explicit valid UTC chapter settings, runs asynchronously, and uses the verified SQLite backup service from LG-1217. `ChapterReadSQL` supplies read-only chapter ID/name/phase/start/end/time-remaining views; PlaceholderAPI exposes `chapter_id`, `chapter_name`, `chapter_phase`, `chapter_start`, `chapter_end`, and `chapter_time_remaining`. Focused contracts cover not-due behavior, restart catch-up through COMPLETE, preserved run progression, seasonal archive/reset/prune, persisted backup failure, and read formatting. Final offline `test shadowJar`: 1,024 tests, zero failures/errors/skips; Shadow JAR built.
  - Files: `ChapterRolloverCoordinatorSQL.kt`, `ChapterRolloverScheduler.kt`, `ChapterReadSQL.kt`, plugin/config/PlaceholderAPI wiring, rollover/read contracts

## PR-13 â€” Backlog: wars & combat (operator, Fain)

- [x] **LG-1301** War system overhaul â€” accurate kill tracking with measurable gameplay impact: per-guild war kill counter (opposing-guild kills only, persisted, reset on war end) driving resolution at `war_kill_win_target` (default 25), surfaced in `/g info` + war menus
  - Tag: `TDD`
  - References: REQ-057
  - Evidence: `WarService.recordOpposingGuildKill` is the synchronized gameplay boundary for kill progress: it accepts only an active war and the exact declaring/defending guild pair, increments the killer-side counter plus opposing deaths with overflow-safe arithmetic, persists the existing durable `WarStats` inside the revision-checked war record, and resolves the war through the normal `endWar` path when the configured global target is reached. `combat.war_kill_win_target` defaults to 25 and nonpositive values fail closed. The Bukkit death listener delegates all kill-state mutation to this service; anti-farming suppresses XP only and does not erase a legitimate opposing-guild kill. Restart coverage records a 4â€“2 score through the production API, recreates the service, and recovers the counters. End/reset semantics preserve final per-war statistics for history/audit while ended wars reject further kills, disappear from active progress, and a later war between the same guilds starts at 0â€“0. Java and Bedrock `/g info` expose active per-opponent `kills/target`; Java and Bedrock war details expose both sides' progress, and the Java active-war list includes the target. Focused war/restart/locale contracts pass; final offline `test shadowJar`: 1,060 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean.
  - Files: `WarService.kt`, `WarServiceBukkit.kt`, `WarKillTrackingListener.kt`, combat config, Java/Bedrock guild-info and war menus, locale and restart/config contracts
- [~] **LG-1302** World War â€” DEFERRED / design pending
  - Tag: `TDD`
  - References: REQ-058
  - Evidence: Tabled by project owner before implementation. The current secret-predicate/all-guild forced-war design does not fit the desired player-driven war loop; retain the requirement for future redesign rather than implementing it as written.
  - Files: none until design is revisited
- [x] **LG-1303** War banners â€” deployable tactical teleport banner: 15 min, destructible, raw-gold cost, 1 active/guild, cooldown, rank-permission gated, broadcast on placement
  - Tag: `TDD`
  - References: REQ-059
  - Evidence: `/g warbanner` issues a guild-bound deployable item only to current members with the new `PLACE_WAR_BANNER` rank permission while their guild has an active war; placement re-validates membership, permission and active-war state so stale/traded items cannot bypass authorization. The item and placed block use the guild's exact persisted banner base color + ordered patterns, with a plain white banner fallback when no design exists, and placement re-reads the current guild design so an old item cannot deploy stale heraldry. Successful placement reserves/commits a configurable physical RAW_GOLD cost (default 64), persists one active banner per guild in schema v32, starts a 15-minute lifetime/cooldown, and globally broadcasts `[Guild Name] has placed down a war banner.`. Render/payment failures compensate both durable state and gold when non-consumption is proven; ambiguous payment keeps the deployed state to prevent duplicate charging. Any player's break overrides prior build-protection cancellation, suppresses drops, deactivates the tactical point, and explosions also destroy it. `/g warbanner tp` is member-gated and goes directly through the existing combat/movement-aware teleport countdown without consuming guild-home slots or a teleport-request path. Active/cooldown state survives restart; expiry is persisted/deactivated and the scheduler removes the matching physical block. Existing ranks are migrated idempotently: every current `DECLARE_WAR` rank plus each guild's highest-priority rank receives `PLACE_WAR_BANNER`; new Owner, Co-Owner and Admin defaults include it. Focused payment/persistence/visual/destruction/config/migration/DI/locale contracts pass. Final offline `test shadowJar`: 1,083 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean.
  - Files: `WarBannerState`, `WarBannerService`, `WarBannerRepositorySQL`, v32 `WarBannerSchema`, Bukkit renderer/teleport adapter, placement/break/explosion listener, `GuildCommand`, rank permission/config/localization/DI wiring, focused contracts
- [x] **LG-1304** Better war notifications â€” prominent declaration alert, persisted unread notices replayed on login, victory/loss broadcasts
  - Tag: `TDD`
  - References: REQ-060
  - Evidence: Java and Bedrock/Geyser clients both attempt the same ephemeral PacketEvents advancement toast with the opposing guild banner icon; title + sound is retained only when PacketEvents toast delivery is unavailable. War lifecycle notices persist per-player in schema v33, replay once on login, and resolved wars broadcast the winner/loser globally. Persistence tests prove unread notices survive restart, duplicate deterministic IDs are ignored, and delivered notices cannot replay again.
  - Files: `WarNotificationServiceBukkit`, `PacketEventsToastSender`, `WarNotificationRepositorySQL`, v33 `WarNotificationSchema`, `PlayerSessionListener`, war lifecycle hooks, localization/DI wiring, focused persistence + migration contracts
  - Notes: replay transitions each successfully presented notice to delivered, so subsequent logins do not replay it
- [~] **LG-1305** Customizable war win conditions â€” DEFERRED / design pending
  - Tag: `TDD`
  - References: REQ-061
  - Evidence: Tabled by project owner before implementation. The bundled custom kill targets, ransom/surrender, Champion duel mode, and high-stakes XP rules do not fit the current guild-war direction cleanly enough to ship as one feature.
  - Files: none until the war-objective design is revisited

## PR-14 â€” Backlog: chat & communication (operator, Fain)

- [x] **LG-1401** Login notifications â€” in-game alert when a guild member logs in
  - Tag: `TDD`
  - References: REQ-062
  - Evidence: Online guildmates receive a TASK-style advancement toast with the joining member's player head on Java and Bedrock/Geyser via the shared PacketEvents toast sender. The joining player is never notified about themselves; offline recipients and opted-out recipients are skipped; recipients shared across multiple guild memberships are deduplicated. A 15-second plugin-start grace window suppresses restart reconnect waves and a 60-second per-player reconnect debounce suppresses rapid relog spam. If toast delivery is unavailable, recipients receive a quiet action-bar + chime fallback. `/g notifications on|off|toggle` persists the recipient preference (default on) in schema v34 for SQLite/MariaDB. Login presence itself is transient and is never persisted/replayed. Preferences are batch-loaded for online recipients. Full `test shadowJar`: 1,092 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean.
  - Files: `GuildLoginNotificationServiceBukkit`, `PlayerNotificationPreferenceRepositorySQL`, v34 `PlayerNotificationPreferenceSchema`, generic `PacketEventsToastSender`, `PlayerSessionListener`, `GuildCommand`, localization/DI wiring, focused behavioral/persistence/migration contracts
  - Harvest: historical announcement persistence was reviewed but intentionally not reused because login presence is transient rather than durable announcement data
- [x] **LG-1402** Rank prefixes in guild chat â€” member's rank shown next to name (legacy restore)
  - Tag: `TDD`
  - References: REQ-063
  - Evidence: LumaGuilds' RoseChat GUILD channel decorates its existing chat/shout format at load time so the live `%lumaguilds_guild_rank%` value appears immediately before RoseChat's player token while preserving any existing global/LuckPerms prefix. The decorator is idempotent (operator formats that already include the guild-rank placeholder are untouched), supports RoseChat `{player}` and direct PlaceholderAPI player-name tokens, and accepts a per-channel `guild-rank-format` override using `<rank>`. ALLY/MODCHAT formats are intentionally unchanged. The existing PAPI rank placeholder resolves membership/rank state on every request, proven by a regression contract that changes the mocked member from Owner to Officer between consecutive resolutions. Full `test shadowJar`: 1,101 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean.
  - Files: `LumaGuildsChannel`, `GuildRankChatFormatter`, live-rank PlaceholderAPI regression contract, RoseChat channel-format integration tests
- [ ] **LG-1403** Guild admin chat â€” dedicated private channel for admins/leadership
  - Tag: `TDD`
  - References: REQ-064
  - Evidence:
  - Files: chat channel registry, permission gate
- [ ] **LG-1404** Custom guild channels â€” guilds create/name own chat channels (pending RoseChat feasibility)
  - Tag: `TDD`
  - References: REQ-065
  - Evidence:
  - Files: channel CRUD, RoseChat integration

## PR-15 â€” Backlog: QoL, UI & Discord integration (operator, Fain)

- [x] **LG-1501** Guild Statistics node completion â€” durable internal invitation tracker and all-time most-invites-per-member leaderboard
  - Tag: `TDD`
  - References: REQ-066
  - Evidence: successful pending-invitation creation and immutable history append commit atomically; duplicate/failed writes do not inflate counts and persistence failures no longer produce false success notifications. Schema v37 adds indexed `guild_invitation_history` for SQLite/MariaDB and the SQLiteâ†’Maria migration utility. `InvitationStatisticsService` exposes bounded per-guild totals/leaderboards with count-descending + inviter-UUID tie ordering. Java Statistics shows a top-3 card plus a database-backed 10-entry paginated detail view; Bedrock shows total + five inviters per selectable page from the same service, resolving current player names only at render time. Focused repository/migration/service/wiring contracts cover history durability, rollback, duplicate handling, deterministic aggregation, both UI surfaces, and fail-closed invite confirmation.
  - Files: `GuildInvitationRepository`, `GuildInvitationRepositorySQLite`, `InvitationStatisticsService`, `InvitationStatisticsSchema`, `GuildStatisticsMenu`, `BedrockGuildStatisticsMenu`, Java/Bedrock invite confirmation menus, localization, migrations/tests
  - Harvest: closed PR #7 was reviewed; its pending-invitation entity/repository work was already present in current code, while its `InvitationService` was interface-only and had no durable sent-invite analytics to reuse.
- [x] **LG-1502** Dynamic spawn banners â€” placeable persistent physical banners track configured guild leaderboard ranks
  - Tag: `TDD`
  - References: REQ-067
  - Evidence: `/lumaguilds spawnbanner <rank> <category>` creates a white PDC-bound admin item; placement persists rank/category/location in schema v38 and renders the matching guild's current physical banner while preserving wall/standing orientation. Missing rank/guild/banner data falls back to white. `GuildLeaderboardRankChangeEvent` and `GuildBannerSetEvent` trigger immediate refresh, with a 60-second reconciliation safety pass; breaking a bound banner unregisters it and returns the configured white display item. Admin `refresh` and `list` controls are included. Focused category, repository, migration, wiring, and locale contracts are green; full `test shadowJar` validation passes 1,153 tests with zero failures/errors/skips and `git diff --check` is clean.
  - Files: `SpawnBannerCategory`, `SpawnBannerRepository`, `SpawnBannerRepositorySQL`, `SpawnBannerSchema`, `SpawnBannerServiceBukkit`, `SpawnBannerListener`, `LumaGuildsCommand`, `PluginKeys`, DI/startup/shutdown wiring, localization, migrations/tests
- [x] **LG-1503** Guild list GUI & leaderboards â€” all guilds, paged at the service boundary, 4 deterministic sort modes (all-time active, weekly active weighted by unique PvP kills, level lowâ†’high, creation oldâ†’new, ties â†’ name â†’ creation)
  - Tag: `TDD`
  - References: REQ-068
  - Evidence: `/g list` now opens a dedicated Java/Bedrock guild directory backed by `GuildListService`; both surfaces request one bounded page and never call `GuildLookup.getAllGuilds()` or slice an unbounded list. SQL owns `LIMIT/OFFSET`, total count, primary ordering, and stable nameâ†’creationâ†’UUID tie-breaking. All-Time Active reuses weighted progression activity across history; Weekly Active uses a trailing seven-day window, excludes raw `PLAYER_KILL` XP, and adds only distinct opposing victims weighted by configured `activity.weights.kills_this_week`. Level and creation sorts default ascending. `guild_list.page_size` defaults to 18 and is clamped to the Java inventory capacity of 36. Focused SQL/service/wiring/locale tests are green; full `test shadowJar` validation passes 1,163 tests with zero failures/errors/skips and `git diff --check` is clean.
  - Files: `GuildListSortKey`, `GuildListRepository`, `GuildListRepositorySQL`, `GuildListService`, Java/Bedrock `GuildListMenu`, `MenuFactory`, `GuildCommand`, config/localization/tests
  - Notes: LG-1504 remains responsible for replacing the temporary book renderer with each guild's physical banner; LG-1503 intentionally does not consume that scope.
- [x] **LG-1504** Guild banners in list â€” physical banner shown per guild, plain white default when unset
  - Tag: `TDD`
  - References: REQ-069
  - Evidence: Java `/g list` entries now render through shared `GuildBannerItemResolver` instead of the temporary book icon. Valid serialized standing banners retain their physical material/base color and ordered pattern layers; missing, corrupt, non-banner, or wall-banner payloads resolve to `WHITE_BANNER`. The existing Java ally/enemy relation browser now uses the same resolver so list surfaces cannot drift on fallback behavior. Bedrock's SimpleForm directory remains behaviorally unchanged because it has no Minecraft `ItemStack` rendering surface. Focused resolver/wiring/locale contracts are green; full `test shadowJar` validation passes 1,167 tests with zero failures/errors/skips and `git diff --check` is clean.
  - Files: `GuildBannerItemResolver`, `GuildListMenu`, `GuildRelationBrowserMenu`, resolver/wiring tests
- [x] **LG-1505** Expandable Enemy/Ally lists in `/g info` â€” full guild list beyond top 3
  - Tag: `TDD`
  - References: REQ-070
  - Evidence: Java `/g info` keeps the compact three-guild Allies/Enemies preview but the cards now open read-only full browsers with 28 guilds per page, actual guild banner items (plain white fallback), stable case-insensitive name ordering with UUID tie-breaks, member/level/mode details, and click-through into the selected guild's info. Bedrock `/g info` now resolves real active relations instead of the previous hardcoded "None" placeholder and exposes native Allies/Enemies buttons backed by 12-entry paged SimpleForms. A shared resolver filters inactive/pending relations and disbanded guilds, deduplicates stale duplicate rows, and is used by both platforms so counts/order cannot diverge. Focused contracts cover filtering, deterministic ordering, Java/Bedrock menu routing, info-menu wiring, localization, and the 29-guild pagination regression. Full `test shadowJar`: 1,109 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean.
  - Files: `GuildInfoRelationResolver`, `GuildInfoMenu`, `GuildRelationBrowserMenu`, `BedrockGuildInfoMenu`, `BedrockGuildRelationBrowserMenu`, `MenuFactory`, locale keys, resolver/factory/wiring contracts
- [x] **LG-1506** Dynamic Discord roles â€” guild creation auto-creates/links a Discord role, grants/removes on membership and Discord link changes
  - Tag: `TDD`
  - References: REQ-071
  - Evidence: DiscordSRV is an optional soft dependency, but when available the integration is enabled by default and every guild receives a durable managed Discord role immediately on creation (no progression level/perk gate). Existing guilds reconcile on startup; persisted role IDs prevent duplicate creation and a manually deleted role is recreated/relinked. Discord-linked members receive the role on guild creation/join and lose it on leave/kick; DiscordSRV account link/unlink events also grant/revoke dynamically, with unlink using the event's captured Discord ID so removal still works after the mapping disappears. Guild renames update the role name, disband deletes the Discord role, orphan links are garbage-collected, and failed persistence compensates by deleting newly created untracked roles. Schema v35 stores guildâ†’role links for SQLite/MariaDB. Focused contracts cover creation-time availability, membership sync, late link/unlink, restart persistence, concurrency, compensation, config, and migration repair. Full `test shadowJar`: 1,125 tests, zero failures/errors/skips; Shadow JAR built; `git diff --check` clean.
  - Files: DiscordSRV gateway/account-link subscription, `GuildDiscordRoleService`, `GuildDiscordRoleListener`, durable role-link repository/schema, config, migrations, lifecycle tests
  - Follow-up (2026-10-01): Gate creation/recreation at `discord.guild_roles.minimum_level` (default 50). Startup and periodic reconciliation automatically remove managed roles and saved links for guilds below the threshold with no completed prestige, including earlier test roles; failed deletion retains the link for retry. Completed prestige permanently qualifies the guild to keep/create/repair its role after the level reset. Failed eligibility reads preserve roles/links. The original creation-time behavior above is historical evidence.
- [x] **LG-1507** Enhanced guild descriptions â€” Discord invite links embeddable in guild description
  - Tag: `TDD`
  - References: REQ-072
  - Evidence: Shared `GuildDescriptionContent` now owns the 200-character invariant, restricted MiniMessage parsing, Discord invite detection, and safe rendering. `discord.gg` and `discord.com/invite` HTTPS URLs become `OPEN_URL` components while unrelated URLs remain inert. User-authored `click`, `hover`, and `insertion` tags are rejected by the command, Java editor, both Bedrock description/settings editors, and again at the service boundary. Java guild info and settings render through the shared component policy; guild info sends invite-bearing descriptions into chat on click so the URL is actionable. Bedrock renders formatting-stripped text while preserving the visible invite URL. The service now correctly requires `MANAGE_DESCRIPTION` rather than the legacy `MANAGE_EMOJI` check. Focused content/wiring/locale contracts are green; full `test shadowJar` validation passes 1,176 tests with zero failures/errors/skips and `git diff --check` is clean.
  - Files: `GuildDescriptionContent`, `Guild`, `GuildServiceBukkit`, `GuildCommand`, Java description editor/settings/info, Bedrock description editor/settings/info, localization/tests
- [x] **LG-1508** Disband announcements â€” global chat broadcast plus ally/enemy relationship toasts
  - Tag: `TDD`
  - References: REQ-073
  - Evidence: `GuildServiceBukkit` snapshots active ALLY/ENEMY relations before the committed disband removes relation rows, then attaches that immutable snapshot to the existing three-argument `GuildDisbandedEvent` without breaking its public constructor contract. `GuildDisbandAnnouncementServiceBukkit` broadcasts one localized server-wide chat message and sends relationship-aware advancement toasts to online members of pre-disband allied/enemy guilds. Recipients are deduplicated across memberships, former members are excluded from relationship toasts, and ENEMY wins if an anomalous player is reachable through both relation types. The delivery path is edition-agnostic and therefore reaches Geyser/Bedrock through the same advancement packet; ally/enemy + guild name are kept in the first toast line because Geyser may omit the second line. Failed toast delivery falls back to an action bar while the global chat announcement remains visible. Focused announcement/wiring/event-API/locale contracts are green; full `test shadowJar` validation passes 1,184 tests with zero failures/errors/skips and `git diff --check` is clean.
  - Files: `GuildDisbandedEvent`, guild disband snapshot path, `GuildDisbandAnnouncementService`, Bukkit announcement service, disband listener/DI, localization/tests
  - Harvest: persistent guild announcement content from closed PR #7 remains separate and is not required for this lifecycle notification.

---

## PR-16 â€” Weekly Guild Quests (Chapter 2)

> **Future network architecture:** `docs/plans/2026-09-20-network-guild-federation-design.md` records the approved direction for separate per-gamemode LumaGuilds instances, local player membership/progression, optional network-guild federation, namespaced Nexo/AuraSkills-capable quest providers, and explicitly network-scoped quests/events. Federation itself is future scope and is not required to complete PR-16.

> Part of the Chapter 2 progression overhaul. Builds on the XP infrastructure in PR-12/LG-1201. Every quest, full-set bonus, and leaderboard Guild EXP payout passes through `QuestRewardSinkBukkit` to `ProgressionService.awardUncappedSystemExperience(guildId, amount, ExperienceSource.WEEKLY_ACTIVITY)`. The permanent award repository records progression and audit rows atomically without creating or consuming a source-cap usage row.
>
> **Procedural-generation contract:** Operators configure generation policy, not an authored quest catalog. Each reset independently composes action + provider-owned namespaced target + sane rounded amount + zero or more compatible conditions from runtime-discovered content. Vanilla targets come from Bukkit/Paper runtime registries/recipes/semantics; optional providers (currently Nexo) contribute custom content without changing generator code.
>
> **Chaos/sanity rule:** difficult, expensive, strange, and conflict-driving quests are intentional; mathematically absurd quests are not. Magnitude derives from action/target rarity classes with small exceptional rules rather than a material-by-material whitelist. Recent exact quests and recent action+target pairs are rejected for configured cooldown windows.
>
> **Claims-disabled constraint (EnthusiaSMP):** Claims are disabled on the current SMP, but ordinary mining/placement quest progress is not claims functionality and remains available. Claims-specific providers/actions may register only on claims-enabled gamemodes.
>
> **Spatial-event intent:** X/Z corridor conditions deliberately bias some activity toward the player-built X=0/Z=0 highway network. Event context carries coordinates only for actions where location is meaningful.
>
> **Future network boundary:** PR-16 quests are local to one gamemode/LumaGuilds instance. Network Guild federation may later aggregate explicitly network-scoped objectives, but local weekly quest progress never implicitly crosses gamemodes. See `docs/plans/2026-09-20-network-guild-federation-design.md`.

- [x] **LG-1601** Procedural quest domain and generator â€” namespaced targets, independent action/target/amount/condition rolls, semantic validation, deterministic bounded generation, history fingerprints, and human-rounded magnitude-aware amounts; domain layer remains Bukkit-free.
  - Tag: `TDD`
  - References: REQ-074, REQ-075, REQ-081
  - Evidence: `QuestGeneratorTest` / `QuestGenerationValidatorTest` cover deterministic sets, current-set uniqueness, recent action-target rejection, axis corridors, structured failures, rounded amounts, and precious-vs-bulk magnitude bounds. `QuestAction` already maps directly to `ExperienceSource`.
  - Files: `domain/values/QuestAction.kt`, `domain/entities/QuestDefinition.kt`, `domain/services/QuestGenerator.kt`, `QuestGenerationValidator.kt`, `QuestAmountPolicy.kt`, `QuestTargetProvider.kt`

- [x] **LG-1602** Quest persistence and schema ownership â€” active/history sets plus per-guild progress/claim/bonus/payout state live behind `QuestRepository`; SQLite and MariaDB migration chains own quest schema v36 and repository construction performs no DDL.
  - Tag: `TDD`
  - References: REQ-074, REQ-077, REQ-080
  - Evidence: `QuestRepositorySQLiteTest` covers restart persistence, claim-preserving upserts and idempotent markers; `QuestSchemaMigrationTest` proves v36 creates the quest tables and generated-target metadata/order columns. Repository write SQL is backend-aware for SQLite/MariaDB.
  - Files: `application/persistence/QuestRepository.kt`, `infrastructure/persistence/guilds/QuestRepositorySQLite.kt`, `infrastructure/persistence/migrations/QuestSchema.kt`, `SQLiteMigrations.kt`, `MariaDBMigrations.kt`

- [x] **LG-1603** Runtime target discovery and generation-policy config â€” ordinary operation requires no authored quest definitions. Bukkit/Paper discovers vanilla block/crop/entity/recipe/enchant targets; Nexo contributes custom blocks/items through the same provider contract. Config controls reset/rewards, condition probabilities, repeat cooldowns, and coordinate generation policy.
  - Tag: `TDD`
  - References: REQ-074, REQ-075
  - Evidence: shipped `progression.yml` contains generation policy only and enables weekly quests; `BukkitQuestTargetProvider`, `NexoQuestTargetProvider`, and `QuestTargetCatalog` supply sorted provider-owned targets without a giant whitelist.
  - Files: `config/QuestGenerationConfig.kt`, `infrastructure/services/BukkitQuestTargetProvider.kt`, `NexoQuestTargetProvider.kt`, `ProgressionConfigService.kt`, `progression.yml`

- [x] **LG-1604** Quest progress listener and provider identity bridge â€” qualifying Bukkit/domain events increment matching active quests using namespaced target IDs and event context including coordinates, dimension/biome, tool/transport, Elytra state, and block provenance. Nexo custom blocks/items retain custom identity instead of collapsing to vanilla backing types.
  - Tag: `TDD`
  - References: REQ-075, REQ-076
  - Evidence: kill, break/harvest/place, craft, smelt, fish, enchant, guild-bank and war-win paths are wired with cancellation/game-mode gates; X/Z corridor conditions are evaluated by `QuestService`. Ordinary block quest handlers intentionally remain registered when claims are disabled.
  - Files: `infrastructure/listeners/QuestProgressListener.kt`, `application/services/QuestService.kt`

- [x] **LG-1605** Quest lifecycle service â€” weekly rotation (default Monday 00:00 UTC), deterministic generation from a stable week seed, startup catch-up, active/history persistence, guild aggregation, and recent-history rejection.
  - Tag: `TDD`
  - References: REQ-074
  - Evidence: `WeeklyQuestCoordinator` discovers/sorts provider targets, reads recent persisted sets for cooldown enforcement, and retains the active set across restart rather than regenerating it.
  - Files: `application/services/QuestService.kt`, `infrastructure/services/WeeklyQuestCoordinator.kt`

- [x] **LG-1606** Quest reward delivery â€” claim flow awards Guild EXP via the uncapped `WEEKLY_ACTIVITY` system pipeline plus optional item rewards; claim, full-set bonus, and leaderboard recipient markers remain idempotent.
  - Tag: `TDD`
  - References: REQ-077
  - Evidence: claim-once persistence, claim-gated full-set bonus, weekly activity XP, namespaced item reward round-trip, stack splitting, inventory overflow drops, and payout-before-cleanup remain covered.
  - Files: reward delivery in `QuestService`, `QuestRewardSinkBukkit`

- [x] **LG-1607** Quest menu UI and dynamic rendering â€” Java ChestGUI and dedicated Bedrock Cumulus forms display the persisted generated weekly quest set without requiring one language key per generated quest.
  - Tag: `TDD`
  - References: REQ-078, REQ-111
  - Evidence: `QuestDisplayFormatter` serves both editions. `BedrockGuildQuestsMenu` now renders six quests per page with human objective text, progress, Guild EXP/item rewards, leaderboard rank, claim state, reset timer and full-set bonus state; claims use the existing idempotent `QuestService` on the server thread. MenuFactory no longer returns the unavailable placeholder for Bedrock. Focused routing/wiring/locale tests and the full 1,357-test suite are green.
  - Files: `interaction/menus/guild/GuildQuestsMenu.kt`, `interaction/menus/bedrock/BedrockGuildQuestsMenu.kt`, `utils/QuestDisplayFormatter.kt`, MenuFactory/localization/tests

- [x] **LG-1608** Read-only localization/placeholders â€” all surrounding player-facing quest UI uses `LangService`; generated components are dynamically formatted, while PlaceholderAPI exposes read-only timer/definition/progress/reward/bonus state.
  - Tag: `INFRA`
  - References: REQ-078, REQ-079
  - Evidence: menu and PAPI adapters consume the persisted active set and never generate/reset/claim/reward from placeholder evaluation.
  - Files: `lang/en_US.yml`, `infrastructure/placeholders/LumaGuildsExpansion.kt`, `utils/QuestDisplayFormatter.kt`

- [x] **Claims-disabled vault startup regression (REQ-015):** Vault claim lookup is optional; claims-enabled placement remains fail-closed. Both real startup graphs pass, and the full test suite plus shadowJar build pass.
- [x] **Withdrawal fee messaging (REQ-015):** Quick withdrawal buttons preview actual capped fees and total deduction; successful physical and personal-account withdrawals report destination, fee and total. Regression test and full suite pass; shadowJar rebuilt.

## Season 2 UI redesign â€” local staging

- [~] **LG-S2-UI** Audit all menu presentations, reconcile Nexo definitions and unify navigation/progression artwork.
  - Tag: `INFRA`
  - References: REQ-087, REQ-094, REQ-095; `docs/implementation.md` Â§Layer Dependency Rules; user handoff 2026-09-24.
  - Evidence: implementation and staging deployment are complete. Full `test shadowJar` passes 1,310 tests with 0 failures / 0 errors / 3 skips; `git diff --check` is clean; Semgrep `p/kotlin` ran 9 rules over 68 tracked guild-menu files with 0 findings. LumaGuilds 2.1.0 and Nexo 1.22.1 enable cleanly on Leaf 1.21.11 / Java 21. A first human Java-client walkthrough was completed on 2026-09-24 and found the presentation broadly improved, especially Progression, while identifying the follow-up work documented below; the overall Season 2 UI task remains partial until those findings are resolved and rechecked.
  - Follow-up validation 2026-09-25: full test + shadowJar passes 1,342 tests with 0 failures / 0 errors / 3 skips; git diff check is clean; Semgrep p/kotlin ran 9 rules over 69 guild-menu targets with 0 findings. Staging JAR SHA-256 512D5734AD6DDC9E8E7E259DD67B6CEAB535B1C68502AC30A08A709257EAA3C6. SELFHOST pack SHA-256 60814E1CB04812B4395D6928536C23EA5C9A1167DE611D77345AEEEBB4E58A21 (915,141 bytes). Human second-pass visual sign-off remains pending.

- [x] **LG-S2-LAYOUT** Separate progression content from sidebar and navigation.
  - Tag: `TDD`
  - References: REQ-094; `docs/implementation.md` Â§Layer Dependency Rules.
  - Evidence: `GuildProgressionLayoutTest` locks the 24 source slots (11â€“16, 20â€“25, 29â€“34, 38â€“43) and prevents collisions with header/sidebar/navigation regions. Full suite green.
- [x] **LG-S2-THEME** One-row GUI theme selector and six themed glyph backgrounds.
  - Tag: `TDD`
  - References: REQ-096, REQ-099.
  - Evidence: Settings â†’ GUI Theme is a one-row selector with six theme choices at slots 0â€“5 and Back at slot 8, permission-gated mutation, current-theme cue, and localized light title. `MenuTitleBuilderTest` covers 1/3/4/5/6-row glyph names for all six themes. Runtime glyphs `guild_bg_<theme>_1_row` use unique U+A018..U+A01D codepoints; all six codepoints are present in the generated pack JSON.
- [x] **LG-S2-PROGRESSION-ICONS** Exhaustive ExperienceSource and pool-aware progression presentation.
  - Tag: `TDD`
  - References: REQ-097.
  - Evidence: every `ExperienceSource` maps explicitly; ORE/CRAFTING pools select semantic pool art; gift artwork is no longer a catch-all. `GuildProgressionIconMappingTest` and the full suite are green.
- [x] **LG-S2-BANK-I18N** Repair Guild Bank literal/computed language lookups and compact layout.
  - Tag: `TDD`
  - References: REQ-098, REQ-100.
  - Evidence: all previously unresolved bank paths are declared, finite quick-action keys are covered by `LocaleContractTest`, dynamic transaction labels reuse declared history keys, and the Java bank uses the approved 4-row / 36-slot shell. Bank localization/layout/runtime contracts and full suite are green.
- [x] **LG-S2-PACK** Install second-pass backgrounds/icons/swatches into local Nexo, audit identifiers, regenerate SELFHOST pack.
  - Tag: `INFRA`
  - References: REQ-095, REQ-099.
  - Evidence: staging-only Nexo remains `SELFHOST`. 26 new item CMD values 733213â€“733238 each occur exactly once; six new glyph chars U+A018â€“U+A01D each occur exactly once; there are zero duplicate item IDs or glyph IDs. Approved 512Ã—512 art remains the master source, while all 60 Nexo/package GUI runtime copies were downscaled to 256Ã—256 to satisfy Nexo's bitmap validator. Clean restart generated `plugins/Nexo/pack/pack.zip` without oversized/placeholder warnings; SHA-256 `17CED3AFD257F80C7EF2209B3646D2C6543B9D969C426AD264E9F7F5983964CC`.
  - Deployment: `plugins/LumaGuilds-Season2.jar` SHA-256 `B695D28BC44DDEEB6C8A54E76876624B1CBDB76257255E862386F86483ADE903`; previous JAR backed up under `_staging_backups/season2-ui-deploy-20260924-194304`. Resource pre-overwrite backup: `_staging_backups/season2-ui-secondpass-20260924-190910`.
- [x] **LG-S2-MENUS** Apply approved dashboard/economy/settings/diplomacy/warfare/member layout family without behavior loss.
  - Tag: `TDD`
  - References: REQ-087, REQ-100.
  - Evidence: dashboard keeps all ten sections with Statistics under Economy and uses the guild's real stored banner when available; Settings is regrouped into identity / appearance / access-location; existing segmented diplomacy/warfare flows and member/rank state/paging are preserved. Layout contracts and full suite are green.
  - Runtime note: a Java client connected successfully after the clean pack rebuild. Bedrock remains explicitly unverified because this staging runtime reports Floodgate/Cumulus classes absent.

## Season 2 Java visual-audit follow-up â€” 2026-09-24

> Source: live Java-client walkthrough on local staging after the second-pass pack/JAR deployment. The walkthrough used an imported guild database, including temporary override/join testing with Vibe and Test. Treat data anomalies as findings to reproduce against authoritative live data before deciding whether they are migration artifacts or runtime defects.
>
> Positive sign-off from the walkthrough: Members looked good; core Quests behavior appeared to work; Progression was specifically called out as clear, intuitive and visually successful; Diplomacy looked good; the Warfare/Party shell looked good. These areas still participate in regression testing but do not need presentation rewrites solely from this audit.

- [x] **LG-S2-DASHBOARD-DATA** Make Dashboard summary authoritative and more useful at a glance.
  - Tag: `TDD`
  - References: REQ-101.
  - Finding: Vibe displayed 23 members / rank 4 / balance 0 on the dashboard while Economy reported guild balance 850. Reproduce and make the dashboard consume the same canonical balance/data source as Economy/Bank. Review which additional high-value guild facts fit the summary without adding clutter.
  - Follow-up evidence: Dashboard now reads the canonical BankService.getBalance(guild.id) value used by Economy/Bank; the stale guild.bankBalance path is contract-tested out. Full suite is green.

- [~] **LG-S2-INFO-DISBAND-INTEGRITY** Reproduce Information access behavior and eliminate stale disband remnants.
  - Tag: `TDD`, `MIGRATION`
  - References: REQ-102.
  - Finding: Information appeared inert while temporarily overridden into Vibe but opened after joining Test, so permission/override behavior needs diagnosis rather than assuming a rendering bug. Test also remained joinable despite having been previously disbanded and showed an unknown owner; the unknown owner could be the former owner UUID. Verify disband persistence cleanup and add pre-cutover detection/reporting for orphaned guild/owner rows.
  - Follow-up evidence: Guild Info re-resolves the canonical guild before rendering and closes cleanly if the guild no longer exists. Chapter 1 to 2 readiness preview now reports orphan members/ranks/relations, invalid member-rank links and ownerless guilds while remaining compatible with older Chapter 1 schemas. Root-cause/live-data validation of the observed Test guild remains pending.

- [x] **LG-S2-RANK-LORE** Localize and compress rank/perk hover content.
  - Tag: `TDD`, `UI`
  - References: REQ-103.
  - Finding: several rank/perk entries expose missing language keys and legacy descriptions are long enough to run off practical tooltip space. Preserve behavior while rewriting to concise, scannable lore and add localization coverage for every displayed perk.
  - Follow-up evidence: Rank cards now show a bounded six-permission preview plus total/overflow count instead of unbounded category lore; all dynamic rank-permission localization keys are contract-tested.

- [x] **LG-S2-QUEST-UX** Make generated weekly quests read like player objectives and expand the weekly set to six.
  - Tag: `TDD`, `UI`
  - References: REQ-104, REQ-074..REQ-078.
  - Findings/examples: `Fish Any` / `100 any above Y96` should render as a natural objective such as `Catch any fish`; `Enchant Items Stone Spear` / `90 stone spear within 50 blocks of Z0` reads like generator output and the highway condition is not meaningful for enchanting; `Deposit Bank Coins` must describe the server's actual currency as Gold Ore and show the amount as Gold Ore, not generic coins. Revisit condition/action compatibility and reshape the menu for six weekly quests instead of three.
  - Follow-up evidence: shipped/default weekly quest count is six with a centered six-card grid; procedural wording now renders player objectives such as Catch Any Fish, Enchant 90 Stone Spears, and Deposit 30,000 Gold Ore to the Guild Bank; crafting/smelting/enchanting no longer receive highway coordinate conditions.

- [~] **LG-S2-BANK-UX-FUNCTIONALITY** Keep the bank feature depth but make the entire flow coherent, localized and operational.
  - Tag: `TDD`, `UI`
  - References: REQ-010..REQ-012, REQ-036, REQ-105.
  - Findings: Transaction History opens deeper statistics/filter controls with missing language keys and apparently inert actions; Automation exposes scheduled deposits, auto rewards, alerts, recurring payments and status/configuration information but several setup controls are still `coming soon`; Bank Statistics and Member Contributions need verified real data. Preserve the intended information/functionality, add purpose-specific custom icons, simplify navigation/labels, and ensure every visible control works or is explicitly read-only. The next-interest/accrual timer should tick from real persisted schedule state rather than behave like static text.
  - Follow-up evidence: missing bank filter/budget localization is repaired; false coming-soon automation clicks were removed or converted to honest read-only state, Budget Alerts opens its real menu, next interest accrual ticks live, and stale-menu art hooks are wired with vanilla fallbacks. Live-data/member-contribution verification remains pending; this staging boot also reports no Vault economy provider.

- [x] **LG-S2-LUNAR-DEFAULT** Disable Lunar tracking by default.
  - Tag: `TDD`, `CONFIG`
  - References: REQ-106.
  - Acceptance: new guild/default settings initialize Lunar tracking off; explicit persisted opt-ins remain stable unless migration policy intentionally changes them.
  - Follow-up evidence: new/default Guild.trackingEnabled is false and covered by GuildSeason2DefaultsTest; existing persisted values are not rewritten.

- [x] **LG-S2-PRESTIGE-RELEASE** Finish and expose the real level-100 Prestige flow for Chapter 2.
  - Tag: `TDD`, `RELEASE`
  - References: REQ-093, REQ-107.
  - Finding: current Progression UI says Prestige requires level 25 and `coming in a future update`; correct eligibility is level 100. Remove the placeholder copy and validate the full prestige transaction/reset/reward path for release while retaining the explicit operator enable/disable policy from REQ-093.
  - Follow-up evidence: level-100 Prestige is now a live selection/confirmation flow backed by an atomic, idempotent repository transaction that debits canonical guild gold, resets run progression to Level 1, promotes the selected eligible reward to permanent ownership, increments lifetime prestige count and refreshes progression state. Reward purchases, wars and Prestige share guild-scoped coordination. Operator progression.prestige.enabled remains false by default per REQ-093.
  - 2026-09-25 operator adjustment: lifetime prestige maximum increased from 3 to 6. Prestige IV / V / VI temporarily reuse the existing approved 30,000 Gold Ore fee ceiling; the cap change does not introduce a new economy curve.
  - 2026-09-29 production cutover: Prestige and Chapter 2 reward purchasing now ship enabled by default. Persisted staging-default config is upgraded only when its exact legacy marker is still present; explicit custom disables remain authoritative. Missing reward/prestige accounts for live guilds are backfilled idempotently from canonical homes at startup, future guild creation initializes reward state in the same transaction, stale persisted Level 25 locale text is migrated to Level 100, and guild deletion cleans live reward ownership state. Historical XP is not converted into perks or prestige; a Level 100 guild must still purchase an eligible current-run perk before confirming Prestige.

- [~] **LG-S2-WAR-CUTOVER** Add a clean-slate Chapter 2 war-state reset and verify warfare statistics.
  - Tag: `TDD`, `MIGRATION`, `RELEASE`
  - References: REQ-108, REQ-024, REQ-033, REQ-039, REQ-057.
  - Findings: imported staging data showed Vibe apparently involved in roughly a dozen long-running wars while Warfare quick stats reported zero. Before cut-over, inspect the live database and distinguish stale legacy rows from current-code defects. Provide an explicit audited admin operation to clear/end all active wars plus incoming/outgoing declarations and peace-agreement state so Chapter 2 starts clean; do not rely on manual SQL deletion as the release procedure.
  - Follow-up evidence: /lumaguilds warcutover CONFIRM is explicit/admin-gated and safely cancels active/declared wars, rejects pending declarations, clears peace proposals and refunds provably unsettled wagers; ambiguous REVIEW payment state fails closed and is reported. Live-database cleanup and warfare-stat reconciliation remain pending.

- [~] **LG-S2-STATS-OVERHAUL** Rebuild the Statistics menu presentation around verified data.
  - Tag: `TDD`, `UI`
  - References: REQ-013, REQ-023, REQ-032, REQ-036, REQ-066, REQ-109.
  - Findings: the menu still largely resembles the legacy vanilla-icon UI. Remove the obsolete `Export Statistics` CSV button. Kill Trends, Periodic Statistics and other entries expose missing localization; Rivalry Statistics and Guild Achievements appeared empty; Top Killers did not appear to surface data; Top Contributors appeared inert; K/D Analysis and other legacy drill-downs require verification. Replace vanilla presentation with intentional Season 2 icons and make each retained view demonstrably data-backed; hide/remove unwired surfaces rather than shipping dead buttons.
  - Follow-up evidence: CSV Export is removed, the unwired Kill Trends entry remains hidden, war-stat load failures display explicit unavailable state rather than believable zeros, and delivered Season 2 art is wired for supported statistics controls with vanilla fallbacks. Representative live-data verification remains pending.

- [~] **LG-S2-LIVE-DB-READINESS** Run a Chapter 2 readiness audit against a copy of the current live database before the production migration.
  - Tag: `MIGRATION`, `RELEASE`
  - References: REQ-091, REQ-102, REQ-108, REQ-109.
  - Scope: report orphaned/disbanded guild records, unknown/missing owners, residual wars/declarations/peace state, guild balances versus canonical `vault_gold.balance`, and representative statistics/leaderboard source rows. Produce a before/after cleanup report and validate the migration on the copy before any production execution.
  - Follow-up evidence: migration/readiness tooling now detects orphan progression/home/member/rank/relation rows, invalid member-rank identity, ownerless guilds and level drift; war cut-over tooling is ready. The audit has not yet been run against a fresh copy of the current production database.

- [x] **LG-S2-STALE-ART** Integrate Astra stale-menu bank/statistics artwork and coordinated persisted-state toggle sprites.
  - Tag: UI, INFRA, TDD
  - References: REQ-099, REQ-105, REQ-109; Astra delivery outputs/season2-stale-menu-icons/NAME-MAPPING.md.
  - Evidence: existing lg_history, lg_automation and lg_nav_statistics IDs were reused; 17 collision-free new Nexo IDs use CMD 733239-733255; 19 delivered 32x32 textures were staged/output-packaged. Generic ON/OFF sprites are used only for real persisted settings/ally-home booleans; Active Automations remains read-only status. Kill Trends art is packaged but intentionally not exposed while that data surface remains unwired. Nexo regenerated SELFHOST pack without validator/placeholder errors.

> Visual-audit release gate: `LG-S2-UI` remains partial until the findings above are resolved/retested and the user performs another Java-client walkthrough. Bedrock validation remains a separate gate because the current staging runtime does not expose Floodgate/Cumulus classes to LumaGuilds.

---

## PR-17 â€” Season 2 Bedrock parity

> Source: `docs/season2-bedrock-parity-audit-2026-09-25.md` (BPAR-001..BPAR-020).
> Plan: `docs/superpowers/plans/2026-09-25-season2-bedrock-parity.md`.
> Contract: Bedrock may use Cumulus-specific presentation, but supported behavior, authority, canonical data and failure semantics must match Java. Static parity and real-client runtime acceptance are separate gates.

- [x] **LG-1801** Bedrock parity contracts, authorization, and navigation isolation.
  - Tag: `TDD`
  - References: REQ-110, REQ-120; BPAR-007, BPAR-009, BPAR-019, BPAR-020.
  - Evidence: SPEAR RED was captured by `BedrockGuildAuthorizationTest` failing compilation on the missing authorization boundary. `BedrockGuildAuthorization` now centralizes management permission checks; Bank auto-deposit/automation/budget/security fail closed and hide mutable settings from unauthorized players; Settings and rank management reuse the guard and recheck before mutation. Invite/kick/member-rank/disband/leave confirmation flows no longer directly construct Java guild menus. `BedrockMenuNavigator` now persists forward/step state, restores recovery state and clears player workflow state on cancellation. The Bedrock control panel exposes all ten Season 2 dashboard domains and routes Quests through the platform-aware factory (the dedicated Quest form remains LG-1802). Focused parity/auth/bank/locale contracts and `LayerRulesTest` are green; full `test shadowJar` passes 1,353 tests with 0 failures / 0 errors / 3 skips; `git diff --check` is clean.
  - Files: `BedrockGuildAuthorization.kt`, `BedrockBankSettingsEditor.kt`, Bedrock bank/settings/rank/confirmation/navigation/control-panel forms, localization, parity/authorization tests.

- [x] **LG-1802** Dedicated Bedrock Weekly Guild Quests form.
  - Tag: `TDD`
  - References: REQ-111, REQ-074..REQ-079, REQ-104; BPAR-001.
  - Evidence: SPEAR RED was captured by `MenuFactoryBedrockQuestsTest` failing compilation because `BedrockGuildQuestsMenu` did not exist. The new Cumulus form consumes only shared `QuestService` read/claim APIs, pages six quests at a time, renders `QuestDisplayFormatter` objective text, progress, XP/item rewards, leaderboard rank, claimed/completable/in-progress state, reset time and full-set bonus state, and dispatches claim/page/back callbacks onto the server thread. The factory now returns the native Bedrock form. Focused routing/wiring/parity/locale tests are green. One initial full run hit the pre-existing timing-sensitive Discord-role concurrency test; that test passed in isolation and the clean rerun of `test shadowJar` passes 1,357 tests with 0 failures / 0 errors / 3 skips. `git diff --check` is clean.
  - Files: `BedrockGuildQuestsMenu.kt`, MenuFactory/control panel/localization, `MenuFactoryBedrockQuestsTest.kt`, `BedrockGuildQuestsWiringTest.kt`.

- [x] **LG-1803** Bedrock Chapter 2 progression and six-prestige flow.
  - Tag: `TDD`
  - References: REQ-093, REQ-107, REQ-112; BPAR-002, BPAR-003.
  - Evidence: Chapter 2 Bedrock progression now consumes the shared reward catalog/purchase services and `GuildPrestigeService`. The progression form exposes explicit unavailable/disabled/not-Level-100/maximum/no-choice/ready prestige states, current/max prestige count, next fee and eligible retained rewards. Selection obtains the shared immutable `PrestigeQuote`; confirmation reuses that exact quote/transaction ID, returns rejected outcomes to selection, and reopens the same quote on uncertain failure for idempotent retry. Bedrock callbacks re-enter the server thread. The Chapter 2 reward route does not use the legacy `PerkType` catalog; legacy progression remains isolated to the disabled pre-Chapter-2 fallback. Focused Bedrock prestige, reward-catalog and prestige service/config tests are green.
  - Files: `BedrockGuildProgressionInfoMenu.kt`, `BedrockPrestigeSelectionMenu.kt`, `BedrockPrestigeConfirmationMenu.kt`, localization and Bedrock prestige contract/wiring tests.

- [x] **LG-1804** Bedrock Settings parity for open state, Lunar tracking, GUI theme and current persisted state.
  - Tag: `TDD`
  - References: REQ-106, REQ-113; BPAR-004.
  - Evidence: SPEAR RED is captured by `BedrockGuildSettingsSeason2ContractTest` with all three assertions failing before implementation. The Bedrock form now refreshes the guild from `GuildService`, renders persisted open/closed, Lunar tracking and all six `GuiTheme` choices, and uses the same `setOpen`, `setTrackingEnabled` and `setGuiTheme` mutations as Java. `MANAGE_GUILD_SETTINGS` controls are read-only when unauthorized and permission is rechecked before mutation; existing description/mode permission behavior remains distinct. Form callbacks return to the Bukkit server thread and the form is no longer cached across stale guild state. Focused Bedrock/Java settings contracts are green; full `test shadowJar` passes 1,365 tests with 0 failures / 0 errors / 3 skips; `git diff --check` has warnings only.
  - Files: `BedrockGuildSettingsMenu.kt`, `BedrockGuildSettingsSeason2ContractTest.kt`, shared GuildService/GuiTheme/localization contracts.

- [x] **LG-1805** Rebuild Bedrock Statistics around authoritative data and explicit unavailable/empty states.
  - Tag: `TDD`
  - References: REQ-109, REQ-114; BPAR-005.
  - Evidence: SPEAR RED is captured by BedrockGuildStatisticsTruthfulnessContractTest (3/3 failing before implementation). Bedrock Statistics now uses the same KillService, WarService, MemberService, BankService, GuildService, LeaderboardService, InvitationStatisticsService and progression repository data as Java. Fabricated 0/800 XP, always-active, join-time activity and 0/0/1 territory placeholders were removed. Supported kill/war/member-performance/recent/top-killer/top-contributor/top-inviter/K-D/periodic/rivalry/achievement/economy states are service-backed; empty and unavailable states are explicit; unsupported territory is omitted; no CSV/export surface exists. Existing paged invitation leaderboard behavior was preserved. Focused truthfulness/data-state/invitation/localization contracts are green; full test shadowJar passes 1,368 tests with 0 failures / 0 errors / 3 skips. JAR SHA-256 F185C98845056790BC96D05865233FE8189591303F355342ADBAD466EFD25413; git diff --check warnings only.
  - Files: BedrockGuildStatisticsMenu.kt, BedrockGuildStatisticsTruthfulnessContractTest.kt, statistics localization and existing invitation/data-state contracts.

- [x] **LG-1806** Bedrock Bank authorization and coherent navigation shell.
  - Tag: `TDD`
  - References: REQ-010..REQ-012, REQ-036, REQ-105, REQ-115; BPAR-006, BPAR-007.
  - Evidence: SPEAR RED is captured by `BedrockBankNavigationSafetyContractTest` failing on the missing Bedrock bank drill-down routes and management-navigation contract. The main Bedrock bank form now routes through `MenuFactory` to History, Statistics, Contributions, Automation, Budget and Security while keeping Deposit / Withdraw as the default transaction surface. Automation/Budget/Security are omitted for players without `MANAGE_BANK_SETTINGS`, and crafted management selections recheck that permission before navigation. Existing automation/budget/security forms and `BedrockBankSettingsEditor` also recheck the same authority before persistence. Deposits remain canonical `BankService.deposit` calls and withdrawals retain `withdrawOutcome` with `BankWithdrawalResult.Ambiguous` routed to the payout-review/pending message. Focused bank/localization contracts are green; full `test shadowJar` passes 1,371 tests with 0 failures / 0 errors / 3 skips. JAR SHA-256 `64A4373A93586E336D02F29C61154D685E4BC3D9786A609B9C1E45CE9EB44346`; `git diff --check` has warnings only.
  - Files: `BedrockGuildBankMenu.kt`, `BedrockBankSettingsEditor.kt`, Bedrock automation/budget/security forms, `en_US.yml`, `BedrockBankNavigationSafetyContractTest.kt`, `BedrockBankSettingsEditorTest.kt`.

- [x] **LG-1807** Bedrock Bank drill-down parity for history, statistics, contributions and automation state.
  - Tag: `TDD`
  - References: REQ-105, REQ-115; BPAR-008.
  - Evidence: `BedrockBankDrilldownParityContractTest` now covers the bounded/filterable history model, complete paged contribution list and executable-vs-retained automation state. Bedrock transaction history loads a bounded 500-row authoritative `BankService` window, pages 10 at a time and supports transaction-type, guild-member, date-range and text search filters while rendering the actual `TransactionType`. Member contributions page the complete bounded `BankService.getMemberContributions` result instead of truncating to ten. Automation now reads `BankAutomationService.getNextInterestRun`, exposes real interest status/countdown, keeps the three persisted-but-unexecuted automation flags visibly read-only, and only mutates the executing interest rate through the authorization-checked `BedrockBankSettingsEditor.saveInterestRate`. Focused Bedrock bank contracts are green; full `test shadowJar` passes 1,375 tests with 0 failures / 0 errors / 3 skips. JAR SHA-256 `4205E64FC4FA9E0EECCAD0CB7E6175A17F263F9ECB5980AAF3956DF7B8984DEF`.
  - Files: `BedrockGuildBankTransactionHistoryMenu.kt`, `BedrockGuildMemberContributionsMenu.kt`, `BedrockGuildBankAutomationMenu.kt`, `BedrockBankSettingsEditor.kt`, `en_US.yml`, `BedrockBankDrilldownParityContractTest.kt`.

- [x] **LG-1808** Bedrock homes, selected-member management and rank-editor convergence.
  - Tag: `TDD`
  - References: REQ-116, REQ-117; BPAR-010, BPAR-012, BPAR-013, BPAR-018.
  - Evidence: native Bedrock per-home rank access and inbound ally-home whitelist forms now use the shared home/relation services; paid home activation/payment-review semantics remain intact; selected-member flow now exposes supported rank-change and kick actions; Bedrock rank creation/editing uses the complete current `RankPermission` model while preserving rank identity and existing priority during ordinary edits. `BedrockHomeMemberRankParityContractTest` went RED (4/4 failures) then green. Focused home/member/rank, locale and architecture tests are green. Full `test shadowJar` passes 1,379 tests with 0 failures / 0 errors / 3 skips. JAR SHA-256 `94ECB2FEC0859E2976E83BEEDC506EC51D5B7D9077E31EB3E602AFABA56CF713`.
  - Files: `BedrockHomeAccessMenu.kt`, `BedrockAllyHomeAccessMenu.kt`, `BedrockGuildHomeMenu.kt`, `BedrockGuildMemberListMenu.kt`, `BedrockGuildMemberDetailMenu.kt`, `BedrockGuildRankManagementMenu.kt`, `BedrockRankCreationMenu.kt`, `BedrockRankEditMenu.kt`, `MenuFactory.kt`, `en_US.yml`, `BedrockHomeMemberRankParityContractTest.kt`.

- [x] **LG-1809** Bedrock Party and LFG end-to-end form parity.
  - Tag: `TDD`
  - References: REQ-118; BPAR-011, BPAR-014.
  - Evidence: `BedrockPartyLfgParityContractTest` started RED (4/4 failing) and is now green. A dedicated `BedrockLfgBrowserMenu` uses `LfgService.getAvailableGuilds()`, bounded paging and the shared join-requirements route; browser -> requirements -> join/result remains in Bedrock forms and paid admission stays delegated to `LfgService.joinGuild`. Party details now render persisted party state instead of an unavailable message; the fake mutable party-permission surface is replaced by truthful read-only guidance; party creation checks `canManageParties` before render and again before `PartyService.createParty`. Focused LFG/party service tests and the final Bedrock parity suite are green.
  - Files: `BedrockLfgBrowserMenu.kt`, `BedrockJoinRequirementsMenu.kt`, `BedrockGuildPartyManagementMenu.kt`, `BedrockPartyCreationMenu.kt`, `MenuFactory.kt`, `en_US.yml`, `BedrockPartyLfgParityContractTest.kt`.

- [x] **LG-1810** Bedrock Warfare/Diplomacy option parity without forking lifecycle logic.
  - Tag: `TDD`
  - References: REQ-039, REQ-108, REQ-119; BPAR-015, BPAR-016, BPAR-017.
  - Evidence: `BedrockWarDiplomacyParityContractTest` started with 2/4 failures, exposing missing time-survival configuration and missing declaration authorization boundaries, then went green. Bedrock declaration now matches the Java-supported KILLS, TIME_SURVIVAL and claims-conditional CLAIMS_CAPTURED objective surfaces, uses the configured kill cap, rechecks `DECLARE_WAR` before render and mutation, and continues to create declarations only through `WarService` with no menu-side escrow. War declaration accept/reject/cancel, wager state, history/stats and peace remain authoritative `WarService` flows; alliance/truce/peace request lifecycle remains `RelationService` backed. Focused War/Relation tests are green.
  - Files: `BedrockGuildWarDeclarationMenu.kt`, `BedrockGuildWarManagementMenu.kt`, `BedrockPeaceAgreementMenu.kt`, `BedrockGuildRelationsMenu.kt`, `en_US.yml`, `BedrockWarDiplomacyParityContractTest.kt`.

- [x] **LG-1811** Bedrock localization and platform-truthfulness cleanup.
  - Tag: `TDD`
  - References: REQ-120; BPAR-014 plus all repaired Bedrock flows.
  - Evidence: `BedrockLocalizationTruthfulnessContractTest`, `LocaleContractTest`, `GuildDescriptionWiringTest` and `GuildDescriptionContentTest` are green. Audited Bedrock Party/LFG/War/Diplomacy flows have no hardcoded unavailable/coming-soon/Java-client fallbacks or literal `sendMessage` strings; stale unused Bedrock fallback adapter/locales were removed; legacy Java party text keys now contain truthful current-state descriptions rather than roadmap promises. Dynamic names remain plain form text and guild descriptions use `GuildDescriptionContent.plainText`, preserving Discord invite URLs visibly for Bedrock.
  - Files: Bedrock Party/LFG/War/Diplomacy forms, `MenuFactory.kt`, `en_US.yml`, `BedrockLocalizationTruthfulnessContractTest.kt`, locale/description contracts.

- [ ] **LG-1812** Bedrock full-suite and real-client release acceptance.
  - Tag: `INFRA`
  - References: REQ-095, REQ-120; BPAR-020.
  - Evidence: automated/static and staging-infrastructure gates are complete: focused Bedrock parity plus architecture/localization/resource contracts are green; final full `test shadowJar` passes **1,393 tests, 0 failures, 0 errors, 3 skipped**; JAR SHA-256 `040C47B3AAD1AC015928389536440CC355C286EC602802F369BB0B39056F5572`; `git diff --check` has no errors (line-ending warnings only), `gradlew check` is green, and Semgrep `p/kotlin` reruns clean with 9 rules / 795 tracked targets / 0 findings (one non-blocking PartialParsing warning in `BedrockGuildSelectionMenu.kt` around `open()` calls); no detekt/ktlint/spotless/checkstyle analyzer is configured. Local-only staging boots that exact JAR with Geyser 2.11.3-b1247, Floodgate 2.2.5-b141, bundled Cumulus, and ViaVersion 5.12.0; Geyser is bound to `127.0.0.1:19132` and Java to `127.0.0.1:25570`. LG-1812 runtime probing also exposed a real SQLite watchdog risk in bank audit pruning: `pruneAuditLogs` was performing database deletes on the Bukkit main thread. `BankInterestScheduler` now keeps inventory-sensitive interest settlement on the main thread but runs retention pruning asynchronously on an offset hourly cadence; the audit cache is concurrency-safe, and `BankInterestSchedulerThreadingContractTest` covers both thread placement and shutdown cancellation. After redeploy/restart, the first five-minute interest cycle and first asynchronous prune window passed with no `SQLITE_BUSY`, database-lock, or watchdog entries, and the server remained responsive to `bedrockcachestats`. **Still open:** the required real Bedrock-client walkthrough and independent final Java/Bedrock sign-off.
  - Files: tests, `BankInterestScheduler.kt`, `BankRepositorySQLite.kt`, `BankInterestSchedulerThreadingContractTest.kt`, `docs/season2-runtime-validation.json`, validation docs, local staging only.

> PR-17 dependency order: LG-1801 first; LG-1806 before LG-1807; LG-1802..LG-1810 before LG-1811; LG-1812 last. P0 findings (Quests, Prestige, Statistics truthfulness, bank authorization, parity-test gate) block Chapter 2 Bedrock sign-off.

## Guild rank customization (2026-10-03)

- [x] Add legacy/hex rank colors with limits based on visible text and shared Java/Bedrock validation.
- [x] Persist a guild-wide rank-chat toggle guarded by MANAGE_GUILD_SETTINGS, exposed in rank/settings menus and /g ranks chat on|off.
- [x] Apply the next-message setting to RoseChat guild formats and direct guild-chat delivery, preserving other channels and guilds.
- [x] Verify formatted names, duplicate identity, permissions, repository reload persistence, disabled output, failure handling, and schema creation/repair locally. Preserve rank IDs and immutable claim-permission profiles; strip colors from Discord profile text and name-based leader/officer channel matching.
- Validation: `gradlew.bat check shadowJar '-PreleaseVersion=3.0.15-rank-customization-test.1' --console=plain` passed: 1,462 tests, zero failures/errors, four skipped. Wiki frontmatter validation passed for 42 pages; all 13 help topics remain in parity. `git diff --check` passed.
- Test artifact: `build/libs/LumaGuilds-3.0.15-rank-customization-test.1.jar`; SHA-256 `8821A1B743D71E0ACE2F60CDFCF84C450C0A97E62E06F731E9DB076DE2EE6DF9`.
- [ ] Live Paper/RoseChat send-path verification, Java/Bedrock client menu walkthrough, and MariaDB runtime verification remain separate. Production was not changed.

## Guild chat RC-4 and bare hex rank correction (2026-10-04)

- [x] Diagnose production `/gc` failure from `latest.log`: LumaGuilds 3.0.17 calls the nine-argument `ChannelMessageOptions` constructor missing from installed RoseChat RC-4, throwing before delivery.
- [x] Copy the runtime record while replacing only `format`, retaining all options, including newer bypass flags. Keep the shared channel format unchanged.
- [x] Accept the reported `#f99801Founder` syntax in shared rank validation/rendering; retain existing `&#RRGGBB`, repeated legacy hex, identity rules, and visible/raw limits.
- [x] Render nested validation messages before Nexus placeholder interpolation in rank creation and editing.
- [x] Prove binary compatibility locally: compile against the old nine-component API and run the same channel/record-copy tests with the eleven-component `RoseChat-RC-4-discord-rank-26.2-test.7.jar` using `-ProseChatRuntimeJar=../rosechat-26.2/build/libs/RoseChat-RC-4-discord-rank-26.2-test.7.jar`. Both focused runs pass. This is not the exact production test.5 artifact or a live send test.
- [x] Full local validation and test artifact finalization: `gradlew.bat check shadowJar '-PreleaseVersion=3.0.18-guild-chat-rc4-test.1' --console=plain` passes, 1,497 tests, zero failures/errors, four skipped. Test artifact: `build/libs/LumaGuilds-3.0.18-guild-chat-rc4-test.1.jar`; SHA-256 `4D15FC67FAA4610C6C0C453658892557FA2503E7689F6E4DAB7A21817108BB37`. `git diff --check` passes.
- [ ] Live `/gc` delivery and rank rename acceptance. Production inspection was read-only; no upload, restart, or activation occurred. Codacy MCP analysis is unavailable in this session.

### SPEAR refinement and canonical delivery gate (2026-10-04)

- Spec: REQ-063 remains the behavior contract. Fetch and inspect current main before refinement; preserve every runtime option and both supported hex syntaxes.
- Prove: production stack trace and focused old/new-runtime regressions are existing evidence, not a new historical test-first claim. The GitHub build passed on `2ffde89954e16ea102f29e4c6c42337e934fdd30`; Codacy reported 35 review annotations.
- Engine: this pass changes documentation/style only; no behavior change is intended. No new behavioral tests are needed for formatting corrections.
- Arch: keep compatibility copying in infrastructure and shared color validation in its existing utility; retain Java/Bedrock consumers.
- [x] Implement the annotation corrections and rerun local checks: focused color/feedback/locale/channel tests pass; old-API-compiled channel/copy tests pass with the newer RC-4 runtime; `gradlew.bat clean check --console=plain` passes with 1,497 tests, zero failures/errors, four skipped. Formatting, documentation, visibility, constants, and shared build configuration were corrected. Reflective spread arguments remain intentional at the variable-arity compatibility boundary, with narrow documented suppressions. Hosted results must be checked against the exact updated PR head separately.
- Unmerged local test artifact, built only after `clean check` passed: `build/libs/LumaGuilds-3.0.18-guild-chat-rc4-test.2.jar`, SHA-256 `696D03E160CE9EA0594939AA7241E6737E5AB7222207102BA4F19854C44E6F84`. This does not authorize production deployment.
- SPEAR tooling boundary: this checkout has no project-local EARS validator or state helper. Requirements and this task/evidence record provide traceability; no automated SPEAR gate is claimed.
- [ ] Canonical delivery: merge the reviewed source through the normal process, update/verify the owning monorepo pin, and build/verify the resulting clean merged commit before any production deployment. No merge or production activation is authorized by this workflow update.
- Verified live owning monorepo: `BadgersMC/enthusia-network`, `plugins/luma-guilds` pins `c427d5dbc4838c95bcde45d57be14a6b6980ff8e`; `plugins/rosechat` pins `cf8a7b040f7194a0bf26d98096493bbb7e53efa9`. The guild pin predates the recent rank feature and this fix. Updating it must include the merged fix and receive combined-build validation; a standalone build is insufficient deployment evidence.
## Guild emoji safety â€” 2026-10-04

- [x] **Guild emoji safety** â€” block GUI glyphs at persistence and all guild-emoji placeholder renderers.
  - Tag: `TDD`
  - References: `docs/plans/2026-10-04-guild-emoji-safety.md`; REQ-025 public Nexo API
  - Evidence: main `6c9f5a5`; live TAB suffix uses `guild_emoji`; live gold/crimson GUI glyphs are height 256 and not emojis. `NexoEmojiService.doesEmojiExist` accepts any registered glyph and absent-plugin format fallback; `GuildServiceBukkit.setEmoji` omits existence validation; `LumaGuildsExpansion.convertEmojiToNexoPlaceholder` delegates saved names without validation. RED: unresolved-glyph regression failed on main. GREEN: full check passes 1,502 tests, zero failures/errors, four skips; architecture checks and diff check pass. Checkpoint snapshot shows Vegas uses valid :imp:; generated and published pack SHA-256 match, mapping imp to a 9-pixel purple face. This guard is preventive hardening; screenshot root cause and client acceptance remain open. See linked plan for boundaries.

---

## Enthusia GUI icons — local branch `enthusia-gui-icons` (operator, Fain)

> Local-only work: no push and no PR until the operator says so. Branch rebased onto `origin/main` 66caca6 (#192) on 2026-10-01. Verification build: offline Gradle 9.1.0 with the operator's cached dependencies (`~/.gradle` + `~/.m2` from the PC) and a Temurin 25.0.2 runtime as the Java 25 toolchain; MockK self-attach enabled for the sandboxed test JVM only (init script, not committed). Baseline `origin/main` on the same setup: green.

- [x] **LG-1900** Bedrock fallback for Java chest menus: vanilla icons and plain titles (opt-in).
  - Tag: `TDD`
  - References: REQ-110..REQ-120 (Bedrock parity); PR-17 global constraint "supported Bedrock flows stay in Cumulus forms"; Paper `InventoryOpenEvent#titleOverride`; PacketEvents 2.11.2 `WrapperPlayServerWindowItems` / `WrapperPlayServerSetSlot`.
  - Scope: only Java chest menus that still reach a Bedrock player (Cumulus unavailable, Bedrock menus disabled, or a menu without a form). Nexo icons built through `NexoItemProvider.getItemStackOrFallback` carry their vanilla fallback in PDC `lumaguilds:bedrock_icon`; `MenuIconAdapter` swaps tagged icons in window packets and drops the `guild_bg_*` glyph from themed titles. Quest reward items use `getItemStack` and stay untagged so they stack.
  - Evidence: `BedrockIconsTest` 3/3, `MenuIconAdapterTest` 8/8 green. Production already maps ~60 `lg_` icons for Bedrock through Geyser custom items (`Geyser-Velocity/custom_mappings/luma-enthusia-nexo.json`, pack `Luma-Enthusia-Unified-Bedrock-1.0.16`), so both behaviours are now **opt-in**: `bedrock.java_menu_vanilla_icons` and `bedrock.java_menu_plain_titles`, default false (`ConfigLoaderConsistencyTest` covers defaults and loading). Still open: a real Bedrock-client walkthrough, including whether Nexo rewrites titles before `InventoryOpenEvent`.

- [ ] **LG-1901** Show the custom Enthusia icons to Bedrock players through Geyser custom items.
  - Tag: `INFRA`
  - References: Geyser custom items v2 (`item_model` mappings, Bedrock resource pack required — https://geysermc.org/wiki/geyser/custom-items/); Nexo Scaffolding add-on (https://docs.nexomc.com/addons/scaffolding, needs Nexo 1.26+ and Geyser 2.11.0-SNAPSHOT+); Rainbow (https://geysermc.org/wiki/other/rainbow/).
  - Finding 2026-10-01: a Bedrock pipeline already exists on the Velocity proxy (`Geyser-Velocity`): custom mappings `luma-enthusia-nexo.json` (123 entries, format v2: `legacy` by custom_model_data and `definition` by `nexo:` item model) and pack `Luma-Enthusia-Unified-Bedrock-1.0.16.mcpack`. Scaffolding is not needed: extend that pipeline instead.
  - Prepared (not live): pack **1.0.17** (42 existing icons retextured with the Enthusia art, 83 new icons) and merged mappings (206 entries; new ones are `definition` type on `nexo:<id>`). Backup of the live mappings saved and byte-verified at `Geyser-Velocity/staged-enthusia-gui-20261001/luma-enthusia-nexo.json.backup-before-enthusia-gui`. Uploading the new pack/mappings to the proxy was stopped by the session's safety check (shared production resource); the files are in the staging kit for the operator. Going live = replace the mappings file + swap 1.0.16 for 1.0.17 in `packs/` + proxy restart, and only after the matching Nexo items exist on the backend.
  - Production check 2026-10-01 (Bloom.host file manager, read-only): Scaffolding is **not installed** (not in `plugins/` or `plugins/Nexo/`). Backend has `nexo-1.28.jar` (meets Scaffolding's Nexo 1.26+), `floodgate-spigot.jar`, `packetevents-spigot-2.13.0.jar`, `ProtocolLib-26.2-dev`, `LumaGuilds-3.0.4-guild-chat-fix.4.jar`, ViaVersion/ViaBackwards 5.12.0, on Leaf 26.2. **No Geyser jar on the backend** — Geyser must run on the proxy, so its version is still unknown and Scaffolding would need to deploy mappings where Geyser actually runs.
  - Next step (operator): confirm the Geyser version/location on the proxy (needs 2.11.0-SNAPSHOT+). Staging already runs Geyser 2.11.3-b1247 (LG-1812), so trial Scaffolding there first.
  - Plan: prefer Scaffolding if production meets its requirements; otherwise generate the Geyser v2 mappings + Bedrock pack from `resourcepack/enthusia-icons` (needs one icon's `item_model` value via F3+H). When Bedrock mappings are live, add a config switch so `MenuIconAdapter` stops swapping icons for Bedrock players (the title cleanup stays).
  - Evidence: none yet.

- [x] **LG-1902** Vanilla menu style for guilds that do not want the custom look.
  - Tag: `TDD`
  - References: REQ-096, REQ-099 (GUI themes); LG-S2-THEME; LG-1900 (shared icon swap).
  - Scope: new `GuiTheme.VANILLA` (`hasBackground = false`), stored by enum name like every other theme (no migration). `MenuTitleBuilder` returns the plain title (white reset to the default chest colour, no glyph or shifts). Settings → GUI Theme offers it as a plain chest swatch; the Bedrock settings form lists it automatically. Members of a Vanilla-style guild are sent vanilla item icons by `MenuIconAdapter`, re-evaluated on every inventory open.
  - Evidence: probes written first — `MenuTitleBuilderTest` (vanilla: no glyph/shift, colour reset, every other theme still has a background), `GuildSettingsThemeSelectorContractTest` (chest swatch), `MenuIconAdapterTest` (themed Java keeps icons; Vanilla-style and Bedrock get vanilla; switching back restores; guildless keeps icons; quit clears). RED captured 2026-10-01 by removing the vanilla branch in `MenuTitleBuilder` and the vanilla-style lookup in `MenuIconAdapter`: exactly the three vanilla probes failed (title glyph, title colour, vanilla-style icons); GREEN with the implementation. Without PacketEvents, Vanilla-style guilds lose the background but keep custom icons.

- [x] **LG-1903** Verify the branch end to end and stage it on SMP Test Server (`5d109214`).
  - Tag: `INFRA`
  - References: LG-1900..LG-1902; operator request 2026-10-01 ("ensure that the menu and icons are functional", "stage the plugin in SMP Test", no proxy change live, no restarts).
  - Refine fixes found by the first real run: 5 contract failures, all from this branch — 107 locale lines left unused by the Guild Actions/tooltip rewrite removed (dead-key baseline 0); `menu.bank.back_to_control_panel` kept (bank sub-menus read it via `getLocalizedString`) and declared as a localized helper key; war-objective claims icon given a drawable glyph (was blank); banner contracts follow `GuildBannerItemResolver.resolveForDisplay`.
  - Evidence: full `test` **1,460 tests, 0 failures, 0 errors, 4 skipped** (includes `LayerRulesTest`, `LocaleContractTest`, `ConfigLoaderConsistencyTest`); `shadowJar` → `LumaGuilds-3.0.4-enthusia-gui.2.jar`, 24,838,126 bytes, class version 69, SHA-256 `1c8a88de2b03449c4225caa43189e4f996427eb5e948962abf166a482443fdbe`.
  - SMP Test survey (read-only): LumaGuilds `2.1.25-network-compat`, Nexo 1.28, Floodgate, PacketEvents 2.14.0; `lg_` items CMD 733000–733103 with `nexo:lg_*` item models; `guild_bg_*` glyphs for the six original themes, rows 3–6, `ascent: 14`, `height: 256`, chars U+A000–U+A017. New glyphs use U+A040–U+A05D; new items CMD 733400–733482.
  - Done on SMP Test: backups of the 9 Nexo item/glyph files in `plugins/Nexo-backup-enthusia-gui-20261001/` (byte-verified); an archive of `plugins/LumaGuilds` was requested (confirm it under `plugins/archive-*.tar.gz`).
  - Staged on SMP Test 2026-10-01 with operator approval: 42 existing `lg_` items repointed to `lumaguilds:enthusia/*` (byte-verified writes); `nexo-enthusia-gui-assets.zip` extracted into `plugins/Nexo/` (122 icon textures, 30 theme backgrounds, `items/lg_enthusia_gui.yml`, `glyphs/enthusia/lumaguilds_enthusia_styles.yml`); `LumaGuilds-2.1.25-network-compat.jar` → `.pre-enthusia-gui.disabled`.
  - Boot 1 (13:30, `3.0.4-enthusia-gui.2`): clean enable, schema v41, Nexo loaded every item and regenerated the pack with no errors for the new files — but `PacketEvents not available`: packetevents enabled after LumaGuilds despite the softdepend. Fixed (adapter now hooks on `PluginEnableEvent` for packetevents; 3 new probes), suite 1,463 / 0 / 4.
  - Boot 2 (13:54, `3.0.4-enthusia-gui.3`, SHA-256 `f103f90adce1a005a84cb4028e80d23ea99c32976b140a7c3d7fae13f4143733`): `Menu icon adapter waiting for packetevents to enable` → `Menu icon adapter active` one second later; `Done (38.634s)`. Remaining LumaGuilds warnings are pre-existing (SMP Test config still lists removed perks `CUSTOM_BANNER_COLORS`/`ANIMATED_EMOJIS`; DiscordSRV not connected on test). The `.2` jar is kept as `.superseded.disabled`.
  - Still open: human Java walkthrough on SMP Test (dashboard, Guild Actions, theme picker incl. Vanilla, Declare Enemy, progression sources, quests and toast); Bedrock go-live stays with LG-1901 (proxy untouched apart from the mapping backup).

- [x] **LG-2013** Enforce service rank authority and atomic ownership transfer (REQ-128/129, checklist G02/G24).
  - Evidence: eight direct RankService regressions initially gave six failures; all pass after guards. Four actual SQLite ownership tests cover second-write rollback, stale compare-and-set, restart and retry. Clean Java 25/Paper 26.2 suite: 1,550 tests, zero failures/errors, four skips. See docs/checklist-correctness.md.
  - Gates: exact-head hosted CI, supported database/runtime acceptance and maintainer review; no production repair or deployment.

## Guild-shop XP (REQ-134)

See [guild-shop-xp.md](guild-shop-xp.md) for approved policy, durable delivery, SPEAR state and acceptance boundaries.

- [x] **SHOP-XP-SPEC** — REQ-134 approved limits, own-guild exclusion and no refund feature; docs/guild-shop-xp.md.
- [x] **SHOP-XP-ENGINE** — quotes, atomic caps/consumption/progression, API, persistence and cache integration.
- [x] **SHOP-XP-PROVE-REFINE** — SQL concurrency/idempotence/rollback/config and full-suite local proof recorded in docs/guild-shop-xp.md.
- [ ] **SHOP-XP-HOSTED** — inspect checks/review for exact published head; paired release contract must execute on the updated CI pin.
- [ ] **SHOP-XP-LIVE** — merged runtime/pins, MariaDB staging, live outside/own-guild purchases, restart/retry and prestige walkthrough. No production changes.
## Unresolved guild investigations — 2026-10-07

- [x] **LG-2001** Direct bank entry for guild members.
  - Tag: `TDD`
  - References: REQ-122; operations audit G03.
  - Spec: reuse MenuFactory Java/Bedrock routing and existing menu command permission; retain all bank mutation guards.
  - Evidence: main a15b244 has no bank subcommand; `/guild menu` gates on unrelated management permissions. New test initially could not compile because onBank was absent; all three bank regressions passed after the adapter was added. Java/Bedrock live acceptance remains separate.
- [x] **LG-2002** Apply configured chat cooldowns.
  - Tag: `TDD`
  - References: REQ-123; spam-control investigation.
  - Evidence: ChatConfig defaults 30/5 minutes, but ChatServiceBukkit used hardcoded 5/1 minutes. Four of five service tests failed before the engine change; all five passed afterwards. Hourly limits preserved, nonpositive cooldowns covered.
- [ ] **LG-2003** Reconcile remaining integration/client gaps.
  - Tag: `DOC`
  - References: G17/G22/G31 and 2026-10-07 fork audit.
  - Evidence: home selectors use Java inventories/Bedrock forms, not a reproduced chat selector; guild-shop XP requires Market-side transaction evidence; menu textures and insert/alignment fixes have separate asset/upstream PR ownership. No production writes or unapproved feature policies authorized.

SPEAR tooling boundary: no project-local EARS validator/state helper exists. Requirements, tasks and the investigation evidence record provide traceability; no automated EARS/state pass is claimed. Production stays read-only. User-confirmed bugs and denied home proposals remain closed.

Local validation (2026-10-07): Java 25 / pinned Paper 26.2, `gradlew clean test shadowJar` passed: 1,546 tests, zero failures/errors, four skips. This includes layer/locale contracts and existing bank, rank, home and leaderboard regressions. Unmerged local test artifact only: `LumaGuilds-3.0.0.jar`, 24,982,673 bytes, SHA-256 `9e8079faea2da9d3f34c24705bc1046cbd2e222a35f428cc2e79d5d3a2cdd120`. No production upload or activation.

PR #210 review refinement: initial head `310df95c` passed GitHub build; Codacy requested 37 test-style corrections (documentation, internal visibility, method names, constants and formatting). Applied those corrections without changing engine behavior; all eight focused tests passed again. Hosted validation must be checked on the revised exact head. Existing workflow deprecation warnings are separate from new test findings.
## Holiday menu styles (EnthusiaHolidays) — 2026-10-06

Ported from FainNeito/LumaGuilds#2/#3 onto the Enthusia redesign: the holiday styles are the Halloween and Christmas styles from the Guild Menus design (`LumaGuilds_Guild_Menus_3`), not separate themes.

- [x] **LG-1904** Cosmetic unlock ledger and public API — `GuildCosmeticUnlock`, `GuildCosmeticUnlockRepository` (SQLite/MariaDB, preload cache), `GuildCosmeticUnlockService`, `net.lumalyte.lg.api.GuildCosmeticUnlocks` registered in ServicesManager.
  - Tag: `TDD`
  - References: REQ-121
  - Evidence: `GuildCosmeticUnlockRepositorySQLiteTest` (4), `GuildCosmeticUnlockServiceTest` (8), `GuildCosmeticUnlocksImplTest` (2), `GuildThemeUpdateSQLTest` (2). Revoking the equipped style resets only `gui_theme` with a compare-and-set (`updateGuiTheme`), never a stale full-guild write. MariaDB DDL mirrors the existing ledgers but was not run against MariaDB.
- [x] **LG-1905** `HALLOWEEN` and `CHRISTMAS` styles — `requiresUnlock` + `seasonalIcons`, offered in Settings → GUI Theme (Java and Bedrock) after Voidlight; locked until earned (Java: locked name and unlock hint; Bedrock: "(locked)" in the dropdown and a locked message). `setGuiTheme` rejects a locked style and fails closed without the ledger.
  - Tag: `TDD`
  - References: REQ-121, REQ-096
  - Evidence: `GuildServiceThemeUnlockTest` (3), `MenuTitleBuilderTest` picker order, `LocaleContractTest`.
- [x] **LG-1906** Seasonal icon sets — menu icons carry their base id in PDC `lumaguilds:icon`; `MenuIconAdapter` sends members of a Halloween/Christmas guild each icon drawn with its `<id>_<style>` Nexo variant (model and custom model data copied; name, lore, count and click handling unchanged). Vanilla icons win over seasonal ones; icons without a variant are unchanged; variants are cached until Nexo reloads.
  - Tag: `TDD`
  - References: REQ-121, LG-1900
  - Evidence: `SeasonalIconsTest` (5), `MenuIconAdapterTest` +4 (holiday style, other styles, vanilla precedence, switch/quit). Probes failed to compile before `seasonalStyleFor` existed. The packet swap itself needs a live client (not verified).
- [x] **LG-1907** Holiday pack assets from the Guild Menus design — 12 backgrounds (`gui/{halloween,christmas}/guild_menu_<style>_<1-6>_row.png`, artwork at origin on 256×256; the design's Enthusia art matches the shipped texture pixel for pixel), glyphs U+A060–U+A06B, `lg_theme_halloween`/`lg_theme_christmas` swatches and 2 × 122 seasonal icons (`Nexo/items/lumaguilds_holiday_styles.yml`; server-kit copy with CMD 733500–733745 and `item_model nexo:<id>`).
  - Tag: `ASSET`
  - References: REQ-121, REQ-099
  - Evidence: `HolidayStylePackTest` (4): every row has a 256×256 background glyph, glyph chars unique across the pack, every holiday item has a 16×16 texture, every seasonal item varies an existing icon. In-game rendering not verified.
- [x] **LG-1908** Fix shifted `gui_theme` / ally-home columns on guild insert (from FainNeito/LumaGuilds#3) — the bound theme now follows the column order in all five insert variants; startup repairs rows written in the shifted layout.
  - Tag: `TDD`
  - References: REQ-121 (GUI themes), ally homes
  - Evidence: `GuildInsertColumnOrderTest` (3) on the migrated schema.
- Full suite on this branch: **1,573 tests, 0 failures, 0 errors, 4 skipped**.
- Still open: Java and Bedrock client walkthrough on SMP Test after installing the pack files; Geyser mappings for the seasonal icons (LG-1901).

- [x] LG-1909: Review #208 ownership/theme contracts against actual companion APIs and prove native MariaDB insert/theme/vault schema compatibility (8 October).
  - Evidence: three native guild-update assertions failed on missing vault columns, then all native ownership/insert/theme contracts passed after additive schema repair. Final full local suite: 1,574 tests, zero failures/errors, four unrelated skips; ten native MariaDB cases, zero skips. Final hosted checks and client/pack acceptance remain distinct gates.
## Discord role cleanup delivery (2026-10-06)

- [ ] **LG-1506-CLEANUP** Remove legacy managed roles below the configured minimum while preserving completed prestige.
  - Tag: `TDD`
  - References: REQ-071
  - Spec: Existing links alone do not establish eligibility. The configured minimum remains 50 by default; recorded completed prestige preserves eligibility after level reset. Only persisted managed role IDs are deleted.
  - Prove: Current canonical main a15b244 uses `repository.get(guildId) != null` as a permanent unlock, explaining retained legacy test roles. Recovered existing Oct 1 regressions; no historical red/green result is claimed for this run.
  - Engine: Recovered eligibility/deletion changes from 05a56f0 and 25b8a86 onto current main. Failed eligibility reads preserve links; deletion failures retain links for retry. Cleanup runs at startup and on the existing five-minute reconciliation schedule.
  - Arch: Reward ownership is queried through its application repository; Discord operations remain in the infrastructure gateway. Persisted schema is unchanged. Current focused architecture and Koin graph checks pass. DiscordSRV 1.30.5 JDA getRoleById/createRole/setName signatures were inspected against the locally downloaded runtime.
  - Refine: Focused role service, listener, SQL repository, architecture and Koin tests: 36 tests, zero failures. Full `test shadowJar` on Java 25 / Paper 26.2: 1542 tests, 0 failures, 0 errors, 4 skipped; build passed. Exact PR-head GitHub checks are pending.
  - Tooling: This checkout has no project-local EARS validator or SPEAR state helper. REQ-071 and this evidence/task record are maintained directly; no helper validation is claimed.
  - Acceptance boundary: No Discord roles have been deleted in this run. Merge, canonical network pin/build, deployment/activation and live role verification remain required.

- [x] CHECKLIST-INTEGRATION: Reconcile the approved PR source chain without losing bank/stall commands, shutdown hooks or Gradle integration inputs.
  - Tag: INFRA
  - References: REQ-071, REQ-121 through REQ-134; docs/checklist-correctness.md.
  - Evidence: local merge conflicts reproduced; reconciled source ancestry verified. Combined suite 1,653 tests, zero failures/errors, four unrelated skips; 54 separate native cases, zero skips. Hosted final-head checks and subsequent authorized release/player acceptance remain distinct.

- [x] CHAT-RECONNECT: Optional guild-wide reconnect reset in Java/Bedrock settings.
  - Tag: TDD
  - References: REQ-135.
  - Evidence: authoritative main a15b244 inspected; isolated branch then integrated preceding approved review source #213 to preserve the checklist chain. Existing join cleanup only removes invalid channels. No project-local EARS/state helpers found; manual requirement/task/evidence maintained. Focused contracts: 58 passed. Clean integration suite: 1,673 tests, zero failures/errors, four skips; native MariaDB: 59 passed, zero skips. Persistence, authority, stale forms, configured channels, and disconnected/replaced sessions are covered. The actual companion runtime contract ran with zero skips. Codacy refinements passed the final clean integration build. Exact-head hosted checks are recorded on the PR. See docs/chat-reconnect.md. Production stays unchanged.

- [x] COMMUNITY-UI: Implement approved G20/G21/G22/G24/G25 with personal chat settings, optional channel indicator, permission-safe individual rank selection and bounded public directory details.
  - Tag: TDD; References: REQ-136 through REQ-139; docs/community-ui.md.
  - Evidence: category-opening regression failed against prior implementation then passed; native SQL failure reproduced and fixed. Full 1,679-test suite passes with four unrelated skips; 61 native cases pass with zero skips. Shared service/SQL contracts and actual companion runtime are verified. No project-local EARS/state helpers exist; manual requirements/tasks/evidence maintained. PR-head CI, manual review and real Java/Bedrock acceptance remain separate. No merge or production changes.

- [x] COMMUNITY-CODACY — Inspect #215 provider results directly, refine public directory types, new-code formatting and method boundaries, preserve literal locale/wiring contracts, rerun full and native SQL validation, then inspect the published head. EARS/state helpers remain absent; manual evidence is in docs/community-ui.md. Codacy passes at source 604e1e5 (check 113413615301), with zero new issues. Full local validation passes 1,680 ordinary cases (15 external skips) and all 62 native cases without skips. Final PR-head checks and manual/client acceptance remain separate.

- [x] VAULT-PERMISSIONS
  - Tag: `TDD`
  - References: REQ-140
  - Evidence: Source correction from canonical main a15b244; historical live member-access report remains unconfirmed. Red: 10 of 12 listener tests failed before the fix. Expanded focused checks pass 75 tests; full Java 25 offline test/shadowJar passes 1,564 tests, zero failures/errors and four external skips. Final helper/fixture refinement passes the same focused test/shadowJar profile; signature-only refinement compiles. See vault-inventory-permissions.md. PR/hosted review and in-game acceptance remain separate; the latter is deferred at user request. Production untouched; no project-local EARS/state helpers found.
  - Files: VaultInventoryListener, VaultItemPermissions, VaultInventoryPermissionTest, docs/vault-inventory-permissions.md

- [x] ALLY-HOME-MENU-AUTHORIZATION
  - Tag: `TDD`
  - References: REQ-141
  - Evidence: Canonical main a15b244 uses rendered access and destination at click time. New actual GuiItem-action regressions fail four of five cases on unchanged source; all five pass after fresh authorization/current-home resolution. Existing ally-access and locale focused checks pass. Full Java 25 offline test/shadowJar passes 1,543 tests with zero failures/errors and four external skips; final focused test/shadowJar also passes including the sixth grant-access case. Hosted/manual review remains separate; in-game testing deferred at user request. No local EARS/state helpers found; production untouched.
  - Files: GuildHomeMenu, AllyHomeMenuAuthorizationTest, docs/ally-home-menu-authorization.md

- [x] AXKOTH-ARTIFACT-PREPARATION - superseded by RETIRE-AXKOTH-147; historical evidence follows.
  - Tag: INFRA; References: REQ-142
  - Latest recovery evidence: `docs/axkoth-ci-recovery.md`. Eight bounded transport/cache regressions pass locally and on hosted repair 68506a4. Clean Guilds companion test/shadowJar passes 1714 cases, zero failures/errors, 19 external skips; four private artwork checks pass separately. Reciprocal Market suite passes 915 cases, zero failures/errors, eight skips. Hosted run 37842408052 still exhausts 32 truncated official AxKothAPI transfers before Gradle; this task remains open for that upstream build gate. Repair uses sanitized branch history and canonical main e11fb16, preserving proprietary assets privately.
  - Spec: Preserve official AxKothAPI 4/axapi 1.4.8 and their original transitive POMs. Verify all cached/downloaded bytes before use; never retry Gradle tests or source errors.
  - Prove: Final #218 hosted job 113460383318 fails before compilation on truncated JAR bodies. Direct curl reproduced error 18; range resume failed with error 33. Artifact-only retry recovered the full AxKothAPI JAR matching publisher SHA-1 8f6bc658e9f18b1e544ede91e61234f1b46e7281 and the previously validated cache SHA-256. Original axapi JAR matches the publisher SHA-256. No historical behavioral red/green claim applies to this infrastructure change.
  - Engine / arch: Shared composite action prepares only these two Maven coordinates in the existing mavenLocal repository, retaining official POMs and unchanged Gradle invocation. The hosted preparation probe reproduced HTTP/2 INTERNAL_ERROR on all six attempts; artifact requests now explicitly use the locally validated HTTP/1.1 transport. Five curl retries bound each transfer; SHA-256 verification fails closed. No plugin behavior, API version, shading or production changes.
  - Refine: All four original artifacts/POMs match the recorded SHA-256 digests and the existing validated Gradle cache. Truncated bytes fail checksum comparison. Full Java 25 offline test/shadowJar using the prepared Maven artifacts passes 1,713 cases, zero failures/errors and 15 external skips; actual Market API contract executes. Final-head hosted workflow validation pending. No project-local EARS/state helper or local Bash/YAML parser is available; manual evidence maintained. In-game tests deferred.


- [x] ALLY-HOME-STACK-REFINEMENT
  - Tag: `TDD`
  - References: REQ-141
  - Evidence: Integrated the reviewed #215/#217 stack with requirement/task conflicts resolved. Added a genuine failing seventh action case for lost current listing/perk eligibility; fixed using current GuildService.getAllyHomes and existing access policy. Final combined test/shadowJar passes 1,713 tests, zero failures/errors and 15 external skips; actual Market artifact contract runs with zero skips. Interactive behavioral preview accompanies the change; in-game acceptance deferred. No production changes, merge or activation. Hosted/manual review remains separate.
  - Files: GuildHomeMenu, AllyHomeMenuAuthorizationTest, docs/ally-home-menu-authorization.md


- [x] STALL-REVIEW-RECONCILIATION
  - Tag: INFRA; References: REQ-143/REQ-144 and existing REQ-136/REQ-139.
  - Prove: Read-only merge-tree probe of #216 and #218 reproduces conflicts in GuildStallMenu and RankCreationSelectionTest. Standalone stall documentation also duplicated REQ-140/REQ-141.
  - Engine / arch: Retained the current shared permission-rechecking sales/access controls and reviewed rank setup, incorporated the existing vault/ally-home corrections and verified artifact preparation, reconciled unique requirement IDs. Existing player-facing behavior is unchanged; no historical red/green runtime claim is made for this merge.
  - Refine: Combined Java 25 offline test/shadowJar passes 1,714 cases, zero failures/errors and 15 external skips, including the actual Market artifact contract. Both previous merge conflicts are resolved; final-head hosted checks and maintainer review remain separate gates. Manual SPEAR records maintained; no EARS/state helpers found. In-game acceptance deferred; production untouched.

- [x] GUILD-START-145/146 implementation/local proof: permission-aware onboarding menus, membership-scoped durable prompt and mobile preview. Evidence: docs/guild-onboarding.md. Clean build: 1,741 cases, zero failures/errors, 15 existing skips; all 27 new cases and four native MariaDB contracts execute. Local EARS/state helpers absent. Hosted checks, independent review, canonical release and client acceptance remain separate gates; production unchanged.

- [x] RETIRE-AXKOTH-147 implementation/local proof: remove unused team hook, API dependencies, repository and soft dependency; preserve guild state/war services. Evidence: docs/retired-axkoth.md. Clean build: 1,538 cases, zero failures/errors, four existing skips; compile/test runtime graphs contain no AxKoth/axapi. Hosted checks and maintainer review remain separate gates; production unchanged.
