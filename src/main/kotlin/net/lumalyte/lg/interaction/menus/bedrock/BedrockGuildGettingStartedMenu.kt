package net.lumalyte.lg.interaction.menus.bedrock

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.guild.GuildGettingStartedNavigation
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.koin.core.component.inject
import java.util.UUID
import java.util.logging.Logger

internal class BedrockGuildGettingStartedMenu(navigator: MenuNavigator, player: Player, guildId: UUID?, logger: Logger) : BaseBedrockMenu(navigator, player, logger) {
    private val navigation = GuildGettingStartedNavigation(navigator, player, guildId)
    private val lang: LangService by inject()
    private val plugin: Plugin by inject()
    override fun shouldCacheForm() = false
    override fun getForm(): Form {
        val token = menuNavigator.currentNavigationToken()
        val current = navigation.snapshot()
        val builder = SimpleForm.builder().title(lang.bedrock("onboarding.title"))
        if (current == null) return builder.content(lang.bedrock("onboarding.unavailable")).button(lang.bedrock("menu.common.item.back.name"))
            .validResultHandler { Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline && menuNavigator.isNavigationCurrent(token)) navigation.back()
            }) }.build()
        val overview = if (current.guild == null) lang.bedrock("onboarding.join_first") else
            lang.bedrock("onboarding.overview", "members" to current.members, "capacity" to current.capacity)
        builder.content(overview + "\n\n" + current.steps.joinToString("\n\n") {
            val details = PlainTextComponentSerializer.plainText().serialize(navigation.details(it.topic))
            val reason = if (it.reason.startsWith("onboarding.details.")) "" else "\n${PlainTextComponentSerializer.plainText().serialize(navigation.reason(it))}"
            "${navigation.bedrockTitle(it.topic)}: $details$reason"
        })
        current.steps.forEach { builder.button("${navigation.bedrockTitle(it.topic)} — ${navigation.bedrockState(it.state)}") }
        builder.button(lang.bedrock("onboarding.help")).button(lang.bedrock("onboarding.refresh"))
            .button(lang.bedrock("onboarding.dismiss")).button(lang.bedrock("menu.common.item.back.name"))
        builder.validResultHandler { response ->
            val selected = response.clickedButtonId()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (player.isOnline && menuNavigator.isNavigationCurrent(token)) select(selected, current.steps.map { it.topic })
            })
        }
        return builder.build()
    }

    private fun select(index: Int, topics: List<net.lumalyte.lg.application.services.GuildStartTopic>) {
        if (index in topics.indices) { navigation.select(topics[index]); return }
        when (index - topics.size) {
            0 -> navigation.help()
            1 -> open()
            2 -> navigation.dismiss()
            3 -> navigation.back()
        }
    }
    override fun handleResponse(player: Player, response: Any?) = Unit
}
