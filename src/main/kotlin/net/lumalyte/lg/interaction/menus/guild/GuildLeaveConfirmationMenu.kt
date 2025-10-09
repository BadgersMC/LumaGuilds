package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildLeaveConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                 private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    override fun open() {
        // Check if player is actually in the guild
        if (!memberService.isPlayerInGuild(player.uniqueId, guild.id)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You are not a member of this guild!")
            player.closeInventory()
            return
        }

        // Check if player is the owner - they cannot leave without disbanding
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val highestRank = rankService.listRanks(guild.id).maxByOrNull { it.priority }
        val isOwner = playerRank?.id == highestRank?.id
        if (isOwner) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ As the guild owner, you cannot leave the guild!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Use <red>/guild disband <gray>to permanently delete the guild instead.")
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            return
        }

        val memberCount = memberService.getMemberCount(guild.id)

        // Create 3x3 confirmation GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow><bold>Leave Guild Confirmation"))

        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Guild info display
        addGuildInfoDisplay(pane)

        // Row 1: Consequences info
        addConsequencesDisplay(pane, memberCount)

        // Row 2: Action buttons
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addGuildInfoDisplay(pane: StaticPane) {
        // Center: Guild door/name
        val guildItem = ItemStack(Material.OAK_DOOR)
            .setAdventureName(player, messageService, "<yellow>${guild.name}")
            .lore(
                "<gray>Owner: <white>${getGuildOwnerName()}",
                "<gray>Your Rank: <white>${getPlayerRankName()}",
                "<gray>Joined: <white>${getPlayerJoinDate()}",
                "",
                "<gray>Are you sure you want to leave?"
            )
        pane.addItem(GuiItem(guildItem), 4, 0)
    }

    private fun addConsequencesDisplay(pane: StaticPane, memberCount: Int) {
        // Left: What you'll lose
        val lossItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>What You'll Lose")
            .lore(
                "<gray>• <red>All guild permissions",
                "<gray>• <red>Access to guild bank",
                "<gray>• <red>Rank and title",
                "<gray>• <red>Guild chat access",
                "<gray>• <red>Party participation"
            )
        pane.addItem(GuiItem(lossItem), 1, 1)

        // Center: Guild impact
        val impactItem = ItemStack(Material.PAPER)
            .setAdventureName(player, messageService, "<yellow>Guild Impact")
            .lore(
                "<gray>Guild members: <white>${memberCount - 1}",
                "<gray>Your departure will be <yellow>noticed",
                "<gray>Guild leadership will be <yellow>informed",
                "<gray>No other changes to the guild"
            )
        pane.addItem(GuiItem(impactItem), 4, 1)

        // Right: What you'll keep
        val keepItem = ItemStack(Material.CHEST)
            .setAdventureName(player, messageService, "<green>What You'll Keep")
            .lore(
                "<gray>• <green>All personal items",
                "<gray>• <green>Your homes and claims",
                "<gray>• <green>Your money and inventory",
                "<gray>• <green>Friendships and reputation",
                "<gray>• <green>Ability to join other guilds"
            )
        pane.addItem(GuiItem(keepItem), 7, 1)
    }

    private fun addActionButtons(pane: StaticPane) {
        // Left: Cancel button
        val cancelItem = ItemStack(Material.RED_CONCRETE)
            .setAdventureName(player, messageService, "<red><bold>❌ STAY IN GUILD")
            .lore(
                "<gray>Return to guild management",
                "<gray>You will <green>remain<gray> a member"
            )
        pane.addItem(GuiItem(cancelItem) { event ->
            event.isCancelled = true
            // Return to control panel
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 1, 2)

        // Right: Confirm leave button
        val confirmItem = ItemStack(Material.GREEN_CONCRETE)
            .setAdventureName(player, messageService, "<green><bold>✅ LEAVE GUILD")
            .lore(
                "<gray>Permanently leave <white>${guild.name}",
                "<gray>You can <green>rejoin<gray> later if invited",
                "<gray>This action can be <red>undone<gray> by rejoining"
            )
        pane.addItem(GuiItem(confirmItem) { event ->
            event.isCancelled = true
            performLeave()
        }, 7, 2)
    }

    private fun performLeave() {
        try {
            // Attempt to remove the member from the guild
            val success = memberService.removeMember(player.uniqueId, guild.id, player.uniqueId)

            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ You have successfully left the guild '${guild.name}'!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>You can rejoin later if you receive a new invitation.")

                // Close menu - player is no longer in guild
                player.closeInventory()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to leave the guild. Please try again.")
                // Return to control panel
                menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ An error occurred while leaving the guild. Please try again.")
            e.printStackTrace()
            // Return to control panel
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
    }

    private fun getPlayerRankName(): String {
        // Try to get player's rank name
        return try {
            val rankId = memberService.getPlayerRankId(player.uniqueId, guild.id)
            if (rankId != null) {
                // This is a simplified implementation - in reality we'd need RankService
                "Member" // Placeholder
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getPlayerJoinDate(): String {
        // Try to get player's join date
        return try {
            val member = memberService.getMember(player.uniqueId, guild.id)
            member?.joinedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getGuildOwnerName(): String {
        // Find the guild owner by looking for the member with the highest priority rank
        return try {
            val members = memberService.getGuildMembers(guild.id)
            val ownerMember = members.maxByOrNull { member ->
                rankService.getPlayerRank(member.playerId, guild.id)?.priority ?: 0
            }
            ownerMember?.let { member ->
                val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
                offlinePlayer.name ?: "Unknown"
            } as String? ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }}
