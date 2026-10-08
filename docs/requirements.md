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
**Conditional.** GIVEN raw-gold economy, THE SYSTEM SHALL require raw gold to create a guild and to activate each guild home, with costs scaling higher as more homes are unlocked. Cost model (contract): home #1..N costs `baseCost * scale^(n-1)`. The level 1–100 reward table MAY grant home capacity only where it explicitly says so. Purchased home capacity and activated home locations SHALL be permanent guild assets and SHALL NOT be revoked by prestige, seasonal-rating changes, or chapter rollover. Prestige SHALL grant exactly one additional permanent home-capacity unit per successful transition, subject to the configured lifetime prestige maximum.

### REQ-055
**State-driven.** THE SYSTEM SHALL enforce a 15-day guild-creation cooldown for players who create and then delete a guild within 7 days of creation (contract: `create_then_delete_window_days = 7`, `creation_cooldown_days = 15`, both operator-configurable). Deleting a guild older than the window does not start a cooldown. The cooldown starts at deletion time and blocks that player from creating a new guild until it expires.

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
**State-driven.** WHEN upgrading the production installation from Chapter 1 to the Chapter 2 progression model THEN THE SYSTEM SHALL archive the complete Chapter 1 standings, reset every guild's current-run level to 1 and current-run XP to 0, initialize seasonal Elo to 1000, and convert the number of canonical saved home locations into that guild's initial permanent home capacity (minimum one). Migration SHALL preserve guild identity, members, ranks, relations, canonical guild gold, vault contents, and every saved home location; SHALL NOT convert historical XP into Elo, prestige, perks, or economic value; and SHALL be dry-run capable, transactional, restart-safe, rollback-capable, and preceded by a verified backup.

### REQ-092
**Ubiquitous.** THE SYSTEM SHALL treat `vault_gold.balance` as the single canonical guild raw-gold-equivalent balance. Personal-account transfers through a Vault Economy provider, physical `RAW_GOLD`/compressed-block deposits, withdrawals, interest, admin credits, war costs, home activation, perk purchases, and prestige fees SHALL use one atomic guild-gold application service. Every credit route SHALL enforce the same effective capacity, transaction limits, permission, fee, suspicious-transaction, and audit rules before external currency/items are irreversibly removed. Ordinary vault-item slot capacity SHALL remain independent from guild-gold capacity. `bank_mode: BOTH` with physical currency enabled SHALL be valid and SHALL permit both personal-account and physical-item ingress into the same balance. A missing Vault Economy provider SHALL disable only personal-account transfers and SHALL NOT disable physical guild-gold or ordinary vault storage.

**Paid admission clarification (2026-09-13).** LFG join fees SHALL use this same journaled pipeline for either personal-account or physical-item payment. Admission eligibility and the default rank SHALL be validated before payment; confirmed admission replaces member deposit permission for this route. A completed join SHALL charge once and credit the canonical guild balance once. An uncertain payment, balance write, or membership completion SHALL remain pending for reconciliation and SHALL block another charge, including after restart. The caller SHALL NOT independently remove currency or issue speculative refunds.

**Durable war clarification (2026-09-14; REQ-039/092).** Declarations, war identity/status, statistics, wager amounts and settlement intent SHALL survive restart. Financial intent SHALL be recorded before a charge or refund. A completed leg SHALL not be repeated after a crash or failed state-marker write; an uncertain leg SHALL prevent speculative compensation and require reconciliation. Acceptance SHALL not publish an active paid war until funding is confirmed. A chosen settlement outcome SHALL be immutable once settlement begins. Corrupt or unavailable persistence SHALL fail closed rather than expose an empty war registry. Stale writers SHALL not overwrite a newer persisted state.

### REQ-093
**Conditional.** WHILE prestige is enabled, GIVEN a guild is current-run level 100, is below the configured lifetime maximum (default 6), has selected a purchased prestige-eligible non-permanent perk, has no active/accepted/unresolved war or outgoing declaration, and can pay the configured tier fee (defaults 10,000/20,000/30,000/30,000/30,000/30,000 raw-gold units) while leaving a balance no greater than its calculated post-prestige capacity, WHEN an authorized leader explicitly confirms prestige THEN THE SYSTEM SHALL atomically deduct the fee, increment prestige count, grant one permanent home-capacity unit, mark the selected perk permanent, reset current-run level to 1 and XP to 0, and deactivate every other non-permanent purchased progression perk. Prestige SHALL preserve seasonal Elo and pair history, source-cap consumption, weekly quest progress/reward flags, guild identity, membership, ranks, relations, canonical guild gold remainder, vault contents, activated homes, permanent home capacity, and prior permanent perks. Failure or retry SHALL grant neither duplicate rewards nor duplicate charges. For the production Chapter 2 cutover, the shipped default SHALL be `prestige.enabled: true`; operators MAY explicitly set it to `false` to disable Prestige.

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

### REQ-121
**Event-driven.** WHEN an integrating plugin (EnthusiaHolidays) unlocks a guild cosmetic through the public GuildCosmeticUnlocks service THEN THE SYSTEM SHALL durably record that the guild owns the cosmetic (type, key) with its display name and source, idempotently, and SHALL report success for an already-owned cosmetic. Holiday menu styles marked 
equiresUnlock (HALLOWEEN, CHRISTMAS) SHALL be offered in the theme selector (Java and Bedrock) only as **locked** until the guild owns them, and setGuiTheme SHALL reject a locked theme regardless of caller. WHEN a cosmetic is revoked THE SYSTEM SHALL remove ownership idempotently and reset a guild currently using that theme to the default style without overwriting other guild fields. WHILE a guild uses a style with seasonalIcons, THE SYSTEM SHALL send its members the <icon>_<style> Nexo variant of each LumaGuilds menu icon that has one, and the normal icon otherwise. The API SHALL use JDK-only signatures, SHALL accept unknown nonblank keys (so a newer integration cannot wedge its sync), and SHALL return false for a nonexistent guild, an invalid blank/over-length identity, or a persistence failure.

### REQ-130
WHEN the staff strike feed cannot read historical guild membership, THE SYSTEM SHALL fail the page for retry and SHALL NOT treat the failed read as an empty history or attribute the punishment to the player's current guild. Configured current-guild fallback SHALL apply only after a successful historical read.

### REQ-133
**Ubiquitous.** THE SYSTEM SHALL initialize historical MariaDB physical-vault fields before guild persistence reads or updates and SHALL preserve existing guild names, home coordinates and vault state on repeated initialization. Schema repair SHALL add only absent fields and SHALL NOT rewrite existing data. SQLite SHALL retain its existing physical-vault semantics.
