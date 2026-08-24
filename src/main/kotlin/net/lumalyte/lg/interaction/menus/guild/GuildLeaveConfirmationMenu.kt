package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberService
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

class GuildLeaveConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.legacy("menu.guild_confirmation.leave.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }
        gui.addPane(pane)

        // Info item
        val infoItem = ItemStack.of(Material.OAK_DOOR)
            .name(lang.legacy("menu.guild_confirmation.leave.item.info.name"))
            .lore(lang.legacy("menu.guild_confirmation.leave.item.info.lore.guild", "guild" to guild.name))
            .lore("")
            .lore(lang.legacy("menu.guild_confirmation.leave.item.info.lore.warning"))
            .lore(lang.legacy("menu.guild_confirmation.leave.item.info.lore.bank"))
            .lore(lang.legacy("menu.guild_confirmation.leave.item.info.lore.homes"))
            .lore(lang.legacy("menu.guild_confirmation.leave.item.info.lore.chat"))
            .lore(lang.legacy("menu.guild_confirmation.leave.item.info.lore.permissions"))
        pane.addItem(GuiItem(infoItem), 4, 0)

        // Confirm leave
        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.legacy("menu.guild_confirmation.leave.item.confirm.name"))
            .lore(lang.legacy("menu.guild_confirmation.leave.item.confirm.lore"))
        pane.addItem(GuiItem(confirmItem) {
            val success = memberService.removeMember(player.uniqueId, guild.id, player.uniqueId)
            if (success) {
                player.sendMessage(lang.msg("menu.guild_confirmation.leave.feedback.success", "guild" to guild.name))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)
                player.closeInventory()
            } else {
                player.sendMessage(lang.msg("menu.guild_confirmation.leave.feedback.failure"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
            }
        }, 3, 2)

        // Cancel
        val cancelItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.legacy("menu.guild_confirmation.common.cancel.name"))
            .lore(lang.legacy("menu.guild_confirmation.common.cancel.lore"))
        pane.addItem(GuiItem(cancelItem) {
            menuNavigator.goBack()
        }, 5, 2)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
