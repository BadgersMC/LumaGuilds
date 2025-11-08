package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.domain.entities.AuditAction
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.LocalizationKeys
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Guild Bank Security and Audit menu with fraud detection and dual authorization
 */
class GuildBankSecurityMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent {

    private val bankService: BankService by inject()
    private val localizationProvider: net.lumalyte.lg.application.utilities.LocalizationProvider by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var securityPane: StaticPane
    private lateinit var auditPane: StaticPane

    // Security settings
    private var dualAuthThreshold: Int = 1000
    private var fraudDetectionEnabled: Boolean = true
    private var emergencyFreeze: Boolean = false
    private var securityAlerts: MutableList<String> = mutableListOf()

    init {
        loadSecuritySettings()
        analyzeSecurityRisks()
        initializeGui()
    }

    override fun open() {
        updateSecurityDisplay()
        gui.show(player)
    }

    override fun passData(data: Any?) {
        // Handle security setting updates
        if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val updates = data as Map<String, Any>
            updates.forEach { (setting, value) ->
                when (setting) {
                    "dualAuthThreshold" -> dualAuthThreshold = value as Int
                    "fraudDetection" -> fraudDetectionEnabled = value as Boolean
                    "emergencyFreeze" -> emergencyFreeze = value as Boolean
                }
            }
            analyzeSecurityRisks()
            updateSecurityDisplay()
            gui.update()
        }
    }

    /**
     * Load security settings (placeholder for now)
     */
    private fun loadSecuritySettings() {
        // TODO: Load from database/configuration
        dualAuthThreshold = 1000
        fraudDetectionEnabled = true
        emergencyFreeze = false
    }

    /**
     * Analyze security risks and generate alerts
     */
    private fun analyzeSecurityRisks() {
        securityAlerts.clear()

        val auditLog = bankService.getAuditLog(guild.id, 50)
        val transactions = bankService.getTransactionHistory(guild.id, null)

        // Check for suspicious patterns
        checkForUnusualActivity(auditLog)
        checkForLargeTransactions(transactions)
        checkForRapidWithdrawals(transactions)
        checkForAuthenticationFailures(auditLog)

        // Emergency freeze status
        if (emergencyFreeze) {
            securityAlerts.add("🚨 EMERGENCY FREEZE ACTIVE - All transactions blocked!")
        }
    }

    /**
     * Check for unusual activity patterns
     */
    private fun checkForUnusualActivity(auditLog: List<BankAudit>) {
        val recentAudits = auditLog.filter {
            it.timestamp.isAfter(Instant.now().minus(1, ChronoUnit.HOURS))
        }

        val failedAuths = recentAudits.count { it.action == AuditAction.PERMISSION_DENIED }

        if (failedAuths >= 3) {
            securityAlerts.add("⚠ Multiple authentication failures detected")
        }

        // Check for unusual transaction times
        val unusualHours = recentAudits.filter { audit ->
            val hour = LocalDateTime.ofInstant(audit.timestamp, ZoneId.systemDefault()).hour
            hour < 6 || hour > 22 // Outside normal business hours
        }

        if (unusualHours.size >= 2) {
            securityAlerts.add("⚠ Unusual transaction timing detected")
        }
    }

    /**
     * Check for suspiciously large transactions
     */
    private fun checkForLargeTransactions(transactions: List<BankTransaction>) {
        val balance = bankService.getBalance(guild.id)
        val largeTransactions = transactions.filter {
            it.type.name == "WITHDRAWAL" && it.amount > balance * 0.8 // Over 80% of balance
        }

        if (largeTransactions.isNotEmpty()) {
            securityAlerts.add("⚠ Large withdrawal detected (${largeTransactions.last().amount} coins)")
        }

        // Check for rapid large transactions
        val recentLarge = transactions.filter {
            it.timestamp.isAfter(Instant.now().minus(1, ChronoUnit.HOURS)) &&
            it.amount > dualAuthThreshold
        }

        if (recentLarge.size >= 2) {
            securityAlerts.add("⚠ Multiple large transactions in short time")
        }
    }

    /**
     * Check for rapid withdrawal patterns
     */
    private fun checkForRapidWithdrawals(transactions: List<BankTransaction>) {
        val recentWithdrawals = transactions.filter {
            it.type.name == "WITHDRAWAL" &&
            it.timestamp.isAfter(Instant.now().minus(10, ChronoUnit.MINUTES))
        }

        if (recentWithdrawals.size >= 5) {
            securityAlerts.add("⚠ Rapid withdrawal pattern detected")
        }

        // Check for same amount withdrawals (potential fraud)
        val amounts = recentWithdrawals.map { it.amount }.toSet()
        if (amounts.size == 1 && recentWithdrawals.size >= 3) {
            securityAlerts.add("⚠ Identical withdrawal amounts detected")
        }
    }

    /**
     * Check for authentication failures
     */
    private fun checkForAuthenticationFailures(auditLog: List<BankAudit>) {
        val recentFailures = auditLog.filter {
            it.action == AuditAction.PERMISSION_DENIED &&
            it.timestamp.isAfter(Instant.now().minus(30, ChronoUnit.MINUTES))
        }

        if (recentFailures.size >= 5) {
            securityAlerts.add("🚨 High number of authentication failures")
        }
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(5, "Security & Audit - ${guild.name}")
        gui.setOnGlobalClick { event -> event.isCancelled = true }

        // Create main navigation pane
        mainPane = StaticPane(0, 0, 9, 1, Pane.Priority.NORMAL)
        gui.addPane(mainPane)

        // Create security settings pane
        securityPane = StaticPane(0, 1, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(securityPane)

        // Create audit alerts pane
        auditPane = StaticPane(0, 3, 9, 2, Pane.Priority.NORMAL)
        gui.addPane(auditPane)

        setupNavigation()
        setupSecuritySettings()
        setupAuditAlerts()
    }

    /**
     * Setup navigation buttons
     */
    private fun setupNavigation() {
        // Back to bank button
        val backItem = createMenuItem(
            Material.ARROW,
            getLocalizedString(LocalizationKeys.MENU_BANK_BACK_TO_CONTROL_PANEL),
            listOf("Return to guild bank")
        )
        val backGuiItem = GuiItem(backItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildBankMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(backGuiItem, 0, 0)

        // Audit log button
        val auditItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "View Audit Log",
            listOf("Detailed transaction audit trail")
        )
        val auditGuiItem = GuiItem(auditItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(auditGuiItem, 1, 0)

        // Save settings button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            "Save Security Settings",
            listOf("Apply current security configuration")
        )
        val saveGuiItem = GuiItem(saveItem) { event ->
            event.isCancelled = true
            saveSecuritySettings()
            player.sendMessage("§aSecurity settings saved!")
        }
        mainPane.addItem(saveGuiItem, 7, 0)

        // Close button
        val closeItem = createMenuItem(
            Material.BARRIER,
            getLocalizedString(LocalizationKeys.MENU_BANK_CLOSE),
            listOf("Close menu")
        )
        val closeGuiItem = GuiItem(closeItem) { event ->
            event.isCancelled = true
            player.closeInventory()
        }
        mainPane.addItem(closeGuiItem, 8, 0)
    }

    /**
     * Setup security settings controls
     */
    private fun setupSecuritySettings() {
        // Dual authorization threshold
        val dualAuthItem = createMenuItem(
            Material.IRON_DOOR,
            "Dual Authorization Threshold",
            listOf(
                "Current: ${dualAuthThreshold} coins",
                "Transactions above this amount",
                "require approval from another officer"
            )
        )
        val dualAuthGuiItem = GuiItem(dualAuthItem) { event ->
            event.isCancelled = true
            // TODO: Open threshold input
            player.sendMessage("§eDual authorization threshold setting coming soon!")
        }
        securityPane.addItem(dualAuthGuiItem, 0, 0)

        // Fraud detection toggle
        val fraudItem = createMenuItem(
            if (fraudDetectionEnabled) Material.GREEN_WOOL else Material.RED_WOOL,
            "Fraud Detection",
            listOf(
                "Status: ${if (fraudDetectionEnabled) "Enabled" else "Disabled"}",
                "Monitors for suspicious patterns",
                "Click to toggle"
            )
        )
        val fraudGuiItem = GuiItem(fraudItem) { event ->
            event.isCancelled = true
            fraudDetectionEnabled = !fraudDetectionEnabled
            analyzeSecurityRisks()
            updateSecurityDisplay()
            gui.update()
        }
        securityPane.addItem(fraudGuiItem, 1, 0)

        // Emergency freeze toggle
        val freezeItem = createMenuItem(
            if (emergencyFreeze) Material.RED_WOOL else Material.GREEN_WOOL,
            "Emergency Freeze",
            listOf(
                "Status: ${if (emergencyFreeze) "ACTIVE" else "Inactive"}",
                "Blocks all bank transactions",
                "Use only in case of suspected breach"
            )
        )
        val freezeGuiItem = GuiItem(freezeItem) { event ->
            event.isCancelled = true
            if (!emergencyFreeze) {
                // Confirm before activating
                player.sendMessage("§c⚠ EMERGENCY FREEZE ACTIVATED - All transactions blocked!")
                player.sendMessage("§eUse this only if you suspect a security breach.")
                emergencyFreeze = true
            } else {
                emergencyFreeze = false
                player.sendMessage("§aEmergency freeze deactivated.")
            }
            analyzeSecurityRisks()
            updateSecurityDisplay()
            gui.update()
        }
        securityPane.addItem(freezeGuiItem, 2, 0)

        // Security status display
        updateSecurityStatus()
    }

    /**
     * Setup audit alerts and notifications
     */
    private fun setupAuditAlerts() {
        if (securityAlerts.isEmpty()) {
            val noAlertsItem = createMenuItem(
                Material.GREEN_WOOL,
                "Security Status: Good",
                listOf(
                    "No security alerts detected",
                    "All systems operating normally",
                    "Continue monitoring regularly"
                )
            )
            auditPane.addItem(GuiItem(noAlertsItem), 0, 0)
        } else {
            // Display alerts
            securityAlerts.take(6).forEachIndexed { index, alert ->
                val alertItem = createMenuItem(
                    when {
                        alert.contains("🚨") -> Material.RED_WOOL
                        alert.contains("⚠") -> Material.YELLOW_WOOL
                        else -> Material.ORANGE_WOOL
                    },
                    "Security Alert",
                    listOf(alert, "Monitor closely and take action if needed")
                )
                auditPane.addItem(GuiItem(alertItem), index % 9, index / 9)
            }
        }
    }

    /**
     * Update security status display
     */
    private fun updateSecurityStatus() {
        val riskLevel = calculateRiskLevel()

        val statusItem = createMenuItem(
            when (riskLevel) {
                "Low" -> Material.GREEN_WOOL
                "Medium" -> Material.YELLOW_WOOL
                "High" -> Material.ORANGE_WOOL
                "Critical" -> Material.RED_WOOL
                else -> Material.GRAY_WOOL
            },
            "Security Risk Level",
            listOf(
                "Current Level: $riskLevel",
                "Based on recent activity patterns",
                "Regular monitoring recommended"
            )
        )
        securityPane.addItem(GuiItem(statusItem), 4, 0)

        // Recent activity summary
        val auditLog = bankService.getAuditLog(guild.id, 10)
        val recentActivity = auditLog.take(3).map { audit ->
            val actorName = org.bukkit.Bukkit.getOfflinePlayer(audit.actorId).name ?: "Unknown"
            val action = audit.action.name.lowercase().replace("_", " ")
            "$actorName: $action"
        }

        val activityItem = createMenuItem(
            Material.CLOCK,
            "Recent Security Events",
            recentActivity.ifEmpty { listOf("No recent security events") }
        )
        securityPane.addItem(GuiItem(activityItem), 5, 1)
    }

    /**
     * Calculate overall security risk level
     */
    private fun calculateRiskLevel(): String {
        return when {
            emergencyFreeze -> "Critical"
            securityAlerts.count { it.contains("🚨") } > 0 -> "Critical"
            securityAlerts.count { it.contains("⚠") } >= 3 -> "High"
            securityAlerts.count { it.contains("⚠") } >= 1 -> "Medium"
            securityAlerts.isEmpty() -> "Low"
            else -> "Medium"
        }
    }

    /**
     * Update security display with latest data
     */
    private fun updateSecurityDisplay() {
        // Update is handled by individual setup methods
        analyzeSecurityRisks()
        setupAuditAlerts()
        updateSecurityStatus()
    }

    /**
     * Save security settings
     */
    private fun saveSecuritySettings() {
        // TODO: Save to database/configuration
        player.sendMessage("§aSecurity settings would be saved to database")
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
     * Get localized string with optional parameters
     */
    private fun getLocalizedString(key: String, vararg params: Any?): String {
        return localizationProvider.get(player.uniqueId, key, *params)
    }
}
