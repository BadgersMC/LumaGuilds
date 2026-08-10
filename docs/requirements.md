# LumaGuilds — Requirements (SPEAR)

Scope: the 2026-08-09 unwired/incomplete-feature audit (`docs/audit/MASS-AUDIT-2026-06-08.md` is the earlier, separate June batch — not in scope). Audit doc: `lumaguilds-audit-2026-08-09.md` (repo root, Aug 9). Each finding becomes one EARS requirement; tasks are derived in `docs/tasks.md` and grouped into PRs.

Legend: **Ubiquitous.** / **Event-driven.** / **State-driven.** / **Unwanted.**

---

## Section A — Critical: permissions

### REQ-001
**Unwanted.** IF a player holds the declared `lumaguilds.bedrock.cache.stats` or `lumaguilds.bedrock.cache.clear` permission THEN THE SYSTEM SHALL NOT deny them via the stale `lumalyte.*` prefix in `BedrockCacheStatsCommand`, AND the command SHALL authorize via the exact declared nodes `lumaguilds.bedrock.cache.stats` / `lumaguilds.bedrock.cache.clear` only.

> Audit C1: `interaction/commands/BedrockCacheStatsCommand.kt:21,55,74,80` checks `lumalyte.bedrock.cache.*`; `plugin.yml:255,258` declares `lumaguilds.bedrock.cache.*`. Align code and plugin.yml to the same prefix.

### REQ-002
**Event-driven.** WHEN a player executes one of the guild subcommands `join`, `list`, `lfg`, `decline`, `invites`, `leave`, `transfer`, `getvault`, `vault`, `help`, `ally`, `enemy`, `truce`, `neutral` THEN THE SYSTEM SHALL authorize it via the corresponding `lumaguilds.guild.<sub>` node, declared with `default: true` both as a child of the `lumaguilds.guild.*` wildcard and as an individual plugin.yml node.

> Audit C2: `GuildCommand.kt:1283,1399,1439,1449,1477,1569,1652,1995,2108,2174,2201,2274,2342,2410` — nodes used but never declared; ACF defaults them to false and silently blocks execution.

### REQ-003
**Ubiquitous.** THE SYSTEM SHALL grant `lumaguilds.claim.partitions`, `lumaguilds.claim.trustlist`, `lumaguilds.claimmenu`, and `lumaguilds.claimoverride` to every holder of the `lumaguilds.command.*` wildcard, and SHALL keep each of the four nodes declared both individually (`default: op`) and as children of that wildcard.

> Audit C2 (wildcard gap): the audit claimed these four nodes were missing from the wildcard's children set — re-verified against code + plugin.yml: they ARE declared (plugin.yml:165,170,175,176 + individual blocks). REQ-003 now locks both declaration forms.

---

## Section B — High: dead config, dead services, dead UI

### REQ-004
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load the entire `vault:` config section (config.yml:165-299: bank_mode, physical_currency, compressable_blocks, and all other vault keys) into the config model and apply it at runtime.

> Audit H1: no `loadVaultConfig()` exists in `infrastructure/services/ConfigServiceBukkit.kt`; admin edits have zero effect.

### REQ-005
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load the `bedrock:` config section (config.yml:673-735: icon URLs, cache settings, menu toggles) and apply it, AND SHALL ship production-usable icon defaults instead of `https://via.placeholder.com/...`.

> Audit H2: no `loadBedrockConfig()`; shipped icon defaults are placeholder-hosted images.

### REQ-006
**Event-driven.** WHEN chat settings are loaded THEN THE SYSTEM SHALL consume `chat.default_channel_visibility` and `chat.colored_chat_enabled` in the chat pipeline.

> Audit H3 (chat): both keys parsed into MainConfig, never read by any service/listener.

### REQ-007
**State-driven.** WHILE a guild is in peaceful mode THEN THE SYSTEM SHALL enforce `guild.peaceful_mode_claim_pvp_disabled` (no PVP inside its claims) and `guild.peaceful_mode_prevent_wars` (war declarations blocked).

> Audit H3 (guild): both flags parsed, never consumed. Also REQ-027 (M11) for the related opt-in field.

### REQ-008
**Ubiquitous.** THE SYSTEM SHALL enforce the combat configuration — `anti_griefing_enabled`, `war_duration_hours`, `war_end_grace_period_minutes`, `max_simultaneous_wars`, `kill_experience`, `war_win_experience`, `war_lose_experience`, `kill_cooldown_minutes`, `same_player_kill_limit`.

> Audit H3 (combat): 9 knobs parsed, never consumed. No war cap/duration enforcement in `WarServiceBukkit`; no anti-grief listener.

### REQ-009
**Ubiquitous.** THE SYSTEM SHALL enforce the bank configuration — interest accrual per `bank.interest_rate_percent` and `bank.interest_compound_period_hours`, `bank.max_bank_balance`, `bank.audit_log_retention_days`, `bank.suspicious_transaction_threshold`, and `bank.auto_lock_suspicious_accounts`.

> Audit H3 (bank): 6 knobs parsed, never consumed. No interest-accrual task exists in `BankServiceBukkit`.

### REQ-010
**Event-driven.** WHEN a player opens the guild bank automation menu THEN THE SYSTEM SHALL display persisted automation settings (not the hardcoded `interestRate=0.02` fakes), persist changes through the save action, and render the real next-run time and status.

> Audit H4: `GuildBankAutomationMenu.kt` — `loadAutomationSettings()` returns fakes, save is a no-op message, next-run = now+1h, "Status: Healthy" hardcoded, 4 "coming soon" buttons (294, 319, 336, 353).

### REQ-011
**Event-driven.** WHEN a player opens the guild bank budget menu THEN THE SYSTEM SHALL display the guild's real persisted budget (`monthly`/`weekly`/`daily`) and persist changes through the save action.

> Audit H5: `GuildBankBudgetMenu.kt` — hardcoded `monthly=10000/weekly=2500/daily=500`, no save, 3 "coming soon" buttons (222, 239, 256).

### REQ-012
**Event-driven.** WHEN a player opens the guild bank transaction history menu THEN THE SYSTEM SHALL render the guild's actual transactions and make the search, type, member, and date filters functional.

> Audit H6: `GuildBankTransactionHistoryMenu.kt` — transaction items never added ("when API resolved"), filters are "coming soon" no-ops (219, 265, 443, 451, 459).

### REQ-013
**Event-driven.** WHEN the statistics menu requests a map render THEN THE SYSTEM SHALL produce real rendered maps for overview, trend, comparison, and proportion views, AND SHALL run the declared TTL cache cleanup.

> Audit H7: `MapRendererServiceBukkit.kt` — all 4 render methods return blank maps, renderer services commented out, `isAvailable()` hardcodes true, cache cleanup never scheduled.

### REQ-014
**Ubiquitous.** THE SYSTEM SHALL implement `CombatServiceBukkit.getPlayerGuilds()` and `getRelationType()` against the guild/relation domain instead of returning `emptySet()` and hardcoded `NEUTRAL`.

> Audit H8: `CombatServiceBukkit.kt:119-129` — inert placeholders; any combat/relation logic sees nothing.

### REQ-015
**Event-driven.** WHEN a player places a guild vault THEN THE SYSTEM SHALL validate the placement against claims whenever claims are enabled.

> Audit H9: `GuildVaultServiceBukkit.kt:273-276` — `// TODO: Add claim validation when claims are enabled`; vaults place anywhere.

### REQ-016
**Ubiquitous.** THE SYSTEM SHALL render guild, bank, war, and admin command messages through the localization system (`lang/defaults/*.properties`), replacing hardcoded `§`-strings, and SHALL leave no lang keys unreferenced.

> Audit H10: ~200 guild/bank/war/progression/command/error/menu keys have zero `translate()` calls; only the claims UI + Bedrock forms use the lang system.
> Decision flag: audit offers "migrate commands to lang OR delete dead keys". Approved direction (finish features): migrate; delete nothing.

---

## Section C — Medium

### REQ-017
**Event-driven.** WHEN the plugin starts THEN THE SYSTEM SHALL either register `ShopIntegrationService` in DI with real consumers or remove it.

> Audit M1: dead class, never registered in DI, no consumers.
> Decision flag: default = remove (no consumers); flip to wire if a shop integration is planned.

### REQ-018
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load `brewingXp` (default 3) from config so operators can tune it.

> Audit M2: `MainConfig.kt:398` — field exists, no yml key/loader.

### REQ-019
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load `modeSwitchingEnabled` (default true) from config so it can be disabled.

> Audit M3: `MainConfig.kt:111` — field exists, no loader.

### REQ-020
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load `nameFilter` / `NameFilterConfig` (50+ regex patterns) from config.

> Audit M4: `MainConfig.kt:107` — field exists, no loader.

### REQ-021
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load `guild.banner_copy_physical_cost` (config.yml:143) and apply it to banner-copy operations.

> Audit M5: `loadGuildConfig()` never reads the key.

### REQ-022
**Ubiquitous.** THE SYSTEM SHALL apply the `ui.*.enchanted` menu-item setting so configured items render with the enchantment glow.

> Audit M6: `MenuItemConfig.kt:338` parses it; menu builders never apply it.

### REQ-023
**Ubiquitous.** THE SYSTEM SHALL NOT ship the CSV export feature: `DiscordCsvService`, `FileExportManager`, `CsvExportService`, the `/bellclaims download|exports|cancel` commands, the bank-history / member-contributions menu export buttons, the `EXPORT_BANK_DATA` rank permission, and the `discord_webhook_url` / `discord_csv_delivery` config keys SHALL be removed.

> Decision flag (2026-08-10): Badger chose full removal over gating delivery on `discord_csv_delivery`. Removal also eliminates the audit M-finding (hardcoded `i.imgur.com/placeholder.png` avatar in `DiscordCsvService.kt:255`) and the dead `temp_exports` book-download path in `LumaGuildsCommand`.

### REQ-024
**Event-driven.** WHEN a war is declared THEN THE SYSTEM SHALL run the accept/decline declaration flow instead of auto-accepting immediately.

> Audit M8: `WarServiceBukkit.kt:80` — `TODO`; wars auto-accept.

### REQ-025
**Ubiquitous.** THE SYSTEM SHALL resolve Nexo emoji glyphs through the public API without reflection into FontManager.

> Audit M9: `NexoEmojiService.kt:198` — reflection hack, TODO to use API directly.
> Decision flag: if Nexo has no public API for glyph resolution, keep the reflection isolated behind the service interface and document why.

### REQ-026
**Event-driven.** WHEN the plugin loads THEN THE SYSTEM SHALL load `combat.war_farming_cooldown_hours` (config.yml:408) and enforce it.

> Audit M10: key never loaded; always 1h default.

### REQ-027
**Event-driven.** WHEN peaceful mode is toggled THEN THE SYSTEM SHALL load and consume `peacefulGuildPvpOptIn` per guild.

> Audit M11: dead field in GuildConfig, never loaded nor consumed. Related to REQ-007.

### REQ-028
**Ubiquitous.** THE SYSTEM SHALL NOT hardcode the Discord CSV avatar URL — it SHALL be configurable.

> Audit M12: `DiscordCsvService.kt:255` hardcodes `https://i.imgur.com/placeholder.png`.

### REQ-029
**Ubiquitous.** THE SYSTEM SHALL ship `parties_enabled` in the shipped `config.yml` defaults.

> Audit M13: feature reads with default true but the key is absent from the defaults file, so it can't be disabled from defaults.

---

## Section D — Low: player-facing "coming soon" stubs

### REQ-030
**Event-driven.** WHEN a player opens the disband, leave, rank-list, or promotion confirmation menus THEN THE SYSTEM SHALL run the real operation instead of sending "coming soon!" and navigating back.

> Audit: `GuildDisbandConfirmationMenu.kt:17`, `GuildLeaveConfirmationMenu.kt:17`, `GuildRankListMenu.kt:12`, `GuildPromotionMenu.kt:17` — whole Java menus are stubs (Bedrock equivalents exist).

### REQ-031
**Event-driven.** WHEN a player opens the bank security menu THEN THE SYSTEM SHALL implement the dual-auth threshold setting instead of showing "coming soon".

> Audit: bank security ×1 (dual-auth threshold) click handler.

### REQ-032
**Event-driven.** WHEN a player opens a statistics detail view THEN THE SYSTEM SHALL render the 14 currently-stubbed drill-downs (kill stats, contributions, etc.) in addition to the implemented charts.

> Audit: 14/17 statistics detail views stubbed; charts implemented.

### REQ-033
**Event-driven.** WHEN a player opens war management THEN THE SYSTEM SHALL implement the 7 stub buttons (details, list, incoming, outgoing, stats, history, detailed).

> Audit: 7 war-management buttons are "coming soon" no-ops.

### REQ-034
**Event-driven.** WHEN a player opens party management THEN THE SYSTEM SHALL implement the 5 stub buttons (details, list, send request, create, access settings).

> Audit: 5 party buttons are "coming soon" no-ops.

### REQ-035
**Event-driven.** WHEN a player opens rank creation or rank edit THEN THE SYSTEM SHALL implement permission-category selection (RankCreationMenu:388) and rank reset (RankEditMenu:385).

> Audit: both handlers stubbed.

### REQ-036
**Event-driven.** WHEN a player interacts with guild settings, relations, or statistics menus THEN THE SYSTEM SHALL implement the remaining stub items: settings name-edit lore (GuildSettingsMenu.kt:77), enemies list (EnemiesListMenu.kt:232), peace agreement (PeaceAgreementMenu.kt:375,379), bank statistics tax system (GuildBankStatisticsMenu.kt:410,417), and statistics online tracking (GuildStatisticsMenu.kt:158).

> Audit: misc menu stubs; tax system text says "coming in a future update".

### REQ-037
**Ubiquitous.** THE SYSTEM SHALL ship no `.coming.soon` lang keys in the Bedrock forms properties.

> Audit: 11 keys in `lang/bedrock/forms.properties` (bank automation/budget/security, claim player/wide permissions, claim edit tool, party management ×2, war detailed stats, relation details, member list invite).

### REQ-038
**Event-driven.** WHEN a Bedrock player opens the bank budget, bank automation, bank security, claim player-permissions, claim wide-permissions, or edit-tool forms THEN THE SYSTEM SHALL present functional forms instead of read-only "coming soon" info forms.

> Audit: `BedrockGuildBankBudgetMenu`, `BedrockGuildBankAutomationMenu`, `BedrockGuildBankSecurityMenu`, `BedrockClaimPlayerPermissionsMenu`, `BedrockClaimWidePermissionsMenu`, `BedrockEditToolMenu` — read-only placeholders.

### REQ-039
**Event-driven.** WHEN a war declaration's escrow is withdrawn THEN THE SYSTEM SHALL complete the withdraw in the war service.

> Audit: `GuildWarDeclarationMenu.kt:527` — "will be implemented in the war service" (never).

### REQ-040
**Event-driven.** WHEN a player selects "return to LFG" in the join-requirements menu THEN THE SYSTEM SHALL reopen the LFG menu instead of closing the inventory.

> Audit: `JoinRequirementsMenu.kt:157`.

### REQ-041
**Event-driven.** WHEN a Bedrock player opens a localized form THEN THE SYSTEM SHALL detect the Floodgate locale instead of always falling back to the Minecraft locale.

> Audit: `BedrockLocalizationServiceFloodgate.kt:52`.

### REQ-042
**Ubiquitous.** THE SYSTEM SHALL construct `BaseBedrockMenu` via DI rather than the service-locator hack.

> Audit: `BaseBedrockMenu.kt:576`.

### REQ-043
**Event-driven.** WHEN a Bedrock player opens the join-requirements flow THEN THE SYSTEM SHALL use the Bedrock flow rather than the Java menu fallback.

> Audit: `MenuFactory.kt:1065`.

### REQ-044
**Event-driven.** WHEN a Bedrock player toggles auto-deposit in the guild bank menu THEN THE SYSTEM SHALL persist and apply the real setting.

> Audit: `BedrockGuildBankMenu.kt:77,301` — toggle hardcoded false; no-op fakes success.

---

## Section E — Domain purity (deferred)

### REQ-045
**Ubiquitous.** THE SYSTEM SHALL keep the `domain/**` layer free of framework/server imports (`org.bukkit`, `org.koin`, `co.aikar`, `net.kyori`), decoupling the 21 domain files (38 imports — mostly `domain/events/*` extending `org.bukkit.event.Event`) from Bukkit so the `forbidden:` contract in `docs/implementation.md` becomes enforceable.

> Origin: CodeRabbit PR #89 comment on `docs/implementation.md:21` — `forbidden: []` is not consumed, and Konsist's `dependsOnNothing()` only checks declared layers, so domain→org.bukkit imports pass the guard. Deferred to PR-10 (LG-1001); when merged, LayerRulesTest gains an external-package assertion and the forbidden list is populated.
