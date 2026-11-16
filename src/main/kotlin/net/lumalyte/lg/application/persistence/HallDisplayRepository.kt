package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import java.time.Instant
import java.util.UUID

/** REQ-260/261: themes a guild has earned and what its Hall statue wears. */
interface HallDisplayRepository {
    /** Owned themes in the order they were earned. */
    fun owned(guildId: UUID): List<HallTheme>

    /** Adds [theme] if the guild owns fewer than [limit] themes and not this one; false otherwise. */
    fun earn(guildId: UUID, theme: HallTheme, limit: Int, at: Instant): Boolean

    fun choices(guildId: UUID): Map<HallSlot, HallTheme>

    fun choose(guildId: UUID, slot: HallSlot, theme: HallTheme?)

    fun skin(guildId: UUID): UUID?

    fun setSkin(guildId: UUID, playerId: UUID?)

    /** REQ-271: the guild's chosen statue armor; missing parts use the tier default. */
    fun armor(guildId: UUID): Map<net.lumalyte.lg.domain.rewards.HallArmorPart, net.lumalyte.lg.domain.rewards.HallArmorPiece>

    fun setArmor(guildId: UUID, part: net.lumalyte.lg.domain.rewards.HallArmorPart, piece: net.lumalyte.lg.domain.rewards.HallArmorPiece?)

    /** Removes everything for the guild, used when it disbands. */
    fun deleteAll(guildId: UUID)
}
