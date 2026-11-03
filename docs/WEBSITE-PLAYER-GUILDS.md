# Public player guild associations: local candidate

Requirement: resolve requested public player UUIDs to their actual guilds so
website profiles can show names, levels and banners. Support multiple guilds;
do not infer membership from market access or names. A successful empty list
means no current memberships; failure must remain unavailable on the website.

GET /api/community/player-guilds?players=uuid,uuid accepts 1-50 distinct canonical
UUIDs and requires the existing Web API bearer token to be nonblank and valid.
The Web API remains disabled by default. Unsupported methods/routes, malformed
queries and unauthorized requests are rejected. Missing producer or producer
failure returns 503; JSON is bounded to 150KB.

The response contains generatedAt and items with id and guildMemberships.
Each membership contains only id, name, level and nullable banner design data.
Deleted guilds are omitted. No roles, permissions, roster, bank, homes or grants
are exported. Existing MemberService/GuildService/BannerService adapters are
used without persistence changes. This additive route preserves existing
leaderboard and guild quest routes and serializers.

Manual SPEAR evidence: no project-local EARS/state helper exists. Focused tests
cover public projection, empty membership, duplicate identity rejection and real
loopback HTTP authentication/query behavior. Full Gradle test and shadowJar pass:
1684 discovered, 1668 pass, 16 existing external/integration skips. No historical
red/green claim is made. Website regressions separately verify joins and failure
handling. Local source and test artifact only; not installed, activated, pushed
or submitted as a PR. Canonical incorporation and runtime acceptance remain
separate delivery gates.

Current-base review: the membership and guild repositories use mutable caches,
while HTTP handlers execute on a worker thread. Their concurrency and a coherent
membership snapshot have not been established. Treat this endpoint as a draft;
resolve thread safety and consistency without blocking the server tick before
enabling or deploying it. The code must not infer absence from an unavailable
lookup. MariaDB and live companion compatibility are also unverified.
