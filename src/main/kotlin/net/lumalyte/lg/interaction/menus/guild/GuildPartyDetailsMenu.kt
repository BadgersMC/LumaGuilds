package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.CsvExportService
import net.lumalyte.lg.application.services.FileExportManager
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ItemBankingService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatistics
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
import org.slf4j.LoggerFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildPartyDetailsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                           private val guild: Guild, private val party: Party, private val messageService: MessageService) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val partyService: PartyService by inject()
    private val itemBankingService: ItemBankingService by inject()
    private val csvExportService: CsvExportService by inject()
    private val fileExportManager: FileExportManager by inject()
    private val logger = LoggerFactory.getLogger(GuildPartyDetailsMenu::class.java)

    private var currentPage = 0
    private val membersPerPage = 8

    override fun open() {
        val gui = ChestGui(6, "<gold>Party Details - ${party.name ?: "Unnamed Party"}", )
        val mainPane = StaticPane(0, 0, 9, 6)

        // Add party overview section
        addPartyOverview(mainPane)

        // Add member management section
        addMemberManagement(mainPane)

        // Add party settings section
        addPartySettings(mainPane)

        // Add navigation
        addNavigation(mainPane)

        gui.addPane(mainPane)
        // CRITICAL SECURITY: Prevent item duplication exploits
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        gui.show(player)
    }

    private fun addPartyOverview(pane: StaticPane) {
        // Party Information
        val partyInfo = ItemStack(Material.BOOK)
        val partyInfoMeta = partyInfo.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOK)!!
        partyInfoMeta.setDisplayName("<aqua>Party Information")
        partyInfoMeta.lore = listOf(
            "<gray>Name: <white>${party.name ?: "Unnamed"}",
            "<gray>Status: <white>${getPartyStatusText(party.status)}",
            "<gray>Created: <white>${formatTimestamp(party.createdAt)}",
            "<gray>Expires: <white>${party.expiresAt?.let { formatTimestamp(it) } ?: "Never"}",
            "<gray>Guilds: <white>${party.guildIds.size}",
            "<gray>Leader Guild: <white>${getLeaderGuildName()}"
        )
        partyInfo.itemMeta = partyInfoMeta

        pane.addItem(GuiItem(partyInfo), 0, 0)

        // Party Statistics
        val stats = calculatePartyStats()
        val statsItem = ItemStack(Material.COMPASS)
        val statsMeta = statsItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.COMPASS)!!
        statsMeta.setDisplayName("<green>Party Statistics")
        statsMeta.lore = listOf(
            "<gray>Total Members: <white>${stats.totalMembers}",
            "<gray>Online Members: <white>${stats.onlineMembers}",
            "<gray>Guilds Involved: <white>${party.guildIds.size}",
            "<gray>Activity Level: <white>${stats.activityLevel}",
            "<gray>Duration: <white>${stats.duration}"
        )
        statsItem.itemMeta = statsMeta

        pane.addItem(GuiItem(statsItem), 2, 0)

        // Party Leader
        val leaderItem = ItemStack(Material.GOLDEN_HELMET)
        val leaderMeta = leaderItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.GOLDEN_HELMET)!!
        leaderMeta.setDisplayName("<gold>Party Leader")
        leaderMeta.lore = listOf(
            "<gray>Leader Guild: <white>${getLeaderGuildName()}",
            "<gray>Leader ID: <white>${party.leaderId}",
            "<gray>Leadership Since: <white>${formatTimestamp(party.createdAt)}"
        )
        leaderItem.itemMeta = leaderMeta

        pane.addItem(GuiItem(leaderItem), 4, 0)

        // Party Access Level
        val accessItem = ItemStack(getAccessMaterial(party.accessLevel))
        val accessMeta = accessItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(getAccessMaterial(party.accessLevel))!!
        accessMeta.setDisplayName("<light_purple>Access Level")
        accessMeta.lore = listOf(
            "<gray>Access: <white>${party.accessLevel.name.lowercase().replaceFirstChar { it.uppercase() }}",
            "<gray>Role Restrictions: <white>${if (party.restrictedRoles != null) "Enabled" else "Disabled"}",
            "<gray>Max Members: <white>${party.maxMembers}",
            "<gray>Current Members: <white>${stats.totalMembers}"
        )
        accessItem.itemMeta = accessMeta

        pane.addItem(GuiItem(accessItem), 6, 0)

        // Party Activity
        val activityItem = ItemStack(Material.CLOCK)
        val activityMeta = activityItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.CLOCK)!!
        activityMeta.setDisplayName("<yellow>Recent Activity")
        activityMeta.lore = listOf(
            "<gray>Last Activity: <white>${getLastActivity()}",
            "<gray>Activity Score: <white>${stats.activityScore}/100",
            "<gray>Events Today: <white>${getEventsToday()}",
            "<gray>Participation: <white>${stats.participationRate}%"
        )
        activityItem.itemMeta = activityMeta

        pane.addItem(GuiItem(activityItem), 8, 0)
    }

    private fun addMemberManagement(pane: StaticPane) {
        // Member List
        val memberListItem = ItemStack(Material.PLAYER_HEAD)
        val memberListMeta = memberListItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PLAYER_HEAD)!!
        memberListMeta.setDisplayName("<green>Party Members")
        memberListMeta.lore = listOf(
            "<gray>View all party members",
            "<gray>Manage member roles and permissions",
            "<gray>${getTotalMembers()} total members"
        )
        memberListItem.itemMeta = memberListMeta

        pane.addItem(GuiItem(memberListItem) { _ ->
            openMemberListMenu()
        }, 1, 2)

        // Invite Members
        val inviteItem = ItemStack(Material.EMERALD)
        val inviteMeta = inviteItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.EMERALD)!!
        inviteMeta.setDisplayName("<green>Invite to Party")
        inviteMeta.lore = listOf(
            "<gray>Invite other guilds to join",
            "<gray>Send party invitations",
            "<gray>Manage pending invitations"
        )
        inviteItem.itemMeta = inviteMeta

        pane.addItem(GuiItem(inviteItem) { _ ->
            openInviteMenu()
        }, 3, 2)

        // Member Roles
        val rolesItem = ItemStack(Material.BOOKSHELF)
        val rolesMeta = rolesItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOKSHELF)!!
        rolesMeta.setDisplayName("<aqua>Member Roles")
        rolesMeta.lore = listOf(
            "<gray>Assign roles and permissions",
            "<gray>Manage party leadership",
            "<gray>Configure access levels"
        )
        rolesItem.itemMeta = rolesMeta

        pane.addItem(GuiItem(rolesItem) { _ ->
            openRolesMenu()
        }, 5, 2)

        // Remove Members
        val removeItem = ItemStack(Material.REDSTONE)
        val removeMeta = removeItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.REDSTONE)!!
        removeMeta.setDisplayName("<red>Remove from Party")
        removeMeta.lore = listOf(
            "<gray>Remove guilds from party",
            "<gray>Handle member departures",
            "<gray>Manage party composition"
        )
        removeItem.itemMeta = removeMeta

        pane.addItem(GuiItem(removeItem) { _ ->
            openRemoveMemberMenu()
        }, 7, 2)
    }

    private fun addPartySettings(pane: StaticPane) {
        // Party Settings
        val settingsItem = ItemStack(Material.CRAFTING_TABLE)
        val settingsMeta = settingsItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.CRAFTING_TABLE)!!
        settingsMeta.setDisplayName("<gold>Party Settings")
        settingsMeta.lore = listOf(
            "<gray>Configure party options",
            "<gray>Manage access controls",
            "<gray>Set party preferences"
        )
        settingsItem.itemMeta = settingsMeta

        pane.addItem(GuiItem(settingsItem) { _ ->
            openSettingsMenu()
        }, 0, 3)

        // Party Chat
        val chatItem = ItemStack(Material.PAPER)
        val chatMeta = chatItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.PAPER)!!
        chatMeta.setDisplayName("<aqua>Party Chat")
        chatMeta.lore = listOf(
            "<gray>Party communication settings",
            "<gray>Chat channel management",
            "<gray>Message history"
        )
        chatItem.itemMeta = chatMeta

        pane.addItem(GuiItem(chatItem) { _ ->
            openChatSettingsMenu()
        }, 2, 3)

        // Party Events
        val eventsItem = ItemStack(Material.BOOK)
        val eventsMeta = eventsItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BOOK)!!
        eventsMeta.setDisplayName("<yellow>Party Events")
        eventsMeta.lore = listOf(
            "<gray>Scheduled party activities",
            "<gray>Event planning and coordination",
            "<gray>Meeting schedules"
        )
        eventsItem.itemMeta = eventsMeta

        pane.addItem(GuiItem(eventsItem) { _ ->
            openEventsMenu()
        }, 4, 3)

        // Party Analytics
        val analyticsItem = ItemStack(Material.KNOWLEDGE_BOOK)
        val analyticsMeta = analyticsItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.KNOWLEDGE_BOOK)!!
        analyticsMeta.setDisplayName("<light_purple>Party Analytics")
        analyticsMeta.lore = listOf(
            "<gray>Party performance metrics",
            "<gray>Member engagement stats",
            "<gray>Activity reports"
        )
        analyticsItem.itemMeta = analyticsMeta

        pane.addItem(GuiItem(analyticsItem) { _ ->
            openAnalyticsMenu()
        }, 6, 3)

        // Export Data
        val exportItem = ItemStack(Material.WRITABLE_BOOK)
        val exportMeta = exportItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.WRITABLE_BOOK)!!
        exportMeta.setDisplayName("<aqua>Export Data")
        exportMeta.lore = listOf(
            "<gray>Export item banking data",
            "<gray>Generate CSV reports",
            "<gray>For analysis and compliance"
        )
        exportItem.itemMeta = exportMeta

        pane.addItem(GuiItem(exportItem) { _ ->
            exportItemBankingData()
        }, 8, 3)

        // Dissolve Party
        val dissolveItem = ItemStack(Material.TNT)
        val dissolveMeta = dissolveItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.TNT)!!
        dissolveMeta.setDisplayName("<dark_red><bold>Dissolve Party")
        dissolveMeta.lore = listOf(
            "<gray>Permanently disband this party",
            "<gray>Remove all members and data",
            "<gray>Requires leader confirmation"
        )
        dissolveItem.itemMeta = dissolveMeta

        pane.addItem(GuiItem(dissolveItem) { _ ->
            openDissolveConfirmation()
        }, 8, 3)
    }

    private fun addNavigation(pane: StaticPane) {
        // Back to Party Management
        val backItem = ItemStack(Material.BARRIER)
        val backMeta = backItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.BARRIER)!!
        backMeta.setDisplayName("<red>Back to Party Management")
        backMeta.lore = listOf("<gray>Return to party list")
        backItem.itemMeta = backMeta

        pane.addItem(GuiItem(backItem) { _ ->
            menuNavigator.goBack()
        }, 4, 5)
    }

    // Helper methods for data retrieval
    private fun getLeaderGuildName(): String {
        val leaderGuildId = partyService.getPartyLeader(party.id) ?: party.leaderId
        val leaderGuild = guildService.getGuild(leaderGuildId)
        return leaderGuild?.name ?: "Unknown Guild"
    }

    private fun getPartyStatusText(status: net.lumalyte.lg.domain.entities.PartyStatus): String {
        return when (status) {
            net.lumalyte.lg.domain.entities.PartyStatus.ACTIVE -> "<green>Active"
            net.lumalyte.lg.domain.entities.PartyStatus.DISSOLVED -> "<red>Dissolved"
            net.lumalyte.lg.domain.entities.PartyStatus.EXPIRED -> "<yellow>Expired"
        }
    }

    private fun formatTimestamp(timestamp: Instant): String {
        return timestamp.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }


    private fun calculatePartyStats(): PartyStats {
        val stats = partyService.getPartyStatistics(party.id)
        return if (stats != null) {
            PartyStats(
                totalMembers = stats.totalMembers,
                onlineMembers = stats.onlineMembers,
                activityLevel = stats.activityLevel,
                duration = stats.duration,
                activityScore = stats.activityScore,
                participationRate = stats.participationRate
            )
        } else {
            // Fallback to basic calculation
            val totalMembers = party.guildIds.sumOf { guildId ->
                guildService.getGuild(guildId)?.let { guild ->
                    guild.level * 5 // Mock calculation
                } ?: 0
            }

            PartyStats(
                totalMembers = totalMembers,
                onlineMembers = 0,
                activityLevel = "Unknown",
                duration = "${java.time.Duration.between(party.createdAt, Instant.now()).toDays()} days",
                activityScore = 0,
                participationRate = 0
            )
        }
    }

    private fun getTotalMembers(): Int {
        return calculatePartyStats().totalMembers
    }

    private fun getLastActivity(): String {
        // This would need actual activity tracking
        return "2 hours ago"
    }

    private fun getEventsToday(): Int {
        // This would need actual event tracking
        return 3
    }

    private fun getAccessMaterial(accessLevel: net.lumalyte.lg.domain.entities.PartyAccessLevel): Material {
        return when (accessLevel) {
            net.lumalyte.lg.domain.entities.PartyAccessLevel.OPEN -> Material.EMERALD_BLOCK
            net.lumalyte.lg.domain.entities.PartyAccessLevel.INVITE_ONLY -> Material.GOLD_BLOCK
            net.lumalyte.lg.domain.entities.PartyAccessLevel.GUILD_ONLY -> Material.DIAMOND_BLOCK
            net.lumalyte.lg.domain.entities.PartyAccessLevel.LEADER_ONLY -> Material.NETHER_STAR
        }
    }

    // Menu opening methods
    private fun openMemberListMenu() {
        val menu = GuildPartyMemberListMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openInviteMenu() {
        val menu = GuildPartyInviteMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openRolesMenu() {
        val menu = GuildPartyRolesMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openRemoveMemberMenu() {
        val menu = GuildPartyRemoveMemberMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openSettingsMenu() {
        val menu = GuildPartyAccessSettingsMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openChatSettingsMenu() {
        val menu = GuildPartyChatSettingsMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openEventsMenu() {
        val menu = GuildPartyEventsMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openAnalyticsMenu() {
        val menu = GuildPartyAnalyticsMenu(menuNavigator, player, guild, party, messageService)
        menu.open()
    }

    private fun openDissolveConfirmation() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>Dissolve Party Confirmation"))
        val pane = StaticPane(0, 0, 9, 3)

        val confirmItem = ItemStack(Material.RED_WOOL)
        val confirmMeta = confirmItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.RED_WOOL)!!
        confirmMeta.setDisplayName("<dark_red><bold>Confirm Dissolution")
        confirmMeta.lore = listOf(
            "<gray>Are you sure you want to dissolve this party?",
            "<gray>This action cannot be undone.",
            "<gray>All members will be removed.",
            "<gray>Party data will be lost."
        )
        confirmItem.itemMeta = confirmMeta

        pane.addItem(GuiItem(confirmItem) { _ ->
            performDissolveParty()
            AdventureMenuHelper.sendMessage(player, messageService, "<dark_red>Party has been dissolved.")
            player.closeInventory()
        }, 3, 1)

        val cancelItem = ItemStack(Material.GRAY_WOOL)
        val cancelMeta = cancelItem.itemMeta ?: Bukkit.getItemFactory().getItemMeta(Material.GRAY_WOOL)!!
        cancelMeta.setDisplayName("<green>Cancel")
        cancelMeta.lore = listOf("<gray>Keep the party active.")
        cancelItem.itemMeta = cancelMeta
        pane.addItem(GuiItem(cancelItem) { _ ->
            player.closeInventory()
        }, 5, 1)

        gui.addPane(pane)
        AntiDupeUtil.protect(gui)
        gui.setOnGlobalClick { event -> event.isCancelled = true }
        gui.show(player)
    }

    private fun performDissolveParty() {
        partyService.dissolveParty(party.id, player.uniqueId)
    }

    private fun exportItemBankingData() {
        try {
            // Get guild chests
            val chests = itemBankingService.getGuildChests(guild.id)

            // Get access logs
            val accessLogs = mutableListOf<net.lumalyte.lg.domain.entities.GuildChestAccessLog>()
            chests.forEach { chest ->
                accessLogs.addAll(itemBankingService.getChestAccessLogs(chest.id))
            }

            // Calculate totals
            val totalItems = accessLogs.sumOf { it.itemAmount }
            val totalValue = accessLogs.sumOf {
                itemBankingService.getItemValue("DIAMOND", it.itemAmount) // Simplified calculation
            }

            // Export asynchronously using the file export manager
            fileExportManager.exportItemBankingDataAsync(
                player = player,
                guildId = guild.id,
                guildName = guild.name,
                chests = chests,
                accessLogs = accessLogs.take(100), // Limit to 100 logs for performance
                totalItems = totalItems,
                totalValue = totalValue
            ) { result ->
                when (result) {
                    is FileExportManager.ExportResult.Success -> {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>Item banking data exported successfully!")
                        AdventureMenuHelper.sendMessage(player, messageService, "<gray>File: ${result.fileName} (${result.fileSize} bytes)")
                    }
                    is FileExportManager.ExportResult.DiscordSuccess -> {
                        AdventureMenuHelper.sendMessage(player, messageService, "<green>Item banking data exported to Discord successfully!")
                    }
                    is FileExportManager.ExportResult.Error -> {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Export failed: ${result.message}")
                    }
                    is FileExportManager.ExportResult.RateLimited -> {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Export failed: ${result.message}")
                    }
                    is FileExportManager.ExportResult.FileTooLarge -> {
                        AdventureMenuHelper.sendMessage(player, messageService, "<red>Export failed: ${result.message}")
                    }
                }
            }

        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Failed to export item banking data.")
            logger.error("Error exporting item banking data", e)
        }
    }

    // Mock data class for party statistics
    private data class PartyStats(
        val totalMembers: Int,
        val onlineMembers: Int,
        val activityLevel: String,
        val duration: String,
        val activityScore: Int,
        val participationRate: Int
    )
}
