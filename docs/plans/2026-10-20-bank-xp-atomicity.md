# Bank growth and XP transaction correction

## Source and ownership

Roundtable **GUILDS-07**, `01a15a91-550e-766a-b0ed-1f109c6cf8b2`, continues
reviewed PR #240 at `243370dd815845b06022ed15e12f924fa3cc79ef`, tree
`01a59fe7bfc9ae5b0c70addcee9cd1037c9b1a33`. The executing worker is
`/root/guilds_bank_recovery`; GUILDS-09 independently reviews the correction.
The coordinator owns native verification, integration, publication and shared
resource operations. This work includes no deployment or live bank operation.

## Reproduced failure

Previously, `ChapterTwoGuildAwardService.awardBankGrowth` committed
`BankProgressionRepository.reserveNetNewUnits` before asking
`PermanentExperienceService` to commit the XP award. A receipt insertion failure
rolled progression and cap usage back, but left high-water advanced. Repeating
the same opening/current balance snapshot therefore returned no units and could
not recover that XP.

Test-only commit `09ab68bf1949a6196c7d32d49b11f3afd39792f0`, tree
`fbd3be6993e1017215b2c4f8d8e29cc42048653c`, adds
`DepositXpVisibilityTest.bankAwardRetryAfterSqlFailurePreservesGrowth`. It uses
the real SQLite repositories and real cached progression adapter, injects an
audit insertion failure, verifies no XP/cap/receipt/cache change, removes the
failure and retries the same snapshot.

The coordinator's Java 25 run on October 20, 18:31:32–18:32:53 UTC executed one
case with one expected assertion failure at the retry result and zero skips.
This is evidence of the original defect, not green validation of the repair.

## Corrected transaction

`PermanentExperienceService.prepareForUnits` captures eligibility and the
configured boost before SQL begins. Its returned calculation has no platform,
configuration or persistence calls: it combines the locked unit count with the
captured policy/boost and returns a ready award or an explicit rejection.

`BankProgressionRepositorySQL.awardNetNewUnits` then performs one transaction:

1. Lock the live guild. MariaDB uses `SELECT ... FOR UPDATE`; SQLite takes the
   writer with a guild update before any read and rejects an absent guild.
2. Seed and lock the current high-water row, calculate newly crossed units, and
   advance the balance according to the existing rule.
3. If no units were crossed, return the existing null outcome without invoking
   the prepared calculation.
4. Validate a ready award's guild, source, policy eligibility, period, exact
   locked units and positive XP. Invoke the existing
   `ExperienceAwardRepositorySQL.awardInTransaction` on that same connection.
5. Commit high-water, cap usage, progression, guild level and receipt together.
   An exception rolls the entire transaction back through the existing
   `committingTransaction` helper.
6. Publish committed progression only after the repository has returned.

There is no nested connection or nested XP commit. The SQL dialect comes from
`Storage.dialect`. The existing dependency binding injects the same concrete XP
repository already used elsewhere in the plugin. The reserve-only method remains
for compatibility, but the production bank award path no longer calls it.

## Preserved policy behavior

| Outcome | High-water and XP behavior |
| --- | --- |
| Actor blocked by the initial Chapter Two activity check | Return before a high-water row is reserved. |
| No newly crossed units | Preserve the existing null outcome; no XP decision or publication. |
| Disabled source or another prepared rejection | Commit high-water as before; do not write XP or publish. |
| Remaining allowance smaller than the calculated award | Consume growth and award only the remaining cap allowance. |
| Exhausted allowance | Commit high-water as before; no additional XP receipt. |
| SQL failure or invalid prepared award | Roll back high-water and all XP writes together. |
| Successful same-period repeat of an already awarded snapshot | No newly crossed units; no additional XP or receipt. |

The full eligible unit amount is multiplied and boosted before rounding and cap
clipping. A fractional boost is not rounded once per unit. Reward amounts, cap
configuration, schemas and external GuildShopXpApi semantics are unchanged.

## Defined regression coverage

The unchanged failing regression is retained. Nine common real SQL scenarios
are defined in `BankGrowthAwardContract` and inherited by both
`BankGrowthAwardSqliteTest` and `BankGrowthAwardMariaDbTest`:

- First-period seed rollback, retry and successful repeat.
- Existing high-water restoration after a later receipt failure.
- Two competing repository instances using the same balance snapshot.
- Withdrawal and redeposit without repeated XP.
- Cap clipping and exhausted-allowance high-water disposition.
- Disabled-policy high-water disposition.
- No units without evaluating an award decision.
- Deleted guild rejection without creating progression, cap or high-water rows.
- Rejection and rollback of mismatched prepared identity, units, source, period,
  eligibility, policy or XP amount.

Six service tests also exercise captured eligibility/boost inputs, complete
award rounding, preparation without XP writes, source rejection and overflow.
They preserve ordinary award evaluation order and arithmetic-error handling:
multiplication overflow does not resolve a boost, and an arithmetic exception
from the ordinary boost provider remains an invalid-units rejection.
An arithmetic exception while capturing a bank boost is likewise retained as a
prepared invalid-units rejection, preserving its high-water disposition.
Existing bank service, visibility and source-policy tests remain present.

Native MariaDB uses the existing `GUILD_SHOP_XP_TEST_MARIA_PORT` profile, fixed
loopback address, dedicated `lg_shop_xp_test` schema and disposable fixture
credentials. An unconfigured optional profile skips; it is not native evidence.
The coordinator records actual discovery, pass/skip counts and exact tested
candidate hashes in the PR and Roundtable before handoff.

## Remaining recovery boundaries

This correction does not create a durable queue or make currency and XP one
transaction. In particular:

- A crash or missed callback after currency commits can still omit the XP
  attempt. A failed first-window attempt now rolls the seed back; if only a
  later, different deposit arrives, its newer opening balance can seed past
  that earlier growth.
- `GuildGoldService.reconcilePending` recovers confirmed credits without
  invoking `BankServiceBukkit.postDepositApplied`. The correction does not add
  notification or XP replay to that reconciliation path.
- Existing physical API requests can return an APPLIED currency receipt and
  re-enter the post-deposit callback with the stored old/new balances. Ordinary
  GUI clicks create new transaction IDs and do not automatically retry an XP
  notification failure. Existing preflight authorization/capacity checks still
  apply to any API retry.
- The bank award callback does not carry the original canonical transaction ID,
  occurrence time, chapter or prestige identity. Replaying an old snapshot in a
  later policy period is not a proven safe recovery operation. No startup scan,
  automatic retry, old-journal iteration or compensating XP is introduced.
- An XP failure still prevents the subsequent bank-deposit event in the current
  `postDepositApplied` block. A missed cache publication after a successful
  commit is also separate: a zero-unit bank repeat does not publish again.
- The new guild lock serializes this transaction with ordinary guild writers;
  it does not establish the provenance of a delayed snapshot across prestige.

Complete durable recovery needs an explicit settlement/outbox design with
original operation, actor, time and run identity, plus policy/cap and replay
rules. A timeout alone is not sufficient evidence to award or compensate XP.
