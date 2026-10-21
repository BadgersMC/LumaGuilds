# Dialog acceptance followups

Base: PR241 aa35e0da; canonical main freshly fetched at 283d75f1.
Continue the isolated existing worktree. No production or merge. Shared TEST
window 796 was released by receipt 797; no current live resource claim.

## Spec

- MF-001: WHILE the same authorized menu remains open THE SYSTEM SHALL keep its
  controls usable beyond two minutes, with one shared bounded twelve-hour
  callback/session lifetime. Navigation replacement, disconnect, revoked
  membership, foreign actors and replay SHALL still fail closed. Independent
  purchase quotes and confirmation deadlines SHALL remain unchanged.
- MF-002: WHEN viewing Statistics achievements THE SYSTEM SHALL use the same
  eight current-stat predicates in its count and detail, and link the personal
  achievement tree with a separate guild combat milestone view. Guild combat
  milestones SHALL show exact conditions, retained-war-history scope and no
  extra XP or permanent historical award claim.
- MF-003: WHEN a matching optional inventory-label plate is present THE SYSTEM
  SHALL draw it behind vanilla Inventory text with zero net title advance,
  without changing any inventory slots or actions. Missing plates and Vanilla
  style SHALL retain the existing rendering.

## Prove and implementation

Baseline evidence: native two-minute idle callbacks stopped responding;
Statistics count has six different predicates while detail has eight; native
Inventory text overlaps the patterned divider. Add behavioral regressions for
idle boundaries, shared predicate counts, native scope navigation and optional
zero-advance title composition before implementation. No local Gradle execution
under an unacknowledged build-capacity claim; use hosted exact-head CI.

Project EARS/state helpers remain absent. Manual requirement/evidence tracking
is used. Economy mutation and exhaustive live theme matrix require a new
acknowledged resource window and fixture scope; no live acceptance is implied.

Hosted baseline 9584f534 ran 2,232 tests: three failures, 68 skips. Two locale
contracts rejected the new dynamic conditional key expression; use literal
calls rather than weakening the baseline. The new scope-navigation fixture
also needs the same LangService.msg stub as the existing personal-tree fixture.
This is recorded failure evidence; a fresh hosted run must pass the refinement.

## Live followup and remaining label correction

Exact source654075e1 passed2,164 tests with68 skips in hosted39603917233 and
was activated on TEST with SHA2564fe2dd426cd3ccc461788e31e9a85b6fee95b9b00ae434d4e8d926a8fc2a8013.
Native paging worked after145 seconds idle. Both combat pages, personal scope,
Back to Statistics and Statistics Back passed. All106 companion plugins and
protected Nexo/Holidays configs stayed unchanged. No economic/reward mutation
or exhaustive theme matrix was performed. Codacy still reports573 broader PR
findings; draft CodeRabbit did not independently review the source.

MF-003 failed native acceptance. Six retained server packs included an old
empty Inventory translation. A reconnect restored the label, but the patterned
divider still overlaps it. Switching the new plate to the background font in
PackPR9 source4a111d7 (CI39610373102 green) did not fix that overlap.

The adapter currently exits when Nexo has already converted the literal tag.
The next source correction recognizes only registered single non-ASCII guild
background glyphs and adds a missing plate once, including through the actual
InventoryOpenEvent path. Missing plates and repeated adaptation stay unchanged.
New regression coverage models the early-resolved title; no historical red CI
is claimed for that new test. Native acceptance remains required after CI.

TEST/client windows802/803 were released by Roundtable805 at14:13:42UTC.
Both clients are back on TEST with menus closed. Further shared TEST/client
use requires a new owner handoff. Screenshots and failed-label evidence:
https://chatgpt.com/space/page_7ef3bde67cf481918bf3b8a0f5b90e99.

### CLAIM808 native result and registered spacing refinement

HUMAN handoff10:40-11:10 EDT authorized one Guilds-only restart. Sourcec1bd3fae
passed2234 discovered tests:2166 passed68 skipped0 failures/errors in39616904637.
TEST loaded SHA25681ec569aadb63cdc263224c0c605c21feecaa19cbf1be6e5b27da28e85f86554;
106 companion entries and protected configs unchanged. Backup archive
2026-10-21T144800Z.tar.gz retained. Native3/6-row Inventory labels still overlap;
MF-003 remains FAILED. A connection reset occurred and was recovered through
the normal network entry; no GuildMenuDialog error appeared in the server log.

Exact TEST Nexo1.29.1 binary5dd73151763da3625ef37d473d745c02172eeffcf932c1b1d5a8f5c8216484c2
was checked with javap. Glyph.registerComponent joins each unicode with
Shift.of(-1), so registered glyph text is not one character. The c1 recognition
guard therefore excludes this runtime shape. Regression now covers a registered
bitmap plus spacing sequence; matching remains bounded to registered non-ASCII
strings and idempotent. This source refinement requires new hosted CI and native
acceptance. No second restart is authorized by CLAIM808 and no pack changes made.

### Final packet title boundary candidate

HUMAN window815 activated bd7136f2 after hosted39621821267 passed2167 tests,
68 skipped, zero failures/errors. Native3/6-row labels still overlap. Client
download577cdda6 includes the correct E102 nexo:default bitmap provider and
its valid PNG; glyphinfo confirms the background and plate IDs. Therefore
registration/asset absence is excluded. Event title handling remains unproven.

Add final OPEN_WINDOW title composition to the existing packet adapter using an
immutable glyph snapshot built on InventoryOpen's Bukkit thread. Packet handling
does not call Nexo or guild services. Reapplication is idempotent, missing plates
and ordinary titles remain unchanged; slot/action packets retain their existing
path. Four structural log entries per adapter startup identify event versus packet
composition without player/title/token contents. Regression models discarded event
override, no Nexo calls at packet boundary, idempotence and ordinary chest fallback.
The intended visual is unchanged from the existing interactive plate proposal.
This candidate requires hosted CI and another explicitly handed-off TEST window;
815's one authorized restart is already used. No native success is claimed.

### CLAIM821 runtime lookup refinement

7dd6c1fc was activated with exact SHA3187d763; native label acceptance still fails.
Both event and packet diagnostics report registered=0 changed=false, so composition
has no registered bitmap identities. The previous default lookup silently catches
provider/API failures. The refinement obtains the live Bukkit Nexo provider and reads
its public fontManager/glyphFromID/getUnicodes/getFont methods through that provider's
classloader. It avoids the Kotlin singleton/default-argument presentation ABI and
never substitutes the required fallback for missing optional IDs. One structural
failure class may be logged; no player/title data is logged.

Matching uses the single registered bitmap, preserving any original trailing Nexo
spacing node. The existing -177 spacing plus bare plate advance177 yields zero net
insertion. Regression covers real split-node shape, preserved font, missing IDs,
no presentation method call and idempotence. Native validation remains required;
this is a compatibility candidate, not a confirmed root-cause resolution. CLAIM821
allows one more CI-passed Guilds-only restart before16:35UTC. No pack/config changes.
