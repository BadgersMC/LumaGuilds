# Deposit quest delivery: implementation and verification plan

Base: fork PR17 `a7bbc02b0af6cc622386f7dfda4d7a994eebc458`. The two uncommitted SQL fixture tests demonstrate lost progress after missed publication and wrong progress after same-week replacement.

## Required behavior

- Capture a durable, immutable identity for each eligible quest definition and the guild run in the existing preparation transaction before external payment; a retry of the same transaction must not replace that identity. Do not infer identity for older transactions.
- Atomically associate captured intent with confirmed applied gold settlement; rejected and ambiguous operations must not deliver quest progress. Recovery consumes only verified provenance and uses an atomic progress/receipt operation.
- A replacement with the same ID but changed definition must be distinguishable. Persist a definition revision or canonical fingerprint covering conditions, target, amount and rewards; do not trust a mutable week ID alone.
- Quest rotation and replacement must be serialized or version-fenced against capture and delivery on both SQLite and MariaDB. Maintain existing guild/chapter/preparation/gold lock ordering; no settlement path may gain a lifecycle/quest lock that creates an inversion.
- Recover bounded batches before quest rotation, defer rotation while eligible deliveries remain or recovery fails, and prevent a poison entry from blocking unrelated guilds. Repeated delivery and reward replay must be idempotent. Define what happens when the original definition was explicitly replaced, the chapter/prestige changed, or quests are disabled; never attribute a receipt to a new definition.
- Legacy receipts without captured identity remain uncredited automatically and require a clearly reported reconciliation path; do not synthesize history or change existing bank balances.

## Verification gates

First retain both known red tests. Add SQL contract tests for prepared/failed/confirmed operations, crash before/after publication and progress commit, identity and content replacement, rollover backlog/failure, restart, quest disabled, chapter/prestige changes, duplicate delivery, recycled gold, caps and multiple quests. Run SQLite and native MariaDB tests, including concurrent definition replacement, deposit preparation, settlement, recovery and rollover with lock timeouts. Verify upgrades from prior schemas, indexes, cleanup/retention and companion contracts. Finish with independent exact-head review and authorized TEST/client acceptance.

The implementation is still undergoing validation. Do not treat it as a deployed or production-ready fix.

## Implementation evidence and remaining gates

The local unmerged implementation captures the definition fingerprint during prepared gold operations, replays canonical applied receipts in bounded batches, records completed recovery attempts, and checks for unresolved receipts before deactivation or rotation. Version 49 creates the additional tables without synthesizing intents for older receipts. Explicitly replaced definitions are treated as ineligible; previous-run and chapter mismatches fail closed. Prepared or otherwise ambiguous payment operations block transition until reconciled. This is an operator-visible tradeoff, not proof that the affected historical reward was delivered.

Focused SQLite tests and a 15-test disposable native MariaDB contract passed on 2026-10-28. The MariaDB run is recorded in operations/guilds-remaining-20261027/pr11-mariadb-xp-9d35b36d8a73/result.json outside this repository. These are local checks against a dirty feature checkout, not hosted CI, an independently reviewed PR, TEST activation, or player acceptance. Concurrency between quest-definition replacement, preparation, payment settlement, recovery, and reset still requires targeted native lock-order validation; an existing local integration test covers concurrent progress/replay only. Legacy receipts without captured identity require investigation rather than inferred rewards.

Additional local verification: the transition fence rejects a deposit before payment and resumes after restart; a native MariaDB contention test races preparation against transition. The 18-test native MariaDB contract passed in operations/guilds-remaining-20261027/pr11-mariadb-xp-2a8896e29fad/result.json. Review must still consider repeated cross-instance contention, operator recovery of ambiguous prepared operations, and player-facing feedback for a temporarily deferred deposit before production acceptance.

Follow-up verification: startup retries a failed reset without preventing plugin enable; a quote captured before a newly started quest week is rejected before payment. Focused SQLite tests passed, and 19 native MariaDB contract tests passed in operations/guilds-remaining-20261027/pr11-mariadb-xp-a9db341b4c8e/result.json. A remaining edge case needs review: a quote captured immediately before quests are disabled may reach preparation after deactivation, when no active week exists. Do not mark the draft release ready until that timing boundary is handled and tested.

## Revision fence follow-up (2026-10-29)

Two tests first reproduced stale preparation after quest deactivation and after a same-week definition edit (both expected a safe retry and instead prepared an operation). Each quote now reads a monotonic database quest revision before its timestamp; preparation locks and checks that revision before journal or external payment. Quest replacement and deactivation increment it in the same transaction as the definition change. A quote captured before a change is rejected, while a fresh quote after deactivation may deposit normally. This does not invent quest progress for historical receipts. The SQLite regression and fixture suites pass locally; the native MariaDB contract passed 21 tests, including both stale-quote cases and a transition contention check, in operations/guilds-remaining-20261027/pr11-mariadb-xp-1e741bad5c2b/result.json. The earlier deactivation blocker above is addressed in this revision; exact-head hosted CI, independent review, wider concurrency checks, and TEST/client acceptance remain.

Hosted CI for prior head 64312a5c had six standalone quest-fixture failures caused by missing bank tables. The affected fixtures now initialize the actual bank schemas and pass locally.

Further refinement: ten consecutive native MariaDB deposit/transition races passed; a failed quote or revision lookup is logged and rejected before any payment journal entry. Migration replay retains an existing quest revision, and failed definition writes roll the revision back. The SQLite payment, migration, and fixture checks passed locally; 22 native MariaDB contract tests passed in operations/guilds-remaining-20261027/pr11-mariadb-xp-e243337e9432/result.json. Hosted full Gradle and wiki checks passed for preceding head 4d2e1171; follow-up exact-head checks and TEST/client acceptance remain separate.
