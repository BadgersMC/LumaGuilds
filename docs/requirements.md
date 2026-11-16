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

**Event-driven.** WHEN a player withdraws through the guild bank menu THEN THE SYSTEM SHALL credit the player's personal Vault Economy account on the server thread without creating inventory items. Withdraw All SHALL resolve the current affordable amount including configured withdrawal fees. A rejected payout SHALL restore the guild debit including fees. A thrown payout with an unchanged, verified personal balance SHALL also restore the debit; an ambiguous provider outcome SHALL be logged for administrator reconciliation without issuing a speculative refund.

**Event-driven.** BEFORE an external withdrawal payout THE SYSTEM SHALL persist a pending payout journal and confirm the guild debit has been written to the database. Failed debit persistence SHALL prevent payment. Completion or a confirmed persisted refund SHALL resolve the journal. Unresolved payouts SHALL block further withdrawals for that guild across restarts, remain available despite routine audit retention, and display an administrator-review warning with the transaction ID in Java and Bedrock menus. Provider balance lookup failures before debit SHALL reject without moving funds.

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
WHEN a guild withdrawal is offered THEN THE SYSTEM SHALL preview the calculated fee and total guild deduction; WHEN it succeeds THEN THE SYSTEM SHALL report the amount delivered, destination, actual charged fee and total deduction.

**Event-driven.** WHEN a player places a guild vault THEN THE SYSTEM SHALL validate the placement against claims whenever claims are enabled.

> Audit H9: `GuildVaultServiceBukkit.kt:273-276` — `// TODO: Add claim validation when claims are enabled`; vaults place anywhere.

### REQ-016
**Ubiquitous.** THE SYSTEM SHALL render guild, bank, war, and admin messages exclusively from MiniMessage locale values, SHALL contain no legacy `§` or `&` formatting in locale resources, SHALL use Adventure `Component` output wherever the destination API supports it, and SHALL leave no lang keys unreferenced. THE SYSTEM SHALL apply opaque black text shadow to Component-capable output. THE SYSTEM SHALL render translated menu titles, item names, and item lore in small caps while preserving digits, punctuation, glyphs, and dynamically supplied proper names in their original spelling; chat text SHALL retain normal casing. String-only destinations such as Floodgate forms SHALL receive plain text and are not required to preserve shadows.

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
**Ubiquitous.** THE SYSTEM SHALL NOT ship a Discord CSV avatar URL configuration.

> **SUPERSEDED by REQ-023** (2026-08-10): the entire CSV export feature — including `DiscordCsvService` and its hardcoded avatar — was removed. This requirement is obsolete; retained only as an audit trail (originally Audit M12: `DiscordCsvService.kt:255` hardcoded `https://i.imgur.com/placeholder.png`).

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
**Ubiquitous.** THE SYSTEM SHALL keep the `domain/**` layer free of framework/server imports (`org.bukkit`, `org.koin`, `co.aikar`, `net.kyori`), decoupling the 20 domain files (38 imports — mostly `domain/events/*` extending `org.bukkit.event.Event`) from Bukkit so the `forbidden:` contract in `docs/implementation.md` becomes enforceable.

> Origin: CodeRabbit PR #89 comment on `docs/implementation.md:21` — `forbidden: []` is not consumed, and Konsist's `dependsOnNothing()` only checks declared layers, so domain→org.bukkit imports pass the guard. Deferred to PR-10 (LG-1001); when merged, LayerRulesTest gains an external-package assertion and the forbidden list is populated.
---

## Section F — Operator backlog (Aug 11, Fain)

> Product backlog promoted to SPEAR requirements. Origin: operator notes (Fain),
> parts 1+2 — see git history on `docs/fain-backlog`. Items are actionable but
> not yet scheduled; PR grouping lives in `docs/tasks.md` (PR-11..PR-15).

### REQ-046
**Event-driven.** WHEN a player runs `/g balance` or `/g baltop` THEN THE SYSTEM SHALL return correct balances/leaderboard data, AND `/g balance` SHALL tab-complete all guild names on the server.

### REQ-047
**Event-driven.** WHEN a guild has set an emoji THEN THE SYSTEM SHALL allow clearing/removing it (currently impossible once set).

### REQ-048
**Conditional.** GIVEN an operator config entry mapping a guild name (string) to an emoji permission, WHEN that guild exists THEN THE SYSTEM SHALL grant all its members the configured Nexo permission (chat + guild-emoji usage); WHEN the mapping is removed, the guild is renamed/disbanded, or a member leaves THEN THE SYSTEM SHALL revoke the permission; WHEN an operator changes a mapping from permission A to permission B THEN THE SYSTEM SHALL revoke A and grant B (configuration-value replacement) so no guild-scoped Nexo permission outlives its grant.

### REQ-049
**State-driven.** THE SYSTEM SHALL maintain guild run progression from level 1 through level 100. Reaching target level `L` from `L - 1` SHALL require `floor(500 * L^1.15 + L * 150)` XP, totaling 5,446,893 cumulative XP to reach level 100 from level 1. Current-run XP and achieved level SHALL NOT decrease through ordinary play, war results, chapter rollover, or seasonal-rating reset; they MAY reset only through the explicitly confirmed, atomic prestige transition in REQ-093. XP SHALL accrue only from configured explicit activity sources and shared weekly guild quests. Every repeatable configured source pool SHALL have one fixed guild-wide period cap; there SHALL be no per-player cap and no combined daily guild cap. A pool reaching its cap SHALL NOT prevent another pool from awarding XP. Weekly guild quest rewards and their completion bonus SHALL remain outside all daily source caps.

### REQ-050
**Documented.** THE SYSTEM SHALL ship a complete level 1–100 reward tier list for each progression run. Every level SHALL provide an automatic visible benefit or level-up event; ordinary non-checkpoint levels SHALL advance the guild-bank capacity track, each level divisible by 5 SHALL unlock a raw-gold-purchasable numeric perk, and each level divisible by 10 SHALL unlock a raw-gold-purchasable major perk. The tier list SHALL state each perk's price, effect, persistence across prestige, and prestige-selection eligibility. Guild membership capacity SHALL be a fixed configurable limit (default 50) independent of level and prestige. Displayed levels 101–200 SHALL represent seasonal Elo rank only and SHALL NOT be treated as XP levels or grant progression rewards unless a later requirement explicitly defines a seasonal reward.

### REQ-051
**Conditional.** GIVEN two guilds are both current-run level 100 and complete a rated guild war, THEN THE SYSTEM SHALL update each guild's seasonal Elo using true opponent-weighted Elo with configurable `k_factor` (default 40), starting and floor rating 1000, and expected score `1 / (1 + 10^((opponentRating - guildRating) / 400))`. A guild pair SHALL produce at most one fully rated result during the configured rematch window (default 7 days). Seasonal Elo SHALL map monotonically to displayed levels 101–200, with the default upper display threshold at 1600 Elo and ratings above that threshold remaining level 200. Prestige SHALL NOT reset seasonal Elo or rated-pair history; a prestiged guild becomes ineligible for new rated results until it reaches current-run level 100 again.

### REQ-052
**Event-driven.** WHEN an operator enables an "increased XP" period (e.g. double-XP weekend) THEN THE SYSTEM SHALL multiply applicable current-run XP awards before source-cap reservation while leaving every configured source cap fixed; the accepted award SHALL be limited to the source's remaining allowance.

### REQ-053
**Event-driven.** WHEN a rated guild war resolves THEN THE SYSTEM SHALL add or remove seasonal Elo according to REQ-051 without changing current-run XP or current-run level. Unrated wars and pre-level-100 wars SHALL NOT change seasonal Elo.

### REQ-054
**Conditional.** GIVEN raw-gold economy, THE SYSTEM SHALL require raw gold to create a guild and to activate each guild home, with costs scaling higher as more homes are unlocked. Cost model (contract): home #1..N costs `baseCost * scale^(n-1)`. Only the nine purchased Chapter 2 Guild Outpost rewards SHALL add home capacity during the level 1–100 run. Purchased home capacity and activated home locations SHALL be permanent guild assets and SHALL NOT be revoked by prestige, seasonal-rating changes, or chapter rollover. Prestige SHALL grant exactly one additional permanent home-capacity unit per successful transition, subject to the configured lifetime prestige maximum. Earned Chapter 2 capacity SHALL be `1 base + up to 9 purchased Guild Outposts + up to 6 completed prestiges`, with an absolute maximum of 16. A legacy Chapter 1 saved-home count SHALL NOT inflate this earned entitlement. Existing saved homes above earned capacity SHALL remain readable and usable subject to normal permission and activation checks, including updates to their existing names, but no new named home SHALL be admitted until earned capacity exceeds the count of saved homes. Java and Bedrock home-placement entry points SHALL reject known over-cap additions before any guild-gold debit, while an independent write-time guard rechecks capacity for serialized Bukkit requests. Cross-instance writes still require transactional serialization.

### REQ-055
**State-driven.** THE SYSTEM SHALL enforce a 15-day guild-creation cooldown for players who create and then delete a guild within 7 days of creation (contract: `create_then_delete_window_days = 7`, `creation_cooldown_days = 15`, both operator-configurable). Deleting a guild older than the window does not start a cooldown. The cooldown starts at deletion time and blocks that player from creating a new guild until it expires.

**Conditional.** GIVEN a player explicitly holds `lumaguilds.guild.creationcooldown.bypass`, WHEN that player creates a guild during their creation cooldown, THE SYSTEM SHALL allow cooldown admission without clearing or shortening the stored cooldown; all other creation checks and costs SHALL remain in force. Players without the permission SHALL retain the existing cooldown behavior. The permission SHALL default to false.

### REQ-056
**Documented.** THE SYSTEM SHALL treat the prior level-200, unbounded-prestige proposal as superseded. Prestige SHALL use the bounded level-100 contract in REQ-093, SHALL reset only current-run level/XP and non-permanent purchased perks, and SHALL NOT reset seasonal Elo, source-cap usage, weekly-quest state, guild membership, ranks, relations, vault contents, canonical guild gold, activated homes, permanent home capacity, or previously selected permanent prestige perks.

### REQ-057
**Event-driven.** WHEN a guild war is active THEN THE SYSTEM SHALL accurately track player kills and make them actually impact gameplay (war system overhaul; residual gaps after PR-4). Measurable contract: each war carries a kill counter per guild that (a) increments only on kills of opposing-guild members during the active war, (b) resets when the war ends, (c) is persisted so restarts do not lose it, and (d) drives war resolution — a guild whose counter reaches `combat.war_kill_win_target` (config, default 25) wins the war; the counter is also surfaced in `/g info` and war menus so its gameplay impact is observable.

### REQ-058
**Conditional.** GIVEN a secret server-side predicate is met THEN THE SYSTEM SHALL trigger a massive, server-wide World War involving all guilds. Contract: (a) the predicate is defined in config as an operator-tunable expression with a documented default (initial default: a single guild reaches level 150 or total server guild level sum exceeds a configured threshold); (b) the predicate is evaluated on a fixed interval (config: `world_war.evaluation_interval_minutes`, default 5) and at guild-level-up; (c) the trigger is idempotent — it fires at most once per cooldown period (config: `world_war.cooldown_days`, default 30) and never re-fires while a World War is active; (d) a config/test override flag (`world_war.debug_force`) exists so the trigger can be exercised deterministically in tests.

### REQ-059
**Event-driven.** WHEN a guild member places a war banner THEN THE SYSTEM SHALL (a) create a tactical teleport point for guild members bypassing teleport requests/guild-home slots, (b) make it destructible by any player, (c) last 15 minutes, (d) cost raw gold, (e) enforce one active banner per guild + placement cooldown, (f) require a specific guild rank permission, AND (g) broadcast `[Guild Name] has placed down a war banner.`

### REQ-060
**Event-driven.** WHEN war is declared on a guild THEN THE SYSTEM SHALL (a) show a prominent in-game alert to online members, AND (b) persist an unread declaration notice per guild member; WHEN a member who was offline at declaration time logs in THEN THE SYSTEM SHALL replay the pending notice and mark it read/acknowledged; WHEN a war ends THEN THE SYSTEM SHALL broadcast victory/loss messages server-wide.

### REQ-061
**Conditional.** GIVEN configurable war win conditions, THE SYSTEM SHALL support (a) required unique opposing-player kill counts (dupes excluded), (b) a ransom fee to surrender/end the war, (c) a "Champion" death-duel mode deciding the outcome, AND (d) XP boost/deduction for winner/loser (high-stakes).

### REQ-062
**Event-driven.** WHEN a guild member logs into the server THEN THE SYSTEM SHALL notify the guild in-game.

### REQ-063
**State-driven.** THE SYSTEM SHALL display each member's current guild rank next to their name in guild chat by default. Guild leaders or members with MANAGE_GUILD_SETTINGS SHALL be able to toggle rank display for the entire guild; the setting SHALL persist across restarts and apply to the next guild-chat message without changing other guilds, public, ally, or officer chat. Guild rank names SHALL support legacy color/format codes and hex colors (including bare `#RRGGBB` and `&#RRGGBB`), with a maximum of 24 visible characters and 255 stored characters. Rank creation/editing on Java and Bedrock SHALL use the same validation; color changes SHALL NOT create duplicate visible rank names or change rank IDs, member assignments, permission sets, or claim-permission profiles. Validation errors SHALL display readable text. Per-message RoseChat formatting SHALL preserve every option supplied by the runtime API, including newer bypass flags, without linking to a version-specific record constructor.

### REQ-064
**Conditional.** GIVEN guild leadership, THE SYSTEM SHALL provide a dedicated private chat channel for guild admins/leadership only.

### REQ-065
**Conditional.** GIVEN RoseChat feasibility, THEN THE SYSTEM SHALL let guilds create and name custom chat channels for their own organizational structure.

### REQ-066
**Event-driven.** WHEN a member successfully persists a guild invitation THEN THE SYSTEM SHALL atomically append an immutable sent-invitation history event keyed by guild, inviter UUID, invitee UUID, and send time. Accepting, declining, expiring, or otherwise removing the pending invitation SHALL NOT remove that history. Failed or duplicate pending-invitation writes SHALL NOT increment statistics. The Guild Statistics UI SHALL display the guild's all-time invitation leaderboard ordered by sent count descending with inviter UUID as the deterministic tie-breaker, resolving the inviter's current player name only for display. The leaderboard SHALL paginate across all historical inviters rather than truncate the result set.

### REQ-067
**State-driven.** THE SYSTEM SHALL support admin-created physical leaderboard banners bound to a positive rank and guild leaderboard category, including Guild Level placement. A bound banner SHALL persist its world location, rank, and category; SHALL render the current guild's configured banner or a plain white banner when the rank/guild/banner is unavailable; SHALL refresh when the relevant leaderboard rank changes or the displayed guild changes its banner; and SHALL periodically reconcile persisted displays so stale or changed state is corrected after missed events or restarts.

### REQ-068
**Event-driven.** WHEN a player opens the guild list GUI THEN THE SYSTEM SHALL list all server guilds with sort options: All-Time Active, Weekly Active, Guild Level (low→high), and Creation Date (old→new). All-Time Active SHALL use the existing weighted guild progression-activity sources across all recorded history. Weekly Active SHALL use the same weighted activity over the trailing seven days, except raw `PLAYER_KILL` activity SHALL NOT score directly; instead PvP SHALL contribute only through distinct opposing-player victims multiplied by the configured `activity.weights.kills_this_week` weight so repeated kills of the same victim cannot inflate the weekly ranking. Retrieval SHALL be bounded at the service boundary — the lookup action accepts `(page, pageSize, sortKey, ascending)` and returns one page plus a total count, never a full `List` sliced in the GUI (the existing `GuildLookup.getAllGuilds()` unbounded path is NOT used). Page size SHALL default to `guild_list.page_size` (default 18), navigation SHALL expose previous/next controls, and sorting SHALL be deterministic with case-insensitive guild name then creation date as stable tie-breakers.

### REQ-069
**Conditional.** GIVEN the guild list GUI, THEN THE SYSTEM SHALL display each guild's stored physical standing-banner item, preserving its material/base color and pattern layers. Missing, unreadable, corrupt, non-banner, or block-only wall-banner payloads SHALL resolve to a plain white standing banner.

### REQ-070
**Event-driven.** WHEN a player clicks the Enemy/Ally sections of `/g info` THEN THE SYSTEM SHALL expand to the full guild list (currently only top 3, no way to view the rest).

### REQ-071
**Event-driven.** WHEN a guild reaches the configured minimum guild level (default 50), THEN THE SYSTEM SHALL create/link a Discord role and dynamically grant/remove it for Discord-linked players as they join/leave the guild or link/unlink their Discord account. Startup and periodic reconciliation SHALL automatically delete existing managed roles and their saved links for guilds below the minimum with no completed prestige, including roles created during earlier testing. Failed Discord deletion SHALL retain the link for retry. Those guilds SHALL wait until the minimum before the role is recreated. A guild with at least one completed prestige SHALL remain eligible to keep/create/repair its role after the level reset. Failed eligibility reads SHALL preserve the saved role/link for retry. Disbanded guild roles SHALL still be removed.

### REQ-072
**Event-driven.** WHEN a guild edits its description THEN THE SYSTEM SHALL accept up to 200 characters of safe display MiniMessage formatting and SHALL recognize HTTPS Discord invite URLs from `discord.gg/<code>` and `discord.com/invite/<code>`. Discord invite URLs SHALL be the only external URLs promoted to clickable `OPEN_URL` components; arbitrary URLs SHALL remain inert text. User-authored interactive MiniMessage event tags including `click`, `hover`, and `insertion` SHALL be rejected at every write path. Java guild-info SHALL expose detected Discord invites through a clickable chat component, while Bedrock SHALL preserve the visible invite URL as plain text.

### REQ-073
**Event-driven.** WHEN a guild is successfully disbanded THEN THE SYSTEM SHALL broadcast a server-wide chat announcement and SHALL send an advancement toast to every online member of each guild that had an active ALLY or ENEMY relation with the disbanded guild immediately before deletion. Relationship recipients SHALL receive the global chat announcement as well as the toast. Relation membership SHALL be snapshotted before persistence cleanup removes relation rows. Toast delivery SHALL use the same player/advancement path for Java and Geyser/Bedrock clients; the toast title SHALL independently identify the relation and disbanded guild because Bedrock may not render the advancement toast description line. Players who appear through multiple related guild memberships SHALL receive at most one relationship toast, with ENEMY taking precedence over ALLY.

### REQ-074
**State-driven.** WHILE weekly guild quests are enabled THE SYSTEM SHALL discover the eligible quest-target universe from registered runtime providers, deterministically generate and persist one shared weekly quest set per configured reset period, preserve it across restarts, catch up a missed reset on startup, retain prior generated sets for repetition checks, and track progress independently per guild.

### REQ-075
**Ubiquitous.** THE SYSTEM SHALL procedurally compose each weekly quest by independently selecting a trackable action, provider-owned namespaced target, sane human-rounded amount, and zero or more compatible optional conditions. Vanilla target pools SHALL be runtime-discovered from Bukkit/Paper materials, entities, recipes, and block semantics with a small technical-exclusion list rather than an operator-authored whitelist; optional providers such as Nexo SHALL expose custom content through the same target contract. THE SYSTEM SHALL reject incompatible, impossible, redundant, duplicate, recently repeated, or amount-invalid rolls with structured reasons and use bounded deterministic fallback generation. Target location metadata SHALL validate only explicit location conditions and SHALL NOT create hidden location requirements.

### REQ-076
**Event-driven.** WHEN qualifying guild-member activity occurs THEN THE SYSTEM SHALL emit the provider-owned target identity plus relevant event context and increment every matching active quest for that member's guild. Progress matching SHALL support explicit X/Z corridor conditions and other compatible conditions, retain progress beyond the milestone for leaderboard ranking, reject cancelled/creative/spectator activity, distinguish custom provider targets from vanilla backing types, and prevent player-placed blocks from satisfying `NATURAL_ONLY` break quests while allowing player-grown crops under an `ANY` provenance policy.

### REQ-077
**Event-driven.** WHEN a guild first reaches a weekly quest milestone THEN THE SYSTEM SHALL automatically settle that quest's configured Guild EXP and item rewards exactly once, mark the milestone complete without requiring a manual claim, notify online guild members with a localized toast using the quest action's custom icon when available, and continue recording all qualifying progress beyond the milestone. WHEN all milestone quests complete before reset THEN THE SYSTEM SHALL award the configured full-set Guild EXP bonus once. WHEN reset occurs THEN THE SYSTEM SHALL pay the configured first-place leaderboard Guild EXP reward for each quest before activating the next weekly set, with stable transaction identity and durable payout markers preventing duplicate rewards. Final weekly scores SHALL remain persisted for history/audit. All reward paths SHALL be idempotent.

### REQ-078
**Event-driven.** WHEN a guild member opens the main guild menu THEN THE SYSTEM SHALL provide access to a localized six-row weekly quest menu showing dynamically rendered procedural quest text, action-specific custom icons with tier/vanilla fallback, the shared quests, that guild's uncapped score, completion state, rewards, current leaderboard rank, full-set bonus state, pagination, and time remaining until reset. WHEN the member activates a quest entry THEN THE SYSTEM SHALL open a paginated quest leaderboard showing guild names, scores, ranks, completion target, the viewer guild's standing, weekly winner reward, and reset time; Java and Bedrock SHALL expose equivalent leaderboard information.

### REQ-079
**Optional feature.** WHERE PlaceholderAPI is installed THE SYSTEM SHALL expose read-only timer, dynamically rendered procedural quest-definition, guild-progress, completion, reward, and weekly-bonus placeholders with documented safe fallbacks for missing players, guilds, quests, and active weeks; placeholder evaluation SHALL NOT discover/generate a new set, reset, claim, reward, or otherwise mutate quest state.

### REQ-087
**Event-driven.** WHEN a guild member opens the main Guild Dashboard THEN THE SYSTEM SHALL show a Statistics navigation item in the bottom-right slot directly below Economy, and activating it SHALL open the guild statistics menu.

### REQ-088
**Ubiquitous.** THE SYSTEM SHALL render every Java inventory-menu item name and lore component with italic decoration explicitly disabled at every component depth while preserving colors and all other intentional text decorations.

### REQ-089
**Ubiquitous.** THE SYSTEM SHALL validate each current-run XP event before cap accounting. Creative/spectator actions, cancelled events, suspicious or AFK input identified by the EnthusiaPlaytime integration, and player-placed blocks submitted as natural mining SHALL award zero XP and consume zero cap. Source definitions, award values, caps, and cap periods SHALL be operator-configurable for any supported vanilla material or entity while shipping the balanced defaults in the Chapter 2 progression design.

### REQ-090
**Event-driven.** WHEN the configured server-wide chapter end time arrives (default chapter duration three months) THEN THE SYSTEM SHALL process rollover through persisted idempotent states `SCHEDULED`, `FROZEN`, `BACKED_UP`, `ARCHIVED`, `RESET`, `PRUNED`, and `COMPLETE`, and SHALL expose safe read-only chapter-name and time-remaining placeholders. Rollover SHALL freeze rated changes, verify a restorable pre-reset database backup, archive final standings and chapter metadata, reset eligible guild seasonal Elo to 1000, prune only configured seasonal/transient data, and preserve guild identity, membership, ranks, canonical guild gold, vault contents, activated homes, permanent home capacity, prestige count, permanent prestige perks, current-run XP/level, quest history required for audit, and other non-seasonal state. Operators SHALL have status, postpone, retry, and explicitly confirmed force controls.

### REQ-091
**State-driven.** WHEN upgrading the production installation from Chapter 1 to the Chapter 2 progression model THEN THE SYSTEM SHALL archive the complete Chapter 1 standings, reset every guild's current-run level to 1 and current-run XP to 0, initialize seasonal Elo to 1000, and record the number of canonical saved home locations as immutable historical migration evidence while granting exactly one base earned home-capacity slot. Existing persisted `initial_home_capacity` values may retain the historical location count for audit/backward compatibility but SHALL NOT inflate current earned entitlement. Migration SHALL preserve guild identity, members, ranks, relations, canonical guild gold, vault contents, and every saved home location, including homes above the earned capacity; SHALL NOT convert historical XP into Elo, prestige, perks, or economic value; and SHALL be dry-run capable, transactional, restart-safe, rollback-capable, and preceded by a verified backup.

### REQ-092
**Ubiquitous.** THE SYSTEM SHALL treat `vault_gold.balance` as the single canonical guild raw-gold-equivalent balance. Personal-account transfers through a Vault Economy provider, physical `RAW_GOLD`/compressed-block deposits, withdrawals, interest, admin credits, war costs, home activation, perk purchases, and prestige fees SHALL use one atomic guild-gold application service. Every credit route SHALL enforce the same effective capacity, transaction limits, permission, fee, suspicious-transaction, and audit rules before external currency/items are irreversibly removed. Ordinary vault-item slot capacity SHALL remain independent from guild-gold capacity. `bank_mode: BOTH` with physical currency enabled SHALL be valid and SHALL permit both personal-account and physical-item ingress into the same balance. A missing Vault Economy provider SHALL disable only personal-account transfers and SHALL NOT disable physical guild-gold or ordinary vault storage.

**Paid admission clarification (2026-09-13).** LFG join fees SHALL use this same journaled pipeline for either personal-account or physical-item payment. Admission eligibility and the default rank SHALL be validated before payment; confirmed admission replaces member deposit permission for this route. A completed join SHALL charge once and credit the canonical guild balance once. An uncertain payment, balance write, or membership completion SHALL remain pending for reconciliation and SHALL block another charge, including after restart. The caller SHALL NOT independently remove currency or issue speculative refunds.

**Durable war clarification (2026-09-14; REQ-039/092).** Declarations, war identity/status, statistics, wager amounts and settlement intent SHALL survive restart. Financial intent SHALL be recorded before a charge or refund. A completed leg SHALL not be repeated after a crash or failed state-marker write; an uncertain leg SHALL prevent speculative compensation and require reconciliation. Acceptance SHALL not publish an active paid war until funding is confirmed. A chosen settlement outcome SHALL be immutable once settlement begins. Corrupt or unavailable persistence SHALL fail closed rather than expose an empty war registry. Stale writers SHALL not overwrite a newer persisted state.

### REQ-093
**Conditional.** WHILE prestige is enabled, GIVEN a guild is current-run level 100, is below the configured lifetime maximum (default 6), has selected a purchased prestige-eligible non-permanent perk, has no active/accepted/unresolved war or outgoing declaration, and can pay the configured tier fee (defaults 10,000/20,000/30,000/30,000/30,000/30,000 raw-gold units), WHEN an authorized leader explicitly confirms prestige THEN THE SYSTEM SHALL atomically deduct the fee, increment prestige count, grant one permanent home-capacity unit, mark the selected perk permanent, reset current-run level to 1 and XP to 0, and deactivate every other non-permanent purchased progression perk. Prestige SHALL preserve seasonal Elo and pair history, source-cap consumption, weekly quest progress/reward flags, guild identity, membership, ranks, relations, canonical guild gold remainder, vault contents, activated homes, permanent home capacity, and prior permanent perks. Failure or retry SHALL grant neither duplicate rewards nor duplicate charges. For the production Chapter 2 cutover, the shipped default SHALL be `prestige.enabled: true`; operators MAY explicitly set it to `false` to disable Prestige.

## Season 2 presentation contract

### REQ-094
**Ubiquitous.** THE SYSTEM SHALL render paginated progression sources in unique row-major content slots that never overlap header, sidebar, back, close, or pagination controls. Layout changes SHALL preserve source accounting, permissions and reward actions.

### REQ-095
**Ubiquitous.** THE staging Season 2 resource pack SHALL resolve all LumaGuilds item IDs, including the nine missing navigation/source IDs and 37 emoji-choice IDs, to existing resources. Icons SHALL use readable silhouettes, consistent gold/slate pixel-art navigation, correctly directed pagination arrows and shape-distinct status indicators. Existing IDs, approved guild backgrounds and recognizable emoji identities SHALL be preserved. Pack generation SHALL retain SELFHOST. Java and Bedrock client validation SHALL be reported independently from static validation.

### REQ-096
**Event-driven.** WHEN an authorized guild member opens Settings → GUI Theme THEN THE SYSTEM SHALL render a one-row six-choice theme selector using `guild_bg_<theme>_1_row`, place theme choices in slots 0–5 and Back in slot 8, use a distinct supplied symbol for every `GuiTheme`, identify the active theme with localized text plus a secondary visual cue, use the localized heading `GUI Theme`, keep the player inventory visible, and reject unauthorized selections without reporting success.

### REQ-097
**Ubiquitous.** THE progression menu SHALL map every `ExperienceSource` to intentional Season 2 artwork without a catch-all reward/gift fallback. Shared pool presentation SHALL use the pool's meaning (`ORE` → ore, shared `CRAFTING` → workbench) rather than an arbitrary representative source, while source-specific mappings remain exhaustive for future non-pooled views. Gift/reward artwork SHALL be reserved for actual rewards.

### REQ-098
**Ubiquitous.** THE Guild Bank Java menu SHALL resolve every literal and computed localization lookup to a string leaf in shipped and active language trees. Quick-action dynamic keys and transaction-type labels SHALL be covered by executable localization tests, and amount/balance/fee/actor placeholders SHALL render without leaking raw keys.

### REQ-099
**Ubiquitous.** THE local Season 2 Nexo pack SHALL ship the approved dark-slate/metallic 1/3/4/5/6-row backgrounds, six theme-choice symbols, and all new progression source icons under unique item/glyph identifiers with no duplicate glyph chars or custom-model-data collisions. Menu titles on these dark backgrounds SHALL use light/ivory foreground text. Pack generation SHALL remain SELFHOST and production hosting/configuration SHALL NOT be changed.

### REQ-100
**Ubiquitous.** THE Season 2 Java GUI redesign SHALL preserve every existing action, authorization rule, data-bearing item, failure path, sorting/paging behavior, and economic safeguard while reorganizing presentation: Dashboard retains all ten sections with Statistics directly below Economy; Progression retains its proven 24-slot content grid and isolated sidebar/navigation; Bank uses a compact four-row shell when all controls/overlays fit; Settings groups identity, appearance, and access/location without dropping conditional controls; Diplomacy/Warfare separate status/requests/consequential actions; Members/Ranks retain player heads and rank state.

## Season 2 Java visual-audit follow-up — 2026-09-24

### REQ-101
**Ubiquitous.** THE Guild Dashboard summary SHALL derive member count, guild rank/level, canonical guild balance, banner and other displayed guild facts from the same authoritative services used by their destination menus. The balance shown on the dashboard SHALL equal the balance shown by Economy/Bank for the same guild and SHALL NOT display a stale or independently calculated zero. The summary SHOULD use the available space for useful at-a-glance guild information without duplicating confusing or low-value fields.

### REQ-102
**Event-driven.** WHEN a player opens Guild Information THEN THE SYSTEM SHALL open the information view whenever that player is authorized to view it, independent of whether they are the owner. Disbanded guilds SHALL NOT remain joinable or resolve to orphaned/unknown owners after a completed disband. Chapter 2 migration/readiness tooling SHALL detect and report stale guild rows, orphaned ownership, and other disband remnants before production cut-over rather than silently carrying them forward.

### REQ-103
**Ubiquitous.** THE Ranks UI SHALL resolve every perk/title/lore localization key and SHALL replace legacy verbose perk descriptions with concise, scannable hover text that communicates the same permission/effect without extending beyond practical inventory-tooltip height. Existing rank authorization and behavior SHALL remain unchanged.

### REQ-104
**Ubiquitous.** Weekly quest presentation SHALL use human-readable action phrases and target nouns rather than generator-style fragments: examples include "Catch any fish", "Enchant 90 Stone Spears", and "Deposit 30,000 Gold Ore to the Guild Bank" rather than "Fish Any", "90 stone spear", or generic "coins". Generated condition text SHALL be limited to conditions that make gameplay sense for the selected action; X/Z highway-corridor conditions SHALL NOT be attached to actions such as enchanting where location is incidental. The shipped Chapter 2 weekly set SHALL contain six guild quests, with menu layout adjusted to present all six cleanly.

### REQ-105
**Ubiquitous.** THE Guild Bank SHALL retain its intended transaction history, automation, member-contribution, interest, alert, recurring-payment, budget and statistics information, but SHALL present it through a coherent navigation hierarchy with purpose-specific Season 2 icons, complete localization and no misleading dead controls. Every visible control SHALL either perform its described operation or clearly represent read-only status; placeholder "coming soon" actions SHALL NOT ship as interactive controls. Interest/next-accrual countdowns SHALL update from real persisted schedule state, and member-contribution/statistics views SHALL use real guild data.

### REQ-106
**Ubiquitous.** Lunar tracking SHALL be disabled by default for new guilds and newly initialized settings while preserving explicit persisted opt-in choices for existing guilds unless migration policy says otherwise.

### REQ-107
**State-driven.** Prestige SHALL require current-run level 100, matching REQ-093, and the release UI SHALL NOT describe level 25 or present Prestige as a future-update placeholder. For Chapter 2 release, the implemented prestige path, eligibility checks, confirmation, rewards, reset semantics and persistence SHALL be production-ready. The production release policy is governed by REQ-093: Prestige ships enabled, while an explicit operator `prestige.enabled: false` remains authoritative.

### REQ-108
**Event-driven.** BEFORE Chapter 2 production cut-over, operators SHALL have an explicit administrative cleanup operation that can end and remove all residual active wars, incoming/outgoing declarations and peace-agreement state so Chapter 2 starts from a clean diplomatic-war state. The operation SHALL be auditable and safe to run against the reviewed live database. War-menu quick statistics (total wars, wins/losses or ratio, active wars and related counters) SHALL be calculated from authoritative persisted war data and validated against live data before release rather than assumed correct from the imported staging snapshot.

### REQ-109
**Ubiquitous.** THE Guild Statistics UI SHALL receive a Season 2 presentation pass with intentional custom icons, complete localization and verified persisted-data backing. The obsolete statistics CSV export control SHALL be removed in accordance with REQ-023. Kill trends, periodic statistics, rivalry statistics, guild achievements, top killers, top contributors, K/D analysis and every other retained statistics surface SHALL either return real supported data or be removed/hidden until implemented; empty guild data SHALL be distinguishable from an unwired data path. Statistics SHALL be validated against representative live-database data before Chapter 2 release.

### REQ-110
**Ubiquitous.** THE dedicated Bedrock guild UI SHALL provide Chapter 2 behavioral parity for every supported primary Guild Dashboard domain while remaining a platform-specific Cumulus presentation adapter. Supported Bedrock flows SHALL use the same authoritative services, authorization rules, mutation semantics and persisted data as Java, SHALL NOT directly construct Java inventory menus as their normal success/back path, and SHALL NOT replace unavailable data with believable fabricated defaults. Form navigation/back/timeout state SHALL remain within the Bedrock flow.

### REQ-111
**Event-driven.** WHEN a Bedrock guild member opens Weekly Guild Quests THEN THE SYSTEM SHALL present the same active shared six-quest set, human-readable generated objectives, guild progress, claim/reward state, full-set bonus state, supported leaderboard state and reset timing available to Java; claiming SHALL use the existing idempotent quest service and SHALL NOT duplicate generation/reward logic in the form layer.

### REQ-112
**State-driven.** WHILE Chapter 2 progression is available to a Bedrock guild member THEN THE SYSTEM SHALL present current-run progression, source-cap state and the approved reward catalog from the same read models as Java. WHILE Prestige is enabled, eligible Bedrock leaders SHALL see the same level-100 eligibility, lifetime maximum (default 6), next fee, eligible retained-perk choices, immutable quote, explicit confirmation and retry-safe result semantics as Java; disabled/unavailable/max states SHALL be explicit and legacy contradictory prestige/perk presentation SHALL NOT be shown.

### REQ-113
**Event-driven.** WHEN an authorized Bedrock player opens Guild Settings THEN THE SYSTEM SHALL expose the persisted current state and mutation controls for open/closed guild access, Lunar tracking and GUI theme in addition to the existing supported identity/appearance/mode controls. Every mutation SHALL enforce the same Java permission requirement before presentation and again before persistence/service execution; toggle labels SHALL describe current persisted state rather than imply an unperformed action.

### REQ-114
**Ubiquitous.** THE Bedrock Guild Statistics surface SHALL render only authoritative supported statistics and SHALL distinguish real zero/empty data from unavailable or unwired data. Hardcoded XP, activity, territory, kill, war or other plausible-looking placeholder values SHALL NOT ship. Retained Bedrock statistics SHALL consume the same kill/war/member/bank/invitation/leaderboard sources as Java or SHALL be hidden/marked unavailable until supported, and the obsolete CSV/export path SHALL remain absent.

### REQ-115
**Ubiquitous.** THE Bedrock Guild Bank SHALL expose a coherent navigation hierarchy for canonical balance/actions, transaction history, statistics, member contributions, automation, budget and security where authorized. Deposit/withdraw and every mutable bank-management setting SHALL use the same Java authority and canonical BankService/persistence semantics, including unresolved payout review states. Management settings SHALL be authorization-checked before rendering mutable controls and again at mutation time; history/analytics SHALL be bounded and use real persisted data.

### REQ-116
**Event-driven.** WHEN a Bedrock player manages guild homes THEN THE SYSTEM SHALL retain the current paid activation/use behavior and SHALL provide functional per-home rank access and inbound ally-home access controls backed by the same services as Java. Bedrock SHALL NOT replace these supported controls with an unavailable placeholder.

### REQ-117
**Ubiquitous.** THE Bedrock member/rank management surface SHALL expose the same currently supported authorized member actions and rank permission/prefix semantics as Java through a coherent selected-member/rank flow. Rank editing SHALL preserve stable rank identity and existing priority unless an explicit reorder action is performed; alternate Bedrock editors SHALL NOT silently expose conflicting permission subsets or change ordering as a side effect of editing.

### REQ-118
**Event-driven.** WHEN a Bedrock player uses guild recruitment or party management THEN THE SYSTEM SHALL keep the supported LFG browse -> join requirements -> admission/result -> return flow within Bedrock forms and SHALL expose current service-backed party settings/permissions rather than a hardcoded platform-unavailable action. Paid admission SHALL continue to use the existing journaled LfgService path.

### REQ-119
**Ubiquitous.** THE Bedrock diplomacy/warfare UI SHALL preserve the current service-backed relation, declaration, wager, acceptance/rejection/cancellation, kill-progress, history/statistics and peace behavior and SHALL expose every currently supported war objective/configuration required by the Chapter 2 Java flow. Bedrock and Java MAY use different layouts, but both SHALL resolve authoritative persisted war/relation state and SHALL NOT fork lifecycle or settlement logic.

### REQ-120
**Event-driven.** BEFORE Chapter 2 Bedrock release sign-off, automated parity contracts SHALL cover primary routing, authorization denials, data truthfulness and the new Quests/Prestige/Settings/Bank/Statistics flows; all visible Bedrock text in those flows SHALL be localized. A compatible staging environment with Geyser/Floodgate/Cumulus SHALL then complete a real Bedrock-client walkthrough of dashboard navigation, quests/claim, reward purchase/prestige, settings, bank, homes/access, members/ranks, party/LFG, diplomacy/warfare, statistics and close/back/timeout/reconnect behavior. Java or static-source validation SHALL NOT be reported as Bedrock runtime validation.

### REQ-147 — Retire unused AxKoth integration

THE SYSTEM SHALL compile, test and start without the retired AxKoth API dependency, repository, plugin soft dependency or team-hook registration. Existing guild war, reward, standings and persistence behavior SHALL remain unchanged. See retired-axkoth.md. IDs 121–146 are reserved by the existing reviewed guild stack.
### REQ-121
**Event-driven.** WHEN an integrating plugin (EnthusiaHolidays) unlocks a guild cosmetic through the public `GuildCosmeticUnlocks` service THEN THE SYSTEM SHALL durably record that the guild owns the cosmetic `(type, key)` with its display name and source, idempotently, and SHALL report success for an already-owned cosmetic. Holiday menu styles marked `requiresUnlock` (`HALLOWEEN`, `CHRISTMAS`) SHALL be offered in the theme selector (Java and Bedrock) only as **locked** until the guild owns them, and `setGuiTheme` SHALL reject a locked theme regardless of caller. WHEN a cosmetic is revoked THE SYSTEM SHALL remove ownership idempotently and reset a guild currently using that theme to the default style without overwriting other guild fields. WHILE a guild uses a style with `seasonalIcons`, THE SYSTEM SHALL send its members the `<icon>_<style>` Nexo variant of each LumaGuilds menu icon that has one, and the normal icon otherwise. The API SHALL use JDK-only signatures, SHALL accept unknown nonblank keys (so a newer integration cannot wedge its sync), and SHALL return false for a nonexistent guild, an invalid blank/over-length identity, or a persistence failure.

### REQ-122
**Event-driven.** WHEN a current guild member uses `/guild bank` or `/g bank` with the existing guild-menu command permission THEN THE SYSTEM SHALL open that member's platform-appropriate Guild Bank without requiring unrelated management permissions. Non-members SHALL receive the existing localized membership denial. Bank operation authorization and currency semantics SHALL remain unchanged.

### REQ-123
**Ubiquitous.** THE SYSTEM SHALL enforce `chat.announce_cooldown_minutes` and `chat.ping_cooldown_minutes` from current configuration when checking announcement and ping cooldowns, SHALL retain existing hourly limits, and SHALL treat non-positive cooldowns as disabling only the time-based limit.

### REQ-124
**Event-driven.** WHEN a guild member opens Guild Stalls from their guild menu or `/g stall` THE SYSTEM SHALL show Market-authoritative guild-owned stalls, state, rent, deadlines, location and guild members with permitted shop actions on Java and Bedrock, SHALL distinguish unavailable data from no stall, and SHALL permit no Market mutations.

### REQ-125
**Ubiquitous.** THE SYSTEM SHALL ignore stale asynchronous stall results after navigation, disconnection or membership loss, SHALL tolerate absent or incompatible Market read providers, and SHALL requery ownership and permissions on refresh rather than cache access across membership or rank changes.

### REQ-126
**Ubiquitous.** THE Java Guild Stalls dashboard button SHALL use the dedicated `lg_nav_stalls` item when that private pack item is installed, SHALL retain its oak-sign fallback when it is unavailable, and SHALL keep the Economy button's existing bank icon. Proprietary icon artwork and generators SHALL remain outside this public repository.

### REQ-127
**Ubiquitous.** THE Bedrock Guild Stalls button SHALL use independent configurable URL/resource-pack icon references and SHALL preserve existing bank icon settings. The matching proprietary texture SHALL be supplied by the separately managed private pack and SHALL NOT be stored or generated in this public repository. THE Gradle test task SHALL track the configured Market API artifact path and contents so enabling or replacing the artifact invalidates cached compatibility results.

### REQ-130
WHEN the staff strike feed cannot read historical guild membership, THE SYSTEM SHALL fail the page for retry and SHALL NOT treat the failed read as an empty history or attribute the punishment to the player's current guild. Configured current-guild fallback SHALL apply only after a successful historical read.

### REQ-132
**Event-driven.** WHEN a prepared guild-shop sale is delivered THEN THE SYSTEM SHALL consume it without XP if its captured chapter or prestige no longer matches, or the current chapter is frozen, closed, elapsed or not started. New installations and missing configuration SHALL default shop XP to disabled until an operator enables the reviewed integration. The approved numerical limits SHALL remain configurable.

### REQ-133
**Ubiquitous.** THE SYSTEM SHALL initialize historical MariaDB physical-vault fields before guild persistence reads or updates and SHALL preserve existing guild names, home coordinates and vault state on repeated initialization. Schema repair SHALL add only absent fields and SHALL NOT rewrite existing data. SQLite SHALL retain its existing physical-vault semantics.

### REQ-134
**Event-driven.** WHEN Guilds enables after persistence and dependency initialization THE SYSTEM SHALL register its existing GuildShopXpApi v2 provider with Bukkit ServicesManager regardless of whether shop XP is enabled, and SHALL unregister its services when disabled. Market SHALL be able to discover that provider for prepare, complete and abort calls without relying on GuildLookup or class-file presence as proof of availability.

**Event-driven.** WHEN EnthusiaMarket prepares a unique paid outside-customer SELL sale before mutating payment or stock THEN THE SYSTEM SHALL durably capture the sale identity, owning guild, buyer, occurrence time, current prestige run, chapter identity and current shop-XP policy. WHEN that same sale is later durably completed, THE SYSTEM SHALL award at most the captured flat XP once, subject atomically to the guild daily cap, buyer-to-guild daily cap and pair cooldown; own-guild buyers, disabled policy, zero caps, stale prestige/chapter runs, frozen chapters and duplicate completion SHALL not grant additional XP. Failed SQL completion SHALL roll back XP, cap/cooldown reservations and receipt finalization so the same completed sale can retry safely. WHEN Market knows a prepared trade failed before durable completion THEN THE SYSTEM SHALL idempotently terminalize that receipt as ABORTED without overwriting any already terminal outcome; duplicate completion replay SHALL NOT re-fire committed progression side effects.

### REQ-128
**Event-driven.** WHEN a member mutates guild ranks or assigns a member rank, THE SYSTEM SHALL enforce management permission and rank priority at the service boundary, reject self-escalation and newly delegated permissions the actor lacks, preserve priority through ordinary edits, and reserve every transition to or from the owner rank for the explicit ownership-transfer service. Administrative override SHALL NOT bypass this owner-integrity boundary. Existing hidden permission values SHALL remain preserved unless explicitly edited.

### REQ-129
**Event-driven.** WHEN a guild owner transfers ownership, THE SYSTEM SHALL commit both rank updates in one transaction using the expected prior member ranks, preserve both prior ranks and caches on any failed write or stale snapshot, and publish success only after the transaction commits. Owner and demotion ranks SHALL belong to the same guild with priorities zero and greater than zero respectively.

### REQ-131 — Accessible teleport completion
**Event-driven.** WHEN a member completes /guild home THE SYSTEM SHALL suggest only homes accepted by the same current home-access decision as teleportation, retaining owner access and full-name completion for home management commands.

### REQ-135
**Event-driven.** WHEN an authorized guild manager changes Reset chat on reconnect in Java or Bedrock Guild Settings THE SYSTEM SHALL persist the guild-wide value, defaulting to disabled for existing/new guilds. WHEN a guild member reconnects in guild or ally chat and any guild they belong to enables the setting THE SYSTEM SHALL move them to RoseChat's configured default channel after join, using current membership/settings. Other channels and an allied guild's setting SHALL NOT trigger reset. Unchanged/stale forms SHALL NOT overwrite newer values; revoked authority and failed writes SHALL NOT change persistence. Existing invalid-channel cleanup SHALL remain.

### REQ-136 — Individual rank permissions
WHEN a rank manager opens a permission category THE SYSTEM SHALL display individual toggles without granting the category, SHALL use the complete shared catalog for creation/editing, and SHALL recheck grant authority on each selection. Disabled claim permissions SHALL remain hidden and preserved on edits.

### REQ-137 — Public directory details
WHEN a player opens the public directory THE SYSTEM SHALL include invite-only guilds and authoritative owners, founding date, active allies, member count, level and existing activity metrics on Java and Bedrock. Missing owners SHALL be labeled unknown; failed reads SHALL NOT fabricate facts.

### REQ-138 — Personal chat preferences and fullscreen announcements
WHEN a player changes personal chat preferences THE SYSTEM SHALL durably store global player-message visibility and an optional destination indicator, defaulting to visible/off. DMs, server notices and private channels SHALL remain visible. Fullscreen guild announcements SHALL reuse existing permission, mute and rate-limit checks; rejected sends SHALL NOT show titles.

### REQ-139 — Stall report shortcut
WHILE the companion accounting command is available THE guild stall detail menu SHALL offer its read-only report to currently authorized shop managers on Java and Bedrock. Market SHALL recheck current ownership, membership and shop authority before querying or disclosing records.
### REQ-140
WHEN a player opens or interacts with a guild vault THE SYSTEM SHALL recheck current ACCESS_VAULT authorization. Item movements SHALL require DEPOSIT_TO_VAULT for ingress and WITHDRAW_FROM_VAULT for egress, including shift transfers, collection, drops and swaps. Swaps SHALL require both permissions. Permission loss SHALL cancel the interaction before mutation and close only the affected vault view safely. Player-only actions SHALL retain normal behavior while access remains valid; the gold button SHALL retain its existing operation-specific checks. Unknown/cancelled actions SHALL NOT mutate or synchronize the vault. Existing explicit admin override semantics SHALL remain unchanged.
### REQ-141
**Event-driven.** WHEN a player clicks a previously rendered Java ally-home button THEN THE SYSTEM SHALL resolve the target guild by its stable identity, read its current ally home, and recheck current eligible-home listing (including both guilds' required perks) and service-backed ally-home access before starting the countdown. Removed homes, disbanded targets and revoked access SHALL be denied; relocated homes SHALL use current coordinates and renamed targets SHALL retain their identity. Existing safety/countdown/economy behavior SHALL be preserved. Real client acceptance is deferred; this correction addresses stale menu state, not authorization changes during an already running countdown.
### REQ-143
**Event-driven.** WHEN a companion checks guild alliance access THE public GuildAllianceLookup SHALL return true only for an active ALLY relation between distinct guilds; pending, ended, absent and enemy relations SHALL return false. The repository SHALL be warmed at registration so event reads use its existing cache.

### REQ-144
**Event-driven.** WHEN a currently authorized shop manager opens guild stall details THE Java and Bedrock controls SHALL offer the Market stall flags/access command. Market SHALL revalidate stall ownership and authority on each mutation; the shortcut SHALL NOT grant permissions.
### REQ-145 — In-server guild Getting Started

**Event-driven.** WHEN a player requests guild onboarding THE SYSTEM SHALL present current, permission-aware Java/Bedrock guidance and read-only milestone states, recheck every click, preserve existing costs/permissions, and offer guild creation and directory help before membership. See `docs/guild-onboarding.md`.

### REQ-146 — One-time membership prompt

**Event-driven.** WHEN a guild creation or new membership occurs THE SYSTEM SHALL persist one pending prompt per player/guild, consume it once only for an online current member, retain pending offline prompts and preserve consumption across duplicate events and restarts. Existing memberships SHALL NOT be retroactively enrolled. See `docs/guild-onboarding.md`.
### REQ-148
**Event-driven.** WHEN a guild owns EMOJI:PUMPKIN_KING THE SYSTEM SHALL allow members with existing MANAGE_EMOJI authority to equip the existing pumpkin_king_icon glyph as its guild emoji through Java, Bedrock and command paths. Unowned guilds SHALL be rejected even with broad personal emoji permissions. Ordinary emojis SHALL preserve their prior permission rules. Only this existing glyph with its expected U+A0A0 character and minecraft:default font SHALL be admitted from non-emoji assets; all other non-emoji glyphs SHALL remain excluded. Revocation SHALL clear only the matching equipped emoji with a conditional field write, preserving unrelated guild fields.
### REQ-150 — Guild recruitment upgrade

The approved recruitment behavior, player/recruiter flows, discovery integration and delivery gates are specified as REC-001 through REC-012 in [Guild recruitment delivery](plans/guild-recruitment.md). This upgrade retains existing LFG access and fee policy, adds dedicated rank authority and requires explicit applicant confirmation before admission.

### REQ-149 — Dynamic Vault economy provider lifecycle
**Event-driven.** WHEN Vault is present but no Economy provider is registered, personal-account guild-bank operations SHALL report the provider as unavailable without declaring the bank permanently broken. BEFORE each personal-account economy operation THE SYSTEM SHALL resolve the currently registered Vault Economy provider, SHALL discover a provider registered after LumaGuilds startup, and SHALL discard a cached provider that is unregistered or replaced. Physical guild-gold and ordinary vault storage SHALL retain their existing availability semantics.

### REQ-150 — Personal guild invite blocking
**Event-driven.** WHEN a player uses the block/unblock/list commands with `lumaguilds.guild.block` THE SYSTEM SHALL durably manage one-way player blocks, reject all invite paths from blocked players or their current guilds without revealing the block, remove covered pending invites, hide unusable invitation lookup/counts and filter blocked sender guild/ally/mod/party chat only for the blocking viewer. Memberships SHALL remain unchanged.

### REQ-151 — Invite block persistence and API safety
**Unwanted.** IF stored blocks cannot be loaded completely or a database write fails THEN THE SYSTEM SHALL respectively refuse invites or report failure without publishing unsaved cache state. Per-instance writes SHALL preserve durable/cache ordering. THE SYSTEM SHALL migrate/create/repair schema v45 for SQLite and migrate MariaDB, provide a JDK-only GuildInviteBlocks API, and use memory-only chat reads.
## Member quest discovery (local only, 2026-10-11)

### REQ-QUEST-DISCOVERY-1
**Event-driven.** WHEN a guild member executes `/g quests`, `/guild quest`, or `/guild quest menu` THEN THE SYSTEM SHALL open the existing Java or Bedrock quest menu using `lumaguilds.guild.quests` (default true), without requiring management rank permissions, AND SHALL reject nonmembers without opening guild management controls.

### REQ-QUEST-DISCOVERY-2
**Event-driven.** WHEN a member joins or a new weekly set is generated AND the corresponding availability notification is enabled AND unfinished milestone quests remain THEN THE SYSTEM SHALL send a clickable `/g quests` reminder. THE SYSTEM SHALL NOT send periodic reminders or reminders for disabled, expired, future, or fully completed sets.

### REQ-QUEST-DISCOVERY-3
**Ubiquitous.** THE SYSTEM SHALL expose active quest descriptions, targets, rewards, availability, own-guild scores/ranks and top guild names/scores through PlaceholderAPI for spawn displays, including playerless global fields. Leaderboard positions SHALL be bounded to 1..100 and cached for 30 seconds per weekly set, and missing entries SHALL return empty strings.

### REQ-QUEST-DISCOVERY-4
**Event-driven.** WHEN a guild member selects Quests from the progression menu THEN THE SYSTEM SHALL open the existing quest menu after rechecking current membership and the dedicated quest permission. THE SYSTEM SHALL preserve progression, reward, prestige and navigation controls on Java and Bedrock. Both short quest aliases and both aliases followed by menu SHALL route to the same member-authorized presentation.

### REQ-STALL-ICONS-1
**Ubiquitous.** THE SYSTEM SHALL give stall entries, member entries, navigation, status and authorized manager controls distinct semantic icons using the existing themed provider with vanilla fallbacks, while preserving all membership, navigation-token and manager authorization checks. Custom private artwork SHALL remain outside the public plugin repository.

## Prestige history, event and member credits

Design, decisions and evidence: [the prestige advancements plan](plans/2026-11-03-prestige-advancements.md).

### REQ-152
**Ubiquitous.** THE SYSTEM SHALL provide a read-only prestige history that lists, for a guild, every applied prestige with its resulting tier, retained perk, confirming actor and commit time, oldest first, where commit time is the `created_at` of the prestige's own canonical gold-journal debit (same transaction ID); and SHALL identify, server-wide, the first applied prestige that reached a given tier, ordered by commit time and then transaction ID. Reading history SHALL NOT write, migrate or alter prestige receipts, and SHALL include prestiges committed before this requirement existed.

### REQ-153
**Event-driven.** WHEN a prestige transaction is newly committed THE SYSTEM SHALL fire exactly one synchronous public `GuildPrestigeEvent` carrying the guild ID, actor ID, resulting tier, retained perk ID, commit time and whether the guild is the first server-wide to reach that tier. Receipt replays, rejections and failures SHALL NOT fire it, and a failing event listener SHALL NOT undo, repeat or alter the committed prestige, its result or its public announcement.

### REQ-154
**State-driven.** WHILE prestige member credits are enabled (default disabled) THE SYSTEM SHALL, whenever a guild member is online after their guild newly commits a prestige and whenever a member joins the server, dispatch the configured member commands as the console once for each applied prestige of the member's current guild whose commit time is at least the configured minimum tenure (default 24 hours) after that member's guild membership began, and additionally dispatch the configured first-reach commands for prestiges that were first server-wide to reach their tier. Placeholders SHALL be the member's UUID, current name and the resulting tier. THE SYSTEM SHALL durably record each delivered member/prestige pair after dispatch and SHALL NOT dispatch a recorded pair again, including after restart; a crash between dispatch and recording MAY repeat that pair once, so configured commands SHALL be safe to repeat. Offline members, members who joined too recently, and members who left the guild SHALL receive nothing.

### REQ-155
**Ubiquitous.** THE SYSTEM SHALL expose PlaceholderAPI values for the requesting player's guild: `guild_prestige` (tier 0 to 6, from reward ownership), `guild_prestige_roman` (I to VI, empty at 0), `guild_prestige_badge` (the operator-configured badge for the tier, empty at 0), `guild_prestige_max` (configured lifetime maximum), `guild_prestige_first_tier` (highest tier this guild was first server-wide to reach, 0 if none) and `guild_prestige_last_date` (UTC `yyyy-MM-dd` of its latest applied prestige, empty if none). Values SHALL be served from a per-guild snapshot cached for at most 30 seconds and refreshed immediately after that guild newly commits a prestige; a guild without a reward account SHALL read as tier 0, and an unreadable snapshot SHALL return empty strings rather than a fabricated tier.

### REQ-156
**Ubiquitous.** THE SYSTEM SHALL add a `prestige` category to the Top-N leaderboard placeholders and the player-guild rank placeholders, ordering guilds by prestige tier, then current-run level and XP, with `value` equal to the tier; Top-N rows SHALL also expose `prestige`, `prestige_roman` and `prestige_badge` fields for every category, using the same 30-second leaderboard cache as the existing categories.

### REQ-157
**State-driven.** WHILE a guild's canonical gold balance exceeds its current bank capacity (for example after a prestige resets level-based capacity), THE SYSTEM SHALL keep the full balance, SHALL reject every credit that would leave the balance above capacity (deposits, transfers in and interest), SHALL allow debits that the balance can fund, and SHALL allow a refund of a debit to restore the balance up to the larger of current capacity and the balance before that debit. A prestige SHALL NOT be rejected because its post-fee balance exceeds the post-prestige capacity, and the prestige confirmation SHALL tell the leader that gold above the new capacity stays withdrawable but cannot be added to until capacity catches up. Receipts recorded with the earlier `POST_PRESTIGE_CAPACITY` rejection SHALL still replay with their original message.

### REQ-162
**Event-driven.** WHEN the owner of a guild whose prestige count is at least 1 starts a disband, through the Java or Bedrock confirmation or `/g disband`, THE SYSTEM SHALL show before anything is deleted a warning naming the guild's prestige tier, stating that the tier and its permanent rewards are lost for good, that members keep the advancements and tags they earned, and that the guild's server-firsts stay in the history under its name; `/g disband` SHALL then open the platform confirmation instead of disbanding at once. WHERE the prestige tier cannot be read THE SYSTEM SHALL show the same warning without the tier. Guilds that have never prestiged SHALL keep the existing disband flow.

### REQ-163
**Event-driven.** WHEN a guild whose prestige count is at least 1 is disbanded THE SYSTEM SHALL, in the same transaction as the deletion, record the guild's id, its name and prestige count at that moment and the disband time, SHALL keep its prestige receipts and gold journal, and SHALL keep every server-first it holds, so that `PrestigeHistory` can still name it as disbanded. A disband that fails SHALL record nothing. Re-founding a guild with the same name SHALL create a new guild with no prestige history.

### REQ-164
**Event-driven.** WHEN a prestige is newly committed and the prestige fanfare is enabled THE SYSTEM SHALL play, from vanilla sounds only and scheduled in server ticks after the public announcement, the member fanfare to that guild's online members (three rising note-block bells, F A C, leading into the vanilla challenge-complete chime with a high F bell on it, all pitched 0.9 so the bells sit in the chime's key; the same for every tier), SHALL show those members a title naming the prestige tier and the guild, and, WHILE the server-wide option is on, SHALL play the same fanfare at no more than 0.3 volume to every other online player. A player who logs out during the fanfare SHALL simply stop receiving its remaining cues, a replayed prestige receipt SHALL NOT play it again, and no failure in the fanfare SHALL affect the prestige.

### REQ-165
**Ubiquitous.** THE SYSTEM SHALL show every prestige tier and prestige count it puts in front of players (the server-wide prestige announcement, the prestige-applied message, the Java and Bedrock progression and prestige menus, and the Chronicle prestige page) as a Roman numeral from I to VI, with a count of zero shown as 0; counts of things other than prestige tiers, such as home slots, SHALL stay as ordinary numbers.

### REQ-172
**Event-driven.** WHEN a player opens a guild's info view with `/g info` on Java or Bedrock THE SYSTEM SHALL show that guild's prestige history: its current prestige in Roman numerals and, oldest first, each applied prestige with its tier in Roman numerals, the date it was applied and, where that prestige was the first on the server to reach its tier, a server-first mark. A guild that never prestiged SHALL show that it has not prestiged yet, and IF the prestige history cannot be read THEN THE SYSTEM SHALL still open the info view and say the history is unavailable.

### REQ-173
**Event-driven.** WHEN a player views a guild's prestige on Java or Bedrock (the progression prestige entry, the Chronicle prestige guide, the reward choices or the confirmation) THE SYSTEM SHALL say in plain language what the next prestige costs, what resets (level, XP, level perks and every purchased reward not kept), what stays (rewards kept from earlier prestiges, purchased home slots, saved homes, members and gold, with gold above the new bank capacity withdraw-only), and what the guild gains (the one chosen reward made permanent, one extra permanent home slot, the next badge and member advancements); each reward choice and the confirmation SHALL state what the kept reward does.

### REQ-174
**Event-driven.** WHEN a player reads Getting Started on Java or Bedrock THE SYSTEM SHALL explain, inside the existing chat and bank steps, that parties are temporary chat groups for the guild or shared with other guilds and used with `/pc` (only while parties are enabled), that the guild bank earns the configured interest rate every configured period (only while the rate is above zero), and that the guild has a separate physical vault opened with `/g vault` (only while the physical vault is enabled), without adding new menu entries.

### REQ-175
**Optional.** WHERE prestige XP carry-over is enabled (default off) THE SYSTEM SHALL, when a prestige commits, start the guild's next climb with the XP it earned after reaching level 100, capped at the XP needed to reach the configured landing level (default 5), SHALL set the guild's level, level progress and post-prestige bank capacity from that carried XP in the same transaction, SHALL tell the confirming player how much XP carried over and the level the guild starts at, and SHALL state the carry-over and its cap on the Java and Bedrock confirmation; WHILE carry-over is disabled THE SYSTEM SHALL keep resetting XP to 0.

### REQ-176
**Ubiquitous.** THE SYSTEM SHALL generate weekly quests only from targets a guild can reasonably work on: kill quests only for hostile mobs, bosses and common farm animals (never villagers, traders, golems, pets, mounts, armor stands, mannequins or mobs that never spawn naturally), with phantoms, breezes, creakings, evokers and piglin brutes counted as rare; harvest quests only for farm crops (wheat, carrots, potatoes, beetroots, nether wart, cocoa, sweet berries, torchflowers, pitcher plants, sugar cane, cactus, kelp and bamboo); place quests only for full building blocks (planks, logs, wood, stone, bricks, concrete, wool, terracotta, glass and similar) rather than stairs, slabs, walls, panes, waxed or weathered copper and other decorative variants; mining quests only for blocks that generate in the world (stone types, ores, logs, sand, gravel, nether and end blocks and similar), never wall-mounted, potted or placed-only blocks; smelting quests only for common smelted goods (ingots, nuggets, glass, stone, bricks, cooked food and similar); and crafting quests never for waxed or weathered copper, with amounts that a guild can make in a week (64 to 800 stackable items, 8 to 64 tools or armor).

### REQ-177
**Event-driven.** WHEN a member breaks sugar cane, cactus, kelp or bamboo THE SYSTEM SHALL count it toward a matching harvest quest regardless of the plant's growth stage, counting the broken block and the blocks of the same plant above it that break with it, except blocks a player placed; and WHEN a quest is shown THE SYSTEM SHALL say that mining quests count only blocks not placed by players and that age-based crops count only when fully grown.

### REQ-178
**State-driven.** WHILE the weekend XP boost is enabled (default on) and the current time falls on one of its days in its time zone (default Saturday and Sunday, America/New_York) THE SYSTEM SHALL multiply XP from every guild activity by its multiplier (default 2), rounding down, for every guild, except weekly-quest and admin-granted XP; WHILE a dated XP boost is also active THE SYSTEM SHALL apply only the larger of the two, never both. The default prestige fees SHALL be 8,000, 16,000, 24,000, 24,000, 24,000 and 24,000 gold for prestiges I to VI.

### REQ-179
**State-driven.** WHILE a guild owns Quick Travel (best tier applies) THE SYSTEM SHALL shorten its members' guild-home and allied-home teleport countdown from 5 seconds to 5 x the Quick Travel multiplier rounded down (at least 1 second) and the cooldown between those teleports to the configured cooldown x the same multiplier, and SHALL apply that one cooldown to command and menu teleports on Java and Bedrock alike; IF the guild's Quick Travel cannot be read THEN THE SYSTEM SHALL use the standard countdown and cooldown.

### REQ-180
**State-driven.** WHILE a guild's current prestige tier is at least the second-ally-home tier (default II) THE SYSTEM SHALL let it keep a second allied home under the same access rules as the first, with set, move, remove and teleport on Java and Bedrock.

### REQ-181
**Ubiquitous.** THE SYSTEM SHALL tell EnthusiaMarket how many extra guild-owned stalls a guild may own (1 at or above the extra-stall tier, default III, otherwise 0), and EnthusiaMarket SHALL refuse a guild stall purchase that would take the guild above its configured guild stall limit (default 1) plus that bonus, without removing any stall a guild already owns.

### REQ-182
**Event-driven.** WHEN a player with Manage home in a host guild opens its allied-home access page on Java or Bedrock THE SYSTEM SHALL let them choose which allied guilds may use the host's allied homes, which ranks of each allowed guild may use them, and which individual players are blocked; WHEN anyone tries to use the host's allied homes THE SYSTEM SHALL allow only a member of an allowed, currently allied guild whose rank is allowed by the host and has Use ally homes in their own guild, and who is not blocked, checking again when the countdown ends.

### REQ-183
**Ubiquitous.** THE SYSTEM SHALL let a player with Manage home choose, for each allied guild separately on Java and Bedrock, which of their own guild's ranks may use that ally's allied homes; WHERE no choice is stored for an ally THE SYSTEM SHALL use each rank's Use ally homes permission, and the guild owner SHALL always keep access on their own side.

### REQ-190
**Ubiquitous.** WHERE member capacity is enabled THE SYSTEM SHALL limit each guild to the smaller of the server maximum and `base + per_step x floor(level / step_levels)` capped at the configured maximum (defaults 10, 5, 5, 100), using level 100 for a guild that has prestiged and never less than the guild's protected floor; WHEN this requirement is first deployed THE SYSTEM SHALL record each existing guild's member count once as its protected floor; THE SYSTEM SHALL never remove a member because of the cap and SHALL only refuse new members while the guild is at or above it.
### REQ-184
**Event-driven.** WHEN a sender with `lumaguilds.admin.prestige` runs `/lumaguilds prestige info <guild>` THE SYSTEM SHALL show that guild's prestige count, level, kept rewards, each applied prestige with its tier, date, kept reward and actor, and any staff adjustments.

### REQ-185
**Event-driven.** WHEN a sender with `lumaguilds.admin.prestige` runs `/lumaguilds prestige set <guild> <count> confirm` with a count below the guild's current prestige count THE SYSTEM SHALL lower the count to it, remove the kept rewards of the removed tiers (newest first), leave level, experience, gold and prestige history unchanged, and record the old count, new count, actor and time as an adjustment; WHEN `confirm` is missing THE SYSTEM SHALL only show what would change; IF the count is higher than the current one THEN THE SYSTEM SHALL change nothing and point to the grant command.

### REQ-186
**Event-driven.** WHEN a sender with `lumaguilds.admin.prestige` runs `/lumaguilds prestige grant <guild> <reward> confirm` THE SYSTEM SHALL apply a prestige to that guild with no fee and no level requirement, keeping the named reward if it is one of the guild's prestige choices, and SHALL run every prestige announcement and listener exactly as for a guild's own prestige; IF the reward is missing or not a choice THEN THE SYSTEM SHALL list the valid choices and change nothing.

### REQ-187
**Event-driven.** WHEN a sender with `lumaguilds.admin.prestige` runs `/lumaguilds prestige rerun <guild> [tier] confirm` THE SYSTEM SHALL forget which members were credited for that guild's applied prestiges (all, or only the given tier) and SHALL credit its online members again, leaving offline members to be credited on their next join.

### REQ-188
**Event-driven.** WHEN a prestige is newly committed AND the Discord webhook is enabled with a URL THE SYSTEM SHALL post one embed naming the guild, the tier in Roman numerals, the actor, the kept reward and whether it is the first guild to reach that tier, off the main thread; IF the request fails or takes longer than 10 seconds THEN THE SYSTEM SHALL log it and the prestige SHALL stay applied.

### REQ-189
**Ubiquitous.** THE SYSTEM SHALL tab-complete every argument of every `/g` and `/lumaguilds` subcommand the sender may use: known values (guilds, players, members, homes, ranks, rewards, tiers, slots, `confirm`) where the argument has them, and otherwise one `<hint>` naming what to type, which disappears once the sender starts typing.

### REQ-191
**Event-driven.** WHEN a player opens a guild's info page THE SYSTEM SHALL show one 6-row page with the description, level, guild card, prestige and relations items centred in the top row, the guild's members as heads in the next four rows (36 per page, sorted by rank from highest to lowest by default), and previous page, page, sort, back and next page items in the bottom row; WHEN the player clicks sort THE SYSTEM SHALL cycle the order through rank, online first and name.

### REQ-192
**Ubiquitous.** THE SYSTEM SHALL show every viewer each member's rank, online status or last seen time, and join date; THE SYSTEM SHALL show a member's bank contribution only to members of that guild; THE SYSTEM SHALL show the invite and banned players items only to viewers with the Manage Members permission in that guild.

### REQ-193
**Event-driven.** WHEN a player clicks a member's head THE SYSTEM SHALL open that member's page; WHERE the viewer has Manage Members in that guild THE SYSTEM SHALL show Promote, Demote, Kick and Ban on it, each usable only when the viewer outranks the member and, for Promote, only when the new rank stays below the viewer's, and SHALL otherwise show the button greyed with the reason; THE SYSTEM SHALL ask for confirmation before a kick or a ban.

### REQ-194
**Event-driven.** WHEN a player with Manage Members bans a player from their guild THE SYSTEM SHALL remove the target from the guild if they are a member, save the ban and delete that guild's pending invites to the target; IF the target is the owner or the actor does not outrank a member target THEN THE SYSTEM SHALL change nothing; WHILE a ban is saved THE SYSTEM SHALL refuse to add the player to that guild by any route and SHALL refuse new invites and applications to it; IF the ban list cannot be read THEN THE SYSTEM SHALL refuse the join; WHEN a player with Manage Members unbans the player THE SYSTEM SHALL delete the ban.

### REQ-195
**Ubiquitous.** THE SYSTEM SHALL show on the guild card Yes or No for whether the guild has a home set, a banner, a placed vault and, when a guild shop lookup is registered, a guild shop, without showing how many homes there are or where they are.

### REQ-196
**Event-driven.** WHEN a player clicks relations on a guild's info page THE SYSTEM SHALL open a page listing allies, enemies and active truces on separate rows, up to 8 guilds each with an item opening the full list when there are more, where clicking a guild opens its info page.

### REQ-197
**Unwanted behaviour.** IF a player who is not the target removes a member and the actor's rank does not outrank the target's rank THEN THE SYSTEM SHALL refuse the removal.

### REQ-198
**Ubiquitous.** THE SYSTEM SHALL show the Java guild menu as 3 rows with Information, Progression, the guild banner, Quests and Statistics centred on the top row; Ranks, Economy, Settings, Guild Stalls and Recruitment centred on the middle row; and Getting Started, Diplomacy, Warfare and Guild Actions on the bottom row with Guild Actions alone in the right corner; THE SYSTEM SHALL NOT show a separate Members button.

### REQ-199
**Event-driven.** WHEN a player kills another player THE SYSTEM SHALL save the killer, victim, each player's guild, the weapon and the time to the kill log, unless the killer is the victim, neither player is in a guild, both are in the same guild, or the same killer killed the same victim within the last 10 minutes.

### REQ-200
**Ubiquitous.** THE SYSTEM SHALL compute a guild's kills, deaths and kill/death ratio from the kill log, where the ratio is kills divided by deaths, or kills when there are no deaths, for all time and for any time range.

### REQ-201
**Ubiquitous.** THE SYSTEM SHALL show the Java guild statistics menu as 3 rows with Combat, Wars, Recent Activity, Rivalries and Achievements centred on the top row, Top Killers, Top Contributors and Top Inviters centred on the middle row, and Back in the bottom row, and SHALL NOT show Member Statistics, Performance, K/D Analysis, Period Statistics, Guild Comparison or Refresh.

### REQ-202
**Event-driven.** WHEN a player opens Recent Activity, Rivalries or Top Killers THE SYSTEM SHALL list, from the kill log, the guild's last 15 kills and deaths, the guilds it has the most kills against or deaths from, or its 10 members with the most kills.

### REQ-219
**Event-driven.** WHEN %lumaguilds_guild_prestige_badge_minimessage% is requested THE SYSTEM SHALL return the same badge as %lumaguilds_guild_prestige_badge% with its colour codes written as MiniMessage, so proxy tab lists such as Velocitab show the badge instead of the raw code.

### REQ-216
**Ubiquitous.** THE SYSTEM SHALL offer in the guild emoji picker only the emojis on the configured approved list, to every guild without an emoji permission, and SHALL never offer an emoji that is not on that list, such as donor heads.

### REQ-217
**State-driven.** WHILE an emoji is configured as a guild-only emoji for a named guild THE SYSTEM SHALL offer it only to that guild, first in its picker, even IF the emoji is also on the approved list.

### REQ-218
**Unwanted.** IF a guild already uses an emoji that is no longer offered to it THEN THE SYSTEM SHALL keep showing that emoji and accept it being saved again, until the guild picks a different one.

### REQ-203
**Event-driven.** WHEN two guilds become allies, end an alliance, become enemies, start or end a truce, make peace, or end a war THE SYSTEM SHALL save one history entry for each guild with the other guild, the change as seen by that guild, the player who made it when there is one, the time and, for a truce, its length in days; IF the entry cannot be saved THEN THE SYSTEM SHALL still complete the change.

### REQ-204
**Event-driven.** WHEN a player opens a guild's diplomacy history THE SYSTEM SHALL list that guild's history entries newest first, 45 per page on Java and 20 per page on Bedrock, each with its date and the player who made it when there is one.

### REQ-205
**Ubiquitous.** THE SYSTEM SHALL show the Java Parties menu as 3 rows with every active party the guild is in centred on the top row, Create New Party, Incoming Requests, Outgoing Requests and How Parties Work on the middle row, and Back on the bottom row, and SHALL NOT show Send Party Request, Party Access Settings or a placeholder message for any button.

### REQ-206
**Event-driven.** WHEN a player clicks a party THE SYSTEM SHALL open that party's page showing its leader, dates, rank limits and guilds; WHERE the player has Manage Parties THE SYSTEM SHALL offer inviting a guild that is not in the party and, after a second click, leaving the party; WHERE the player has Manage Relations THE SYSTEM SHALL offer moderating the party channel.

### REQ-207
**Ubiquitous.** THE SYSTEM SHALL keep parties for talking between two or more guilds: WHEN a guild is created THE SYSTEM SHALL NOT create Guild_Chat, Officer_Chat or Leader_Chat parties for it, and IF anyone tries to create a party that only one guild can ever join THEN THE SYSTEM SHALL refuse it.

### REQ-208
**Event-driven.** WHEN the plugin starts THE SYSTEM SHALL end every active single-guild party by marking it dissolved, without deleting it, and SHALL move every player whose party chat pointed at one of those parties back to normal chat.

### REQ-209
**Event-driven.** WHEN a player turns on guild, ally or mod chat while party chat is on THE SYSTEM SHALL turn party chat off and tell them; WHEN a player switches to party chat while in guild, ally or mod chat THE SYSTEM SHALL move them out of that channel and tell them.

### REQ-210
**Event-driven.** WHEN a player runs /party THE SYSTEM SHALL open their guild's party menu; WHEN they run /party invite without a guild THE SYSTEM SHALL open the guild-invite page for their guild's party, or the party creation menu IF their guild has no party.

### REQ-211
**Event-driven.** WHEN a player with Manage Parties runs /party invite with a guild name THE SYSTEM SHALL invite that guild to their guild's party, first creating a party IF their guild has none, and SHALL tell the invited guild's online members how to accept.

### REQ-212
**Event-driven.** WHEN a player with Manage Parties runs /party accept, /party deny or /party cancel with a guild name THE SYSTEM SHALL accept or deny that guild's request to their guild, or cancel their guild's request to that guild; WHEN no guild is named THE SYSTEM SHALL list the matching requests with clickable actions.

### REQ-213
**Event-driven.** WHEN a player runs /party info THE SYSTEM SHALL show their guild's party name, leader, guilds, online count and end time; WHEN they run /party list THE SYSTEM SHALL list every active party between guilds with its guilds.

### REQ-214
**Event-driven.** WHEN a player with Manage Parties runs /party leave, or the party leader or a player with Manage Parties in the leader's guild runs /party disband, THE SYSTEM SHALL ask them to run the same command again within 30 seconds and only then leave or end the party.

### REQ-215
**Event-driven.** WHEN a player runs /party chat with no message THE SYSTEM SHALL switch party chat on for their guild's party, or off IF it is already on; WHEN they add a message THE SYSTEM SHALL send only that message to the party; WHEN a player with Manage Parties runs /party rename THE SYSTEM SHALL rename the party IF the name is 1 to 32 characters with no spaces.

### REQ-220
**Unwanted.** IF a party request has expired THEN THE SYSTEM SHALL leave it out of every request list, menu and /party command, so players are never offered an invite that can no longer be accepted, and SHALL say how long invites last when a guild has no request to act on.

### REQ-221
**Event-driven.** WHEN a player runs /pc with no arguments THE SYSTEM SHALL turn party chat on for their guild's party, or off IF it is already on, without the player naming the party; WHEN they run /pc on, /pc switch, /pc off or /pc mute THE SYSTEM SHALL turn party chat on, off, or hide party messages; WHEN they run /pc with any other text THE SYSTEM SHALL send that text once to their guild's party; and THE SYSTEM SHALL tab-complete these words and the party's name.

### REQ-222
**State-driven.** WHILE a player has party chat on THE SYSTEM SHALL cancel their chat message before other chat plugins handle it, so it never also appears in normal chat, and SHALL always show the sender their own party message; WHEN a player turns party chat on THE SYSTEM SHALL show party messages again IF they had hidden them.

### REQ-223
**Ubiquitous.** THE SYSTEM SHALL format a party chat message like guild chat, starting with [Party] and the sender's guild name in brackets, then the sender's name (no rank), and SHALL build it without PlaceholderAPI or guild emojis, so no raw placeholder text ever appears.

### REQ-224
**Ubiquitous.** THE SYSTEM SHALL offer long-term guild advancements that extend the existing chains: guild levels 25, 50 and 75 before Vanguard Status; 5 and 25 war wins; 250 and 1,000 war kills; 10 and 50 KOTH captures; 10,000, 50,000 and 100,000 combined play hours; 10,000 guild shop sales; 5 alliances; 10 Withers and 5 Ender Dragons; and larger collective smelting, block-breaking, harvesting and mob-kill totals, with no two advancements drawn at the same position in the advancement tree.

### REQ-225
**State-driven.** WHILE a player is online in a guild THE SYSTEM SHALL check, when they join and every minute, the guild's current member count and the number of whole days since the guild was created, and SHALL award member-count advancements at 10, 25 and 50 members and guild-age advancements at 30, 180 and 365 days.

### REQ-226
**Ubiquitous.** THE SYSTEM SHALL count combined play hours without a ceiling, so play-hour advancements above 1,000 hours can be earned, and the advancement inspector SHALL state each advancement's own goal.

### REQ-227
**Event-driven.** WHEN a member of a guild completes a guild advancement that carries a guild XP reward THE SYSTEM SHALL award that guild XP to the member's guild once, using a payout ID fixed per guild and advancement, so later members completing the same advancement, restarts and retries never pay that guild again; the award SHALL use its own XP source that no weekend or dated XP boost multiplies.

### REQ-228
**State-driven.** WHILE a guild meets the goal of a level, combined play hours, member count or guild age advancement THE SYSTEM SHALL pay that advancement's guild XP once, even if every online member had already completed it before guild XP rewards existed; jokes and penalties (Friendly Fire, The Exile, Coup d'etat) and the root SHALL pay no guild XP, and the advancement inspector SHALL show each advancement's guild XP reward.

### REQ-229
**Event-driven.** WHEN a member completes a guild advancement THE SYSTEM SHALL announce it to the server (chat, toast and Discord) only when that completion is the guild's first, which for advancements with a guild XP reward is the completion that pays it; WHEN the guild had already earned it, or a reward-free advancement completes while the member is joining, THE SYSTEM SHALL tell only that member, privately, in chat.

### REQ-230
**Ubiquitous.** THE SYSTEM SHALL end a war when either guild reaches that war's own kill target: the kill objective the declaring guild chose, or `combat.war_kill_win_target` when the war has no kill objective; guilds SHALL be able to choose kill targets of 5, 10, 25 and 50 (and the configured default) up to `combat.war_kill_target_max`.

### REQ-231
**Ubiquitous.** THE SYSTEM SHALL show each war's own kill target wherever a war's target is displayed, including "Until a guild gets N kills" in the declare menu, the war list and details, and guild info.

### REQ-236
**Ubiquitous.** THE SYSTEM SHALL show a clickable "Browse Recruiting Guilds" card directly under the guild's recruitment status on the Manage Recruitment dialog, and SHALL show listing setup, settings and the player's applications as ordinary buttons there instead of behind "More listing options".

### REQ-237
**Ubiquitous.** THE SYSTEM SHALL show a guild's prestige badge and tier with its level directly under its name on the guild info card, and SHALL list the guild's active wars last, under an "Active wars" heading.

### REQ-233
**Event-driven.** WHEN a war is declared, begins, ends in victory or defeat, or ends by accepted peace, THE SYSTEM SHALL show each online member of the guilds involved a centred title and subtitle naming the guilds, a one-line "⚔ <MOMENT> »" chat line, and a sound distinct to that moment (two bells and a low impact for a declaration), as in the approved war notification mockup; members who were offline SHALL get the same moment when they next join.

### REQ-234
**Event-driven.** WHEN two guilds become allies THE SYSTEM SHALL show online members of both guilds an "ALLIANCE FORMED" title, subtitle, chat line and sound in the same style.

### REQ-235
**Event-driven.** WHEN a player gets a war kill THE SYSTEM SHALL play the killer a war-kill sound (a slowed critical hit followed by a quick level-up chime) with the existing war-kill chat line.

### REQ-232
**Ubiquitous.** THE SYSTEM SHALL fit every chest menu title inside the menu's title bar, shortening a title that would run past it with "..." while keeping its colors, so long guild names never draw outside the menu.

## Guild Chronicle

The catalog-backed Chronicle and durable guild goal follow CHR-001 through
CHR-005 in [the Chronicle specification](plans/2026-10-16-guild-chronicle.md).

## Guild history layout

### REQ-238
**Event-driven.** WHEN a player runs `/guild history <player>`, THE SYSTEM SHALL show the player's name, the number of guilds joined, and one numbered line per guild with its name, join date and status, framed by rule lines instead of box-drawing characters.

## Steady dialog buttons

### REQ-240
**Ubiquitous.** THE SYSTEM SHALL keep every pack-art dialog button label no wider than the button's width minus 4 pixels, so the client never scrolls the label and its artwork side to side.

## War declaration feedback

### REQ-239
**Unwanted behaviour.** IF a guild can't declare war because of its declaration cooldown, a farming cooldown on either guild, or either guild's war limit, THEN THE SYSTEM SHALL tell the player which one applies and, for cooldowns, how long is left, instead of a generic failure.

## Main vault keeps earned rows

### REQ-250
**Ubiquitous.** THE SYSTEM SHALL never shrink a guild's main vault below the largest size it has earned, including after a prestige, while the second vault (REQ-248) still follows the guild's current level.

## Second guild vault at Prestige VI

Plan: `plans/2026-11-14-prestige-second-vault.md`.

### REQ-242
**State-driven.** WHILE a guild's prestige tier is at least `prestige.perks.second_vault_tier` (default 6), THE SYSTEM SHALL let a member with the place-vault permission get a "Guild Vault II" chest item with `/g getvault 2`, when the guild has no second vault placed.

### REQ-243
**Event-driven.** WHEN a member places a Guild Vault II chest, THE SYSTEM SHALL apply the main vault's placement rules, also refuse a spot next to the main vault, and record the second vault's location separately from the main vault.

### REQ-244
**Ubiquitous.** THE SYSTEM SHALL store the second vault's items separately from the main vault, with no gold balance button, and SHALL NOT read it for any gold balance or payment.

### REQ-248
**Ubiquitous.** THE SYSTEM SHALL size the second vault by the guild's current level with the same capacity rule as the main vault, so it grows as the guild levels up after Prestige VI.

### REQ-245
**State-driven.** WHILE a guild with a placed second vault is below the second-vault tier, THE SYSTEM SHALL let members take items out of it and refuse every way of putting items in.

### REQ-246
**Event-driven.** WHEN the second vault is broken, the guild disbands, or the server starts, THE SYSTEM SHALL apply the main vault's break confirmation, item-drop and restore rules to the second vault.

### REQ-247
**Ubiquitous.** THE SYSTEM SHALL list the second vault among the perks a guild unlocks at its prestige tier on Java and Bedrock.

## Prestige confirmation lists exactly what is earned

### REQ-249
**Event-driven.** WHEN a player reviews a prestige before confirming it, THE SYSTEM SHALL list, on Java and Bedrock, what the prestige earns (tier badge, the kept reward and its effect, the home slot count before and after, the tier's perks, and, only where they apply, the kept member limit and Discord role on the first prestige, the exact XP carried over and starting level, member advancements, being first on the server, and how it is announced), what it costs (fee, level and XP reset, purchased rewards reset), and what stays, and SHALL say when it is the final prestige.

## Guild advancement reward text

### REQ-241
**Ubiquitous.** THE SYSTEM SHALL list a guild advancement's guild XP reward first in its advancement tooltip, followed by any personal Minecraft XP its members also receive, and SHALL tell the member who completes it first how much guild XP the guild earned.

### REQ-267
**Ubiquitous.** WHEN a guild service, repository, placeholder or web handler turns an exception into a safe fallback, THE SYSTEM SHALL log it with its call site, at most once a minute per site with a count of repeats.

### REQ-268
**Unwanted behaviour.** IF the storage is MariaDB, THEN THE SYSTEM SHALL not run the SQLite membership_history DDL, so recording a guild join never fails on a syntax error.

### REQ-273
**Unwanted behaviour.** IF a MariaDB database has the first `relations` layout (guild_id, target_guild_id, relation_type), THEN THE SYSTEM SHALL move it aside as `relations_legacy_v1`, create the current layout and carry the old rows across as active relations, so alliances, enemies and truces load.

## Prestige rank-up animation

### REQ-251
**Event-driven.** WHEN a guild prestiges, THE SYSTEM SHALL show each online member who can see it the rank-up animation from the Enthusia pack, one frame per tick for 64 frames (old badge shatters, the four-point star flashes, the new tier badge appears, "PRESTIGE N" wipes in), with the guild name as the subtitle once the title has appeared, hold the last frame for 2.5 seconds and fade it out, and SHALL play the members' fanfare 7 ticks after the animation starts with a shatter sound when the old badge breaks (tiers II to VI).

### REQ-252
**Unwanted behaviour.** IF a member is on Bedrock, the pack's `guild_prestige_rankup_v1` contract glyph does not resolve, or `progression.prestige.fanfare.member_animation` is false, THEN THE SYSTEM SHALL show that member the plain prestige title and fanfare instead.

### REQ-253
**Event-driven.** WHEN a player with `lumaguilds.admin.prestige` runs `/lumaguilds prestige preview <tier> [guild]`, THE SYSTEM SHALL play that tier's rank-up animation and members' fanfare (or the plain title where REQ-252 applies) to that player only, with the given or their own guild name as subtitle, and SHALL NOT change any guild, record history, announce, or play anything to other players.

## Staff sound preview

### REQ-254
**Event-driven.** WHEN a player with `lumaguilds.admin.sounds` runs `/lumaguilds sounds`, THE SYSTEM SHALL list every guild sound by group with a click-to-play entry and a hover saying when players hear it; `/lumaguilds sounds <id>` SHALL play that sound to the sender only, `all` SHALL play every sound once in order with its name on the action bar, and `stop` SHALL end a running preview. War, prestige and rank-up previews SHALL use the same cue definitions as the live events, and no preview SHALL change a guild or reach another player.

## Everyday guild XP fixes

### REQ-255
**Event-driven.** WHEN a member breaks or right-click harvests a fully grown crop (wheat, carrots, potatoes, beetroots, nether wart, cocoa, sweet berries, pitcher plants, torchflowers) in Survival or Adventure THE SYSTEM SHALL award crop XP even though a player planted it, and WHEN a member takes a potion out of a brewing stand THE SYSTEM SHALL award brewing XP only if that stand brewed the potion and its XP has not been claimed, so water bottles and potions put in by hand earn nothing.

### REQ-257
**Ubiquitous.** THE SYSTEM SHALL count every hostile mob except the bosses (which have their own sources) and neutral mobs as a guild mob kill, and natural ground, stone, sand, nether and end blocks and tree logs as guild block breaking, in both the built-in defaults and the shipped config.yml.

### REQ-259
**Ubiquitous.** THE SYSTEM SHALL show on each XP source in the progression menu how much guild XP one action gives (read from the live source settings), what counts towards it in plain words, and the period used, cap and time until it resets.

### REQ-265
**Unwanted behaviour.** IF a player breaks a block that a player placed, THEN THE SYSTEM SHALL not award block-place guild XP for that player's next placement of the same material within 30 minutes, so placing and breaking the same block can't farm XP.
