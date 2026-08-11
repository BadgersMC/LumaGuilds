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
  - Evidence: `peaceful_guild_pvp_opt_in` now loaded in `loadGuildConfig` (was dead field); `ModeServiceBukkit.isPvpAllowedBetween` consumes it — peaceful guilds are PvP-blocked by default, but when the opt-in is true their members can fight. Verified by `PeacefulModeEnforcementTest` (opt-in off blocks, opt-in on allows).
  - Files: `ConfigServiceBukkit.kt`, `ModeServiceBukkit.kt`

---

## PR-6 — Statistics & maps

- [ ] **LG-601** MapRendererServiceBukkit produces real overview/trend/comparison/proportion renders; TTL cache cleanup scheduled; `isAvailable()` honest
  - Tag: `TDD`
  - References: REQ-013
  - Evidence:
  - Files: `infrastructure/services/MapRendererServiceBukkit.kt`, renderer services
- [ ] **LG-602** Implement the 14 stubbed statistics drill-downs (kill stats, contributions, etc.)
  - Tag: `TDD`
  - References: REQ-032
  - Evidence:
  - Files: `interaction/menus/GuildStatisticsMenu.kt` + detail views

---

## PR-7 — Localization migration (cross-cutting)

- [ ] **LG-701** Migrate guild/bank/war/admin command messages off hardcoded `§`-strings onto `lang/defaults/*.properties`; zero unreferenced lang keys remain
  - Tag: `TDD`
  - References: REQ-016
  - Evidence:
  - Files: `interaction/commands/*`, `lang/defaults/*.properties`, `LocalizationProviderProperties`
  - Note: large — decompose into per-command sub-tasks during spec if the briefing exceeds ~1500 tokens.

---

## PR-8a — Java UI completion

- [ ] **LG-801** Apply `ui.*.enchanted` menu-item glow
  - Tag: `TDD`
  - References: REQ-022
  - Evidence:
  - Files: `MenuItemConfig.kt:338`, menu builders
- [ ] **LG-802** Real disband/leave/rank-list/promotion menus (replace "coming soon!" stubs)
  - Tag: `TDD`
  - References: REQ-030
  - Evidence:
  - Files: `GuildDisbandConfirmationMenu.kt:17`, `GuildLeaveConfirmationMenu.kt:17`, `GuildRankListMenu.kt:12`, `GuildPromotionMenu.kt:17`
- [ ] **LG-803** War management buttons ×7 (details/list/incoming/outgoing/stats/history/detailed) implemented
  - Tag: `TDD`
  - References: REQ-033
  - Evidence:
  - Files: war management menus
  - Note: needs PR-4 war data.
- [ ] **LG-804** Party management buttons ×5 (details/list/send request/create/access settings) implemented
  - Tag: `TDD`
  - References: REQ-034
  - Evidence:
  - Files: party menus
- [ ] **LG-805** Rank permission-category selection (RankCreationMenu:388) + rank reset (RankEditMenu:385) implemented
  - Tag: `TDD`
  - References: REQ-035
  - Evidence:
  - Files: `RankCreationMenu.kt`, `RankEditMenu.kt`
- [ ] **LG-806** Misc menu stubs: settings name-edit lore, enemies list, peace agreement, bank statistics tax, statistics online tracking
  - Tag: `TDD`
  - References: REQ-036
  - Evidence:
  - Files: `GuildSettingsMenu.kt:77`, `EnemiesListMenu.kt:232`, `PeaceAgreementMenu.kt:375,379`, `GuildBankStatisticsMenu.kt:410,417`, `GuildStatisticsMenu.kt:158`

---

## PR-8b — Bedrock & misc UX

- [ ] **LG-811** Remove all `.coming.soon` lang keys from `lang/bedrock/forms.properties`
  - Tag: `TDD`
  - References: REQ-037
  - Evidence:
  - Files: `lang/bedrock/forms.properties`
- [ ] **LG-812** Functional Bedrock forms for bank budget/automation/security, claim player/wide permissions, and edit tool
  - Tag: `TDD`
  - References: REQ-038
  - Evidence:
  - Files: `BedrockGuildBankBudgetMenu`, `BedrockGuildBankAutomationMenu`, `BedrockGuildBankSecurityMenu`, `BedrockClaimPlayerPermissionsMenu`, `BedrockClaimWidePermissionsMenu`, `BedrockEditToolMenu`
  - Note: bank forms need PR-3 persistence; claim forms need PR-5.
- [ ] **LG-813** Floodgate locale detection in `BedrockLocalizationServiceFloodgate`
  - Tag: `TDD`
  - References: REQ-041
  - Evidence:
  - Files: `BedrockLocalizationServiceFloodgate.kt:52`
- [ ] **LG-814** `BaseBedrockMenu` constructed via DI, not service-locator
  - Tag: `INFRA`
  - References: REQ-042
  - Evidence:
  - Files: `BaseBedrockMenu.kt:576`, Koin modules
- [ ] **LG-815** Bedrock join-requirements flow — no Java menu fallback
  - Tag: `TDD`
  - References: REQ-043
  - Evidence:
  - Files: `MenuFactory.kt:1065`
- [ ] **LG-816** Bedrock guild bank auto-deposit toggle persisted and applied
  - Tag: `TDD`
  - References: REQ-044
  - Evidence:
  - Files: `BedrockGuildBankMenu.kt:77,301`
- [ ] **LG-817** "Return to LFG" in join-requirements menu reopens LFG
  - Tag: `TDD`
  - References: REQ-040
  - Evidence:
  - Files: `JoinRequirementsMenu.kt:157`

---

## PR-9 — Tech debt sweep

- [ ] **LG-901** Remove `ShopIntegrationService` (dead class, no DI registration, no consumers)
  - Tag: `INFRA`
  - References: REQ-017
  - Evidence:
  - Files: `infrastructure/services/ShopIntegrationService.kt`
- [ ] **LG-902** NexoEmojiService resolves glyphs without reflection into FontManager
  - Tag: `TDD`
  - References: REQ-025
  - Evidence:
  - Files: `NexoEmojiService.kt:198`
- [ ] **LG-903** Discord CSV avatar URL configurable (no hardcoded placeholder)
  - Tag: `TDD`
  - References: REQ-028
  - Evidence:
  - Files: `DiscordCsvService.kt:255`, discord config section

---

## PR-10 — Domain purity II (Bukkit-free domain)

- [ ] **LG-1001** Decouple domain events from `org.bukkit.event.Event`; remove `org.bukkit`/`org.koin`/`co.aikar`/`net.kyori` imports from `domain/**`; make the `forbidden:` contract executable (LayerRulesTest external-package assertion + populated list)
  - Tag: `TDD`
  - References: REQ-045
  - Evidence:
  - Files: `domain/events/*` (21 files, 38 Bukkit imports), `domain/entities/{VaultInventory,ViewerSession,WriteBuffer}.kt`, `LayerRulesTest`, `docs/implementation.md`
