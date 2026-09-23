# LumaGuilds — Implementation Guide

## Layer Dependency Rules

Layers, from most stable to most volatile:

- **domain** — pure guild/claim/bank/war/rank models, invariants, and ports. Depends on nothing outside `net.lumalyte.lg.domain` and the Kotlin stdlib.
- **application** — use cases / application services orchestrating domain objects. Depends on `domain` only.
- **infrastructure** — Bukkit adapters, persistence (MariaDB/IDB), external integrations (Nexo, RoseChat, LiteBans, Geyser/Floodgate, PlaceholderAPI, Vault, CombatLogX). Depends on `application` and `domain`.

Precedence: **domain <- application <- infrastructure**. Never the reverse. Enforcement: `src/test/kotlin/net/lumalyte/lg/architecture/LayerRulesTest.kt` (Konsist).

Packages outside the three layers (`api/`, `common/`, `config/`, `di/`, `integrations/`, `interaction/`, `utils/`) are not asserted by LayerRulesTest; `interaction/` (commands + menus) may depend on any of the three layers plus `common/`/`utils/`.

## Forbidden Domain Annotations

```yaml
forbidden:
  - org.bukkit
  - org.koin
  - co.aikar
  - net.kyori
```

> **Status: executable contract.** `LayerRulesTest` enforces both layer dependencies (domain ← application ← infrastructure) and the external-package prefixes listed above. The remaining violations are removed by REQ-045 / LG-1001.

The `domain/**` package must stay free of framework and server annotations (no Bukkit/Spigot imports, no Koin annotations, no ACF annotations, no Adventure types). When a domain model needs a port to the server, define it in `domain` and implement it in `infrastructure`.

### Public Guild Event Migration

Public guild events remain synchronous, non-cancellable Bukkit events with unchanged constructors and payload properties. External plugins must replace `net.lumalyte.lg.domain.events` imports with `net.lumalyte.lg.api.events` and recompile.

| Old package | New package |
|---|---|
| `net.lumalyte.lg.domain.events.GuildBankDepositEvent` | `net.lumalyte.lg.api.events.GuildBankDepositEvent` |
| `net.lumalyte.lg.domain.events.GuildBannerChangedEvent` | `net.lumalyte.lg.api.events.GuildBannerChangedEvent` |
| `net.lumalyte.lg.domain.events.GuildBannerSetEvent` | `net.lumalyte.lg.api.events.GuildBannerSetEvent` |
| `net.lumalyte.lg.domain.events.GuildCreatedEvent` | `net.lumalyte.lg.api.events.GuildCreatedEvent` |
| `net.lumalyte.lg.domain.events.GuildDisbandedEvent` | `net.lumalyte.lg.api.events.GuildDisbandedEvent` |
| `net.lumalyte.lg.domain.events.GuildHomeSetEvent` | `net.lumalyte.lg.api.events.GuildHomeSetEvent` |
| `net.lumalyte.lg.domain.events.GuildLeaderboardRankChangeEvent` | `net.lumalyte.lg.api.events.GuildLeaderboardRankChangeEvent` |
| `net.lumalyte.lg.domain.events.GuildLevelUpEvent` | `net.lumalyte.lg.api.events.GuildLevelUpEvent` |
| `net.lumalyte.lg.domain.events.GuildMemberJoinEvent` | `net.lumalyte.lg.api.events.GuildMemberJoinEvent` |
| `net.lumalyte.lg.domain.events.GuildMemberRemovedEvent` | `net.lumalyte.lg.api.events.GuildMemberRemovedEvent` |
| `net.lumalyte.lg.domain.events.GuildOwnershipTransferEvent` | `net.lumalyte.lg.api.events.GuildOwnershipTransferEvent` |
| `net.lumalyte.lg.domain.events.GuildRelationChangeEvent` | `net.lumalyte.lg.api.events.GuildRelationChangeEvent` |
| `net.lumalyte.lg.domain.events.GuildTrackingChangedEvent` | `net.lumalyte.lg.api.events.GuildTrackingChangedEvent` |
| `net.lumalyte.lg.domain.events.GuildVaultPlacedEvent` | `net.lumalyte.lg.api.events.GuildVaultPlacedEvent` |
| `net.lumalyte.lg.domain.events.GuildWarDeclaredEvent` | `net.lumalyte.lg.api.events.GuildWarDeclaredEvent` |
| `net.lumalyte.lg.domain.events.GuildWarEndEvent` | `net.lumalyte.lg.api.events.GuildWarEndEvent` |
| `net.lumalyte.lg.domain.events.GuildWarKillEvent` | `net.lumalyte.lg.api.events.GuildWarKillEvent` |

The audited migration moved 17 Bukkit events to `api.events` and three Bukkit-backed state types out of `domain.entities` into the infrastructure-owned vault subsystem. `LayerRulesTest` enforces both layer direction and the documented external forbidden-prefix list.

## Authoring Conventions

- EARS requirements live in `docs/requirements.md`; tasks in `docs/tasks.md`; SPEAR state in `.claude/spear-state.json` (gitignored).
- Every task carries exactly one tag (`TDD`/`DOC`/`INFRA`), a `References:` line, and an `Evidence:` block filled with real source citations during execution.
- TDD tasks run the full cycle: spec → prove (failing test) → engine (min impl) → arch (layer check) → refine (green + close).

## Guild Invitation Statistics (LG-1501)

Pending guild invitations remain operational state in `guild_invitations`; analytics are stored separately in append-only `guild_invitation_history`. A successful invite writes both records in one JDBC transaction, so a history failure rolls the pending invite back and a duplicate pending invite cannot inflate statistics. Removing a pending invitation on accept, decline, expiry, or cancellation leaves the historical send event intact.

`InvitationStatisticsService` exposes bounded per-guild aggregation while persistence orders by sent count descending and inviter UUID ascending for stable ties. Statistics UI resolves the inviter UUID to the current player name only at render time. Java displays a top-inviters summary and a 10-entry paginated detail view with previous/next controls; Bedrock renders five entries per page with a page selector from the same shared service. Pagination is database-backed with deterministic LIMIT/OFFSET ordering and a distinct-inviter count, so historical inviters are not truncated. Schema v37 owns the history table and indexes for both SQLite and MariaDB.
## Weekly Guild Quests (PR-16)

The domain owns provider-neutral namespaced quest targets, typed actions/conditions, provenance policies, validation results, amount policy, deterministic fingerprints/history rejection, and pure procedural generation rules. Infrastructure supplies runtime target providers: Bukkit/Paper discovers vanilla blocks, mature crops, living entities, registered crafting/cooking recipes and enchantable items; Nexo optionally contributes custom block and item identities without exposing vanilla backing materials to the generator. The application layer owns active-week lifecycle, progress evaluation, atomic claims, completion bonuses, and leaderboard payout orchestration through repository and Guild EXP ports. Infrastructure translates Bukkit/domain events into namespaced progress facts with relevant spatial/tool context, persists active/history sets and progress for SQLite/MariaDB, schedules reset catch-up, and supplies PlaceholderAPI adapters. Interaction dynamically renders generated quest text without requiring a language key for every combination.

One `WeeklyQuestSet` is shared server-wide for a stable reset-period ID and persists the fully generated definitions so restarts do not reroll the week. `GuildQuestProgress` is keyed by `(week_id, quest_id, guild_id)`. Generation independently rolls action, compatible provider target, human-rounded magnitude-aware amount, and optional compatible conditions; exact fingerprints and recent action+target pairs are rejected across configured history windows. X/Z corridor conditions encode `center:radius` and are intentionally biased toward X=0/Z=0 by configuration; event coordinates are evaluated only when that explicit condition exists. Location metadata never creates a hidden requirement. Reward flags and reset processing are persisted/idempotent; leaderboard payouts run before expired progress is cleared. Schema v36 owns all weekly quest tables and generated-target metadata in both migration chains; repositories no longer create their own schema.

## Chapter 2 Progression (PR-12)

### Creation cooldown

New guild creation records an immutable original creator in `guild_creators`.
`GuildCreationHistorySQL` serializes admission and deletion on the creator's SQL
row. Guild insertion and creator history commit together; guild deletion and its
cooldown receipt also commit together, including member/rank/relation/home removal
and closing membership stints. Cache eviction and vault/hologram removal follow
successful commits. Failed SQL leaves dependent rows, caches, and world state intact.
`GuildServiceBukkit` uses these paths, while failed setup uses ordinary removal
without a cooldown. The command shows a localized UTC expiry and rejects an
unavailable cooldown lookup; SQL admission rechecks the current deadline.

`guild.create_then_delete_window_days` defaults to 7 and
`guild.creation_cooldown_days` defaults to 15. Deletion strictly before the first
window ends blocks creation until the deletion timestamp plus the second window.
Negative values are invalid; zero disables new penalties. Existing deadlines are
never shortened by configuration changes or another deletion. Creator attribution
survives ownership transfers. Legacy guilds without an original creator record are
not attributed to their current owner or administrator and receive no retroactive
penalty. This is forward-only tracking, not a historical creator migration.

The transaction covers the guild row and cooldown history. Existing disband-time
vault/member cleanup precedes it and is not made transactional by this change.
See `docs/plans/2026-09-17-creation-cooldown.md` for the SPEAR specification.

### Reward foundation

`domain/rewards/RewardCatalog` is the executable operator-approved 100-level
catalog. `RewardEntitlementResolver` derives effects and offers from purchased and
permanent IDs, and validates ownership transitions. Neither leveling nor resolving
an offer grants a purchase. `RewardOwnershipRepositorySQL` provides versioned,
transactional ownership storage with explicit corrupt/missing/failed reads. It is
registered lazily behind the Chapter 2 reward read gate. Its snapshot writes are not purchase or prestige use
cases. `GuildGoldService.purchaseReward` delegates to `RewardPurchaseRepositorySQL`
to commit canonical gold, ownership and an immutable retry receipt on one connection.
Matching receipt replay precedes guards; new requests revalidate authorization, price,
level, version, frozen/pending gold state and eligibility under the account lock.
The purchase service defaults to unavailable without an adapter and authorization
defaults to deny. Read-model migration remains LG-1214. See the
2026-09-17 atomic reward purchase specification and verification evidence.

`GuildRewardService` reads one transactionally consistent level/ownership snapshot
through `RewardStateRepositorySQL`, then resolves all entitlements. The reloadable
`progression.chapter_two_rewards_enabled` switch defaults false. Disabled reads do
not access reward storage; missing, failed or invalid state is explicitly unavailable
and never falls back to legacy grants. No read initializes an account. Gold settings,
progression/home benefits and member limits share this resolver. Java and Bedrock
catalog views distinguish locked, available, purchased, permanent and dominated
offers. Selecting an offer creates a fresh immutable quote through
`GuildRewardPurchaseService`; separate Java/Bedrock confirmation screens are the
only menu path into the atomic purchase use case. Membership and both
`MANAGE_GUILD_SETTINGS` and `WITHDRAW_FROM_BANK` are required, using a rank belonging
to this guild. The gold service rechecks authority during execution and checks the
reloadable rollout gate before accessing purchase storage. Uncertain results keep
the identical quote/transaction ID for retry; definitive outcomes refresh the
catalog. Bedrock callbacks dispatch onto the server thread and ignore disconnected
players. Cancelling or viewing never spends gold.

`guild_reward_*` placeholders report unavailable numeric values as blank and expose
state separately. Migration readiness is covered by the Chapter 1→2 migration/read-model integration
contract. Live Paper/Java/Bedrock validation remains a deployment gate before the
switch is enabled for a server. See `docs/plans/2026-09-17-reward-purchase-ui.md`.

`ExperienceBoost` defines an immutable UTC interval and source selection for
`progression.xp_boost`. `PermanentExperienceService` applies it after eligibility and
anti-AFK validation but before atomic source-cap reservation. The complete XP award
is multiplied and rounded down; the source policy and cap are unchanged. Evaluating
the event timestamp implements scheduled activation/expiration across restarts and
reloads without a separate mutable timer. See `docs/plans/2026-09-17-xp-boost.md`.

Current-run progression, permanent guild rewards/prestige, canonical guild gold, and seasonal competition are separate aggregates. The domain owns the level curve, typed XP sources/cap periods, guild-gold capacity, perk/prestige state, Elo calculation, rated-pair identity, and chapter transition rules. Application services validate activity, atomically reserve a source allowance and award run XP, process every guild-gold transfer/purchase, execute bounded prestige, resolve rated wars, and advance rollover states through ports. Infrastructure translates Paper events, integrates EnthusiaPlaytime suspicious-input checks, bridges Vault Economy and physical raw-gold items, persists progression/prestige/gold/provenance/cap/rating/chapter records for SQLite and MariaDB, schedules catch-up, and verifies backups. Interaction and PlaceholderAPI adapters consume read models only.

Current-run progression is keyed by `guild_id`; permanent perks by `(guild_id, reward_id)` plus guild prestige count; canonical guild gold by `guild_id` in `vault_gold`; source usage by `(guild_id, source_pool, period_start)`; ratings and standings by `(chapter_id, guild_id)`; and rematch guards by an unordered guild pair within a chapter. XP award plus cap reservation is one transaction. A guild-gold mutation plus audit is one transaction with compensation for failed external Vault/item legs. Prestige fee, permanent rewards, temporary-perk reset, level/XP reset, and audit commit together. A rated result plus both Elo updates plus its pair guard is one transaction. Rollover follows `SCHEDULED -> FROZEN -> BACKED_UP -> ARCHIVED -> RESET -> PRUNED -> COMPLETE`; no reset or prune transition may run before a verified backup and archived standings exist.

The XP/Elo/chapter contract lives in `docs/superpowers/specs/2026-08-27-chapter-2-progression-revamp-design.md`. The canonical guild-gold, Chapter 1 migration, reward cadence, and bounded-prestige contract lives in `docs/superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md`.
