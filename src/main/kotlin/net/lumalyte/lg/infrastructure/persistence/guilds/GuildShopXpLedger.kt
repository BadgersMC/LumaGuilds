package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpSale
import net.lumalyte.lg.domain.entities.ExperienceAwardRequest
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

/** Durable quotes, pair usage and terminal receipts; the repository owns locking and commit. */
internal class GuildShopXpLedger(private val storage: Storage<Database>) {
    private val maria = storage.dialect == SqlDialect.MARIADB
    private val lock = if (maria) " FOR UPDATE" else ""
    init {
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_shop_xp_sales ( " +
                "id VARCHAR(36) PRIMARY KEY, guild_id VARCHAR(36) NOT NULL, buyer_id VARCHAR(36) NOT NULL, " +
                "occurred_at BIGINT NOT NULL, prestige_count INT NOT NULL, eligible INT NOT NULL, " +
                "award_xp INT NOT NULL, guild_cap INT NOT NULL, buyer_cap INT NOT NULL, " +
                "cooldown_ms BIGINT NOT NULL, status VARCHAR(32) NOT NULL " +
                ") ",
        )
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_shop_xp_pairs ( " +
                "guild_id VARCHAR(36) NOT NULL, buyer_id VARCHAR(36) NOT NULL, last_award BIGINT NOT NULL, " +
                "PRIMARY KEY (guild_id, buyer_id) " +
                ") ",
        )
    }

    fun read(c: Connection, id: UUID): GuildShopXpReceipt? =
        c.selectOne("SELECT * FROM guild_shop_xp_sales WHERE id = ?$lock", id.toString(), mapper = ::readReceipt)

    fun identity(c: Connection, id: UUID): UUID {
        return c.selectOne("SELECT guild_id FROM guild_shop_xp_sales WHERE id = ?", id.toString()) {
            UUID.fromString(it.getString(1))
        } ?: error("Sale has not been prepared")
    }

    fun prestige(c: Connection, guild: UUID): Int? {
        return c.selectOne(
            "SELECT prestige_count FROM guild_reward_accounts WHERE guild_id = ?$lock",
            guild.toString(),
        ) {
            it.getInt(1)
        }
    }

    fun lastAward(c: Connection, receipt: GuildShopXpReceipt): Long? {
        return c.selectOne(
            "SELECT last_award FROM guild_shop_xp_pairs WHERE guild_id = ? AND buyer_id = ?",
            receipt.guild.toString(),
            receipt.buyer.toString(),
        ) { it.getLong(1) }
    }

    fun capture(c: Connection, sale: GuildShopXpSale, policy: GuildShopXpPolicy, chapterAllowed: Boolean) {
        val prestige = prestige(c, sale.guild) ?: error("Guild progression identity unavailable")
        val own = c.isGuildMember(sale)
        val status =
            when {
                own -> "OWN_GUILD"
                policy.disabled() -> "DISABLED"
                !chapterAllowed -> "CHAPTER_FROZEN"
                else -> "PREPARED"
            }
        insert(c, sale, policy, Capture(status, own, prestige))
    }

    private fun insert(c: Connection, sale: GuildShopXpSale, policy: GuildShopXpPolicy, capture: Capture) {
        c.updateStatement(
            "INSERT INTO guild_shop_xp_sales VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            sale.id.toString(), sale.guild.toString(), sale.buyer.toString(), sale.occurredAt, capture.prestige,
            if (capture.own) 0 else 1, policy.xpPerSale, policy.guildDailyCap, policy.buyerDailyCap,
            policy.pairCooldownSeconds * MILLIS_PER_SECOND, capture.status,
        )
    }

    private data class Capture(val status: String, val own: Boolean, val prestige: Int)

    fun buyerAllowance(c: Connection, sale: GuildShopXpReceipt, pool: String, window: PeriodWindow): Int {
        val sql =
            "SELECT awarded_xp FROM guild_experience_source_usage " +
                "WHERE guild_id = ? AND source_pool = ? AND period_start = ?"
        val used =
            c.selectOne(sql, sale.guild.toString(), pool, window.startInclusive.toEpochMilli()) { it.getInt(1) } ?: 0
        return sale.xp.coerceAtMost((sale.buyerCap - used).coerceAtLeast(0))
    }

    fun reserveBuyer(c: Connection, sale: GuildShopXpReceipt, reservation: BuyerReservation) {
        val sql = buyerReservationSql(maria)
        c.updateStatement(
            sql,
            sale.guild.toString(),
            reservation.pool,
            reservation.window.startInclusive.toEpochMilli(),
            reservation.window.endExclusive.toEpochMilli(),
            reservation.xp,
        )
        c.reservePair(sale, maria)
    }

    fun finish(c: Connection, id: UUID, sale: GuildShopXpReceipt, status: String): GuildShopXpCompletion {
        check(
            c.updateStatement(
                "UPDATE guild_shop_xp_sales SET status = ? WHERE id = ? AND status = 'PREPARED'",
                status,
                id.toString(),
            ) ==
                1,
        )
        return GuildShopXpCompletion(status, sale.guild)
    }

    fun lockGuild(c: Connection, guild: UUID, required: Boolean = true): Boolean {
        val exists = c.selectOne("SELECT level FROM guilds WHERE id = ?$lock", guild.toString()) { true } ?: false
        check(exists || !required) { "Guild no longer exists" }
        return exists
    }

    data class BuyerReservation(val pool: String, val window: PeriodWindow, val xp: Int)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

internal data class GuildShopXpReceipt(
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

internal fun GuildShopXpReceipt.experiencePolicy(): ExperiencePolicy =
    ExperiencePolicy(ExperienceSource.SHOP_SALE, "SHOP_SALE", xp, guildCap, CapPeriod.DAILY, true)

internal fun GuildShopXpReceipt.awardRequest(id: UUID): ExperienceAwardRequest {
    return ExperienceAwardRequest(
        guild,
        buyer,
        ExperienceSource.SHOP_SALE,
        1,
        Instant.ofEpochMilli(at),
        transactionId = id,
    )
}

internal fun GuildShopXpReceipt.isCoolingDown(last: Long?): Boolean =
    last != null && (at < last || at - last < cooldown)

private fun readReceipt(r: ResultSet): GuildShopXpReceipt {
    return GuildShopXpReceipt(
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
}

private fun GuildShopXpPolicy.disabled(): Boolean = !enabled || xpPerSale == 0

private fun Connection.reservePair(sale: GuildShopXpReceipt, maria: Boolean) {
    val pairUpsert =
        if (maria) {
            "ON DUPLICATE KEY UPDATE last_award = VALUES(last_award)"
        } else {
            "ON CONFLICT(guild_id, buyer_id) DO UPDATE SET last_award = excluded.last_award"
        }
    updateStatement(
        "INSERT INTO guild_shop_xp_pairs VALUES (?, ?, ?) $pairUpsert",
        sale.guild.toString(),
        sale.buyer.toString(),
        sale.at,
    )
}

private fun Connection.isGuildMember(sale: GuildShopXpSale): Boolean {
    return selectOne(
        "SELECT 1 FROM members WHERE guild_id = ? AND player_id = ?",
        sale.guild.toString(),
        sale.buyer.toString(),
    ) { true } ?: false
}

private fun buyerReservationSql(maria: Boolean): String {
    val suffix =
        if (maria) {
            "ON DUPLICATE KEY UPDATE awarded_xp = awarded_xp + VALUES(awarded_xp)"
        } else {
            "ON CONFLICT(guild_id, source_pool, period_start) " +
                "DO UPDATE SET awarded_xp = awarded_xp + excluded.awarded_xp"
        }
    return "INSERT INTO guild_experience_source_usage " +
        "(guild_id, source_pool, period_start, period_end, awarded_xp) VALUES (?, ?, ?, ?, ?) $suffix"
}
