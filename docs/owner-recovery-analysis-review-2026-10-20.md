# GUILDS-04 owner recovery analysis follow-up

## Scope and status

This review follows PR [#239](https://github.com/BadgersMC/LumaGuilds/pull/239) at
`b8270521a3fcb4fb0220a4f9c0f06195f9021ebd`. It addresses the 14 informational findings
in the coordinator's `owner-recovery-codacy-style-issues.json` capture. Six findings
have readable production refactors; eight have deliberate retention dispositions.
These are source review dispositions, not a claim that a new hosted analysis passed.

The only implementation edits are:

- `GuildOwnerRecoveryCommand.kt`: separate sender eligibility from permission
  handling, and name the rejected-preview and offered-confirmation responses.
- `MemberOwnerRecoverySQL.kt`: separate membership integrity, individual rank-row
  validation, and timestamp text compatibility from recovery orchestration.

All recovery tests, fixtures, result types, ordinary member service/repository
paths, locale entries, and analysis configuration remain unchanged. No test is
split, renamed, removed, disabled, or newly suppressed.

## Finding dispositions

`Command` denotes `interaction/commands/GuildOwnerRecoveryCommand.kt`; `SQL` denotes
`infrastructure/persistence/guilds/MemberOwnerRecoverySQL.kt`. Test names refer to
their existing classes. Lines and reported measurements below are from the
review baseline, so they remain traceable after extraction moves source lines.

| Baseline issue ID | Location and finding | Disposition and reason |
| --- | --- | --- |
| `1aefba55045cde70e2bd8c53687c41ea` | `GuildOwnerRecoveryCommandTest`, line 1; file NLOC 389 | Retain. The 37 discovered cases exercise one actor-bound confirmation state machine using the real language bundle. Shared lifecycle, permissions, token clock, and audit assertions make the complete contract reviewable together. Splitting solely at a file-size threshold would add fixture coordination without improving the scenarios. |
| `a611e5b04822f43f14b321c08aea3832` | `MemberOwnerRecoveryMariaDbTest.writeFailurePreservesState`, line 96; anonymous NLOC 23 | Retain. The trigger setup, storage rejection, database and cache assertions, trigger removal, and successful retry establish one rollback contract. Keep fault injection beside its assertions. |
| `38e68009ee1914a561dcbd8f2bd681fd` | `SQL.readRanks` query lambda, line 112; complexity 7 | Refactor. `readRank` validates a single row's canonical UUID, non-null/nonblank name, and nonnegative integer priority. The result assembly still rejects duplicate normalized rank IDs before inserting them. Validation order and typed rejection are preserved. |
| `b196a425b3dc18e97453fb07eb72f49c` | `MemberOwnerRecoveryTest.normalChangesRemainAvailable`, line 298; anonymous NLOC 21 | Retain. Ordinary rank updates, recovery, transfer, member removal, and disband form a compatibility sequence against the same stored guild. Every operation and the final state remain visible in one scenario. |
| `e1af08c10ef96e94d44f5b2e5b2299b4` | `MemberOwnerRecoveryTest`, line 1; file NLOC 334 | Retain. All 14 SQLite recovery scenarios share one disposable fixture and each keeps its rejection or rollback contract independently runnable. There is no assertion or discovery reduction. |
| `6bae7cefac7004e8063df23a7d1db24a` | `SQL.parseJoinedAt`, line 183; complexity 6 | Refactor. Native `Timestamp` and `LocalDateTime` dispatch stays in `parseJoinedAt`; `parseJoinedAtText` preserves the ordered ISO-instant and SQL/local-date-time fallbacks. Parsing uses the same original JDBC object retained for the conditional update. Unsupported types and malformed text still reject. |
| `d5379911910c2b193b18fd93f670b331` | `MemberOwnerRecoveryTest.recoveryPreservesData`, line 66; anonymous NLOC 27 | Retain. This is the complete successful-write invariant: selected rank, cache, other member, exact date representation, member count, sole Owner, guild progress, fresh adapter, and repeat rejection. Removing or hiding assertions would weaken review. |
| `f64bc94436a333b1d1e975036835010d` | `Command.reject`, line 197; complexity 8 | Retain. The exhaustive typed `when` requires every rejection to have a literal translated response. A lookup map, catch-all branch, or dynamically assembled translation key would lose compiler exhaustiveness or source-scanner visibility. |
| `6dd77015304473b78182ce165bd6a0f4` | `Command.preview`, line 81; NLOC 21 | Refactor. Preview orchestration now dispatches to `reportPreviewRejection` or `offerConfirmation`. Both branches remain exhaustive single expressions. The confirmation helper preserves creation, pending-state publication, audit, preview text, and confirmation text in the original order. |
| `76c15955f481b67a00995fd466a3872c` | `MemberOwnerRecoveryMariaDbTest.guildRowsArePreserved`, line 112; anonymous NLOC 23 | Retain. The protective guild-update trigger, read-only preview, recovery, and progress assertions are one native-database contract. Keeping the trigger visible makes the protected side effect explicit. |
| `ea0ab19d9841f6b12d2faf8e8bc572f9` | `SQL.replaceRank` statement lambda, line 161; complexity 6 | Retain. The body has one explicit row-count conditional and five JDBC bindings. The SQL binds the selected Owner rank, exact player/guild pair, original rank, and original `joined_at` value. Keep those bindings adjacent to the statement and retain the exactly-one-row requirement. No SQL parameter or predicate is replaced to alter a metric. |
| `f22b7d0e1702f0c4731d8810c503a8d8` | `SQL.readState`, line 65; NLOC 21 | Refactor. `validateMemberRanks` names the membership-integrity preconditions: no duplicate normalized player IDs and no rank outside the guild's rank map. The recovery state assembly keeps ownership and selected-target checks together. |
| `da766e962168698de2bccb465188f64a` | `SQL.readState`, line 65; complexity 6 | Refactor. The same extraction removes membership-integrity branches from orchestration while preserving their order before existing-Owner and missing-target rejection. Every check still runs inside the original transaction and requested lock scope. |
| `ec4c522a60a81088b3dacfe105782b0e` | `Command.authorize`, line 54; `OptionalWhenBraces` | Refactor. An early return rejects unsupported senders, followed by the existing permission-denial expression. This removes the `when` construct that conflicted with `BracesOnWhenStatements`; both permission checks and pending-token removal remain explicit. |

The reported anonymous-method sizes are retained as analyzer evidence. They are
not presented as hand-counted lambda lengths or a demonstrated runtime defect.
No analysis gate, threshold, exclusion, or suppression is added by this follow-up.

## Invariant review

Sender identity is still restricted to an eligible player administrator or local
server console. The existing actor classification continues to reject RCON and
command blocks. Both permissions are checked before parsing or storage access;
denial removes the actor's pending recovery. The expiry sweep, exact 60-second
boundary, actor/target/token binding, replacement of an older preview, and
consume-before-storage confirmation path are unchanged.

All `lang.msg` keys and placeholder literals remain visible to
`LocaleSourceScanner`. The rejection mapping remains exhaustive, and audit
records preserve actor, guild, member, ranks, and outcomes without token logging.

The SQL recovery still acquires SQLite's zero-row writer reservation before
reading and uses MariaDB's existing `FOR UPDATE` queries. The refactor does not
move a query, validation, comparison, or write outside its transaction. Recovery
still validates the complete guild state, compares the current preview to the
approved snapshot, and conditionally updates exactly one rank field using the
original JDBC timestamp representation. Commit, rollback, cache publication,
ordinary transfer protections, and service invalidation are unchanged.

## Validation handoff

Local review checks cover diff whitespace, the 120-column source limit, untouched
test discovery, and preservation of the transaction, confirmation, localization,
and update-predicate code. They do not replace Kotlin compilation or runtime tests.

The coordinator owns native execution and hosted analysis. No Gradle task,
MariaDB instance, remote mutation, PR update, or deployment was launched by this
worker. The Codacy MCP analyze tool and local Lizard were unavailable; no tool was
installed. Hosted Codacy must be rerun for the final commit, and retained findings
must be reviewed under the existing policy rather than silently treated as passed.

Run the existing focused JVM selections with `test shadowJar --offline
--no-daemon --console=plain` and one `--tests` argument per class:

| Existing class selector (unchanged) | Baseline discovered cases |
| --- | ---: |
| `net.lumalyte.lg.interaction.commands.GuildOwnerRecoveryCommandTest` | 37 |
| `net.lumalyte.lg.infrastructure.persistence.guilds.MemberOwnerRecoveryTest` | 14 |
| `net.lumalyte.lg.infrastructure.services.MemberOwnerRecoveryServiceTest` | 15 |
| `net.lumalyte.lg.interaction.commands.LumaGuildsCommandTest` | 25 |
| `net.lumalyte.lg.infrastructure.i18n.LocaleContractTest` | 19 |

These selectors preserve the previous 110-case focused run. Since the SQL helper
changed, also include
`net.lumalyte.lg.infrastructure.persistence.guilds.MemberOwnershipTransferTest`.

Then, in the coordinator's serialized disposable loopback MariaDB window with
`GUILD_OWNERSHIP_TEST_MARIA_PORT` configured, run these unchanged native selectors:

- `net.lumalyte.lg.infrastructure.persistence.guilds.MemberOwnerRecoveryMariaDbTest`
  (6 existing recovery cases).
- `net.lumalyte.lg.infrastructure.persistence.guilds.MemberOwnershipMariaDbTest`
  (2 existing transfer cases).

The native tests must execute rather than report environment-related skips.
The pre-follow-up results belong to their original commits; this note does not
relabel those results as validation of the current refactor.
