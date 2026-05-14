package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.sql.Connection

/**
 * Shared backfill that scrubs the `guilds.name` column to match the runtime
 * validator (alphanumerics + spaces, max 32 chars). Strips legacy color codes
 * like `&r`, `&l`, and the explicit punctuation set [&!@#$%^&*()"',.].
 *
 * Collisions and empty results are resolved by appending the short id suffix.
 */
internal object GuildNameSanitizer {
    private val ALLOWED = Regex("[^A-Za-z0-9 ]")
    private const val MAX_LEN = 32

    fun sanitizeAll(connection: Connection, logger: ComponentLogger) {
        data class Row(val id: String, val original: String, val sanitized: String)

        val all = mutableListOf<Pair<String, String>>() // id, current name
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, name FROM guilds").use { rs ->
                while (rs.next()) all.add(rs.getString("id") to (rs.getString("name") ?: ""))
            }
        }

        val used = mutableSetOf<String>()
        val pending = mutableListOf<Row>()
        for ((id, name) in all) {
            val cleaned = clean(name, id)
            if (cleaned == name) {
                used.add(name)
                continue
            }
            pending.add(Row(id, name, cleaned))
        }

        connection.prepareStatement("UPDATE guilds SET name = ? WHERE id = ?").use { ps ->
            var changed = 0
            for (row in pending) {
                val unique = ensureUnique(row.sanitized, row.id, used)
                used.add(unique)
                ps.setString(1, unique)
                ps.setString(2, row.id)
                ps.executeUpdate()
                changed++
                logger.info(Component.text("  • renamed guild ${row.id}: '${row.original}' → '$unique'"))
            }
            logger.info(Component.text("✓ Sanitized $changed guild name(s)"))
        }
    }

    private fun clean(name: String, id: String): String {
        val stripped = ALLOWED.replace(name, "").trim().replace(Regex(" +"), " ")
        val truncated = stripped.take(MAX_LEN)
        return if (truncated.isBlank()) "Guild${id.replace("-", "").take(8)}" else truncated
    }

    private fun ensureUnique(base: String, id: String, used: Set<String>): String {
        if (base !in used) return base
        val suffix = id.replace("-", "").take(6)
        val room = MAX_LEN - suffix.length - 1
        val prefix = base.take(maxOf(1, room))
        var candidate = "$prefix $suffix"
        var n = 1
        while (candidate in used) {
            candidate = "${prefix.take(maxOf(1, room - 2))} $suffix$n"
            n++
        }
        return candidate
    }
}
