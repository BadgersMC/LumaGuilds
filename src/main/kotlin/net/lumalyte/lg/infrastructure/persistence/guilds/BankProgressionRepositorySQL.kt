package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.application.persistence.BankProgressionRepository
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardRules
import net.lumalyte.lg.domain.values.PeriodWindow
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.util.UUID

class BankProgressionRepositorySQL(
    private val storage: Storage<Database>,
) : BankProgressionRepository {
    private val mariaDb = storage.javaClass.simpleName.contains("MariaDB")

    init {
        storage.connection.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS guild_bank_xp_high_water (
                guild_id VARCHAR(36) NOT NULL,
                period_start BIGINT NOT NULL,
                period_end BIGINT NOT NULL,
                high_water_balance BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (guild_id, period_start)
            )
            """.trimIndent()
        )
    }

    override fun reserveNetNewUnits(
        guildId: UUID,
        currentBalance: Long,
        valuePerUnit: Long,
        window: PeriodWindow,
    ): Int = storage.connection.connection.use { connection ->
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            execute(
                connection,
                seedSql(),
                guildId.toString(),
                window.startInclusive.toEpochMilli(),
                window.endExclusive.toEpochMilli(),
            )
            val previous = connection.prepareStatement(
                "SELECT high_water_balance FROM guild_bank_xp_high_water WHERE guild_id = ? AND period_start = ?${if (mariaDb) " FOR UPDATE" else ""}"
            ).use { statement ->
                statement.setString(1, guildId.toString())
                statement.setLong(2, window.startInclusive.toEpochMilli())
                statement.executeQuery().use { results ->
                    check(results.next()) { "Bank high-water row was not created" }
                    results.getLong("high_water_balance")
                }
            }
            val units = ChapterTwoGuildAwardRules.netNewBankUnits(previous, currentBalance, valuePerUnit)
            if (currentBalance > previous) {
                execute(
                    connection,
                    "UPDATE guild_bank_xp_high_water SET period_end = ?, high_water_balance = ? WHERE guild_id = ? AND period_start = ?",
                    window.endExclusive.toEpochMilli(),
                    currentBalance,
                    guildId.toString(),
                    window.startInclusive.toEpochMilli(),
                )
            }
            connection.commit()
            units
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun execute(connection: Connection, sql: String, vararg parameters: Any?): Int =
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }

    private fun seedSql(): String = if (mariaDb) {
        """
        INSERT INTO guild_bank_xp_high_water
            (guild_id, period_start, period_end, high_water_balance)
        VALUES (?, ?, ?, 0)
        ON DUPLICATE KEY UPDATE period_end = VALUES(period_end)
        """.trimIndent()
    } else {
        """
        INSERT INTO guild_bank_xp_high_water
            (guild_id, period_start, period_end, high_water_balance)
        VALUES (?, ?, ?, 0)
        ON CONFLICT(guild_id, period_start) DO UPDATE SET period_end = excluded.period_end
        """.trimIndent()
    }
}
