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
