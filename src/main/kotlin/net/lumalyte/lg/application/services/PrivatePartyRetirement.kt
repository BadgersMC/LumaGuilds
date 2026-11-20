package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.PartyRepository
import net.lumalyte.lg.application.persistence.PlayerPartyPreferenceRepository
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus

/**
 * REQ-208: parties are only for talking between guilds, so the old single-guild parties
 * (Guild_Chat, Officer_Chat, Leader_Chat) are ended at startup. Rows are kept, only the
 * status changes, and anyone whose party chat pointed at one goes back to normal chat.
 */
class PrivatePartyRetirement(
    private val parties: PartyRepository,
    private val preferences: PlayerPartyPreferenceRepository,
) {
    /** Ends the single-guild parties and returns how many were ended. */
    fun run(): Int {
        val retired = toRetire(parties.getAll()).filter { parties.update(it) }
        val ids = retired.map { it.id }.toSet()
        if (ids.isNotEmpty()) {
            preferences.getAll().filter { it.partyId in ids }.forEach { preferences.removeByPlayerId(it.playerId) }
        }
        return retired.size
    }

    companion object {
        /** The active single-guild parties, already marked dissolved. */
        fun toRetire(all: Collection<Party>): List<Party> =
            all.filter { it.status == PartyStatus.ACTIVE && it.isPrivateParty() }
                .map { it.copy(status = PartyStatus.DISSOLVED) }
    }
}
