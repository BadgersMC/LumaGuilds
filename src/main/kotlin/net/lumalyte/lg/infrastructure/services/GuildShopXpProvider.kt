package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.GuildShopXpApi
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import java.util.UUID

class GuildShopXpProvider(
    private val repository: GuildShopXpRepository,
    private val config: ConfigService,
    private val progression: ProgressionService,
) : GuildShopXpApi {
    override fun apiVersion(): Int = 1
    override fun prepare(saleId: UUID, owningGuildId: UUID, buyerId: UUID, occurredAtMillis: Long): String =
        repository.prepare(saleId, owningGuildId, buyerId, occurredAtMillis, config.loadConfig().progression.shopXp)

    override fun complete(saleId: UUID): String {
        val result = repository.complete(saleId)
        if (result.awardedNow || result.status.startsWith("AWARDED:")) progression.onCommittedExperience(result.guildId, result.level)
        return result.status
    }
}
