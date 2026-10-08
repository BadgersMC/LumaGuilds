# Guild checklist correctness

Current canonical base: a15b244. Isolated branch codex/guild-checklist-correctness; no production mutations, role deletion, merge or deployment.

## SPEAR
Spec: REQ-128 / checklist G02,G24. Existing menu priority rules must also hold at RankService entry points. Owner transfer remains the explicit MemberService operation; an ordinary edit cannot change priority or remove ownership.
Prove: eight direct-service regressions ran against current main: six failed and two passed. They cover self-promotion, owner assignment, peer-rank editing, delegated permissions and direct priority mutation. All eight pass after the service guards. Ownership transfer was found to use separate writes; four real SQLite tests now cover commit/restart, a failed second write and retry, stale member state, and invalid ownership ranks. No historical red/green is claimed for ownership transfer.
Engine: retain existing permission checks and enforce relative priority and permission delegation at RankService entry points. Ordinary rank edits preserve priority; explicit ownership transfer alone changes the owner. Transfer uses compare-and-set updates in one transaction and changes caches only after commit.
Architecture: transaction and SQL stay in infrastructure; the repository port defaults to rejecting transfer for unsupported adapters rather than falling back to non-atomic writes. Existing member joining, rank identities, hidden permissions and explicit reorder behavior are retained.
Refine: clean Java 25 / pinned Paper 26.2 test and shadowJar passed 1,550 tests, zero failures/errors and four optional skips. Existing rank priority, permission profiles, home access, bank, progression, quest and leaderboard suites ran as part of that total. Focused ownership and rank tests passed. Hosted CI, MariaDB ownership transfer and actual player acceptance are separate gates.
Project-local EARS/state tools are absent; these docs and task records provide manual evidence.
Community proposals remain deferred. User-confirmed defects remain closed. Database/client/staging acceptance remains separate from local automated checks.

## Release boundaries

This branch is an unmerged review artifact, not a production release. Current production provenance could not be verified through the panel download. Review and merge are required before rebuilding the clean merged source and updating the network's dependency pins. No live database repair is attempted: an already ownerless guild needs its actual read-only evidence and a separately approved recovery operation.

Review refinement: new files formatted, descriptive priority constants added and optional native ownership test inputs tracked. Both native MariaDB transaction tests executed successfully on disposable loopback MariaDB 11.8.3: commit/restart/stale retry, and a failed second write preserving SQL rows/caches followed by successful retry. The native fixture uses pre-created InnoDB tables, matching the migration-before-repository lifecycle. Never run it concurrently with the shop-XP native fixture, which shares the disposable lg_shop_xp_test database. Four SQLite ownership and eight rank authority tests also passed. No production database was accessed or changed.

G16 completion investigation found `/guild home` shared the unfiltered management-name completion. The extracted current behavior failed inaccessibleHomeNotSuggested (one failure), while allowed-name behavior passed. The teleport completion now filters through the existing canUseHome decision on the server thread; activation/removal retain the complete management list. G37 review also asserts that an authority-rejected war declaration never reaches the notification service or durable record map. These are existing-behavior regressions, not community feature additions.

Final refinement: clean full test/shadowJar passed 1,554 tests, zero failures/errors and four unrelated skips, including both native ownership cases. The separate mariaDbRewardTest initially found a real guild-disband failure against the historical MariaDB relations schema, plus a SQLite-only TEXT-primary-key fixture. Disband now selects the existing relation-column layout using JDBC metadata in its existing transaction; the orphan-history fixture uses bounded UUID strings on both engines. All 44 opt-in native reward/boost/creation contracts then passed without skips on disposable MariaDB 11.8.3. Teleport/management completion behavior and permission-rejected war notification boundaries remain covered. No production mutation or community feature was added.


## Combined checklist integration (8 October)

The independent review branches conflicted in SPEAR records, bank/stall command insertion and test/startup wiring. Reconciled the existing PRs into the ordered source chain #207 -> #208 -> #209 -> #210 -> #211 -> #212 -> #213, retaining both commands, all shutdown hooks and all external artifact/native database test inputs. Earlier PRs remain open for individual review; merge them in order before this final correctness layer. Shop-XP policy uses REQ-134 so holiday ownership retains REQ-121.

Spec: preserve each approved feature and failure boundary across the combined source; community proposals remain deferred.
Prove: a local merge probe reproduced the conflicts before resolution. No new behavioral red/green claim applies to documentation reconciliation.
Engine/architecture: combine the approved adapters and service guards without changing the economy or public API; use actual ignored RoseChat RC-2, CombatLogX, Playtime and EnthusiaStaff API artifacts.
Refine: combined Java 25 / pinned Paper 26.2 test + shadowJar passes 1,653 tests, zero failures/errors, four unrelated optional skips. Nine native shop-XP tests, two native ownership-transfer tests and the actual Market stall artifact contract execute without skips in that suite. The separate native MariaDB reward/boost/creation/cosmetic/insert/theme suite passes 54 cases with zero skips. Configured databases are disposable loopback test databases. Hosted checks must confirm the final published head; local evidence does not establish GitHub, production or client acceptance.

No project-local EARS/state helper exists; this manual requirement/task/evidence record is maintained directly. No production writes, role deletion, deployment, restart or merge occurred.
