package net.lumalyte.lg.infrastructure.persistence.migrations

import java.sql.Connection
import java.time.Duration
import java.time.Instant

data class ChapterReadView(
    val id: String,
    val name: String,
    val phase: String,
    val startsAt: Long?,
    val endsAt: Long?,
) {
    fun timeRemaining(now: Long): Long = ((endsAt ?: now) - now).coerceAtLeast(0)
    fun timeRemainingText(now: Long): String {
        val d = Duration.ofMillis(timeRemaining(now))
        val days = d.toDays()
        val hours = d.minusDays(days).toHours()
        val minutes = d.minusDays(days).minusHours(hours).toMinutes()
        return if (days > 0) "${days}d ${hours}h" else if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
    fun startIso(): String = startsAt?.let { Instant.ofEpochMilli(it).toString() } ?: ""
    fun endIso(): String = endsAt?.let { Instant.ofEpochMilli(it).toString() } ?: ""
}

class ChapterReadSQL(private val connection: Connection) {
    fun current(): ChapterReadView? =
        connection.createStatement().use { s ->
            s.executeQuery("""
                SELECT chapter_id,chapter_name,phase,starts_at,ends_at
                FROM chapter_lifecycle
                ORDER BY CASE WHEN phase='COMPLETE' THEN 1 ELSE 0 END, starts_at DESC
                LIMIT 1
            """.trimIndent()).use { r ->
                if (!r.next()) return null
                val start = r.getLong("starts_at").let { if (r.wasNull()) null else it }
                val end = r.getLong("ends_at").let { if (r.wasNull()) null else it }
                ChapterReadView(r.getString("chapter_id"),r.getString("chapter_name"),r.getString("phase"),start,end)
            }
        }
}
