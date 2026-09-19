package net.lumalyte.lg.infrastructure.services

import co.aikar.idb.Database
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.infrastructure.persistence.migrations.ChapterRolloverCoordinatorSQL
import net.lumalyte.lg.infrastructure.persistence.migrations.ChapterRolloverPlan
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteChapterBackupService
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ChapterRolloverScheduler(
    private val plugin: LumaGuilds,
    private val storage: Storage<Database>,
) {
    private var task: BukkitTask? = null

    fun start() {
        if (!plugin.config.getBoolean("chapter.enabled", false)) {
            plugin.logger.info("Chapter scheduler disabled")
            return
        }
        val intervalSeconds = plugin.config.getLong("chapter.check_interval_seconds", 60L).coerceAtLeast(10L)
        ensureConfiguredChapter()
        task = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable { tick() }, 20L, intervalSeconds * 20L)
        plugin.logger.info("Chapter scheduler started (every ${intervalSeconds}s)")
    }

    fun stop() { task?.cancel(); task = null }

    internal fun tick(now: Long = System.currentTimeMillis()) {
        storage.connection.connection.use { connection ->
            val active = net.lumalyte.lg.infrastructure.persistence.migrations.ChapterReadSQL(connection).current() ?: return
            val current = net.lumalyte.lg.infrastructure.persistence.migrations.ChapterAdminRecoverySQL(connection).status(active.id)
            val end = current.endsAt ?: return
            val nextId = nextId(current.chapterId)
            val nextName = nextName(current.chapterName)
            val nextEnd = ZonedDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneOffset.UTC)
                .plusMonths(plugin.config.getLong("chapter.duration_months", 3L).coerceAtLeast(1L))
                .toInstant().toEpochMilli()
            val plan = ChapterRolloverPlan(current.chapterId,current.chapterName,nextId,nextName,end,nextEnd)
            val backup: (String,String,Long)->Unit = { chapterId, backupId, at ->
                check(storage.dialect == SqlDialect.SQLITE) { "Automatic verified backup currently requires SQLite" }
                SQLiteChapterBackupService(connection, plugin.dataFolder.toPath().resolve("chapter-backups"))
                    .createVerifiedBackup(chapterId, backupId, at)
            }
            val status = ChapterRolloverCoordinatorSQL(connection, backup).catchUp(plan, now)
            if (status.lastError != null) plugin.logger.severe("Chapter rollover paused at ${status.phase}: ${status.lastError}")
        }
    }

    private fun ensureConfiguredChapter() {
        val id = requireNotNull(plugin.config.getString("chapter.id")).takeIf { it.isNotBlank() }
            ?: error("chapter.id is required when chapter.enabled=true")
        val name = requireNotNull(plugin.config.getString("chapter.name")).takeIf { it.isNotBlank() }
            ?: error("chapter.name is required when chapter.enabled=true")
        val starts = Instant.parse(requireNotNull(plugin.config.getString("chapter.starts_at"))).toEpochMilli()
        val ends = Instant.parse(requireNotNull(plugin.config.getString("chapter.ends_at"))).toEpochMilli()
        require(ends > starts) { "chapter.ends_at must be after starts_at" }

        storage.connection.connection.use { c ->
            val exists = c.prepareStatement("SELECT 1 FROM chapter_lifecycle WHERE chapter_id=?").use {
                it.setString(1,id); it.executeQuery().use { r -> r.next() }
            }
            if (!exists) c.prepareStatement("""
                INSERT INTO chapter_lifecycle (chapter_id,chapter_name,phase,starts_at,ends_at,updated_at,version)
                VALUES (?,?,'SCHEDULED',?,?,?,0)
            """.trimIndent()).use {
                it.setString(1,id); it.setString(2,name); it.setLong(3,starts); it.setLong(4,ends); it.setLong(5,System.currentTimeMillis()); it.executeUpdate()
            }
        }
    }

    private fun nextId(id: String): String {
        val m = Regex("^(.*?)(\\d+)$").matchEntire(id)
        return if (m != null) m.groupValues[1] + (m.groupValues[2].toInt() + 1) else "${id}-next"
    }
    private fun nextName(name: String): String {
        val m = Regex("^(.*?)(\\d+)$").matchEntire(name)
        return if (m != null) m.groupValues[1] + (m.groupValues[2].toInt() + 1) else "${name} Next"
    }
}
