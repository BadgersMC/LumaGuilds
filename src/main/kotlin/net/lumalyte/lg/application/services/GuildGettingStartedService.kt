package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

enum class GuildStartTopic { MEMBERSHIP, INVITES, RANKS, CHAT, HOME, QUESTS, PROGRESSION, BANK, STALLS }
enum class GuildStartState { COMPLETED, AVAILABLE, LOCKED }
data class GuildStartStep(val topic: GuildStartTopic, val state: GuildStartState, val action: GuildStartTopic?, val reason: String)
data class GuildStartSnapshot(val guild: Guild?, val steps: List<GuildStartStep>, val members: Int, val capacity: Int)

/** Current authority and observable milestones; no rewards or synthetic setup completion. */
class GuildGettingStartedService(
    private val guilds: GuildService,
    private val members: MemberService,
    private val homes: GuildHomeActivationService,
) {
    fun snapshot(player: UUID, selected: UUID?): GuildStartSnapshot? {
        if (selected == null) {
            val current = guilds.getPlayerGuilds(player).minByOrNull { it.id.toString() }
            return if (current == null) introduction() else snapshot(player, current.id)
        }
        val guild = guilds.getGuild(selected) ?: return null
        if (members.getMember(player, selected) == null) return null
        val count = members.getMemberCount(selected)
        val capacity = members.getMemberLimit(selected)
        val activeHome = guild.homes.homeNames.any { homes.isActive(selected, it) }
        val steps = listOf(
            step(GuildStartTopic.MEMBERSHIP, true, true),
            step(GuildStartTopic.INVITES, count > 1, allowed(player, selected, RankPermission.MANAGE_MEMBERS) && count < capacity, "onboarding.invite_requirement"),
            step(GuildStartTopic.RANKS, false, allowed(player, selected, RankPermission.MANAGE_RANKS)),
            step(GuildStartTopic.CHAT, false, true),
            homeStep(player, guild, activeHome),
            step(GuildStartTopic.QUESTS, false, true),
            step(GuildStartTopic.PROGRESSION, guild.level > 1, true),
            step(GuildStartTopic.BANK, false, true),
            step(GuildStartTopic.STALLS, false, true),
        )
        return GuildStartSnapshot(guild, steps, count, capacity)
    }

    private fun homeStep(player: UUID, guild: Guild, active: Boolean): GuildStartStep {
        val manager = allowed(player, guild.id, RankPermission.MANAGE_HOME)
        val accessible = guild.homes.homeNames.any { guilds.canUseHome(player, guild.id, it) }
        if (active) return step(GuildStartTopic.HOME, true, manager || accessible)
        if (!manager) return step(GuildStartTopic.HOME, false, false)
        if (guilds.getAvailableHomeSlots(guild.id) <= 0) {
            return GuildStartStep(GuildStartTopic.HOME, GuildStartState.LOCKED, GuildStartTopic.PROGRESSION, "onboarding.home_unlock")
        }
        return step(GuildStartTopic.HOME, false, true, "onboarding.home_costs")
    }

    private fun allowed(player: UUID, guild: UUID, permission: RankPermission) = members.hasPermission(player, guild, permission)

    private fun step(topic: GuildStartTopic, complete: Boolean, allowed: Boolean, reason: String? = null): GuildStartStep =
        GuildStartStep(topic, if (complete) GuildStartState.COMPLETED else if (allowed) GuildStartState.AVAILABLE else GuildStartState.LOCKED,
            topic.takeIf { allowed }, reason ?: if (allowed) "onboarding.details.${topic.name.lowercase()}" else "onboarding.permission")

    private fun introduction() = GuildStartSnapshot(null, GuildStartTopic.entries.map {
        GuildStartStep(it, if (it == GuildStartTopic.MEMBERSHIP) GuildStartState.AVAILABLE else GuildStartState.LOCKED,
            it.takeIf { it == GuildStartTopic.MEMBERSHIP }, if (it == GuildStartTopic.MEMBERSHIP) "onboarding.details.membership" else "onboarding.join_first")
    }, 0, 0)
}
