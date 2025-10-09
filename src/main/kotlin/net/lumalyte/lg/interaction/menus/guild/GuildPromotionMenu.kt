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

class GuildPromotionMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val guildService: GuildService by inject()

    private var targetMember: Member? = null

    override fun open() {
        // Check permissions
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage member ranks!")
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
            return
        }

        // If no target member is set, open member selection
        if (targetMember == null) {
            openMemberSelection()
            return
        }

        // Show promotion/demotion interface for the selected member
        showPromotionInterface()
    }

    private fun openMemberSelection() {
        // Create member selection menu
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Select Member to Promote/Demote"))

        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Load manageable members
        val manageableMembers = getManageableMembers()

        // Display members (up to 8 per page, simplified for now)
        for ((index, member) in manageableMembers.take(8).withIndex()) {
            val row = 1 + (index / 4)
            val col = (index % 4) * 2 + 1

            addMemberSelectionItem(pane, member, col, row)
        }

        // Back button
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>Back to Control Panel")
            .addAdventureLore(player, messageService, "<gray>Return to guild management")
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 5)

        gui.show(player)
    }

    private fun showPromotionInterface() {
        val member = targetMember ?: return
        val currentRank = rankService.getPlayerRank(member.playerId, guild.id) ?: return
        val allRanks = rankService.listRanks(guild.id).sortedBy { it.priority }

        // Create promotion interface
        val gui = ChestGui(5, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>Promote/Demote ${Bukkit.getOfflinePlayer(member.playerId).name}"))

        val pane = StaticPane(0, 0, 9, 5)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Member info and current rank
        addMemberInfo(pane, member, currentRank)

        // Rows 1-3: Available ranks with promote/demote options
        addRankOptions(pane, allRanks, currentRank)

        // Row 4: Action buttons
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addMemberInfo(pane: StaticPane, member: Member, currentRank: Rank) {
        val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)

        val infoItem = ItemStack(if (offlinePlayer.isOnline) Material.PLAYER_HEAD else Material.SKELETON_SKULL)
            .name("<white>${offlinePlayer.name ?: "Unknown"}")
            .lore(
                "<gray>Current Rank: <white>${currentRank.name}",
                "<gray>Rank Level: <white>${currentRank.priority}",
                "<gray>Joined: <white>${member.joinedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                "<gray>Status: <white>${if (offlinePlayer.isOnline) "§a● Online" else "<gray>● Offline"}"
            )
        pane.addItem(GuiItem(infoItem), 4, 0)
    }

    private fun addRankOptions(pane: StaticPane, allRanks: List<Rank>, currentRank: Rank) {
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id) ?: return
        val manageableRanks = allRanks.filter { it.priority < playerRank.priority && it.priority != currentRank.priority }

        for ((index, rank) in manageableRanks.take(9).withIndex()) {
            val row = 1 + (index / 3)
            val col = (index % 3) * 3

            addRankOption(pane, rank, currentRank, col, row)
        }
    }

    private fun addRankOption(pane: StaticPane, rank: Rank, currentRank: Rank, x: Int, y: Int) {
        val isPromotion = rank.priority > currentRank.priority
        val action = if (isPromotion) "PROMOTE" else "DEMOTE"
        val color = if (isPromotion) "<green>" else "<yellow>"

        // Rank info button
        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "$color${rank.name}")
            .lore(
                "<gray>Level: <white>${rank.priority}",
                "<gray>Permissions: <white>${rank.permissions.size}",
                "<gray>Action: <white>${action}",
                "",
                "<gray>Click to ${action.lowercase()} to this rank"
            )
        pane.addItem(GuiItem(infoItem) { event ->
            event.isCancelled = true
            performRankChange(rank)
        }, x, y)

        // Promote/Demote button
        val actionMaterial = if (isPromotion) Material.GREEN_CONCRETE else Material.YELLOW_CONCRETE
        val actionItem = ItemStack(actionMaterial)
            .setAdventureName(player, messageService, "$color⬆ $action")
            .lore(
                "<gray>Change rank to ${rank.name}",
                "<gray>This will ${if (isPromotion) "give" else "remove"} permissions"
            )
        pane.addItem(GuiItem(actionItem) { event ->
            event.isCancelled = true
            performRankChange(rank)
        }, x + 1, y)
    }

    private fun addActionButtons(pane: StaticPane) {
        // Left: Change member
        val changeItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Change Member")
            .addAdventureLore(player, messageService, "<gray>Select a different member")
        pane.addItem(GuiItem(changeItem) { event ->
            event.isCancelled = true
            targetMember = null
            open()
        }, 1, 4)

        // Center: Cancel
        val cancelItem = ItemStack(Material.RED_CONCRETE)
            .setAdventureName(player, messageService, "<red>Cancel")
            .addAdventureLore(player, messageService, "<gray>Return without changes")
        pane.addItem(GuiItem(cancelItem) { event ->
            event.isCancelled = true
        menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }, 4, 4)

        // Right: Back to member list
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<gray>Back to Member List")
            .addAdventureLore(player, messageService, "<gray>Return to member management")
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(menuFactory.createGuildMemberListMenu(menuNavigator, player, guild))
        }, 7, 4)
    }

    private fun addMemberSelectionItem(pane: StaticPane, member: Member, x: Int, y: Int) {
        val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
        val rank = rankService.getPlayerRank(member.playerId, guild.id)

        val memberItem = ItemStack(if (offlinePlayer.isOnline) Material.PLAYER_HEAD else Material.SKELETON_SKULL)
            .name("<white>${offlinePlayer.name ?: "Unknown"}")
            .lore(
                "<gray>Rank: <white>${rank?.name ?: "Unknown"}",
                "<gray>Joined: <white>${member.joinedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                "",
                "<gray>Click to manage this member"
            )

        pane.addItem(GuiItem(memberItem) { event ->
            event.isCancelled = true
            targetMember = member
            open()
        }, x, y)
    }

    private fun performRankChange(newRank: Rank) {
        val member = targetMember ?: return

        try {
            val success = memberService.changeMemberRank(member.playerId, guild.id, newRank.id, player.uniqueId)

            if (success) {
                val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Successfully changed ${offlinePlayer.name}'s rank to ${newRank.name}!")

                // Refresh the promotion interface to show new current rank
                showPromotionInterface()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to change member rank. Please try again.")
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ An error occurred while changing the rank. Please try again.")
            e.printStackTrace()
        }
    }

    private fun getManageableMembers(): List<Member> {
        val allMembers = memberService.getGuildMembers(guild.id)
        val playerRank = rankService.getPlayerRank(player.uniqueId, guild.id) ?: return emptyList()

        return allMembers.filter { member ->
            // Cannot manage yourself
            if (member.playerId == player.uniqueId) return@filter false

            // Cannot manage owner (highest priority rank)
            val memberRank = rankService.getPlayerRank(member.playerId, guild.id)
            val highestRank = rankService.listRanks(guild.id).maxByOrNull { it.priority }
            if (memberRank?.id == highestRank?.id) return@filter false

            // Can only manage members with lower rank
            (memberRank?.priority ?: 0) < playerRank.priority
        }
    }
}
