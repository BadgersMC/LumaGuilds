# Optional live acceptance probe

Canonical main283d75f1 was fetched on October26. This continuation is stacked on the unchanged bank candidate125e4ae3 (PR243), in an isolated worktree. No product engine change is proposed. Project EARS/state helpers are absent; this plan and Roundtable provide manual SPEAR traceability. No historical failing product test is claimed for a new test helper.

## Specification

- AP-01: The production LumaGuilds artifact SHALL exclude all probe classes, configuration and commands. Only the explicit `acceptanceProbeJar` task packages the separate helper.
- AP-02: Every action SHALL require the actual console, explicit enabled configuration, matching TEST marker/environment/nonce, and the exact loaded Guilds JAR hash. Mutations additionally require a bounded unexpired window, only the configured online FainNeito UUID, and disposable guild `GoldProofOct26`.
- AP-03: Quest installation SHALL add only a5000-gold/500XP DEPOSIT_BANK definition to the current week. It SHALL NOT seed progress, award XP, grant gold, change an existing quest or finalize the old week.
- AP-04: Before installing the definition the tool SHALL durably journal the original week and definition fingerprint. Cleanup SHALL preserve all unrelated definitions and fail closed on drift. Startup, shutdown and expiry attempt cleanup; uncertain outcomes retain the journal for review. Fixture progress receipts remain retained evidence, not deleted to permit another claim.
- AP-05: Replay SHALL invoke the real contribution consumer using only the last observed canonical deposit identity from the configured actor/current disposable guild. No supplied SQL, reflection, arbitrary commands, reward grants or fake receipts are accepted.

## Proof and architecture

`QuestFixturePlanTest` covers exact definition restoration, duplicate installation, week rollover and unrelated-definition drift. Four cases pass with zero failures/errors/skips. The adapter uses the Guilds composition root and public application/repository contracts; TestBridge configuration/marker are read only. It does not change TestBridge or companion code. The separate source set depends on the existing engine and never enters the production shadow artifact. Build requires the same pinned companion APIs as the candidate. Initial compilation failed because copied ignored APIs were nested one directory too deep; after correcting the local dependency paths the build and four tests passed. This is tooling setup evidence, not a Guilds regression.

## Operations proposed, not performed by this plan

Current TEST creation price0 and missing Chapter2 physical-cost flag prevent a paid-creation acceptance test. Propose a unique backup and exact temporary config diff: `guild.create_guild_cost=2000` and `progression.chapter_two_gold_costs_enabled=true`, preserving all other bytes. Restore the original bytes and reload afterward. The flag also enables home activation costs; no home action is in scope.

After native reconnect evidence, FainNeito may leave the TEST guild as explicitly authorized. If the leave path says sole owner, stop rather than silently disband. Use a disposable named guild. Test insufficient raw gold, duplicate-name reservation/refund, successful paid creation, quest partial progress, withdrawal/redeposit rejection, fresh completion, duplicate receipt and persisted state. Only TEST-issued funds are used and every charge reconciled.

The probe JAR requires a separately authorized TEST activation/restart; a second restart can exercise persistence and remove the helper. No production activation, global quest reroll, ranks, shared provider swap, server clock change or raw database write is authorized. Existing full-set definitions stay intact and no other TEST participant may be online during fixture actions. Client screenshots prove behavior separately from unit/build results. Market purchase fixtures require a real eligible sale/stock target; gateway-only calls must not be presented as player purchases.

Hosted CI/review and actual runtime activation remain separate evidence. No visual design changes are made; native acceptance screenshots are evidence rather than a new design preview.
