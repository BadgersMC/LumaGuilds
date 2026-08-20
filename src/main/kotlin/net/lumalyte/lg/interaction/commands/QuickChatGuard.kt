package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.bukkit.entity.Player
import java.util.UUID

internal fun Player.requireGuildMembership(guildService: GuildService, lang: LangService): Boolean {
    val guilds = guildService.getPlayerGuilds(uniqueId)
    return if (guilds.isEmpty()) {
        sendMessage(lang.msg("guild.not_member"))
        false
    } else {
        true
    }
}

internal fun Player.requireGuildPermission(
    guildService: GuildService,
    memberService: MemberService,
    permission: RankPermission,
    noPermissionMessage: Component,
    lang: LangService,
): Boolean {
    return requireGuildForPermission(
        guildService,
        memberService,
        permission,
        noPermissionMessage,
        lang,
    ) != null
}

internal fun Player.requireGuildForPermission(
    guildService: GuildService,
    memberService: MemberService,
    permission: RankPermission,
    noPermissionMessage: Component,
    lang: LangService,
): Guild? {
    val guilds = guildService.getPlayerGuilds(uniqueId)
    return if (guilds.isEmpty()) {
        sendMessage(lang.msg("guild.not_member"))
        null
    } else {
        val authorizedGuild =
            guilds.firstOrNull { guild ->
                memberService.hasPermission(uniqueId, guild.id, permission)
            }
        if (authorizedGuild == null) {
            sendMessage(noPermissionMessage)
            null
        } else {
            authorizedGuild
        }
    }
}

/**
 * Finds the single guild where [player] has SEND_ANNOUNCEMENTS.
 *
 * Returns the guild ID if exactly one guild qualifies. Returns `null` for
 * zero qualifying guilds or ambiguity (multiple guilds have the permission).
 * Callers should inspect the return value and send an appropriate error message
 * for each case.
 */
internal fun resolveAnnouncementGuild(
    player: Player,
    guildService: GuildService,
    memberService: MemberService,
): UUID? {
    val guilds = guildService.getPlayerGuilds(player.uniqueId)
    val qualifying =
        guilds.filter { guild ->
            memberService.hasPermission(
                player.uniqueId,
                guild.id,
                RankPermission.SEND_ANNOUNCEMENTS,
            )
        }
    return if (qualifying.size == 1) qualifying.first().id else null
}
