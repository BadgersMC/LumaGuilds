package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.domain.values.SeasonalElo
import net.lumalyte.lg.domain.values.SeasonalEloSettings
import java.sql.Connection
import java.util.UUID

sealed interface SeasonalWarRatingResult {
    data class Rated(
        val firstBefore: Int,
        val secondBefore: Int,
        val firstAfter: Int,
        val secondAfter: Int,
    ) : SeasonalWarRatingResult
    data object RematchGuarded : SeasonalWarRatingResult
    data object Ineligible : SeasonalWarRatingResult
    data object Frozen : SeasonalWarRatingResult
    data object Replayed : SeasonalWarRatingResult
}

class SeasonalEloRepositorySQL(
    private val connection: Connection,
    private val mariaDb: Boolean,
    private val settings: SeasonalEloSettings,
) {
    fun rate(
        warId: UUID,
        chapterId: String,
        firstGuildId: UUID,
        secondGuildId: UUID,
        firstScore: Double,
        secondScore: Double,
        ratedAt: Long,
    ): SeasonalWarRatingResult {
        require(firstGuildId != secondGuildId)
        require(firstScore in 0.0..1.0 && secondScore in 0.0..1.0)
        require(kotlin.math.abs((firstScore + secondScore) - 1.0) < 1e-9) {
            "Rated war scores must describe one complementary outcome"
        }
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            lockChapter(chapterId)
            val chapter = chapterWindow(chapterId)
            if (resultExists(warId) || decisionExists(warId)) {
                connection.rollback()
                SeasonalWarRatingResult.Replayed
            } else if (chapter.phase != "SCHEDULED") {
                connection.rollback()
                SeasonalWarRatingResult.Frozen
            } else if (!chapter.contains(ratedAt)) {
                insertDecision(warId, chapterId, "OUTSIDE_ACTIVE_INTERVAL", ratedAt)
                connection.commit()
                SeasonalWarRatingResult.Ineligible
            } else if (level(firstGuildId) != 100 || level(secondGuildId) != 100) {
                insertDecision(warId, chapterId, "INELIGIBLE_LEVEL", ratedAt)
                connection.commit()
                SeasonalWarRatingResult.Ineligible
            } else {
                ensureRating(chapterId, firstGuildId, ratedAt)
                ensureRating(chapterId, secondGuildId, ratedAt)
                val lower = minOf(firstGuildId.toString(), secondGuildId.toString())
                val higher = maxOf(firstGuildId.toString(), secondGuildId.toString())
                val lastRatedAt = pairLastRatedAt(chapterId, lower, higher)
                if (lastRatedAt != null && ratedAt - lastRatedAt < settings.rematchWindowMillis) {
                    insertDecision(warId, chapterId, "REMATCH_GUARDED", ratedAt)
                    connection.commit()
                    SeasonalWarRatingResult.RematchGuarded
                } else {
                    val firstBefore = requiredRating(chapterId, firstGuildId)
                    val secondBefore = requiredRating(chapterId, secondGuildId)
                    val (firstAfter, secondAfter) = SeasonalElo.calculate(
                        firstBefore, secondBefore, firstScore, secondScore,
                        settings.kFactor, SeasonalElo.FLOOR_RATING,
                    )
                    updateRating(chapterId, firstGuildId, firstAfter, ratedAt)
                    updateRating(chapterId, secondGuildId, secondAfter, ratedAt)
                    upsertPair(chapterId, lower, higher, warId, ratedAt)
                    insertResult(
                        warId, chapterId, firstGuildId, secondGuildId,
                        firstBefore, secondBefore, firstAfter, secondAfter,
                        firstScore, secondScore, ratedAt,
                    )
                    connection.commit()
                    SeasonalWarRatingResult.Rated(firstBefore, secondBefore, firstAfter, secondAfter)
                }
            }
        } catch (error: Exception) {
            runCatching { connection.rollback() }.onFailure(error::addSuppressed)
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    fun decideUnrated(
        warId: UUID,
        chapterId: String,
        decision: String,
        decidedAt: Long,
    ): SeasonalWarRatingResult {
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            lockChapter(chapterId)
            val chapter = chapterWindow(chapterId)
            when {
                resultExists(warId) || decisionExists(warId) -> {
                    connection.rollback()
                    SeasonalWarRatingResult.Replayed
                }
                chapter.phase != "SCHEDULED" -> {
                    connection.rollback()
                    SeasonalWarRatingResult.Frozen
                }
                else -> {
                    insertDecision(warId, chapterId, decision, decidedAt)
                    connection.commit()
                    SeasonalWarRatingResult.Ineligible
                }
            }
        } catch (error: Exception) {
            runCatching { connection.rollback() }.onFailure(error::addSuppressed)
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    fun rating(chapterId: String, guildId: UUID): Int? =
        connection.prepareStatement("SELECT elo FROM chapter_seasonal_ratings WHERE chapter_id=? AND guild_id=?").use {
            it.setString(1, chapterId); it.setString(2, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        }

    fun isSettled(warId: UUID): Boolean = resultExists(warId) || decisionExists(warId)

    fun rank(chapterId: String, guildId: UUID): Int? {
        val current = rating(chapterId, guildId) ?: return null
        return connection.prepareStatement(
            "SELECT COUNT(*) + 1 FROM chapter_seasonal_ratings WHERE chapter_id=? AND elo>?"
        ).use {
            it.setString(1, chapterId); it.setInt(2, current)
            it.executeQuery().use { rows -> check(rows.next()); rows.getInt(1) }
        }
    }

    private fun lockChapter(chapterId: String) {
        if (mariaDb) {
            connection.prepareStatement("SELECT chapter_id FROM chapter_lifecycle WHERE chapter_id=? FOR UPDATE").use {
                it.setString(1, chapterId)
                it.executeQuery().use { rows -> check(rows.next()) { "Chapter lifecycle row missing" } }
            }
        } else {
            connection.prepareStatement("UPDATE chapter_lifecycle SET version=version WHERE chapter_id=?").use {
                it.setString(1, chapterId)
                check(it.executeUpdate() == 1) { "Chapter lifecycle row missing" }
            }
        }
    }

    private data class ChapterWindow(val phase: String, val startsAt: Long?, val endsAt: Long?) {
        fun contains(timestamp: Long): Boolean =
            (startsAt == null || timestamp >= startsAt) && (endsAt == null || timestamp < endsAt)
    }

    private fun chapterWindow(chapterId: String): ChapterWindow =
        connection.prepareStatement("SELECT phase,starts_at,ends_at FROM chapter_lifecycle WHERE chapter_id=?").use {
            it.setString(1, chapterId)
            it.executeQuery().use { rows ->
                check(rows.next())
                val startsAt = rows.getLong(2).let { value -> if (rows.wasNull()) null else value }
                val endsAt = rows.getLong(3).let { value -> if (rows.wasNull()) null else value }
                ChapterWindow(rows.getString(1), startsAt, endsAt)
            }
        }

    private fun resultExists(warId: UUID): Boolean =
        connection.prepareStatement("SELECT 1 FROM chapter_rated_war_results WHERE war_id=?").use {
            it.setString(1, warId.toString()); it.executeQuery().use { rows -> rows.next() }
        }

    private fun decisionExists(warId: UUID): Boolean =
        connection.prepareStatement("SELECT 1 FROM chapter_war_rating_decisions WHERE war_id=?").use {
            it.setString(1, warId.toString()); it.executeQuery().use { rows -> rows.next() }
        }

    private fun insertDecision(warId: UUID, chapterId: String, decision: String, decidedAt: Long) {
        connection.prepareStatement(
            "INSERT INTO chapter_war_rating_decisions (war_id,chapter_id,decision,decided_at) VALUES (?,?,?,?)"
        ).use {
            it.setString(1, warId.toString())
            it.setString(2, chapterId)
            it.setString(3, decision)
            it.setLong(4, decidedAt)
            check(it.executeUpdate() == 1)
        }
    }

    private fun level(guildId: UUID): Int? =
        connection.prepareStatement("SELECT current_level FROM guild_progression WHERE guild_id=?").use {
            it.setString(1, guildId.toString())
            it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
        }

    private fun ensureRating(chapterId: String, guildId: UUID, now: Long) {
        val sql = if (mariaDb)
            "INSERT IGNORE INTO chapter_seasonal_ratings (chapter_id,guild_id,elo,updated_at) VALUES (?,?,?,?)"
        else
            "INSERT OR IGNORE INTO chapter_seasonal_ratings (chapter_id,guild_id,elo,updated_at) VALUES (?,?,?,?)"
        connection.prepareStatement(sql).use {
            it.setString(1, chapterId); it.setString(2, guildId.toString())
            it.setInt(3, SeasonalElo.STARTING_RATING); it.setLong(4, now); it.executeUpdate()
        }
    }

    private fun requiredRating(chapterId: String, guildId: UUID): Int =
        requireNotNull(rating(chapterId, guildId))

    private fun updateRating(chapterId: String, guildId: UUID, value: Int, now: Long) {
        connection.prepareStatement(
            "UPDATE chapter_seasonal_ratings SET elo=?,updated_at=? WHERE chapter_id=? AND guild_id=?"
        ).use {
            it.setInt(1, value); it.setLong(2, now); it.setString(3, chapterId); it.setString(4, guildId.toString())
            check(it.executeUpdate() == 1)
        }
    }

    private fun pairLastRatedAt(chapterId: String, lower: String, higher: String): Long? =
        connection.prepareStatement(
            "SELECT last_rated_at FROM chapter_rated_pair_guards WHERE chapter_id=? AND lower_guild_id=? AND higher_guild_id=?"
        ).use {
            it.setString(1, chapterId); it.setString(2, lower); it.setString(3, higher)
            it.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun upsertPair(chapterId: String, lower: String, higher: String, warId: UUID, ratedAt: Long) {
        val updated = connection.prepareStatement(
            "UPDATE chapter_rated_pair_guards SET last_rated_at=?,last_war_id=? WHERE chapter_id=? AND lower_guild_id=? AND higher_guild_id=?"
        ).use {
            it.setLong(1, ratedAt); it.setString(2, warId.toString()); it.setString(3, chapterId); it.setString(4, lower); it.setString(5, higher)
            it.executeUpdate()
        }
        if (updated == 0) connection.prepareStatement(
            "INSERT INTO chapter_rated_pair_guards (chapter_id,lower_guild_id,higher_guild_id,last_rated_at,last_war_id) VALUES (?,?,?,?,?)"
        ).use {
            it.setString(1, chapterId); it.setString(2, lower); it.setString(3, higher); it.setLong(4, ratedAt); it.setString(5, warId.toString())
            it.executeUpdate()
        }
    }

    private fun insertResult(
        warId: UUID, chapterId: String, first: UUID, second: UUID,
        firstBefore: Int, secondBefore: Int, firstAfter: Int, secondAfter: Int,
        firstScore: Double, secondScore: Double, ratedAt: Long,
    ) {
        connection.prepareStatement("""
            INSERT INTO chapter_rated_war_results
            (war_id,chapter_id,first_guild_id,second_guild_id,first_rating_before,second_rating_before,
             first_rating_after,second_rating_after,first_score,second_score,rated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """.trimIndent()).use {
            it.setString(1, warId.toString()); it.setString(2, chapterId)
            it.setString(3, first.toString()); it.setString(4, second.toString())
            it.setInt(5, firstBefore); it.setInt(6, secondBefore); it.setInt(7, firstAfter); it.setInt(8, secondAfter)
            it.setDouble(9, firstScore); it.setDouble(10, secondScore); it.setLong(11, ratedAt)
            check(it.executeUpdate() == 1)
        }
    }
}
