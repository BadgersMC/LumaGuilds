package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.BanResult
import net.lumalyte.lg.application.services.UnbanResult

/** REQ-194: chat replies for bans, shared by `/g ban`, the Java menu and the Bedrock form. */
internal object GuildBanMessages {
    fun ban(lang: LangService, result: BanResult, player: String): Component = when (result) {
        is BanResult.Banned -> lang.msg("guild_ban.command.banned", "player" to player)
        BanResult.NoPermission -> lang.msg("guild_ban.command.no_permission")
        BanResult.Self -> lang.msg("guild_ban.command.self")
        BanResult.Owner -> lang.msg("guild_ban.command.owner")
        BanResult.NotOutranked -> lang.msg("guild_ban.command.not_outranked")
        BanResult.AlreadyBanned -> lang.msg("guild_ban.command.already_banned", "player" to player)
        BanResult.Failed -> lang.msg("guild_ban.command.failed")
    }

    fun preview(lang: LangService, wasMember: Boolean, player: String): Component =
        if (wasMember) lang.msg("guild_ban.command.preview_member", "player" to player)
        else lang.msg("guild_ban.command.preview_outsider", "player" to player)

    fun unban(lang: LangService, result: UnbanResult, player: String): Component = when (result) {
        UnbanResult.UNBANNED -> lang.msg("guild_ban.command.unbanned", "player" to player)
        UnbanResult.NO_PERMISSION -> lang.msg("guild_ban.command.no_permission")
        UnbanResult.NOT_BANNED -> lang.msg("guild_ban.command.not_banned", "player" to player)
        UnbanResult.FAILED -> lang.msg("guild_ban.command.failed")
    }
}
