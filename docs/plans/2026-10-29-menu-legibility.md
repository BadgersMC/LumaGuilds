# Bank history and weekly quest legibility

Scope: isolated visual follow-up to PR19 b1af1bb3. Roundtable source/build claims
2321,2330,2338,2343; no publication, CI mutation, deployment or TEST/client use.

## Spec

- UI-LEG-001: WHEN a Java player opens bank history or its member filter with
  the original shipped history title THEN the system SHALL use a compact heading
  independent of guild-name length and retain the complete guild name in the
  existing Back item's tooltip.
- UI-LEG-002: WHEN an installation customizes the history title THEN the system
  SHALL preserve its translation and guild interpolation in both views without
  rewriting the locale file.
- UI-LEG-003: WHEN weekly quest progress is displayed using shipped defaults THEN
  empty progress cells and percentage SHALL use light gray, retaining gold filled
  cells, clamping, counts, callbacks and quest semantics.

## Prove

Read-only TEST claim2299 captured an overflowing Bank Transaction History -
GoldProofOct26 title at1302x776, and dark gray progress on the red theme. Evidence
is retained outside this repository in guild-navigation-evidence. These are
observed defects, not attributed to the cooldown/reset changes. Source confirms
unbounded guild-name interpolation at both title sites, DARK_GRAY empty cells
and a dark_gray percentage token.

## Engine / architecture

BankHistoryTitle recognizes only the exact original shipped template. It selects
separate localized Bank History / Bank Members headings for that template; any
other translation uses the original key and guild placeholder. Both views keep
full guild context on Back tooltips. Saved files are never rewritten or backed
up. Existing custom titles can still be long by operator choice. Existing custom
percentage values also retain precedence; inspect effective locale values before
a separately coordinated deployment.

No MenuTitleBuilder calibration, Bedrock form, inventory callback, authorization,
payment, persistence, API or dependency changes. Project EARS/state helpers are
absent; this record supplies manual SPEAR evidence.

## Refine / acceptance

The initial512MiB compile attempt exhausted memory before tests. Explicit4GiB
resolved that environment failure. A retained unused placeholder argument caused
one locale contract failure in the first full run (2401 total,2294 passed,
106 skipped). The compatible helper then passed rendering tests but its initial
unqualified renderer calls were invisible to the source scanner. Qualifying those
calls matches existing GuiLang conventions; scanner rules and expected baseline
remain unchanged. Fresh final verification is recorded below after completion.

The external interactive HTML preview simulates shipped default labels/palette,
not a Minecraft render. System Chromium local-file navigation was denied by
policy (ERR_BLOCKED_BY_ADMINISTRATOR); no alternate route/policy change was used.
Interaction/mobile viewport validation and real-client acceptance are unproven.
No mobile-accessible hosting is configured under this source-only scope; the
preview is available as a local downloadable artifact. Publication, exact-head
hosted checks, maintainer review and deployment remain separate gates.

Final claim2343 verification:79 focused GUI/locale cases passed without skips.
Full Java25 offline suite:2404 total,2298 passed,106 optional/environment skips,
zero failures/errors. The real-adapter/mocked-player payment flow3, physical
adapter9 and SQLite creation admission12 all executed without skips. No fresh
native MariaDB or live Staff-runtime acceptance is claimed. Diff check passes;
no assertions, scanner rules or expected baselines were relaxed. Evidence/XML
are retained outside Git in /workspace/guild-menu-evidence. Source is committed
locally only; exact hash is recorded in that evidence directory and Roundtable.
