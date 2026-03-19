package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
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
import org.slf4j.LoggerFactory
import java.util.*

class GuildMemberRankConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetMember: Member,
    private val newRank: Rank
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    private val logger = LoggerFactory.getLogger(GuildMemberRankConfirmationMenu::class.java)

    override fun open() {
        val gui = ChestGui(3, "§6Confirm Rank Change")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Add member info
        addMemberInfo(pane)

        // Add rank change info
        addRankChangeInfo(pane)

        // Add confirmation buttons
        addConfirmationButtons(pane)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addMemberInfo(pane: StaticPane) {
        // Member head
        val headItem = createMemberHead()
        pane.addItem(GuiItem(headItem), 0, 0)

        // Member info
        val playerName = Bukkit.getPlayer(targetMember.playerId)?.name ?: "Unknown Player"
        val infoItem = ItemStack.of(Material.PAPER)
            .name("§f👤 Member Details")
            .lore("§7Player: §f$playerName")
            .lore("§7Guild: §f${guild.name}")

        pane.addItem(GuiItem(infoItem), 1, 0)
    }

    private fun addRankChangeInfo(pane: StaticPane) {
        val currentRank = rankService.getRank(targetMember.rankId)

        // Current rank
        val currentRankItem = if (currentRank != null) {
            ItemStack.of(Material.RED_CONCRETE)
                .name("§c⬇ Current Rank")
                .lore("§7Rank: §f${currentRank.name}")
                .lore("§7Priority: §f${currentRank.priority}")
        } else {
            ItemStack.of(Material.BARRIER)
                .name("§c❌ Current Rank Error")
        }

        // New rank
        val newRankItem = ItemStack.of(Material.GREEN_CONCRETE)
            .name("§a⬆ New Rank")
            .lore("§7Rank: §f${newRank.name}")
            .lore("§7Priority: §f${newRank.priority}")

        // Change direction indicator
        val changeDirection = if (newRank.priority < (currentRank?.priority ?: 0)) {
            "§a⬆ PROMOTION"
        } else {
            "§c⬇ DEMOTION"
        }

        val summaryItem = ItemStack.of(Material.BOOK)
            .name("§6📋 Rank Change Summary")
            .lore("§7$changeDirection")
            .lore("§7From: §f${currentRank?.name ?: "Unknown"}")
            .lore("§7To: §f${newRank.name}")
            .lore("§7Priority change: §f${(currentRank?.priority ?: 0) - newRank.priority}")
            .lore("§7")
            .lore("§7New permissions: §f${newRank.permissions.size}")

        pane.addItem(GuiItem(currentRankItem), 3, 0)
        pane.addItem(GuiItem(summaryItem), 4, 0)
        pane.addItem(GuiItem(newRankItem), 5, 0)
    }

    private fun addConfirmationButtons(pane: StaticPane) {
        // Confirm button
        val confirmItem = ItemStack.of(Material.GREEN_WOOL)
            .name("§a✅ CONFIRM CHANGE")
            .lore("§7Change ${Bukkit.getPlayer(targetMember.playerId)?.name ?: "Unknown"}'s rank")
            .lore("§7to ${newRank.name}")

        val confirmGuiItem = GuiItem(confirmItem) {
            performRankChange()
        }
        pane.addItem(confirmGuiItem, 2, 2)

        // Cancel button
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name("§c❌ CANCEL")
            .lore("§7Return without making changes")

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.goBack()
        }
        pane.addItem(cancelGuiItem, 6, 2)
    }

    private fun performRankChange() {
        try {
            val success = memberService.changeMemberRank(
                targetMember.playerId,
                guild.id,
                newRank.id,
                player.uniqueId
            )

            if (success) {
                val targetName = Bukkit.getPlayer(targetMember.playerId)?.name ?: "Unknown Player"
                val currentRank = rankService.getRank(targetMember.rankId)

                val changeType = if (newRank.priority < (currentRank?.priority ?: 0)) {
                    "promoted"
                } else {
                    "demoted"
                }

                player.sendMessage("§a✅ Successfully $changeType $targetName")
                player.sendMessage("§7New rank: §f${newRank.name}")

                // Notify the target player if they're online
                val targetPlayer = Bukkit.getPlayer(targetMember.playerId)
                if (targetPlayer != null && targetPlayer.isOnline) {
                    targetPlayer.sendMessage("§6🏆 Your rank in ${guild.name} has been changed!")
                    targetPlayer.sendMessage("§7New rank: §f${newRank.name}")
                }

                // Return to member management menu
                menuNavigator.goBack()
            } else {
                player.sendMessage("§c❌ Failed to change member rank!")
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage("§c❌ Error changing member rank: ${e.message}")
            logger.error("Error changing member rank", e)
        }
    }

    private fun createMemberHead(): ItemStack {
        val head = ItemStack.of(Material.PLAYER_HEAD)
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
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback if skull texture setting fails
        }

        head.itemMeta = meta

        return head.name("§f👤 $playerName")
            .lore("§7Player: §f$playerName")
            .lore("§7Confirming rank change")
    }

    override fun passData(data: Any?) {
        // Handle data passed back from sub-menus if needed
    }
}
