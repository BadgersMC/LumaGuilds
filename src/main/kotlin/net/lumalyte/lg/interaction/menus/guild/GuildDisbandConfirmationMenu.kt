package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
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

class GuildDisbandConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                   private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    override fun open() {
        // Check if player is the guild owner (highest priority rank)
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id)
        val highestRank = rankService.listRanks(guild.id).maxByOrNull { it.priority }
        val isOwner = playerRank?.id == highestRank?.id

        if (!isOwner) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Only the guild owner can disband the guild!")
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            return
        }

        val memberCount = memberService.getMemberCount(guild.id)

        // Create 3x3 confirmation GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<red><red><bold>⚠ DISBAND GUILD ⚠"))

        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Guild info display
        addGuildInfoDisplay(pane)

        // Row 1: Consequences warning
        addConsequencesDisplay(pane, memberCount)

        // Row 2: Action buttons
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addGuildInfoDisplay(pane: StaticPane) {
        // Center: Guild skull/name
        val guildItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<red><bold>${guild.name}")
            .lore(
                "<gray>Owner: <white>${getGuildOwnerName()}",
                "<gray>Founded: <white>${guild.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                "<gray>Level: <white>${guild.level}",
                "",
                "<red><bold>⚠ THIS ACTION CANNOT BE UNDONE ⚠"
            )
        pane.addItem(GuiItem(guildItem), 4, 0)
    }

    private fun addConsequencesDisplay(pane: StaticPane, memberCount: Int) {
        // Left: Member impact
        val membersItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<yellow>Member Impact")
            .lore(
                "<gray>Members affected: <red>$memberCount",
                "<gray>All members will be <red>removed",
                "<gray>All ranks will be <red>lost",
                "<gray>Bank funds will be <red>lost"
            )
        pane.addItem(GuiItem(membersItem), 1, 1)

        // Center: Warning details
        val warningItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red><bold>IRREVERSIBLE ACTION")
            .lore(
                "<red>This will permanently delete:",
                "<gray>• Guild data and settings",
                "<gray>• All member relationships",
                "<gray>• Bank transactions history",
                "<gray>• War declarations and history",
                "<gray>• All guild achievements",
                "",
                "<red><bold>NO RECOVERY POSSIBLE!"
            )
        pane.addItem(GuiItem(warningItem), 4, 1)

        // Right: Permission loss
        val permsItem = ItemStack(Material.REDSTONE)
            .setAdventureName(player, messageService, "<yellow>Permission Loss")
            .lore(
                "<gray>All permissions will be <red>revoked",
                "<gray>Homes and claims <red>will remain",
                "<gray>But become <red>unmanageable",
                "<gray>Leaderboards will <red>forgot guild"
            )
        pane.addItem(GuiItem(permsItem), 7, 1)
    }

    private fun addActionButtons(pane: StaticPane) {
        // Left: Cancel button
        val cancelItem = ItemStack(Material.RED_CONCRETE)
            .setAdventureName(player, messageService, "<red><bold>❌ CANCEL")
            .lore(
                "<gray>Return to guild management",
                "<gray>Guild will <green>not<gray> be disbanded"
            )
        pane.addItem(GuiItem(cancelItem) { event ->
            event.isCancelled = true
            // Return to control panel
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 1, 2)

        // Right: Confirm disband button
        val confirmItem = ItemStack(Material.GREEN_CONCRETE)
            .setAdventureName(player, messageService, "<green><bold>✅ CONFIRM DISBAND")
            .lore(
                "<red><bold>⚠ PERMANENTLY DELETE GUILD ⚠",
                "<gray>This action cannot be undone!",
                "<gray>All data will be lost forever!"
            )
        pane.addItem(GuiItem(confirmItem) { event ->
            event.isCancelled = true
            performDisband()
        }, 7, 2)
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
            } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun performDisband() {
        try {
            // Attempt to disband the guild
            val success = guildService.disbandGuild(guild.id, player.uniqueId)

            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Guild '${guild.name}' has been successfully disbanded!")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>All members have been notified and removed from the guild.")

                // Close menu and return to main menu (guild is gone)
                player.closeInventory()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to disband guild. Please try again or contact an administrator.")
                // Return to control panel
                menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ An error occurred while disbanding the guild. Please try again.")
            e.printStackTrace()
            // Return to control panel
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
    }}
