package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.PenaltyType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuildResolver
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Confirmation step for a single penalty action. Clicking confirm applies the
 * penalty through [PenaltyService]; the strike ledger is intentionally left
 * intact so the public /g strikes view keeps its full history.
 */
class GuildStrikePenaltyConfirmMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val penaltyType: PenaltyType,
    private val strikesConfig: StrikesConfig,
) : Menu, KoinComponent {

    private val penaltyService: PenaltyService by inject()
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("menu.guild_strike_penalty_confirm.title", "type" to penaltyTypeGuiName())))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { event -> event.isCancelled = true }
        gui.setOnBottomClick { event ->
            if (event.click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ||
                event.click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
            ) {
                event.isCancelled = true
            }
        }

        val warning = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.guild_strike_penalty_confirm.warning.name"))
            .lore(lang.gui("menu.guild_strike_penalty_confirm.warning.description"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_strike_penalty_confirm.warning.guild", "guild" to GuildResolver.displayName(guild)))
            .lore(lang.gui("menu.guild_strike_penalty_confirm.warning.penalty", "penalty" to describePenalty()))
        pane.addItem(GuiItem(warning), 0, 0)

        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.gui("menu.guild_strike_penalty_confirm.confirm.name", "type" to penaltyTypeGuiName()))
            .lore(lang.gui("menu.guild_strike_penalty_confirm.confirm.description"))
        pane.addItem(GuiItem(confirmItem) { applyPenalty() }, 4, 1)

        val cancelItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.gui("menu.guild_strike_penalty_confirm.cancel.name"))
            .lore(lang.gui("menu.guild_strike_penalty_confirm.cancel.description"))
            .lore(lang.gui("menu.guild_strike_penalty_confirm.cancel.safe"))
        pane.addItem(GuiItem(cancelItem) {
            menuNavigator.openMenu(GuildStrikePenaltyMenu(menuNavigator, player, guild, strikesConfig))
        }, 6, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun describePenalty(): Component {
        val p = strikesConfig.penalties
        return when (penaltyType) {
            PenaltyType.LEVEL_REDUCTION -> lang.gui("menu.guild_strike_penalty_confirm.description.level", "levels" to p.levelReductionLevels)
            PenaltyType.EXP_REDUCTION -> lang.gui("menu.guild_strike_penalty_confirm.description.experience", "amount" to p.expReductionAmount)
            PenaltyType.GUILD_MUTE -> lang.gui("menu.guild_strike_penalty_confirm.description.mute", "hours" to "%.1f".format(p.guildMuteDurationMillis / 3_600_000.0))
            PenaltyType.DISBAND -> lang.gui("menu.guild_strike_penalty_confirm.description.disband")
        }
    }

    private fun applyPenalty() {
        val result = when (penaltyType) {
            PenaltyType.LEVEL_REDUCTION -> penaltyService.applyLevelReduction(guild, player.uniqueId, player.name)
            PenaltyType.EXP_REDUCTION -> penaltyService.applyExpReduction(guild, player.uniqueId, player.name)
            PenaltyType.GUILD_MUTE -> penaltyService.applyGuildMute(guild, player.uniqueId, player.name)
            PenaltyType.DISBAND -> penaltyService.applyDisband(guild, player.uniqueId, player.name)
        }

        player.closeInventory()

        when (result) {
            is PenaltyService.PenaltyResult.Success -> {
                player.sendMessage(renderPenaltyFeedback(result))
                // Notify online members of their guild being penalized.
                if (penaltyType != PenaltyType.DISBAND) {
                    // One member lookup for the whole guild, then filter online —
                    // avoids N guildService.getPlayerGuilds() calls on the tick thread.
                    val memberUuids = memberService.getGuildMembers(guild.id).map { it.playerId }
                    Bukkit.getOnlinePlayers()
                        .filter { online -> online.uniqueId in memberUuids }
                        .forEach { member ->
                            member.sendMessage(lang.msg("menu.guild_strike_penalty_confirm.feedback.member_notification", "type" to penaltyTypeName()))
                        }
                }
            }
            is PenaltyService.PenaltyResult.Failure -> {
                player.sendMessage(renderPenaltyFeedback(result))
                menuNavigator.openMenu(GuildStrikePenaltyMenu(menuNavigator, player, guild, strikesConfig))
            }
        }
    }

    private fun renderPenaltyFeedback(result: PenaltyService.PenaltyResult) = when (result.feedback) {
        PenaltyService.PenaltyFeedback.LEVEL_DISABLED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.level_disabled")
        PenaltyService.PenaltyFeedback.LEVEL_REDUCED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.level_reduced", "level" to result.parameters["level"], "guild" to result.parameters["guild"])
        PenaltyService.PenaltyFeedback.EXP_DISABLED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.exp_disabled")
        PenaltyService.PenaltyFeedback.EXP_REDUCED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.exp_reduced", "amount" to result.parameters["amount"], "guild" to result.parameters["guild"])
        PenaltyService.PenaltyFeedback.MUTE_DISABLED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.mute_disabled")
        PenaltyService.PenaltyFeedback.MUTED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.muted", "guild" to result.parameters["guild"], "hours" to result.parameters["hours"])
        PenaltyService.PenaltyFeedback.DISBAND_FAILED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.disband_failed", "guild" to result.parameters["guild"])
        PenaltyService.PenaltyFeedback.DISBANDED ->
            lang.msg("menu.guild_strike_penalty_confirm.feedback.disbanded", "guild" to result.parameters["guild"])
    }

    private fun penaltyTypeName(): String = when (penaltyType) {
        PenaltyType.LEVEL_REDUCTION -> lang.raw("menu.guild_strike_penalty_confirm.type.level_reduction")
        PenaltyType.EXP_REDUCTION -> lang.raw("menu.guild_strike_penalty_confirm.type.exp_reduction")
        PenaltyType.GUILD_MUTE -> lang.raw("menu.guild_strike_penalty_confirm.type.guild_mute")
        PenaltyType.DISBAND -> lang.raw("menu.guild_strike_penalty_confirm.type.disband")
    }

    private fun penaltyTypeGuiName(): Component = when (penaltyType) {
        PenaltyType.LEVEL_REDUCTION -> lang.gui("menu.guild_strike_penalty_confirm.type.level_reduction")
        PenaltyType.EXP_REDUCTION -> lang.gui("menu.guild_strike_penalty_confirm.type.exp_reduction")
        PenaltyType.GUILD_MUTE -> lang.gui("menu.guild_strike_penalty_confirm.type.guild_mute")
        PenaltyType.DISBAND -> lang.gui("menu.guild_strike_penalty_confirm.type.disband")
    }
}
