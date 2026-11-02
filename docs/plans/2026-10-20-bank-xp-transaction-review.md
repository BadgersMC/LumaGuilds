# Bank growth XP transaction review

## Review ownership and source

Roundtable **GUILDS-09**, `01a15a91-5553-7e68-8195-a3762192dc0a`, is the
independent review of **GUILDS-07**. The native reviewer is
`/root/guilds_bank_review`; the implementation worker is
`/root/guilds_bank_recovery`. The coordinator owns native verification,
integration, publication and all remote or shared-resource operations.

Review baseline:

- Commit: `243370dd815845b06022ed15e12f924fa3cc79ef`.
- Tree: `01a59fe7bfc9ae5b0c70addcee9cd1037c9b1a33`.
- Existing review: [LumaGuilds PR #240](https://github.com/BadgersMC/LumaGuilds/pull/240).
- Test-only regression: `09ab68bf1949a6196c7d32d49b11f3afd39792f0`.
- Final implementation reviewed: `6efe3704112ca99da6a2cc524308f4da1deb4302`.
- Final implementation tree: `8513b2f936c6a88ca78f8b68aab5362fa5183357`.

The reviewer checked the exact baseline and clean worktree, inspected production
source, the complete correction and the added tests, and ran no build, database
or live-player operation. The final implementation worktree was also clean at
the reviewed commit/tree. The coordinator reproduced the failing regression on Java 25 at the
exact test-only commit on October 20, 18:31:32–18:32:53 UTC: one executed test,
one expected assertion failure, zero skips.

**Source-review disposition: no blocking defect remains in the narrow
high-water/XP transaction correction.** The corrected native run and final
integration checks remain coordinator gates; this disposition is not a claim
that those tests have already passed or that durable currency/XP recovery is
complete.

## Confirmed failure boundary

`ChapterTwoGuildAwardService.awardBankGrowth` first calls
`BankProgressionRepository.reserveNetNewUnits` and then calls
`PermanentExperienceService.award`. At the baseline, the SQL implementation of
each operation obtains and commits its own connection. An exception while saving
XP can therefore roll back cap usage, progression and the XP receipt after the
high-water allowance has already committed.

The regression uses the real SQL award repository and cached progression adapter.
A SQLite `BEFORE INSERT` trigger rejects the XP receipt after the award transaction
has attempted its cap and progression writes. It requires all XP state to remain
unchanged and then retries the same guild, actor, opening balance, resulting
balance and occurrence time. The retry must award exactly once. On the reviewed
baseline, the committed high-water row instead reports zero remaining units.

This establishes a precise source-level defect and a reproduced failing
regression. The coordinator retains the actual failing run and will separately
record the corrected run.

## Required correction properties

| Property | Review requirement |
| --- | --- |
| Transaction boundary | The high-water change, cap usage, progression, guild-level mirror and XP receipt use one caller-owned connection and commit together. |
| Failed SQL | Roll back the entire group, including first-period seed creation and updates to an existing seed. A later supported retry with the same original snapshot can attempt the same growth. |
| Concurrency | Obtain the database writer/row locks before reading a high-water snapshot; concurrent overlapping growth calls cannot both award the same units. |
| Policy | Preserve the intended treatment of AFK exclusion, disabled source, invalid or overflowing amounts, cap exhaustion, cap clipping, boost and period bounds. Non-awarding policy outcomes must not accidentally become retryable awards. |
| Anti-farming | Withdrawals and redeposits below the period's high-water balance cannot produce new XP. |
| Publication | Publish progression and level effects only after the containing transaction returns successfully. No platform or currency effect belongs inside the SQL transaction. |
| Deletion | A removed guild must not be recreated indirectly through progression or cap rows. |
| Compatibility | Use the storage's declared SQL dialect. Do not rely on a class-name guess when choosing MariaDB locking or seed syntax. |

The existing `ExperienceAwardRepositorySQL.awardInTransaction` is a suitable
building block when the caller owns the connection and locking. Calling the
ordinary `PermanentExperienceService.award` through a callback that opens a
second connection would leave the original atomicity defect and can also block
the SQLite writer.

## Final implementation assessment

The final implementation satisfies those transaction requirements:

- `ChapterTwoGuildAwardService` now calls `awardNetNewUnits`; it does not reserve
  allowance and then call the ordinary independent XP transaction.
- `PermanentExperienceService.prepareForUnits` captures source/actor eligibility
  and the configured boost before SQL. The returned calculation only combines
  those inputs with the locked unit count and creates a ready or rejected result.
- `BankProgressionRepositorySQL` uses `Storage.dialect`, locks the guild before
  reading high-water state, and uses the existing `committingTransaction` helper.
  That same connection reaches `awardInTransaction`. There is no nested XP commit.
- Ready awards must match the locked guild, exact reserved units, bank request
  and policy source, cap window, enabled policy and eligible request, and must
  carry positive XP. A mismatch throws inside the containing transaction and
  rolls the high-water write back. An absent guild is rejected before a seed or
  XP write.
- A prepared rejection or `NoAllowance` retains the previous high-water
  consumption behavior. Disabled policies explicitly support `awardXp = 0`
  without resolving a boost or attempting an XP write. Zero net-new units returns
  null without invoking the prepared calculation.
- The concrete XP repository is a required constructor dependency and is wired
  through the existing Koin binding. There is no fallback to separate commits.
  The reserve-only method remains, but is not the production bank award path.
- Cache/level publication occurs only after the bank repository returns from
  its completed transaction. No currency, player, event or configuration lookup
  runs through the prepared calculation inside SQL.

Review of the initial implementation also identified an evaluation-order issue
in the shared ordinary XP helper: a default argument resolved the boost before
`multiplyExact` and outside the arithmetic exception handler. Follow-up
`a8258095644d3018e0b5484a24b203efa048e123` restores the lazy supplier call inside
the existing handler, after multiplication. Final follow-up
`6efe3704112ca99da6a2cc524308f4da1deb4302` also preserves a bank boost-provider
`ArithmeticException` as a prepared invalid-units rejection. Other provider
exceptions propagate before SQL begins. Source/actor rejection short-circuits
boost lookup.

The reviewer checked nine common SQL contracts inherited by both SQLite and
MariaDB test classes, the real SQL/cache failure regression, and six new service
tests. They cover first/existing high-water rollback, repeated and concurrent
snapshots, anti-farming, disabled/capped outcomes, invalid prepared inputs,
captured eligibility, whole-award boost rounding, and ordinary/prepared arithmetic
handling. Existing test methods remain; their fixture construction was adapted
for the required collaborator. The SQL cases use real repositories and trigger
failures rather than a mock transaction that merely assumes atomicity.

The native bank class uses the already-forwarded
`GUILD_SHOP_XP_TEST_MARIA_PORT`, fixed loopback host and the existing dedicated
disposable XP schema. Its nine cases must actually execute in the coordinator's
native profile. The complete source delta passes `git diff --check`; no test or
build was run by this reviewer.

## Boundaries that remain separate

### Currency settlement and durable delivery

`BankServiceBukkit` invokes `postDepositApplied` after the canonical physical or
personal currency service returns `Applied`. That helper catches notification
failures and preserves the successful currency result. It has no durable XP
delivery marker or replay queue.

`GuildGoldService.reconcilePending` recovers confirmed currency credits directly
through the gold repository. It does not invoke the bank adapter's post-deposit
callback. Combining the high-water and XP writes does not make currency credit
and XP one transaction, nor does it guarantee recovery after a process exits
before the callback.

A first-period seed also illustrates the limit of a rollback-only correction:
after that seed rolls back, a later, distinct deposit can seed its newer opening
balance. It cannot reconstruct the earlier missed deposit without retained
evidence. The correction must be described as preserving a same-snapshot retry,
not automatically catching up every missed award.

### Original identity, time and progression state

The baseline bank adapter logs the canonical currency transaction ID but does not
pass it to the XP request. Bank XP uses a newly generated receipt ID and the
callback's current time. Its high-water row is scoped to a cap period; the generic
bank award path does not retain the shop system's original chapter or prestige
snapshot.

An automatic replay of old currency rows must therefore not be added by merely
calling the existing bank callback again. A replay in a later period can create
a fresh high-water row from the old opening/resulting balance snapshot. A complete
durable design needs the original operation identity, timestamp, eligibility and
progression-run disposition, plus a terminal marker retained for the intended
retry lifetime. No live or historical compensating award follows from this review.

### Deposit events and cache refresh

The baseline `postDepositApplied` runs XP award and `GuildBankDepositEvent`
publication in the same `runCatching` block. An XP exception prevents that event
from being fired. The event feeds deposit quest progress and the activity
leaderboard but carries no currency operation ID. Repeated callbacks therefore
are not a proof of exactly-once event delivery.

Likewise, a successful high-water/XP commit followed by missed cache publication
is a different failure from an XP SQL rollback. A replay that sees zero net-new
units has no retained bank receipt from which to reconstruct a publication-only
result. The existing shop receipt cache repair must not be assumed to cover this
bank path.

These are follow-up design boundaries. They do not justify refunding an uncertain
currency transfer, inventing a receipt, repeating a deposit, or compensating XP
without its authoritative evidence.

## Verification handoff

Minimum useful verification for the narrow correction is:

1. Reproduce the test-only receipt-trigger failure on the original implementation.
2. Pass that same test on the corrected implementation, including zero publication
   on failure, exactly one eventual XP receipt and no additional award on repeat.
3. Cover rollback of an existing high-water row as well as a first-period seed.
4. Exercise overlapping concurrent growth calls and withdrawal/redeposit behavior.
5. Check non-awarding policy outcomes and clipped awards without changing caps or
   multiplier semantics.
6. Execute the MariaDB path with a real isolated database, including rollback and
   concurrency. A skipped optional profile is not evidence for MariaDB behavior.
7. Review the final implementation commit and integration diff; retain explicit
   separation between source/SQL verification and shared TEST/player acceptance.

The coordinator records executed results and exact artifacts. Production, shared
TEST deployment, live currency or inventory mutations, and canonical merge are
outside this review.
