# Chapter 2 Guild Progression Revamp

Date: 2026-08-27

Status: Proposed for implementation

Tasks: LG-1201, LG-1202, LG-1203, LG-1205, LG-1208, LG-1209

Requirements: REQ-049, REQ-050, REQ-051, REQ-053, REQ-089, REQ-090, REQ-091, REQ-092, REQ-093

## 1. Purpose

Chapter 2 separates repeatable guild growth from seasonal competition. Ordinary Minecraft activity and weekly guild quests advance the current progression run from level 1 through 100. Once a guild reaches level 100, rated guild wars drive a separate seasonal Elo rating presented as levels 101 through 200. A bounded, operator-disabled-by-default prestige option may reset only the current progression run in exchange for one permanent perk and one permanent home; seasonal Elo and chapter rollover remain separate.

The model rewards broad, active play. It rejects AFK or suspicious input before rewards or caps are touched, prevents placed-block mining loops, and limits every repeatable source independently. It deliberately has no per-player cap and no combined guild cap: more recruited active members can work on more sources and finish shared quests sooner, so larger coordinated guilds retain a natural advantage without receiving a hidden multiplier.

## 2. Non-goals

- No claim-based XP or quest activity; EnthusiaSMP does not use claims.
- No conversion of legacy excess XP into seasonal Elo.
- No permanent perks attached to seasonal levels 101–200 in this change.
- No unbounded or level-200 prestige system. The only supported reset is the bounded level-100 contract in the companion prestige/gold design.
- No location rules for mob kills. Intrinsic targets such as the Ender Dragon need no redundant dimension condition.
- No single combined daily cap and no per-player source caps.

## 3. Player-facing model

### 3.1 Current-run progression

Each progression run spans levels 1 to 100. XP needed to reach target level `L` from `L - 1` is:

```text
floor(500 * L^1.15 + L * 150)
```

The floor operation is used consistently in calculation, migration, UI, and tests. The cumulative target from level 1 through level 100 is exactly 5,446,893 XP. Current-run XP never falls because of a war, seasonal reset, or chapter rollover; only an explicitly confirmed successful prestige resets it.

### 3.2 Seasonal Elo and displayed levels

Only guilds at current-run level 100 can participate in rated wars. Each eligible guild begins a chapter at 1000 Elo and cannot fall below 1000. After a rated result:

```text
expected = 1 / (1 + 10 ^ ((opponentRating - rating) / 400))
newRating = max(1000, round(rating + K * (score - expected)))
```

`score` is 1 for a win, 0 for a loss, and 0.5 only if the war system produces an approved draw. `K` defaults to 40 and is configurable. Both ratings are calculated from the same pre-result snapshot and committed atomically.

The UI maps 1000 Elo to displayed level 101 and 1600 Elo to displayed level 200 using a monotonic configurable mapping. Ratings may exceed 1600, but the displayed level remains 200. The exact Elo rating remains visible for leaderboard ordering and tie resolution. There is no placement phase.

One opponent pair can produce only one fully rated result in a rolling seven-day window by default. Additional wars in that window remain playable but do not affect Elo. Pair identity is unordered, so A-versus-B and B-versus-A share the same guard.

### 3.3 Chapter lifecycle

One server-wide chapter lasts three months by default. The configured chapter ID, display name, start, end, and time remaining are visible through menus and read-only placeholders. A restart cannot silently skip a due rollover.

Rollover is a persisted state machine:

```text
SCHEDULED -> FROZEN -> BACKED_UP -> ARCHIVED -> RESET -> PRUNED -> COMPLETE
```

- `FROZEN`: block new rated changes while allowing normal server activity.
- `BACKED_UP`: create and verify a restorable pre-rollover database backup.
- `ARCHIVED`: persist final standings, ratings, guild identifiers/names, chapter metadata, and completion timestamp.
- `RESET`: set eligible guild ratings to 1000 for the new chapter.
- `PRUNED`: remove only configured seasonal/transient records, including expired opponent-pair guards.
- `COMPLETE`: publish the new active chapter and unfreeze rating changes.

Each transition is transactional or restart-idempotent. Failure leaves the current state recorded and rated changes frozen when necessary. Admin controls provide status, postpone, retry, and an explicitly confirmed force operation. Force never skips the verified backup requirement unless a future requirement explicitly authorizes an emergency bypass. Rollover does not reset current-run level/XP, prestige state, permanent homes, or permanent perks.

## 4. XP sources and shipped defaults

All values and supported vanilla targets are configurable. Caps apply guild-wide to that source and period. Reaching one cap never consumes or blocks another source. Weekly quests do not consume these caps.

| Source | Default award | Guild-wide cap | Period | Eligibility notes |
|---|---:|---:|---|---|
| Normal mob kill | 2 XP | 6,000 XP | Daily | Approved entities; suspicious/AFK kills rejected |
| Eligible unique PvP kill | 100 XP | 6,000 XP | Daily | Existing anti-repeat/eligibility policy applies |
| Common natural block | 2 XP | 12,000 XP | Daily | Natural provenance only |
| Coal or copper ore | 5 XP | Shared ore cap | Daily | Natural provenance only |
| Iron, lapis, or redstone ore | 8 XP | Shared ore cap | Daily | Natural provenance only |
| Gold or nether quartz ore | 10 XP | Shared ore cap | Daily | Natural provenance only |
| Diamond or emerald ore | 20 XP | Shared ore cap | Daily | Natural provenance only |
| Ancient debris | 40 XP | 18,000 XP shared ore cap | Daily | Natural provenance only |
| Eligible block placed | 3 XP | 13,500 XP | Daily | Configured common/build materials |
| Mature crop harvested | 5 XP | 12,000 XP | Daily | Mature/player-grown crops allowed |
| Common crafted item | 1 XP | Shared craft cap | Daily | Configured material pool |
| Utility crafted item | 5 XP | Shared craft cap | Daily | Configured material pool |
| Equipment crafted item | 10 XP | Shared craft cap | Daily | Configured material pool |
| Rare crafted item | 20 XP | 12,000 XP shared craft cap | Daily | Configured material pool |
| Smelted output | 5 XP | 9,000 XP | Daily | Award on legitimate output collection |
| Brewed potion | 30 XP | 9,000 XP | Daily | Award on completed eligible brew |
| Fish caught | 25 XP | 11,250 XP | Daily | Valid fishing event only |
| Item enchanted | 75 XP | 11,250 XP | Daily | Valid enchant transaction only |
| Exploration milestone | 50 XP | 11,250 XP | Daily | Server-defined unique milestones |
| Net-new guild-bank value | 1 XP / 100 value | 500 XP | Daily | Net increase; withdrawals/redeposits cannot loop |
| Ender Dragon kill | 1,200 XP | 12,000 XP | Weekly | Boss pool; no redundant location condition |
| Wither kill | 700 XP | 10,500 XP | Weekly | Boss pool |
| Elder Guardian kill | 500 XP | 7,500 XP | Weekly | Boss pool |
| Warden kill | 600 XP | 6,000 XP | Weekly | Boss pool |
| Qualified recruit | 1,000 XP | 5,000 XP | Weekly | Award after seven retained days |
| Pre-level-100 war win | 10,000 XP | 20,000 XP | Weekly | Permanent XP only; not Elo-rated |

Source caps limit awarded XP, not raw event count. Configuration validation rejects negative awards/caps, unknown source keys, invalid periods, non-vanilla identifiers where a vanilla identifier is required, and a cap smaller than one award unless the source explicitly supports partial final awards. Reload uses an immutable validated snapshot so an event never sees half-applied configuration.

## 5. Weekly guild quest contribution

The shared weekly quest system remains independent of source caps and uses the same quest set for every guild. The target reward budget is:

| Component | Count | Typical reward | Range/notes |
|---|---:|---:|---|
| Common quest | 3 | 25,000 XP each | 20,000–30,000 each |
| Challenging quest | 1 | 50,000 XP | 40,000–60,000 |
| Leaderboard quest | 1 | 50,000 XP | Rank reward model remains idempotent |
| Complete-all bonus | 1 | 50,000 XP | Once per guild per week |
| **Average weekly total** |  | **225,000 XP** | Before exceptional leaderboard variance |

Quest actions continue to enforce their own provenance, game-mode, and suspicious-input rules. Location constraints are optional and only generated for compatible actions/targets. Spawn-oriented place/break conditions default to a 2,000-block radius and use broad common materials rather than assuming rare decorative blocks already exist.

## 6. Validation and anti-abuse order

Every event follows this order:

1. Translate the platform event into a typed activity fact.
2. Reject cancelled events and creative/spectator actors.
3. Ask the EnthusiaPlaytime adapter whether the input is suspicious or AFK; fail closed according to configured integration policy.
4. Apply source-specific eligibility, cooldown, uniqueness, maturity, and provenance checks.
5. Resolve guild membership and the immutable source configuration snapshot.
6. Atomically reserve the remaining source-period allowance.
7. Award only the accepted amount and record an auditable transaction.

A rejected event awards zero and consumes zero cap. Player block placement is persisted as provenance; breaking a player-placed block cannot earn natural-mining XP. Provenance cleanup must not make an old placed block appear natural. Bank XP uses net-new value over the period, not deposit event volume.

## 7. Architecture

### Domain

Pure domain types own `ProgressionLevel`, `PrestigeState`, `ExperienceSource`, `SourceCap`, `CapPeriod`, `GuildGoldBalance`, `SeasonRating`, `RatedWarResult`, `OpponentPair`, `Chapter`, `ChapterState`, and the XP/Elo/capacity calculations. Domain imports no Bukkit, database, scheduling, PlaceholderAPI, Vault Economy, or EnthusiaPlaytime types.

### Application

Application services orchestrate:

- activity validation and current-run XP award;
- atomic cap reservation;
- current-run level calculation;
- atomic guild-gold transfers, purchases, and prestige eligibility;
- bounded prestige transition;
- rated-war eligibility and Elo update;
- opponent-pair rematch policy;
- chapter state transitions, archive creation, and migration;
- read models for menus and placeholders.

Ports cover guild lookup, progression/prestige persistence, canonical guild-gold persistence, personal Vault Economy transfers, physical-item exchange, cap ledger, rating persistence, war history, chapter archive, verified backup, clock, suspicious-input classification, and transaction boundaries.

### Infrastructure and interaction

Infrastructure adapters translate Paper events, call EnthusiaPlaytime, bridge the Vault Economy provider, exchange physical raw-gold items, persist SQLite/MariaDB data, schedule reset catch-up, create/verify backups, and expose PlaceholderAPI values. Interaction code renders commands and menus from application read models and does not calculate XP, gold capacity, prestige eligibility, Elo, or rollover transitions.

## 8. Persistence and atomicity

The schema keeps current-run progression, prestige/permanent rewards, canonical guild gold, seasonal ratings, source-cap ledgers, rated-pair history, chapter state, and archived standings separate. Logical keys are:

- current-run progression: `guild_id`;
- prestige/permanent reward state: `(guild_id, reward_id)` plus guild prestige count;
- canonical guild gold: `guild_id` in `vault_gold`;
- source cap: `(guild_id, source_pool, period_start)`, where every source sharing a pool consumes the same cap contract;
- rating: `(chapter_id, guild_id)`;
- pair guard: `(chapter_id, lower_guild_id, higher_guild_id)`;
- archive standing: `(chapter_id, guild_id)`;
- rollover: `chapter_id` with current state and transition metadata.

An XP award and its cap reservation commit together. A guild-gold mutation and its audit record commit together, with external personal currency or physical items compensated when the database mutation fails. Prestige fee, permanent rewards, temporary-perk reset, level/XP reset, and audit record commit as one transition. A rated war result, both rating changes, and pair-guard record commit together. Rollover archives before reset and cannot prune prestige or other permanent tables. SQLite and MariaDB implementations must satisfy the same repository contract tests.

## 9. Migration

Before the one-time Chapter 1 to Chapter 2 migration, the system creates and verifies a backup and archives final Chapter 1 standings. For every guild:

- reset current-run level to 1 and current-run XP to 0 because Chapter 1 used materially different and AFK-abusable earning rules;
- preserve guild identity, membership, ranks, relations, canonical `vault_gold` balance, ordinary vault contents, and every saved guild-home location;
- initialize permanent home capacity to the number of canonical saved homes, with a minimum of one;
- initialize prestige count to zero and no permanent prestige perk selections;
- initialize seasonal Elo to 1000;
- never convert historical XP into Elo, prestige, perks, raw gold, or other economic value.

Migration uses a version marker and transactional batches so restart retries are safe. A dry-run report lists archived standings, reset counts, preserved-home counts, canonical balances, and backup path without modifying data. Rollback restores the verified pre-migration backup.

## 10. Configuration and placeholders

Configuration groups current-run progression, permanent prestige/home state, source awards/caps, eligibility target pools, seasonal Elo, chapter schedule, rollover retention, integration policy, and presentation. Any vanilla material or entity may be configured where its source supports that target. Bosses include Ender Dragon, Wither, Elder Guardian, and Warden by default.

Read-only placeholders include current-run level/XP/progress, prestige count, seasonal Elo/display level/rank, chapter ID/name/start/end/time remaining, rating eligibility, canonical guild-gold balance/capacity, and source cap used/remaining. Missing player, guild, chapter, or integration context returns documented safe fallback text and never mutates state.

## 11. Verification strategy

SPEAR implementation proceeds requirement by requirement with a failing test first. Minimum acceptance coverage includes:

- formula boundaries and consistent rounding for levels 1, 99, and 100;
- current-run XP never decreases except through one successful REQ-093 prestige transition;
- every shipped source award and cap, including shared ore/craft pools;
- no cross-source or combined-cap interference;
- no per-player cap behavior;
- rejection-before-cap for creative, spectator, cancelled, AFK, suspicious, and placed-block mining;
- weekly quest rewards bypass daily source caps;
- Elo expected-score examples, rating floor, simultaneous updates, and 101–200 display mapping;
- both-guild-level-100 eligibility and unordered seven-day pair guard;
- restart-safe chapter transitions and failure recovery at every state;
- verified-backup requirement and immutable archived standings;
- migration reset of current-run level and XP, preservation of required legacy fields, no legacy-XP-to-Elo conversion, dry run, and retry;
- SQLite/MariaDB repository parity and architecture boundary tests.

## 12. Rollout and observability

Rollout order is migration dry run, verified backup, schema migration, current-run source activation, seasonal-rating activation, then chapter scheduler activation. Operators receive structured logs and status output for rejected XP reasons, source cap exhaustion, integration health, rating changes, pair-guard decisions, chapter state, backup verification, and migration counts. No player-facing award is silently dropped: expected cap/eligibility rejection is observable at debug level and operational failures are surfaced at warning/error level.

The balance defaults are an initial Chapter 2 model, not immutable game design. Operators may tune values through validated configuration after observing live source mix, completion rates, and time-to-level-100, without a code change or data migration.
