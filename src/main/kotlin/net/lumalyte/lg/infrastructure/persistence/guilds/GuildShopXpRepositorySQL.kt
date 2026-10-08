package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.application.services.GuildActionCoordinator
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
import net.lumalyte.lg.domain.entities.ExperienceAwardResult
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.domain.values.ExperiencePolicy
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** Persistent quotes and terminal decisions are deliberately not pruned with XP history. */
internal class GuildShopXpRepositorySQL(
    private val storage: Storage<Database>,
    private val awards: ExperienceAwardRepositorySQL,
    private val guildActions: GuildActionCoordinator = GuildActionCoordinator(),
) : GuildShopXpRepository {
    private companion object {
        const val PREPARED = "PREPARED"
        const val CAPPED = "CAPPED"
        const val MILLIS_PER_SECOND = 1000L
    }

    private val maria = storage.dialect == SqlDialect.MARIADB
    private val lock = if (maria) " FOR UPDATE" else ""

    init {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_shop_xp_sales (
                id VARCHAR(36) PRIMARY KEY, guild_id VARCHAR(36) NOT NULL, buyer_id VARCHAR(36) NOT NULL,
                occurred_at BIGINT NOT NULL, prestige_count INT NOT NULL, eligible INT NOT NULL,
                award_xp INT NOT NULL, guild_cap INT NOT NULL, buyer_cap INT NOT NULL,
                cooldown_ms BIGINT NOT NULL, status VARCHAR(32) NOT NULL
            )
            """.trimIndent(),
        )
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_shop_xp_pairs (
                guild_id VARCHAR(36) NOT NULL, buyer_id VARCHAR(36) NOT NULL, last_award BIGINT NOT NULL,
                PRIMARY KEY (guild_id, buyer_id)
            )
            """.trimIndent(),
        )
    }

    override fun prepare(
        sale: GuildShopXpSale,
        policy: GuildShopXpPolicy,
    ): String {
        val (id, guild, buyer, occurredAt) = sale
        require(occurredAt > 0)
        return guildActions.withGuilds(guild) {
            transaction { c ->
                lockGuild(c, guild)
                val existing = query(c, "SELECT * FROM guild_shop_xp_sales WHERE id = ?$lock", id.toString(), map = ::sale)
                if (existing != null) {
                    require(existing.guild == guild && existing.buyer == buyer && existing.at == occurredAt) { "Sale identity conflict" }
                    return@transaction PREPARED
                }
                val prestige =
                    query(c, "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?$lock", guild.toString()) { it.getInt(1) }
                        ?: error("Guild progression identity unavailable")
                val ownGuild =
                    query(c, "SELECT 1 FROM members WHERE guild_id = ? AND player_id = ?", guild.toString(), buyer.toString()) { true }
                        ?: false
                val status =
                    when {
                        ownGuild -> "OWN_GUILD"
                        !policy.enabled || policy.xpPerSale == 0 -> "DISABLED"
                        else -> PREPARED
                    }
                execute(
                    c,
                    "INSERT INTO guild_shop_xp_sales VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id.toString(),
                    guild.toString(),
                    buyer.toString(),
                    occurredAt,
                    prestige,
                    if (ownGuild) 0 else 1,
                    policy.xpPerSale,
                    policy.guildDailyCap,
                    policy.buyerDailyCap,
                    policy.pairCooldownSeconds * MILLIS_PER_SECOND,
                    status,
                )
                PREPARED
            }
        }
    }

    override fun complete(id: UUID): GuildShopXpCompletion {
        // Autocommit identity lookup must precede the MariaDB writer lock/snapshot.
        val identity = storage.connection.connection.use { c -> readIdentity(c, id) }
        return guildActions.withGuilds(identity) { transaction { c -> completeLocked(c, id, identity) } }
    }

    private fun readIdentity(
        c: Connection,
        id: UUID,
    ): UUID =
        query(c, "SELECT guild_id FROM guild_shop_xp_sales WHERE id = ?", id.toString()) {
            UUID.fromString(it.getString(1))
        } ?: error("Sale has not been prepared")

    private fun completeLocked(
        c: Connection,
        id: UUID,
        guild: UUID,
    ): GuildShopXpCompletion {
        val guildExists = lockGuild(c, guild, required = false)
        val sale =
            query(c, "SELECT * FROM guild_shop_xp_sales WHERE id = ?$lock", id.toString(), map = ::sale)
                ?: error("Sale disappeared")
        if (sale.status != PREPARED) return GuildShopXpCompletion(sale.status, guild)
        val denial = completionDenial(c, sale, guildExists)
        return if (denial == null) awardSale(c, id, sale) else finish(c, id, sale, denial)
    }

    private fun completionDenial(
        c: Connection,
        sale: Sale,
        guildExists: Boolean,
    ): String? {
        val prestige =
            query(
                c,
                "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?$lock",
                sale.guild.toString(),
            ) { it.getInt(1) }
        if (!guildExists || prestige != sale.prestige) return "STALE_RUN"
        val last =
            query(
                c,
                "SELECT last_award FROM guild_shop_xp_pairs WHERE guild_id = ? AND buyer_id = ?",
                sale.guild.toString(),
                sale.buyer.toString(),
            ) { it.getLong(1) }
        if (last != null && (sale.at < last || sale.at - last < sale.cooldown)) return "COOLDOWN"
        return if (sale.guildCap == 0 || sale.buyerCap == 0) CAPPED else null
    }

    private fun awardSale(
        c: Connection,
        id: UUID,
        sale: Sale,
    ): GuildShopXpCompletion {
        val at = Instant.ofEpochMilli(sale.at)
        val policy =
            ExperiencePolicy(
                ExperienceSource.SHOP_SALE,
                "SHOP_SALE",
                sale.xp,
                sale.guildCap,
                CapPeriod.DAILY,
                true,
            )
        val window = checkNotNull(policy.windowContaining(at))
        val buyerPool = "SHOP_BUYER:${sale.buyer}"
        val allowed = buyerAllowance(c, sale, buyerPool, window)
        if (allowed == 0) return finish(c, id, sale, CAPPED)
        val request =
            ExperienceAwardRequest(
                sale.guild,
                sale.buyer,
                ExperienceSource.SHOP_SALE,
                1,
                at,
                transactionId = id,
            )
        val result = awards.awardInTransaction(c, request, policy, allowed, window)
        return if (result is ExperienceAwardResult.Awarded) {
            reserveBuyer(c, sale, BuyerReservation(buyerPool, window, result.acceptedXp))
            finish(c, id, sale, "AWARDED:${result.acceptedXp}").copy(level = result.leveledUpTo, awardedNow = true)
        } else {
            finish(c, id, sale, CAPPED)
        }
    }

    private fun buyerAllowance(
        c: Connection,
        sale: Sale,
        pool: String,
        window: PeriodWindow,
    ): Int {
        val sql =
            "SELECT awarded_xp FROM guild_experience_source_usage " +
                "WHERE guild_id = ? AND source_pool = ? AND period_start = ?"
        val used = query(c, sql, sale.guild.toString(), pool, window.startInclusive.toEpochMilli()) { it.getInt(1) } ?: 0
        return sale.xp.coerceAtMost((sale.buyerCap - used).coerceAtLeast(0))
    }

    private fun reserveBuyer(
        c: Connection,
        sale: Sale,
        reservation: BuyerReservation,
    ) {
        val usageSeed =
            if (maria) {
                "ON DUPLICATE KEY UPDATE awarded_xp = awarded_xp + VALUES(awarded_xp)"
            } else {
                "ON CONFLICT(guild_id, source_pool, period_start) " +
                    "DO UPDATE SET awarded_xp = awarded_xp + excluded.awarded_xp"
            }
        val sql =
            "INSERT INTO guild_experience_source_usage " +
                "(guild_id, source_pool, period_start, period_end, awarded_xp) VALUES (?, ?, ?, ?, ?) $usageSeed"
        execute(
            c,
            sql,
            sale.guild.toString(),
            reservation.pool,
            reservation.window.startInclusive.toEpochMilli(),
            reservation.window.endExclusive.toEpochMilli(),
            reservation.xp,
        )
        val pairUpsert =
            if (maria) {
                "ON DUPLICATE KEY UPDATE last_award = VALUES(last_award)"
            } else {
                "ON CONFLICT(guild_id, buyer_id) DO UPDATE SET last_award = excluded.last_award"
            }
        execute(
            c,
            "INSERT INTO guild_shop_xp_pairs VALUES (?, ?, ?) $pairUpsert",
            sale.guild.toString(),
            sale.buyer.toString(),
            sale.at,
        )
    }

    private data class BuyerReservation(
        val pool: String,
        val window: PeriodWindow,
        val xp: Int,
    )

    private fun finish(
        c: Connection,
        id: UUID,
        sale: Sale,
        status: String,
    ): GuildShopXpCompletion {
        check(execute(c, "UPDATE guild_shop_xp_sales SET status = ? WHERE id = ? AND status = 'PREPARED'", status, id.toString()) == 1)
        return GuildShopXpCompletion(status, sale.guild)
    }

    private fun lockGuild(
        c: Connection,
        guild: UUID,
        required: Boolean = true,
    ): Boolean {
        val exists = query(c, "SELECT level FROM guilds WHERE id = ?$lock", guild.toString()) { true } ?: false
        check(exists || !required) { "Guild no longer exists" }
        return exists
    }

    private fun <T> transaction(block: (Connection) -> T): T =
        storage.connection.connection.use { c ->
            c.committingTransaction {
                if (!maria) execute(c, "UPDATE guild_shop_xp_sales SET status = status WHERE 0")
                block(c)
            }
        }

    private data class Sale(
        val guild: UUID,
        val buyer: UUID,
        val at: Long,
        val prestige: Int,
        val xp: Int,
        val guildCap: Int,
        val buyerCap: Int,
        val cooldown: Long,
        val status: String,
    )

    private fun sale(r: ResultSet) =
        Sale(
            UUID.fromString(r.getString("guild_id")),
            UUID.fromString(r.getString("buyer_id")),
            r.getLong("occurred_at"),
            r.getInt("prestige_count"),
            r.getInt("award_xp"),
            r.getInt("guild_cap"),
            r.getInt("buyer_cap"),
            r.getLong("cooldown_ms"),
            r.getString("status"),
        )

    private fun execute(
        c: Connection,
        sql: String,
        vararg args: Any?,
    ): Int =
        c.prepareStatement(sql).use { s ->
            args.forEachIndexed { i, v -> s.setObject(i + 1, v) }
            s.executeUpdate()
        }

    private fun <T> query(
        c: Connection,
        sql: String,
        vararg args: Any?,
        map: (ResultSet) -> T,
    ): T? =
        c.prepareStatement(sql).use { s ->
            args.forEachIndexed { i, v -> s.setObject(i + 1, v) }
            s.executeQuery().use { r -> if (r.next()) map(r) else null }
        }
}
