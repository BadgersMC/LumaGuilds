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

Initial full `test shadowJar` passed 1540 tests on JDK 25 with the existing
RoseChat and CombatLogX compile dependencies supplied locally. Initial attempts
failed because the ignored companion JARs were absent, followed by a test fixture
name violating the existing guild-name length limit; the fixture was corrected.
No production dependencies or APIs were patched. Clean verification on the new
base passed `clean test shadowJar` in 3m 4s: 1540 tests discovered, 1536 passed,
four skipped, zero failures/errors. Local unmerged LumaGuilds-3.0.0.jar SHA-256:
`c8546bc631c5205b69a05d44993ff949b64385d0d13bce566830a14ad7821d2b`.
Server/client acceptance remains deferred. No upload, restart or activation.
