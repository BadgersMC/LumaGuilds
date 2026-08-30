# Chapter 2 Prestige and Guild Gold

Date: 2026-08-30

Status: Proposed for implementation

Tasks: LG-1202, LG-1205, LG-1206, LG-1208, LG-1209

Requirements: REQ-049, REQ-050, REQ-051, REQ-054, REQ-056, REQ-090, REQ-091, REQ-092, REQ-093

## 1. Purpose

Chapter 2 needs a progression reset that is meaningful without deleting the social and physical infrastructure that made a guild worth building. It also needs one authoritative raw-gold economy path before level perks, home activation, or prestige can safely charge guilds.

This design establishes three distinct kinds of state:

1. **Current-run state**: level 1–100, XP, and non-permanent purchased perks. A successful prestige resets this state.
2. **Permanent guild state**: identity, roster, ranks, relations, vault contents, canonical raw-gold balance, activated homes, permanent home capacity, prestige count, and selected permanent prestige perks. Prestige and chapter rollover preserve this state.
3. **Seasonal state**: Elo, rated-pair history, standings, and chapter metadata. Chapter rollover resets or archives this state; prestige does not.

Prestige ships disabled because the community is divided on the mechanic. The complete contract is defined now so enabling it later cannot create an improvised or destructive reset.

## 2. Confirmed production baseline

The 2026-08-29 production snapshot contains 146 guilds. Guild gold is stored canonically in `vault_gold.balance`; the legacy `guilds.bank_balance` values are zero after balance consolidation. Thirteen guilds have a positive canonical balance, totaling 17,008 raw-gold-equivalent units; the highest balance is 7,072.

Guild homes are stored in `guild_homes`. Eighty-four guilds have at least one saved home, 182 homes exist in total, and the highest observed guild count is six. Eleven guilds have six saved homes. The Chapter 1 progression configuration theoretically allowed more homes, but migration preserves only actual saved locations, not unused entitlement.

The deployed configuration permits a personal Vault Economy route and physical raw-gold interaction. Both ultimately mutate `vault_gold.balance`, but physical menus, direct vault interaction, interest, and some system credits currently bypass part or all of the bank limits and fees. Ordinary vault inventory capacity (9–54 item slots) is separate from the numeric guild-gold balance.

## 3. Canonical guild-gold model

### 3.1 Unit and storage

One guild-gold unit equals one configured physical currency item, shipped as `RAW_GOLD`. A configured compressed block contributes its exact base-item value; shipped `RAW_GOLD_BLOCK` value is nine. The canonical balance remains `vault_gold.balance` and is independent of ordinary `vault_slots` inventory capacity.

The legacy `guilds.bank_balance` column and transaction ledgers are not balance authorities. Ledgers remain audit/history records only.

### 3.2 Supported ingress and egress

All balance changes pass through one application service:

```text
personal Vault account ----\
physical RAW_GOLD ----------+--> GuildGoldService --> vault_gold.balance
interest/admin credit ------/

vault_gold.balance --> personal Vault account
vault_gold.balance --> physical RAW_GOLD
vault_gold.balance --> system sink (war/home/perk/prestige cost)
```

`bank_mode: BOTH` with physical currency enabled is valid. It means both the personal-account bridge and physical-item exchange are available while sharing the same guild balance. If no Vault Economy provider is registered, only personal-account transfers are unavailable; physical deposits/withdrawals, guild spending, ordinary vault storage, and balance display continue to work.

### 3.3 Capacity

Effective guild-gold capacity is:

```text
min(global safety ceiling, current-run tier capacity + permanent bank-capacity benefits)
```

The level 1–100 table supplies the current-run capacity track. The global ceiling is an operator safety limit and validation rejects or warns about tier capacities that exceed it. Every credit route, including physical deposits, personal-account transfers, interest, and admin credit, enforces the same capacity before committing.

Ordinary item-slot capacity remains a separate concern and does not change guild-gold capacity.

### 3.4 Atomic transfers and compensation

For a personal-account deposit, the service validates guild capacity and transaction policy, debits the player through Vault Economy, credits canonical guild gold, and writes the audit record. If the canonical credit fails after the external debit, it refunds the player.

For a physical deposit, the service validates before removing items. It removes only the accepted currency items, credits canonical guild gold, and restores or drops the exact items safely if the credit fails. Concurrent deposits reserve capacity atomically so two individually valid deposits cannot exceed the cap together.

Withdrawals debit canonical guild gold before paying the personal account or creating physical items. Failed payouts restore the guild balance. All system sinks and credits carry a stable transaction ID so retry is idempotent.

### 3.5 Policy enforcement

The same configured policy applies regardless of entry point:

- minimum and maximum transaction amounts;
- effective guild capacity;
- rank permission;
- deposit/withdrawal fees and configured fee caps;
- per-transaction withdrawal percentage and daily withdrawal allowance;
- suspicious-transaction threshold and optional freeze;
- immutable transaction and security audit records.

Interest reads and credits canonical guild gold. It cannot credit beyond capacity, cannot accrue while the bank is frozen, and cannot mint a partial or duplicate period on retry. The level reward table may supply an active interest rate, but a rate is effective only after its perk has been purchased or retained permanently.

## 4. Level 1–100 reward cadence

Membership capacity is not a progression reward. Every guild receives the configured fixed limit, shipped as 50, from level 1. This prevents Chapter 2 migration or future prestige from ejecting members and permits recruitment at every stage.

The comprehensive LG-1202 tier table follows this cadence:

- Every level triggers a visible guild-only level-up event.
- Ordinary levels that are not divisible by five advance the automatic guild-bank capacity track.
- Levels divisible by five unlock a numeric perk that must be purchased from canonical guild gold.
- Levels divisible by ten unlock a larger named perk or bundle that must be purchased from canonical guild gold.
- Leveling never grants raw gold, Vault currency, valuable repeatable vanilla items, XP multipliers, or increased source caps.
- Each perk record declares price, effect, whether it survives prestige, and whether it may be selected as a permanent prestige perk.

Home-capacity purchases are permanent and are not repurchased after prestige. Other checkpoint perks are current-run purchases unless selected through prestige. The LG-1202 table is responsible for exact perk identities, effects, and prices; it must fit the validated 5,446,893-XP journey and the production raw-gold economy before implementation.

## 5. Chapter 1 to Chapter 2 migration

The migration deliberately gives every guild a clean Chapter 2 progression start because Chapter 1 used materially different XP sources, allowed AFK-heavy gains, and configured no reward table beyond level 30.

Before mutation, the system creates and verifies a restorable backup and archives Chapter 1 standings. The dry run reports every affected guild, old level/XP, canonical balance, saved-home count, and proposed result.

For each guild, migration:

- resets current-run level to 1 and XP to 0;
- initializes prestige count to zero and no selected permanent perks;
- initializes seasonal Elo to 1000;
- preserves identity, members, ranks, relations, vault contents, and canonical guild gold;
- preserves every canonical `guild_homes` location and its access rules;
- sets permanent home capacity to `max(1, saved canonical home count)`;
- grants no unused home entitlement based on legacy level;
- converts no historical XP into Elo, prestige, perks, raw gold, or other value.

The operation is versioned, transactional in restart-safe batches, and rollback-capable from the verified backup. A completed migration cannot run twice.

## 6. Prestige contract

### 6.1 Availability and maximum

Prestige is configurable and ships disabled:

```yaml
prestige:
  enabled: false
  max_count: 3
  costs:
    1: 10000
    2: 20000
    3: 30000
```

Prestige count belongs to the guild and is never transferred to a player or another guild. Three is a lifetime maximum. After Prestige III, the guild may complete level 100 again and remain at the endgame permanently.

### 6.2 Eligibility

A guild may prestige only when all conditions hold:

- prestige is enabled;
- current-run level is exactly 100;
- prestige count is below the configured maximum;
- an authorized leader has selected one purchased, non-permanent, prestige-eligible perk not already permanent;
- there is no active, accepted, or unresolved war and no outgoing pending war declaration;
- canonical guild gold can pay the next prestige fee;
- after deducting the fee, the remainder is no greater than calculated post-prestige capacity;
- no prestige transaction for the guild is already pending.

Incoming unaccepted war invitations do not block prestige and are declined when prestige commits. The UI reports every failed condition and, for capacity failure, the exact amount that must be withdrawn before retry.

### 6.3 Atomic transition

One confirmed prestige transaction:

1. Revalidates every eligibility condition under the transaction lock.
2. Deducts and destroys the configured raw-gold fee.
3. Increments prestige count.
4. Adds one permanent home-capacity unit.
5. Marks the selected perk permanent.
6. Resets current-run level to 1 and XP to 0.
7. Deactivates every other non-permanent purchased progression perk.
8. Writes one immutable audit record with the actor, tier, fee, balances, selected perk, home capacity, and transaction ID.

All steps commit or roll back together. Retry with the same transaction ID cannot charge or reward twice.

### 6.4 Preserved and reset state

Prestige resets only:

- current-run level and XP;
- unlocked-but-unpurchased current-run rewards;
- purchased current-run perks not marked permanent.

Prestige preserves:

- guild identity and history;
- members, ranks, invitations, and relations;
- the fixed member limit;
- canonical guild-gold remainder and ordinary vault contents;
- all activated homes and permanent home capacity;
- prior prestige count and permanent prestige perks;
- seasonal Elo and rated-pair history;
- current daily source-cap consumption;
- current weekly quest definitions, progress, completion, claim, bonus, and leaderboard flags;
- anti-AFK, suspicious-input, placed-block provenance, and anti-farm history.

Prestige never grants a fresh daily allowance, a second weekly reward, or another lifetime milestone item. Repeatable progression milestones provide no raw gold or economically valuable item payout.

### 6.5 Permanent perk selection

The reward registry is the authority for prestige eligibility. A selected perk must already be purchased in the current run, may be selected only once, and retains exactly its configured effect across later resets. XP multipliers, source-cap increases, raw-gold generation, and home capacity are not selectable; prestige already grants its separate permanent home.

The LG-1202 table must bound every selectable effect. Non-stackable perks remain non-stackable, and mutually exclusive perk families cannot both become permanent. Up to three selections are possible because prestige is capped at three.

## 7. Elo and chapter interaction

Seasonal Elo is independent of current-run progression. Prestige does not reset rating or rated-pair history. While a guild is releveling below 100, its existing rating remains visible in seasonal history but it cannot produce a new rated result. It becomes eligible again at current-run level 100.

Chapter rollover archives standings and resets Elo according to the chapter contract. It does not reset current-run level/XP, prestige count, permanent perks, homes, or guild gold.

The progression UI displays current-run level and prestige count. The seasonal leaderboard displays Elo and its 101–200 presentation independently; it never substitutes a seasonal display level for the guild's current-run level.

## 8. Architecture

### Domain

Pure types define guild-gold amounts/capacity, perk purchase state, permanent reward state, prestige count, eligibility failures, and transition results. They import no Bukkit, Vault API, database, scheduler, menu, or PlaceholderAPI types.

### Application

`GuildGoldService` owns every credit, debit, transfer, fee, purchase, and capacity decision. `PrestigeService` orchestrates eligibility and one transaction across progression, permanent rewards, homes, gold, wars, and audit ports. `RewardEntitlementService` resolves automatic, available-to-purchase, purchased, and permanent states.

### Infrastructure and interaction

Infrastructure adapters implement SQLite/MariaDB persistence, Vault Economy transfer, physical-item exchange, and server transaction boundaries. Java/Bedrock menus consume application read models and never remove items, debit balances, calculate capacity, or reset progression directly.

## 9. Verification

Implementation follows SPEAR and must prove:

- both personal and physical deposits reach the same canonical balance;
- every ingress path enforces the same capacity and policy;
- concurrent deposits cannot cross capacity;
- failed external or database legs compensate without duplication or loss;
- missing Vault Economy disables only personal transfers;
- ordinary vault-slot capacity is independent from guild-gold capacity;
- migration preserves exact homes and gold while resetting level/XP;
- prestige rejects every individual failed precondition;
- prestige is rejected during active/accepted/unresolved wars;
- post-fee capacity is calculated correctly with permanent benefits;
- one atomic prestige charges once and grants exactly one home/perk;
- failure injection at every transition boundary leaves the guild unchanged;
- daily caps, weekly quests, Elo, homes, roster, vault, and provenance survive prestige;
- non-permanent perks deactivate and the selected perk remains active;
- fourth prestige is rejected with the shipped maximum of three;
- SQLite and MariaDB satisfy identical repository contracts;
- domain/application layer rules remain intact.

## 10. Delivery order

1. Merge this documentation contract.
2. Implement the canonical guild-gold pipeline and configuration correction.
3. Complete and approve the LG-1202 level 1–100 reward/pricing table.
4. Implement perk purchase and permanent home-capacity persistence.
5. Implement and dry-run the Chapter 2 migration.
6. Implement seasonal Elo and chapter lifecycle against the revised current-run terminology.
7. Implement prestige behind `enabled: false` only after the community approves enabling it.
