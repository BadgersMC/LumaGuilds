package net.lumalyte.lg.infrastructure.persistence.guilds

import co.aikar.idb.Database
import net.lumalyte.lg.domain.values.BannerColor
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID

/** One database read view; no HTTP thread traverses the mutable guild caches. */
class PublicPlayerGuildSnapshotSql(private val storage: Storage<Database>) {
    fun build(players: List<UUID>): Map<String, Any?> {
        require(players.size in 1..50 && players.distinct().size == players.size)
        val memberships = players.associateWith { mutableListOf<Map<String, Any?>>() }
        val seen = mutableSetOf<Pair<UUID, UUID>>()
        val placeholders = players.joinToString(",") { "?" }
        val sql = """SELECT m.player_id, g.id AS guild_id, g.name, g.level,
            b.base_color, b.patterns FROM members m JOIN guilds g ON g.id = m.guild_id
            LEFT JOIN guild_banners b ON b.guild_id = g.id AND b.is_active = 1
            WHERE m.player_id IN ($placeholders) ORDER BY m.player_id, g.id LIMIT 1001""".trimIndent()
        storage.connection.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                players.forEachIndexed { index, id -> statement.setString(index + 1, id.toString()) }
                statement.executeQuery().use { rows ->
                    var count = 0
                    while (rows.next()) {
                        check(++count <= 1000) { "Public guild lookup exceeds bounds" }
                        val player = UUID.fromString(rows.getString("player_id"))
                        val guild = UUID.fromString(rows.getString("guild_id"))
                        val list = checkNotNull(memberships[player])
                        check(seen.add(player to guild) && list.size < 20) { "Ambiguous guild membership" }
                        val base = rows.getString("base_color")
                        val banner = if (base == null) null else banner(base, rows.getString("patterns"))
                        list.add(mapOf("id" to guild.toString(), "name" to rows.getString("name"),
                            "level" to rows.getInt("level"), "banner" to banner))
                    }
                }
            }
        }
        return mapOf("generatedAt" to Instant.now().toString(), "items" to players.map {
            mapOf("id" to it.toString(), "guildMemberships" to memberships.getValue(it).toList())
        })
    }

    private fun banner(base: String, raw: String?): Map<String, Any> {
        val color = BannerColor.valueOf(base).name
        val encoded = requireNotNull(raw)
        val patterns = if (encoded.isEmpty()) emptyList() else encoded.split('|').map { value ->
            val parts = value.split(':')
            require(parts.size == 2 && parts[0].matches(Regex("[A-Z_]{1,40}")))
            mapOf("type" to parts[0], "color" to BannerColor.valueOf(parts[1]).name)
        }
        require(patterns.size <= 20)
        return mapOf("baseColor" to color, "patterns" to patterns)
    }
}
