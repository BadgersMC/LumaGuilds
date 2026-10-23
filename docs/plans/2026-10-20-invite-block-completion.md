# Guild invite block completion contract (G13)

## Source status and defect

This change is based on Guilds source commit
`0106651cb4c7857412e4c7fe410b80c04d0f5a7a` and adds the separate V2 capability
agreed with the Friends consumer. It also corrects the SQL setter used by V1 on
both SQLite and MariaDB. The native validation commands below are a handoff plan;
the implementation author has not run them. An immutable reviewed source commit,
native results and a newly built artifact receipt are required before describing
this implementation as validated or installed.

The previous setter could return `false` after an autocommitted insert when
statement or connection close threw. Its cache was then still empty. A compensating
unblock could see that matching empty cache, return `true` without SQL, and leave
the stored block behind. A pending-invitation cleanup exception could also escape
after a block had already been saved. Neither a V1 Boolean nor a subsequent cache
read was proof that a failed invocation was rolled back.

The correction removes the cache success shortcut. Every admitted setter executes
an insert or delete in an explicit transaction, including an apparently unchanged
state. Duplicate inserts are accepted only after SQL verifies the exact stored
pair. Confirmed SQL state is published to the cache before success is returned.

## Public API and compatibility

`src/main/java/net/lumalyte/lg/api/GuildInviteBlocksV2.java` is the separately
registered public service. Its complete wire surface uses only JDK types:

```java
package net.lumalyte.lg.api;

public interface GuildInviteBlocksV2 {
    int apiVersion();
    String storageScopeKind();
    boolean isBlockedDurable(java.util.UUID ownerId, java.util.UUID targetId);
    java.util.concurrent.CompletionStage<Void> setBlockedDurable(
        java.util.UUID ownerId, java.util.UUID targetId, boolean blocked);
}
```

| Member | Contract |
| --- | --- |
| `apiVersion()` | Exactly `2`. |
| `storageScopeKind()` | Exactly `BACKEND_LOCAL_SQLITE`. |
| `isBlockedDurable(owner, target)` | Queries committed SQL state, reconciles this provider's pair cache, then returns the observed Boolean. Failure throws; it is not authoritative absence. |
| `setBlockedDurable(owner, target, flag)` | Normal stage completion contains strictly `null`. The requested state is committed, the same provider's cache is reconciled, and this invocation has no block work left to apply later. |

Both operations currently perform synchronous database I/O. Callers should dispatch
them off the main thread. The returned setter stage is already settled; the
provider does not enqueue writes, retries or later completion callbacks. A
caller-created dispatcher timeout or cancellation does not establish underlying
completion and must remain UNKNOWN. An exceptional stage or thrown exception is
also UNKNOWN, including the case where the database committed but cache publication
failed. No external typed NOT_APPLIED result is offered.

The existing `GuildInviteBlocks` registration remains separate, has the same
signatures and returns API version `1`. Its read methods remain memory-only. Its
setter now reports `true` only for confirmed SQL state plus cache publication;
`false` still cannot prove rollback. Existing consumers are not silently given
V2 completion or authoritative-read semantics.

## Storage scope and ownership

Only the SQLite storage dialect registers V2. The existing local database is
`lumaguilds.db` under the Guilds plugin data folder, with the directional block
stored in `player_invite_blocks` under the `(owner_id, target_id)` primary key.
The table was introduced by schema version 45; this correction needs no migration.

Consumers pin the registered provider together with their established
`backend:<server-id>` identity. The scope marker identifies a capability kind; it
does not derive or authenticate a backend ID, expose filesystem details, or identify
a shared database. Deployment must preserve the consumer's backend-to-store
association. A changed provider/store must be rediscovered and reconciled under
the consumer's existing workflow rules.

MariaDB continues to receive the common V1 SQL correction, but V2 is not advertised
there. Advertising shared storage later requires authoritative reads, coordination
of all participating provider caches, and a stable nonsecret store identity. This
change does not claim those properties.

Friends owns its durable per-pair/provider/scope generations and cleanup claims.
This API does not grant ownership of manual block changes, supply compare-and-set
generations, or fence an earlier invocation whose outcome is UNKNOWN. A fresh
authoritative read is evidence of observed committed state, not proof that an older
UNKNOWN invocation never applied or cannot need separate reconciliation.

## Transaction certainty and connection disposal

`InviteBlockSqlTransactions` records database certainty independently of cache and
cleanup. `commitAttempted` is set before calling JDBC `commit()`.

| SQL event | Internal outcome | Public V2 setter result |
| --- | --- | --- |
| No write was admitted, or failure before commit followed by acknowledged rollback | `NOT_APPLIED` | Exceptional, conservatively UNKNOWN. |
| JDBC commit returned normally | `COMMITTED` | Normal strictly-null completion only after cache reconciliation. |
| Commit was attempted and threw, even if later rollback returned normally | `UNKNOWN` | Exceptional UNKNOWN. |
| Write failure has an unconfirmed rollback, including when physical disposal also fails | `UNKNOWN` | Exceptional UNKNOWN. |
| Commit returned, then cache publication failed | `COMMITTED`, cache unreconciled | Exceptional UNKNOWN; the known commit is retained internally. |
| Commit returned, connection cleanup reported an error, and cache publication succeeded | `COMMITTED`, cache reconciled | Normal strictly-null completion; the cleanup problem is diagnosed separately. |

The release path restores `autoCommit=true` only after a confirmed commit or
confirmed rollback. A failed reset does not downgrade a known commit. An
acknowledged rollback before commit remains internally NOT_APPLIED even if its
later reset or disposal fails; the public failed completion is still UNKNOWN. An
unresolved lease is never logically closed or reset back to autocommit: doing so
could return a dirty connection to the pool and let an unrelated caller commit
leftover work.

The inspected dependencies are IDB `1.0.0-SNAPSHOT` (cache artifact hash directory
`278eca0d4780ed7124fa33bac3658913ed296be3`), HikariCP `5.1.0`, and the SQLite test
driver `3.45.1.0`. The source also uses MariaDB Connector/J `3.3.2`. The dependency
bytecode inspection established these relevant paths:

- IDB returns `DataSource.getConnection()` directly. Its data source field is not
  public and there is no public data source getter to obtain a pool owner.
- Hikari `ProxyConnection.unwrap(Connection.class)` exposes the physical delegate.
  Logical close attempts rollback and reset, but a failure path can still recycle
  the lease. Ordinary close alone is therefore not disposal proof after an
  unconfirmed rollback.
- SQLite JDBC `abort(Executor)` is a no-op. Physical close calls the SQLite database
  close path without restoring autocommit; attempting close is not enough if close
  throws or the connection remains open.

On the unresolved path, the helper unwraps the physical connection, calls physical
close, and checks `isClosed`. It retains the logical wrapper and never returns it
to the pool, including when unwrap, physical close or close verification fails.
This quarantine avoids reuse of an unproven dirty lease. Failed commit remains
UNKNOWN even after physical closure is confirmed. The rare quarantined lease
reduces pool capacity until shutdown/restart; no automatic recovery or background
retry is scheduled. Failed physical closure can leave the transaction unresolved
and hold a database lock, so consumers must retain their unresolved obligation.

Once a transaction is known resolved, a logical-close error is diagnosed without
retroactively closing the physical delegate: logical close may already have
returned that connection to another borrower. Its acknowledged commit is retained.

## Cache, invitation cleanup and lifecycle

V1 writes, V2 reads/writes and provider close use the same service monitor and the
same repository/cache. SQL writes and pair publication are also serialized in the
repository. V2 reads always query SQL and publish the observed pair state, including
both directions of a stale-cache mismatch. No successful read is manufactured from
the cache or from the repository availability flag.

Covered pending-invitation removal is synchronous best effort after the block
commit. A false removal return or ordinary cleanup exception is logged separately
and cannot falsely report that the block was rolled back. It is not part of the
promised durable block state, and no later cleanup retry is scheduled. If cache
publication fails after commit, V2 remains UNKNOWN even if invitation cleanup
finishes normally.

`close()` acquires the same monitor, waits for an admitted synchronous operation to
finish, and permanently closes that provider. `LumaGuilds.onDisable()` closes this
gate before any unrelated cleanup or service unregistration. Captured V2 references
then reject durable reads and return exceptional write stages; V1 writes return
false. A new enable constructs a new provider; the old provider is never reopened.

## Authored regression coverage and native handoff

`InviteBlockDurabilitySQLTest` uses the real IDB/Hikari storage fixture, SQLite by
default and the existing disposable loopback MariaDB profile when requested.
One-shot JDBC fault proxies wrap real statements and connections after repository
preload. They exercise stale-cache compensation in both directions, authoritative
reads, postcommit logical-close and reset failures, precommit statement-close
rollback, both possible states behind a thrown commit, failed rollback followed by
physical disposal, an unrelated pool borrower, and failed physical close without
logical pool return. Additional cases exercise committed SQL with failed cache
publication and strictly-null successful completion.

`InviteBlockCompletionServiceTest` covers cleanup exceptions/false returns, durable
read failure, captured closed providers and close draining an admitted write.
`GuildInviteBlocksRegistrationTest` checks the exact Java return type, version and
scope metadata, separate V1 registration, provider identity and the MariaDB V2
absence. The real Koin graph smoke test asserts that both interfaces resolve to
the identical service instance. The existing hand-written repository fake was
updated for the new internal methods without changing its V1 behavior.

The author has performed source inspection only. The validation owner should run
these commands with the already approved native dependency fixture and JVM settings:

```text
gradlew.bat test --tests "*InviteBlock*" --tests "*KoinGraphSmokeTest" --console=plain
gradlew.bat mariaDbRewardTest -PmariaDbTestPort=<disposable-loopback-port> --tests "*InviteBlockDurabilitySQLTest" --tests "*InviteBlockMigrationSQLTest" --console=plain
gradlew.bat check shadowJar --console=plain
```

The MariaDB fixture uses `lg.test.mariadb.port`, populated by the task from
`mariaDbTestPort`; it requires a nondefault loopback port, creates a random disposable
schema and drops that schema during teardown. The native runner may obtain its port
from its existing `GUILD_OWNERSHIP_TEST_MARIA_PORT` fixture and pass the Gradle
property. Ordinary tests remain SQLite. No production database should be used.

These source changes do not identify the previously loaded TEST bytecode. A JAR
filename is insufficient evidence, and the earlier TestBridge receipt does not map
this new implementation to an installed artifact. Installation/consumer acceptance
must use a fresh immutable build and loaded-artifact receipt for the reviewed commit.
