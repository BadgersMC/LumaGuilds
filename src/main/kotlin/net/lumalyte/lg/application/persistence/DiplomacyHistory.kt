package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DiplomacyEntry
import java.util.UUID

/** REQ-203/REQ-204: saved diplomacy history. */
interface DiplomacyHistory {
    /** Saves both sides of one change together. */
    fun record(entries: List<DiplomacyEntry>)

    /** Newest first. */
    fun page(guildId: UUID, offset: Int, limit: Int): List<DiplomacyEntry>

    fun count(guildId: UUID): Int
}
