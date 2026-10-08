# Retired AxKoth integration

## Spec (REQ-147)

The server no longer uses AxKoth. LumaGuilds shall stop registering its AxKoth team hook and shall remove the AxKoth plugin soft dependency, API compile/test coordinates and dedicated artifact repository. Guild wars, rewards, standings and persisted guild data remain unchanged. Provider-neutral notification APIs and existing configuration remain compatible. Production is untouched.

## SPEAR

- Spec: start from fetched canonical main e11fb16, in an isolated checkout. Existing stack #219 remains separate and will receive this cleanup without restoring the proprietary assets removed by current main.
- Prove: source contains a mandatory AxKothAPI compile/test dependency and startup registration. The existing #219 hosted build stops on six partial API transfers before Gradle. This is dependency/setup evidence, not a historical behavioral red/green claim.
- Engine: remove the retired platform adapter and dependency wiring, including obsolete preparation steps wherever they exist in the reviewed stack.
- Arch: no guild persistence or war/reward service behavior changes. Keep generic capture notifications/configuration compatible; they are not AxKoth API links.
- Refine: clean Java 25 offline test/shadowJar passes 1,538 cases, zero failures/errors and four existing environment skips. Compile and test-runtime Gradle dependency reports contain no AxKoth, axapi or Artillex dependency. Existing architecture, locale, schema and guild lifecycle tests execute. Exact-head GitHub review/check inspection remains a separate gate. Project-local EARS/state helpers and docs/verification.md are absent; this manual record applies. No new tests that merely repeat deleted implementation text.

Canonical merge/release and any monorepo pin update remain review gates. No production upload, activation or restart is authorized by this change.

## Reviewed stack follow-up

The onboarding branch incorporates canonical main e11fb16 without restoring proprietary resourcepack assets. Its three obsolete API preparation steps, composite action and artifact digest manifest are removed; REQ-142 is superseded. EnthusiaStaff and all other active companion integrations are preserved. Clean Java 25 offline test/shadowJar on the combined stack passes 1741 cases, 0 failures, 0 errors and 15 existing environment skips, with the actual Market artifact contract configured. Compile and test runtime dependency reports contain no AxKoth, axapi or Artillex. No persistence change requires a new native database rehearsal; earlier onboarding MariaDB evidence remains separately recorded. Hosted checks, canonical merge and client acceptance are separate gates.

The remote history was sanitized during delivery. Reviewed changes were applied to rewritten onboarding head 9dddf0a and current main e11fb16, preserving cleaned history. Runtime/build/test inputs match the 1,741-case validated tree; only documentation and ancestry changed. Proprietary artwork remains private.
