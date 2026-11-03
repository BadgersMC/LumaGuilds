# Guild quest discovery - local delivery

## Spec

REQ-QUEST-DISCOVERY-1/2/3 cover direct member commands, configurable availability reminders and quest/score placeholders. Authoritative repository: BadgersMC/LumaGuilds. Base: bfa6931ed2b0beb78ef37b8c730c3c5146f18958, fetched before isolated work on codex/quest-member-discovery. No PR, push, production upload, reload, restart, or spawn configuration change is authorized by this delivery.

## Prove

Current main's GuildCommand.onMenu requires management rank privileges. Existing GuildQuestsMenu and BedrockGuildQuestsMenu already check membership and provide quest leaderboard navigation; these presentations are reused. Existing weekly placeholders cover objectives and guild progress, so only availability, score/rank aliases and top guild/score fields are added. This is source-based brownfield evidence, not a historical automated failing baseline.

## Engine and architecture

The dedicated default-true lumaguilds.guild.quests permission does not grant management permissions. Nonmembers receive existing feedback. QuestAvailabilityNotifierBukkit lives in infrastructure and reads shared services without modifying progress, claims, or rewards. Join and reset notifications use separate progression.yml switches. WeeklyQuestCoordinator's callback runs only after generation of a new set; ordinary minute refreshes do not announce again. English fallback locale and /g help quests document direct access.

Placeholder leaderboards and viewer ranks use 30-second caches scoped to the weekly set. Top indexes are bounded to 1..100. Guild progress cache now includes week identity so rollover cannot reuse the previous week's score. No companion plugin API or persistence schema is changed.

## Refine

GuildQuestAccessTest covers member/nonmember behavior, aliases and declared permission defaults. QuestAvailabilityNotifierBukkitTest covers clickable reminders, independent switches, completed/expired/future/disabled sets and denied/nonmember players. WeeklyQuestDiscoveryPlaceholderTest covers playerless spawn output, over-target scores, bounded/missing rows, rank caching and rollover. WeeklyQuestDiscoveryResetTest covers one announcement per generated set and disabled quests. Existing Bedrock routing and layer checks remain part of validation.

No project-local EARS validator or SPEAR state helper was found under tools, ci, docs, or the repository root; this requirement/task/verification record is manual evidence and does not claim those tools passed.

## Remaining acceptance

- Test /g quests and /guild quest menu as ordinary Java and Bedrock members, including an explicit deny of lumaguilds.guild.menu.
- Test nonmember rejection and existing management permission gates.
- Verify reminders at join and weekly rollover, including both switches disabled and a fully completed guild.
- Configure the server's chosen spawn display with the documented placeholders and verify player-context versus global rendering.
- Verify real Minecraft menu artwork, resource-pack rendering and Bedrock forms. Browser preview is a schematic with sample data.

Local validation results are recorded below after the final run. Hosted checks, staging and production acceptance remain separate.

## Final local validation

- JDK 25.0.3 / Paper API 26.2.build.129-stable: `gradlew.bat test shadowJar --offline --no-daemon -PreleaseVersion=3.0.0-quest-discovery-LOCAL-UNMERGED` passed after the final rank-cache change.
- 1,657 tests discovered: 1,643 passed, zero failures/errors, 14 skipped. Skips: native MariaDB shop-XP fixtures (9), Bukkit target-provider environment fixtures (3), optional companion Market artifact (1), schema rehearsal environment (1). These are not claimed as validated runtime profiles.
- All 15 newly added quest-discovery tests passed. Existing architecture and Bedrock routing checks passed.
- Initial local compilation failed because companion dependency JARs were copied into a nested directory; corrected the local build setup without changing project dependencies. The permission declaration test's reader was corrected to treat dotted permission keys literally before loading YAML. No production failures were inferred from these setup issues.
- Generated testing JAR: build/libs/LumaGuilds-3.0.0-quest-discovery-LOCAL-UNMERGED.jar. Embedded plugin.yml version verified. SHA-256: cd2e53d1a95d7df3ca933ed492e7641d4dcbb3960943144176800592508827ec. Built from the isolated local working tree; not merged or production-approved.
- git diff --check passed. Canonical origin/main was re-fetched and remained bfa6931.
- Private schematic preview saved with owner-only sharing; no public art published. Reminder/menu/leaderboard/nonmember/inactive/completed/Bedrock flow checked locally at 360px with no horizontal overflow. The private HTTPS Page redirects unauthenticated browsers to login; signed-in mobile Page rendering remains unverified.
- No PR or push. No hosted CI results claimed. No staging, production upload, reload, restart or real-client acceptance.
