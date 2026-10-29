# Weekly reset snapshot revision fence

References: PR18 recovery contract in `2026-10-28-deposit-quest-delivery-recovery.md`.
Tag: TDD. Scope: local Roundtable request2229, ACKs2230/2231, claim2232 through
2026-10-29 19:12 UTC. Isolated branch on exact PR18 head076b7ad2. PR19 is untouched.

QS-RST-001: a weekly reset SHALL NOT award bonuses or leaderboard payouts from a
quest-definition snapshot replaced before its transition fence is acquired.
A stale snapshot SHALL stop before any payout or paid marker, leave the current
set unchanged, and permit retry with the new definition. Existing deposit recovery,
transactional revision increments and transition retry semantics remain intact.

Prove: deterministic two-adapter SQLite/MariaDB regression pauses reset after its
active-definition read, commits a same-week payout edit, then resumes reset.
The old implementation is expected to pay the stale amount. Capture actual red
results before any engine correction. No historical reproduction is assumed.

Engine/arch: only after reproduction, bind reset's definition snapshot to the
existing monotonic revision and check it atomically when taking the transition
fence. Preserve existing repository entry points and supported companion APIs;
new adapter capability must fail closed. No schema, economy policy or GUI changes.

Refine: focused SQLite/native MariaDB, full regression and packaging, then source
review/evidence. One sequential cloud Gradle worker and one disposable loopback
MariaDB only. No shared provider writes, TEST/client, merge or deployment. Stop
all task processes by the claim deadline; local bridge records explicit release.
Project EARS/state helpers are absent; this is manual SPEAR traceability.

## Reproduction and correction

On unchanged engine076b7ad2, the new shared contract failed on both SQLite and
MariaDB11.4.13: reset paid500XP after a second adapter committed the200XP edit.
Actual red log and both XML results are in /workspace/pr18-reset-evidence/red.log
and red-xml. This is a new executed reproduction, not inherited review evidence.

The correction reads the existing monotonic revision before loading the payout
snapshot and passes it to a revision-aware transition overload. The SQL adapter
checks it under the active-row/revision locks before any transition marker or
payout. Existing one-argument entry points are retained. The new port overload
defaults false, so other repository implementations must explicitly opt in; no
supported companion implementation was found in the available source inventory.
The in-memory test repository models revision changes explicitly.

## Final local verification

- RED on unchanged076b7ad2: two tests executed, both failed with stale500XP payouts
  after a committed200XP same-week edit; no skips or execution errors.
- Focused corrected suite:71 cases,70 passed,0 failures/errors,1 expected skip
  (the MariaDB-only contention test in the SQLite subclass). All25 native MariaDB
  quest contracts passed without skips, including the new race and200XP retry.
- Full `check shadowJar verifyAcceptanceProbePackaging`:2390 cases,2284 passed,
  0 failures/errors,106 skips in other opt-in fixtures and MockBukkit operations.
  The native race is separately proven in the focused run; it is opt-in in full.
- Local wiki front matter/topic parity and whitespace checks passed. Compiled
  inspection retains both transition descriptors; no schema/companion API changes.
- A fresh-context reviewer found no actionable issue in the implemented fix and
  independently inspected the saved red/green XML. This is agent review, not
  maintainer approval or independently rerun tests.

Logs and XML snapshots: /workspace/pr18-reset-evidence/{red,focused,full}.log and
{red,focused,full}-xml. The disposable MariaDB container was stopped and removed.
No remote branches or PRs were changed. The fix is a local unmerged commit on
codex/quest-reset-revision-fence-20261029; publication, exact-head hosted CI and
stack placement require a separate coordinated step. PR18/19 remain unchanged.
No TEST/client, shared provider writes, merge, deployment or historical XP repair.
