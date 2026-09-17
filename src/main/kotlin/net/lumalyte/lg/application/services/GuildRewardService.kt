package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.RewardStateRepository
import net.lumalyte.lg.domain.rewards.*
import java.util.UUID

/** Fresh, read-only resolution. Missing state never initializes an account or grants benefits. */
class GuildRewardService(
    private val repository: RewardStateRepository,
    catalog: RewardCatalog,
    private val settings: () -> RewardReadSettings
) {
    private val resolver = RewardEntitlementResolver(catalog)

    fun read(guildId: UUID): GuildRewardRead = try {
        val config = settings()
        if (!config.enabled) GuildRewardRead.Disabled
        else when (val state = repository.read(guildId)) {
            is RewardStateRead.Found -> GuildRewardRead.Available(state.level, state.snapshot.version,
                resolver.resolve(state.level, state.snapshot.ownership, config.bankCeiling, config.memberCapacity))
            RewardStateRead.Missing, RewardStateRead.Failed -> GuildRewardRead.Unavailable
        }
    } catch (_: Exception) {
        GuildRewardRead.Unavailable
    }

    /** Legacy consumers may fall back only when disabled, never when storage is unavailable. */
    fun entitlementsIfEnabled(guildId: UUID): RewardEntitlements? = when (val result = read(guildId)) {
        GuildRewardRead.Disabled -> null
        GuildRewardRead.Unavailable -> throw RewardStateUnavailableException()
        is GuildRewardRead.Available -> result.entitlements
    }
}
