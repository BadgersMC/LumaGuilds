# Deposit XP anti-farming acceptance investigation

## Scope and SPEAR

User asked to continue unverified Guilds acceptance and check withdrawal/redeposit XP farming protection. Canonical BadgersMC/LumaGuilds main283d75f1 was fetched and inspected unchanged. Continue the existing reviewed candidate5ccbec01441dcb293f74acdb1e93c0b472f3178a in this separate diagnostic worktree. The active menu delivery worktree remains clean. The initial diagnostic phase made no engine changes or JAR; the later authorized correction is recorded below.

Project EARS/state helpers are absent. This record and the Roundtable messages are manual traceability. New expectations below are anti-abuse acceptance probes, not claims that these guarantees already existed in the approved daily policy.

## Expected behavior under investigation

- BF-001: Successful withdrawal/redeposit in one source period must not repeat ordinary BANK_DEPOSIT XP.
- BF-002: Moving a withdrawal before the period boundary and its redeposit after the boundary must not convert recycled money into fresh XP, including after database reopen.
- BF-003: Deposit-quest completion must not be obtainable solely by sending gross deposit notifications for recycled money. Quest XP must remain exactly once per legitimate quest claim.
- BF-004: Live evidence requires a normal nonstaff actor, known activity disposition, before/after inventory/bank/XP, and supported current fixtures. Unit tests are not live acceptance.

## Proof at the unchanged production engine

Two focused test invocations were performed on the diagnostic branch. The first could not compile because ignored pinned companion API JARs were missing in the new checkout. It is not product failure evidence. After copying the exact API JARs from the existing verified candidate checkout and recording their hashes, the focused test compiled normally.

BankPeriodBoundaryProbeTest executes the real GuildGoldService, SQL bank repository and durable deposit XP consumer on an isolated SQLite database, with controlled external economy and clock. Same-day deposit1000, award10XP, withdraw1000 and redeposit1000 passes NO_UNITS. With the redeposit moved to the next day after closing/reopening repositories, expected NO_UNITS instead returns AWARDED. This is a reproducible policy-boundary hole.

The refined BankQuestRecyclingProbeTest uses the real canonical SQL gold service and XP consumer for physical deposit2500, successful personal-account withdrawal2500, and physical redeposit2500. The test publishes the normal post-credit bank event after each confirmed deposit, matching BankService event publication. Ordinary deposit XP returns NO_UNITS for the second deposit, but the real quest service/listener completes a5000 deposit quest and awards500XP through the recording reward sink. Quest persistence is an in-memory repository and external inventory/account ports are controlled fixtures; this is not live Bukkit/player acceptance or concurrent SQL quest-claim proof. The initial event-only probe was refined to include real canonical withdrawal settlement; its narrower evidence was superseded. Existing quest exactly-once claim tests remain green.

Combined focused run:14 tests,12 pass,2 expected anti-abuse assertion failures,0errors,0skips. Native XML and build logs retained under operations/guilds-acceptance-20261024. Do not mark these expectations implemented, green or deployed.

## Architecture review and recommended correction

Current high-water is keyed by guild and source-period start. A new period seeds from the balance immediately before its first deposit. A withdrawal before rollover lowers that opening seed. The durable XP receipt protects repeated transaction IDs and same-period consumption, but not that new-period wash.

QuestProgressListener consumes GuildBankDepositEvent.amount without a canonical transaction ID, old/new bank balance or net-new contribution amount. Its callback is separate from durable XP delivery. Reusing ordinary XP accepted amount would be incorrect because AFK, cap clipping, boost and unit rounding are separate eligibility decisions.

Recommended follow-up: define a persistent guild contribution ledger shared by deposit XP and deposit quests, account for successful player withdrawals across period and prestige boundaries, and make progress publication idempotent by canonical gold transaction ID. Preserve fresh contributions after legitimate guild spending, exclude admin/interest/fees/system funding, and reserve/settle currency plus contribution accounting atomically with existing external payment recovery. Do not silently switch to lifetime maximum bank balance: after reaching capacity it could prevent all future earning, including legitimate spending/refill cycles. This needs a focused source change and regression/SQLite/MariaDB review before a new candidate.

## Live acceptance boundary

No live access, fixture mutation, deployment or client acceptance was performed for this correction. The previous TEST/client window was released. Live acceptance remains open and separate from isolated SQL proof.

## Authorized correction (2026-10-24)

User requested fixing both proven holes. BF-005: confirmed player withdrawals create persistent guild-wide recycling debt; future deposits repay debt before earning contribution rewards. Fees, rejected/ambiguous/compensated payouts, admission, admin, interest and system operations must not create fresh contributions. BF-006: settlement records provenance exactly once in the canonical gold transaction; period, prestige and repository reopen never reset debt. BF-007: deposit quests consume verified canonical contribution receipts atomically and at most once per quest, preserving normal claim recovery; unverified gross events cannot count. BF-008: fresh refills after actual guild spending remain eligible; ordinary XP caps, eligibility and captured policy remain independent of quest contribution amount.

Cutover: no historical eligibility or rewards are fabricated. First use derives outstanding withdrawal debt from retained confirmed canonical player operations, excluding the current transaction. Equal-time historical credits precede debits conservatively; pre-journal history cannot be reconstructed. Historic READY XP without new provenance fails closed. System spending deficits do not create debt. No GUI changes, TEST, client, merge or deployment in this source correction scope.

## Engine and architecture

Additive schema v48 owns retained contribution accounts, transaction receipts, preparation guards and per-quest consumption markers. Confirmed canonical settlement atomically changes the ledger. A cross-instance preparation guard closes the pending-operation race without adding lifecycle or preparation locks to finalization. The XP consumer uses cumulative fresh boundaries to avoid rounding losses after mixed recycled/fresh deposits. One existing contract now expects fresh external refill after a prestige system fee to earn XP; the stale prior receipt remains consumed. This is BF-008, not acceptance of recycled withdrawals.

Quest progress queries confirmed receipts, not event amounts or accepted ordinary XP. Run/time/identity checks precede atomic progress and consumption; claims retain existing stable reward identities. All original event API contracts remain intact; the new receipt event has its own contract coverage. No domain/application dependency on Bukkit or SQL was introduced.

Native proof exposed a mixed-collation join failure with default MariaDB legacy tables; using the locked run state avoids comparing differently collated guild columns. Original red probes were retained and expanded to real SQL quest persistence. New fault injection proves bank/debt rollback and recovery, plus quest marker rollback/retry. Concurrent preparation and concurrent quest replay are covered. Existing application claim tests remain intact.

Project-local EARS/state helpers remain unavailable; this plan, docs/tasks.md and Roundtable maintain manual SPEAR traceability. No GUI changes require a new visual artifact.

Review refinement: the first draft used historical high-water deficits for debt seeding; this was rejected because it could penalize real past guild spending. The final bootstrap uses only confirmed player deposit/withdrawal journals. A dedicated upgrade/spending regression prevents reintroducing that penalty. Historical eligibility and XP are never backfilled.

## EARS acceptance trace

- BF-005/006: WHEN a confirmed player withdrawal settles THEN the system SHALL atomically persist guild-wide withdrawal debt and a canonical receipt, and SHALL retain that debt across source periods, restarts and prestige.
- BF-003/007: IF a bank notification lacks a confirmed, matching contribution receipt or has already been consumed for a quest THEN the system SHALL NOT increase that quest progress or award its completion reward.
- BF-001/002/008: WHEN a player deposits gold THEN the system SHALL repay outstanding withdrawal debt before counting fresh contribution, and SHALL preserve fresh unit fractions and legitimate refill rewards after system spending, subject to the captured XP policy.
- BF-004: WHILE no shared TEST/client handoff is held the system SHALL keep runtime acceptance unverified and SHALL NOT mutate shared server/client state.

Test mapping: BankContributionContract covers BF-001/002/005/006/008 on SQLite and MariaDB; BankQuestContributionContract covers BF-003/007 with real SQL progress, concurrent replay and rollback/reopen; GuildEventApiContractTest preserves legacy API and verifies the new event; GuildGoldMigrationTest verifies additive repair without debt loss or reward fabrication. BF-004 remains a deferred live gate.

## Final local verification

Java25 / Paper26.2.build.129-stable, original pinned companion APIs and actual Market1.0.53 runtime SHA230504aa: full suite 2310 tests, zero failures/errors, four unchanged optional skips (offline snapshot and three existing target-provider placeholders). Native MariaDB contribution/quest/preparation/ownership cases ran in that suite; the separate native reward profile passed95 tests, zero failures/errors/skips. Additive v48 migration/repair and the original event API contracts pass. Wiki frontmatter42 pages and13-topic parity pass. Final build command: test shadowJar mariaDbRewardTest -PmariaDbTestPort=<owned-loopback-port> --offline -PreleaseVersion=3.0.0-bank-contribution-UNMERGED-TEST.

Local unmerged artifact SHA256612c1e9d7733bcff04c5b84d892f9b568fdd4f93c29634e7470f157a1759b00f, version3.0.0-bank-contribution-UNMERGED-TEST. Evidence: operations/guilds-acceptance-20261024/{fix-full-final.log,fix-candidate.json,fix-concurrency.log,probe-results.json}. No new skip or test reduction;35 cases added over the2275-case candidate. Initial full verification caught the event-contract expansion and old schema-version expectations; a separate receipt event preserves the old API and migration assertions now track schema48.

Hosted checks, independent review, canonical merge and live TEST/client acceptance remain separate gates. No deployment, restart, pack replacement, permission grant or production write occurred for this correction.
