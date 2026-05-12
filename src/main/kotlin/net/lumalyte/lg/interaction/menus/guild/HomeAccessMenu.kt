package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val homeName: String
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val menuFactory: MenuFactory by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage("§c❌ You need MANAGE_HOME to configure home access.")
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val home = guildService.getHome(guild.id, homeName)
        if (home == null) {
            player.sendMessage("§cHome '$homeName' no longer exists.")
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val gui = ChestGui(4, "§6Access: $homeName")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(pane)

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val ownerRank = rankService.getHighestRank(guild.id)
        val allowed = home.allowedRankIds.toMutableSet()

        ranks.take(27).forEachIndexed { idx, r ->
            val row = idx / 9
            val col = idx % 9
            val isOwner = r.id == ownerRank?.id
            val on = isOwner || r.id in allowed
            val item = ItemStack.of(
                when {
                    isOwner -> Material.NETHER_STAR
                    on -> Material.LIME_DYE
                    else -> Material.GRAY_DYE
                }
            ).name(if (on) "§a✓ ${r.name}" else "§c✗ ${r.name}")
                .lore("§7Priority: §f${r.priority}")
                .lore("§7")
                .lore(
                    when {
                        isOwner -> "§eOwner — always allowed"
                        on -> "§eClick to revoke"
                        else -> "§eClick to grant"
                    }
                )
            pane.addItem(GuiItem(item) {
                if (isOwner) return@GuiItem
                if (r.id in allowed) allowed.remove(r.id) else allowed.add(r.id)
                guildService.setHomeAllowedRanks(guild.id, homeName, allowed.toSet(), player.uniqueId)
                open()
            }, col, row)
        }

        val backItem = ItemStack.of(Material.ARROW).name("§7← Back to Homes")
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 8, 3)

        gui.show(player)
    }
}
