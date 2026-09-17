# Chapter 2 atomic reward purchase verification — 2026-09-17

Scope: LG-1212 and LG-1213, following the approved LG-1202 catalog and
[purchase specification](2026-09-17-atomic-reward-purchases.md). No live plugin
wiring, player action, migration, deployment or production data change is included.

## SPEAR evidence

- Spec: define immutable request identity, terminal rejection, same-ID retry,
  authorization and atomic canonical payment plus ownership before implementation.
- Probe: new purchase contracts first failed compilation on missing request,
  repository and service API types. Implement the minimal transaction boundary,
  then verify success and rejection behavior against real SQL adapters.
- Engine: extract the existing canonical gold mutation and ownership save bodies
  into connection-scoped operations; the purchase adapter owns the outer commit.
  Any ownership or receipt write failure rolls back the debit and journal too.
- Arch: domain request/results and entitlement rules remain independent of SQL;
  the application service exposes the entry point and injected authorization;
  infrastructure owns JDBC locks, durable receipts and storage dialects.
- Refine: thirteen purchase contracts cover restart/replay, home permanence,
  fingerprint conflicts, locked/stale/duplicate/dominated offers, authorization,
  freezes, pending transfers, quote price, insufficient funds, missing state,
  concurrent requests, injected ownership/receipt failure and journal integrity.
  The same eight ownership and thirteen purchase contracts run on both databases.

## Final verification

JDK 21, Gradle 8.5:

```text
gradlew.bat test shadowJar mariaDbRewardTest -PmariaDbTestPort=33371 --console=plain
BUILD SUCCESSFUL in 2m 3s
test: 959 tests, 0 failures, 0 errors, 0 skipped
mariaDbRewardTest: 21 tests, 0 failures, 0 errors, 0 skipped
```

Counts were read from the final JUnit XML reports. The full suite includes the
existing gold service/repository and architecture tests. The Shadow artifact is
`build/libs/LumaGuilds-2.1.0.jar`.

MariaDB 11.4.5 ran as a disposable local process bound to `127.0.0.1:33371`,
using the official Windows ZIP verified against its published SHA-256:
`b7c11d38657f16b837e68199d73670510aadb78f42dfa5d5fdea31a7aab342e3`.
Each test creates and drops only its own fresh `lg_reward_test_<uuid>` schema.
The task requires an explicit nonstandard port and rejects port 3306. Test-only
credentials may be supplied using `LG_TEST_MARIADB_USER` and
`LG_TEST_MARIADB_PASSWORD`; the default is root with an empty password for this
disposable instance. Ordinary `test` continues to use temporary SQLite databases.

## Remaining Chapter 2 work

Subsequent read-model and XP boost work is recorded in
[integration verification](2026-09-17-chapter2-integration-verification.md).
The list below describes the atomic purchase checkpoint.

LG-1214 must wire the validated read model and purchase adapter into configuration,
gold policy, home/member checks, menus and placeholders with migration readiness.
Paid home activation, actual prestige, migration and seasonal lifecycle retain
their separate Chapter 2 tasks. The purchase entry point is unavailable by default;
these tests do not establish live Paper/client behavior or rollout readiness.
