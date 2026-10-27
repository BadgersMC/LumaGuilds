# Optional live acceptance probe

Canonical main283d75f1 was fetched on October26. This continuation is stacked on the unchanged bank candidate125e4ae3 (PR243), in an isolated worktree. No product engine change is proposed. Project EARS/state helpers are absent; this plan and Roundtable provide manual SPEAR traceability. No historical failing product test is claimed for a new test helper.

## Specification

- AP-01: The production LumaGuilds artifact SHALL exclude all probe classes, configuration and commands. Only the explicit `acceptanceProbeJar` task packages the separate helper.
- AP-02: Every action SHALL require the actual console, explicit enabled configuration, matching TEST marker/environment/nonce, and the exact loaded Guilds JAR hash. Mutations additionally require a bounded unexpired window, only the configured online FainNeito UUID, and disposable guild `GoldProofOct26`.
- AP-03: Quest installation SHALL add only a5000-gold/500XP DEPOSIT_BANK definition to the current week. It SHALL NOT seed progress, award XP, grant gold, change an existing quest or finalize the old week.
- AP-04: Before installing the definition the tool SHALL durably journal the original week and definition fingerprint. Cleanup SHALL preserve all unrelated definitions and fail closed on drift. Startup, shutdown and expiry attempt cleanup; uncertain outcomes retain the journal for review. Fixture progress receipts remain retained evidence, not deleted to permit another claim.
- AP-05: Replay SHALL invoke the real contribution consumer using only the last observed canonical deposit identity from the configured actor/current disposable guild. No supplied SQL, reflection, arbitrary commands, reward grants or fake receipts are accepted.
- AP-06: Service lookup SHALL use the pinned Guilds plugin's Koin classloader through a Java `Class` entry point; Kotlin `KClass` and function types SHALL NOT cross the helper/provider lookup boundary. A different Koin owner SHALL fail closed.
- AP-07: A missing or false enabled flag SHALL disable the helper before event registration or task scheduling. An unresolved cleanup journal SHALL remain preserved for review; disabling the helper is not evidence of fixture restoration.

## Proof and architecture

`QuestFixturePlanTest` covers exact definition restoration, duplicate installation, week rollover and unrelated-definition drift. Four cases pass with zero failures/errors/skips. The adapter uses the Guilds composition root and public application/repository contracts; TestBridge configuration/marker are read only. It does not change TestBridge or companion code. The separate source set depends on the existing engine and never enters the production shadow artifact. Build requires the same pinned companion APIs as the candidate. Initial compilation failed because copied ignored APIs were nested one directory too deep; after correcting the local dependency paths the build and four tests passed. This is tooling setup evidence, not a Guilds regression.

## Operations proposed, not performed by this plan

Current TEST creation price0 and missing Chapter2 physical-cost flag prevent a paid-creation acceptance test. Propose a unique backup and exact temporary config diff: `guild.create_guild_cost=2000` and `progression.chapter_two_gold_costs_enabled=true`, preserving all other bytes. Restore the original bytes and reload afterward. The flag also enables home activation costs; no home action is in scope.

After native reconnect evidence, FainNeito may leave the TEST guild as explicitly authorized. If the leave path says sole owner, stop rather than silently disband. Use a disposable named guild. Test insufficient raw gold, duplicate-name reservation/refund, successful paid creation, quest partial progress, withdrawal/redeposit rejection, fresh completion, duplicate receipt and persisted state. Only TEST-issued funds are used and every charge reconciled.

The probe JAR requires a separately authorized TEST activation/restart; a second restart can exercise persistence and remove the helper. No production activation, global quest reroll, ranks, shared provider swap, server clock change or raw database write is authorized. Existing full-set definitions stay intact and no other TEST participant may be online during fixture actions. Client screenshots prove behavior separately from unit/build results. Market purchase fixtures require a real eligible sale/stock target; gateway-only calls must not be presented as player purchases.

Hosted CI/review and actual runtime activation remain separate evidence. No visual design changes are made; native acceptance screenshots are evidence rather than a new design preview.

## Native attempt and source correction

The authorized TEST attempt confirmed insufficient-funds rejection, duplicate-name refund, successful paid creation and restart persistence. The helper's original reified Koin lookup failed in the disposable-guild authorization gate with a `KClass` loader constraint. No quest definition or journal was installed. Thus the four original definition tests did not establish runtime compatibility. Original Guilds config was restored exactly; all Guilds, Market and Staff artifact hashes remained unchanged. Bloom denied helper file rename, so the old JAR remains installed with configuration disabled and commands verified denied. This source correction is not installed on TEST.

`ProbeClassLoaderTest` creates isolated provider/consumer loaders, each with its own Kotlin runtime. Its retained original-call control reproduces the actual loader constraint. The new Java entry point resolves the provider's binding with those same separate loaders. Wrong Koin owner and missing binding fail closed. The helper contains no shaded Kotlin/Koin libraries and performs no reflective service lookup. `ProbeDisabledStartupTest` executes the real startup method with controlled platform dependencies and verifies disablement before event registration/scheduling.

The optional helper version is now `0.1.1-UNMERGED-TEST`. `verifyAcceptanceProbePackaging` verifies that the production shadow JAR excludes all acceptance classes, the separate JAR contains its Java entry point, and that separate JAR embeds neither Kotlin nor Koin. The PR workflow now builds that helper and executes packaging verification alongside the normal tests. Publication, hosted approval and native acceptance remain separate gates.

Canonical main was fetched again and remains `283d75f1`; the existing helper branch continues on candidate `125e4ae3`. No project SPEAR/EARS helper was found. This specification, executable tests and Roundtable remain the manual requirement/task/evidence record. Test-harness corrections during this run included replacing a Java call to Koin's synthetic-only `declare` with a provider-local Kotlin fixture and verifying the plugin manager directly rather than counting its shared getter as an event registration. Those setup failures are not product regression results.

## Remaining live acceptance plan

Obtain a fresh matching TEST/client owner handoff before any activation. Identify the old retained helper and its disabled config, verify current provider hashes and quest week, then authorize the exact replacement/cleanup operations with working panel file permissions. Use only TEST-issued funds and the retained disposable guild; snapshot its current XP, bank and inventory before starting. The planned quest appends one definition to the current week, leaving all original quests intact.

Exercise fresh deposit progress, withdrawal and converted-payout redeposit, fresh completion, canonical receipt replay, then definition restoration and restart persistence. Capture actual quest progress and committed XP, not only chat or cached guild values. No direct progress/reward grants or SQL repair are part of the test. The old helper stays disabled until a reviewed replacement is explicitly activated.

For Market acceptance, first confirm the exact runtime and active XP policy. Prepare two disposable actors with separate buyer/seller membership and a disposable guild-owned stall, approved test stock and issued buyer funds. Select a price satisfying the actual minimum/cap policy. Complete a normal native purchase and reconcile buyer debit, seller guild-bank credit, stock and XP; repeat to check cooldown/caps, then test same-guild exclusion and a rejected purchase. Capture canonical sale IDs and verify replay cannot charge or award twice. Do not present a direct gateway call as a real purchase, modify an unrelated existing stall, or use another client's account without a fresh handoff. Market setup, stock/funds issuance and cleanup need concrete authorization; no unsupported command is assumed here.

## Release review status

PR243 remains draft at `125e4ae3`, stacked on PR241 (`5ccbec01`), with no human reviews or review threads returned. Exact-head GitHub build and four documentation checks pass. Codacy remains in progress with zero current annotations; the older summary reporting issues is not exact-head clearance. The connector rejects direct annotation endpoint reads; no permission bypass was used. Integration, approval, canonical merge, monorepo pins, native quest/Market acceptance and production activation remain open. The helper's earlier PR creation was denied by the GitHub integration; a published branch is not a created PR or hosted approval.

## Corrected helper local verification

Java25 / Paper26.2: `test mariaDbRewardTest verifyAcceptanceProbePackaging --offline`, with a new owned loopback MariaDB instance and exact Market runtime SHA256 `230504aa0243d0ff3145c5fb95723cbbd8db0ff01b60d39de54f57a21257c064`, passed. Full suite: **2319 tests, zero failures/errors, four existing skips**. Separate MariaDB reward profile: **95 tests, zero failures/errors/skips**. The full suite includes the four separate-loader cases, disabled-startup case, twelve actual Market gateway cases, twelve MariaDB bank-contribution cases and five MariaDB quest-contribution cases without skips. This is database/gateway contract proof, not native player purchases or completed live quest acceptance.

The new helper SHA256 is `14e9b93451d74a77050967589266726437f17e7efe46902f5bc4e51bb59e0517`. Production/helper packaging verification passed. The disposable database was shut down normally after the run; no shared provider instance was used. Wiki frontmatter validates42pages, topic parity validates13topics, strict MkDocs build passes, and `git diff --check` passes. Evidence: `operations/guilds-offline-readiness-20261026`, plus local `probe-loader-check.log`, `probe-native-full.log` and `probe-wiki-strict.log`. Hosted approval of this correction and the next native test window remain pending; no deployment, restart, client input or production mutation occurred in this source-only continuation.
