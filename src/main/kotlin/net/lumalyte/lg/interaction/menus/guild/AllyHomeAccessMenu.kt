package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
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

class AllyHomeAccessMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: MenuFactory by inject()

    override fun open() {
        if (!rankService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_HOME)) {
            player.sendMessage("§c❌ You need MANAGE_HOME to configure ally-home access.")
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
            return
        }

        val current = guildService.getGuild(guild.id) ?: return
        val allies: List<Pair<java.util.UUID, String>> = relationService.getGuildRelationsByType(guild.id, RelationType.ALLY)
            .filter { it.isActive() }
            .mapNotNull { rel ->
                val otherId = rel.getOtherGuild(guild.id)
                val otherGuild = guildService.getGuild(otherId)
                if (otherGuild != null) otherId to otherGuild.name else null
            }
        val allowed = current.allyHomeAllowedGuilds.toMutableSet()

        val gui = ChestGui(4, "§6Ally-home Access")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick {
            if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true
        }
        gui.addPane(pane)

        allies.take(18).forEachIndexed { idx, (allyId, allyName) ->
            val row = idx / 9
            val col = idx % 9
            val on = allyId in allowed
            val item = ItemStack.of(if (on) Material.LIME_DYE else Material.GRAY_DYE)
                .name(if (on) "§a✓ $allyName" else "§c✗ $allyName")
                .lore("§7Click to toggle inbound access")
            pane.addItem(GuiItem(item) {
                if (allyId in allowed) allowed.remove(allyId) else allowed.add(allyId)
                guildService.setAllyHomeAllowedGuilds(guild.id, allowed.toSet(), player.uniqueId)
                open()
            }, col, row)
        }

        val info = ItemStack.of(Material.BOOK)
            .name("§eOutbound access (read-only)")
            .lore("§7To control which of YOUR ranks can use ally homes,")
            .lore("§7edit the §fUSE_ALLY_HOMES§7 permission in rank settings.")
        pane.addItem(GuiItem(info), 4, 3)

        val backItem = ItemStack.of(Material.ARROW).name("§7← Back to Homes")
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildHomeMenu(menuNavigator, player, guild))
        }, 8, 3)

        gui.show(player)
    }
}
