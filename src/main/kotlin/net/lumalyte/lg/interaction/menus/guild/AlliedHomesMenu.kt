package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.infrastructure.adapters.bukkit.toPosition3D
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * REQ-180, REQ-182: the guild's own allied homes in one place. Each slot sets or
 * moves the home to where the player stands (left-click) or removes it
 * (right-click); slot 2 shows as locked until Prestige II. The door opens the
 * access page (which allies, ranks and players).
 */
class AlliedHomesMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(guild.guiTheme, ROWS, lang.guiTitle("menu.allied_homes.title")))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(buildPane())
        gui.show(player)
    }

    internal fun buildPane(): StaticPane {
        val pane = StaticPane(0, 0, 9, ROWS)
        pane.addItem(slotItem(1), 1, 0)
        if (guildService.allyHomeSlots(guild.id) >= 2) {
            pane.addItem(slotItem(2), 3, 0)
        } else {
            val locked = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.allied_homes.slot.locked.name"))
                .lore(lang.gui("menu.allied_homes.slot.locked.description"))
            pane.addItem(GuiItem(locked), 3, 0)
        }
        val access = NexoItemProvider.getItemStackOrFallback("lg_ally_access") { ItemStack.of(Material.IRON_DOOR) }
            .name(lang.gui("menu.guild_home.ally_access.name"))
            .lore(lang.gui("menu.allied_homes.access.description"))
        pane.addItem(GuiItem(access) {
            menuNavigator.openMenu(menuFactory.createAllyHomeAccessMenu(menuNavigator, player, guild))
        }, 5, 0)
        val info = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.allied_homes.info.name"))
            .lore(lang.gui("menu.allied_homes.info.line_1", "guild" to guild.name))
            .lore(lang.gui("menu.allied_homes.info.line_2"))
        pane.addItem(GuiItem(info), 7, 0)
        val back = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.allied_homes.back"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 4, ROWS - 1)
        return pane
    }

    private fun slotItem(slot: Int): GuiItem {
        val home = guildService.getAllyHome(guild.id, slot)
        val item = ItemStack.of(if (home == null) Material.WHITE_BED else Material.RED_BED)
            .name(lang.gui("menu.allied_homes.slot.name", "slot" to slot))
        if (home == null) {
            item.lore(lang.gui("menu.allied_homes.slot.unset"))
                .lore(lang.gui("menu.allied_homes.slot.set_here"))
        } else {
            item.lore(lang.gui("menu.allied_homes.slot.at", "world" to worldName(home),
                "x" to home.position.x, "y" to home.position.y, "z" to home.position.z))
                .lore(lang.gui("menu.allied_homes.slot.move_here"))
                .lore(lang.gui("menu.allied_homes.slot.remove"))
        }
        return GuiItem(item) { event ->
            if (event.isRightClick) {
                if (home != null) feedback(guildService.removeAllyHome(guild.id, player.uniqueId, slot), "removed")
            } else {
                val here = GuildHome(player.location.world.uid, player.location.toPosition3D())
                feedback(guildService.setAllyHome(guild.id, here, player.uniqueId, slot), "set")
            }
        }
    }

    private fun feedback(ok: Boolean, action: String) {
        player.sendMessage(
            when {
                !ok -> lang.msg("menu.allied_homes.feedback.failed")
                action == "set" -> lang.msg("menu.allied_homes.feedback.set")
                else -> lang.msg("menu.allied_homes.feedback.removed")
            },
        )
        open()
    }

    private fun worldName(home: GuildHome): String = Bukkit.getWorld(home.worldId)?.name ?: lang.raw("general.unknown")

    private companion object {
        const val ROWS = 3
    }
}
