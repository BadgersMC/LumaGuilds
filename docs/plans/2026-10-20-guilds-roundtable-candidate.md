# Guilds Roundtable integration candidate

## Scope and provenance

This review branch continues Codex session
`01a0ca20-8e1b-72b2-8236-bbd670689b51` through Roundtable project **GUILDS**.
It retains the combined unmerged source previously recorded on TEST, then adds
the remaining navigation, shop XP replay, and owner-recovery follow-ups. A second
dispatch adds atomic bank growth awards and current Market companion contracts.
The later GUILDS-10–12 dispatch adds durable new-deposit XP delivery and schema 47.
The verified checkpoint below is source-specific; later documentation or contract
changes do not inherit its artifact or executed-test identity.

| Component | Verified source |
| --- | --- |
| Canonical main at intake | `283d75f1e1016d415aa5afdfac840252f0391846` |
| Historical combined TEST source | `580b1fbf4708896f138a00ee7026e814fd3e81bc` |
| Historical combined source tree | `04cedf5908bd38bf2d3b64ff091461df5f8f36c1` |
| Navigation/dialog PR [#236](https://github.com/BadgersMC/LumaGuilds/pull/236) | `b1339efb067b3ec312022e8e4d192edc25f4f262` |
| Deposit/quest XP PR [#237](https://github.com/BadgersMC/LumaGuilds/pull/237) | `f2da62900e6974404cb1db2e02059d1b173f16c6` |
| API registration PR [#238](https://github.com/BadgersMC/LumaGuilds/pull/238) | `8a32cba4d36f419be0f83387963ffc5ed221835b` |
| Owner-recovery PR [#239](https://github.com/BadgersMC/LumaGuilds/pull/239) at intake | `b8270521a3fcb4fb0220a4f9c0f06195f9021ebd` |
| Navigation follow-up including test compilation correction | `e9ec42b77d75ab6d48a1ab4a4d920c5fb3b2fdf8` |
| Shop replay follow-up | `c406d067393d2272d36db3ef536a410dd2da55fb` |
| Owner-recovery analysis follow-up | `b2f2eb6a800df44c40552a44d8dad1a3377ee8b3` |
| First published and verified PR #240 candidate | `243370dd815845b06022ed15e12f924fa3cc79ef` |
| Atomic bank growth follow-up | `6efe3704112ca99da6a2cc524308f4da1deb4302` |
| Current Market contract follow-up | `aa41228965afe0c60cd2172d5b375fa43f18227d` |
| Paired Market combined candidate | `654db0eec2bcf5afa69e4791a271f4877c9bcac4` |
| Verified schema-47 delivery candidate | `0106651cb4c7857412e4c7fe410b80c04d0f5a7a` |

Git ancestry confirms that the historical combined source contains the pinned
heads of PRs #236, #237 and #238. It does not contain #239. Owner recovery is
therefore an explicit addition in this candidate. The large diff against main
includes the earlier unmerged recruitment, Chronicle and native-dialog stack;
those existing contributions must not be mistaken for changes authored during
this Roundtable run.

The October 18 session reported API v2 registration and synchronization of five
guild stalls after loading `3.0.0-shop-api-integrated-UNMERGED-TEST`. That is dated
startup evidence, not current artifact verification or successful purchase and
bank accounting acceptance. Standalone artifacts from #237 or #238 omit the
dialog stack and are not interchangeable with this combined source.

The inherited native dialog surfaces also depend on the existing private pack
PR #2 and its seven palettes. This follow-up does not change pack assets. Current
server/client pack identity remains part of the shared acceptance gate; previous
local renderer captures are not a new live verification.

## Work and review ownership

| Roundtable task | Work |
| --- | --- |
| GUILDS-01 | Coordinator: exact session recovery, source reconciliation and dependency record. |
| GUILDS-02 | Navigation worker: remaining parent returns, retained editor state and presentation defects. |
| GUILDS-03 | XP/shop worker: reproduced receipt replay cache gap, scoped repair and acceptance procedure. |
| GUILDS-04 | Owner-recovery worker: readable static-analysis follow-up with explicit retained findings. |
| GUILDS-05 | Deferred current-artifact and live TEST acceptance, coordinated with Market/KOTH. |
| GUILDS-06 | Coordinator: final integration, validation, independent review and draft PR evidence. |
| GUILDS-07 | Bank worker: reproduce reservation loss and commit bank high-water and XP together. |
| GUILDS-08 | Companion worker: execute the actual Market gateway and stall record contracts. |
| GUILDS-09 | Independent bank transaction and recovery-boundary review. |
| GUILDS-10 | Durable quote, canonical settlement, ordered XP delivery and acknowledged cache recovery. |
| GUILDS-11 | Independent SQLite/MariaDB delivery and publication contracts. |
| GUILDS-12 | Independent delivery implementation/contract review. |

Three native workers executed in each dispatch, using isolated checkouts. Roundtable records their
actual source acknowledgments and results; logical task seats are routing labels,
not a substitute for an executing worker. The coordinator owns remote operations,
combined builds and publishing. The owner-recovery worker independently reviewed
the XP repair and build wiring; the XP worker reviews navigation. The coordinator
reviews owner-recovery changes and integrates the exact contributions.
In the second dispatch, the bank reviewer challenges the transaction design and
reviews its immutable correction; the coordinator reviews the Market tests.

## Follow-up behavior

Menu returns restore the existing parent and carry edited child data before it
opens. Party drafts, moderation pages and rank icon drafts survive the return.
Guild selection redraws its page and clamps an emptied list. The remaining
recruitment Close labels use the shared fallback, and purchase review keeps the
reward and price without displaying its internal UUID as standalone content.
Independent review also caught denied party-moderation opens that left the old
detail visible; both denial paths now unwind to accessible party management.
The navigation contribution adds 21 regression cases. Recruitment filter width
was already fitted at the starting pin and did not need another layout change.

The shop provider now refreshes retained `AWARDED:n` receipts with a null level.
This repairs a missed cache publication while leaving durable XP, receipt identity
and level-up effects unchanged. The baseline SQL/cache regression ran under Java
25: persisted XP was 5, while replay left cached XP at 0. The one executed case
failed that assertion with no skips or errors at test-only commit
`a7723a49775e4afa8ca366f580a4858be5cdbcd0`.

The test task tracks both the path and content of `MARKET_RUNTIME_JAR`, and the
paired test treats blank opt-in as absent while rejecting a configured missing
file. These changes travel together. The API comment now includes the already
implemented `CHAPTER_FROZEN` terminal result; there is no API version change.

Owner recovery keeps both permissions, actor-bound single-use confirmation,
the 60-second lifetime, RCON rejection, serialized database checks, original
membership timestamp comparison and cache-after-commit behavior. Six of the
14 prior analysis findings have targeted refactors; eight have documented
dispositions. Only fresh hosted analysis can establish the new issue count.
No gate configuration or test assertions were weakened.

The earlier bank regression separately demonstrated that an XP SQL rollback left a
committed high-water reservation, making the same recorded growth ineligible on
retry. At test-only `09ab68bf1949a6196c7d32d49b11f3afd39792f0`, Java 25 executed
one case with one expected assertion failure and no skips, from 18:31:32 to
18:32:53 UTC. The correction locks the guild and commits high-water, cap usage,
progression and the XP receipt on one connection. A failed SQL write rolls that
group back. Non-awarding policy dispositions and the ordinary award service's
boost/overflow behavior are preserved. The bank contribution adds 25 cases when
the native profile is enabled; see the [atomicity note](2026-10-20-bank-xp-atomicity.md).

The [durable delivery follow-up](2026-10-20-bank-xp-delivery.md) now connects new
configured deposits to XP consumption with a retained quote and READY receipt.
True canonical APPLIED settlement assigns the guild sequence and makes delivery
READY in the same transaction. Consumption commits high-water, usage, progression,
XP audit and terminal decision together, then acknowledges the exact receipt only
after actual cache refresh. Currency operation, delivery and bank XP audit use the
same UUID. The startup timer independently retries bounded delivery/cache work.
Existing unquoted operations remain legacy; deposit events are not replayed and
historical compensation is not inferred. See the
[independent review](2026-10-20-bank-xp-delivery-review.md) for retained boundaries.

## Verified delivery candidate

The coordinator's retained `DELIVERY-CANDIDATE-VERIFIED.json` and
`DELIVERY-REMOTE-RECOVERY-CHECKPOINT.json` record the following completed checkpoint.
This documentation update reads those receipts; it runs no native or live tests.

| Evidence | Exact checkpoint |
| --- | --- |
| Guilds source / tree | `0106651cb4c7857412e4c7fe410b80c04d0f5a7a` / `6492dee6b43d532ffc780e68d48a44fabc27db6f` |
| Native execution | `delivery-full-native-v2`, Java 25 `check shadowJar`; 2026-10-20 20:25:52.725–20:30:16.036 UTC, exit 0, clean source before/after |
| Fresh JUnit result | 2,192 cases across 419 suites; 2,188 passed, four known skips, zero failures/errors |
| Executed native contracts | 49 MariaDB cases, including 23 bank delivery cases; 23 SQLite delivery and two publication cases; 16 paired Market cases, all without skips |
| Guilds review artifact | `LumaGuilds-3.0.0-roundtable-delivery-v2-20261020-UNMERGED-REVIEW.jar`; 26,048,600 bytes; SHA-256 `5410744243025939030554a5d670e0c0777e3cde8070d6840cd484fac40c5f17` |
| Paired Market source / artifact | `654db0eec2bcf5afa69e4791a271f4877c9bcac4`; 4,477,968 bytes; SHA-256 `230504aa0243d0ff3145c5fb95723cbbd8db0ff01b60d39de54f57a21257c064` |
| Hosted receipt at 20:39:15 UTC | `PR-240-DELIVERY-HOSTED-CHECKS.json`: six check runs passed at that head; Codacy reported no source issues. CodeRabbit skipped the draft and is not counted as a review. |

The four skips are the existing-schema rehearsal without an offline snapshot and
three unimplemented Bukkit quest-target fixtures. None of the configured MariaDB,
delivery or paired profiles skipped. Full command arguments, XML identities and
artifact checks remain in the coordinator's receipts. These results belong to
the source and artifacts above, not a later head or an installed TEST binary. The
[Market contract note](2026-10-20-guilds-market-contracts.md) separates API/classloader
proof from live payment, stock and durable Market queue execution.

## Remaining GUILDS-05 acceptance

Native verification and review at the checkpoint are complete. Continue from
current operational evidence rather than rerunning that work:

- Collect installed/loaded Guilds provenance, actual backend/schema, fixture rows
  and configuration read-only. Label evidence from the older installed build
  **OLD_BUILD**; it cannot pass acceptance for schema-47 delivery or the new menus.
- Resolve the verified rollback route, shared loaded-artifact and exact-player
  durable-state evidence owned by SHARED-02, private pack and paired runtime, and
  acknowledged TEST/client window. The coordinator owns those runtime decisions.
- Before startup, verify a consistent restore point covering affected Guilds,
  personal economy/inventory and Market state together with the exact old binary
  and configuration. Schema 47 is an additive same-database upgrade, but an old JAR
  alone is insufficient rollback evidence; mixed writers or resuming transient
  awarding against outstanding delivery receipts are unsupported. Do not use the
  incomplete cross-engine copy utility or rewind shared state while others write.
- Execute the existing bounded bank, quest, shop and menu journeys and agreed
  cleanup, recording exact operation/receipt IDs and external asset deltas. Both
  bank menus withdraw to personal Vault balances; a physical redeposit consumes
  separately available items. The
  [acceptance procedure](2026-10-20-guilds-xp-shop-acceptance.md) specifies that flow,
  schema-47 queries, expected delivery states and remaining fixture inputs.

PR #240 was recorded as a draft and unmerged at the checkpoint. No canonical merge,
production deployment, TEST deployment/restart, monorepo version-pin update or live
player mutation is performed by this documentation update.
