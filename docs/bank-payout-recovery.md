# Personal-account withdrawal recovery (REQ-009)

The guild bank pays the player's personal Vault Economy account, not inventory items.
The bank stores a `PAYOUT_PENDING` row in `bank_audit` before debiting the guild and
requires a successful database flush before calling the economy provider.
`PAYOUT_COMPLETED` confirms both monetary steps; `PAYOUT_REFUNDED` confirms either
that no debit occurred or that the entire debit, including fees, was restored and saved.

A pending row without either terminal marker blocks further withdrawals for that
guild, including after restart. Java and Bedrock menus show the transaction ID and
tell the player not to retry. Deposits remain available. These three journal actions
are intentionally excluded from ordinary audit retention; do not prune them manually.

## Administrator reconciliation

1. Pause affected bank activity and take a database backup. Locate the reported
   transaction ID in `bank_audit` and the server log. The pending row records the
   player, guild, requested amount, fee, and pre-payment personal balance.
2. Check the economy provider's transaction history and the guild vault transaction
   history/database balance. Current personal balance alone is not proof: the player
   may have received or spent other funds. A pending row can mean interruption before
   debit, after debit, after payout, or during refund/terminal-state persistence.
3. Reconcile both accounts exactly once using that evidence. If payment completed,
   do not refund or pay it again. If no payment occurred, confirm whether the guild
   debit was saved and whether a refund has already happened before restoring funds.
   If the outcome cannot be established, leave the withdrawal blocked.
4. Only after reconciliation, record the matching terminal `bank_audit` action
   (`PAYOUT_COMPLETED` or `PAYOUT_REFUNDED`) with the original transaction ID, guild ID,
   an appropriate administrator actor ID, timestamp, and an explanation of the evidence
   and any correction. This requires controlled database maintenance; there is no new
   automatic replay or recovery command in this patch. Do not simply delete the pending row.

The local database and an arbitrary Vault provider cannot share an atomic transaction.
The journal makes interruptions discoverable and blocks blind retries; it does not
claim automatic exactly-once recovery across those systems.
