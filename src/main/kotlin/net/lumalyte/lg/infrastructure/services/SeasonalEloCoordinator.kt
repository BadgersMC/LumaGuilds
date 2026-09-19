package net.lumalyte.lg.infrastructure.services

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.infrastructure.persistence.migrations.ChapterReadSQL
import net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalEloRepositorySQL
import net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.util.UUID

class SeasonalEloCoordinator(
    private val storage: Storage<Database>,
    private val configService: ConfigService,
) {
    fun currentRatedChapterId(): String? {
        val config = configService.loadConfig().seasonalElo
        if (!config.enabled) return null
        return storage.connection.connection.use { connection ->
            ChapterReadSQL(connection).current()
                ?.takeIf { it.phase == "SCHEDULED" }
                ?.id
        }
    }

    fun rateWar(
        warId: UUID,
        chapterId: String,
        firstGuildId: UUID,
        secondGuildId: UUID,
        firstScore: Double,
        secondScore: Double,
        ratedAt: Long = System.currentTimeMillis(),
    ): SeasonalWarRatingResult {
        val config = configService.loadConfig().seasonalElo
        if (!config.enabled) return SeasonalWarRatingResult.Ineligible
        return storage.connection.connection.use { connection ->
            SeasonalEloRepositorySQL(
                connection,
                storage.dialect == SqlDialect.MARIADB,
                config.settings(),
            ).rate(warId, chapterId, firstGuildId, secondGuildId, firstScore, secondScore, ratedAt)
        }
    }

    fun view(guildId: UUID): SeasonalEloView? {
        val config = configService.loadConfig().seasonalElo
        if (!config.enabled) return null
        return storage.connection.connection.use { connection ->
            val chapter = ChapterReadSQL(connection).current() ?: return@use null
            val repo = SeasonalEloRepositorySQL(
                connection,
                storage.dialect == SqlDialect.MARIADB,
                config.settings(),
            )
            val rating = repo.rating(chapter.id, guildId)
                ?: net.lumalyte.lg.domain.values.SeasonalElo.STARTING_RATING
            val level = net.lumalyte.lg.domain.values.SeasonalElo.displayLevel(
                rating,
                upper = config.upperDisplayRating,
            )
            val rank = repo.rank(chapter.id, guildId)
            val eligible = connection.prepareStatement(
                "SELECT current_level FROM guild_progression WHERE guild_id=?"
            ).use {
                it.setString(1, guildId.toString())
                it.executeQuery().use { rows -> rows.next() && rows.getInt(1) == 100 }
            } && chapter.phase == "SCHEDULED" && config.enabled
            SeasonalEloView(chapter.id, rating, level, rank, eligible)
        }
    }
}

data class SeasonalEloView(
    val chapterId: String,
    val rating: Int,
    val displayLevel: Int,
    val rank: Int?,
    val eligible: Boolean,
)
