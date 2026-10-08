# Guild stalls (SPEAR)

REQ-124/125, LG-2010. Sanitized rebuild based on current main after the reviewed #210 replacement. The old stacked #211 merge commit is intentionally excluded.

The guild dashboard and Bedrock control panel now include Guild Stalls; `/g stall` and `/g stalls` use the existing `lumaguilds.guild.menu` permission. Current members can view the guild's stalls and inspect location, state, stored rent/interval, UTC due/grace dates and members' guild-granted shop actions. Offline member names use Bukkit's known profile name, falling back to UUID. Read-only views neither teleport nor change payments, ownership or permissions. Staff overrides and other protection rules are explicitly identified as separate.

Market owns the versioned read service. Guilds discovers its public Bukkit service by name using the provider's classloader; no mandatory Market linkage or circular plugin dependency is introduced. Missing, incompatible, exceptional or malformed reads display Unavailable. A successful empty result displays No guild stalls. Refresh and pagination request fresh ownership, roster and permissions.

Persistence work runs in Market's IO executor; WorldGuard and menu operations run on the server scheduler. Java inventory-close callbacks invalidate navigation. The result also checks the actual loading inventory identity. Navigators share a per-live-player generation through synchronized weak keys, covering another command's Java inventory or Bedrock form without retaining disconnected players. Both platforms recheck navigation, connectivity and current membership before using asynchronous results or responses. A new separate-navigator regression failed before this shared generation was implemented.

Proof: the adapter contract initially failed compilation because its new implementation did not exist. The first actual inventory test exposed a late completion reopening a closed view. MockBukkit represents the closed view with a null top inventory; final tests check that actual representation rather than inventing a crafting inventory. Tests cover close, replacement inventory, membership departure, unavailable/empty/failure distinctions, the shared late-result guard, actual Cumulus scheduled response handling and the real companion API records loaded from a separate classloader. This is local adapter/UI proof, not live Java/Bedrock client acceptance.

Local checks: initial clean full test/shadowJar passed 1,548 tests; the full suite after the separate-navigator fix passed 1,549 tests, zero failures/errors, four skips. Java 25/Paper 26.2 and the real existing RoseChat/CombatLogX compile-only dependencies were used. Set `MARKET_API_JAR` to the companion API build for `GuildStallRuntimeContractTest`; without it that optional integration test is explicitly skipped. That profile was exercised locally against the actual Market artifact, not a copied interface fixture. LocaleContractTest covers the new `<placeholder>` keys. Final focused checks cover the subsequent display/callback/test refactoring; exact-head hosted results are a separate gate.

Project-local EARS/state helpers are absent; requirement/task/evidence records are maintained manually. Market's API depends on its PR #197 authority fixes. Both source PRs must reach canonical main, and the owning network pins and combined clean build must be reviewed before a future deployment. No production change, merge, upload or client acceptance is claimed here.

## Dedicated dashboard icon (REQ-126, LG-2011)

The Guild Stalls button uses the `lg_nav_stalls` Nexo item when the separately managed private pack provides it; otherwise the Java menu keeps its `OAK_SIGN` fallback. Economy remains on `lg_nav_economy`. This public repository contains only the item identifier and fallback behavior, not the proprietary texture, Nexo item definition, Bedrock texture, or any generator capable of reconstructing them.

Bedrock uses independent `bedrock.guild_stalls_icon_url` / `bedrock.guild_stalls_icon_path` settings. Both empty means the form remains text-only. Pack release, mapping, and client rendering are deployment concerns owned by the private resource-pack pipeline and require separate acceptance.

## Review follow-up (REQ-127)

The stall icon settings are independent from bank icon settings. Gradle fingerprints both `MARKET_API_JAR` path and contents so changing or replacing the optional companion artifact invalidates stale compatibility results. No Market mutation API is introduced, and no resource-pack bytes are stored by this repository.

## Separate shop XP policy

The owner approved configurable defaults on 7 October 2026: 5 XP per eligible outside sale, 500 XP/guild/UTC day, 50 XP/buyer-to-guild/UTC day and a five-minute pair cooldown across the guild's shops. Own-guild purchases must earn zero XP. Quantity/price do not multiply sale XP. These numbers are policy acceptance, not active XP behavior.

Implementation is a separate transaction contract: positive completed SELL payment to the stall's recorded owner guild, durable transaction identity/outbox, atomic cap/cooldown/consumption plus progression award, failed-read retry, and a progression-run guard. The owner clarified that completed purchases cannot be returned; a BUY-shop resale is a separate trade, awards no sale XP and does not undo the original purchase's XP. Automatic failed-trade compensation occurs before success and must earn zero XP; no refund grace delay or clawback is needed. Market's existing PostShopTransactionEvent lacks durable transaction identity and resolved guild ownership, so simply subscribing and calling awardExperience would not satisfy recovery or anti-farming requirements. No XP award hook is introduced by this menu PR.

## Hosted review refinement

Follow-up reduces unnecessary public API surface, extracts localized display formatting from navigation, and validates nullable companion values without unchecked casts. Invalid non-null values still produce Unavailable; they are never silently accepted as missing fields. Formatter changes are limited to the new stall files. Clean Java 25/Paper 26.2 test + shadowJar with the actual MARKET_API_JAR passed 1,550 tests, zero failures/errors, four unrelated skips. Hosted Codacy for this revised head remains a separate gate; no production or client acceptance is claimed.
