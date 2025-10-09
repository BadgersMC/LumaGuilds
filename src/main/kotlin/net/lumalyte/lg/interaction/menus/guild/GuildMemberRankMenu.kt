package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildMemberRankMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetMember: Member
, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Rank Management"))
        val pane = StaticPane(0, 0, 9, 4)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

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
        val playerName = Bukkit.getPlayer(targetMember.playerId)?.name ?: "Unknown Player"
        val infoItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<white>👤 Member Info")
            .addAdventureLore(player, messageService, "<gray>Player: <white>$playerName")
            .addAdventureLore(player, messageService, "<gray>Joined: <white>${targetMember.joinedAt}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Click on a rank below to change")

        pane.addItem(GuiItem(infoItem), 1, 0)
    }

    private fun addCurrentRankSection(pane: StaticPane) {
        val currentRank = rankService.getRank(targetMember.rankId)

        val rankItem = if (currentRank != null) {
            ItemStack(Material.DIAMOND_CHESTPLATE)
                .setAdventureName(player, messageService, "<gold>🏆 Current Rank")
                .addAdventureLore(player, messageService, "<gray>Rank: <white>${currentRank.name}")
                .addAdventureLore(player, messageService, "<gray>Priority: <white>${currentRank.priority}")
                .addAdventureLore(player, messageService, "<gray>Permissions: <white>${currentRank.permissions.size}")
        } else {
            ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>❌ Rank Error")
                .addAdventureLore(player, messageService, "<gray>Could not load current rank")
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
                .name("${if (isCurrentRank) "<green>✓" else "<white>"} ${rank.name}")
                .addAdventureLore(player, messageService, "<gray>Priority: <white>${rank.priority}")
                .addAdventureLore(player, messageService, "<gray>Members: <white>${memberService.getMembersByRank(guild.id, rank.id).size}")
                .addAdventureLore(player, messageService, "<gray>Permissions: <white>${rank.permissions.size}")
                .addAdventureLore(player, messageService, "<gray>")
                .lore(if (isCurrentRank) "<green>Current rank" else "<yellow>Click to select")

            val rankGuiItem = GuiItem(rankItem) {
                if (isCurrentRank) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>This is already their current rank!")
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
                .setAdventureName(player, messageService, "<gray>... and ${availableRanks.size - 6} more")
                .addAdventureLore(player, messageService, "<gray>Ranks are ordered by priority")
            pane.addItem(GuiItem(scrollItem), 2, 2)
        }
    }

    private fun createMemberHead(): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        val playerName = Bukkit.getPlayer(targetMember.playerId)?.name ?: "Unknown Player"

        // Set skull owner using Craftatar API URL
        try {
            val skullMeta = meta as SkullMeta
            // Use Craftatar API for player heads
            val textureUrl = "https://craftatar.com/avatars/${targetMember.playerId}?size=64&default=MHF_Steve&overlay"
            // Note: In a real implementation, you'd need to set the skull texture properly
            // This is a simplified version - you'd need skull texture utilities
        } catch (e: Exception) {
            // Fallback if skull texture setting fails
        }

        head.itemMeta = meta

        return head.setAdventureName(player, messageService, "<white>👤 $playerName")
            .addAdventureLore(player, messageService, "<gray>Player: <white>$playerName")
            .addAdventureLore(player, messageService, "<gray>Rank Management")
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>⬅️ BACK")
            .addAdventureLore(player, messageService, "<gray>Return to guild control panel")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }}

