@file:Suppress("LibraryEntitiesShouldNotBePublic")

package net.lumalyte.lg.api

import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.domain.entities.RelationType
import java.util.UUID

/** Warmed relation repository serves current cached relations without SQL on Market event paths. */
class GuildAllianceLookupImpl(private val relations: RelationRepository) : GuildAllianceLookup {
    init { relations.getAll() }

    override fun areAllied(guildId: UUID, otherGuildId: UUID): Boolean {
        if (guildId == otherGuildId) return false
        val relation = relations.getByGuilds(guildId, otherGuildId) ?: return false
        return relation.type == RelationType.ALLY && relation.isActive()
    }
}
