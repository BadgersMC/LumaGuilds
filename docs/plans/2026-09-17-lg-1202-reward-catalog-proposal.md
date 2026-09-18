# LG-1202 approved level 1–100 reward catalog

Status: **Approved by the operator on 2026-09-17.** Prepared against PR #144 head
`6dfc5fb0fb258d8b6b4e75eba49f6f3e7747161c`. This DOC deliverable does not change
runtime configuration, enable prestige, or authorize deployment.

## Authority and scope

Follow [REQ-050](../requirements.md#req-050), REQ-049/054/056/090–093 and the
[approved prestige/gold design](../superpowers/specs/2026-08-30-chapter-2-prestige-gold-design.md).
The [handoff](2026-09-17-lg-1202-developer-handoff.md) requires inventory,
a complete table, exact totals, persistence rules and community copy.
This catalog supplies those deliverables. Existing requirements remain authoritative;
the operator accepted decisions 1–6 and the full reward table on 2026-09-17.

SPEAR: LG-1202 is tagged DOC. Specification and source inventory precede drafting;
architecture review and document checks follow. No TDD prove/engine phase or runtime
test success is claimed for documentation. Runtime work must separately follow
requirement → failing test → minimal implementation → layer checks → full verification.
The repository documents this workflow in [implementation.md](../implementation.md#authoring-conventions);
this checkout contains no AGENTS.md, SPEAR SKILL.md or state runner.

## Accepted decisions

1. Start at **8,000 gold capacity**. Level 1 initializes that capacity and shows a
   guild-only welcome; it does not add another ordinary increment. Each later level
   not divisible by five adds **500 capacity** automatically.
2. At multiples of ten, **both purchases unlock independently**: the numeric reward
   required at every fifth level and an additional major reward. Neither purchase
   requires buying the other. Checkpoint levels do not automatically increase capacity.
3. Use the 30 purchases below: ten bank extensions, five cooldown upgrades, five
   withdrawal discounts, nine permanent home slots and one ally-home unlock.
   Repeated home slots provide useful major unlocks without inventing unsupported
   cosmetics. The operator approved these names, counts and prices.
4. Numeric purchase price is **100 × (level / 5)** gold. Major purchase price is
   **500 × (level / 10)** gold. No prerequisite purchases: an unlocked higher tier
   may be bought directly at its listed price. Prices are not upgrade differences.
   The guild must meet level, permission, ownership and available-gold checks.
5. Retained bank extensions add by unique ID; cooldown and withdrawal families use
   their best absolute multiplier and permit at most one permanent selection per
   family. An already permanent purchase cannot be bought or selected again.
6. Home slots add to migrated/new-guild permanent capacity, once per unique ID for
   the guild's lifetime. Nine catalog slots plus at most three prestige slots means
   **13 homes for a fresh guild**, or **18 for a guild migrated with six saved homes**.
   This is capacity only; activating locations is paid separately under REQ-054.

## Existing implementation inventory

Paths below are relative to `src/main/kotlin/net/lumalyte/lg/`. These reference
existing effect consumers, **not an implemented paid/permanent reward registry**.

| Ref | Existing source and behavior | Gap before this catalog can ship |
| --- | --- | --- |
| E | `infrastructure/services/ProgressionServiceBukkit.kt`, `processLevelUp` | Existing Bukkit event/notification path; creation welcome and complete 1–100 messaging must be verified. |
| B | `infrastructure/services/BankServiceBukkit.kt`, `computeProgressionBankLimit`; `infrastructure/services/ConfiguredGuildGoldSettings.kt`, `settingsFor` | Existing tier/global capacity and canonical gold policy. Current adapter passes permanent capacity as zero; paid additive extensions need entitlement resolution. |
| C | `infrastructure/services/ProgressionServiceBukkit.kt`, `getHomeCooldownMultiplier`; `interaction/commands/GuildCommand.kt`, home and ally-home cooldown checks | Lowest level-derived multiplier already affects cooldown. Replace level-only activation with purchased/permanent state; keep existing rounding and other teleport restrictions. |
| F | `infrastructure/services/ConfiguredGuildGoldSettings.kt`, `settingsFor` | Multiplies configured withdrawal fee percentage by the lowest reached multiplier. Purchase gating is absent; existing rounding, caps, daily limits and freeze rules remain authoritative. |
| H | `infrastructure/services/ProgressionServiceBukkit.kt`, `getMaxHomes`; `infrastructure/services/GuildServiceBukkit.kt`, `setHome` | Existing home entitlement/slot enforcement. Permanent additive ownership and separate paid activation need implementation; do not overwrite saved homes. |
| A | `infrastructure/services/GuildServiceBukkit.kt`, `getAllyHomes`; `interaction/menus/guild/GuildHomeMenu.kt` | Both guilds currently require ALLY_HOME_ACCESS; existing alliance/home-access restrictions still apply. Purchase and permanent gating need implementation. |

Additional audited boundaries:

- `config/ProgressionConfig.kt`, `LevelRewardConfig`, and
  `infrastructure/services/ProgressionConfigService.kt`, `loadLevelRewards`,
  load level rewards with no price, reward ID, purchase record or persistence fields.
- `domain/entities/Progression.kt`, `LevelPerkConfig.getDefaultConfigs`, supplies
  a separate hardcoded perk map; `ProgressionServiceBukkit.getPerksForLevel` reads it,
  while numeric getters read YAML. Both must resolve one authoritative entitlement
  model before players see or receive purchases.
- `ProgressionServiceBukkit.getMaxBankBalance` has a 50,000 fallback/floor;
  the canonical bank helper uses configured positive tiers. A future 8,000 baseline
  must reconcile displays and consumers, not merely edit YAML.
- `ProgressionServiceBukkit.getMaxMembers` is still level-derived. Fixed default-50
  membership is an approved contract, not verified runtime behavior in this branch.
- `GuildServiceBukkit.getAvailableHomeSlots` uses another hardcoded level schedule
  (one base slot, up to five additional), separate from `getMaxHomes`. Home creation
  enforcement and progression displays must share the permanent entitlement resolver.
- `src/main/resources/progression.yml` still contains legacy level-30 defaults and
  milestone coin/item payouts. Do not load this catalog into that schema or copy
  those payouts into Chapter 2.
- Interest has a live numeric consumer but generates gold; excluded from this
  approved catalog. Claim rewards are unsuitable for the no-claims deployment.
  Particle/home-sound/war-sound enum names alone are not proof of effect support;
  no new cosmetic implementation is promised here.

## Table semantics

Every level 2–100 also produces a guild-only level-up event (E). Level 1 uses the
welcome event. “Auto cap” is the **absolute current-run capacity**, excluding purchased
extensions and the global safety ceiling. “+500” changes capacity, never balance.

Each `bank-N` purchase (Bank Extension N) adds **1,000 capacity** by unique ID.
Each `cooldown-N` (Quick Travel N) sets an **absolute** home cooldown multiplier;
each `fee-N` (Treasury Discount N) sets an **absolute** withdrawal fee multiplier.
These multipliers are not percentage-point reductions and do not multiply together.
A 0.5 fee multiplier halves the configured fee rate, not the withdrawn principal.
Each `home-N` (Guild Outpost N) adds **one permanent capacity slot**.
`ally-homes` (Allied Waypoints) enables existing reciprocal ally-home access.

All prices are raw-gold units debited from canonical guild gold via the application
service. A displayed unlock does not grant an effect or debit gold.

Prerequisite “Lx” means current-run level at least x and the common purchase checks
above. Previously permanent effects remain active below that level without purchase.
R = purchase resets on prestige unless selected; eligible for selection.
P = permanent on purchase; ineligible for selection.
All purchases and automatic capacity survive chapter rollover. Automatic current-run
capacity resets to 8,000 on prestige and is never a selectable reward.

| Level | Automatic benefit | Auto cap | Numeric purchase: ID, exact effect, gold | Major purchase: ID, exact effect, gold | Prerequisite | Persistence / selection | Refs |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Initialize 8,000 + welcome | 8,000 | — | — | — | Auto: run / no | E, B |
| 2 | Capacity +500 + event | 8,500 | — | — | — | Auto: run / no | E, B |
| 3 | Capacity +500 + event | 9,000 | — | — | — | Auto: run / no | E, B |
| 4 | Capacity +500 + event | 9,500 | — | — | — | Auto: run / no | E, B |
| 5 | Level-up event | 9,500 | bank-1: +1,000 capacity; 100 | — | L5 | Numeric: R | E, B |
| 6 | Capacity +500 + event | 10,000 | — | — | — | Auto: run / no | E, B |
| 7 | Capacity +500 + event | 10,500 | — | — | — | Auto: run / no | E, B |
| 8 | Capacity +500 + event | 11,000 | — | — | — | Auto: run / no | E, B |
| 9 | Capacity +500 + event | 11,500 | — | — | — | Auto: run / no | E, B |
| 10 | Level-up event | 11,500 | cooldown-1: 0.9x; 200 | home-1: +1 slot; 500 | L10 | Numeric: R; major: P | E, B, C, H |
| 11 | Capacity +500 + event | 12,000 | — | — | — | Auto: run / no | E, B |
| 12 | Capacity +500 + event | 12,500 | — | — | — | Auto: run / no | E, B |
| 13 | Capacity +500 + event | 13,000 | — | — | — | Auto: run / no | E, B |
| 14 | Capacity +500 + event | 13,500 | — | — | — | Auto: run / no | E, B |
| 15 | Level-up event | 13,500 | bank-2: +1,000 capacity; 300 | — | L15 | Numeric: R | E, B |
| 16 | Capacity +500 + event | 14,000 | — | — | — | Auto: run / no | E, B |
| 17 | Capacity +500 + event | 14,500 | — | — | — | Auto: run / no | E, B |
| 18 | Capacity +500 + event | 15,000 | — | — | — | Auto: run / no | E, B |
| 19 | Capacity +500 + event | 15,500 | — | — | — | Auto: run / no | E, B |
| 20 | Level-up event | 15,500 | cooldown-2: 0.8x; 400 | home-2: +1 slot; 1000 | L20 | Numeric: R; major: P | E, B, C, H |
| 21 | Capacity +500 + event | 16,000 | — | — | — | Auto: run / no | E, B |
| 22 | Capacity +500 + event | 16,500 | — | — | — | Auto: run / no | E, B |
| 23 | Capacity +500 + event | 17,000 | — | — | — | Auto: run / no | E, B |
| 24 | Capacity +500 + event | 17,500 | — | — | — | Auto: run / no | E, B |
| 25 | Level-up event | 17,500 | bank-3: +1,000 capacity; 500 | — | L25 | Numeric: R | E, B |
| 26 | Capacity +500 + event | 18,000 | — | — | — | Auto: run / no | E, B |
| 27 | Capacity +500 + event | 18,500 | — | — | — | Auto: run / no | E, B |
| 28 | Capacity +500 + event | 19,000 | — | — | — | Auto: run / no | E, B |
| 29 | Capacity +500 + event | 19,500 | — | — | — | Auto: run / no | E, B |
| 30 | Level-up event | 19,500 | cooldown-3: 0.7x; 600 | home-3: +1 slot; 1500 | L30 | Numeric: R; major: P | E, B, C, H |
| 31 | Capacity +500 + event | 20,000 | — | — | — | Auto: run / no | E, B |
| 32 | Capacity +500 + event | 20,500 | — | — | — | Auto: run / no | E, B |
| 33 | Capacity +500 + event | 21,000 | — | — | — | Auto: run / no | E, B |
| 34 | Capacity +500 + event | 21,500 | — | — | — | Auto: run / no | E, B |
| 35 | Level-up event | 21,500 | bank-4: +1,000 capacity; 700 | — | L35 | Numeric: R | E, B |
| 36 | Capacity +500 + event | 22,000 | — | — | — | Auto: run / no | E, B |
| 37 | Capacity +500 + event | 22,500 | — | — | — | Auto: run / no | E, B |
| 38 | Capacity +500 + event | 23,000 | — | — | — | Auto: run / no | E, B |
| 39 | Capacity +500 + event | 23,500 | — | — | — | Auto: run / no | E, B |
| 40 | Level-up event | 23,500 | cooldown-4: 0.6x; 800 | home-4: +1 slot; 2000 | L40 | Numeric: R; major: P | E, B, C, H |
| 41 | Capacity +500 + event | 24,000 | — | — | — | Auto: run / no | E, B |
| 42 | Capacity +500 + event | 24,500 | — | — | — | Auto: run / no | E, B |
| 43 | Capacity +500 + event | 25,000 | — | — | — | Auto: run / no | E, B |
| 44 | Capacity +500 + event | 25,500 | — | — | — | Auto: run / no | E, B |
| 45 | Level-up event | 25,500 | bank-5: +1,000 capacity; 900 | — | L45 | Numeric: R | E, B |
| 46 | Capacity +500 + event | 26,000 | — | — | — | Auto: run / no | E, B |
| 47 | Capacity +500 + event | 26,500 | — | — | — | Auto: run / no | E, B |
| 48 | Capacity +500 + event | 27,000 | — | — | — | Auto: run / no | E, B |
| 49 | Capacity +500 + event | 27,500 | — | — | — | Auto: run / no | E, B |
| 50 | Level-up event | 27,500 | cooldown-5: 0.5x; 1000 | ally-homes: reciprocal access; 2500 | L50 | Numeric: R; major: R | E, B, C, A |
| 51 | Capacity +500 + event | 28,000 | — | — | — | Auto: run / no | E, B |
| 52 | Capacity +500 + event | 28,500 | — | — | — | Auto: run / no | E, B |
| 53 | Capacity +500 + event | 29,000 | — | — | — | Auto: run / no | E, B |
| 54 | Capacity +500 + event | 29,500 | — | — | — | Auto: run / no | E, B |
| 55 | Level-up event | 29,500 | bank-6: +1,000 capacity; 1100 | — | L55 | Numeric: R | E, B |
| 56 | Capacity +500 + event | 30,000 | — | — | — | Auto: run / no | E, B |
| 57 | Capacity +500 + event | 30,500 | — | — | — | Auto: run / no | E, B |
| 58 | Capacity +500 + event | 31,000 | — | — | — | Auto: run / no | E, B |
| 59 | Capacity +500 + event | 31,500 | — | — | — | Auto: run / no | E, B |
| 60 | Level-up event | 31,500 | fee-1: 0.9x; 1200 | home-5: +1 slot; 3000 | L60 | Numeric: R; major: P | E, B, F, H |
| 61 | Capacity +500 + event | 32,000 | — | — | — | Auto: run / no | E, B |
| 62 | Capacity +500 + event | 32,500 | — | — | — | Auto: run / no | E, B |
| 63 | Capacity +500 + event | 33,000 | — | — | — | Auto: run / no | E, B |
| 64 | Capacity +500 + event | 33,500 | — | — | — | Auto: run / no | E, B |
| 65 | Level-up event | 33,500 | bank-7: +1,000 capacity; 1300 | — | L65 | Numeric: R | E, B |
| 66 | Capacity +500 + event | 34,000 | — | — | — | Auto: run / no | E, B |
| 67 | Capacity +500 + event | 34,500 | — | — | — | Auto: run / no | E, B |
| 68 | Capacity +500 + event | 35,000 | — | — | — | Auto: run / no | E, B |
| 69 | Capacity +500 + event | 35,500 | — | — | — | Auto: run / no | E, B |
| 70 | Level-up event | 35,500 | fee-2: 0.8x; 1400 | home-6: +1 slot; 3500 | L70 | Numeric: R; major: P | E, B, F, H |
| 71 | Capacity +500 + event | 36,000 | — | — | — | Auto: run / no | E, B |
| 72 | Capacity +500 + event | 36,500 | — | — | — | Auto: run / no | E, B |
| 73 | Capacity +500 + event | 37,000 | — | — | — | Auto: run / no | E, B |
| 74 | Capacity +500 + event | 37,500 | — | — | — | Auto: run / no | E, B |
| 75 | Level-up event | 37,500 | bank-8: +1,000 capacity; 1500 | — | L75 | Numeric: R | E, B |
| 76 | Capacity +500 + event | 38,000 | — | — | — | Auto: run / no | E, B |
| 77 | Capacity +500 + event | 38,500 | — | — | — | Auto: run / no | E, B |
| 78 | Capacity +500 + event | 39,000 | — | — | — | Auto: run / no | E, B |
| 79 | Capacity +500 + event | 39,500 | — | — | — | Auto: run / no | E, B |
| 80 | Level-up event | 39,500 | fee-3: 0.7x; 1600 | home-7: +1 slot; 4000 | L80 | Numeric: R; major: P | E, B, F, H |
| 81 | Capacity +500 + event | 40,000 | — | — | — | Auto: run / no | E, B |
| 82 | Capacity +500 + event | 40,500 | — | — | — | Auto: run / no | E, B |
| 83 | Capacity +500 + event | 41,000 | — | — | — | Auto: run / no | E, B |
| 84 | Capacity +500 + event | 41,500 | — | — | — | Auto: run / no | E, B |
| 85 | Level-up event | 41,500 | bank-9: +1,000 capacity; 1700 | — | L85 | Numeric: R | E, B |
| 86 | Capacity +500 + event | 42,000 | — | — | — | Auto: run / no | E, B |
| 87 | Capacity +500 + event | 42,500 | — | — | — | Auto: run / no | E, B |
| 88 | Capacity +500 + event | 43,000 | — | — | — | Auto: run / no | E, B |
| 89 | Capacity +500 + event | 43,500 | — | — | — | Auto: run / no | E, B |
| 90 | Level-up event | 43,500 | fee-4: 0.6x; 1800 | home-8: +1 slot; 4500 | L90 | Numeric: R; major: P | E, B, F, H |
| 91 | Capacity +500 + event | 44,000 | — | — | — | Auto: run / no | E, B |
| 92 | Capacity +500 + event | 44,500 | — | — | — | Auto: run / no | E, B |
| 93 | Capacity +500 + event | 45,000 | — | — | — | Auto: run / no | E, B |
| 94 | Capacity +500 + event | 45,500 | — | — | — | Auto: run / no | E, B |
| 95 | Level-up event | 45,500 | bank-10: +1,000 capacity; 1900 | — | L95 | Numeric: R | E, B |
| 96 | Capacity +500 + event | 46,000 | — | — | — | Auto: run / no | E, B |
| 97 | Capacity +500 + event | 46,500 | — | — | — | Auto: run / no | E, B |
| 98 | Capacity +500 + event | 47,000 | — | — | — | Auto: run / no | E, B |
| 99 | Capacity +500 + event | 47,500 | — | — | — | Auto: run / no | E, B |
| 100 | Level-up event | 47,500 | fee-5: 0.5x; 2000 | home-9: +1 slot; 5000 | L100 | Numeric: R; major: P | E, B, F, H |

## Totals, affordability and retention

There are 79 automatic +500 increments after initialization, 20 numeric purchases
and 10 major purchases. Automatic level-100 capacity is
`8,000 + 79 × 500 = 47,500`; buying all ten bank extensions raises it to **57,500**.
The approved global safety ceiling constraint must be at least 57,500 to realize the full
catalog. Retention never adds an eleventh copy of an extension: the union of current
and permanent bank IDs is bounded by ten.

Numeric purchases total **21,000**; major purchases total **27,500**;
the full first-run catalog costs **48,500 gold**. Buying only the best multipliers
is permitted and costs less; 48,500 means buying every listed purchase, including
intermediate upgrades. Home-capacity purchases alone total **25,000**.
Activation costs, transfer fees, guild creation, and prestige fees are excluded.

At any level L, automatic capacity is
`8,000 + 500 × (L - floor(L / 5) - 1)`.
Effective capacity is `min(global ceiling, automatic capacity + 1,000 × distinct
active bank IDs)`. No checkpoint purchase in this table costs more than the
automatic capacity at its unlock, even when neither paired purchase has been bought.

Permanent home capacity is `H0 + owned unique home IDs + prestige count`, where
`H0 = max(1, saved canonical homes at migration)` (1 for a fresh guild).
The general maximum is **H0 + 12**, not a hard cap that deletes migrated homes.
Home activation remains `baseCost × scale^(n-1)`; base, scale and payment rounding
belong to the separate activation contract and are not invented here.

Accepted retention rules:

- A bank extension is independently selectable and retains exactly +1,000.
  At most three can be retained because there are three lifetime prestiges.
- Cooldown and withdrawal upgrades use `min(1.0, active multipliers)` per family.
  Only one tier per family can become permanent; selecting a weaker tier does not
  permit replacing or upgrading that permanent choice later. Show this consequence
  before confirmation. A stronger temporary tier may still be purchased on a later
  run and supersedes the retained effect only until reset.
- Reject purchase of an already owned ID, including a permanent ID, without charging.
  Reject a multiplier that cannot improve the currently effective family value.
  A guild that bought a weaker tier before a stronger one receives no refund.
- All unique bank extensions and home slots are independently purchasable at their
  levels without lower-tier prerequisites. Permanent homes cannot be repurchased,
  refunded by deleting a location, or chosen as the prestige perk.
- Allied Waypoints is a non-stackable boolean and can be retained once. It does not
  bypass the other guild's entitlement, alliance or home-access restrictions.
- All ownership is guild-scoped, persistent and applied once by transaction ID.
  Deleting a home location does not clear its purchased capacity entitlement.

Immediately after prestige, capacity is 8,000 plus retained bank extensions:
**8,000–9,000 after I**, **8,000–10,000 after II**, **8,000–11,000 after III**,
subject to the global ceiling. Selecting other perks provides no bank increase.
For example, with no retained bank extensions, a balance of 18,000 can pay the
first 10,000 fee and fit exactly; 18,001 must be rejected until at least one gold
is withdrawn (subject to normal withdrawal policy). Never destroy the excess.
All three approved fees (10,000 / 20,000 / 30,000) fit the 47,500 automatic
level-100 capacity; eligibility still requires an eligible purchased perk and
the other REQ-093 guards. Prestige remains disabled by default.

The approved design records an **August 29 historical snapshot**, not a live check:
17,008 total gold across 13 funded guilds, largest balance 7,072.
The approved 8,000 starting capacity accommodates that recorded largest balance;
it does not prove that every balance today fits. Migration must inspect current
balances and stop/report conflicts without truncation. The full catalog costs
about 2.85 times that old server-wide balance, spread over 5,446,893 XP and optional
purchases. That is an approved long-term sink, not evidence of affordability or a
gold-earning rate. Before migration, validate current balances and income;
no production database access or economic retuning is part of this document.

## Community explanation (accepted copy; feature not yet shipped)

Your guild grows from level 1 to 100. Ordinary levels expand how much gold your
bank can hold; they never create gold. Every fifth level opens a numeric upgrade
you can choose to buy with guild gold. Every tenth level also opens a major
upgrade, such as another permanent home slot or ally-home access. Reaching a
level unlocks the choice; it does not buy the upgrade automatically.

Guilds start with room for 50 members regardless of level. Purchased home capacity
and saved homes survive resets; activating a new location has its own gold cost.
The approved catalog offers nine extra permanent home slots. Seasonal Elo ranks
101–200 are separate from these XP levels.

Prestige is off by default. If enabled later, it lets a level-100 guild pay a fee,
keep one eligible purchased perk and gain one permanent home slot, up to three
times. Your run level and other temporary perks reset; your members, homes, items,
remaining gold and seasonal Elo stay. Gold left after the fee must fit the new
bank capacity before the reset can proceed.

## SPEAR architecture review and later implementation gates

This catalog defines product data only. Pure reward IDs, effects, ownership and
eligibility belong in domain; application services resolve entitlements and own
purchases and atomic prestige; adapters persist and charge canonical gold.
Menus and placeholders consume the same read model. No layer changes here.

Before implementing accepted values, add failing tests for:

- Exactly 100 unique levels, positive prices, 20 numeric and 10 major purchases,
  ordinary capacity increments, distinct IDs, no currency/item/XP-cap filler.
- Unlock without purchase grants nothing; paired purchases are independent;
  owned/permanent/dominated purchases fail without charge.
- Bank additive union, multiplier minimum, permanent-family exclusivity, and
  activation distinct from capacity, including migration with more than six homes.
- Reset versus rollover, three-prestige bounds, saved-state preservation and
  capacity checks after fees; retained perks resolve at level 1.
- Atomic ownership/payment with same-ID retry, concurrent purchase, storage failure,
  reload/restart, and SQLite/MariaDB contracts.
- Config/legacy-map reconciliation, fixed membership and matching Java/Bedrock
  menus/placeholders, with no remaining milestone payouts.

Then implement minimally, run Konsist layer checks and `./gradlew test shadowJar`
using JDK 21 and the documented local API dependencies, and record actual results.
Do not claim the handoff's historical 922-test baseline as validation of this catalog.

## Acceptance record

The operator explicitly replied "I approve" on 2026-09-17 to the request for
approval of decisions 1–6 and the complete reward table. This accepts all listed
names, prices, effects, retention-family restrictions and the capacity-ceiling
constraint. LG-1202 is complete as a DOC task. Runtime implementation remains
separate TDD work; approval does not claim implementation, live validation or deployment.
