package net.lumalyte.lg.infrastructure.services

import co.aikar.idb.Database
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.War
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
        val now = System.currentTimeMillis()
        return storage.connection.connection.use { connection ->
            ChapterReadSQL(connection).current()
                ?.takeIf { chapter ->
                    chapter.phase == "SCHEDULED" &&
                        (chapter.startsAt == null || now >= chapter.startsAt) &&
                        (chapter.endsAt == null || now < chapter.endsAt)
                }
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

    fun rateResolvedWar(war: War): SeasonalWarRatingResult? {
        val chapterId = war.ratedChapterId ?: return null
        if (!war.isEnded) return null
        val ratedAt = war.endedAt?.toEpochMilli() ?: return null
        val draw = war.winner == null
        val firstScore = when {
            draw -> 0.5
            war.winner == war.declaringGuildId -> 1.0
            else -> 0.0
        }
        val secondScore = when {
            draw -> 0.5
            war.winner == war.defendingGuildId -> 1.0
            else -> 0.0
        }
        return rateWar(
            war.id,
            chapterId,
            war.declaringGuildId,
            war.defendingGuildId,
            firstScore,
            secondScore,
            ratedAt,
        )
    }

    fun markWarUnratedForChapterEnd(war: War, decidedAt: Long): SeasonalWarRatingResult? {
        val chapterId = war.ratedChapterId ?: return null
        val config = configService.loadConfig().seasonalElo
        return storage.connection.connection.use { connection ->
            SeasonalEloRepositorySQL(
                connection,
                storage.dialect == SqlDialect.MARIADB,
                config.settings(),
            ).decideUnrated(
                war.id,
                chapterId,
                "CHAPTER_ENDED_BEFORE_WAR_RESOLUTION",
                decidedAt,
            )
        }
    }

    fun isWarRatingSettled(warId: UUID): Boolean {
        val config = configService.loadConfig().seasonalElo
        return storage.connection.connection.use { connection ->
            SeasonalEloRepositorySQL(
                connection,
                storage.dialect == SqlDialect.MARIADB,
                config.settings(),
            ).isSettled(warId)
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
            }
            val now = System.currentTimeMillis()
            val insideChapterWindow =
                (chapter.startsAt == null || now >= chapter.startsAt) &&
                    (chapter.endsAt == null || now < chapter.endsAt)
            SeasonalEloView(
                chapter.id,
                rating,
                level,
                rank,
                eligible && chapter.phase == "SCHEDULED" && insideChapterWindow && config.enabled,
            )
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
