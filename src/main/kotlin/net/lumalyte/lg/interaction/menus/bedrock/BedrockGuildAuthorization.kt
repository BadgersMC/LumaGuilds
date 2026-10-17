package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/**
 * Shared authorization boundary for mutable Bedrock guild-management forms.
 *
 * Forms still rely on the underlying service to enforce authorization during the
 * mutation itself; this guard prevents the Bedrock adapter from exposing or
 * persisting management controls without the matching guild permission.
 */
class BedrockGuildAuthorization(
    private val guildService: GuildService
) {
    fun canManageBankSettings(playerId: UUID, guildId: UUID): Boolean =
        guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_BANK_SETTINGS)

    fun canManageGuildSettings(playerId: UUID, guildId: UUID): Boolean =
        guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_GUILD_SETTINGS)

    fun canManageRanks(playerId: UUID, guildId: UUID): Boolean =
        guildService.hasPermission(playerId, guildId, RankPermission.MANAGE_RANKS)
}