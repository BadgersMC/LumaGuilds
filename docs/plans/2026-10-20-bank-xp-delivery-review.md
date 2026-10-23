# Independent review: durable bank deposit XP delivery

Roundtable **GUILDS-12**, task `01a15adb-f07d-75ec-8f21-c4283ee67b33`, seat `guilds-bank-delivery-review`.

## Source disposition

**Approved for the scoped new-deposit recovery path; no blocking production or regression-source finding remains at the reviewed commits.** The coordinator reported successful native production/test compilation of the initial integrated candidate; the full SQL/regression and artifact gates remain pending. This review does not authorize deployment, a canonical merge, historical compensation, or live currency/player actions.

The earlier high-water/XP transaction correction did not retain an XP delivery intent with a committed currency operation. This change closes that gap for new personal-account and physical-item deposits prepared by the configured `GuildGoldService`. It does not reconstruct eligibility for older operations, replay deposit events, or promise that a pending award survives a chapter/prestige transition.

## Exact reviewed source

| Item | Commit | Tree |
| --- | --- | --- |
| Dispatch baseline | `a7896b57266d7a2e6387b2f764e8e9402ae503d8` | `9796eec0396d0493978deddb7e8c0b17127965bb` |
| Core quote, settlement and consumer implementation | `6a71ced90b69b3fcdf6c348a3a7b1f1d7c261e70` | `d4447d1e4d38e9f8ebd0ae79faa8aa4c7fe17388` |
| Runtime, publication, migration and lifecycle fences | `6822aa41f957615de1a6bedf29f3cd4597fe18f4` | `253acfbe6ae49c0f7bc3cb89d48a020c39a8ead0` |
| Initial independent SQL contracts | `213fc4f688abd16e92d0a4facc09c32c8af2e7a1` | `23fce6d3283afbb0d0869a0797065b44144be2c0` |
| Fairness, absent-guild and composed publication contracts | `6970eeccf10cf9685fe389f0017562c61e27c43f` | `674a3d840fd0a5e960051eb6a62859c261c302c0` |

The implementation worktree was clean at `6822aa41`; the contracts worktree was clean at both reviewed contract commits. The implementation delta from the dispatch baseline and both contracts deltas passed `git diff --check`.

The reviewer read production and test source, traced the existing currency and progression paths, and reviewed the immutable commits. The reviewer ran no builds, tests, database commands, remote operations, PR actions, or shared-resource mutations. The only reviewer-owned edit is this new review document.

## Findings and accepted invariants

### 1. Quote and external-payment permission

`GuildGoldService.existingDepositResultOrPrepare` preserves an existing canonical operation and its original quote without asking the current XP quote provider to replace it. For a new configured deposit, `GuildGoldRepositorySQL.prepareDeposit` writes the canonical `PREPARED` operation and `BankDepositXpLedger.capture` on the same connection and transaction. A failure writing either rolls back both, before `beginExternal` permits payment.

The quote contains the original occurrence time, source pool, award amount, cap, period, enabled state, activity disposition, effective boost multiplier and gold value per unit. SQL capture adds chapter identity/eligibility, prestige count and the canonical mutation fingerprint. The fingerprint retains operation, guild, actor, route, amount, fee and description. The source is constrained to `BANK_DEPOSIT` and the currency mutation to a positive player deposit.

The new preparation guard rejects a guild already absent at the locked capture point before inserting an operation or calling a money provider. Delayed delivery of an already-settled deposit after deletion is handled separately as a terminal stale result.

The production Koin binding supplies the quote factory. Its lazy resolution avoids introducing a `GuildGoldService`/progression construction cycle. Compatibility constructors without that factory retain their existing behavior; they are not the configured runtime path being approved.

### 2. All true settlement paths retain delivery atomically

`GuildGoldRepositorySQL.finishApplied` invokes the ledger settlement hook on its caller-owned transaction. `completeExternal` now also uses a transaction. The inspected hooks cover all three relevant entry paths:

| Currency path | Delivery boundary |
| --- | --- |
| Personal-account deposit | Canonical balance, `APPLIED`, sequence and `READY` commit together after the external debit is confirmed. |
| Physical-item deposit | `BALANCE_APPLIED` remains provisional. `completeExternal` creates `READY` only after confirmed consumption. |
| Confirmed-credit recovery | Both recovery from `PREPARED` and finalization of a held balance use the same settlement hook. |

The ledger requires external phase `CONFIRMED` and purpose `DEPOSIT`, verifies the original mutation and exact old/new balance relation, and records a monotonic guild settlement sequence. Repeating a canonical result cannot allocate another sequence or invent a new quote.

Admissions, withdrawals, interest, administrative credits and system credits do not prepare delivery quotes. An operation without a quote is treated as legacy or outside this route. Neither the consumer nor the cache retry path calls an economy/inventory provider.

Rejection and compensation retain `CANCELLED` for an unactivated quote. A settled quote cannot silently be cancelled: the consistency check rolls back such a conflicting status update. Unknown external outcomes remain in the existing held currency protocol; this feature does not infer failure from elapsed time or issue speculative refunds.

### 3. Settlement order protects the first window and cap allocation

`BankDepositXpRepositorySQL.complete` accepts only the earliest `READY` receipt for a guild. A later receipt returns the response-only `WAITING` state while an earlier one remains ready. The sequence comes from currency settlement, not timestamp precision or UUID sorting.

This prevents the first-window loss demonstrated by A `0→100`, B `100→200`, with B's callback arriving first. B cannot seed the first high-water row at 100 and discard A's first unit. Strict order also determines which captured award receives scarce remaining cap allowance.

The existing canonical preparation hold remains: an unresolved `PREPARED`, `BALANCE_APPLIED` or `FAILED_COMPENSATION` player transfer prevents a later new personal/physical deposit from starting. New quoted preparations serialize on the SQL guild fence. A provisional physical credit is therefore not an ordinary service path through which a later player deposit may settle first.

### 4. XP and every terminal decision share the high-water transaction

The consumer's transaction performs the original-window high-water reservation, existing `ExperienceAwardRepositorySQL.awardInTransaction`, cap/progression/audit writes and retained delivery decision together. Receipt insertion/update faults or XP faults leave the receipt `READY` and roll back the associated XP accounting and high-water update.

The existing XP receipt uses the canonical operation ID. A retained terminal delivery result remains authoritative after pruning `experience_transactions`; it is not inferred from the audit table. A still-ready delivery encountering an unexpected existing XP receipt is an integrity error and remains retryable without granting more XP.

The quote computes `floor(awardXp × units × capturedMultiplier)` for the entire award. It does not multiply separately rounded per-unit boosts. Overflow produces a retained invalid decision, not a partial award. Delivery does not reinterpret time, actor activity, policy or unit size from current settings.

### 5. Denied settled growth cannot leak into a later award

The consumer advances original-window high-water for confirmed settled growth while the guild exists, including when XP is denied for AFK, disabled policy, invalid units, cap exhaustion, stale run or chapter closure. The zero-XP terminal result commits in that same transaction and consumes no XP cap allowance.

This resolved an explicit review counterexample. With an existing high-water row, an old-run deposit `0→1000`, a prestige fee of 100 and a later deposit `900→1000`, simply denying the first receipt without consuming its growth would let the later 100-coin deposit claim ten units. Ordered denied-growth consumption prevents that carry-forward.

This is intentionally stricter than the previous immediate AFK early return, which could leave growth available to a later eligible callback. A deleted guild is the exception: the consumer records stale delivery without creating orphan high-water, usage or progression rows. The existing no-window behavior for `UNLIMITED` remains non-awarding.

Captured chapter/prestige mismatches become terminal `STALE_RUN`; an otherwise matching chapter outside its open phase/time becomes terminal `CHAPTER_FROZEN`. Preparation-time closure is also retained. Reopening or changing current policy cannot reactivate a terminal decision.

### 6. SQL locking has no new inverse edge in the reviewed paths

| Path | Reviewed resource order |
| --- | --- |
| New quoted preparation | Lifecycle → guild → reward account → canonical operation → delivery quote |
| Canonical finalization | Existing operation/balance locks as applicable → guild sequence counter → delivery `READY` |
| XP consumer | Lifecycle → guild → reward account → delivery → high-water → usage/progression/audit |
| Prestige | Guild fence before its first replay snapshot → existing account/progression/operation work |
| Deletion | Existing creator/cooldown guard where applicable → guild → existing child/reward-account deletion |

Finalization does not acquire lifecycle/guild/reward-account locks after taking a canonical operation lock. The consumer never locks a canonical operation or sequence counter. Its nonlocking canonical confirmation/fingerprint read occurs after locking the ready receipt, and the identity lookup is performed on a separate autocommit connection. This avoids the inverse operation/guild edge and the MariaDB stale-snapshot ordering problem identified during design review.

The prestige fence is at transaction entry, before the ordinary replay read opens a consistent snapshot. It does not prevent replay of a retained prestige receipt after guild deletion. Both deletion paths acquire the guild before touching reward accounts. SQLite quote/consumer transactions reserve the writer before reads; MariaDB behavior uses `Storage.dialect`.

### 7. Publication is acknowledged after real cache refresh

An awarded delivery retains `cache_pending`. `BankDepositXpDeliveryService` passes an acknowledgement for that exact operation to `ProgressionService.onCommittedBankDepositExperience`.

The Bukkit adapter invokes the acknowledgement inside the actual main-thread runnable, after both progression and guild-level refresh have succeeded. Enqueueing work, returning `Unit`, or swallowing/logging a refresh exception cannot clear the durable request. An acknowledgement SQL fault also leaves the request pending. Updating only the exact receipt avoids acknowledging a newer award that committed after the refresh.

The first successful completion response may carry a level-up value. Replaying an awarded receipt always publishes a null level and refreshes caches without repeating XP or level effects. Cache publication is separate from deposit-event delivery.

### 8. Runtime and bounded retry behavior are explicit

The live `BankServiceBukkit` path delivers by canonical operation ID and has a separate exception boundary for `GuildBankDepositEvent`. An XP failure therefore no longer suppresses that live event. The startup minute timer independently attempts currency recovery, XP delivery and cache retry, so a failure or zero result in currency recovery does not prevent processing already-applied deposits.

`pending(limit)` selects at most one ready head per guild. Independently committed attempt timestamps change even when the subsequent XP transaction rolls back, moving a failing guild behind others in bounded pages. Cache work has its own selection and attempt timestamps. The default batch is 64, with a repository maximum of 128.

The current implementation processes one selected head per guild per drain invocation. A single guild's backlog therefore advances by at most one queued operation per minute through the timer, in addition to successful direct live deliveries. This is a documented bounded catch-up rate, not an unbounded drain. A permanently failing head intentionally holds that guild's later receipts.

## Regression source reviewed

The initial independent contract commit adds 21 shared tests, intended to execute against both SQLite and the existing disposable loopback MariaDB fixture. It uses actual gold services/repositories and JDBC state assertions, with controlled external payment receipts and SQL triggers at real write boundaries.

Reviewed coverage includes personal and physical missed-callback/restart recovery, quote rollback before payment, both finalization/recovery failures, XP/audit and terminal-receipt rollback, reversed callback order with tied timestamps, captured policy/time/pool/boost/unit size, AFK and stale-run carry-forward, disabled/capped/no-growth retention, chapter/deletion guards, physical hold and compensation, route/legacy exclusion, retained audit-prune behavior, and competing repository consumers.

The JDBC scalar helper preserves absent/null distinction and avoids the earlier idb SQLite `Integer`-to-`Long` cast failure. The MariaDB class remains explicitly bound to the existing disposable loopback XP profile; the reviewer did not instantiate it.

The runtime commit adds two existing visibility-fixture tests for scheduled refresh acknowledgement and retry after guild-mirror refresh failure. It adds a migration test retaining a legacy operation without creating a quote and updates existing final-version assertions to schema 47 without removing their schema checks. Existing bank delegation now asserts use of the canonical delivery ID while retaining balance assertions.

The follow-up `6970eecc` adds two shared SQL cases without changing the initial 21 cases or fixture:

- A real XP insertion failure rolls back guild A's accounting while its attempt timestamp survives a repository restart. A bounded page then selects guild B, while A's follower remains behind its own head. XP and cache queues are asserted independently.
- Both actual deposit services reject a guild absent before new preparation. The assertions retain zero personal debit/physical reservation effects and zero canonical operations, external attempts, delivery quotes and sequence rows.

The same follow-up adds two SQL/Bukkit composition tests in a new publication test class. A trigger rejects only the cache acknowledgement after successful refresh; the test asserts one XP receipt, one pair of level notifications, retained cache work, and a later null-level retry with no repeated notifications or deposit event. A real XP audit insertion fault during physical `BankServiceBukkit` delivery leaves the receipt ready while the live deposit event still fires once; subsequent queue recovery refreshes actual progression without recharging currency or replaying that event. The guild repository and unrelated Bukkit dependencies are controlled test doubles, while the progression cache, delivery state and XP accounting use SQL.

This yields 23 shared SQL contracts, intended for 46 executions across SQLite and the opted-in MariaDB profile, plus two composed publication tests. Those are coverage definitions, not reported passing execution totals.

## Coordinator validation checkpoint

The coordinator reported that Java 25 production and `testClasses` compilation passed on the clean integrated candidate `ee55bc8ca6034cfa64fe2e8652d76dc6e22bdf9d`, tree `b296712b760c5424cce232957750c690655c78b4`, on 2026-10-20 from 20:09:11 to 20:10:59 UTC. That phase included the production implementation and initial SQL contracts; it executed no tests.

The coordinator subsequently integrated the reviewed contract follow-up as `4b07dcd4ac93db81900af13edf4579effdea4e2a`, tree `10ec6c2ef6d31069b3822de5e1cdd8faf04fb90c`. Compilation of those additions, native SQL/full regression outcomes, MariaDB execution and `shadowJar` validation were still pending at this document's source acceptance checkpoint. The reviewer did not run or independently observe those native gates; their results must be attached by the coordinator before treating this change as runtime-verified.

## Remaining supported limits

- **Historical operations:** Existing applied or pending operations without captured quotes remain outside this recovery path. No old-record scan, current-policy backfill, compensation or reconciliation of already-missed historical XP is authorized by this implementation.
- **Run boundaries:** Pending XP is explicitly denied across chapter/prestige transitions. Those transitions are not blocked until all XP receipts drain. The retained denial prevents a later-run award; it does not recover the old run's lost opportunity.
- **Currency lifetime:** Guild deletion after a valid external operation was prepared remains an existing broader currency/deletion race. This patch rejects an already-absent guild at new preparation and denies stale XP, but does not invent refunds or claim to fix the full external-transfer lifetime.
- **Deposit events and downstream counters:** Quest and activity listeners still consume a best-effort live event without durable operation receipts. XP recovery never replays that event. Missed events and repeated live-caller notifications remain outside this guarantee.
- **Level effects:** A crash after XP commit but before its first completion response can lose level effects. Cache retries pass null level and do not reconstruct or repeat those effects.
- **Balance semantics:** The underlying period high-water algorithm remains based on canonical balances. Non-deposit routes do not create delivery receipts; this is not a new attribution ledger for every source of bank balance changes.
- **Migration and rollback:** Schema 47 is an additive same-database upgrade. The existing SQLite-to-MariaDB copy utility already omits canonical currency/high-water state and is not made safe by this patch. Mixed old/new writers or rolling back to transient callback-based awarding with retained outstanding receipts are outside the reviewed recovery strategy.
- **Native/live evidence:** Source review and fixture inspection do not replace the coordinator's native build/test gates or deferred live acceptance. No production deployment is part of this review.
