# Ally-home stale menu authorization

## Spec

REQ-141 preserves existing access policy and safety/countdown behavior while checking current authorization and destination when clicking an old Java menu. Stable guild identity survives renames. No new homes, pricing or cooldown policy is introduced.

## Prove

Canonical main a15b244 captures `allowed` and `home` when rendering. After fixing test harness compatibility with InventoryFramework's plugin lookup, the unchanged implementation failed four of five actual GuiItem-action cases: revoked access, removed home, disbanded target and relocated coordinates. Rename behavior passed. All five cases pass after the change; a sixth regression verifies newly granted access also uses current state. Existing ally-access and locale focused tests pass. These are automated adapter regressions, not client acceptance.

## Engine / arch

The click handler resolves GuildService.getGuild using the stable target ID, reads current allyHome, and delegates authorization to canUseAllyHome. It uses the existing denial messages and teleport safety/countdown path. No API, database, platform adapter or economy contract changes. Countdown-time authorization is outside this bounded correction and is covered separately by the existing command authorization work; this PR does not claim it for the Java ally menu.

## Refine

Full Java 25 offline test/shadowJar passes 1,543 tests, zero failures/errors and four existing external skips. The final helper refinement and sixth access-grant case pass the focused test/shadowJar profile. No project-local EARS/state helpers exist; manual requirement/task/evidence records are maintained. In-game acceptance is deferred at user request. Production untouched; any generated JAR is an unmerged local test artifact. Hosted CI and manual review remain separate gates.


## Review stack and final eligibility refinement

Integrated reviewed #215/#217 source without changing prior implementation; retained REQ-121 through REQ-140 and all task records. Review order: #215 -> #217 -> #218. Additional source inspection found that getAllyHomes applies current mutual perk eligibility while canUseAllyHome does not; the old button bypassed that current listing. A seventh actual-action regression failed before the eligibility-list check, then passed. The click now checks the current named home against the target resolved by stable ID before invoking access authorization.

Final combined Java 25 offline test/shadowJar: 1,713 tests, zero failures/errors and 15 external skips. The actual Market review-artifact contract executes with zero skips. Native database results from prerequisite work remain separate; this new adapter change introduces no SQL/API contract changes. Interactive behavioral preview is delivered alongside the changes in chat; cropped inventory rows, sample guilds/items and resulting actions are simulated, not server/client acceptance. Production untouched. Final-head hosted checks and manual review remain separate.
