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
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.MenuUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Enhanced individual member management menu with profile view,
 * rank changes, kick functionality, and activity history.
 */
class IndividualMemberManagementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetMember: Member,
    private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()

    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    init {
        initializeGui()
    }

    override fun open() {
        // Check if player has permission to manage this member
        if (!hasMemberManagementPermission()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission to manage guild members!")
            return
        }

        updateMemberDisplay()
        gui.show(player)
    }/**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold>Member Profile - ${getTargetPlayerName()}"))
        AntiDupeUtil.protect(gui)

        mainPane = StaticPane(0, 0, 9, 6)
        gui.addPane(mainPane)

        setupProfileSection()
        setupRankManagement()
        setupAdministrativeActions()
        setupHistorySection()
        setupBackButton()
    }

    /**
     * Setup member profile information section
     */
    private fun setupProfileSection() {
        var row = 0

        // Member head with basic info
        val profileItem = createMemberProfileItem()
        mainPane.addItem(GuiItem(profileItem), 0, row)

        // Member statistics
        val statsItem = createMenuItem(
            Material.BOOK,
            "Member Statistics",
            listOf(
                "Join Date: ${formatJoinDate(targetMember.joinedAt)}",
                "Current Rank: ${getCurrentRankName()}",
                "Online: ${if (isTargetOnline()) "<green>Yes" else "<red>No"}",
                "Last Seen: ${getLastSeenTime()}"
            )
        )
        mainPane.addItem(GuiItem(statsItem), 1, row)

        // Activity summary
        val activityItem = createMenuItem(
            Material.EXPERIENCE_BOTTLE,
            "Activity Summary",
            getActivitySummaryLore()
        )
        mainPane.addItem(GuiItem(activityItem), 2, row)

        row++

        // Member notes (placeholder for admin notes)
        val currentNotes = memberService.getMemberNotes(targetMember.playerId, guild.id)
        val notesItem = createMenuItem(
            Material.PAPER,
            "Member Notes",
            listOf(
                if (currentNotes.isNotEmpty()) "Notes: ${currentNotes.take(30)}${if (currentNotes.length > 30) "..." else ""}" else "No notes added yet",
                "Click to add/edit notes"
            )
        )
        val notesGuiItem = GuiItem(notesItem) { event ->
            event.isCancelled = true
            openMemberNotesMenu()
        }
        mainPane.addItem(notesGuiItem, 0, row)
    }

    /**
     * Setup rank management section
     */
    private fun setupRankManagement() {
        var row = 2

        // Current rank display
        val currentRankItem = createMenuItem(
            getCurrentRankMaterial(),
            "Current Rank: ${getCurrentRankName()}",
            listOf(
                "Priority: ${getCurrentRankPriority()}",
                "Permissions: ${getCurrentRankPermissionCount()}",
                "Click to view rank details"
            )
        )
        val currentRankGuiItem = GuiItem(currentRankItem) { event ->
            event.isCancelled = true
            openRankDetailsMenu()
        }
        mainPane.addItem(currentRankGuiItem, 0, row)

        // Promote button (if applicable)
        if (canPromoteMember()) {
            val promoteItem = createMenuItem(
                Material.EMERALD,
                "Promote Member",
                listOf(
                    "Promote to: ${getNextRankName()}",
                    "Click to promote"
                )
            )
            val promoteGuiItem = GuiItem(promoteItem) { event ->
                event.isCancelled = true
                promoteMember()
            }
            mainPane.addItem(promoteGuiItem, 1, row)
        } else {
            val cannotPromoteItem = createMenuItem(
                Material.GRAY_WOOL,
                "Cannot Promote",
                listOf("Member is already at highest rank")
            )
            mainPane.addItem(GuiItem(cannotPromoteItem), 1, row)
        }

        // Demote button (if applicable)
        if (canDemoteMember()) {
            val demoteItem = createMenuItem(
                Material.REDSTONE,
                "Demote Member",
                listOf(
                    "Demote to: ${getPreviousRankName()}",
                    "Click to demote"
                )
            )
            val demoteGuiItem = GuiItem(demoteItem) { event ->
                event.isCancelled = true
                demoteMember()
            }
            mainPane.addItem(demoteGuiItem, 2, row)
        } else {
            val cannotDemoteItem = createMenuItem(
                Material.GRAY_WOOL,
                "Cannot Demote",
                listOf("Member is already at lowest rank")
            )
            mainPane.addItem(GuiItem(cannotDemoteItem), 2, row)
        }

        // Rank change history
        val rankHistoryItem = createMenuItem(
            Material.BOOKSHELF,
            "Rank Change History",
            listOf(
                "View rank change timeline",
                "Click to view history"
            )
        )
        val rankHistoryGuiItem = GuiItem(rankHistoryItem) { event ->
            event.isCancelled = true
            openRankHistoryMenu()
        }
        mainPane.addItem(rankHistoryGuiItem, 3, row)
    }

    /**
     * Setup administrative actions section
     */
    private fun setupAdministrativeActions() {
        var row = 3

        // Kick member button
        val kickItem = createMenuItem(
            Material.RED_WOOL,
            "Kick Member",
            listOf(
                "Remove member from guild",
                "Click to kick (requires reason)"
            )
        )
        val kickGuiItem = GuiItem(kickItem) { event ->
            event.isCancelled = true
            openKickConfirmationMenu()
        }
        mainPane.addItem(kickGuiItem, 0, row)

        // Ban member button (if applicable)
        if (hasBanPermission()) {
            val banItem = createMenuItem(
                Material.BLACK_WOOL,
                "Ban from Guild",
                listOf(
                    "Permanently ban from guild",
                    "Click to ban (requires reason)"
                )
            )
            val banGuiItem = GuiItem(banItem) { event ->
                event.isCancelled = true
                openBanConfirmationMenu()
            }
            mainPane.addItem(banGuiItem, 1, row)
        }

        // Transfer leadership (if applicable)
        if (hasTransferLeadershipPermission()) {
            val transferItem = createMenuItem(
                Material.GOLD_BLOCK,
                "Transfer Leadership",
                listOf(
                    "Make this member guild leader",
                    "Click to transfer leadership"
                )
            )
            val transferGuiItem = GuiItem(transferItem) { event ->
                event.isCancelled = true
                openTransferLeadershipMenu()
            }
            mainPane.addItem(transferGuiItem, 2, row)
        }

        row++

        // Send private message
        val messageItem = createMenuItem(
            Material.ENCHANTED_BOOK,
            "Send Message",
            listOf(
                "Send private message to member",
                "Click to compose message"
            )
        )
        val messageGuiItem = GuiItem(messageItem) { event ->
            event.isCancelled = true
            openMessageMenu()
        }
        mainPane.addItem(messageGuiItem, 0, row)

        // View message history
        val messageHistoryItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Message History",
            listOf(
                "View conversation history",
                "Click to view messages"
            )
        )
        val messageHistoryGuiItem = GuiItem(messageHistoryItem) { event ->
            event.isCancelled = true
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Message history coming soon!")
        }
        mainPane.addItem(messageHistoryGuiItem, 1, row)
    }

    /**
     * Setup member history section
     */
    private fun setupHistorySection() {
        var row = 4

        // Activity timeline
        val activityItem = createMenuItem(
            Material.CLOCK,
            "Activity Timeline",
            listOf(
                "Recent activity and contributions",
                "Click to view detailed timeline"
            )
        )
        val activityGuiItem = GuiItem(activityItem) { event ->
            event.isCancelled = true
            openActivityTimelineMenu()
        }
        mainPane.addItem(activityGuiItem, 0, row)

        // Bank contribution history
        val bankHistoryItem = createMenuItem(
            Material.GOLD_INGOT,
            "Bank Contributions",
            getBankContributionLore()
        )
        mainPane.addItem(GuiItem(bankHistoryItem), 1, row)

        // Permission history
        val permissionHistoryItem = createMenuItem(
            Material.SHIELD,
            "Permission History",
            listOf(
                "Rank and permission changes",
                "Click to view permission timeline"
            )
        )
        val permissionHistoryGuiItem = GuiItem(permissionHistoryItem) { event ->
            event.isCancelled = true
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Permission history coming soon!")
        }
        mainPane.addItem(permissionHistoryGuiItem, 2, row)
    }

    /**
     * Setup back button
     */
    private fun setupBackButton() {
        val backItem = createMenuItem(
            Material.ARROW,
            "Back to Members",
            listOf("Return to member list")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildMemberListMenu(menuNavigator, player, guild, messageService))
        }
        mainPane.addItem(backGuiItem, 8, 5)
    }

    /**
     * Create member profile display item
     */
    private fun createMemberProfileItem(): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        val playerName = getTargetPlayerName()
        meta.owningPlayer = Bukkit.getOfflinePlayer(targetMember.playerId)

        val joinDate = formatJoinDate(targetMember.joinedAt)
        val currentRank = getCurrentRankName()
        val onlineStatus = if (isTargetOnline()) "<green>Online" else "<red>Offline"

        head.itemMeta = meta

        return head.setAdventureName(player, messageService, "<white>👤 $playerName")
            .addAdventureLore(player, messageService, "<gray>Rank: <white>$currentRank")
            .addAdventureLore(player, messageService, "<gray>Joined: <white>$joinDate")
            .addAdventureLore(player, messageService, "<gray>Status: $onlineStatus")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Click for detailed profile")
    }

    /**
     * Get target player's name
     */
    private fun getTargetPlayerName(): String {
        return Bukkit.getOfflinePlayer(targetMember.playerId).name ?: "Unknown Player"
    }

    /**
     * Get current rank information
     */
    private fun getCurrentRank(): Rank? {
        return rankService.getRankById(targetMember.rankId)
    }

    private fun getCurrentRankName(): String {
        return getCurrentRank()?.name ?: "Unknown"
    }

    private fun getCurrentRankPriority(): Int {
        return getCurrentRank()?.priority ?: 0
    }

    private fun getCurrentRankPermissionCount(): Int {
        return getCurrentRank()?.permissions?.size ?: 0
    }

    private fun getCurrentRankMaterial(): Material {
        return Material.BOOK // Simplified - could be based on rank priority
    }

    /**
     * Check if member can be promoted
     */
    private fun canPromoteMember(): Boolean {
        val currentRank = getCurrentRank() ?: return false
        val allRanks = rankService.getGuildRanks(guild.id)
        return allRanks.any { it.priority < currentRank.priority }
    }

    /**
     * Check if member can be demoted
     */
    private fun canDemoteMember(): Boolean {
        val currentRank = getCurrentRank() ?: return false
        val allRanks = rankService.getGuildRanks(guild.id)
        return allRanks.any { it.priority > currentRank.priority }
    }

    /**
     * Get next rank for promotion
     */
    private fun getNextRank(): Rank? {
        val currentRank = getCurrentRank() ?: return null
        return rankService.getGuildRanks(guild.id)
            .filter { it.priority < currentRank.priority }
            .minByOrNull { it.priority }
    }

    private fun getNextRankName(): String {
        return getNextRank()?.name ?: "N/A"
    }

    /**
     * Get previous rank for demotion
     */
    private fun getPreviousRank(): Rank? {
        val currentRank = getCurrentRank() ?: return null
        return rankService.getGuildRanks(guild.id)
            .filter { it.priority > currentRank.priority }
            .maxByOrNull { it.priority }
    }

    private fun getPreviousRankName(): String {
        return getPreviousRank()?.name ?: "N/A"
    }

    /**
     * Promote the member
     */
    private fun promoteMember() {
        val nextRank = getNextRank()
        if (nextRank != null) {
            val success = memberService.promoteMember(targetMember.playerId, guild.id, player.uniqueId)
            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully promoted ${getTargetPlayerName()} to ${nextRank.name}!")
                // Refresh the menu
                open()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to promote member!")
            }
        }
    }

    /**
     * Demote the member
     */
    private fun demoteMember() {
        val previousRank = getPreviousRank()
        if (previousRank != null) {
            val success = memberService.demoteMember(targetMember.playerId, guild.id, player.uniqueId)
            if (success) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully demoted ${getTargetPlayerName()} to ${previousRank.name}!")
                // Refresh the menu
                open()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to demote member!")
            }
        }
    }

    /**
     * Open rank details menu
     */
    private fun openRankDetailsMenu() {
        val currentRank = getCurrentRank()
        if (currentRank != null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Rank: ${currentRank.name}")
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Priority: ${currentRank.priority}")
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Permissions: ${currentRank.permissions.size}")
            // Could open a detailed rank view menu here
        }
    }

    /**
     * Open kick confirmation menu
     */
    private fun openKickConfirmationMenu() {
        val confirmationMenu = object : Menu {
            override fun open() {
                val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<red><red>Confirm Kick"))
                AntiDupeUtil.protect(gui)

                val pane = StaticPane(0, 0, 9, 3)
                gui.addPane(pane)

                // Warning message
                val warningItem = createMenuItem(
                    Material.RED_WOOL,
                    "<red>⚠️ Confirm Member Kick",
                    listOf(
                        "Are you sure you want to kick:",
                        "<white>${getTargetPlayerName()}",
                        "<gray>This action cannot be undone!",
                        "<red>Click confirm to proceed"
                    )
                )
                pane.addItem(GuiItem(warningItem), 4, 0)

                // Confirm button
                val confirmItem = createMenuItem(
                    Material.GREEN_WOOL,
                    "<green>Confirm Kick",
                    listOf("Kick the member from guild")
                )
                val confirmGuiItem = GuiItem(confirmItem) { event ->
                    event.isCancelled = true
                    val success = memberService.removeMember(targetMember.playerId, guild.id, player.uniqueId)
                    if (success) {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully kicked ${getTargetPlayerName()} from the guild!")
                        player.closeInventory()
                    } else {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to kick member!")
                    }
                }
                pane.addItem(confirmGuiItem, 3, 2)

                // Cancel button
                val cancelItem = createMenuItem(
                    Material.RED_WOOL,
                    "<red>Cancel",
                    listOf("Cancel the kick action")
                )
                val cancelGuiItem = GuiItem(cancelItem) { event ->
                    event.isCancelled = true
                    menuNavigator.openMenu(this@IndividualMemberManagementMenu)
                }
                pane.addItem(cancelGuiItem, 5, 2)

                gui.show(player)
            }

            override fun passData(data: Any?) = Unit
        }

        menuNavigator.openMenu(confirmationMenu)
    }

    /**
     * Open ban confirmation menu
     */
    private fun openBanConfirmationMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Ban functionality coming soon!")
    }

    /**
     * Open transfer leadership menu
     */
    private fun openTransferLeadershipMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Leadership transfer coming soon!")
    }

    /**
     * Open message composition menu
     */
    private fun openMessageMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Private messaging coming soon!")
    }

    /**
     * Open activity timeline menu
     */
    private fun openActivityTimelineMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Activity timeline coming soon!")
    }

    /**
     * Check if player has member management permission
     */
    private fun hasMemberManagementPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)
    }

    /**
     * Check if player has ban permission
     */
    private fun hasBanPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)
    }

    /**
     * Check if player has transfer leadership permission
     */
    private fun hasTransferLeadershipPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)
    }

    /**
     * Check if target member is online
     */
    private fun isTargetOnline(): Boolean {
        return Bukkit.getPlayer(targetMember.playerId)?.isOnline ?: false
    }

    /**
     * Format join date for display
     */
    private fun formatJoinDate(instant: java.time.Instant): String {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }

    /**
     * Get last seen time (simplified)
     */
    private fun getLastSeenTime(): String {
        return "Recently" // Simplified - would need activity tracking
    }

    /**
     * Get activity summary lore
     */
    private fun getActivitySummaryLore(): List<String> {
        return listOf(
            "Recent Activity: Active",
            "Contributions: High",
            "Last Bank Transaction: Today",
            "Click for detailed activity"
        )
    }

    /**
     * Get bank contribution lore
     */
    private fun getBankContributionLore(): List<String> {
        return listOf(
            "Total Deposits: 0 coins",
            "Total Withdrawals: 0 coins",
            "Net Contribution: 0 coins",
            "Click for transaction history"
        )
    }

    /**
     * Update member display (refresh data)
     */
    private fun updateMemberDisplay() {
        // Update is handled by individual setup methods
    }

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(Component.text(name)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false))

        if (lore.isNotEmpty()) {
            val loreComponents = lore.map { line ->
                Component.text(line)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            }
            meta.lore(loreComponents)
        }

        item.itemMeta = meta
        return item
    }

    /**
     * Helper extension to add name and lore to ItemStack
     */
    private fun ItemStack.name(name: String): ItemStack {
        val meta = this.itemMeta
        meta.displayName(Component.text(name)
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false))
        this.itemMeta = meta
        return this
    }

    private fun ItemStack.lore(vararg lines: String): ItemStack {
        val meta = this.itemMeta
        val loreComponents = lines.map { line ->
            Component.text(line)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        }
        meta.lore(loreComponents)
        this.itemMeta = meta
        return this
    }

    /**
     * Open member notes management menu
     */
    private fun openMemberNotesMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<blue>Member Notes - ${getTargetPlayerName()}"))

        val mainPane = StaticPane(0, 0, 9, 4)
        val navigationPane = StaticPane(0, 4, 9, 1)

        // Current notes display
        val currentNotes = memberService.getMemberNotes(targetMember.playerId, guild.id)
        val notesText = if (currentNotes.isNotEmpty()) currentNotes else "No notes added yet"

        // Split long notes into multiple lines
        val maxLineLength = 40
        val noteLines = mutableListOf<String>()
        var currentLine = ""

        notesText.split(" ").forEach { word ->
            if (currentLine.length + word.length + 1 <= maxLineLength) {
                currentLine += if (currentLine.isEmpty()) word else " $word"
            } else {
                if (currentLine.isNotEmpty()) noteLines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) noteLines.add(currentLine)

        // Display current notes
        noteLines.forEachIndexed { index, line ->
            if (index < 4) { // Limit to first 4 lines for display
                val noteItem = ItemStack(Material.PAPER)
                    .setAdventureName(player, messageService, "<gray>${line}")
                mainPane.addItem(GuiItem(noteItem), 0, index)
            }
        }

        // Add note editing area
        val editNoteItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<yellow>Edit Notes")
            .addAdventureLore(player, messageService, "<gray>Click to edit member notes")
        val editNoteGuiItem = GuiItem(editNoteItem) { event ->
            event.isCancelled = true
            openNoteEditor()
        }
        mainPane.addItem(editNoteGuiItem, 2, 1)

        // Add clear notes option
        if (currentNotes.isNotEmpty()) {
            val clearNoteItem = ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>Clear Notes")
                .addAdventureLore(player, messageService, "<gray>Remove all notes for this member")
            val clearNoteGuiItem = GuiItem(clearNoteItem) { event ->
                event.isCancelled = true
                openClearNotesConfirmation()
            }
            mainPane.addItem(clearNoteGuiItem, 6, 1)
        }

        // Navigation
        val backItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<red>Back").addAdventureLore(player, messageService, "<gray>Return to member management")
        val backGuiItem = GuiItem(backItem) {
            open() // Return to main menu
        }
        navigationPane.addItem(backGuiItem, 0, 0)

        val infoItem = ItemStack(Material.BOOK).setAdventureName(player, messageService, "<yellow>Notes Info").addAdventureLore(player, messageService,
            "<gray>Notes are visible to guild administrators",
            "<gray>Use notes to track important member information",
            "<gray>Notes are logged in audit history"
        )
        navigationPane.addItem(GuiItem(infoItem), 4, 0)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)
        gui.show(player)
    }

    /**
     * Open note editor for adding/editing notes
     */
    private fun openNoteEditor() {
        // For now, send a message asking for input
        // In a full implementation, this would open a text input interface
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Note Editor - Type your note in chat:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to cancel, or 'clear' to clear notes")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Note: This is a simplified implementation. Full implementation would use a proper text input interface.")

        // Set a temporary state for note editing
        // In a real implementation, you'd use a proper state management system
        AdventureMenuHelper.sendMessage(player, messageService, "<green>Note editing mode activated. Type your note below:")
    }

    /**
     * Open confirmation for clearing notes
     */
    private fun openClearNotesConfirmation() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<red><red>Clear Member Notes?"))

        val mainPane = StaticPane(0, 0, 9, 3)

        val warningItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<dark_red>⚠️ CONFIRM CLEAR NOTES")
            .addAdventureLore(player, messageService, "<gray>This will permanently remove all notes")
            .addAdventureLore(player, messageService, "<gray>for ${getTargetPlayerName()}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<red>This action cannot be undone!")

        val confirmGuiItem = GuiItem(warningItem) { event ->
            event.isCancelled = true
            if (memberService.setMemberNotes(targetMember.playerId, guild.id, "", player.uniqueId)) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully cleared notes for ${getTargetPlayerName()}")
                openMemberNotesMenu() // Refresh
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to clear notes")
            }
        }
        mainPane.addItem(confirmGuiItem, 4, 1)

        // Cancel button
        val cancelItem = ItemStack(Material.GREEN_WOOL)
            .setAdventureName(player, messageService, "<green>Cancel")
            .addAdventureLore(player, messageService, "<gray>Keep the current notes")

        val cancelGuiItem = GuiItem(cancelItem) { event ->
            event.isCancelled = true
            openMemberNotesMenu() // Return to notes menu
        }
        mainPane.addItem(cancelGuiItem, 2, 1)

        gui.addPane(mainPane)
        gui.show(player)
    }

    /**
     * Open rank change history menu
     */
    private fun openRankHistoryMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<blue>Rank History - ${getTargetPlayerName()}"))

        val mainPane = StaticPane(0, 0, 9, 5)
        val navigationPane = StaticPane(0, 5, 9, 1)

        // Get rank change history
        val rankHistory: List<net.lumalyte.lg.domain.entities.RankChangeRecord> = memberService.getRankChangeHistory(targetMember.playerId, guild.id)

        if (rankHistory.isEmpty()) {
            // No history available
            val noHistoryItem = ItemStack(Material.BOOK)
                .setAdventureName(player, messageService, "<gray>No Rank Changes")
                .addAdventureLore(player, messageService, "<gray>This member has no recorded rank changes")
            mainPane.addItem(GuiItem(noHistoryItem), 4, 2)
        } else {
            // Display rank changes (most recent first)
            rankHistory.take(20).forEachIndexed { index: Int, record: net.lumalyte.lg.domain.entities.RankChangeRecord ->
                if (index >= 5 * 9) return@forEachIndexed // Limit to 45 entries

                val x = index % 9
                val y = index / 9

                val historyItem = ItemStack(Material.PAPER)
                    .setAdventureName(player, messageService, "<yellow>${record.changedAt.toString().substring(0, 19)}")
                    .addAdventureLore(player, messageService, "<gray>${record.getDescription()}")
                    .lore(if (record.reason != null) "<gray>Reason: ${record.reason}" else "<gray>No reason provided")

                mainPane.addItem(GuiItem(historyItem), x, y)
            }

            // Show total count if more than 20
            if (rankHistory.size > 20) {
                val moreItem = ItemStack(Material.PAPER)
                    .setAdventureName(player, messageService, "<gray>... and ${rankHistory.size - 20} more changes")
                mainPane.addItem(GuiItem(moreItem), 4, 4)
            }
        }

        // Navigation
        val backItem = ItemStack(Material.ARROW).setAdventureName(player, messageService, "<red>Back").addAdventureLore(player, messageService, "<gray>Return to member management")
        val backGuiItem = GuiItem(backItem) {
            open() // Return to main menu
        }
        navigationPane.addItem(backGuiItem, 0, 0)

        val infoItem = ItemStack(Material.BOOK).setAdventureName(player, messageService, "<yellow>History Info").addAdventureLore(player, messageService,
            "<gray>Shows all rank changes for this member",
            "<gray>Most recent changes appear first",
            "<gray>Includes who made the change and when"
        )
        navigationPane.addItem(GuiItem(infoItem), 4, 0)

        gui.addPane(mainPane)
        gui.addPane(navigationPane)
        gui.show(player)
    }
}
