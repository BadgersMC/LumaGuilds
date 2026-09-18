# Chapter 2 reward foundation — verification

## Scope and setup

REQ-050/054/056/093; approved LG-1202 catalog. Changes provide the typed catalog,
pure entitlement/ownership invariants, and a SQL ownership repository foundation.
The adapter is deliberately not registered at startup. No player purchase action,
gold debit, migration, prestige activation or production database mutation is added.

Local verification uses JDK 21.0.8 and the repository's Gradle 8.5 wrapper.
RoseChat-RC-2.jar came from the operator's existing local download. CombatLogX API
was built from CI's pinned source commit
`4812e85af1264ebb27da481b9d9cbf8de0956e53` with `:api:jar`.
The baseline initially could not compile because that API jar was missing;
building it resolved the setup failure. Local dependency binaries are not committed.

## SPEAR evidence

1. Spec: the approved catalog and implementation plan define cadence, purchase
   eligibility, immutable identity, home permanence and retained-family behavior.
2. Prove: `test --tests '*RewardCatalogTest' --tests '*RewardEntitlementsTest'`
   failed compilation on the missing reward types before implementation. The
   persistence test likewise failed on the missing repository. Subsequent integrity
   tests produced two actual assertion failures: a 65-character ID was accepted,
   and orphaned ownership was reported Missing rather than Failed.
3. Engine: implement typed definitions/resolution, bounded IDs and snapshot copies;
   add new SQL account/ownership tables, compare-and-set versioning and transactional
   writes with rollback. Detect corruption and preserve permanent state.
4. Arch: domain owns reward and ownership-transition invariants; the application
   port exposes typed Found/Missing/Failed and Saved/Conflict/Failed outcomes;
   the infrastructure adapter owns JDBC and storage dialect. Konsist checks pass.
5. Refine: all 24 reward tests plus the five LayerRulesTest checks pass with the
   standard repository command `test --tests '*Reward*Test' --tests '*LayerRulesTest'`.
   Tests verify every row against the committed approved table, totals, ownership,
   level gates, multiplier minima, family exclusion, immutable snapshots, overflow,
   eight SQLite storage cases including restart, concurrent stale writers, injected
   SQL failure rollback, corrupt/orphan reads, and permanent-asset preservation.

Final standard repository command: `./gradlew test shadowJar --console=plain`.
Result: **BUILD SUCCESSFUL**, 946 tests, zero failures, errors or skips;
`build/libs/LumaGuilds-2.1.0.jar` built successfully. Counts were read from all
JUnit XML reports after the final run, not from a historical baseline.

## Gates at the foundation checkpoint

The MariaDB and atomic purchase gates below were subsequently completed; see
[atomic purchase verification](2026-09-17-atomic-reward-verification.md). The
946-test result above describes the earlier foundation commit.

- Execute the SQL contract against MariaDB; SQLite results are not MariaDB proof.
- Implement one atomic gold payment/ownership transaction with durable retry
  identity before exposing purchase actions. Calling `save` after a separate gold
  debit would violate the approved contract.
- Integrate configuration, legacy consumers, menus and placeholders; verify migration
  and live Java/Bedrock behavior before enabling the Chapter 2 runtime.
- Implement the remaining home activation, migration/chapter and prestige tasks;
  prestige remains disabled by default.
