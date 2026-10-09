# Read-only alliance snapshot for KOTH

SPEAR: main a15b244e inspected before edits; rebased onto freshly fetched e11fb16e
after its history/resource cleanup. The source diff is only these five API/test/
evidence files; no removed resources are reintroduced. Public GuildLookup already
exposes guild/member identity but does not expose relations. Requirement API-KOTH:
THE SYSTEM SHALL provide a read-only UUID alliance graph containing every guild
and active ALLY relation, excluding pending/expired/truce/enemy relations. Unknown
provider capability SHALL return null, distinct from a known empty graph.

Add a default-null interface method for old provider compatibility and implement
it through the existing RelationService; keep Koin inside LumaGuilds. No bank,
XP, combat, persistence or permission changes. Existing four-argument construction
remains supported; runtime injects RelationService. Local tests, build/review and
real companion runtime acceptance are separate gates. No deployment authorized.

Initial full `test shadowJar` succeeded with 1540 discovered tests on JDK 25 and the existing
RoseChat and CombatLogX compile dependencies supplied locally. Initial attempts
failed because the ignored companion JARs were absent, followed by a test fixture
name violating the existing guild-name length limit; the fixture was corrected.
No production dependencies or APIs were patched. Clean verification on the new
base passed `clean test shadowJar` in 3m 4s: 1540 tests discovered, 1536 passed,
four skipped, zero failures/errors. Local unmerged LumaGuilds-3.0.0.jar SHA-256:
`c8546bc631c5205b69a05d44993ff949b64385d0d13bce566830a14ad7821d2b`.
Server/client acceptance remains deferred. No upload, restart or activation.

Hosted Wiki Checks passed. Unit Test Gradle failed twice before compilation due
to truncated upstream AxKothAPI/axapi downloads. Codacy's 41 added annotations
were test formatting/documentation/visibility and one chained-call layout;
these were refined and the two focused alliance tests passed again in 28s.
The artifact hash above identifies the earlier clean build before style refinement.
Current-head hosted checks remain a delivery gate, distinct from local proof.

## EnthusiaKoth delivery refinement

Current canonical main e60d99e1 and AxKoth retirement #221 are incorporated. The newly added runtime-only retired registrar is also removed. Existing Discord cleanup remains intact. Full clean Java 25 test/shadowJar at implementation fff95638 passes 1,544 cases, zero failures/errors and four existing skips. Codacy formatting findings are corrected with small fixture helpers; the two focused alliance cases pass again.

EnthusiaKoth #5 now supplies an opt-in actualGuildApiTest. All 14 real-provider cases execute against this built artifact, with a checked class origin, alliance/old API/system-bank/protected-payout coverage and tracked path/content inputs. Changing either the configured path or JAR bytes reruns the task; unchanged bytes are UP-TO-DATE. Ordinary KOTH suite has 282 passing cases plus one explicit real-provider-only skip. Provider and consumer hashes are recorded in its companion-api-verification.md. This is runtime API boundary proof, not actual server/client acceptance; the existing Paper 1.21.11 compilation profile and Java 25 provider-test profile are distinct.

Project-local EARS/state helpers remain absent. Exact-head hosted checks and review are inspected separately. No XP, payout amount, reward selection, permission, bank or alliance policy was added or changed; no production action performed.
