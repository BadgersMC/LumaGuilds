package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildRecruitmentEligibility
import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import java.util.UUID

/** Fresh admission checks remain shared by free membership and compensated bank admission. */
internal class LfgAdmissionChecks(
    private val guilds: GuildRepository,
    private val members: MemberService,
    private val ranks: RankService,
    private val config: ConfigService,
    private val recruitment: GuildRecruitmentEligibility?,
    private val validation: LfgService,
) {
    fun prepare(player: UUID, quote: Guild, allowed: (Guild) -> Boolean): LfgAdmissionCheck {
        val current = guilds.getById(quote.id)
            ?: return LfgAdmissionCheck.Denied(LfgJoinResult.Error("This guild is no longer available"))
        return prepareCurrent(player, quote, current, allowed)
    }

    private fun prepareCurrent(
        player: UUID,
        quote: Guild,
        current: Guild,
        allowed: (Guild) -> Boolean,
    ): LfgAdmissionCheck {
        return if (!allowed(current) || !sameFee(current, quote)) {
            LfgAdmissionCheck.Denied(recruitmentChanged())
        } else {
            prepareRank(player, current)
        }
    }

    private fun prepareRank(player: UUID, guild: Guild): LfgAdmissionCheck {
        val rank = ranks.getDefaultRank(guild.id)
            ?: return LfgAdmissionCheck.Denied(LfgJoinResult.Error("Guild configuration error: No default rank found"))
        val result = validation.canJoinGuild(player, guild)
        return if (result is LfgJoinResult.Success) {
            LfgAdmissionCheck.Ready(guild, rank.id)
        } else {
            LfgAdmissionCheck.Denied(result)
        }
    }

    fun allows(player: UUID, quote: Guild, rank: UUID, allowed: (Guild) -> Boolean): Boolean {
        val current = guilds.getById(quote.id) ?: return false
        return allowed(current) && sameFee(current, quote) && memberEligible(player, quote, rank)
    }

    private fun memberEligible(player: UUID, guild: Guild, rank: UUID): Boolean {
        val limit = minOf(config.loadConfig().guild.maxMembersPerGuild, members.getMemberLimit(guild.id))
        return recruitment?.allows(player, guild.id) != false && members.getPlayerGuilds(player).isEmpty() &&
            members.getMemberCount(guild.id) < limit && ranks.getDefaultRank(guild.id)?.id == rank
    }

    private fun sameFee(current: Guild, quote: Guild): Boolean =
        current.joinFeeEnabled == quote.joinFeeEnabled && current.joinFeeAmount == quote.joinFeeAmount

    private fun recruitmentChanged(): LfgJoinResult =
        LfgJoinResult.Error("Guild recruitment changed. Please reopen the guild browser")
}
