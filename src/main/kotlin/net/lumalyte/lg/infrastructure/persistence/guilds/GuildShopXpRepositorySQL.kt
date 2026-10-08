package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.time.Instant
import java.util.UUID

/** Persistent quotes and terminal decisions are deliberately not pruned with XP history. */
internal class GuildShopXpRepositorySQL(
    private val storage: Storage<Database>,
    private val awards: ExperienceAwardRepositorySQL,
    private val guildActions: GuildActionCoordinator = GuildActionCoordinator(),
) : GuildShopXpRepository {
    private val maria = storage.dialect == SqlDialect.MARIADB

    private val ledger = GuildShopXpLedger(storage)
    private val chapters = GuildShopXpChapterGuard(storage)

    override fun prepare(sale: GuildShopXpSale, policy: GuildShopXpPolicy): String {
        require(sale.occurredAt > 0)
        return guildActions.withGuilds(sale.guild) {
            transaction { c ->
                val chapter = chapters.current(c)
                ledger.lockGuild(c, sale.guild)
                val existing = ledger.read(c, sale.id)
                if (existing == null) {
                    val chapterAllowed = chapters.capture(c, sale.id, sale.occurredAt, chapter)
                    ledger.capture(c, sale, policy, chapterAllowed)
                } else {
                    require(
                        existing.guild == sale.guild && existing.buyer == sale.buyer && existing.at == sale.occurredAt,
                    ) {
                        "Sale identity conflict"
                    }
                }
                PREPARED
            }
        }
    }

    override fun complete(id: UUID): GuildShopXpCompletion {
        // Autocommit identity lookup must precede the MariaDB writer lock/snapshot.
        val identity = storage.connection.connection.use { c -> ledger.identity(c, id) }
        return guildActions.withGuilds(identity) { transaction { c -> completeLocked(c, id, identity) } }
    }

    private fun completeLocked(c: Connection, id: UUID, guild: UUID): GuildShopXpCompletion {
        val chapter = chapters.current(c)
        val guildExists = ledger.lockGuild(c, guild, required = false)
        val sale =
            ledger.read(c, id)
                ?: error("Sale disappeared")
        if (sale.status != PREPARED) return GuildShopXpCompletion(sale.status, guild)
        val denial = chapters.denial(c, id, chapter) ?: completionDenial(c, sale, guildExists)
        return if (denial == null) awardSale(c, id, sale) else ledger.finish(c, id, sale, denial)
    }

    private fun completionDenial(c: Connection, sale: GuildShopXpReceipt, guildExists: Boolean): String? {
        val prestige = ledger.prestige(c, sale.guild)
        return if (!guildExists || prestige != sale.prestige) {
            "STALE_RUN"
        } else {
            saleAllowanceDenial(c, sale)
        }
    }

    private fun saleAllowanceDenial(c: Connection, sale: GuildShopXpReceipt): String? {
        return when {
            sale.isCoolingDown(ledger.lastAward(c, sale)) -> "COOLDOWN"
            sale.guildCap == 0 || sale.buyerCap == 0 -> CAPPED
            else -> null
        }
    }

    private fun awardSale(c: Connection, id: UUID, sale: GuildShopXpReceipt): GuildShopXpCompletion {
        val policy = sale.experiencePolicy()
        val window = checkNotNull(policy.windowContaining(Instant.ofEpochMilli(sale.at)))
        val buyerPool = "SHOP_BUYER:${sale.buyer}"
        val allowed = ledger.buyerAllowance(c, sale, buyerPool, window)
        if (allowed == 0) return ledger.finish(c, id, sale, CAPPED)
        val request = sale.awardRequest(id)
        val result = awards.awardInTransaction(c, request, policy, allowed)
        return if (result is ExperienceAwardResult.Awarded) {
            ledger.reserveBuyer(c, sale, GuildShopXpLedger.BuyerReservation(buyerPool, window, result.acceptedXp))
            ledger.finish(
                c,
                id,
                sale,
                "AWARDED:${result.acceptedXp}",
            ).copy(level = result.leveledUpTo, awardedNow = true)
        } else {
            ledger.finish(c, id, sale, CAPPED)
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T {
        return storage.connection.connection.use { c ->
            c.committingTransaction {
                if (!maria) c.updateStatement("UPDATE guild_shop_xp_sales SET status = status WHERE 0")
                block(c)
            }
        }
    }

    private companion object {
        const val PREPARED = "PREPARED"
        const val CAPPED = "CAPPED"
    }
}
