# Guild founding: digital Vault payment hotfix (#242 base)

## Scope and invariants

- Founding charges the player's **personal digital Vault economy balance**, not raw-gold inventory. Home activation and physical guild-bank/war-banner flows are unchanged.
- The #242 SQL guild/progression/default-rank/owner-admission transaction remains the source of truth for whether a guild was founded.
- Vault's Economy API has no provider-independent idempotency key. An ambiguous withdrawal, refund, SQL acknowledgement, or guild creation cannot be retried automatically.
- The active per-player guard in `guild_creation_vault_guards` is created atomically with a `guild_creation_vault_payments` receipt before any external withdrawal. This blocks concurrent commands and persists over restarts.
- State transitions record `DEBITING` before invoking `withdrawPlayer` and `CREDITING` before invoking `depositPlayer`. Any interrupted or unknown state stays guarded until an operator reconciles it.
- Any negative or thrown response **after a withdrawal was attempted** stays guarded, even if the immediate balance appears unchanged. Only pre-call unavailable/insufficient checks can fail without a payment intent.
- Completed `COMMITTED` and `RESTORED` receipts remain in the journal after the active guard is removed in the same SQL transaction. Receipt `created_at` and `updated_at` are persisted as UTC Unix epoch milliseconds.

## Operator reconciliation (never blind-unblock)

When `/guild create` prints the transaction reference, do not tell the player to retry while the journal guard exists. Collect the exact UUID, player UUID, time, configured cost, and provider name.

Read-only queries (run against the appropriate LumaGuilds SQLite/MariaDB database):

```sql
SELECT transaction_id, player_id, amount, phase, created_at, updated_at
FROM guild_creation_vault_payments
WHERE transaction_id = ?;

SELECT player_id, transaction_id
FROM guild_creation_vault_guards
WHERE player_id = ?;
```

Check the actual Vault economy provider's independent transaction history or authoritative account records, **not just the current balance** (which other gameplay can change). Correlate any withdrawal and credit by player, amount, and time. Separately inspect guild existence, creator attribution, progression, owner rank and membership in LumaGuilds SQL.

| Phase | Meaning and safe action |
| --- | --- |
| `CLAIMED` | No call should have begun, but a missing/crashed worker still requires checking the receipt and timing. |
| `DEBITING` | Withdrawal was about to run or ran; outcome cannot be inferred from the phase. Verify provider ledger before any compensation. |
| `DEBITED` | Adapter observed a successful withdrawal; creation may be persisted, failed, or ambiguous. Check guild/owner SQL first. |
| `CREDITING` | Refund may already have occurred; **never automatically issue a second credit**. Reconcile provider receipt. |
| `COMMITTED` | Creation reported and debit finalized. Active guard should not remain. |
| `RESTORED` | Confirmed refund after proven creation failure. Active guard should not remain. |

If provider or database evidence is missing or inconsistent, keep the payment held and escalate to an administrator with financial audit access. **Do not delete a guard, rerun a payment, or fabricate a successful rollback.** Once the real outcome is proven, an authorized maintainer can use a separately reviewed, audited reconciliation operation that records evidence and atomically transitions the receipt to a terminal state while releasing the matching player guard. This hotfix intentionally does not offer a blind staff unlock/refund command.

## Verification and release gates

- Focus tests exercise SQL receipts, pending guard, durable restart, ambiguous debit/refund, rejected-but-mutated Vault responses, concurrency, sufficient/insufficient/provider missing, rollback, and #242 owner bootstrap.
- Use disposable SQLite and MariaDB test profiles. Never run a payment test against TEST/production balances.
- This is an isolated review candidate. Human approval, independent QA/Security review and a separate deployment plan are required before any merge or rollout.
