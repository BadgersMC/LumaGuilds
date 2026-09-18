# LG-1207 creation cooldown verification

SPEAR specification: `2026-09-17-creation-cooldown.md` (REQ-055).
The initial RED run failed compilation because the required history adapter and
policy did not exist. Implementation adds immutable creator attribution, locked
admission/deletion, atomic guild/history writes, configuration and localized
command feedback. The architecture and localization contracts pass in the full
regression run.

## Results

- Full `test`: 987 tests, zero failures/errors/skips.
- `mariaDbRewardTest -PmariaDbTestPort=33371`: 35 tests, zero failures/errors/skips.
- `shadowJar`: successful, `build/libs/LumaGuilds-2.1.0.jar`.
- `git diff --check`: clean.

Ten new tests cover configuration/defaults, exact expiry and seven-day boundaries,
restart persistence, missing legacy attribution, deletion replay, competing
deletions, failed creation cleanup, and injected history write failures. Nine SQL
contracts also run against disposable MariaDB 11.4.5; the real guild repository
tests initialize the production MariaDB migration chain. Fault injection verifies
that guild rows and cache publication survive rollback correctly.

## Local environment adjustments

This session's Windows sandbox required a workspace-local Gradle cache and Java
temporary directory, offline resolution, in-process Kotlin compilation and a 4 GB
Gradle heap. JUnit's automatic temporary-directory cleanup hit AccessDeniedException
inside Java path resolution. Cleanup was deferred using
`junit.jupiter.tempdir.cleanup.mode.default=NEVER`; no tests were skipped.
The subsequent PowerShell cleanup was rejected by automatic approval policy, so
temporary JUnit files remain under the workspace's `.tools/java-tmp` directory.

MockBukkit tag loading also failed while mounting its JAR as a Java NIO filesystem,
causing 65 cascading failures in an initial full run. The final run prepended an
unchanged extraction of the installed MockBukkit JAR's `tags/` resources to the
test classpath using a local Gradle init script. No production dependencies, mock
behavior or assertions were changed. The final log is retained locally as
`../creation-cooldown-final.log`; sandbox tooling remains outside the repository.

## Boundaries and remaining work

Creator tracking applies to newly created guilds. Legacy guilds have no reliable
creator record and receive no retroactive penalty. Existing disband vault/member
cleanup is outside the guild-row/history transaction. Live Paper/client validation
remains outstanding; no production server or database was modified.

Chapter lifecycle/migration, paid creation/home activation, seasonal Elo, bounded
prestige runtime, and reward purchase actions remain separate pending Chapter 2
tasks. The Chapter 2 reward gate remains disabled by default.
