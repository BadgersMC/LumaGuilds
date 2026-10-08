# Community chat, ranks and directory

## Spec

Owner approved G20/G21/G22/G24/G25 and Market-owned G44 on 8 October.
This branch starts at freshly fetched canonical main a15b244 and includes the
open #214 dependency chain; target #214 until dependencies merge.

- REQ-136: Rank creation opens individual permission selection without granting
  an entire category. Creation/editing share the complete supported permission
  catalog; disabled claims are hidden and preserved on edits. Category placement
  stays inside the inventory. Existing service authority/priority guards apply.
- REQ-137: Public guild directory retains all guilds, including invite-only
  guilds, and displays authoritative owner(s), founding date, allies, member
  count, level and existing activity metrics on Java and Bedrock. Missing owners
  are explicitly unknown. No online/last-active value is invented.
- REQ-138: Chat provides an opt-in persistent destination indicator, a personal
  global-player-message visibility preference, and guild fullscreen announcements
  using existing announcement authorization/rate limits. DMs, server notices and
  private channels remain visible. Indicator must not replace another plugin's
  action bar. Unknown channels retain their actual name.

## Prove / engine / arch / refine

Current creation category handler toggles all permissions despite its individual
selection comment; six groups cannot use index * 3 + 1 in a nine-column row.
Regression tests and exact commands/results will be recorded before delivery.
No local EARS or SPEAR state helpers are present; manual records apply.
Market FIFO attribution policy is approved separately; no payout changes.

Production remains unchanged. Local tests, GitHub checks, manual review and
real Java/Bedrock acceptance are distinct. No merge/deployment authorization.

### Recorded proof and boundaries

The new category-opening regression failed against the prior handler and passed
after replacing bulk grants with individual selection. SQLite/MariaDB preference
contracts cover defaults, persistence, rollback without cache publication and
64-bit announcement timestamps. Native MariaDB first failed on legacy TEXT key
DDL; portable UUID keys, BIGINT times and REPLACE writes resolve that failure.
Directory contracts cover owner/alliance authority, pending exclusion and page
bounds. Chat tests preserve DMs/notices/private channels, exercise opt-in indicator
cleanup and prove muted/rate-rejected fullscreen sends never show titles.

The report shortcut appears only when Market registers its accounting command.
It uses the same existing EDIT_SHOP_STOCK mapping as Market's MANAGE_SHOPS port;
it does not introduce a new rank permission. The companion rechecks access.
Offline owner names fall back to UUID rather than blocking on profile lookup.
Local clean full-suite and native-profile totals are recorded below after final
validation. Hosted checks and real Java/Bedrock rendering remain separate gates.

### Local verification, 8 October 2026

Java 25 / Paper 26.2: `clean test shadowJar mariaDbRewardTest
-PmariaDbTestPort=33318 -PreleaseVersion=3.0.0-community-review.1` (version
argument quoted in PowerShell) completed. Full suite: **1,679 tests, zero
failures/errors, four unrelated skips**. Separate disposable MariaDB profile:
**61 tests, zero failures/errors/skips**. The actual companion runtime contract
executed with the frozen Market accounting artifact, not a mocked API. After a
whitespace-only SQL cleanup, focused persistence verification and the review JAR
were refreshed without broadening the behavioral claim.

Local review artifacts are unmerged test builds. Exact PR-head hosted checks,
manual review, canonical source/pin integration and real Java/Bedrock acceptance
remain independent release gates. Production was not accessed or changed.

Hosted review opened as [PR #215](https://github.com/BadgersMC/LumaGuilds/pull/215),
paired with [Market #207](https://github.com/BadgersMC/EnthusiaMarket/pull/207).
The first hosted wiki lint identified four extra-blank-line issues; they were
removed. The exact configured local markdownlint v0.13.0/v0.34.0 checks all 70
wiki/plan files with zero errors; topic parity passes. Frontmatter and strict
MkDocs passed on the initial hosted source; local frontmatter tooling could not
run because PyYAML is absent. Hosted checks for the updated head remain a distinct
gate and are inspected through GitHub. No helper/tooling success is invented.

The initial hosted unit build exposed an empty CI `MARKET_API_JAR` variable.
Empty/blank values now select the optional profile, while a non-empty configured
missing file still fails. Both the valid real-artifact execution and blank-profile
skip were verified locally; Gradle tracks only non-blank configured artifacts.

### Codacy refinement

The current provider report exposes 286 new findings; GitHub annotations had not
yet synchronized. Inspect severity and affected code directly. Preserve all
permission, privacy, database rollback and unknown-data contracts while refining
new code formatting and method boundaries; do not suppress the quality gate.

Refinement preserves literal locale keys (the source contract caught a dynamic-key
refactor) and the established banner resolver wiring. Those regressions were
repaired and the final full suite passes; public directory facts use a regular
class, and chat filtering, destination labels, rank toggles and SQL readers have
smaller methods. Existing SQL rollback and cache-publication contracts remain.
The optional broad default Detekt scan also sees historical repository findings;
it is not presented as a passing project gate or a substitute for hosted Codacy.

Final refinement validation: 1,679 tests, zero failures/errors, 15 external skips
in the ordinary profile; all 61 disposable native MariaDB cases pass without
skips. The actual companion runtime contract executes. Wiki lint (42 selected
wiki/plan files) and all 13 topic-parity checks pass. Production is untouched.

Second hosted refinement: #215 head e1381b6 reduced the provider report from 286
to 125 findings and removed the high compatibility issue. Refine remaining
return counts, constants, documentation, configured formatting and transaction
method boundaries. The focused privacy, SQL rollback/cache, directory, rank,
announcement, locale and stall-menu contracts pass (40 tests, zero failures/errors).
Final-head full/native/hosted validation remains separate. No gate suppression.

Third hosted refinement: the provider reports 41 remaining findings at 6751cbc.
Public directory facts now expose an interface and constructor-style factory
with a private immutable data implementation; value equality remains available.
Restore unchanged legacy SQL-method formatting, split test arrangement from
assertions without dropping coverage, and refine remaining configured style.
Focused behavior, locale, wiring and wiki checks pass. The preceding full matrix
passed 1,679 ordinary cases and all 61 native cases; the final head is rechecked
separately before completion.

Fourth hosted refinement: ffe4ec3 reduced the direct provider report to 22
findings. Directory implementation types and the adapter constructor are now
internal; the supported guild API does not expose these types. Java, Bedrock
and Discord consumers compile, with immutable snapshots/equality retained.
Remaining configured formatting and test-method length are refined without
dropping assertions or suppressing checks. Focused contracts pass; the preceding
full matrix passed 1,680 ordinary cases (15 external skips) and all 62 native
cases without skips. Final published-head validation is checked separately.
An online dependency metadata request timed out at JitPack; the cached offline
profile compiled and passed, which is distinct from hosted CI.
