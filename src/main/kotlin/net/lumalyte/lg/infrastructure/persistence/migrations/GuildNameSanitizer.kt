package net.lumalyte.lg.infrastructure.persistence.migrations

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import java.sql.Connection
import java.util.TreeSet

/**
 * Shared backfill that scrubs the `guilds.name` column to match the runtime
 * validator (alphanumerics + spaces, max 32 chars). Strips legacy color codes
 * like `&r`, `&l`, and the explicit punctuation set [&!@#$%^&*()"',.].
 *
 * Collisions and empty results are resolved by appending the short id suffix.
 */
internal object GuildNameSanitizer {
    private val ALLOWED = Regex("[^A-Za-z0-9 ]")
    // Strip legacy color/format markers + the following char so "&rTest" becomes
    // "Test" instead of leaving an orphaned "rTest" for the player to rename.
    private val LEGACY_COLOR = Regex("[&§].")
    private const val MAX_LEN = 32

    fun sanitizeAll(connection: Connection, logger: ComponentLogger) {
        data class Row(val id: String, val original: String, val sanitized: String)

        val all = mutableListOf<Pair<String, String>>() // id, current name
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, name FROM guilds").use { rs ->
                while (rs.next()) all.add(rs.getString("id") to (rs.getString("name") ?: ""))
            }
        }

        // Case-insensitive set: getByName compares with ignoreCase=true, so two
        // post-migration names that differ only in case would still collide.
        val used = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
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
        // Loop so adjacent codes ("&l&oFoo") and Bukkit hex ("&x&a&a&f&f&c&cMint")
        // fully collapse instead of leaving the trailing code letters behind.
        var prev: String
        var s = name
        do {
            prev = s
            s = LEGACY_COLOR.replace(s, "")
        } while (s != prev)

        val stripped = ALLOWED.replace(s, "").trim().replace(Regex(" +"), " ")
        val truncated = stripped.take(MAX_LEN)
        return if (truncated.isBlank()) "Guild${id.replace("-", "").take(8)}" else truncated
    }

    private fun ensureUnique(base: String, id: String, used: Set<String>): String {
        if (base !in used) return base
        val suffix = id.replace("-", "").take(6)
        val room = (MAX_LEN - suffix.length - 1).coerceAtLeast(1)
        val prefix = base.take(room)
        var candidate = "$prefix $suffix".take(MAX_LEN)
        var n = 1
        while (candidate in used) {
            val tag = "$suffix$n"
            val prefixRoom = (MAX_LEN - tag.length - 1).coerceAtLeast(1)
            candidate = "${prefix.take(prefixRoom)} $tag".take(MAX_LEN)
            n++
            // Safety: bail after a sane number of attempts to avoid infinite loops
            // in absurd corner cases. 1000 collisions is way past plausible.
            if (n > 1000) {
                candidate = (prefix.take(1) + java.util.UUID.randomUUID().toString().take(MAX_LEN - 2))
                break
            }
        }
        return candidate
    }
}
