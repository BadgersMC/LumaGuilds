# Guilds Roundtable integration candidate

## Scope and provenance

This review branch continues Codex session
`01a0ca20-8e1b-72b2-8236-bbd670689b51` through Roundtable project **GUILDS**.
It retains the combined unmerged source previously recorded on TEST, then adds
the remaining navigation, shop XP replay, and owner-recovery follow-ups. A second
dispatch adds atomic bank growth awards and current Market companion contracts.

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

The bank regression separately demonstrated that an XP SQL rollback left a
committed high-water reservation, making the same recorded growth ineligible on
retry. At test-only `09ab68bf1949a6196c7d32d49b11f3afd39792f0`, Java 25 executed
one case with one expected assertion failure and no skips, from 18:31:32 to
18:32:53 UTC. The correction locks the guild and commits high-water, cap usage,
progression and the XP receipt on one connection. A failed SQL write rolls that
group back. Non-awarding policy dispositions and the ordinary award service's
boost/overflow behavior are preserved. The bank contribution adds 25 cases when
the native profile is enabled; see the [atomicity note](2026-10-20-bank-xp-atomicity.md).

## Validation and remaining gates

The coordinator records the final candidate commit/tree, executed test counts,
JUnit results and JAR checksum with the PR and Roundtable task. Source review,
ordinary tests, native database contracts, paired artifact tests and live player
acceptance are distinct results. A skipped external profile is not a passing
compatibility test.

The first candidate at `243370dd` passed 2,103 cases across 414 suites: 2,097
passed, six skipped and no failures or errors. Its 17 native MariaDB contracts
executed. Those results are historical evidence for that exact candidate.

The second dispatch's current-artifact profile passed all 16 gateway/stall cases,
with no failures, errors or skips, at Guilds `aa41228965afe0c60cd2172d5b375fa43f18227d`
on October 20, 18:42:51–18:43:22 UTC. Both Market artifact variables pointed to the
same independently copied and hash-verified combined JAR from `654db0eec2bc`:
4,477,968 bytes, SHA-256
`230504aa0243d0ff3145c5fb95723cbbd8db0ff01b60d39de54f57a21257c064`.
The [contract note](2026-10-20-guilds-market-contracts.md) distinguishes actual
classloader/API coverage from Market payment, stock and durable queue execution.

- Run the combined Java 25 test/build profile and inspect the new navigation,
  replay, bank and owner-recovery cases at the final integrated head.
- Run the nine bank, six recovery and two transfer contracts, plus the nine shop contracts,
  on the coordinator's dedicated disposable loopback MariaDB fixture.
- Include the pinned current Market artifact in the final combined run. The
  earlier two-case Market-owned result applies to `243370dd`; the new 16-case
  result applies to `aa412289`. Neither replaces final integrated verification.
- Verify current TEST plugin, backend, pack and fixture state before requesting
  a bounded shared resource window. GUILDS-05 remains pending until overlapping
  owners acknowledge that window; no reply does not establish ownership.
- Execute and record the bounded purchase, bank, quest and menu journeys, including
  before/after currency, stock, bank, XP and receipts. See the
  [XP/shop acceptance procedure](2026-10-20-guilds-xp-shop-acceptance.md).

Bank currency credit remains separate from the now-atomic high-water/XP group.
Missed post-credit callbacks, original operation/time/run identity, event delivery
and startup currency reconciliation still need a durable recovery design. The
correction preserves a same-snapshot retry; it does not automatically replay old
deposits or authorize compensating awards. Shared TEST acceptance still requires
current installed binary/rollback and backend/pack evidence. Market withdrew its
earlier ungranted TEST window while those external prerequisites remain unresolved.

This is a draft review candidate. Canonical merge, production deployment, TEST
deployment/restart, monorepo version-pin updates and live player mutations are not
performed by this integration record.
