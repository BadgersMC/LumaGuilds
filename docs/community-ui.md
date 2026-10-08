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

### Codacy refinement complete

Inspected the direct provider report because GitHub initially omitted/truncated
findings. The initial report contained 286 new findings. Refinement preserved
literal locale keys, banner wiring, permission/privacy rules, transaction rollback
and cache publication; existing contracts caught and helped repair intermediate
locale/wiring regressions. No quality-gate suppression was added.

Directory implementation types and the Discord adapter constructor are internal;
the supported guild API does not expose them. Immutable directory facts retain
value equality. SQL readers, chat filtering/destination labels, announcements,
rank toggles and test arrangement have smaller methods and configured formatting.
The broad default Detekt advisory also found historical repository issues; it is
not a passing project gate or a substitute for the hosted provider.

Final implementation/test source: 604e1e57e76656a604cf58a44509ba150f28a3ad.
Codacy check 113413615301 succeeds with zero annotations/new issues. All four
hosted wiki checks pass at that source head. Full local Java-25 / Paper-26.2
matrix: 1,680 ordinary tests, zero failures/errors, 15 external skips; all 62
separate native MariaDB cases pass without skips. The actual frozen Market
companion runtime contract executes. The owned loopback helper is stopped.

Online JitPack metadata timed out during one validation attempt; the cached
offline profile compiled and passed. This is separate from hosted CI. Wiki lint
passes for 42 selected wiki/plan files. Local EARS/state helpers remain absent;
manual requirements/tasks/evidence are maintained. Final hosted results are
recorded on #215. Manual review and real Java/Bedrock acceptance remain open.

This final evidence/state update changes documentation only; additional engine
or behavioral proof does not apply because runtime/test sources are unchanged.
Production stays untouched; no merge, deployment, approval or restart occurred.
