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
import org.slf4j.LoggerFactory
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildMemberRankConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetMember: Member,
    private val newRank: Rank
, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    private val logger = LoggerFactory.getLogger(GuildMemberRankConfirmationMenu::class.java)

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Confirm Rank Change"))
        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

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
        val infoItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<white>👤 Member Details")
            .addAdventureLore(player, messageService, "<gray>Player: <white>$playerName")
            .addAdventureLore(player, messageService, "<gray>Guild: <white>${guild.name}")

        pane.addItem(GuiItem(infoItem), 1, 0)
    }

    private fun addRankChangeInfo(pane: StaticPane) {
        val currentRank = rankService.getRank(targetMember.rankId)

        // Current rank
        val currentRankItem = if (currentRank != null) {
            ItemStack(Material.RED_CONCRETE)
                .setAdventureName(player, messageService, "<red>⬇️ Current Rank")
                .addAdventureLore(player, messageService, "<gray>Rank: <white>${currentRank.name}")
                .addAdventureLore(player, messageService, "<gray>Priority: <white>${currentRank.priority}")
        } else {
            ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>❌ Current Rank Error")
        }

        // New rank
        val newRankItem = ItemStack(Material.GREEN_CONCRETE)
            .setAdventureName(player, messageService, "<green>⬆️ New Rank")
            .addAdventureLore(player, messageService, "<gray>Rank: <white>${newRank.name}")
            .addAdventureLore(player, messageService, "<gray>Priority: <white>${newRank.priority}")

        // Change direction indicator
        val changeDirection = if (newRank.priority < (currentRank?.priority ?: 0)) {
            "<green>⬆️ PROMOTION"
        } else {
            "<red>⬇️ DEMOTION"
        }

        val summaryItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📋 Rank Change Summary")
            .addAdventureLore(player, messageService, "<gray>$changeDirection")
            .lore("<gray>From: <white>${currentRank?.name ?: "Unknown"}")
            .addAdventureLore(player, messageService, "<gray>To: <white>${newRank.name}")
            .addAdventureLore(player, messageService, "<gray>Priority change: <white>${(currentRank?.priority ?: 0) - newRank.priority}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>New permissions: <white>${newRank.permissions.size}")

        pane.addItem(GuiItem(currentRankItem), 3, 0)
        pane.addItem(GuiItem(summaryItem), 4, 0)
        pane.addItem(GuiItem(newRankItem), 5, 0)
    }

    private fun addConfirmationButtons(pane: StaticPane) {
        // Confirm button
        val confirmItem = ItemStack(Material.GREEN_WOOL)
            .setAdventureName(player, messageService, "<green>✅ CONFIRM CHANGE")
            .lore("<gray>Change ${Bukkit.getPlayer(targetMember.playerId)?.name ?: "Unknown"}'s rank")
            .addAdventureLore(player, messageService, "<gray>to ${newRank.name}")

        val confirmGuiItem = GuiItem(confirmItem) {
            performRankChange()
        }
        pane.addItem(confirmGuiItem, 2, 2)

        // Cancel button
        val cancelItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>❌ CANCEL")
            .addAdventureLore(player, messageService, "<gray>Return without making changes")

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

                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Successfully $changeType $targetName")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>New rank: <white>${newRank.name}")

                // Notify the target player if they're online
                val targetPlayer = Bukkit.getPlayer(targetMember.playerId)
                if (targetPlayer != null && targetPlayer.isOnline) {
                    targetPlayer.sendMessage("<gold>🏆 Your rank in ${guild.name} has been changed!")
                    targetPlayer.sendMessage("<gray>New rank: <white>${newRank.name}")
                }

                // Return to member management menu
                menuNavigator.goBack()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to change member rank!")
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error changing member rank: ${e.message}")
            logger.error("Error changing member rank", e)
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
            .addAdventureLore(player, messageService, "<gray>Confirming rank change")
    }}
