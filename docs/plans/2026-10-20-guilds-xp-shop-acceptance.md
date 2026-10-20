# Guilds XP and shop acceptance

## Current acceptance checkpoint

This procedure now targets the schema-47 delivery source at
`0106651cb4c7857412e4c7fe410b80c04d0f5a7a`. The coordinator's
[source-specific native checkpoint](2026-10-20-guilds-roundtable-candidate.md#verified-delivery-candidate)
records 2,192 cases: 2,188 passed, four known skips, zero failures/errors, including
49 MariaDB and 16 paired Market cases. That result belongs to that exact source,
tree and artifact; it does not establish a later candidate or the installed TEST
build. This acceptance-document update performs no native or live verification.

GUILDS-05 remains dependent on a verified rollback route, the shared loaded-artifact
and exact-player durable-state evidence owned by SHARED-02, current private-pack
and paired-runtime evidence, and an acknowledged TEST/client window. Read-only
observations of the currently installed older build must be labelled **OLD_BUILD**.

## Ownership and source

Roundtable task **GUILDS-03**, `01a15a55-541c-7865-a0f5-33e5d47bb63f`, is executed by
the native ChatGPT worker `/root/guilds_xp_shop`, routed as `guilds-xp-shop`. The
coordinator owns shared transport, native Java 25 verification and any later TEST
window. MARKET-07 owns the combined Market candidate; MARKET-08 owns its artifact
and access readiness. This document prepares that shared acceptance work.

The worker started with a clean checkout at:

| Source | Exact revision |
| --- | --- |
| Combined Guilds source | `580b1fbf4708896f138a00ee7026e814fd3e81bc` |
| Combined source tree | `04cedf5908bd38bf2d3b64ff091461df5f8f36c1` |
| Navigation PR [#236](https://github.com/BadgersMC/LumaGuilds/pull/236) | `b1339efb067b3ec312022e8e4d192edc25f4f262` |
| Deposit/quest PR [#237](https://github.com/BadgersMC/LumaGuilds/pull/237) | `f2da62900e6974404cb1db2e02059d1b173f16c6` |
| Registration PR [#238](https://github.com/BadgersMC/LumaGuilds/pull/238) | `8a32cba4d36f419be0f83387963ffc5ed221835b` |
| New receipt/cache regression | `a7723a49775e4afa8ca366f580a4858be5cdbcd0` |

The source was imported from the recorded combined TEST checkout in a verified Git
bundle. A standalone artifact from either PR #237 or #238 would omit part of the
unmerged GUI/dialog stack; it is not the combined candidate described here.

The imported Codex session `01a0ca20-8e1b-72b2-8236-bbd670689b51` last reported, on
2026-10-18 at 15:21 UTC, that TEST loaded
`3.0.0-shop-api-integrated-UNMERGED-TEST`, registered API v2 and synchronized five
stalls. That is historical startup evidence. It does not establish the current
TEST artifact, a completed purchase, correct bank accounting or player acceptance.
The earlier paired tests used released Market 1.0.53, which does not establish
compatibility with the candidate being reconciled in MARKET-07/08.

## Contracts checked in source

These are source and existing-test inspections, not claims that this worker ran
the suite or performed live transactions.

| Contract | Implementation and existing proof | Remaining boundary |
| --- | --- | --- |
| Deposit XP becomes visible below a level threshold | New configured personal/physical deposits capture a durable quote before payment. Canonical APPLIED settlement retains READY atomically; `BankServiceBukkit.postDepositApplied` delivers its canonical operation UUID. `BankDepositDelivery*Test`, `BankDepositPublicationTest`, `DepositXpVisibilityTest` and delegation tests cover SQL delivery and acknowledged cache refresh. | Current TEST raw-gold removal, canonical bank credit, retained delivery and visible progression in one player journey. |
| Quest awards and replays refresh the saved total | `awardUncappedSystemExperienceOnce` refreshes both new and duplicate receipts. Duplicate publication supplies no level. `QuestService` derives stable claim/full-set/leaderboard IDs; reward delivery markers permit recovery. `DepositXpVisibilityTest` and `QuestServiceTest` cover replay. | A current TEST quest completion and claim, including any item reward or full-set bonus in that fixture. |
| Numeric and legacy timestamps remain readable | Progression refresh and preload use the same mapper, which reads raw values before conversion. `mixedTimestampsRemainReadable` checks numeric epoch milliseconds, ISO text and nullable last-level-up. | No live database timestamp rewrite is needed or proposed. |
| Repeated bank movement does not farm XP | Ordered delivery consumes newly crossed balance boundaries using the captured period and unit size (default 100). Confirmed denied growth, including AFK growth, advances high-water without XP or cap usage so a later deposit cannot reclaim it. | Same-period bank movements with each physical/personal asset delta, fee, delivery decision and high-water row captured. |
| API v2 is published independently of the reward switch | `LumaGuilds.onEnable` registers after database/Koin initialization; disable unregisters the plugin's services. `GuildShopXpStartupTest` checks source wiring; `GuildShopXpRegistrationTest` uses real MockBukkit service discovery. | Discovery from the current paired TEST plugins and their actual classloaders. |
| A paid shop receipt awards at most once | `GuildShopXpRepositorySQL` commits progression, source/buyer usage, pair cooldown and terminal receipt in one SQL transaction. Existing SQLite and optional MariaDB tests cover concurrency, rollback/retry and retained receipts after XP-history cleanup. | Market's durable payment/stock transaction and the exact candidate's retry path. |
| Failed or ineligible shop trades cannot later become awards | Existing tests cover terminal aborts, own-guild membership captured before payment, disabled/zero-cap policies, identity conflicts, prestige/disband guards and chapter freeze/rollover. | MARKET-07 must prove that personal/free/BUY/barter trades never call this API and that `complete` follows a durably paid SELL transaction. |

The shop XP API's durable terminal codes are `AWARDED:n`,
`OWN_GUILD`, `DISABLED`, `COOLDOWN`, `CAPPED`, `STALE_RUN`, `ABORTED` and
`CHAPTER_FROZEN`. The starting Java API comment omitted `CHAPTER_FROZEN`; the
coordinator corrected the comment in integration commit `7faa2155` without
changing the contract. The paired Market review must include the actual status.
`prepare` returning `PREPARED` acknowledges durable capture; a captured own-guild,
disabled or frozen sale can already retain a non-awarding terminal decision.

### Receipt replay visibility defect

At the starting source, `GuildShopXpProvider.complete` only publishes when
`awardedNow` is true. If SQL commits but the publication is missed, the cache can
remain stale: retry returns the retained `AWARDED:n` receipt with `awardedNow=false`
and skips publication again. The quest path already repairs this situation.

The new SQL/cache regression commits one 5-XP shop receipt while the real cache is
warm at zero, then retries through the real provider and progression adapter. It
expects cached and persisted XP to agree at 5 with one XP receipt. Its first native
run was performed by the coordinator on Java 25 from 17:33:06 to 17:35:53 UTC on
2026-10-20: one test executed, one assertion failure, zero skips/errors. The
persisted-XP assertion passed; line 140 then expected cached XP 5 and received 0.
The red log/status and JUnit XML are retained with the coordinator's task evidence.

The repair refreshes a retained awarded receipt with a null level, preserving
payment/XP idempotency and avoiding repeated level-up effects. Provider interaction
tests check the null-level replay and all seven non-awarding terminal outcomes;
the paired gateway test also expects a replay refresh without another level event.
No award amount, cap, receipt identity, schema or accounting path changes.

### Boundaries that remain separate

The earlier **GUILDS-07** correction combined high-water and XP; **GUILDS-10–12**
subsequently added [durable bank delivery](2026-10-20-bank-xp-delivery.md). Currency
settlement and XP consumption still use separate transactions, connected by the
durable quote/READY receipt. New configured deposits can recover XP without
another charge. Existing operations without a quote remain legacy operations;
the runtime neither backfills their eligibility nor awards historical compensation.
Live deposit events and their quest/activity listeners remain best effort and are
not replayed by XP recovery. A timeout never authorizes a fresh deposit or refund.

The verified source fingerprints both `MARKET_API_JAR` and `MARKET_RUNTIME_JAR` by
path/content. The recorded paired run executed against the pinned companion;
missing or skipped optional inputs cannot establish another artifact's contract.

## Native evidence boundary

The native work at the checkpoint above is complete. Its `check shadowJar` run
included the later durable bank delivery/publication contracts, configured MariaDB
profiles and the exact pinned Market companion. Full arguments, XML identities,
counts and artifact hashes remain in the coordinator's verification receipts.
GUILDS-05 does not require repeating those completed runs.

A changed source or companion needs its own coordinator-owned verification. For
any such run, record commit/tree, command, Java version, both companion hashes,
actual passed/failed/skipped counts and XML location. Missing/blank optional
artifact inputs mean a skip; a configured missing file must fail. The paired
gateway test uses actual service discovery with controlled repository outcomes;
it does not execute Market payment/stock persistence. Disposable MariaDB profiles
drop and recreate fixture tables and must never address the shared TEST database.

## Bounded live TEST matrix

### Read-only preparation and execution order

1. Before a client window, the coordinator can collect **OLD_BUILD** installed and
   loaded artifact provenance, backend/schema, selected policy and fixture rows,
   existing currency holds, and retained backup metadata. Discover tables before
   querying them. SQLite uses `PRAGMA user_version`; MariaDB uses
   `SELECT version FROM schema_version`. Source defaults and a JAR on disk do not
   establish loaded configuration or loaded binary identity.
2. Resolve the entry conditions and rollback route below. After the coordinator's
   authorized TEST cutover, capture the exact candidate/companion hashes, successful
   migration and schema-47 table/index definitions before player transactions.
3. First exercise existing menus: repeated Back from party creation/selection
   without submitting creation; homes/access Back; rank category Cancel and draft
   Back without saving; history → contributions → Back; recruitment Close/filter
   text; and reward purchase review → Cancel. Confirm retained parent/draft/page
   state and no return loop. Use direct `/guild bank`, `/guild homes`, `/guild ranks`,
   `/guild quests` or `/guild lfg` where supported by the fixture's existing access.
   Record Java/Bedrock presentation and pack identity. The
   [navigation audit](2026-10-20-guild-menu-return-audit.md) retains offline coverage
   for permission revocation, removed objects and failed saves; do not create those
   mutations merely to exercise Back in this window.
4. Run BANK-1, BANK-2 and QUEST-1, then SHOP-1 followed immediately by SHOP-2 while
   its captured cooldown remains active. Complete SHOP-3, cancellation and supported
   receipt replay within the existing budget. Take the scoped snapshots below
   around each action, rather than inferring attribution from the final total.
5. Capture terminal rows and visible values, perform the agreed cleanup, and release
   the window with any skipped or blocked cases explicitly recorded.

### Entry conditions and fixture

Request one acknowledged, at most 30-minute TEST/client window from the shared
coordinator, including the Market owner. Before starting, record current server
identity, backend/schema, both running plugin hashes, source provenance and
resource-pack identity. Confirm API v2 discovery and the Market transaction/retry
interface on those exact artifacts. Startup and stall-sync logs alone do not meet
this condition.

Use an existing disposable fixture guild, its already authorized bank actor, an
outside buyer, and an existing own-guild buyer. The outside buyer must be outside
the guild at preparation time and have no active pair cooldown. Use one existing
guild SELL shop with at least three units of disposable stock. Record guild,
player, shop and stall IDs. The owners must approve the fixture and cleanup plan
before its first transaction. If suitable fixtures or permissions do not exist,
the affected case stays pending; do not grant ranks or create funds to improvise
the test.

Budget: at most **three paid one-unit shop purchases**, **three bank movements**,
**one quest claim**, one pre-payment cancellation and at most two supported receipt
retries. Agree a per-purchase maximum of 10 configured currency units, or lower if
the existing fixture permits. Stop on an unexpected delta, missing durable ID,
ambiguous payment outcome, artifact change or expiry of the window. Caps,
concurrent writers, forced SQL failures and chapter rollover are exercised in
disposable automated fixtures rather than by farming or reconfiguring shared TEST.

Record actual policy before calculating expected values:

- Shop enabled flag, XP per sale, guild/buyer caps and remaining usage, pair
  cooldown, chapter phase/time bounds and prestige count. Source defaults are
  disabled, 5 XP, 500 guild XP/day, 50 buyer XP/day and 300 seconds. If disabled,
  prove a non-awarding paid sale and leave the enabled-award case pending; the
  service must still be discoverable.
- Bank fee percentages/caps, capacity, withdrawal limits, current bank balance,
  original-period high-water and usage. The runtime reads
  `progression.sources.bank_deposit` and `progression.xp_boost`; the older
  `bank_deposit_xp_per_100` setting is not the durable quote's policy source.
  Use the retained quote's `source_pool`, `award_xp`, `cap_xp`, `cap_period`,
  `occurred_at`, `boost_multiplier` and `value_per_unit` to calculate
  `units = max(0, floor(newBalance / valuePerUnit) - floor(previousHighWater / valuePerUnit))`,
  then `floor(awardXp * units * boostMultiplier)`, clipped to remaining allowance.
  If the period has no high-water row, its initial high-water is the operation's
  canonical opening balance. Daily windows start at UTC midnight; weekly windows
  start Monday UTC. Do not substitute the inspection time for `occurred_at`.
- Current total XP and level threshold, the selected quest's exact reward, item
  rewards, any applicable WEEKLY_ACTIVITY boost at delivery, and whether this
  claim would also trigger a full-set bonus. Prefer a
  fixture far enough below a level threshold to isolate visibility. Record any
  unrelated activity-source XP from completing the quest separately.

The numerical bank example below assumes `B0 = H0 = 200`, enough capacity for 300,
an eligible active actor and matching open chapter/prestige run, the source-default
1% deposit / 2% withdrawal fees without a fee multiplier, no XP boost and at least
1 XP of daily allowance. A 100-unit physical deposit therefore
requires 101 raw-gold units. Adapt the expected figures to recorded live policy;
do not change policy to make the example fit. Canonical fees are
`min(floor(amount * percentage), feeCap)`.

For each physical deposit, the fixture actor must carry only the exact quoted
currency value and no additional accepted physical currency: deposit-all/window
confirmation can consume all eligible currency. Confirm the configured
`vault.physical_currency_material` and compressed denomination/value before opening
the flow. Closing a populated GoldDepositMenu confirms its deposit; it is not a
safe cancellation exercise. Both Java and Bedrock bank-menu withdrawals credit
the personal Vault account. BANK-2 therefore needs separately available physical
currency for its redeposit, or an already supported personal-deposit entrypoint;
do not invent a conversion or grant funds. A zero configured shop cooldown makes
SHOP-2 inapplicable; record the skipped case instead of expecting a denial.

If the fixture instead selects an existing PERSONAL_ACCOUNT redeposit entrypoint,
its BANK-2 redeposit debits the personal balance by `25 + deposit fee`, leaves
physical inventory unchanged and creates no physical reservation. Under the
zero-fee 25-unit example, personal balance returns to its pre-withdrawal value.
The confirmed canonical credit and terminal NO_UNITS delivery expectations remain
the same. The default BANK-2 row below describes the physical redeposit route.

### Observations for every case

Capture before and after: UTC time, actor/guild IDs, personal currency balance,
physical currency item value, purchased item count, shop stock, Market transaction
ID/state, canonical guild bank balance, bank operation ID/fees, persisted XP,
visible menu/placeholder XP, level, XP receipt IDs, source/buyer usage and pair
timestamp. Use the actual Market schema/inspection interface supplied by its owner;
this document does not invent Market table names.

The Guilds read-only evidence is scoped to the fixture IDs:

| Record | Fields to capture |
| --- | --- |
| `vault_gold` | `guild_id`, `balance` |
| `guild_gold_operations` and `guild_gold_external_attempts` | Transaction ID, route, amount, fee, old/new balances, state, external phase |
| `guild_gold_reservations` | Physical operation UUID, exact player UUID, reserved value including fee, phase and retained item payload evidence |
| `guild_bank_xp_deliveries`, `guild_bank_xp_delivery_sequences` | Canonical transaction UUID, immutable quote/run/fingerprint, settlement balances/sequence, status, accepted XP, cache acknowledgement and retry timestamps; guild's last sequence |
| `guild_progression` and `guilds` | Total XP, current level, level field, next-level threshold, update timestamps |
| `experience_transactions` | ID, guild, actor, source, amount and timestamp for the case |
| `guild_bank_xp_high_water` | Period bounds and high-water balance |
| `guild_shop_xp_sales`, `guild_shop_xp_chapters` | Sale ID, immutable guild/buyer/time, policy snapshot, status, chapter ID |
| `guild_experience_source_usage`, `guild_shop_xp_pairs` | SHOP_SALE/BANK_DEPOSIT/buyer-pool usage and pair last-award time |
| `guild_quest_progress` and quest definition | Week/quest/guild, claim actor, claimed/delivered markers and expected rewards |
| `members`, `guild_reward_accounts`, `chapter_lifecycle` | Pre-payment membership/rank, current prestige and selected chapter phase/time bounds |

A new configured bank deposit uses **one canonical UUID** for
`guild_gold_operations.transaction_id`, `guild_bank_xp_deliveries.transaction_id`
and any awarded `experience_transactions.id`. A physical reservation uses that
UUID too. Existing unquoted operations remain legacy: absence of their delivery
row is expected, and their older XP IDs cannot be inferred from the currency ID.
A shop XP receipt uses the Market sale UUID. Its buyer usage pool is
`SHOP_BUYER:<buyer UUID>`; its guild pool is `SHOP_SALE`.

Quest claim XP uses Java `UUID.nameUUIDFromBytes` over the UTF-8 bytes of
`lumaguilds:weekly-quest:claim:<weekId>:<questId>:<guildId>`. A full-set bonus uses
`lumaguilds:weekly-quest:full-set:<weekId>:all:<guildId>`. Capture those exact IDs;
the claim actor is on `guild_quest_progress` and need not be the system XP row's
actor. Reopen progression after the callback and compare the menu and, when
available, `%lumaguilds_guild_xp_total%` / `%lumaguilds_guild_level%` with persisted
progression and the guild level mirror.

The following are **read-only query specifications**, not commands run by this
worker. Bind `?` parameters through the coordinator's existing backend interface.
Take before/after snapshots for the selected guild and each exact operation. The
operation discovery query binds guild, actor, case-start and case-end epoch-ms;
retain its ID set before acting and require one attributable new ID per bank
movement. All schema-47 queries require confirmed table presence. Raw currency
inventory and personal economy evidence come from the exact player/backend
inspection route; Guilds' SQL rows alone cannot prove those external balances.

```sql
SELECT guild_id, balance, last_modified FROM vault_gold WHERE guild_id = ?;
SELECT guild_id, total_experience, current_level, experience_this_level,
       experience_for_next_level, last_level_up, total_level_ups, last_updated
FROM guild_progression WHERE guild_id = ?;
SELECT id, level, bank_frozen FROM guilds WHERE id = ?;
SELECT guild_id, prestige_count, version FROM guild_reward_accounts WHERE guild_id = ?;
SELECT player_id, guild_id, rank_id, joined_at FROM members
WHERE guild_id = ? AND player_id IN (?, ?, ?);
SELECT chapter_id, phase, starts_at, ends_at FROM chapter_lifecycle
ORDER BY CASE WHEN phase = 'COMPLETE' THEN 1 ELSE 0 END, starts_at DESC LIMIT 1;

SELECT o.*, e.phase AS external_phase, e.purpose AS external_purpose
FROM guild_gold_operations o LEFT JOIN guild_gold_external_attempts e
ON e.transaction_id = o.transaction_id
WHERE o.guild_id = ? AND o.actor_id = ? AND o.created_at >= ? AND o.created_at <= ?
ORDER BY o.created_at, o.transaction_id;
SELECT transaction_id, route, status FROM guild_gold_operations WHERE guild_id = ?
AND status IN ('PREPARED', 'BALANCE_APPLIED', 'FAILED_COMPENSATION');
SELECT * FROM guild_bank_xp_deliveries WHERE transaction_id = ?;
SELECT * FROM guild_bank_xp_delivery_sequences WHERE guild_id = ?;
SELECT transaction_id, player_id, amount, phase, payload
FROM guild_gold_reservations WHERE transaction_id = ?;
SELECT id, guild_id, actor_id, source, amount, timestamp
FROM experience_transactions WHERE id = ?;
SELECT period_start, period_end, high_water_balance FROM guild_bank_xp_high_water
WHERE guild_id = ? AND period_start = ?;
SELECT source_pool, period_start, period_end, awarded_xp
FROM guild_experience_source_usage WHERE guild_id = ? AND source_pool = ? AND period_start = ?;

SELECT s.*, c.chapter_id FROM guild_shop_xp_sales s LEFT JOIN guild_shop_xp_chapters c
ON c.sale_id = s.id WHERE s.id = ?;
SELECT guild_id, buyer_id, last_award FROM guild_shop_xp_pairs WHERE guild_id = ? AND buyer_id = ?;
SELECT week_id, quest_id, guild_id, current_count, claimed, completed_at,
       claim_actor_id, reward_delivered FROM guild_quest_progress
WHERE week_id = ? AND quest_id = ? AND guild_id = ?;
SELECT week_id, quest_id, target_count, experience_reward, item_rewards
FROM weekly_quest_definitions WHERE week_id = ? AND quest_id = ?;
SELECT * FROM guild_quest_weekly_bonus WHERE week_id = ? AND guild_id = ?;
```

For usage/high-water queries, bind the original captured period start and repeat
usage for the bank quote's pool, `SHOP_SALE` and each `SHOP_BUYER:<UUID>` as relevant.
For Market proceeds, repeat the canonical operation join with
`WHERE o.transaction_id = ?` using its owner-supplied Guilds credit UUID; do not
infer that UUID from the shop XP sale UUID.
Retain physical payloads only in fixture evidence, not general logs. Bank delivery
status `AWARDED` stores its amount separately in `accepted_xp`; shop status stores
`AWARDED:n`. `WAITING` is response-only and must not be expected as a database row.
An awarded bank row may briefly have `cache_pending = 1`; successful actual cache
refresh acknowledges that exact UUID with zero. The timer starts after one tick
and then runs every 1,200 ticks, attempting one READY head per guild per drain.
If READY/cache work remains, capture status, sequence and attempt timestamps on
the next scheduled observation; do not create another currency operation or force
a failure/restart to demonstrate recovery. Failure injection and ordering already
have the source-specific native contracts recorded above.

### Cases and expected outcomes

| Case | Bounded action | Required outcome |
| --- | --- | --- |
| BANK-1: new physical growth | Deposit exactly 100 bank units through the ordinary raw-gold flow with the example fixture. | Physical value decreases 101; personal balance stays unchanged; bank increases 100 to 300. One PHYSICAL_ITEM/CREDIT operation is APPLIED with fee 1 and external CONFIRMED/DEPOSIT; its reservation is COMMITTED for 101. The same UUID has AWARDED delivery/accepted_xp 1 and one BANK_DEPOSIT XP audit row; sequence increases once, high-water becomes 300 and usage/visible/persisted XP increase by 1. After cache refresh, cache_pending is 0; no level-up, stock or Market receipt change. |
| BANK-2: withdraw and redeposit | Withdraw 25 units to the personal Vault account, then deposit 25 separately available physical units in the same policy period. Use an existing supported personal-deposit entrypoint only if explicitly selected in the fixture. | Under the example fees, bank moves 300 → 275 → 300; personal balance rises 25 on withdrawal, then stays there for a physical redeposit, whose physical value decreases 25. The withdrawal is its own PERSONAL_ACCOUNT/DEBIT APPLIED operation with external CONFIRMED/WITHDRAWAL and no XP delivery. The new deposit UUID has its own confirmed APPLIED credit and terminal NO_UNITS delivery; sequence increases once, high-water remains 300, and XP/audit count/usage do not increase. Record any nonzero fees explicitly; bank must not exceed prior high-water. This is not a return of the withdrawn physical items. |
| QUEST-1: reward without level-up | Finish and claim one eligible quest once; use a quest that is not the last full-set milestone if available. | Persisted and visible XP increase by the quest reward with any recorded WEEKLY_ACTIVITY boost (whole-award floor), plus separately attributable activity XP. One stable WEEKLY_ACTIVITY receipt records that claim reward; record a separate stable full-set receipt if applicable. Claim/delivery markers and any item rewards match the definition. Bank and shop stock remain unchanged unless the quest activity itself is BANK-1, which must be recorded as the same case. |
| SHOP-1: outside paid SELL | Buy one unit once, under a prepared quote with remaining allowances. | Market durably records payment and stock movement before Guilds completion. Buyer currency decreases by the quote's total debit; buyer item count increases one; stock decreases one; guild bank increases by the quote's net guild proceeds; fee/tax entries explain any difference. One captured sale and one SHOP_SALE XP receipt retain the same sale UUID. XP increases by the minimum of captured per-sale XP and remaining guild/buyer allowance; usage and pair time change once. Menu and database agree. |
| SHOP-2: cooldown across shops | Make one further paid one-unit purchase by that outside buyer before the captured cooldown elapses; a second shop of the same guild may be used if already available. | Normal payment, bank proceeds and stock movement occur once for this new sale ID. Terminal XP outcome is COOLDOWN, with zero additional XP receipt/usage/pair advancement. Do not treat a new paid transaction as a retry of SHOP-1. |
| SHOP-3: own-guild purchase | An existing guild member buys one unit under an ordinary paid SELL quote. | Normal single payment/stock/bank movement and an OWN_GUILD terminal receipt; no SHOP_SALE XP, usage or pair award. Membership is the pre-payment snapshot. |
| CANCEL-1: no payment | Open and cancel one purchase before payment begins. | No player currency, stock, item, bank or XP change. If no preparation happened, no Guilds sale is expected. For a stored PREPARED sale, supported abort must retain ABORTED for that same ID. An already captured OWN_GUILD, DISABLED or CHAPTER_FROZEN receipt retains its original non-awarding status; the API's PREPARED response alone does not mean the stored row is still PREPARED. |
| RETRY-1: acknowledged award replay | Through Market's supported retry mechanism, redeliver completion for SHOP-1's durably paid sale UUID at most twice. | Same terminal AWARDED amount, no new charge, proceeds, items, stock movement, XP receipt or cap usage. Visible XP equals persisted XP. No repeated level-up event/effect. If the candidate exposes no supported bounded replay mechanism, this row remains pending and the SQL/provider tests supply only offline evidence. |
| ABORT-1: prepared definite failure | Only if the Market owner has a supported bounded fixture for a definitely failed prepared sale, use it once within the same retry budget; otherwise retain offline coverage. | `abort` changes a stored PREPARED row to ABORTED; an already captured non-awarding terminal decision is retained. A subsequent supported completion retry cannot award either result. No final charge/proceeds/stock loss after any documented compensation. If failure happened before prepare, no sale receipt is expected. A timeout or unknown payment state is not evidence for abort/refund. |

If the outside policy is disabled, SHOP-1 must retain DISABLED with zero XP while
the paid transaction still completes. Record that result explicitly; it does not
prove the positive-award or cooldown cases. The automated SQLite/MariaDB contracts
cover clipping at caps, UTC boundaries, concurrent receipts, retained terminal
history, disband/prestige and frozen/changed chapters. MARKET-07 must provide the
personal/free/BUY/barter exclusion proof on its exact candidate; extra live trades
are outside the small transaction budget above.

### Schema 47 migration and rollback

The candidate performs an additive upgrade in the **same configured database**.
Both migration runners and repository initialization ensure
`guild_bank_xp_deliveries` and `guild_bank_xp_delivery_sequences`; the delivery table
has a unique `(guild_id, settlement_sequence)` constraint and indexes
`idx_bank_xp_delivery_pending` / `idx_bank_xp_cache_pending`. Record the actual
pre-upgrade version and schema: an older installed database may also need earlier
migrations. After startup, verify version 47 and actual table/index definitions.
The standalone `schema.sql` version-19 seed is not this migration procedure.
The SQLite-to-MariaDB utility omits canonical money/high-water state and these
delivery tables; switching engines is outside this acceptance window.

Starting the candidate is a write operation even before client actions: schema
initialization and the startup recovery timer can change state. Retaining the old
JAR alone is therefore insufficient rollback evidence. There is no reviewed
down-migration for outstanding schema-47 receipts, and mixed old/new writers or a
binary that resumes transient callback awarding are not supported recovery routes.
Do not clear the queue, drop its tables, lower the schema version or replay legacy
operations to make a binary rollback appear clean.

Before cutover the coordinator must identify the restore operator, exact retained
old artifact/configuration and a verified consistent pre-upgrade snapshot, its
restore destination and the writers that must be stopped. The rollback must cover
Guilds balances, canonical operations/external attempts/physical reservations,
progression/high-water/usage/retained deliveries, plus every affected player
economy/inventory and Market stock/transaction store. A shared snapshot rewind is
usable only while overlapping writers are excluded and their owners coordinate
the same restore point. If that route cannot be demonstrated, candidate startup
and live mutation remain pending; the OLD_BUILD read-only preparation can continue.

### Cleanup and release of resources

Before the window, the fixture owners must choose either supported fixture-only
cleanup that preserves durable journals, or an isolated complete snapshot restore
covering player economy/inventory, Market stock/transactions and Guilds state
together. A shared database rewind cannot be used while other projects may write.
Bank withdrawal alone is not an XP rollback, and deleting an XP history row is
not receipt cleanup: retained shop sales deliberately prevent repeat awards.

After recording final balances and IDs, perform only that agreed cleanup through
the coordinator. Retain proof of the transaction results and cleanup outcome.
Do not delete receipt IDs, lower high-water values, alter live ranks, manually
regrant XP or retry an uncertain transaction as a fresh sale. An ambiguous result
stops further transactions for the fixture and is handed to the Market/bank owner
with its durable IDs. Release the shared window with explicit remaining cases.

## Completion record

Source implementation, independent review, native green verification, paired
execution and MariaDB contracts are recorded for the exact checkpoint source.
GUILDS-05 still needs the current TEST/rollback/backend/player/pack evidence and
coordinated client window, then the bounded live cases and cleanup. Required
fixture inputs are guild UUID, bank actor UUID and authorized access, outside and
own-guild buyer UUIDs, shop/stall IDs, supported Market inspection/replay path,
existing funds/stock, and selected quest/week IDs. The coordinator chooses the
rollback/cleanup route and actual supported deposit route; unavailable fixtures
leave their cases pending. No live transaction, TEST deployment/restart, production
action or remote publication is performed by this documentation update.

Related source records:

- [Deposit and quest XP visibility](2026-10-18-deposit-xp-visibility.md)
- [Shop API registration](2026-10-18-shop-xp-registration.md)
- [Provider](../../src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildShopXpProvider.kt)
- [Combined SQL/cache tests](../../src/test/kotlin/net/lumalyte/lg/infrastructure/services/DepositXpVisibilityTest.kt)
- [Shop SQL receipt tests](../../src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildShopXpRepositorySQLTest.kt)
