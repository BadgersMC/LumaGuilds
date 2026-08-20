package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.Pane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.persistence.BankSettingsRepository
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.AuditAction
import net.lumalyte.lg.domain.entities.BankAudit
import net.lumalyte.lg.domain.entities.BankSettings
import net.lumalyte.lg.domain.entities.BankTransaction
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
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
 * Guild Bank Security and Audit menu with fraud detection and dual authorization (REQ-031)
 */
class GuildBankSecurityMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild
) : Menu, KoinComponent, ChatInputHandler {

    private enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    private val bankService: BankService by inject()
    private val guildService: GuildService by inject()
    private val bankSettingsRepository: BankSettingsRepository by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val lang: LangService by inject()

    // GUI components
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private lateinit var securityPane: StaticPane
    private lateinit var auditPane: StaticPane

    // Security settings (dual-auth threshold persisted per guild)
    private var dualAuthThreshold: Int = 1000
    private var emergencyFreeze: Boolean = false
    private var securityAlerts: MutableList<String> = mutableListOf()

    // Active input mode for chat-based configuration
    private var inputMode: String? = null

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
                    "emergencyFreeze" -> emergencyFreeze = value as Boolean
                }
            }
            analyzeSecurityRisks()
            updateSecurityDisplay()
            gui.update()
        }
    }

    /**
     * Load security settings: dual-auth threshold from persisted per-guild settings (REQ-031),
     * freeze state from the guild entity.
     */
    private fun loadSecuritySettings() {
        val settings = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        dualAuthThreshold = settings.dualAuthThreshold
        // Read persisted freeze state from database via guild entity
        val currentGuild = guildService.getGuild(guild.id)
        emergencyFreeze = currentGuild?.bankFrozen ?: false
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
            securityAlerts.add(lang.raw("menu.bank_security.alert.freeze"))
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
            securityAlerts.add(lang.raw("menu.bank_security.alert.authentication_failures"))
        }

        // Check for unusual transaction times
        val unusualHours = recentAudits.filter { audit ->
            val hour = LocalDateTime.ofInstant(audit.timestamp, ZoneId.systemDefault()).hour
            hour < 6 || hour > 22 // Outside normal business hours
        }

        if (unusualHours.size >= 2) {
            securityAlerts.add(lang.raw("menu.bank_security.alert.unusual_timing"))
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
            securityAlerts.add(lang.legacy("menu.bank_security.alert.large_withdrawal", "amount" to largeTransactions.last().amount))
        }

        // Check for rapid large transactions
        val recentLarge = transactions.filter {
            it.timestamp.isAfter(Instant.now().minus(1, ChronoUnit.HOURS)) &&
            it.amount > dualAuthThreshold
        }

        if (recentLarge.size >= 2) {
            securityAlerts.add(lang.raw("menu.bank_security.alert.multiple_large"))
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
            securityAlerts.add(lang.raw("menu.bank_security.alert.rapid_withdrawal"))
        }

        // Check for same amount withdrawals (potential fraud)
        val amounts = recentWithdrawals.map { it.amount }.toSet()
        if (amounts.size == 1 && recentWithdrawals.size >= 3) {
            securityAlerts.add(lang.raw("menu.bank_security.alert.identical_withdrawals"))
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
            securityAlerts.add(lang.raw("menu.bank_security.alert.high_authentication_failures"))
        }
    }

    /**
     * Initialize the GUI structure
     */
    private fun initializeGui() {
        gui = ChestGui(5, MenuTitleBuilder.build(guild.guiTheme, 5, lang.legacy("menu.bank_security.title", "guild" to guild.name)))
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
            getLocalizedString("menu.bank.back_to_control_panel"),
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
            lang.raw("menu.bank_security.navigation.audit.name"),
            listOf(lang.raw("menu.bank_security.navigation.audit.description"))
        )
        val auditGuiItem = GuiItem(auditItem) { event ->
            event.isCancelled = true
            menuNavigator.openMenu(GuildBankTransactionHistoryMenu(menuNavigator, player, guild))
        }
        mainPane.addItem(auditGuiItem, 1, 0)

        // Save settings button
        val saveItem = createMenuItem(
            Material.WRITABLE_BOOK,
            lang.raw("menu.bank_security.navigation.save.name"),
            listOf(lang.raw("menu.bank_security.navigation.save.description"))
        )
        val saveGuiItem = GuiItem(saveItem) { event ->
            event.isCancelled = true
            // saveSecuritySettings() reports the upsert result itself — no
            // unconditional success message here (would contradict a failure).
            saveSecuritySettings()
        }
        mainPane.addItem(saveGuiItem, 7, 0)

        // Close button
        val closeItem = createMenuItem(
            Material.BARRIER,
            getLocalizedString("menu.bank.close"),
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
            lang.raw("menu.bank_security.dual_auth.name"),
            listOf(
                lang.legacy("menu.bank_security.dual_auth.current", "amount" to dualAuthThreshold),
                lang.raw("menu.bank_security.dual_auth.description"),
                lang.raw("menu.bank_security.dual_auth.requirement")
            )
        )
        val dualAuthGuiItem = GuiItem(dualAuthItem) { event ->
            event.isCancelled = true
            inputMode = "dualAuth"
            chatInputListener.startInputMode(player, this@GuildBankSecurityMenu)
            player.sendMessage(lang.msg("menu.bank_security.feedback.threshold_prompt"))
        }
        securityPane.addItem(dualAuthGuiItem, 0, 0)

        // Emergency freeze toggle
        val freezeItem = createMenuItem(
            if (emergencyFreeze) Material.RED_WOOL else Material.GREEN_WOOL,
            lang.raw("menu.bank_security.freeze.name"),
            listOf(
                if (emergencyFreeze) lang.raw("menu.bank_security.freeze.status.active") else lang.raw("menu.bank_security.freeze.status.inactive"),
                lang.raw("menu.bank_security.freeze.description"),
                lang.raw("menu.bank_security.freeze.warning")
            )
        )
        val freezeGuiItem = GuiItem(freezeItem) { event ->
            event.isCancelled = true
            val newFrozen = !emergencyFreeze
            val success = guildService.setBankFrozen(guild.id, newFrozen, player.uniqueId)
            if (success) {
                emergencyFreeze = newFrozen
                if (newFrozen) {
                    player.sendMessage(lang.msg("menu.bank_security.feedback.freeze_activated"))
                    player.sendMessage(lang.msg("menu.bank_security.feedback.freeze_hint"))
                } else {
                    player.sendMessage(lang.msg("menu.bank_security.feedback.freeze_deactivated"))
                }
            } else {
                player.sendMessage(lang.msg("menu.bank_security.feedback.freeze_failed"))
            }
            analyzeSecurityRisks()
            updateSecurityDisplay()
            gui.update()
        }
        securityPane.addItem(freezeGuiItem, 1, 0)

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
                lang.raw("menu.bank_security.status.good.name"),
                listOf(
                    lang.raw("menu.bank_security.status.good.description"),
                    lang.raw("menu.bank_security.status.good.systems"),
                    lang.raw("menu.bank_security.status.good.monitoring")
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
                    lang.raw("menu.bank_security.alert.name"),
                    listOf(alert, lang.raw("menu.bank_security.alert.action"))
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
                RiskLevel.LOW -> Material.GREEN_WOOL
                RiskLevel.MEDIUM -> Material.YELLOW_WOOL
                RiskLevel.HIGH -> Material.ORANGE_WOOL
                RiskLevel.CRITICAL -> Material.RED_WOOL
            },
            lang.raw("menu.bank_security.risk.name"),
            listOf(
                lang.legacy("menu.bank_security.risk.current", "level" to riskLevelName(riskLevel)),
                lang.raw("menu.bank_security.risk.description"),
                lang.raw("menu.bank_security.risk.monitoring")
            )
        )
        securityPane.addItem(GuiItem(statusItem), 4, 0)

        // Recent activity summary
        val auditLog = bankService.getAuditLog(guild.id, 10)
        val recentActivity = auditLog.take(3).map { audit ->
            val actorName = org.bukkit.Bukkit.getOfflinePlayer(audit.actorId).name ?: lang.raw("menu.bank_security.events.unknown_actor")
            val action = audit.action.name.lowercase().replace("_", " ")
            lang.legacy("menu.bank_security.events.entry", "actor" to actorName, "action" to action)
        }

        val activityItem = createMenuItem(
            Material.CLOCK,
            lang.raw("menu.bank_security.events.name"),
            recentActivity.ifEmpty { listOf(lang.raw("menu.bank_security.events.empty")) }
        )
        securityPane.addItem(GuiItem(activityItem), 5, 1)
    }

    /**
     * Calculate overall security risk level
     */
    private fun calculateRiskLevel(): RiskLevel {
        return when {
            emergencyFreeze -> RiskLevel.CRITICAL
            securityAlerts.count { it.contains("🚨") } > 0 -> RiskLevel.CRITICAL
            securityAlerts.count { it.contains("⚠") } >= 3 -> RiskLevel.HIGH
            securityAlerts.count { it.contains("⚠") } >= 1 -> RiskLevel.MEDIUM
            securityAlerts.isEmpty() -> RiskLevel.LOW
            else -> RiskLevel.MEDIUM
        }
    }

    private fun riskLevelName(level: RiskLevel): String = when (level) {
        RiskLevel.LOW -> lang.raw("menu.bank_security.risk.level.low")
        RiskLevel.MEDIUM -> lang.raw("menu.bank_security.risk.level.medium")
        RiskLevel.HIGH -> lang.raw("menu.bank_security.risk.level.high")
        RiskLevel.CRITICAL -> lang.raw("menu.bank_security.risk.level.critical")
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
     * Save security settings (REQ-031): persists the dual-auth threshold per guild.
     * Note: the emergency freeze toggle saves immediately via guildService.setBankFrozen().
     */
    private fun saveSecuritySettings() {
        val current = bankSettingsRepository.getByGuildId(guild.id) ?: BankSettings(guild.id)
        val updated = current.copy(dualAuthThreshold = dualAuthThreshold)
        val saved = bankSettingsRepository.upsert(updated)
        if (saved) {
            player.sendMessage(lang.msg("menu.bank_security.feedback.saved"))
        } else {
            player.sendMessage(lang.msg("menu.bank_security.feedback.save_failed"))
        }
    }

    // ChatInputHandler interface methods (REQ-031)
    override fun onChatInput(player: Player, input: String) {
        // Guard FIRST: if the listener session outlived the menu interaction,
        // inputMode is null and this is ordinary chat — do not intercept it.
        val mode = inputMode ?: return

        val threshold = input.trim().toIntOrNull()
        if (threshold == null || threshold < 0) {
            player.sendMessage(lang.msg("menu.bank_security.feedback.invalid_threshold"))
            inputMode = null
            return
        }
        when (mode) {
            "dualAuth" -> {
                dualAuthThreshold = threshold
                player.sendMessage(lang.msg("menu.bank_security.feedback.threshold_set", "amount" to threshold))
            }
            else -> return
        }
        inputMode = null
        analyzeSecurityRisks()
        updateSecurityDisplay()
        gui.update()
    }

    override fun onCancel(player: Player) {
        inputMode = null
        player.sendMessage(lang.msg("menu.bank_security.feedback.cancelled"))
    }

    /**
     * Create a menu item with consistent formatting
     */
    private fun createMenuItem(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack.of(material)
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
    private fun getLocalizedString(key: String): String {
        return lang.legacy(key)
    }
}
