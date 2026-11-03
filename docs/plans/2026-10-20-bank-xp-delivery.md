# Durable bank deposit XP delivery

Roundtable GUILDS-10 (`01a15adb-f020-7a6f-b341-d23a71776b2c`), with independent GUILDS-11 contracts and GUILDS-12 review. This follows the atomic high-water/XP correction documented in `2026-10-20-bank-xp-atomicity.md`.

## Problem and boundary

The earlier correction made a bank high-water update and an XP award atomic. The currency credit still committed before an in-memory callback attempted XP. A missed callback, process restart, or recovered external payment could leave currency credited without another eligible XP attempt. The callback also generated a fresh XP ID and time, so replaying historical gold records could not safely repair this gap.

This change covers **new personal-account and physical-item bank deposits prepared through the configured GuildGoldService**. The original canonical operation ID identifies both its durable delivery receipt and its XP audit transaction. Admissions, withdrawals, interest, administrative credits, and system credits do not create deposit quotes. Existing operations replay their original result without consulting today's XP quote provider. An existing operation with no quote remains a legacy operation; no quote is inferred or backfilled.

## Captured evidence

`BankDepositXpQuote` captures the original occurrence time, BANK_DEPOSIT policy (pool, award XP, cap, period, enabled state), activity/eligibility disposition, the boost multiplier applicable at that time, and the gold value per XP unit. The unit conversion defaults to 100 and is persisted so a future code/configuration change cannot reinterpret an outstanding deposit. Whole-award multiplication and floor rounding match the existing boost calculation: `floor(awardXp * units * multiplier)`.

The SQL preparation transaction retains the complete currency fingerprint (operation, guild, actor, route, amount, fee, description), the current chapter identity and its original eligibility, and the guild's prestige count. These are captured before `beginExternal` permits an external effect. An already-absent guild fails preparation before a new currency operation is inserted or a provider is called. A new-operation insertion and its quote either both commit or neither does.

## Durable state changes

Schema version **47** adds two tables through both migration runners and idempotent repository initialization:

| Table | Responsibility |
| --- | --- |
| `guild_bank_xp_deliveries` | Immutable quote/fingerprint, captured run, settlement balances/order, retained terminal decision, accepted XP, and cache acknowledgement |
| `guild_bank_xp_delivery_sequences` | Monotonic settlement counter per guild; independent of timestamp precision or UUID ordering |

The preparation status is `PREPARED`. Only true canonical `APPLIED` settlement changes it to `READY`, recording the canonical old/new balances and the next guild sequence in the **same transaction**. The journal must contain confirmed external consumption with purpose `DEPOSIT`. The hooks cover:

- Personal-account `applyInTransaction` reaching `APPLIED` after a confirmed external debit.
- Physical `completeExternal`, after confirmed inventory consumption.
- Both `recoverConfirmedCredit` paths, including a previously provisional balance and a credit recovered from `PREPARED`.

Physical `BALANCE_APPLIED` is provisional and never makes XP deliverable. Rejection/compensation records retain `CANCELLED` when applicable. A rollback while making a credit `READY` leaves the canonical operation in its previous recoverable state; it never authorizes another external charge or a speculative refund.

## Ordered atomic consumption

The consumer locks the guild and the selected receipt. If an earlier `READY` receipt exists for that guild, the later receipt returns `WAITING` without changing high-water, cap usage, progression, or its decision. The response is not stored as a terminal status.

This order matters for both the first high-water seed and scarce cap allowance. For deposits A `0→100` and B `100→200`, consuming B first must not seed away A's first unit. The persisted settlement sequence enforces A before B even if both have identical timestamps or B's UUID sorts first. Canonical preparation continues to hold later player deposits behind an unresolved physical/personal operation; a provisional physical credit cannot be skipped by a later new player deposit.

For a confirmed receipt whose guild still exists, original-window high-water, XP/cap/progression/audit writes, and the terminal delivery decision commit on one connection. Any exception rolls all of those writes back. The consumer never calls a currency provider and never starts another money operation.

The retained decision controls replay even after `experience_transactions` cleanup. An unexpected XP audit duplicate for a still-READY receipt is an integrity error, not invented success. Terminal receipts are not pruned with the XP audit history.

### Denied growth is consumed

Confirmed settled money consumes its original-window high-water growth even when its XP decision is AFK, disabled, invalid, capped, stale, or chapter-frozen. This is intentionally stricter than the old immediate AFK callback, which returned before touching high-water. Without this rule, a later eligible deposit could claim growth from a denied receipt: an existing high-water of 0, denied deposit `0→1000`, withdrawal of 100, then deposit `900→1000` could otherwise earn ten units from a one-unit deposit.

The denial awards no XP and reserves no cap allowance. A removed guild is terminal `STALE_RUN` **without creating orphan high-water, usage, or progression rows**. `UNLIMITED` retains the existing explicit no-bank-window/non-awarding behavior. No-units, disabled, invalid, capped, AFK, stale-run, chapter-frozen, and cancelled decisions are retained and cannot become eligible after a later configuration/activity change.

Captured chapter or prestige mismatch becomes `STALE_RUN`; an otherwise matching chapter that is no longer open becomes `CHAPTER_FROZEN`. Original closure at preparation is also sticky. Outstanding XP is deliberately denied across these transitions rather than credited into a later run. The change does not block prestige/chapter transitions until every XP receipt drains.

## Lock order

MariaDB uses declared `Storage.dialect`; SQLite takes its writer before quote/consumer reads. No helper obtains a second connection to commit part of a business transaction.

| Path | Locks/resources in order |
| --- | --- |
| New deposit preparation | Lifecycle → guild → reward account → canonical operation → quote |
| Canonical APPLIED finalization | Canonical operation/balance as applicable → guild sequence counter → delivery READY |
| XP consumer | Lifecycle → guild → reward account → delivery → high-water → usage/progression/audit |
| Prestige | Guild fence at transaction entry → existing receipt/account/progression/operation work |
| Guild deletion | Existing creator/cooldown guard where applicable → guild → existing child/reward-account deletion |

Finalization never takes lifecycle, guild, or reward-account locks after locking a canonical operation. The consumer never locks the canonical operation or sequence counter. Its nonlocking canonical fingerprint/confirmation read follows the locked READY receipt, with the immutable identity lookup performed on a separate autocommit connection. This avoids both an inverse lock edge and opening a stale MariaDB repeatable-read snapshot before waiting for settlement.

The new early guild fences in prestige and both deletion paths align their existing reward-account access with the consumer. No reward price, prestige requirement, deletion policy, or system-debit policy changes.

## Bounded delivery and cache acknowledgement

`BankDepositXpDeliveryService` attempts the supplied canonical ID after a normal bank deposit, and startup's existing minute timer separately attempts bounded XP and cache queues. These are independent of the currency recovery result and its exception boundary.

Each XP page includes only the earliest READY receipt per guild. A failed head intentionally holds that guild's later receipts. Attempt timestamps commit independently before the XP transaction, including on rollback, so a failing head moves behind other guilds rather than occupying every limited page. The default batch is 64; repository calls accept at most 128. Cache work uses its own bounded queue and attempt ordering. There is no retry loop inside a transaction or currency provider retry in the XP consumer.

An awarded receipt sets `cache_pending`. Publication uses `ProgressionService.onCommittedBankDepositExperience` with an exact-receipt acknowledgement callback. The Bukkit adapter invokes it only inside the actual main-thread runnable, after both progression and guild-level cache refresh succeed. Merely scheduling work, returning Unit, or logging a refresh failure does not clear the durable request. The acknowledgement updates that operation ID only, so it cannot acknowledge a newer award that committed after the refresh.

The first committed completion may publish its resulting level. Replaying an AWARDED receipt always passes a null level and only refreshes caches. If scheduling, refresh, or acknowledgement SQL fails, the retained cache request can retry without awarding again or repeating level effects.

## Event and compatibility limits

- `GuildBankDepositEvent` remains a best-effort notification on the live BankService path, in a separate exception boundary from XP delivery. XP recovery does **not** replay it. The event has no canonical operation ID; current quest/activity listeners increment without durable consumer receipts. Missed events and duplicate notifications from live caller retries are not repaired by this queue.
- Level-up effects are best effort on the first successful completion response. A crash between XP commit and that response may lose those effects; cache replay never repeats them. The cache acknowledgement is not an event-delivery receipt.
- Existing APPLIED deposits lack this quote/run evidence and are not replayed. The earlier atomic helper remains for compatibility/tests; the runtime bank deposit path uses the durable queue exclusively.
- A guild deleted **after** a valid external operation was prepared remains a pre-existing canonical currency-lifetime issue. This change denies stale XP and does not invent refunds, reconstruct a guild, or claim to repair that broader currency/deletion boundary.
- This is an additive same-database upgrade. The existing SQLite→MariaDB migration utility does not copy all canonical gold journals/high-water today; adding only the new tables would not make that migration safe. Cross-engine money/journal migration remains separate work. Mixed old/new writers and rollback to a binary that resumes transient bank awarding are not supported recovery strategies for retained outstanding receipts.

## Validation ownership

The independent contracts worker supplies real SQLite/MariaDB repository tests for normal/recovered credits, provisional/reversed credits, finalization and XP SQL faults, ordered settlement, repeated/concurrent consumers, captured policy/time/boost/conversion, denied-growth exclusion, run/deletion guards, and retained receipt/cache behavior after audit pruning. Existing migration and BankService delegation fixtures are updated without dropping assertions. `DepositXpVisibilityTest` adds actual scheduled-cache acknowledgement and refresh-failure retry cases while retaining the earlier SQL RED regression.

Native Java/Gradle/MariaDB execution, integration and publication are root-coordinator gates. The implementation worker does not run builds, mutate shared TEST/production, merge, or deploy.
