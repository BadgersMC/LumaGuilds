package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class GuildMemberRankMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetMember: Member
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(4, "§6Rank Management")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Add member info section
        addMemberInfoSection(pane)

        // Add current rank display
        addCurrentRankSection(pane)

        // Add rank selection
        addRankSelectionSection(pane)

        // Add back button
        addBackButton(pane, 4, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addMemberInfoSection(pane: StaticPane) {
        // Member head
        val headItem = createMemberHead()
        pane.addItem(GuiItem(headItem), 0, 0)

        // Member info
        val playerName = Bukkit.getOfflinePlayer(targetMember.playerId)?.name ?: "Unknown Player"
        val infoItem = ItemStack(Material.PAPER)
            .name("§f👤 Member Info")
            .lore("§7Player: §f$playerName")
            .lore("§7Joined: §f${targetMember.joinedAt}")
            .lore("§7")
            .lore("§7Click on a rank below to change")

        pane.addItem(GuiItem(infoItem), 1, 0)
    }

    private fun addCurrentRankSection(pane: StaticPane) {
        val currentRank = rankService.getRank(targetMember.rankId)

        val rankItem = if (currentRank != null) {
            ItemStack(Material.DIAMOND_CHESTPLATE)
                .name("§6🏆 Current Rank")
                .lore("§7Rank: §f${currentRank.name}")
                .lore("§7Priority: §f${currentRank.priority}")
                .lore("§7Permissions: §f${currentRank.permissions.size}")
        } else {
            ItemStack(Material.BARRIER)
                .name("§c❌ Rank Error")
                .lore("§7Could not load current rank")
        }

        pane.addItem(GuiItem(rankItem), 3, 0)
    }

    private fun addRankSelectionSection(pane: StaticPane) {
        val availableRanks = rankService.listRanks(guild.id)
            .sortedByDescending { it.priority } // Highest priority (lowest number) first

        // Show up to 6 ranks in the selection area
        val displayRanks = availableRanks.take(6)

        displayRanks.forEachIndexed { index, rank ->
            val isCurrentRank = rank.id == targetMember.rankId
            val rankItem = ItemStack(if (isCurrentRank) Material.LIME_CONCRETE else Material.GRAY_CONCRETE)
                .name("${if (isCurrentRank) "§a✓" else "§f"} ${rank.name}")
                .lore("§7Priority: §f${rank.priority}")
                .lore("§7Members: §f${memberService.getMembersByRank(guild.id, rank.id).size}")
                .lore("§7Permissions: §f${rank.permissions.size}")
                .lore("§7")
                .lore(if (isCurrentRank) "§aCurrent rank" else "§eClick to select")

            val rankGuiItem = GuiItem(rankItem) {
                if (isCurrentRank) {
                    player.sendMessage("§7This is already their current rank!")
                } else {
                    // Open confirmation menu
                    val menuFactory = MenuFactory()
                    menuNavigator.openMenu(menuFactory.createGuildMemberRankConfirmationMenu(
                        menuNavigator, player, guild, targetMember, rank
                    ))
                }
            }

            // Arrange in a 3x2 grid starting at (0,1)
            val row = 1 + (index / 3)
            val col = index % 3
            pane.addItem(rankGuiItem, col, row)
        }

        // Add scroll indicator if there are more ranks
        if (availableRanks.size > 6) {
            val scrollItem = ItemStack(Material.PAPER)
                .name("§7... and ${availableRanks.size - 6} more")
                .lore("§7Ranks are ordered by priority")
            pane.addItem(GuiItem(scrollItem), 2, 2)
        }
    }

    private fun createMemberHead(): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)

        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(targetMember.playerId).build())

        val meta = head.itemMeta as SkullMeta

        val playerName = Bukkit.getOfflinePlayer(targetMember.playerId)?.name ?: "Unknown Player"

        head.itemMeta = meta

        return head.name("§f👤 $playerName")
            .lore("§7Player: §f$playerName")
            .lore("§7Rank Management")
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .name("§c⬅ BACK")
            .lore("§7Return to guild control panel")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }

    override fun passData(data: Any?) {
        // Handle data passed back from sub-menus if needed
    }
}

