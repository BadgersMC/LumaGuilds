package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildGettingStartedService
import net.lumalyte.lg.application.services.GuildStartTopic
import net.lumalyte.lg.application.services.GuildStartStep
import net.lumalyte.lg.application.services.GuildStartState
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.persistence.GuildOnboardingRepository
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.bedrock
import net.lumalyte.lg.interaction.help.HelpTopicsRenderer
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/** Both UIs re-resolve membership and action authority at click time. */
internal class GuildGettingStartedNavigation(private val navigator: MenuNavigator, private val player: Player, private val guildId: UUID?) : KoinComponent {
    private val service: GuildGettingStartedService by inject()
    private val factory: MenuFactory by inject()
    private val prompts: GuildOnboardingRepository by inject()
    private val lang: LangService by inject()
    fun snapshot() = runCatching { service.snapshot(player.uniqueId, guildId) }.getOrNull()
    fun title(topic: GuildStartTopic) = when (topic) {
        GuildStartTopic.MEMBERSHIP -> lang.gui("onboarding.topics.membership")
        GuildStartTopic.INVITES -> lang.gui("onboarding.topics.invites")
        GuildStartTopic.RANKS -> lang.gui("onboarding.topics.ranks")
        GuildStartTopic.CHAT -> lang.gui("onboarding.topics.chat")
        GuildStartTopic.HOME -> lang.gui("onboarding.topics.home")
        GuildStartTopic.QUESTS -> lang.gui("onboarding.topics.quests")
        GuildStartTopic.PROGRESSION -> lang.gui("onboarding.topics.progression")
        GuildStartTopic.BANK -> lang.gui("onboarding.topics.bank")
        GuildStartTopic.STALLS -> lang.gui("onboarding.topics.stalls")
    }
    fun details(topic: GuildStartTopic) = when (topic) {
        GuildStartTopic.MEMBERSHIP -> lang.gui("onboarding.details.membership")
        GuildStartTopic.INVITES -> lang.gui("onboarding.details.invites")
        GuildStartTopic.RANKS -> lang.gui("onboarding.details.ranks")
        GuildStartTopic.CHAT -> lang.gui("onboarding.details.chat")
        GuildStartTopic.HOME -> lang.gui("onboarding.details.home")
        GuildStartTopic.QUESTS -> lang.gui("onboarding.details.quests")
        GuildStartTopic.PROGRESSION -> lang.gui("onboarding.details.progression")
        GuildStartTopic.BANK -> lang.gui("onboarding.details.bank")
        GuildStartTopic.STALLS -> lang.gui("onboarding.details.stalls")
    }
    fun reason(step: GuildStartStep) = when (step.reason) {
        "onboarding.home_unlock" -> lang.gui("onboarding.home_unlock")
        "onboarding.home_costs" -> lang.gui("onboarding.home_costs")
        "onboarding.invite_requirement" -> lang.gui("onboarding.invite_requirement")
        "onboarding.join_first" -> lang.gui("onboarding.join_first")
        "onboarding.permission" -> lang.gui("onboarding.permission")
        else -> details(step.topic)
    }
    fun stateName(state: GuildStartState) = when (state) {
        GuildStartState.COMPLETED -> lang.gui("onboarding.states.completed")
        GuildStartState.AVAILABLE -> lang.gui("onboarding.states.available")
        GuildStartState.LOCKED -> lang.gui("onboarding.states.locked")
    }
    fun bedrockTitle(topic: GuildStartTopic) = PlainTextComponentSerializer.plainText().serialize(title(topic))
    fun bedrockState(state: GuildStartState) = PlainTextComponentSerializer.plainText().serialize(stateName(state))
    fun back() = navigator.goBack()
    fun help() { player.sendMessage(HelpTopicsRenderer.renderTopicMenu(lang)) }
    fun dismiss() {
        val guild = snapshot()?.guild
        if (guild != null && !prompts.dismiss(player.uniqueId, guild.id)) {
            player.sendMessage(lang.msg("onboarding.failed")); return
        }
        player.sendMessage(lang.msg("onboarding.dismissed"))
    }

    fun select(topic: GuildStartTopic) {
        if (!player.isOnline) return
        val current = snapshot() ?: run { player.sendMessage(lang.msg("onboarding.unavailable")); return }
        val step = current.steps.first { it.topic == topic }
        val action = step.action ?: run { player.sendMessage(reason(step)); return }
        val guild = current.guild
        if (guild == null) { navigator.openMenu(factory.createGuildListMenu(navigator, player)); return }
        val menu = when (action) {
            GuildStartTopic.MEMBERSHIP -> factory.createGuildInfoMenu(navigator, player, guild)
            GuildStartTopic.INVITES -> factory.createGuildInviteMenu(navigator, player, guild)
            GuildStartTopic.RANKS -> factory.createGuildRankManagementMenu(navigator, player, guild)
            GuildStartTopic.CHAT -> factory.createPlayerChatSettingsMenu(navigator, player)
            GuildStartTopic.HOME -> factory.createGuildHomeMenu(navigator, player, guild)
            GuildStartTopic.QUESTS -> factory.createGuildQuestsMenu(navigator, player, guild)
            GuildStartTopic.PROGRESSION -> factory.createGuildProgressionMenu(navigator, player, guild)
            GuildStartTopic.BANK -> factory.createGuildBankMenu(navigator, player, guild)
            GuildStartTopic.STALLS -> factory.createGuildStallMenu(navigator, player, guild)
        }
        navigator.openMenu(menu)
    }
}
