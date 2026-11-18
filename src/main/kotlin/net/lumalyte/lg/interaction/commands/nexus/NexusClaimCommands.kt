package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.lumalyte.lg.application.actions.player.tool.GivePlayerClaimTool
import net.lumalyte.lg.application.results.player.tool.GivePlayerClaimToolResult
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player

/**
 * ACF-free equivalents of /claim and /claimmenu. Kept inactive until the
 * complete Nexus command and dependency-graph cutover.
 */
@Command(name = "claim")
class NexusClaimToolCommand(
    private val lang: LangService,
    private val givePlayerClaimTool: GivePlayerClaimTool,
) {
    @Subcommand("")
    @Permission("lumaguilds.command.claim")
    @PlayerOnly
    fun claim(@Context player: Player) {
        when (givePlayerClaimTool.execute(player.uniqueId)) {
            GivePlayerClaimToolResult.PlayerAlreadyHasTool ->
                player.sendMessage(lang.msg("command.claim.already_have_tool"))
            GivePlayerClaimToolResult.Success ->
                player.sendMessage(lang.msg("command.claim.success"))
            GivePlayerClaimToolResult.PlayerNotFound ->
                player.sendMessage(lang.msg("general.error"))
        }
    }
}

@Command(name = "claimmenu")
class NexusClaimMenuCommand(private val menuFactory: MenuFactory) {
    @Subcommand("")
    @Permission("lumaguilds.command.claimmenu")
    @PlayerOnly
    fun open(@Context player: Player) {
        val navigator = MenuNavigator(player)
        navigator.openMenu(menuFactory.createClaimListMenu(navigator, player))
    }
}
