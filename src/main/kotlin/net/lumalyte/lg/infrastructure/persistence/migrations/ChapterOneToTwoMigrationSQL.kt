package net.lumalyte.lg.infrastructure.persistence.migrations

import net.lumalyte.lg.domain.values.ProgressionCurve
import java.sql.Connection
import java.sql.ResultSet

data class ChapterOneToTwoGuildPlan(
    val guildId: String,
    val guildName: String,
    val beforeLevel: Int,
    val beforeExperience: Int,
    val canonicalGold: Long,
    val homeCount: Int,
    val initialHomeCapacity: Int,
    val placement: Int,
)

data class ChapterOneToTwoPreview(
    val guilds: List<ChapterOneToTwoGuildPlan>,
    val orphanProgressionRows: Int,
    val orphanHomeRows: Int,
    val levelDriftRows: Int,
)

data class ChapterOneToTwoMigrationResult(
    val migratedGuilds: Int,
    val replayed: Boolean,
)

class ChapterOneToTwoMigrationSQL(
    private val connection: Connection,
    private val mariaDb: Boolean,
    private val curve: ProgressionCurve,
) {
    fun preview(
        migrationId: String,
        sourceChapterId: String,
        targetChapterId: String = "chapter-2",
    ): ChapterOneToTwoPreview {
        require(migrationId.isNotBlank())
        require(sourceChapterId.isNotBlank())
        require(targetChapterId.isNotBlank())
        return buildPreview()
    }

    fun apply(
        migrationId: String,
        sourceChapterId: String,
        targetChapterId: String = "chapter-2",
        now: Long,
    ): ChapterOneToTwoMigrationResult {
        require(migrationId.isNotBlank())
        require(sourceChapterId.isNotBlank())
        require(targetChapterId.isNotBlank())

        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            lockLifecycle(sourceChapterId)
            lockLiveGuilds()
            val preview = buildPreview()
            val existingReceipts = receiptCount(migrationId)
            if (existingReceipts > 0) {
                check(existingReceipts == preview.guilds.size) {
                    "Partial migration receipt set requires recovery"
                }
                connection.rollback()
                ChapterOneToTwoMigrationResult(0, replayed = true)
            } else {
                requireVerifiedBackup(sourceChapterId)
                requireLifecyclePhase(sourceChapterId, "BACKED_UP")
                requireNoExistingRewardAccounts(preview)
                archiveStandings(sourceChapterId, preview, now)
                preview.guilds.forEach { guild ->
                    resetProgression(guild, now)
                    initializeRewardAccount(guild)
                    initializeSeasonalRating(targetChapterId, guild.guildId, now)
                    recordReceipt(migrationId, guild, now)
                }
                updateLifecycle(sourceChapterId, "RESET", now)
                connection.commit()
                ChapterOneToTwoMigrationResult(preview.guilds.size, replayed = false)
            }
        } catch (error: Exception) {
            runCatching { connection.rollback() }.onFailure { rollback ->
                error.addSuppressed(rollback)
            }
            throw if (error is IllegalStateException) error
            else IllegalStateException("Chapter 1→2 migration failed", error)
        } finally {
            connection.autoCommit = previousAutoCommit
        }
    }

    private fun buildPreview(): ChapterOneToTwoPreview {
        val rows = mutableListOf<ChapterOneToTwoGuildPlan>()
        val sql = """
            SELECT g.id, g.name, g.level AS legacy_level,
                   p.current_level, p.total_experience,
                   COALESCE(v.balance, 0) AS canonical_gold,
                   (SELECT COUNT(*) FROM guild_homes h WHERE h.guild_id = g.id) AS home_count
            FROM guilds g
            JOIN guild_progression p ON p.guild_id = g.id
            LEFT JOIN vault_gold v ON v.guild_id = g.id
            ORDER BY p.current_level DESC, p.total_experience DESC, LOWER(g.name), g.id
        """.trimIndent()
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                var placement = 1
                while (result.next()) {
                    val homeCount = result.getInt("home_count")
                    rows += ChapterOneToTwoGuildPlan(
                        guildId = result.getString("id"),
                        guildName = result.getString("name"),
                        beforeLevel = result.getInt("current_level"),
                        beforeExperience = result.getInt("total_experience"),
                        canonicalGold = result.getLong("canonical_gold"),
                        homeCount = homeCount,
                        initialHomeCapacity = maxOf(1, homeCount),
                        placement = placement++,
                    )
                }
            }
        }
        val liveGuildCount = scalarInt("SELECT COUNT(*) FROM guilds")
        check(rows.size == liveGuildCount) {
            "Every live guild must have one progression row before migration"
        }
        val orphanProgressionRows = scalarInt("""
            SELECT COUNT(*) FROM guild_progression p
            LEFT JOIN guilds g ON g.id = p.guild_id
            WHERE g.id IS NULL
        """.trimIndent())
        val orphanHomeRows = scalarInt("""
            SELECT COUNT(*) FROM guild_homes h
            LEFT JOIN guilds g ON g.id = h.guild_id
            WHERE g.id IS NULL
        """.trimIndent())
        val levelDriftRows = scalarInt("""
            SELECT COUNT(*) FROM guilds g
            JOIN guild_progression p ON p.guild_id = g.id
            WHERE g.level <> p.current_level
        """.trimIndent())
        return ChapterOneToTwoPreview(
            guilds = rows,
            orphanProgressionRows = orphanProgressionRows,
            orphanHomeRows = orphanHomeRows,
            levelDriftRows = levelDriftRows,
        )
    }

    private fun lockLifecycle(chapterId: String) {
        if (mariaDb) {
            connection.prepareStatement(
                "SELECT chapter_id FROM chapter_lifecycle WHERE chapter_id = ? FOR UPDATE"
            ).use { statement ->
                statement.setString(1, chapterId)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "Chapter lifecycle row is missing" }
                }
            }
        } else {
            connection.prepareStatement(
                "UPDATE chapter_lifecycle SET version = version WHERE chapter_id = ?"
            ).use { statement ->
                statement.setString(1, chapterId)
                check(statement.executeUpdate() == 1) { "Chapter lifecycle row is missing" }
            }
        }
    }

    private fun lockLiveGuilds() {
        if (!mariaDb) return
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id FROM guilds ORDER BY id FOR UPDATE").use { rows ->
                while (rows.next()) Unit
            }
        }
    }

    private fun receiptCount(migrationId: String): Int =
        queryInt(
            "SELECT COUNT(*) FROM chapter_migration_receipts WHERE migration_id = ?",
            migrationId,
        )
    private fun requireVerifiedBackup(sourceChapterId: String) {
        val verified = connection.prepareStatement("""
            SELECT 1
            FROM chapter_lifecycle l
            JOIN chapter_backup_evidence b ON b.backup_id = l.backup_id
            WHERE l.chapter_id = ?
              AND b.chapter_id = l.chapter_id
              AND b.verification_status = 'VERIFIED'
              AND b.verified_at IS NOT NULL
              AND b.restore_verified_at IS NOT NULL
            LIMIT 1
        """.trimIndent()).use { statement ->
            statement.setString(1, sourceChapterId)
            statement.executeQuery().use(ResultSet::next)
        }
        check(verified) { "Verified restorable backup required before migration" }
    }

    private fun requireLifecyclePhase(chapterId: String, expected: String) {
        val actual = connection.prepareStatement(
            "SELECT phase FROM chapter_lifecycle WHERE chapter_id = ?"
        ).use { statement ->
            statement.setString(1, chapterId)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Chapter lifecycle row is missing" }
                rows.getString(1)
            }
        }
        check(actual == expected) {
            "Chapter migration requires phase=$expected; current phase=$actual"
        }
    }

    private fun requireNoExistingRewardAccounts(preview: ChapterOneToTwoPreview) {
        preview.guilds.forEach { guild ->
            val count = queryInt(
                "SELECT COUNT(*) FROM guild_reward_accounts WHERE guild_id = ?",
                guild.guildId,
            )
            check(count == 0) {
                "Guild \${guild.guildId} already has a Chapter 2 reward account"
            }
        }
    }

    private fun archiveStandings(
        chapterId: String,
        preview: ChapterOneToTwoPreview,
        now: Long,
    ) {
        connection.prepareStatement("""
            INSERT INTO chapter_standings_archive
            (chapter_id, guild_id, placement, run_level, run_experience,
             seasonal_elo, canonical_gold, archived_at)
            VALUES (?, ?, ?, ?, ?, 1000, ?, ?)
        """.trimIndent()).use { statement ->
            preview.guilds.forEach { guild ->
                statement.setString(1, chapterId)
                statement.setString(2, guild.guildId)
                statement.setInt(3, guild.placement)
                statement.setInt(4, guild.beforeLevel)
                statement.setInt(5, guild.beforeExperience)
                statement.setLong(6, guild.canonicalGold)
                statement.setLong(7, now)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
    private fun resetProgression(guild: ChapterOneToTwoGuildPlan, now: Long) {
        connection.prepareStatement("""
            UPDATE guild_progression
            SET total_experience = 0,
                current_level = 1,
                experience_this_level = 0,
                experience_for_next_level = ?,
                last_level_up = NULL,
                unlocked_perks = '[]',
                last_updated = ?
            WHERE guild_id = ?
        """.trimIndent()).use { statement ->
            statement.setInt(1, curve.experienceForNextLevel(1))
            statement.setLong(2, now)
            statement.setString(3, guild.guildId)
            check(statement.executeUpdate() == 1) {
                "Progression row disappeared for \${guild.guildId}"
            }
        }
        connection.prepareStatement(
            "UPDATE guilds SET level = 1 WHERE id = ?"
        ).use { statement ->
            statement.setString(1, guild.guildId)
            check(statement.executeUpdate() == 1) {
                "Guild row disappeared for \${guild.guildId}"
            }
        }
    }

    private fun initializeRewardAccount(guild: ChapterOneToTwoGuildPlan) {
        connection.prepareStatement("""
            INSERT INTO guild_reward_accounts
            (guild_id, version, initial_home_capacity, prestige_count)
            VALUES (?, 0, ?, 0)
        """.trimIndent()).use { statement ->
            statement.setString(1, guild.guildId)
            statement.setInt(2, guild.initialHomeCapacity)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun initializeSeasonalRating(
        chapterId: String,
        guildId: String,
        now: Long,
    ) {
        connection.prepareStatement("""
            INSERT INTO chapter_seasonal_ratings
            (chapter_id, guild_id, elo, updated_at)
            VALUES (?, ?, 1000, ?)
        """.trimIndent()).use { statement ->
            statement.setString(1, chapterId)
            statement.setString(2, guildId)
            statement.setLong(3, now)
            check(statement.executeUpdate() == 1)
        }
    }
    private fun recordReceipt(
        migrationId: String,
        guild: ChapterOneToTwoGuildPlan,
        now: Long,
    ) {
        connection.prepareStatement("""
            INSERT INTO chapter_migration_receipts
            (migration_id, guild_id, migration_kind, dry_run, status,
             before_level, before_experience, home_count,
             initial_home_capacity, applied_at)
            VALUES (?, ?, 'CHAPTER_1_TO_2', 0, 'APPLIED', ?, ?, ?, ?, ?)
        """.trimIndent()).use { statement ->
            statement.setString(1, migrationId)
            statement.setString(2, guild.guildId)
            statement.setInt(3, guild.beforeLevel)
            statement.setInt(4, guild.beforeExperience)
            statement.setInt(5, guild.homeCount)
            statement.setInt(6, guild.initialHomeCapacity)
            statement.setLong(7, now)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun updateLifecycle(chapterId: String, phase: String, now: Long) {
        connection.prepareStatement("""
            UPDATE chapter_lifecycle
            SET phase = ?, archived_at = COALESCE(archived_at, ?),
                updated_at = ?, version = version + 1, last_error = NULL
            WHERE chapter_id = ?
        """.trimIndent()).use { statement ->
            statement.setString(1, phase)
            statement.setLong(2, now)
            statement.setLong(3, now)
            statement.setString(4, chapterId)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun scalarInt(sql: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }

    private fun queryInt(sql: String, parameter: String): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, parameter)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getInt(1)
            }
        }
}
