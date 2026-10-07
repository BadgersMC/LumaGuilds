# Guild stalls (SPEAR)

REQ-124/125, LG-2010. Base: freshly fetched canonical main `a15b244e8a294bf18e6dedf722462edf9faa40ae` in an isolated branch. Existing user work and PR #210 remain separate.

The guild dashboard and Bedrock control panel now include Guild Stalls; `/g stall` and `/g stalls` use the existing `lumaguilds.guild.menu` permission. Current members can view the guild's stalls and inspect location, state, stored rent/interval, UTC due/grace dates and members' guild-granted shop actions. Offline member names use Bukkit's known profile name, falling back to UUID. Read-only views neither teleport nor change payments, ownership or permissions. Staff overrides and other protection rules are explicitly identified as separate.

Market owns the versioned read service. Guilds discovers its public Bukkit service by name using the provider's classloader; no mandatory Market linkage or circular plugin dependency is introduced. Missing, incompatible, exceptional or malformed reads display Unavailable. A successful empty result displays No guild stalls. Refresh and pagination request fresh ownership, roster and permissions.

Persistence work runs in Market's IO executor; WorldGuard and menu operations run on the server scheduler. Java inventory-close callbacks invalidate navigation. The result also checks the actual loading inventory identity, covering a different command that uses a separate MenuNavigator. Both platforms recheck navigation, connectivity and current membership before using asynchronous results or responses.

Proof: the adapter contract initially failed compilation because its new implementation did not exist. The first actual inventory test exposed a late completion reopening a closed view. MockBukkit represents the closed view with a null top inventory; final tests check that actual representation rather than inventing a crafting inventory. Tests cover close, replacement inventory, membership departure, unavailable/empty/failure distinctions, the shared late-result guard, actual Cumulus scheduled response handling and the real companion API records loaded from a separate classloader. This is local adapter/UI proof, not live Java/Bedrock client acceptance.

Local checks: clean full test/shadowJar uses Java 25/Paper 26.2 and the real existing RoseChat/CombatLogX compile-only dependencies. Set `MARKET_API_JAR` to the companion API build for `GuildStallRuntimeContractTest`; without it that optional integration test is explicitly skipped. That profile was exercised locally against the actual Market artifact, not a copied interface fixture. LocaleContractTest covers the new `<placeholder>` keys. Final counts and exact-head hosted results are recorded with the task/PR evidence.

Project-local EARS/state helpers are absent; requirement/task/evidence records are maintained manually. Market's API depends on its PR #197 authority fixes. Both source PRs must reach canonical main, and the owning network pins and combined clean build must be reviewed before a future deployment. No production change, merge, upload or client acceptance is claimed here.

## Separate shop XP policy

The owner approved configurable defaults on 7 October 2026: 5 XP per eligible outside sale, 500 XP/guild/UTC day, 50 XP/buyer-to-guild/UTC day and a five-minute pair cooldown across the guild's shops. Own-guild purchases must earn zero XP. Quantity/price do not multiply sale XP. These numbers are policy acceptance, not active XP behavior.

Implementation is a separate transaction contract: positive completed SELL payment to the stall's recorded owner guild, durable transaction identity/outbox, atomic cap/cooldown/consumption plus progression award, failed-read retry, and a progression-run guard. Refund behavior is awaiting the owner's answer. Market's existing PostShopTransactionEvent lacks durable transaction identity and resolved guild ownership, so simply subscribing and calling awardExperience would not satisfy recovery or anti-farming requirements. No XP award hook is introduced by this menu PR.
