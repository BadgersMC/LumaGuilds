package net.lumalyte.lg.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import org.bukkit.entity.Player

@CommandAlias("pc|pchat|partychat")
class PartyChatCommand(
    dependencies: PartyChatCommandDependencies,
) : BaseCommand() {

    /** Shared framework-free behavior; this class only carries legacy ACF routing metadata. */
    private val handler = PartyChatHandler(dependencies)

    @Default
    @CommandPermission("lumaguilds.partychat")
    fun onPartyChat(player: Player) {
        if (!handler.checkPartiesEnabled(player)) return

        // No arguments - show current party info
        handler.showCurrentPartyInfo(player)
    }

    @Default
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("@parties switch help toggle clear")
    fun onPartyChat(player: Player, firstArg: String) {
        if (!handler.checkPartiesEnabled(player)) return

        // Switch when the argument names a party, otherwise send it as a message
        handler.switchOrSend(player, firstArg, firstArg)
    }

    @Default
    @CommandPermission("lumaguilds.partychat")
    fun onPartyChat(player: Player, firstArg: String, vararg restArgs: String) {
        // Legacy: this overload never applied the parties_enabled guard.
        val fullMessage = (arrayOf(firstArg) + restArgs).joinToString(" ")
        handler.switchOrSend(player, firstArg.takeIf { restArgs.isEmpty() }, fullMessage)
    }

    @Subcommand("switch")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("GLOBAL|@parties")
    fun onSwitch(player: Player, targetName: String) = handler.switchTo(player, targetName)

    @Subcommand("switch")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onSwitchList(player: Player) = handler.switchList(player)

    @Subcommand("toggle")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onToggle(player: Player) = handler.toggle(player)

    @Subcommand("help")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onHelp(player: Player) = handler.help(player)

    @Subcommand("clear")
    @CommandPermission("lumaguilds.partychat")
    @CommandCompletion("")
    fun onClear(player: Player) = handler.clear(player)
}
