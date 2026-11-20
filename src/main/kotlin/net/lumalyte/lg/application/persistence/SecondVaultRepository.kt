package net.lumalyte.lg.application.persistence

import java.time.Instant
import java.util.UUID

/** Where a guild's second vault chest stands (REQ-243). */
data class SecondVaultSpot(val worldId: UUID, val x: Int, val y: Int, val z: Int)

/**
 * REQ-242 to REQ-246: the Prestige VI second vault's location and items, stored apart from the
 * main vault so nothing that reads the main vault or its gold ever sees them. Items are stored
 * serialized, one row per slot.
 */
interface SecondVaultRepository {
    /** Every placed second vault, by guild. */
    fun spots(): Map<UUID, SecondVaultSpot>

    /** Records the guild's second vault; false when it already has one. */
    fun place(guildId: UUID, spot: SecondVaultSpot, at: Instant): Boolean

    fun removeSpot(guildId: UUID)

    fun items(guildId: UUID): Map<Int, String>

    /** Replaces the guild's stored items with [items] in one transaction. */
    fun saveItems(guildId: UUID, items: Map<Int, String>)

    /** Removes the spot and every stored item, used when the guild disbands. */
    fun deleteAll(guildId: UUID)
}
