package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Relation
import net.lumalyte.lg.domain.entities.RelationType
import java.util.Locale
import java.util.UUID

data class GuildInfoRelationEntry(
    val relation: Relation,
    val guild: Guild,
)

object GuildInfoRelationResolver {
    fun resolve(
        guildId: UUID,
        type: RelationType,
        relations: Iterable<Relation>,
        guildLookup: (UUID) -> Guild?,
    ): List<GuildInfoRelationEntry> =
        relations.asSequence()
            .filter { it.type == type && it.isActive() && it.involves(guildId) }
            .mapNotNull { relation ->
                guildLookup(relation.getOtherGuild(guildId))
                    ?.let { GuildInfoRelationEntry(relation, it) }
            }
            .distinctBy { it.guild.id }
            .sortedWith(
                compareBy<GuildInfoRelationEntry> {
                    it.guild.name.lowercase(Locale.ROOT)
                }.thenBy { it.guild.id.toString() }
            )
            .toList()

    fun totalPages(totalItems: Int, pageSize: Int): Int {
        require(pageSize > 0) { "pageSize must be positive" }
        return maxOf(1, (totalItems + pageSize - 1) / pageSize)
    }

    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        require(pageSize > 0) { "pageSize must be positive" }
        if (items.isEmpty()) return emptyList()

        val safePage = page.coerceIn(0, totalPages(items.size, pageSize) - 1)
        val start = safePage * pageSize
        return items.subList(start, minOf(start + pageSize, items.size))
    }
}
