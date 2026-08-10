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

- [ ] **LG-101** Bedrock cache commands authorize via `lumaguilds.bedrock.cache.*` — no stale `lumalyte.*` prefix
  - Tag: `TDD`
  - References: REQ-001
  - Evidence:
  - Files: `interaction/commands/BedrockCacheStatsCommand.kt`, `src/main/resources/plugin.yml`, test asserting code prefix == plugin.yml prefix
- [ ] **LG-102** Declare the 14 `lumaguilds.guild.*` command nodes (join, list, lfg, decline, invites, leave, transfer, getvault, vault, help, ally, enemy, truce, neutral) in plugin.yml with sane defaults
  - Tag: `TDD`
  - References: REQ-002
  - Evidence:
  - Files: `src/main/resources/plugin.yml`, test scanning `@CommandPermission` vs plugin.yml declarations
- [ ] **LG-103** Add `claim.partitions`, `claim.trustlist`, `claimmenu`, `claimoverride` to the `lumaguilds.command.*` wildcard children
  - Tag: `TDD`
  - References: REQ-003
  - Evidence:
  - Files: `src/main/resources/plugin.yml`, wildcard children test

---

## PR-2 — Config plumbing (dead sections + orphan keys)

- [ ] **LG-201** Load the full `vault:` config section (config.yml:165-299) and apply it at runtime
  - Tag: `TDD`
  - References: REQ-004
  - Evidence:
  - Files: `infrastructure/services/ConfigServiceBukkit.kt`, `config/MainConfig.kt`, loader tests
- [ ] **LG-202** Load the `bedrock:` config section (config.yml:673-735) and replace placeholder icon defaults
  - Tag: `TDD`
  - References: REQ-005
  - Evidence:
  - Files: `infrastructure/services/ConfigServiceBukkit.kt`, `config/MainConfig.kt`, bedrock defaults in `config.yml`
- [ ] **LG-203** Consume `chat.default_channel_visibility` and `chat.colored_chat_enabled` in the chat pipeline
  - Tag: `TDD`
  - References: REQ-006
  - Evidence:
  - Files: chat services/listeners, config model
- [ ] **LG-204** Load `brewingXp` from config (operator-tunable)
  - Tag: `TDD`
  - References: REQ-018
  - Evidence:
  - Files: `config/MainConfig.kt`, `config.yml`, loader
- [ ] **LG-205** Load `modeSwitchingEnabled` from config (can be disabled)
  - Tag: `TDD`
  - References: REQ-019
  - Evidence:
  - Files: `config/MainConfig.kt`, `config.yml`, loader
- [ ] **LG-206** Load `nameFilter` / `NameFilterConfig` from config
  - Tag: `TDD`
  - References: REQ-020
  - Evidence:
  - Files: `config/MainConfig.kt`, `config.yml`, loader
- [ ] **LG-207** Load `guild.banner_copy_physical_cost` and apply it to banner-copy operations
  - Tag: `TDD`
  - References: REQ-021
  - Evidence:
  - Files: `loadGuildConfig()`, banner-copy service
- [ ] **LG-208** Gate CSV delivery on `discord_csv_delivery` (no delivery when disabled even with webhook set)
  - Tag: `TDD`
  - References: REQ-023
  - Evidence:
  - Files: `ConfigServiceBukkit.kt:276`, `DiscordCsvService.kt`
- [ ] **LG-209** Ship `parties_enabled` in the config.yml defaults
  - Tag: `TDD`
  - References: REQ-029
  - Evidence:
  - Files: `src/main/resources/config.yml`, DI parties module

---

## PR-3 — Bank features (knobs + real menus)

- [ ] **LG-301** Enforce bank config: interest accrual task, max balance, audit retention, suspicious-transaction detection + auto-lock
  - Tag: `TDD`
  - References: REQ-009
  - Evidence:
  - Files: `infrastructure/services/BankServiceBukkit.kt`, bank config model
- [ ] **LG-302** Bank automation menu: persisted settings, real save, real next-run time + status
  - Tag: `TDD`
  - References: REQ-010
  - Evidence:
  - Files: `interaction/menus/GuildBankAutomationMenu.kt`, automation persistence
- [ ] **LG-303** Bank budget menu: real persisted budget + save
  - Tag: `TDD`
  - References: REQ-011
  - Evidence:
  - Files: `interaction/menus/GuildBankBudgetMenu.kt`, budget persistence
- [ ] **LG-304** Bank transaction history: renders actual transactions; search/type/member/date filters functional
  - Tag: `TDD`
  - References: REQ-012
  - Evidence:
  - Files: `interaction/menus/GuildBankTransactionHistoryMenu.kt`, transaction repository
- [ ] **LG-305** Bank security menu: dual-auth threshold setting implemented
  - Tag: `TDD`
  - References: REQ-031
  - Evidence:
  - Files: bank security menu, dual-auth config

---

## PR-4 — Combat & wars (knobs + real services)

- [ ] **LG-401** Enforce combat config: war duration, grace period, max simultaneous wars, kill/win/lose XP, kill cooldown, same-player kill limit, anti-griefing
  - Tag: `TDD`
  - References: REQ-008
  - Evidence:
  - Files: `infrastructure/services/WarServiceBukkit.kt`, combat listener
- [ ] **LG-402** Implement `CombatServiceBukkit.getPlayerGuilds()` and `getRelationType()` against the guild/relation domain
  - Tag: `TDD`
  - References: REQ-014
  - Evidence:
  - Files: `infrastructure/services/CombatServiceBukkit.kt:119-129`
- [ ] **LG-403** War declaration accept/decline flow (no instant auto-accept)
  - Tag: `TDD`
  - References: REQ-024
  - Evidence:
  - Files: `WarServiceBukkit.kt:80`, declaration menu
- [ ] **LG-404** Load and enforce `combat.war_farming_cooldown_hours`
  - Tag: `TDD`
  - References: REQ-026
  - Evidence:
  - Files: `config.yml:408`, war service
- [ ] **LG-405** War declaration escrow withdraw completed in the war service
  - Tag: `TDD`
  - References: REQ-039
  - Evidence:
  - Files: `GuildWarDeclarationMenu.kt:527`, war escrow service

---

## PR-5 — Claims, peaceful mode & vault (Section B residuals)

- [ ] **LG-501** Enforce peaceful-mode flags: claim PVP disabled, war declarations blocked
  - Tag: `TDD`
  - References: REQ-007
  - Evidence:
  - Files: claim PVP listener, war service
- [ ] **LG-502** Vault placement validates against claims when claims are enabled
  - Tag: `TDD`
  - References: REQ-015
  - Evidence:
  - Files: `GuildVaultServiceBukkit.kt:273-276`, claim domain
- [ ] **LG-503** Load and consume `peacefulGuildPvpOptIn` per guild
  - Tag: `TDD`
  - References: REQ-027
  - Evidence:
  - Files: GuildConfig, peaceful-mode service

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
