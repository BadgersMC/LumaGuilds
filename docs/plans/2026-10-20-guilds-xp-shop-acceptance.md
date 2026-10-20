# Guilds XP and shop acceptance

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
| Deposit XP becomes visible below a level threshold | `BankServiceBukkit.postDepositApplied` invokes Chapter Two bank growth after the canonical physical/personal credit. `ChapterTwoGuildAwardService` publishes committed awards through the deferred DI callback. `DepositXpVisibilityTest` uses the real SQL writer and cached repository. `BankServiceBukkitDelegationTest` covers the physical deposit adapter. | Current TEST raw-gold removal, canonical bank credit and visible progression in one player journey. |
| Quest awards and replays refresh the saved total | `awardUncappedSystemExperienceOnce` refreshes both new and duplicate receipts. Duplicate publication supplies no level. `QuestService` derives stable claim/full-set/leaderboard IDs; reward delivery markers permit recovery. `DepositXpVisibilityTest` and `QuestServiceTest` cover replay. | A current TEST quest completion and claim, including any item reward or full-set bonus in that fixture. |
| Numeric and legacy timestamps remain readable | Progression refresh and preload use the same mapper, which reads raw values before conversion. `mixedTimestampsRemainReadable` checks numeric epoch milliseconds, ISO text and nullable last-level-up. | No live database timestamp rewrite is needed or proposed. |
| Repeated bank movement does not farm XP | The high-water repository reserves only newly crossed 100-unit balance boundaries in the configured period. Its SQL test and the combined visibility test cover withdrawal/redeposit; the service test checks AFK exclusion before reservation. | A same-period player withdrawal/redeposit with fees and the actual high-water row captured. |
| API v2 is published independently of the reward switch | `LumaGuilds.onEnable` registers after database/Koin initialization; disable unregisters the plugin's services. `GuildShopXpStartupTest` checks source wiring; `GuildShopXpRegistrationTest` uses real MockBukkit service discovery. | Discovery from the current paired TEST plugins and their actual classloaders. |
| A paid shop receipt awards at most once | `GuildShopXpRepositorySQL` commits progression, source/buyer usage, pair cooldown and terminal receipt in one SQL transaction. Existing SQLite and optional MariaDB tests cover concurrency, rollback/retry and retained receipts after XP-history cleanup. | Market's durable payment/stock transaction and the exact candidate's retry path. |
| Failed or ineligible shop trades cannot later become awards | Existing tests cover terminal aborts, own-guild membership captured before payment, disabled/zero-cap policies, identity conflicts, prestige/disband guards and chapter freeze/rollover. | MARKET-07 must prove that personal/free/BUY/barter trades never call this API and that `complete` follows a durably paid SELL transaction. |

The durable terminal codes present in this combined source are `AWARDED:n`,
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

At this inspection's original source, bank currency credit, high-water reservation
and XP award were separate commits. The subsequent **GUILDS-07** correction
combines high-water and XP after reproducing reservation loss on an XP SQL failure;
see the [bank atomicity note](2026-10-20-bank-xp-atomicity.md). Currency credit still
commits separately, and currency recovery can bypass the ordinary post-deposit
callback. Neither the cache repair nor the atomic XP group supplies a durable
replay of every missed deposit callback. Do not issue compensating XP from a
timeout; retain currency and XP evidence for a separate recovery decision.

The starting test task fingerprints `MARKET_API_JAR`, while the shop lifecycle test
reads `MARKET_RUNTIME_JAR`. The coordinator added path/content inputs for the
latter in the combined integration branch. Until that change is included, force
the paired test to execute rather than accepting a cached or skipped result.

## Native verification handoff

Run on the coordinator's serialized Java 25 / Paper 26.2 build slot. Use the
checkout's Gradle wrapper and existing compile-only dependencies. No new dependency
installation or alternative Paper profile is part of this task. On Windows use
`gradlew.bat` with the same arguments.

First reproduce the cache defect at the test-only revision above:

```sh
./gradlew test \
  --tests 'net.lumalyte.lg.infrastructure.services.DepositXpVisibilityTest.shopRewardReplayRefreshesCache' \
  --offline --no-daemon --rerun-tasks --no-build-cache
```

After the repair, use these exact selectors for the affected contracts:

```sh
./gradlew test \
  --tests 'net.lumalyte.lg.infrastructure.services.DepositXpVisibilityTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildShopXpProviderTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildShopXpRegistrationTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildShopXpStartupTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildShopXpConfigurationTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.ProgressionServiceBukkitAwardTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.BankServiceBukkitDelegationTest' \
  --tests 'net.lumalyte.lg.application.services.ChapterTwoGuildAwardServiceTest' \
  --tests 'net.lumalyte.lg.application.services.QuestServiceTest' \
  --tests 'net.lumalyte.lg.infrastructure.persistence.guilds.BankProgressionRepositorySQLTest' \
  --tests 'net.lumalyte.lg.infrastructure.persistence.guilds.ExperienceAwardRepositorySQLTest' \
  --tests 'net.lumalyte.lg.infrastructure.persistence.guilds.GuildShopXpRepositorySQLTest' \
  --offline --no-daemon --rerun-tasks --no-build-cache
```

For the exact paired Market artifact, MARKET-08 must supply its source revision,
build identity, byte count and SHA-256. Set `MARKET_RUNTIME_JAR` to that existing
file. Set `MARKET_API_JAR` to the verified companion file that contains the stall
read API, recording its hash as well if it is separate. Then run:

```sh
./gradlew test \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildShopXpRegistrationTest' \
  --tests 'net.lumalyte.lg.infrastructure.services.GuildStallRuntimeContractTest' \
  --offline --no-daemon --rerun-tasks --no-build-cache
```

Inspect JUnit XML to confirm that `marketLifecycle` and `decodesCompanionRuntime`
actually executed with zero skips. The lifecycle test uses the actual Market
gateway and real provider discovery, with repository outcomes controlled by the
test. It does not execute Market payment or stock persistence. A missing/blank
optional variable is a skip; a configured file that does not exist must fail.

The optional MariaDB shop profile is separate:

```sh
./gradlew test \
  --tests 'net.lumalyte.lg.infrastructure.persistence.guilds.GuildShopXpMariaDbTest' \
  --offline --no-daemon --rerun-tasks --no-build-cache
```

It requires `GUILD_SHOP_XP_TEST_MARIA_PORT` for a disposable loopback instance and
the dedicated `lg_shop_xp_test` fixture database. It drops and recreates its tables.
The coordinator must verify fixture ownership before setting that variable; it
must never address the shared TEST database. Nine test methods are present in the
starting source. A skipped optional profile is not MariaDB proof. The similarly
named `mariaDbRewardTest` task selects other repository classes and does not run
these shop concurrency/rollback cases.

Record commit/tree, command, Java version, companion hashes, actual
passed/failed/skipped counts and XML location for every run. A combined
`check shadowJar` and hosted review belong to the coordinator's final candidate
gate; historical totals are not substitute results for that candidate.

## Bounded live TEST matrix

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
  today's high-water balance and BANK_DEPOSIT usage. For this source,
  `units = max(0, floor(newBalance / 100) - floor(previousHighWater / 100))`.
  Apply the configured award value, any active eligible boost, and remaining cap.
- Current total XP and level threshold, the selected quest's exact reward, item
  rewards and whether this claim would also trigger a full-set bonus. Prefer a
  fixture far enough below a level threshold to isolate visibility. Record any
  unrelated activity-source XP from completing the quest separately.

The numerical bank example below assumes `B0 = H0 = 200`, enough capacity for 300,
the source-default 1% deposit / 2% withdrawal fees without a fee multiplier, no XP
boost and at least 1 XP of daily allowance. A 100-unit physical deposit therefore
requires 101 raw-gold units. Adapt the expected figures to recorded live policy;
do not change policy to make the example fit. Canonical fees are
`min(floor(amount * percentage), feeCap)`.

For the physical deposit case, the fixture actor must carry only the exact quoted
currency value and no additional accepted physical currency: deposit-all/window
confirmation can consume all eligible currency. Confirm this before opening the
deposit flow. A zero configured shop cooldown makes SHOP-2 inapplicable; record
the skipped case instead of expecting a denial.

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
| `guild_progression` and `guilds` | Total XP, current level, level field, next-level threshold, update timestamps |
| `experience_transactions` | ID, guild, actor, source, amount and timestamp for the case |
| `guild_bank_xp_high_water` | Period bounds and high-water balance |
| `guild_shop_xp_sales`, `guild_shop_xp_chapters` | Sale ID, immutable guild/buyer/time, policy snapshot, status, chapter ID |
| `guild_experience_source_usage`, `guild_shop_xp_pairs` | SHOP_SALE/BANK_DEPOSIT/buyer-pool usage and pair last-award time |
| `guild_quest_progress` and quest definition | Week/quest/guild, claim actor, claimed/delivered markers and expected rewards |

A shop XP receipt uses the Market sale UUID. Quest IDs are derived from the stable
claim identity. Bank XP receipt IDs are generated independently of the currency
operation; correlate the captured before/after set, source, actor, time and amount
instead of assuming those IDs are equal. Reopen progression through the ordinary
player flow after the callback has run; record both visible and persisted values.

### Cases and expected outcomes

| Case | Bounded action | Required outcome |
| --- | --- | --- |
| BANK-1: new physical growth | Deposit exactly 100 bank units through the ordinary raw-gold flow with the example fixture. | Raw currency value decreases 101; bank increases 100 to 300; one APPLIED currency operation records a fee of 1; high-water becomes 300; one BANK_DEPOSIT XP receipt awards 1; cache/menu and database increase by 1 without a level-up. Shop stock and Market receipts stay unchanged. |
| BANK-2: withdraw and redeposit | Withdraw 25 units, then redeposit the same 25 in the same policy period. | Under the example fees, bank moves 300 to 275 to 300 and physical currency increases/decreases 25. High-water remains 300, XP and XP receipt count do not increase, and each currency movement has its own terminal operation. If actual fees are nonzero, record their additional debit explicitly; the bank must not exceed its prior high-water. |
| QUEST-1: reward without level-up | Finish and claim one eligible quest once; use a quest that is not the last full-set milestone if available. | Persisted and visible XP increase by the exact quest reward plus separately attributable activity XP. One stable WEEKLY_ACTIVITY receipt records the reward. Claim/delivery markers and any item rewards match the definition. Bank and shop stock remain unchanged unless the quest activity itself is BANK-1, which must be recorded as the same case. |
| SHOP-1: outside paid SELL | Buy one unit once, under a prepared quote with remaining allowances. | Market durably records payment and stock movement before Guilds completion. Buyer currency decreases by the quote's total debit; buyer item count increases one; stock decreases one; guild bank increases by the quote's net guild proceeds; fee/tax entries explain any difference. One captured sale and one SHOP_SALE XP receipt retain the same sale UUID. XP increases by the minimum of captured per-sale XP and remaining guild/buyer allowance; usage and pair time change once. Menu and database agree. |
| SHOP-2: cooldown across shops | Make one further paid one-unit purchase by that outside buyer before the captured cooldown elapses; a second shop of the same guild may be used if already available. | Normal payment, bank proceeds and stock movement occur once for this new sale ID. Terminal XP outcome is COOLDOWN, with zero additional XP receipt/usage/pair advancement. Do not treat a new paid transaction as a retry of SHOP-1. |
| SHOP-3: own-guild purchase | An existing guild member buys one unit under an ordinary paid SELL quote. | Normal single payment/stock/bank movement and an OWN_GUILD terminal receipt; no SHOP_SALE XP, usage or pair award. Membership is the pre-payment snapshot. |
| CANCEL-1: no payment | Open and cancel one purchase before payment begins. | No player currency, stock, item, bank or XP change. If no preparation happened, no Guilds sale is expected. If the candidate prepares before this cancellation, the Market owner must show a durable ABORTED result for that same sale ID. |
| RETRY-1: acknowledged award replay | Through Market's supported retry mechanism, redeliver completion for SHOP-1's durably paid sale UUID at most twice. | Same terminal AWARDED amount, no new charge, proceeds, items, stock movement, XP receipt or cap usage. Visible XP equals persisted XP. No repeated level-up event/effect. If the candidate exposes no supported bounded replay mechanism, this row remains pending and the SQL/provider tests supply only offline evidence. |
| ABORT-1: prepared definite failure | Only if the Market owner has a supported bounded fixture for a definitely failed prepared sale, use it once; otherwise retain offline coverage. | `abort` retains ABORTED and a subsequent supported completion retry cannot award. No final charge/proceeds/stock loss after any documented compensation. If failure happened before prepare, no sale receipt is expected. A timeout or unknown payment state is not evidence for abort/refund. |

If the outside policy is disabled, SHOP-1 must retain DISABLED with zero XP while
the paid transaction still completes. Record that result explicitly; it does not
prove the positive-award or cooldown cases. The automated SQLite/MariaDB contracts
cover clipping at caps, UTC boundaries, concurrent receipts, retained terminal
history, disband/prestige and frozen/changed chapters. MARKET-07 must provide the
personal/free/BUY/barter exclusion proof on its exact candidate; extra live trades
are outside the small transaction budget above.

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

Source inspection, the native failing regression and the scoped provider repair
are complete. Native green verification is delegated and pending.
Current Market candidate provenance, paired execution, optional MariaDB execution,
current TEST artifact verification, the shared window, live cases and cleanup
remain pending. No live transaction, TEST deployment/restart, production action,
push or PR mutation has been performed by this worker.

Related source records:

- [Deposit and quest XP visibility](2026-10-18-deposit-xp-visibility.md)
- [Shop API registration](2026-10-18-shop-xp-registration.md)
- [Provider](../../src/main/kotlin/net/lumalyte/lg/infrastructure/services/GuildShopXpProvider.kt)
- [Combined SQL/cache tests](../../src/test/kotlin/net/lumalyte/lg/infrastructure/services/DepositXpVisibilityTest.kt)
- [Shop SQL receipt tests](../../src/test/kotlin/net/lumalyte/lg/infrastructure/persistence/guilds/GuildShopXpRepositorySQLTest.kt)
