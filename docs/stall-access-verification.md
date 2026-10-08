# Selected allied-guild stall access

REQ-143: WHEN a companion checks guild alliance access THE public GuildAllianceLookup SHALL return true only for an active ALLY relation between distinct guilds; pending, ended, absent and enemy relations SHALL return false. The repository SHALL be warmed at registration so event reads use its existing cache.

REQ-144: WHEN a currently authorized shop manager opens guild stall details THE Java and Bedrock controls SHALL offer the Market stall flags/access command. Market SHALL revalidate stall ownership and authority on each mutation; the shortcut SHALL not grant permissions.

SPEAR: spec above; prove alliance contract tests and existing stall menu safety tests; engine optional public service and command shortcut; arch JDK-only API, existing repository cache, no cross-plugin container access; refine local checks and exact-head review evidence. Project-local EARS/state helpers were not found.

Stacked on the existing community guild UI branch (#215). Merge that prerequisite first. No live deployment or player acceptance is claimed.

Local validation: Java 25 full test and shadowJar passed, 1,681 cases, zero failures/errors, 16 environment/integration skips. New alliance contract and existing stall-menu safety tests pass. Actual rank contract maps Market MANAGE_SHOPS to Guilds EDIT_SHOP_STOCK; the shortcut uses that existing permission and Market rechecks it. Optional ignored compile-time companion jars were copied from the existing checkout; no production files changed.


Integration reconciliation: fetched canonical main a15b244 and incorporated the reviewed #215 -> #217 -> #218 source. Resolved the obsolete standalone sales helper versus current shared sales/access controls by retaining the shared controls; retained the reviewed rank regression setup. Unique REQ-143/REQ-144 replace conflicting local IDs. Review order is #215 -> #217 -> #218 -> #216. No player-facing visual behavior changes in this reconciliation; both existing shortcuts are preserved. Final combined Java 25 offline test/shadowJar passes 1,714 cases, zero failures/errors and 15 external skips. Actual Market artifact contract executes; hosted checks remain separate. No local EARS/state helper exists; manual requirement/task/evidence maintained. No merge of a GitHub PR, workflow approval, release, production action or in-game test.
