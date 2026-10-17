package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.sql.Connection
import java.util.UUID

/** Fences quotes against the same lifecycle row that rollover updates before seasonal work. */
internal class GuildShopXpChapterGuard(storage: Storage<Database>) {
    private val lock = if (storage.dialect == SqlDialect.MARIADB) " FOR UPDATE" else ""

    init {
        storage.connection.executeUpdate(
            "CREATE TABLE IF NOT EXISTS guild_shop_xp_chapters " +
                "(sale_id VARCHAR(36) PRIMARY KEY, chapter_id VARCHAR(64))",
        )
    }

    fun current(c: Connection): Chapter? {
        return c.selectOne(
            "SELECT chapter_id, phase, starts_at, ends_at FROM chapter_lifecycle " +
                "ORDER BY CASE WHEN phase = 'COMPLETE' THEN 1 ELSE 0 END, starts_at DESC LIMIT 1$lock",
        ) { row ->
            Chapter(
                row.getString("chapter_id"),
                row.getString("phase"),
                row.getObject("starts_at")?.let { (it as Number).toLong() },
                row.getObject("ends_at")?.let { (it as Number).toLong() },
            )
        }
    }

    fun capture(c: Connection, id: UUID, at: Long, chapter: Chapter?): Boolean {
        c.updateStatement("INSERT INTO guild_shop_xp_chapters VALUES (?, ?)", id.toString(), chapter?.id)
        return chapter == null || chapter.allows(at)
    }

    fun denial(c: Connection, id: UUID, chapter: Chapter?): String? {
        val captured =
            c.selectOne(
                "SELECT chapter_id FROM guild_shop_xp_chapters WHERE sale_id = ?",
                id.toString(),
            ) { it.getString(1) }
        return when {
            captured != chapter?.id -> "STALE_RUN"
            chapter != null && !chapter.allows(System.currentTimeMillis()) -> "CHAPTER_FROZEN"
            else -> null
        }
    }

    class Chapter(val id: String, val phase: String, val startsAt: Long?, val endsAt: Long?) {
        fun allows(at: Long): Boolean {
            return phase == "SCHEDULED" &&
                (startsAt == null || at >= startsAt) && (endsAt == null || at < endsAt)
        }
    }
}
