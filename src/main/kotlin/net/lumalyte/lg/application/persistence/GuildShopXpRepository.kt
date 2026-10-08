package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import java.util.UUID

data class GuildShopXpCompletion(val status: String, val guildId: UUID, val level: Int? = null, val awardedNow: Boolean = false)

interface GuildShopXpRepository {
    fun prepare(id: UUID, guild: UUID, buyer: UUID, occurredAt: Long, policy: GuildShopXpPolicy): String
    fun complete(id: UUID): GuildShopXpCompletion
}
