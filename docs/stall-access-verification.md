# Selected allied-guild stall access

REQ-140: WHEN a companion checks guild alliance access THE public GuildAllianceLookup SHALL return true only for an active ALLY relation between distinct guilds; pending, ended, absent and enemy relations SHALL return false. The repository SHALL be warmed at registration so event reads use its existing cache.

REQ-141: WHEN a currently authorized shop manager opens guild stall details THE Java and Bedrock controls SHALL offer the Market stall flags/access command. Market SHALL revalidate stall ownership and authority on each mutation; the shortcut SHALL not grant permissions.

SPEAR: spec above; prove alliance contract tests and existing stall menu safety tests; engine optional public service and command shortcut; arch JDK-only API, existing repository cache, no cross-plugin container access; refine local checks and exact-head review evidence. Project-local EARS/state helpers were not found.

Stacked on the existing community guild UI branch (#215). Merge that prerequisite first. No live deployment or player acceptance is claimed.

Local validation: Java 25 full test and shadowJar passed, 1,681 cases, zero failures/errors, 16 environment/integration skips. New alliance contract and existing stall-menu safety tests pass. Actual rank contract maps Market MANAGE_SHOPS to Guilds EDIT_SHOP_STOCK; the shortcut uses that existing permission and Market rechecks it. Optional ignored compile-time companion jars were copied from the existing checkout; no production files changed.
