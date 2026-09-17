# Chapter 2 reward implementation — SPEAR

Source: REQ-050/054/056/093 and the operator-approved
[LG-1202 catalog](2026-09-17-lg-1202-reward-catalog-proposal.md).

The DOC catalog and prestige design are complete. Steps 1–4 below are implemented
and verified on the operator's fork. Step 5 now has gated read-model consumers,
read-only reward views and placeholders; purchase actions and migration readiness
remain open. LG-1204 scheduled increased-XP periods are implemented separately.
See [atomic purchase verification](2026-09-17-atomic-reward-verification.md).
Do not enable Chapter 2
or migrate existing guilds merely by changing the legacy reward YAML.

1. LG-1210 (TDD): validated typed catalog containing all 100 levels and 30 purchases.
   Verify the committed table as an independent fixture, checkpoint cadence, price
   totals, effects, identity uniqueness, and invalid input before implementation.
2. LG-1211 (TDD): pure entitlement resolution and purchase/retention eligibility.
   Prove unlocked is not owned, unique-ID additive bank capacity, absolute multiplier
   minima, permanent homes, retained selection constraints, invalid-state rejection,
   and post-reset resolution. No implicit gold mutation in this layer.
3. LG-1212 (TDD): durable ownership snapshots, optimistic concurrency, explicit
   missing/failed reads, restart and rollback tests. This storage foundation alone
   is not a purchase operation. Verify both SQLite and MariaDB contracts.
4. LG-1213 (TDD): persist ownership and canonical gold payment atomically with journal
   replay, concurrent purchase and failure tests. Never introduce a second balance
   authority or debit-then-grant without recovery.
5. LG-1214 (TDD): integrate one read model into config, gold policy, home checks, fixed membership,
   progression menus and placeholders. Gate rollout until migration is ready.
6. Finish paid home activation, migration, seasonal lifecycle and disabled-by-default
   prestige using their existing requirements and separate SPEAR test cycles.

For each completed slice, record failing-test and passing-test evidence, run the
Konsist layer checks and full test/shadowJar gate where dependencies permit, and
record limits accurately. Existing production data and deployments are out of scope.
