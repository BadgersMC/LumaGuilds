package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player

internal fun Player.requireGuildMembership(guildService: GuildService): Boolean {
    val guilds = guildService.getPlayerGuilds(uniqueId)
    return if (guilds.isEmpty()) {
        sendMessage("§c❌ You are not in a guild!")
        false
    } else {
        true
    }
}

internal fun Player.requireGuildPermission(
    guildService: GuildService,
    memberService: MemberService,
    permission: RankPermission,
    noPermissionMessage: String,
): Boolean {
    return requireGuildForPermission(
        guildService,
        memberService,
        permission,
        noPermissionMessage,
    ) != null
}

internal fun Player.requireGuildForPermission(
    guildService: GuildService,
    memberService: MemberService,
    permission: RankPermission,
    noPermissionMessage: String,
): Guild? {
    val guilds = guildService.getPlayerGuilds(uniqueId)
    return if (guilds.isEmpty()) {
        sendMessage("§c❌ You are not in a guild!")
        null
    } else {
        val authorizedGuild =
            guilds.firstOrNull { guild ->
                memberService.hasPermission(
                    uniqueId,
                    guild.id,
                    permission,
                )
            }
        if (authorizedGuild == null) {
            sendMessage(noPermissionMessage)
            null
        } else {
            authorizedGuild
        }
    }
}
