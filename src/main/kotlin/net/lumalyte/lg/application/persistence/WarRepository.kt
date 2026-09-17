package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.DurableWarRecord
import java.util.UUID

interface WarRepository {
    fun get(id: UUID): DurableWarRecord?
    fun getAll(): List<DurableWarRecord>

    /** Save only at the supplied revision. True advances the stored revision by one. */
    fun save(record: DurableWarRecord): Boolean
}
