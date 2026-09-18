# LG-1214 reward purchase actions — SPEAR

REQ-050/092/093. Add Java and Bedrock purchase confirmation to the existing
catalog. Readiness remains gated by `progression.chapter_two_rewards_enabled`;
missing ownership/progression never initializes accounts. Migration remains separate.

Use a fresh server-side quote containing actor, guild, canonical reward/price,
ownership version and transaction UUID. Viewing/selecting/cancelling never spends.
Confirmation uses the existing atomic `GuildGoldService.purchaseReward` path.
Require current membership and both MANAGE_GUILD_SETTINGS and WITHDRAW_FROM_BANK
on a rank belonging to this guild. Recheck permission and frozen state at execution;
recheck the rollout gate before touching purchase storage, including after reload.

An uncertain result retains the identical request for retry, never a new charge
or speculative refund. Definitive rejection returns to a freshly read catalog.
Repeated confirmation must replay the same receipt. Bedrock callbacks schedule
Bukkit work on the server thread and ignore disconnected players. Localize every
confirmation, success, rejection and retry message. Preserve the legacy disabled UI.

Prove gate/permission changes, immutable quotes, cancellation, retry identity,
stale ownership and SQL integration. Run UI contracts, layer/localization/Koin
checks, full regression, shadowJar and the SQLite/MariaDB purchase contracts.
