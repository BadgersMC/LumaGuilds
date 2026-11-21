package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.commands.annotations.Arg
import net.badgersmc.nexus.commands.annotations.Command
import net.badgersmc.nexus.commands.annotations.Context
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.paper.commands.annotations.Permission
import net.badgersmc.nexus.paper.commands.annotations.PlayerOnly
import net.badgersmc.nexus.paper.commands.annotations.Subcommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.interaction.help.HelpTopics
import net.lumalyte.lg.interaction.help.HelpTopicsRenderer
import org.bukkit.entity.Player

/**
 * First ACF-free handler extracted from GuildCommand. Not registered until the
 * all-at-once Nexus command cutover; the existing /g help remains authoritative.
 */
@Command(name = "guild", aliases = ["g"])
class GuildHelpCommands(private val lang: LangService) {
    @Subcommand("help")
    @Permission("lumaguilds.guild.help")
    @PlayerOnly
    fun help(@Context player: Player, @Arg("topic", required = false) topic: String? = null) {
        if (topic.isNullOrBlank()) {
            player.sendMessage(HelpTopicsRenderer.renderTopicMenu(lang))
            return
        }

        val found = HelpTopics.bySlug(topic)
        if (found == null) {
            player.sendMessage(
                lang.msg("command.guild.help.unknown_topic_prefix")
                    .append(Component.text(topic, NamedTextColor.YELLOW))
                    .append(lang.msg("command.guild.help.unknown_topic_suffix"))
            )
            return
        }
        player.sendMessage(HelpTopicsRenderer.renderTopicPage(found, lang))
    }
}
