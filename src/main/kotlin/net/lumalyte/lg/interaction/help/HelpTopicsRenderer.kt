package net.lumalyte.lg.interaction.help

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component

/** Renders the locale-owned `/g help` MiniMessage components. */
object HelpTopicsRenderer {

    fun renderTopicMenu(lang: LangService): Component {
        val output = Component.text()
            .append(lang.msg("command.guild.help.menu.header"))
            .append(Component.newline())
            .append(lang.msg("command.guild.help.menu.intro"))
            .append(Component.newline())

        HelpTopics.all.forEach { topic ->
            output.append(lang.msg(topic.menuKey)).append(Component.newline())
        }

        return output.append(lang.msg("command.guild.help.menu.wiki")).build()
    }

    fun renderTopicPage(topic: HelpTopic, lang: LangService): Component =
        lang.msg(topic.pageKey)
}
