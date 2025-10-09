package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ActivityLevel
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.UUID
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Bulk member operations menu for mass rank updates, messaging, and grouping tools.
 */
class GuildBulkMemberOperationsMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val messageService: MessageService
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()

    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    // Selected members for bulk operations
    private var selectedMembers: MutableSet<UUID> = mutableSetOf()
    private var bulkOperationMode: BulkOperationMode = BulkOperationMode.NONE

    init {
        initializeGui()
    }

    override fun open() {
        // Check if player has bulk operations permission
        if (!hasBulkOperationsPermission()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You don't have permission for bulk member operations!")
            return
        }

        updateDisplay()
        gui.show(player)
    }

    override fun passData(data: Any?) {
        if (data is Map<*, *>) {
            data.forEach { (key, value) ->
                when (key) {
                    "operationMode" -> {
                        bulkOperationMode = value as BulkOperationMode
                    }
                }
            }
            updateDisplay()
            gui.update()
        }
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Bulk Member Operations - ${guild.name}"))
        AntiDupeUtil.protect(gui)

        mainPane = StaticPane(0, 0, 9, 6)
        gui.addPane(mainPane)

        setupNavigation()
        setupSelectionSection()
        setupBulkOperations()
        setupMemberList()
        setupBackButton()
    }

    /**
     * Setup navigation and info section
     */
    private fun setupNavigation() {
        var row = 0

        // Operation mode display
        val modeItem = createMenuItem(
            Material.COMPASS,
            "Operation Mode: ${bulkOperationMode.displayName}",
            listOf(
                "Selected: ${selectedMembers.size} members",
                "Click to change mode"
            )
        )
        val modeGuiItem = GuiItem(modeItem) { event ->
            event.isCancelled = true
            openOperationModeMenu()
        }
        mainPane.addItem(modeGuiItem, 0, row)

        // Selection summary
        val selectionItem = createMenuItem(
            Material.BOOK,
            "Member Selection",
            listOf(
                "Selected: ${selectedMembers.size} members",
                "Total members: ${memberService.getMemberCount(guild.id)}",
                "Click to modify selection"
            )
        )
        val selectionGuiItem = GuiItem(selectionItem) { event ->
            event.isCancelled = true
            openMemberSelectionMenu()
        }
        mainPane.addItem(selectionGuiItem, 1, row)

        // Execute operations button
        val executeItem = createMenuItem(
            if (selectedMembers.isEmpty()) Material.GRAY_WOOL else Material.GREEN_WOOL,
            if (selectedMembers.isEmpty()) "No Members Selected" else "Execute Bulk Operation",
            listOf(
                if (selectedMembers.isEmpty()) "Select members first" else "Apply ${bulkOperationMode.displayName} to ${selectedMembers.size} members",
                "Click to execute"
            )
        )
        val executeGuiItem = GuiItem(executeItem) { event ->
            event.isCancelled = true
            if (selectedMembers.isNotEmpty()) {
                executeBulkOperation()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Please select members first!")
            }
        }
        mainPane.addItem(executeGuiItem, 7, row)

        // Clear selection button
        val clearItem = createMenuItem(
            Material.RED_WOOL,
            "Clear Selection",
            listOf("Remove all selected members")
        )
        val clearGuiItem = GuiItem(clearItem) { event ->
            event.isCancelled = true
            selectedMembers.clear()
            updateDisplay()
            gui.update()
            AdventureMenuHelper.sendMessage(player, messageService, "<green>Selection cleared!")
        }
        mainPane.addItem(clearGuiItem, 8, row)
    }

    /**
     * Setup member selection section
     */
    private fun setupSelectionSection() {
        var row = 1

        // Quick selection filters
        val allMembersItem = createMenuItem(
            Material.CHEST,
            "Select All Members",
            listOf("Select all guild members")
        )
        val allMembersGuiItem = GuiItem(allMembersItem) { event ->
            event.isCancelled = true
            selectAllMembers()
        }
        mainPane.addItem(allMembersGuiItem, 0, row)

        val onlineMembersItem = createMenuItem(
            Material.GREEN_WOOL,
            "Select Online Members",
            listOf("Select only online members")
        )
        val onlineMembersGuiItem = GuiItem(onlineMembersItem) { event ->
            event.isCancelled = true
            selectOnlineMembers()
        }
        mainPane.addItem(onlineMembersGuiItem, 1, row)

        val inactiveMembersItem = createMenuItem(
            Material.REDSTONE,
            "Select Inactive Members",
            listOf("Select inactive members (30+ days)")
        )
        val inactiveMembersGuiItem = GuiItem(inactiveMembersItem) { event ->
            event.isCancelled = true
            selectInactiveMembers()
        }
        mainPane.addItem(inactiveMembersGuiItem, 2, row)

        // Advanced selection
        val advancedSelectionItem = createMenuItem(
            Material.ENCHANTED_BOOK,
            "Advanced Selection",
            listOf("Filter by rank, activity, join date")
        )
        val advancedSelectionGuiItem = GuiItem(advancedSelectionItem) { event ->
            event.isCancelled = true
            openAdvancedSelectionMenu()
        }
        mainPane.addItem(advancedSelectionGuiItem, 3, row)
    }

    /**
     * Setup bulk operations section
     */
    private fun setupBulkOperations() {
        var row = 2

        // Bulk rank change
        val rankChangeItem = createMenuItem(
            Material.GOLDEN_SWORD,
            "Bulk Rank Change",
            listOf(
                "Change ranks for multiple members",
                "Select new rank to apply"
            )
        )
        val rankChangeGuiItem = GuiItem(rankChangeItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.RANK_CHANGE
            openRankSelectionMenu()
        }
        mainPane.addItem(rankChangeGuiItem, 0, row)

        // Bulk messaging
        val messageItem = createMenuItem(
            Material.ENCHANTED_BOOK,
            "Bulk Message",
            listOf(
                "Send message to multiple members",
                "Compose message to send"
            )
        )
        val messageGuiItem = GuiItem(messageItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.MESSAGING
            openMessageCompositionMenu()
        }
        mainPane.addItem(messageGuiItem, 1, row)

        // Bulk kick
        val kickItem = createMenuItem(
            Material.RED_WOOL,
            "Bulk Kick",
            listOf(
                "Remove multiple members",
                "Requires kick reason"
            )
        )
        val kickGuiItem = GuiItem(kickItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.KICK
            openKickReasonMenu()
        }
        mainPane.addItem(kickGuiItem, 2, row)

        // Bulk activity check
        val activityItem = createMenuItem(
            Material.EXPERIENCE_BOTTLE,
            "Activity Check",
            listOf(
                "Check activity levels",
                "View member engagement"
            )
        )
        val activityGuiItem = GuiItem(activityItem) { event ->
            event.isCancelled = true
            openActivityCheckMenu()
        }
        mainPane.addItem(activityGuiItem, 3, row)
    }

    /**
     * Setup member list display
     */
    private fun setupMemberList() {
        var row = 3

        // Show selected members (first 6 for preview)
        val selectedList = selectedMembers.take(6)
        for ((index, memberId) in selectedList.withIndex()) {
            val member = memberService.getMember(memberId, guild.id)
            if (member != null) {
                val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown"
                val memberItem = createMemberSelectionItem(member, true)
                mainPane.addItem(GuiItem(memberItem), index % 9, row + (index / 9))
            }
        }

        // Show "..." if more than 6 selected
        if (selectedMembers.size > 6) {
            val moreItem = createMenuItem(
                Material.PAPER,
                "... and ${selectedMembers.size - 6} more",
                listOf("Click to view all selected")
            )
            val moreGuiItem = GuiItem(moreItem) { event ->
                event.isCancelled = true
                openFullSelectionView()
            }
            mainPane.addItem(moreGuiItem, 6, row)
        }
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
     * Open operation mode selection menu
     */
    private fun openOperationModeMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Select Operation Mode"))
        val pane = StaticPane(0, 0, 9, 3)
        AntiDupeUtil.protect(gui)

        var currentRow = 0

        // Rank Change mode
        val rankChangeItem = createMenuItem(
            Material.GOLDEN_SWORD,
            "Rank Changes",
            listOf("Bulk promote/demote members")
        )
        val rankChangeGuiItem = GuiItem(rankChangeItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.RANK_CHANGE
            updateDisplay()
            gui.update()
        }
        pane.addItem(rankChangeGuiItem, 0, currentRow)

        // Messaging mode
        val messageItem = createMenuItem(
            Material.ENCHANTED_BOOK,
            "Messaging",
            listOf("Send messages to multiple members")
        )
        val messageGuiItem = GuiItem(messageItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.MESSAGING
            updateDisplay()
            gui.update()
        }
        pane.addItem(messageGuiItem, 2, currentRow)

        currentRow++

        // Kick mode
        val kickItem = createMenuItem(
            Material.RED_WOOL,
            "Member Removal",
            listOf("Bulk kick members from guild")
        )
        val kickGuiItem = GuiItem(kickItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.KICK
            updateDisplay()
            gui.update()
        }
        pane.addItem(kickGuiItem, 4, currentRow)

        currentRow++

        // Activity Check mode
        val activityItem = createMenuItem(
            Material.EXPERIENCE_BOTTLE,
            "Activity Analysis",
            listOf("Check member activity levels")
        )
        val activityGuiItem = GuiItem(activityItem) { event ->
            event.isCancelled = true
            bulkOperationMode = BulkOperationMode.ACTIVITY_CHECK
            updateDisplay()
            gui.update()
        }
        pane.addItem(activityGuiItem, 6, currentRow)

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back",
            listOf("Return to bulk operations")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Open member selection menu
     */
    private fun openMemberSelectionMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Select Members"))
        val pane = StaticPane(0, 0, 9, 6)
        AntiDupeUtil.protect(gui)

        // Get all guild members
        val allMembers = memberService.getGuildMembers(guild.id)
        var currentRow = 0
        var currentCol = 0

        for (member in allMembers.take(45)) { // Show first 45 members
            val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown"
            val isSelected = selectedMembers.contains(member.playerId)
            val memberItem = createMemberSelectionItem(member, isSelected)

            val guiItem = GuiItem(memberItem) { event ->
                event.isCancelled = true
                if (isSelected) {
                    selectedMembers.remove(member.playerId)
                } else {
                    selectedMembers.add(member.playerId)
                }
                updateDisplay()
                gui.update()
            }
            pane.addItem(guiItem, currentCol, currentRow)

            currentCol++
            if (currentCol >= 9) {
                currentCol = 0
                currentRow++
            }
        }

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back",
            listOf("Return to bulk operations")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Select all guild members
     */
    private fun selectAllMembers() {
        val allMembers = memberService.getGuildMembers(guild.id)
        selectedMembers = allMembers.map { it.playerId }.toMutableSet()
        updateDisplay()
        gui.update()
        AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected all ${selectedMembers.size} members!")
    }

    /**
     * Select only online members
     */
    private fun selectOnlineMembers() {
        val allMembers = memberService.getGuildMembers(guild.id)
        selectedMembers = allMembers
            .filter { Bukkit.getPlayer(it.playerId)?.isOnline == true }
            .map { it.playerId }
            .toMutableSet()
        updateDisplay()
        gui.update()
        AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected ${selectedMembers.size} online members!")
    }

    /**
     * Select inactive members
     */
    private fun selectInactiveMembers() {
        val inactiveMembers = memberService.getInactiveMembers(guild.id, 30)
        selectedMembers = inactiveMembers.map { it.playerId }.toMutableSet()
        updateDisplay()
        gui.update()
        AdventureMenuHelper.sendMessage(player, messageService, "<green>Selected ${selectedMembers.size} inactive members!")
    }

    /**
     * Open advanced selection menu
     */
    private fun openAdvancedSelectionMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Advanced selection menu coming soon!")
    }

    /**
     * Open rank selection for bulk operations
     */
    private fun openRankSelectionMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Select Target Rank"))
        val pane = StaticPane(0, 0, 9, 4)
        AntiDupeUtil.protect(gui)

        val allRanks = rankService.getGuildRanks(guild.id)
        var currentRow = 0
        var currentCol = 0

        for (rank in allRanks) {
            val rankItem = createMenuItem(
                Material.BOOK,
                rank.name,
                listOf(
                    "Priority: ${rank.priority}",
                    "Click to select this rank"
                )
            )
            val rankGuiItem = GuiItem(rankItem) { event ->
                event.isCancelled = true
                openRankChangeConfirmationMenu(rank)
            }
            pane.addItem(rankGuiItem, currentCol, currentRow)

            currentCol++
            if (currentCol >= 9) {
                currentCol = 0
                currentRow++
            }
        }

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back",
            listOf("Return to bulk operations")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Open rank change confirmation menu
     */
    private fun openRankChangeConfirmationMenu(targetRank: Rank) {
        val confirmationMenu = object : Menu {
            override fun open() {
                val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Confirm Rank Change"))
                AntiDupeUtil.protect(gui)

                val pane = StaticPane(0, 0, 9, 3)
                gui.addPane(pane)

                // Warning message
                val warningItem = createMenuItem(
                    Material.ORANGE_WOOL,
                    "<gold>⚠️ Confirm Bulk Rank Change",
                    listOf(
                        "Change ${selectedMembers.size} members to:",
                        "<white>${targetRank.name}",
                        "<gray>This action affects multiple members!",
                        "<gold>Click confirm to proceed"
                    )
                )
                pane.addItem(GuiItem(warningItem), 4, 0)

                // Confirm button
                val confirmItem = createMenuItem(
                    Material.GREEN_WOOL,
                    "<green>Confirm Rank Change",
                    listOf("Apply rank change to all selected members")
                )
                val confirmGuiItem = GuiItem(confirmItem) { event ->
                    event.isCancelled = true
                    val successCount = memberService.bulkChangeRank(guild.id, selectedMembers.toList(), targetRank.id, player.uniqueId)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>Successfully changed rank for $successCount/${selectedMembers.size} members!")
                    selectedMembers.clear()
                    bulkOperationMode = BulkOperationMode.NONE
                    menuNavigator.openMenu(this@GuildBulkMemberOperationsMenu)
                }
                pane.addItem(confirmGuiItem, 3, 2)

                // Cancel button
                val cancelItem = createMenuItem(
                    Material.RED_WOOL,
                    "<red>Cancel",
                    listOf("Cancel the rank change")
                )
                val cancelGuiItem = GuiItem(cancelItem) { event ->
                    event.isCancelled = true
                    menuNavigator.openMenu(this@GuildBulkMemberOperationsMenu)
                }
                pane.addItem(cancelGuiItem, 5, 2)

                gui.show(player)
            }

            override fun passData(data: Any?) = Unit
        }

        menuNavigator.openMenu(confirmationMenu)
    }

    /**
     * Execute the current bulk operation
     */
    private fun executeBulkOperation() {
        when (bulkOperationMode) {
            BulkOperationMode.RANK_CHANGE -> {
                // Handled in rank selection menu
            }
            BulkOperationMode.MESSAGING -> {
                openMessageCompositionMenu()
            }
            BulkOperationMode.KICK -> {
                openKickReasonMenu()
            }
            BulkOperationMode.ACTIVITY_CHECK -> {
                openActivityCheckMenu()
            }
            BulkOperationMode.NONE -> {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>Please select an operation mode first!")
            }
        }
    }

    /**
     * Open message composition menu
     */
    private fun openMessageCompositionMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Message composition coming soon!")
    }

    /**
     * Open kick reason menu
     */
    private fun openKickReasonMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Kick reason input coming soon!")
    }

    /**
     * Open activity check menu
     */
    private fun openActivityCheckMenu() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "Activity Check Results"))
        val pane = StaticPane(0, 0, 9, 4)
        AntiDupeUtil.protect(gui)

        var currentRow = 0

        // Activity level breakdown
        val activityBreakdown = getActivityBreakdown()
        activityBreakdown.forEach { (level, count) ->
            val item = createMenuItem(
                getActivityLevelMaterial(level),
                "${level.name}: $count members",
                listOf("Members with $level activity level")
            )
            pane.addItem(GuiItem(item), 0, currentRow++)
        }

        // Back button
        val backItem = createMenuItem(
            Material.ARROW,
            "Back",
            listOf("Return to bulk operations")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(this)
        }
        pane.addItem(backGuiItem, 8, 3)

        gui.addPane(pane)
        gui.show(player)
    }

    /**
     * Open full selection view
     */
    private fun openFullSelectionView() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Full selection view coming soon!")
    }

    /**
     * Get activity level breakdown for selected members
     */
    private fun getActivityBreakdown(): Map<ActivityLevel, Int> {
        val breakdown = mutableMapOf<ActivityLevel, Int>()
        selectedMembers.forEach { memberId ->
            val stats = memberService.getMemberActivityStats(guild.id, memberId)
            breakdown[stats.activityLevel] = breakdown.getOrDefault(stats.activityLevel, 0) + 1
        }
        return breakdown
    }

    /**
     * Get material for activity level
     */
    private fun getActivityLevelMaterial(level: ActivityLevel): Material {
        return when (level) {
            ActivityLevel.HIGH -> Material.EMERALD
            ActivityLevel.MEDIUM -> Material.GOLD_INGOT
            ActivityLevel.LOW -> Material.REDSTONE
            ActivityLevel.INACTIVE -> Material.COAL
        }
    }

    /**
     * Create member selection item
     */
    private fun createMemberSelectionItem(member: net.lumalyte.lg.domain.entities.Member, isSelected: Boolean): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as org.bukkit.inventory.meta.SkullMeta

        val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: "Unknown"
        meta.owningPlayer = Bukkit.getOfflinePlayer(member.playerId)

        head.itemMeta = meta

        val selectionStatus = if (isSelected) "<green>✓ Selected" else "<gray>○ Not selected"
        val rankName = rankService.getRankById(member.rankId)?.name ?: "Unknown"

        return head.setAdventureName(player, messageService, "<white>👤 $playerName")
            .addAdventureLore(player, messageService, "<gray>Rank: <white>$rankName")
            .addAdventureLore(player, messageService, "<gray>Status: $selectionStatus")
            .addAdventureLore(player, messageService, "<gray>")
            .lore("<yellow>Click to ${if (isSelected) "deselect" else "select"}")
    }

    /**
     * Check if player has bulk operations permission
     */
    private fun hasBulkOperationsPermission(): Boolean {
        return memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)
    }

    /**
     * Update display with current data
     */
    private fun updateDisplay() {
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
}

/**
 * Bulk operation modes
 */
enum class BulkOperationMode(val displayName: String) {
    NONE("None"),
    RANK_CHANGE("Rank Change"),
    MESSAGING("Messaging"),
    KICK("Member Removal"),
    ACTIVITY_CHECK("Activity Check")
}