package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.PeaceAgreement
import net.lumalyte.lg.domain.entities.PeaceOffering
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class PeaceAgreementMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var targetGuild: Guild? = null
) : Menu, KoinComponent, ChatInputHandler {

    private val warService: WarService by inject()
    private val guildService: GuildService by inject()
    private val chatInputListener: ChatInputListener by inject()

    // State for input handling
    private var inputMode: String? = null // "peace_terms", "offering_money", "offering_exp"
    private var currentWarId: UUID? = null
    private var peaceTerms: String = ""
    private var offeringMoney: Int = 0
    private var offeringExp: Int = 0

    override fun open() {
        // Check permissions first
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage("§c❌ You don't have permission to manage peace agreements for your guild!")
            return
        }

        val gui = ChestGui(6, "§6Peace Agreements - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 1: Current Wars
        addCurrentWarsSection(pane)

        // Row 2: Peace Agreements
        addPeaceAgreementsSection(pane)

        // Row 3: Actions
        addPeaceActionsSection(pane)

        gui.show(player)
    }

    private fun addCurrentWarsSection(pane: StaticPane) {
        val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }

        if (activeWars.isEmpty()) {
            val noWarsItem = ItemStack(Material.GRAY_DYE)
                .name("§7No Active Wars")
                .lore("§7Your guild is not currently at war")
                .lore("§7Peace agreements require an active war")

            pane.addItem(GuiItem(noWarsItem), 4, 0)
            return
        }

        // Show up to 3 active wars
        activeWars.take(3).forEachIndexed { index, war ->
            val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val enemyGuild = guildService.getGuild(enemyId)

            val warItem = ItemStack(Material.RED_WOOL)
                .name("§c⚔ War vs ${enemyGuild?.name ?: "Unknown"}")
                .lore("§7Duration: ${war.duration.toDays()} days")
                .lore("§7Status: ${war.status}")
                .lore("")
                .lore("§eClick to propose peace")

            val guiItem = GuiItem(warItem) {
                showPeaceProposalMenu(war.id)
            }

            pane.addItem(guiItem, index * 2, 0)
        }
    }

    private fun addPeaceAgreementsSection(pane: StaticPane) {
        val pendingAgreements = warService.getPendingPeaceAgreementsForGuild(guild.id)

        if (pendingAgreements.isEmpty()) {
            val noAgreementsItem = ItemStack(Material.GRAY_DYE)
                .name("§7No Pending Agreements")
                .lore("§7No peace agreements to respond to")

            pane.addItem(GuiItem(noAgreementsItem), 4, 1)
            return
        }

        // Show up to 3 pending agreements
        pendingAgreements.take(3).forEachIndexed { index, agreement ->
            val proposingGuild = guildService.getGuild(agreement.proposingGuildId)
            val war = warService.getWar(agreement.warId)

            val agreementItem = ItemStack(Material.PAPER)
                .name("§a§ Peace from ${proposingGuild?.name ?: "Unknown"}")
                .lore("§7Terms: ${agreement.peaceTerms}")
                .lore("§7War: ${war?.let { "vs ${guildService.getGuild(if (it.declaringGuildId == guild.id) it.defendingGuildId else it.declaringGuildId)?.name ?: "Unknown"}" } ?: "Unknown"}")
                .lore("")
                .lore("§eLeft-click to accept")
                .lore("§cShift+Click to reject")

            val guiItem = GuiItem(agreementItem) { event ->
                when (event.click) {
                    ClickType.LEFT -> {
                        val endedWar = warService.acceptPeaceAgreement(agreement.id, guild.id)
                        if (endedWar != null) {
                            player.sendMessage("§a✓ Peace agreement accepted!")
                            player.sendMessage("§7The war has ended peacefully.")
                            open() // Refresh menu
                        } else {
                            player.sendMessage("§c✗ Failed to accept peace agreement")
                        }
                    }
                    ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                        val success = warService.rejectPeaceAgreement(agreement.id, guild.id)
                        if (success) {
                            player.sendMessage("§c✗ Peace agreement rejected")
                            open() // Refresh menu
                        } else {
                            player.sendMessage("§c✗ Failed to reject peace agreement")
                        }
                    }
                    else -> {}
                }
            }

            pane.addItem(guiItem, index * 2, 1)
        }
    }

    private fun addPeaceActionsSection(pane: StaticPane) {
        // Propose Peace Button
        val proposeItem = ItemStack(Material.WHITE_WOOL)
            .name("§a☮ Propose Peace")
            .lore("§7Propose peace terms to end a war")
            .lore("§7Can include offerings to sweeten the deal")

        val proposeGuiItem = GuiItem(proposeItem) {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            if (activeWars.isEmpty()) {
                player.sendMessage("§c✗ Your guild is not currently at war!")
                return@GuiItem
            }

            if (activeWars.size == 1) {
                showPeaceProposalMenu(activeWars.first().id)
            } else {
                // Show war selection menu
                showWarSelectionForPeace()
            }
        }

        pane.addItem(proposeGuiItem, 2, 3)

        // View History Button
        val historyItem = ItemStack(Material.BOOK)
            .name("§e📚 Peace History")
            .lore("§7View past peace agreements")
            .lore("§7and war outcomes")

        val historyGuiItem = GuiItem(historyItem) {
            showPeaceHistory()
        }

        pane.addItem(historyGuiItem, 4, 3)

        // Back Button
        val backItem = ItemStack(Material.ARROW)
            .name("§c⬅ Back")
            .lore("§7Return to war management")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }

        pane.addItem(backGuiItem, 6, 3)
    }

    private fun showPeaceProposalMenu(warId: UUID) {
        currentWarId = warId
        peaceTerms = ""
        offeringMoney = 0
        offeringExp = 0

        val gui = ChestGui(4, "§6Propose Peace Agreement")
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Peace Terms Input
        val termsItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§f§ Peace Terms")
            .lore("§7Current: ${if (peaceTerms.isNotEmpty()) peaceTerms else "§oNone set"}")
            .lore("")
            .lore("§eClick to set peace terms")

        val termsGuiItem = GuiItem(termsItem) {
            inputMode = "peace_terms"
            chatInputListener.startInputMode(player, this)
            player.closeInventory()
            player.sendMessage("§eType your peace terms in chat (or 'cancel' to skip):")
        }

        pane.addItem(termsGuiItem, 2, 0)

        // Money Offering
        val moneyItem = ItemStack(Material.GOLD_INGOT)
            .name("§6$ Money Offering")
            .lore("§7Current: ${offeringMoney} coins")
            .lore("")
            .lore("§eClick to set money offering")

        val moneyGuiItem = GuiItem(moneyItem) {
            inputMode = "offering_money"
            chatInputListener.startInputMode(player, this)
            player.closeInventory()
            player.sendMessage("§eEnter the amount of money to offer (or 'cancel' to skip):")
        }

        pane.addItem(moneyGuiItem, 4, 0)

        // EXP Offering
        val expItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .name("§b☆ EXP Offering")
            .lore("§7Current: ${offeringExp} EXP")
            .lore("")
            .lore("§eClick to set EXP offering")

        val expGuiItem = GuiItem(expItem) {
            inputMode = "offering_exp"
            chatInputListener.startInputMode(player, this)
            player.closeInventory()
            player.sendMessage("§eEnter the amount of EXP to offer (or 'cancel' to skip):")
        }

        pane.addItem(expGuiItem, 6, 0)

        // Send Agreement Button
        val sendItem = ItemStack(Material.EMERALD_BLOCK)
            .name("§a✓ Send Peace Agreement")
            .lore("§7Send your peace proposal")
            .lore("§7to the enemy guild")

        val canSend = peaceTerms.isNotEmpty()
        if (!canSend) {
            sendItem.name("§7✓ Send Peace Agreement")
                .lore("§7Send your peace proposal")
                .lore("§7to the enemy guild")
                .lore("")
                .lore("§c✗ Peace terms required")
        }

        val sendGuiItem = GuiItem(sendItem) {
            if (canSend) {
                sendPeaceAgreement()
            } else {
                player.sendMessage("§c✗ You must set peace terms before sending the agreement!")
            }
        }

        pane.addItem(sendGuiItem, 4, 2)

        // Cancel Button
        val cancelItem = ItemStack(Material.REDSTONE_BLOCK)
            .name("§c✗ Cancel")
            .lore("§7Return to peace agreements menu")

        val cancelGuiItem = GuiItem(cancelItem) {
            open()
        }

        pane.addItem(cancelGuiItem, 6, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun sendPeaceAgreement() {
        val warId = currentWarId ?: return

        val offering = if (offeringMoney > 0 || offeringExp > 0) {
            PeaceOffering(
                money = offeringMoney,
                exp = offeringExp
            )
        } else null

        val agreement = warService.proposePeaceAgreement(
            warId = warId,
            proposingGuildId = guild.id,
            peaceTerms = peaceTerms,
            offering = offering
        )

        if (agreement != null) {
            player.sendMessage("§a✓ Peace agreement sent!")
            player.sendMessage("§7The enemy guild must accept your proposal.")

            if (offering != null) {
                player.sendMessage("§7Offering: ${offering.totalValue}")
            }

            // Broadcast to enemy guild
            val war = warService.getWar(warId)
            if (war != null) {
                val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyId)

                // For now, just send a simple broadcast to all online players
                // TODO: Implement proper guild member messaging
                val server = org.bukkit.Bukkit.getServer()
                server.onlinePlayers.forEach { onlinePlayer ->
                    onlinePlayer.sendMessage("§6☮ ${guild.name} has proposed peace!")
                    onlinePlayer.sendMessage("§7Terms: $peaceTerms")
                    if (offering != null) {
                        onlinePlayer.sendMessage("§7Offering: ${offering.totalValue}")
                    }
                }
            }

            open()
        } else {
            player.sendMessage("§c✗ Failed to send peace agreement!")
        }
    }

    private fun showWarSelectionForPeace() {
        // Implementation for selecting which war to propose peace for
        player.sendMessage("§eWar selection for peace proposals coming soon!")
    }

    private fun showPeaceHistory() {
        player.sendMessage("§ePeace history coming soon!")
    }

    // ChatInputHandler implementation
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "peace_terms" -> {
                if (input.lowercase() == "cancel") {
                    player.sendMessage("§7Peace terms input cancelled")
                } else {
                    peaceTerms = input
                    player.sendMessage("§a✓ Peace terms set: §f\"$input\"")
                }
                inputMode = null
                currentWarId?.let { showPeaceProposalMenu(it) }
            }
            "offering_money" -> {
                if (input.lowercase() == "cancel") {
                    player.sendMessage("§7Money offering input cancelled")
                } else {
                    val amount = input.toIntOrNull()
                    if (amount != null && amount >= 0) {
                        offeringMoney = amount
                        player.sendMessage("§a✓ Money offering set: §6$amount coins")
                    } else {
                        player.sendMessage("§c✗ Invalid amount! Please enter a valid number.")
                        return
                    }
                }
                inputMode = null
                currentWarId?.let { showPeaceProposalMenu(it) }
            }
            "offering_exp" -> {
                if (input.lowercase() == "cancel") {
                    player.sendMessage("§7EXP offering input cancelled")
                } else {
                    val amount = input.toIntOrNull()
                    if (amount != null && amount >= 0) {
                        offeringExp = amount
                        player.sendMessage("§a✓ EXP offering set: §b$amount EXP")
                    } else {
                        player.sendMessage("§c✗ Invalid amount! Please enter a valid number.")
                        return
                    }
                }
                inputMode = null
                currentWarId?.let { showPeaceProposalMenu(it) }
            }
        }
    }

    override fun onCancel(player: Player) {
        when (inputMode) {
            "peace_terms" -> player.sendMessage("§7Peace terms input cancelled")
            "offering_money" -> player.sendMessage("§7Money offering input cancelled")
            "offering_exp" -> player.sendMessage("§7EXP offering input cancelled")
        }
        inputMode = null
        currentWarId?.let { showPeaceProposalMenu(it) }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
