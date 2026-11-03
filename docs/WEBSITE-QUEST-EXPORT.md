# Local website quest export

Original base: authoritative main 8740521949d2c1787511e50b265c1459475b3d41.
The candidate is being reconciled against current main c0a144b for review.
No plugin upload or activation has occurred.

Requirements: expose a read-only, bearer-protected JSON snapshot on the existing
Web API; use one consistent SQL statement for the active definitions and ranked
progress because bank/war quest progress can change asynchronously. Export only
guild IDs/names, objectives and counts/reward status. Never export claim actor
IDs, member rosters or private locations. Preserve Long precision as decimal
strings and the existing count DESC / guild UUID ASC ordering. No claim or reward
method may be invoked. Missing active quests return an explicit empty snapshot.

This export covers the current persisted set. Historical default bonus/payout
settings are not stored per week, so do not fabricate historical reward policy.
Standings are bounded to 50 per quest and 30 quests. Report partial coverage.
The website preserves its older signed snapshot route as an alternative.

Proof uses real SQLite query tests and projection/privacy tests; a complete
plugin build is separate from an isolated source harness and live acceptance.
No project-local EARS/state tool was found, so this requirement/evidence record
is manual. Publication requires canonical source incorporation and network pin
verification under the user's standing working agreements.

## Verification

Full Gradle test passes: 1682 discovered, 1666 pass, 16 skipped for external
MariaDB/schema rehearsal/runtime-provider gates. Export tests use a real SQLite
query and real loopback HTTP, verifying Long precision, no claim actor fields,
no grants, exact routes, authentication, methods and explicit currentWeekId:null.
The dedicated quest serializer preserves null without changing leaderboard JSON.
No producer is activated and no live server/client acceptance is claimed.

Current-base review found that the inherited authorization helper treats a blank
bearer token as public. The quest route now explicitly rejects blank tokens,
matching the documented protected contract, and a loopback HTTP regression
checks that behavior. Thirteen focused tests passed locally on c0a144b plus the
candidate; MariaDB and full-suite acceptance remain separate. Existing local
companion JARs were used for compilation and their runtime provenance is not
established.
