# Vault inventory permission correction

## Spec

REQ-140 covers the existing ACCESS_VAULT, DEPOSIT_TO_VAULT and WITHDRAW_FROM_VAULT enum semantics. Canonical main a15b244 grants ordinary slot movements without operation permission and returns before processing player-inventory shift transfers. Existing open views are not reauthorized. This is source evidence, not confirmation of the historical live member-access symptom.

Expected behavior: preserve view-only access, enforce transfer direction and both directions for swaps; recheck current access on opens/clicks/drags; retain gold-button operation checks and explicit operator override; respect prior cancellation and synchronize permitted player-to-vault transfers after the click.

## Prove / engine / arch / refine

Observed red: 12 listener cases on unchanged canonical main, 10 failures. The expanded focused run passed 75 tests across inventory authorization, gold withdrawal and locale contracts. The full Java 25 offline `test shadowJar` run passed 1,564 tests with zero failures/errors and four existing external skips. A full-suite fixture failure exposed leftover global Koin state; the new fixture now stops it before each case as well as after each case.

The listener maps Bukkit transfer directions to existing domain permissions, rechecks access on open/click/drag, closes only the still-revoked original view, preserves player-only inventory use and gold-button checks, and synchronizes allowed shift transfers. No economy policy, schema, assets or production state changes are in scope. Explicit admin override remains delegated to MemberService.

No project-local EARS/state helpers exist; requirement/task/evidence records are maintained manually. Adapter logic stays outside the domain layer. GitHub PR #217 and manual review are separate gates. Codacy findings prompted smaller helpers and split operation/session fixtures; final focused test/shadowJar passes the same 75 cases and the signature-only refinement compiles. Necessary compile/regression checks passed; in-game acceptance is deferred at user request. The generated JAR is an unmerged local test artifact, not a production release.
