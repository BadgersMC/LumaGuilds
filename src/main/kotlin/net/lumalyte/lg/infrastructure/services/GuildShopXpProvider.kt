package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.api.GuildShopXpApi
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import java.util.UUID

/** Publishes the JDK-only companion API and refreshes committed progression on the server thread. */
internal class GuildShopXpProvider(
    private val repository: GuildShopXpRepository,
    private val config: ConfigService,
    private val progression: ProgressionService,
    private val completedSale: (UUID, UUID) -> Unit = { _, _ -> },
) : GuildShopXpApi {
    override fun apiVersion(): Int = 2

    override fun prepare(saleId: UUID, owningGuildId: UUID, buyerId: UUID, occurredAtMillis: Long): String =
        repository.prepare(
            GuildShopXpSale(saleId, owningGuildId, buyerId, occurredAtMillis),
            config.loadConfig().progression.shopXp,
        )

    override fun abort(saleId: UUID): String = repository.abort(saleId).status

    override fun complete(saleId: UUID): String {
        val result = repository.complete(saleId)
        if (result.awardedNow || result.status.startsWith("AWARDED:")) {
            // A retained award repairs missed cache publication without replaying level-up effects.
            val level = result.level.takeIf { result.awardedNow }
            progression.onCommittedExperience(result.guildId, level)
        }
        if (result.status.startsWith("AWARDED:") || result.status in setOf("CAPPED", "COOLDOWN", "DISABLED")) {
            // The existing v2 contract only completes durably paid guild SELL purchases.
            // Achievement publication is non-financial and may never alter the retained API outcome.
            runCatching { completedSale(saleId, result.guildId) }.onFailure {
                java.util.logging.Logger.getLogger(GuildShopXpProvider::class.java.name)
                    .warning("Guild achievement sale publication failed for $saleId; retained sale outcome unchanged")
            }
        }
        return result.status
    }
}
