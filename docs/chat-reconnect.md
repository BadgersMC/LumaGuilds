# Guild chat reconnect preference (REQ-135)

## Spec

Guild Settings on Java and Bedrock exposes **Reset chat on reconnect**, controlled by `MANAGE_GUILD_SETTINGS`. New and existing guilds default to disabled. Enabling it applies to every member, including ally chat. A member of multiple guilds resets when any of their own guilds enables it; an allied guild's preference does not apply.

The next main-thread join callback checks the current session, current membership, durable preference and actual channel. Only `guild` and `guild-ally` move to RoseChat's configured default channel. Party, staff, local and other channels remain unchanged. The existing invalid-membership cleanup remains active independently of this toggle.

## Prove and engine

The initial SQLite repository run produced three failures: two missing persistence/CAS behaviors and one missing schema during the failure-path setup. No historical red result is claimed for the listener or menus.

The application owns authorization and membership policy through a repository port. Infrastructure owns JDBC and RoseChat integration. The additive `guild_chat_reconnect_settings` table is included in SQLite/MariaDB migration and both guild-deletion transactions. Reads fail closed to disabled; writes have no local cache and use a bound atomic expected-state update. An unchanged old form cannot overwrite a newer preference. Authority is rechecked when a click/form is submitted.

The join callback is deferred one tick after normal join handlers, and rejects offline or replaced sessions. It uses the real RoseChat adapter and configured default channel, with no new channel provider or message-routing bypass.

## Refine and acceptance

Focused suite: **58 tests passed**, including six service cases, five repository cases, five scheduled listener cases, four actual Java/Cumulus menu cases, locale contracts and existing cleanup/settings contracts. Separate native MariaDB preference suite: **five passed, zero skipped**. The Bedrock validation test controls only the external form reopen transport; submitted form parsing, authority and save handlers are real.

Final clean `test shadowJar mariaDbRewardTest` on Java 25 / Paper 26.2 passed after the Codacy refinements: **1,673 tests, zero failures/errors, four skips**, plus **59 native MariaDB tests, zero failures/errors/skips**. The actual copied Market companion runtime contract executed with zero skips. The four remaining skips are an absent offline snapshot and three MockBukkit registry limitations.

The unmerged local review artifact is `LumaGuilds-3.0.0-reconnect-review.2.jar`, SHA-256 `df96f0bd197ac2c3d1ecf36efec4405fc4eb4fef22512cf7fa592162d267831e`. It is not a production release.

Codacy's first-head findings were addressed by narrowing new implementation types to internal visibility, reducing return/branch complexity and nested JDBC reads, documenting the schema identifier, and refining test fixtures/style. The focused 58-case suite passed after the behavioral refactoring and again after the last two fixture-only formatting fixes. The final runtime source passed the complete clean rerun above; the final focused rerun covers the subsequently refined language fixture.

The first clean attempt encountered locked local database log files; an immediate helper restart then encountered the old process's data-file lock. After verifying the disposable loopback instance was ready, the complete clean integration rerun passed. These were local test-harness failures; no product workaround was applied. Exact-head hosted checks and review findings are tracked on the pull request. This checkout has no project-local EARS validator or SPEAR state helpers; requirement/task/evidence records are maintained directly. No helper validation is claimed.

This is an unmerged review change stacked after PR #213. No production files, configuration, roles or running server were changed. Merge, canonical network pin/build, authorized deployment and a real Java/Bedrock reconnect walkthrough are separate acceptance gates.
