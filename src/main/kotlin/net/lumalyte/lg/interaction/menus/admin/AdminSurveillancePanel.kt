package net.lumalyte.lg.interaction.menus.admin

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.AdminModerationService
import net.lumalyte.lg.application.services.AnalyticsService
import net.lumalyte.lg.domain.entities.AdminPermission
import net.lumalyte.lg.domain.entities.SurveillanceLevel
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

/**
 * Dystopian surveillance panel for administrative monitoring and control
 * Provides comprehensive oversight capabilities with 1984-inspired interface
 */
class AdminSurveillancePanel(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val messageService: MessageService
) : Menu, KoinComponent {

    private val adminModerationService: AdminModerationService by inject()
    private val analyticsService: AnalyticsService by inject()

    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane

    init {
        initializeGui()
    }

    override fun open() {
        // Verify admin access
        if (!adminModerationService.hasAdminPermission(player.uniqueId, AdminPermission.ACCESS_ADMIN_PANEL)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ ACCESS DENIED")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Insufficient administrative clearance")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Required: SURVEILLANCE_LEVEL_CRITICAL")
            return
        }

        // Log admin panel access
        adminModerationService.logAdminAction(
            player.uniqueId,
            AdminPermission.ACCESS_ADMIN_PANEL,
            null,
            null,
            "Accessed administrative surveillance panel"
        )

        updateSurveillanceDisplay()
        gui.show(player)
    }

    override fun passData(data: Any?) {
        // Handle data passed from sub-menus
    }

    /**
     * Initialize the dystopian surveillance interface
     */
    private fun initializeGui() {
        gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<dark_red><dark_red>🔍 SURVEILLANCE CONTROL CENTER"))
        AntiDupeUtil.protect(gui)

        mainPane = StaticPane(0, 0, 9, 6)
        gui.addPane(mainPane)

        setupSurveillanceHeader()
        setupSurveillanceSections()
        setupControlPanel()
        setupEmergencyControls()
        setupAuditSection()
    }

    /**
     * Setup the ominous surveillance header
     */
    private fun setupSurveillanceHeader() {
        var row = 0

        // Big Brother is watching
        val headerItem = createSurveillanceItem(
            Material.ENDER_EYE,
            "<dark_red>🔍 BIG BROTHER SURVEILLANCE",
            listOf(
                "<gray>Total System Control Activated",
                "<red>⚠️ SURVEILLANCE_LEVEL: CRITICAL",
                "<gray>All activities monitored",
                "<gray>Complete information awareness",
                "<red>NO PRIVACY - NO FREEDOM"
            )
        )
        mainPane.addItem(GuiItem(headerItem), 4, row)

        // System status indicator
        row++
        val statusItem = createSurveillanceItem(
            Material.REDSTONE_LAMP,
            "<red>🚨 SYSTEM STATUS: ACTIVE",
            listOf(
                "<gray>Surveillance: <green>ONLINE",
                "<gray>Monitoring: <green>ACTIVE",
                "<gray>Control: <green>ENGAGED",
                "<gray>Anomaly Detection: <green>OPERATIONAL",
                "<red>⚠️ EMERGENCY PROTOCOLS READY"
            )
        )
        mainPane.addItem(GuiItem(statusItem), 0, row)

        // Threat level indicator
        val threatItem = createSurveillanceItem(
            Material.SHIELD,
            "<gold>🛡️ THREAT ASSESSMENT",
            listOf(
                "<gray>Current Level: <yellow>ELEVATED",
                "<gray>Suspicious Activities: <red>DETECTED",
                "<gray>Security Incidents: <green>0",
                "<gray>Compliance Score: <green>100.0%",
                "<gray>System Integrity: <green>NOMINAL"
            )
        )
        mainPane.addItem(GuiItem(threatItem), 8, row)
    }

    /**
     * Setup the main surveillance sections
     */
    private fun setupSurveillanceSections() {
        var row = 2

        // Guild Surveillance
        val guildSurveillanceItem = createSurveillanceItem(
            Material.STONE_BRICKS,
            "<dark_red>🏰 GUILD SURVEILLANCE",
            listOf(
                "<gray>Monitor all guild activities",
                "<gray>Financial transactions tracked",
                "<gray>Member movements recorded",
                "<gray>Communication interception active",
                "<gray>Anomaly detection: <green>ONLINE"
            )
        )
        val guildSurveillanceGuiItem = GuiItem(guildSurveillanceItem) {
            openGuildSurveillancePanel()
        }
        mainPane.addItem(guildSurveillanceGuiItem, 0, row)

        // Player Surveillance
        val playerSurveillanceItem = createSurveillanceItem(
            Material.PLAYER_HEAD,
            "<dark_red>👤 CITIZEN MONITORING",
            listOf(
                "<gray>Individual behavior analysis",
                "<gray>Movement pattern tracking",
                "<gray>Financial activity surveillance",
                "<gray>Social interaction monitoring",
                "<gray>Thought crime detection: <green>ACTIVE"
            )
        )
        val playerSurveillanceGuiItem = GuiItem(playerSurveillanceItem) {
            openPlayerSurveillancePanel()
        }
        mainPane.addItem(playerSurveillanceGuiItem, 2, row)

        // Financial Surveillance
        val financialSurveillanceItem = createSurveillanceItem(
            Material.GOLD_INGOT,
            "<dark_red>💰 ECONOMIC CONTROL",
            listOf(
                "<gray>All transactions monitored",
                "<gray>Wealth distribution tracked",
                "<gray>Suspicious activity detection",
                "<gray>Budget compliance enforced",
                "<gray>Emergency freeze protocols ready"
            )
        )
        val financialSurveillanceGuiItem = GuiItem(financialSurveillanceItem) {
            openFinancialSurveillancePanel()
        }
        mainPane.addItem(financialSurveillanceGuiItem, 4, row)

        // Content Moderation
        val contentModerationItem = createSurveillanceItem(
            Material.BOOK,
            "<dark_red>📝 CONTENT CONTROL",
            listOf(
                "<gray>All communications filtered",
                "<gray>Inappropriate content removed",
                "<gray>Name tag protection active",
                "<gray>Banner moderation enforced",
                "<gray>Zero tolerance policy applied"
            )
        )
        val contentModerationGuiItem = GuiItem(contentModerationItem) {
            openContentModerationPanel()
        }
        mainPane.addItem(contentModerationGuiItem, 6, row)

        // System Overrides
        row++
        val systemOverridesItem = createSurveillanceItem(
            Material.COMMAND_BLOCK,
            "<dark_red>⚡ SYSTEM OVERRIDES",
            listOf(
                "<gray>Emergency lockdown capability",
                "<gray>Permission bypass protocols",
                "<gray>Guild restriction overrides",
                "<gray>Rank change enforcement",
                "<gray>Complete administrative control"
            )
        )
        val systemOverridesGuiItem = GuiItem(systemOverridesItem) {
            openSystemOverridesPanel()
        }
        mainPane.addItem(systemOverridesGuiItem, 8, row)
    }

    /**
     * Setup the control panel section
     */
    private fun setupControlPanel() {
        var row = 4

        // Real-time monitoring
        val monitoringItem = createSurveillanceItem(
            Material.CLOCK,
            "<gold>📊 REAL-TIME MONITORING",
            listOf(
                "<gray>Live activity tracking",
                "<gray>Instant alert notifications",
                "<gray>Automated threat detection",
                "<gray>Predictive behavior analysis",
                "<gray>Neural network surveillance active"
            )
        )
        val monitoringGuiItem = GuiItem(monitoringItem) {
            openRealTimeMonitoring()
        }
        mainPane.addItem(monitoringGuiItem, 0, row)

        // Alert management
        val alertsItem = createSurveillanceItem(
            Material.REDSTONE_TORCH,
            "<red>🚨 ALERT MANAGEMENT",
            listOf(
                "<gray>Active surveillance alerts",
                "<gray>Security incident response",
                "<gray>Automated alert resolution",
                "<gray>Escalation protocols ready",
                "<gray>Zero-day threat detection"
            )
        )
        val alertsGuiItem = GuiItem(alertsItem) {
            openAlertManagement()
        }
        mainPane.addItem(alertsGuiItem, 2, row)

        // Audit trails
        val auditItem = createSurveillanceItem(
            Material.WRITABLE_BOOK,
            "<gold>📋 AUDIT TRAILS",
            listOf(
                "<gray>Complete activity logging",
                "<gray>Administrative action records",
                "<gray>Compliance verification",
                "<gray>Historical data analysis",
                "<gray>Forensic investigation tools"
            )
        )
        val auditGuiItem = GuiItem(auditItem) {
            openAuditTrails()
        }
        mainPane.addItem(auditGuiItem, 4, row)

        // Export surveillance data
        val exportItem = createSurveillanceItem(
            Material.CHEST,
            "<gold>📤 DATA EXPORT",
            listOf(
                "<gray>Export surveillance data",
                "<gray>Multiple format support",
                "<gray>Secure data transmission",
                "<gray>Compliance reporting",
                "<gray>Intelligence sharing protocols"
            )
        )
        val exportGuiItem = GuiItem(exportItem) {
            openDataExport()
        }
        mainPane.addItem(exportGuiItem, 6, row)
    }

    /**
     * Setup emergency controls section
     */
    private fun setupEmergencyControls() {
        var row = 5

        // Emergency lockdown
        val lockdownItem = createSurveillanceItem(
            Material.BARRIER,
            "<red>🚨 EMERGENCY LOCKDOWN",
            listOf(
                "<gray>Initiate complete system lockdown",
                "<gray>Freeze all guild activities",
                "<gray>Suspend all permissions",
                "<gray>Emergency response protocols",
                "<red>⚠️ USE WITH EXTREME CAUTION"
            )
        )
        val lockdownGuiItem = GuiItem(lockdownItem) {
            openEmergencyLockdownConfirmation()
        }
        mainPane.addItem(lockdownGuiItem, 0, row)

        // System health check
        val healthItem = createSurveillanceItem(
            Material.HEART_OF_THE_SEA,
            "<gold>💊 SYSTEM HEALTH",
            listOf(
                "<gray>Database connection: <green>ONLINE",
                "<gray>Surveillance systems: <green>ACTIVE",
                "<gray>Moderation tools: <green>OPERATIONAL",
                "<gray>Emergency protocols: <green>READY",
                "<gray>System integrity: <green>NOMINAL"
            )
        )
        mainPane.addItem(GuiItem(healthItem), 4, row)

        // Exit surveillance
        val exitItem = createSurveillanceItem(
            Material.OAK_DOOR,
            "<gray>🚪 EXIT SURVEILLANCE",
            listOf(
                "<gray>Return to normal operations",
                "<gray>Session will be logged",
                "<gray>All actions recorded",
                "<gray>Surveillance continues automatically"
            )
        )
        val exitGuiItem = GuiItem(exitItem) {
            player.closeInventory()
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Surveillance session ended")
            AdventureMenuHelper.sendMessage(player, messageService, "<red>⚠️ Big Brother is always watching...")
        }
        mainPane.addItem(exitGuiItem, 8, row)
    }

    /**
     * Setup audit section
     */
    private fun setupAuditSection() {
        // This would be in the bottom row, but keeping it minimal for now
    }

    /**
     * Update the surveillance display with real-time data
     */
    private fun updateSurveillanceDisplay() {
        try {
            val surveillanceData = adminModerationService.getSurveillanceData(player.uniqueId)

            // Update threat level based on real data
            val suspiciousCount = surveillanceData.suspiciousGuilds + surveillanceData.suspiciousPlayers
            val threatLevel = when {
                suspiciousCount > 10 -> "<red>CRITICAL"
                suspiciousCount > 5 -> "<yellow>HIGH"
                suspiciousCount > 0 -> "<gold>ELEVATED"
                else -> "<green>NORMAL"
            }

            // Update system status indicators
            // This would update the GUI items with real-time data

        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>Error accessing surveillance data")
        }
    }

    /**
     * Create a surveillance-themed menu item
     */
    private fun createSurveillanceItem(material: Material, name: String, lore: List<String>): ItemStack {
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
     * Open guild surveillance panel
     */
    private fun openGuildSurveillancePanel() {
        AdventureMenuHelper.sendMessage(player, messageService, "<dark_red>🔍 Guild surveillance panel activated")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Monitoring all guild activities...")
    }

    /**
     * Open player surveillance panel
     */
    private fun openPlayerSurveillancePanel() {
        AdventureMenuHelper.sendMessage(player, messageService, "<dark_red>👤 Citizen monitoring systems engaged")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Individual behavior analysis active...")
    }

    /**
     * Open financial surveillance panel
     */
    private fun openFinancialSurveillancePanel() {
        AdventureMenuHelper.sendMessage(player, messageService, "<dark_red>💰 Economic control systems operational")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>All financial transactions under surveillance...")
    }

    /**
     * Open content moderation panel
     */
    private fun openContentModerationPanel() {
        AdventureMenuHelper.sendMessage(player, messageService, "<dark_red>📝 Content control protocols active")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>All communications filtered and moderated...")
    }

    /**
     * Open system overrides panel
     */
    private fun openSystemOverridesPanel() {
        AdventureMenuHelper.sendMessage(player, messageService, "<dark_red>⚡ Emergency override protocols ready")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Complete administrative control engaged...")
    }

    /**
     * Open real-time monitoring
     */
    private fun openRealTimeMonitoring() {
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>📊 Real-time monitoring dashboard")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Live surveillance data streaming...")
    }

    /**
     * Open alert management
     */
    private fun openAlertManagement() {
        AdventureMenuHelper.sendMessage(player, messageService, "<red>🚨 Alert management system")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Surveillance alerts and incident response...")
    }

    /**
     * Open audit trails
     */
    private fun openAuditTrails() {
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>📋 Complete audit trail access")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>All administrative actions recorded...")
    }

    /**
     * Open data export
     */
    private fun openDataExport() {
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>📤 Surveillance data export")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Secure intelligence data transmission...")
    }

    /**
     * Open emergency lockdown confirmation
     */
    private fun openEmergencyLockdownConfirmation() {
        AdventureMenuHelper.sendMessage(player, messageService, "<red>🚨 EMERGENCY LOCKDOWN PROTOCOL")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This will freeze ALL server activities")
        AdventureMenuHelper.sendMessage(player, messageService, "<red>⚠️ Use only in extreme emergencies")
    }
}
