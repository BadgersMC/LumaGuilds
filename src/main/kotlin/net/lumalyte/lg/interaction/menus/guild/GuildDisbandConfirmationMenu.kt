package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildDisbandConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val lang: LangService by inject()

    override fun open() {
        // Permission pre-check before rendering the destructive button
        // (matches the MANAGE_RANKS gate enforced by GuildService.disbandGuild)
        if (!guildService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)) {
            player.sendMessage(lang.msg("menu.guild_confirmation.disband.feedback.no_permission"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.guiTitle("menu.guild_confirmation.disband.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }
        gui.addPane(pane)

        // Info item
        val infoItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.guild_confirmation.disband.item.info.name"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.guild", "guild" to guild.name))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.level", "level" to guild.level))
            .lore("")
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.irreversible"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.info.lore.vault_loss"))
        pane.addItem(GuiItem(infoItem), 4, 0)

        // Confirm button
        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.gui("menu.guild_confirmation.disband.item.confirm.name"))
            .lore(lang.gui("menu.guild_confirmation.disband.item.confirm.lore"))
        pane.addItem(GuiItem(confirmItem) {
            val success = guildService.disbandGuild(guild.id, player.uniqueId)
            if (success) {
                player.sendMessage(lang.msg("menu.guild_confirmation.disband.feedback.success", "guild" to guild.name))
                player.playSound(player.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
                player.closeInventory()
                menuNavigator.clearMenuStack()
            } else {
                player.sendMessage(lang.msg("menu.guild_confirmation.disband.feedback.failure"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }, 3, 2)

        // Cancel button
        val cancelItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.gui("menu.guild_confirmation.common.cancel.name"))
            .lore(lang.gui("menu.guild_confirmation.common.cancel.lore"))
        pane.addItem(GuiItem(cancelItem) {
            menuNavigator.goBack()
        }, 5, 2)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
