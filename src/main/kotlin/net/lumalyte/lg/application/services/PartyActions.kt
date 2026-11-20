package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.utils.GuildResolver
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** What a /party subcommand came to. */
sealed interface PartyResult {
    data class Done(val party: Party? = null, val guild: Guild? = null, val created: Boolean = false) : PartyResult
    data class UnknownGuild(val name: String) : PartyResult
    data class NoRequest(val guild: Guild) : PartyResult
    data object NoGuild : PartyResult
    data object NoPermission : PartyResult
    data object NoParty : PartyResult
    data object OwnGuild : PartyResult
    data object AlreadyInParty : PartyResult
    data object AlreadyInvited : PartyResult
    data object BadName : PartyResult
    data object Failed : PartyResult
}

/**
 * REQ-211 to REQ-215: the work behind /party. The party service checks permissions again;
 * this class adds the guild-name lookups and the leader's-guild rule for disbanding.
 */
class PartyActions(
    private val parties: PartyService,
    private val guilds: GuildService,
    private val partyLength: () -> Duration,
) {
    fun guildOf(playerId: UUID): Guild? = guilds.getPlayerGuilds(playerId).firstOrNull()

    /** The guild's party with other guilds (or one still waiting for its second guild). */
    fun partyOf(guildId: UUID): Party? =
        parties.getActivePartiesForGuild(guildId).firstOrNull { !it.isPrivateParty() }

    fun invite(actor: UUID, targetName: String): PartyResult = managing(actor) { own ->
        val target = GuildResolver.resolveGuildByName(targetName, guilds) ?: return@managing PartyResult.UnknownGuild(targetName)
        if (target.id == own.id) return@managing PartyResult.OwnGuild
        val current = partyOf(own.id)
        if (current?.includesGuild(target.id) == true) return@managing PartyResult.AlreadyInParty
        if (parties.getPendingRequestsFromGuild(own.id).any { it.toGuildId == target.id }) {
            return@managing PartyResult.AlreadyInvited
        }
        val party = current ?: parties.createParty(newParty(own, actor)) ?: return@managing PartyResult.Failed
        if (parties.inviteToParty(party.id, own.id, target.id, actor) == null) return@managing PartyResult.Failed
        PartyResult.Done(party, target, created = current == null)
    }

    fun accept(actor: UUID, fromName: String): PartyResult = request(actor, fromName, incoming = true) { own, id ->
        parties.acceptPartyRequest(id, own.id, actor)?.let { PartyResult.Done(it) }
    }

    fun deny(actor: UUID, fromName: String): PartyResult = request(actor, fromName, incoming = true) { own, id ->
        if (parties.rejectPartyRequest(id, own.id, actor)) PartyResult.Done() else null
    }

    fun cancel(actor: UUID, toName: String): PartyResult = request(actor, toName, incoming = false) { own, id ->
        if (parties.cancelPartyRequest(id, own.id, actor)) PartyResult.Done() else null
    }

    fun leave(actor: UUID): PartyResult = managing(actor) { own ->
        val party = partyOf(own.id) ?: return@managing PartyResult.NoParty
        parties.leaveParty(party.id, own.id, actor)
        // leaveParty returns null both when it fails and when the party ends, so look again.
        val after = parties.getParty(party.id)
        val left = after == null || after.status != PartyStatus.ACTIVE || !after.includesGuild(own.id)
        if (left) PartyResult.Done(party) else PartyResult.Failed
    }

    fun disband(actor: UUID): PartyResult {
        val own = guildOf(actor) ?: return PartyResult.NoGuild
        val party = partyOf(own.id) ?: return PartyResult.NoParty
        val leaderGuilds = guilds.getPlayerGuilds(party.leaderId).map { it.id }.toSet()
        val allowed = party.leaderId == actor || (own.id in leaderGuilds && parties.canManageParties(actor, own.id))
        if (!allowed) return PartyResult.NoPermission
        return if (parties.dissolveParty(party.id, actor)) PartyResult.Done(party) else PartyResult.Failed
    }

    fun rename(actor: UUID, name: String): PartyResult = managing(actor) { own ->
        val party = partyOf(own.id) ?: return@managing PartyResult.NoParty
        if (!NAME.matches(name)) return@managing PartyResult.BadName
        if (parties.setPartyName(party.id, name, actor)) PartyResult.Done(party.copy(name = name)) else PartyResult.Failed
    }

    /** Every active party between two or more guilds, by name. */
    fun list(): List<Party> =
        guilds.getAllGuilds().asSequence()
            .flatMap { parties.getActivePartiesForGuild(it.id).asSequence() }
            .filter { !it.isPrivateParty() && it.guildIds.size >= 2 }
            .distinctBy { it.id }
            .sortedBy { (it.name ?: "").lowercase() }
            .toList()

    private fun newParty(own: Guild, actor: UUID): Party {
        val now = Instant.now()
        return Party(
            id = UUID.randomUUID(),
            guildIds = setOf(own.id),
            leaderId = actor,
            status = PartyStatus.ACTIVE,
            createdAt = now,
            expiresAt = now.plus(partyLength()),
            shared = true,
        )
    }

    private inline fun managing(actor: UUID, block: (Guild) -> PartyResult): PartyResult {
        val own = guildOf(actor) ?: return PartyResult.NoGuild
        if (!parties.canManageParties(actor, own.id)) return PartyResult.NoPermission
        return block(own)
    }

    private inline fun request(
        actor: UUID,
        otherName: String,
        incoming: Boolean,
        act: (Guild, UUID) -> PartyResult?,
    ): PartyResult = managing(actor) { own ->
        val other = GuildResolver.resolveGuildByName(otherName, guilds) ?: return@managing PartyResult.UnknownGuild(otherName)
        val request = if (incoming) {
            parties.getPendingRequestsForGuild(own.id).firstOrNull { it.fromGuildId == other.id }
        } else {
            parties.getPendingRequestsFromGuild(own.id).firstOrNull { it.toGuildId == other.id }
        } ?: return@managing PartyResult.NoRequest(other)
        act(own, request.id) ?: PartyResult.Failed
    }

    private companion object {
        val NAME = Regex("^\\S{1,32}$")
    }
}
