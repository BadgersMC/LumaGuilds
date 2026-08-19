package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder

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

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3))
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
            .name("§c⚠ CONFIRMATION")
            .lore("§cThis action cannot be undone!")
            .lore("§7")
            .lore("§7Guild: §f${GuildResolver.displayName(guild)}")
            .lore("§7Penalty: §f${describePenalty()}")
        pane.addItem(GuiItem(warning), 0, 0)

        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name("§c✅ CONFIRM ${penaltyType.name.replace('_', ' ').uppercase()}")
            .lore("§7Click to apply the penalty")
        pane.addItem(GuiItem(confirmItem) { applyPenalty() }, 4, 1)

        val cancelItem = ItemStack.of(Material.GREEN_WOOL)
            .name("§a❌ CANCEL")
            .lore("§7Return to penalty menu")
            .lore("§7No changes will be made")
        pane.addItem(GuiItem(cancelItem) {
            menuNavigator.openMenu(GuildStrikePenaltyMenu(menuNavigator, player, guild, strikesConfig))
        }, 6, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun describePenalty(): String {
        val p = strikesConfig.penalties
        return when (penaltyType) {
            PenaltyType.LEVEL_REDUCTION -> "Remove ${p.levelReductionLevels} level(s)"
            PenaltyType.EXP_REDUCTION -> "Remove ${p.expReductionAmount} XP"
            PenaltyType.GUILD_MUTE -> "Mute guild chat for ${"%.1f".format(p.guildMuteDurationMillis / 3_600_000.0)} hour(s)"
            PenaltyType.DISBAND -> "FULLY DISBAND the guild"
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
                player.sendMessage("§8[§bLumaGuilds§8] ${result.message}")
                // Notify online members of their guild being penalized.
                if (penaltyType != PenaltyType.DISBAND) {
                    // One member lookup for the whole guild, then filter online —
                    // avoids N guildService.getPlayerGuilds() calls on the tick thread.
                    val memberUuids = memberService.getGuildMembers(guild.id).map { it.playerId }
                    Bukkit.getOnlinePlayers()
                        .filter { online -> online.uniqueId in memberUuids }
                        .forEach { member ->
                            member.sendMessage("§8[§bLumaGuilds§8] §c⚠ Your guild has received a penalty: ${penaltyType.name.replace('_', ' ')}")
                        }
                }
            }
            is PenaltyService.PenaltyResult.Failure -> {
                player.sendMessage("§8[§bLumaGuilds§8] ${result.message}")
                menuNavigator.openMenu(GuildStrikePenaltyMenu(menuNavigator, player, guild, strikesConfig))
            }
        }
    }
}
