# LG-1214 reward purchase actions — verification

Specification: `2026-09-17-reward-purchase-ui.md`, REQ-050/092/093.

## SPEAR evidence

- RED: `GuildRewardPurchaseServiceTest` failed compilation before the quote and
  confirmation service existed (`../reward-purchase-ui-red.log`, local evidence).
- Engine: fresh server quotes, paired guild-settings/bank-withdrawal permissions,
  lazy atomic purchase adapter, reloadable gate, Java confirmation and Bedrock
  catalog/modal handlers, localized outcomes and identical-request retry.
- Architecture/refine: layer, locale and real Koin graphs for claims on/off pass.
  The initial focused run caught registration in the claims-only module and an
  obsolete read-only locale key. Both were corrected. Cumulus is now explicitly a
  test dependency so real form responses are tested; production remains compile-only.

## Final results

JDK 21, Gradle 8.5, workspace Gradle cache, offline dependencies, in-process Kotlin
compiler with 4 GB heap. Full command:

```text
gradlew.bat test shadowJar mariaDbRewardTest -PmariaDbTestPort=33371 --offline -Pkotlin.compiler.execution.strategy=in-process "-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m" --console=plain
```

- Focused purchase, UI, SQL, layer, locale and startup contracts: 48 passing.
- Full regression: 998 tests, zero failures/errors/skips.
- Disposable MariaDB 11.4.5: 36 tests, zero failures/errors/skips.
- Shadow JAR: `build/libs/LumaGuilds-2.1.0.jar`, SHA-256
  `2943a21b5ac31b76dece61381b4975d98739afa67bd3491f8dbb157f648e0021`.
- `git diff --check`: clean.

The final run used ordinary JUnit cleanup and normal MockBukkit resources, without
the prior restricted-session cleanup/resource overrides. The local final log is
`../reward-purchase-ui-final.log`. MariaDB ran only on loopback port 33371 with fresh
test schemas; fixtures drop their schemas and the disposable server is shut down
after verification. Cleanup of older restricted-session temporary directories was
blocked by approval policy; those files remain outside the repository.

## Coverage and limits

New contracts verify canonical quote price/version/identity, no spending on quote
or cancel, disabled/unauthorized/unavailable/locked/owned rejection, wrong-actor
confirmation, gate reload, cross-guild rank rejection, safe uncertain retry, actual
Java button handlers, actual Cumulus responses, server-thread dispatch and disconnect
handling. SQL gate-reload checks run on both databases and prove no balance,
ownership or receipt write while disabled. Existing contracts cover atomicity,
concurrent purchases, permission revocation, stale quotes and restart receipt replay.

Migration readiness is now proven by `ChapterRewardMigrationReadinessTest`: actual
Chapter 1→2 migration output reopens through production pooled SQLite storage and
resolves through the enabled reward read model with migrated home capacity, run level
1 and empty purchase ownership; no account is synthesized by the read path and no
historical Chapter 1 progression becomes a Chapter 2 purchase. The final offline
`test shadowJar` gate passes 1,055 tests with zero failures/errors/skips.

LG-1214 code readiness is complete. The default Chapter 2 gate remains false and
live Paper/Java/Bedrock client validation is intentionally deferred to the Sep 28
closed-server deployment gate. No production data or server was changed by this proof.
