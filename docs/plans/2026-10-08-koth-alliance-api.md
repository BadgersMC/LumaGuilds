# Read-only alliance snapshot for KOTH

SPEAR: current main a15b244e inspected before edits. Public GuildLookup already
exposes guild/member identity but does not expose relations. Requirement API-KOTH:
THE SYSTEM SHALL provide a read-only UUID alliance graph containing every guild
and active ALLY relation, excluding pending/expired/truce/enemy relations. Unknown
provider capability SHALL return null, distinct from a known empty graph.

Add a default-null interface method for old provider compatibility and implement
it through the existing RelationService; keep Koin inside LumaGuilds. No bank,
XP, combat, persistence or permission changes. Existing four-argument construction
remains supported; runtime injects RelationService. Local tests, build/review and
real companion runtime acceptance are separate gates. No deployment authorized.
