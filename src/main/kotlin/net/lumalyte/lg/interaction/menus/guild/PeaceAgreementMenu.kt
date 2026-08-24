package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
import org.slf4j.LoggerFactory
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
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val lang: LangService by inject()
    private val logger = LoggerFactory.getLogger(PeaceAgreementMenu::class.java)

    // State for input handling
    private var inputMode: String? = null // "peace_terms", "offering_money", "offering_exp"
    private var currentWarId: UUID? = null
    private var peaceTerms: String = ""
    private var offeringMoney: Int = 0
    private var offeringExp: Int = 0

    override fun open() {
        // Check permissions first
        if (!guildService.hasPermission(player.uniqueId, guild.id, RankPermission.DECLARE_WAR)) {
            player.sendMessage(lang.msg("menu.peace_agreement.feedback.no_permission"))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.peace_agreement.title", "guild" to guild.name)))
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
            val noWarsItem = ItemStack.of(Material.GRAY_DYE)
                .name(lang.legacy("menu.peace_agreement.war.none.name"))
                .lore(lang.legacy("menu.peace_agreement.war.none.description"))
                .lore(lang.legacy("menu.peace_agreement.war.none.requirement"))

            pane.addItem(GuiItem(noWarsItem), 4, 0)
            return
        }

        // Show up to 3 active wars
        activeWars.take(3).forEachIndexed { index, war ->
            val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
            val enemyGuild = guildService.getGuild(enemyId)

            val warItem = ItemStack.of(Material.RED_WOOL)
                .name(lang.legacy("menu.peace_agreement.war.name", "guild" to (enemyGuild?.name ?: lang.raw("general.unknown"))))
                .lore(lang.legacy("menu.peace_agreement.war.duration", "days" to war.duration.toDays()))
                .lore(lang.legacy("menu.peace_agreement.war.status", "status" to war.status))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.peace_agreement.war.propose"))

            val guiItem = GuiItem(warItem) {
                showPeaceProposalMenu(war.id)
            }

            pane.addItem(guiItem, index * 2, 0)
        }
    }

    private fun addPeaceAgreementsSection(pane: StaticPane) {
        val pendingAgreements = warService.getPendingPeaceAgreementsForGuild(guild.id)

        if (pendingAgreements.isEmpty()) {
            val noAgreementsItem = ItemStack.of(Material.GRAY_DYE)
                .name(lang.legacy("menu.peace_agreement.pending.none.name"))
                .lore(lang.legacy("menu.peace_agreement.pending.none.description"))

            pane.addItem(GuiItem(noAgreementsItem), 4, 1)
            return
        }

        // Show up to 3 pending agreements
        pendingAgreements.take(3).forEachIndexed { index, agreement ->
            val proposingGuild = guildService.getGuild(agreement.proposingGuildId)
            val war = warService.getWar(agreement.warId)

            val agreementItem = ItemStack.of(Material.PAPER)
                .name(lang.legacy("menu.peace_agreement.pending.name", "guild" to (proposingGuild?.name ?: lang.raw("general.unknown"))))
                .lore(lang.legacy("menu.peace_agreement.pending.terms", "terms" to agreement.peaceTerms))
                .lore(lang.legacy("menu.peace_agreement.pending.war", "guild" to (war?.let { guildService.getGuild(if (it.declaringGuildId == guild.id) it.defendingGuildId else it.declaringGuildId)?.name } ?: lang.raw("general.unknown"))))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.peace_agreement.pending.accept"))
                .lore(lang.legacy("menu.peace_agreement.pending.reject"))

            val guiItem = GuiItem(agreementItem) { event ->
                when (event.click) {
                    ClickType.LEFT -> {
                        val endedWar = warService.acceptPeaceAgreement(agreement.id, guild.id)
                        if (endedWar != null) {
                            player.sendMessage(lang.msg("menu.peace_agreement.feedback.accepted"))
                            player.sendMessage(lang.msg("menu.peace_agreement.feedback.war_ended"))
                            open() // Refresh menu
                        } else {
                            player.sendMessage(lang.msg("menu.peace_agreement.feedback.accept_failed"))
                        }
                    }
                    ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                        val success = warService.rejectPeaceAgreement(agreement.id, guild.id)
                        if (success) {
                            player.sendMessage(lang.msg("menu.peace_agreement.feedback.rejected"))
                            open() // Refresh menu
                        } else {
                            player.sendMessage(lang.msg("menu.peace_agreement.feedback.reject_failed"))
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
        val proposeItem = ItemStack.of(Material.WHITE_WOOL)
            .name(lang.legacy("menu.peace_agreement.action.propose.name"))
            .lore(lang.legacy("menu.peace_agreement.action.propose.description"))
            .lore(lang.legacy("menu.peace_agreement.action.propose.offerings"))

        val proposeGuiItem = GuiItem(proposeItem) {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            if (activeWars.isEmpty()) {
                player.sendMessage(lang.msg("menu.peace_agreement.feedback.not_at_war"))
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
        val historyItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.peace_agreement.action.history.name"))
            .lore(lang.legacy("menu.peace_agreement.action.history.description"))
            .lore(lang.legacy("menu.peace_agreement.action.history.outcomes"))

        val historyGuiItem = GuiItem(historyItem) {
            showPeaceHistory()
        }

        pane.addItem(historyGuiItem, 4, 3)

        // Back Button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.war_declaration.item.back.name"))
            .lore(lang.legacy("menu.war_declaration.item.back.lore"))

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }

        pane.addItem(backGuiItem, 6, 3)
    }

    private fun showPeaceProposalMenu(warId: UUID) {
        // Only reset state when switching to a different war
        if (currentWarId != warId) {
            currentWarId = warId
            peaceTerms = ""
            offeringMoney = 0
            offeringExp = 0
        }

        val gui = ChestGui(4, MenuTitleBuilder.build(guild.guiTheme, 4, lang.legacy("menu.peace_agreement.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 4)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Peace Terms Input
        val termsItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.peace_agreement.proposal.terms.name"))
            .lore(if (peaceTerms.isNotEmpty()) lang.legacy("menu.peace_agreement.proposal.current", "value" to peaceTerms) else lang.legacy("menu.peace_agreement.proposal.none"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.peace_agreement.proposal.terms.click"))

        val termsGuiItem = GuiItem(termsItem) {
            inputMode = "peace_terms"
            chatInputListener.startInputMode(player, this)
            player.closeInventory()
            player.sendMessage(lang.msg("menu.peace_agreement.input.terms.prompt"))
        }

        pane.addItem(termsGuiItem, 2, 0)

        // Money Offering
        val moneyItem = ItemStack.of(Material.GOLD_INGOT)
            .name(lang.legacy("menu.peace_agreement.proposal.money.name"))
            .lore(lang.legacy("menu.peace_agreement.proposal.money.current", "amount" to offeringMoney))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.peace_agreement.proposal.money.click"))

        val moneyGuiItem = GuiItem(moneyItem) {
            inputMode = "offering_money"
            chatInputListener.startInputMode(player, this)
            player.closeInventory()
            player.sendMessage(lang.msg("menu.peace_agreement.input.money.prompt"))
        }

        pane.addItem(moneyGuiItem, 4, 0)

        // EXP Offering
        val expItem = ItemStack.of(Material.EXPERIENCE_BOTTLE)
            .name(lang.legacy("menu.peace_agreement.proposal.exp.name"))
            .lore(lang.legacy("menu.peace_agreement.proposal.exp.current", "amount" to offeringExp))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.peace_agreement.proposal.exp.click"))

        val expGuiItem = GuiItem(expItem) {
            inputMode = "offering_exp"
            chatInputListener.startInputMode(player, this)
            player.closeInventory()
            player.sendMessage(lang.msg("menu.peace_agreement.input.exp.prompt"))
        }

        pane.addItem(expGuiItem, 6, 0)

        // Send Agreement Button
        val sendItem = ItemStack.of(Material.EMERALD_BLOCK)
            .name(lang.legacy("menu.peace_agreement.proposal.send.name"))
            .lore(lang.legacy("menu.peace_agreement.proposal.send.description"))
            .lore(lang.legacy("menu.peace_agreement.proposal.send.target"))

        val canSend = peaceTerms.isNotEmpty()
        if (!canSend) {
            sendItem.name(lang.legacy("menu.peace_agreement.proposal.send.disabled"))
                .lore(lang.legacy("menu.peace_agreement.proposal.send.description"))
                .lore(lang.legacy("menu.peace_agreement.proposal.send.target"))
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.peace_agreement.proposal.send.required"))
        }

        val sendGuiItem = GuiItem(sendItem) {
            if (canSend) {
                sendPeaceAgreement()
            } else {
                player.sendMessage(lang.msg("menu.peace_agreement.feedback.terms_required"))
            }
        }

        pane.addItem(sendGuiItem, 4, 2)

        // Cancel Button
        val cancelItem = ItemStack.of(Material.REDSTONE_BLOCK)
            .name(lang.legacy("menu.peace_agreement.proposal.cancel.name"))
            .lore(lang.legacy("menu.peace_agreement.proposal.cancel.description"))

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
            player.sendMessage(lang.msg("menu.peace_agreement.feedback.sent"))
            player.sendMessage(lang.msg("menu.peace_agreement.feedback.must_accept"))

            if (offering != null) {
                player.sendMessage(lang.msg("menu.peace_agreement.feedback.offering", "offering" to offering.totalValue))
            }

            // Broadcast to enemy guild members
            val war = warService.getWar(warId)
            if (war != null) {
                val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyId)

                // Send message to all online members of the enemy guild
                if (enemyGuild != null) {
                    val enemyMembers = memberService.getGuildMembers(enemyId)
                    val server = org.bukkit.Bukkit.getServer()
                    enemyMembers.forEach { member ->
                        val onlinePlayer = server.getPlayer(member.playerId)
                        if (onlinePlayer != null && onlinePlayer.isOnline) {
                            onlinePlayer.sendMessage(lang.msg("menu.peace_agreement.notification.proposed", "guild" to guild.name))
                            onlinePlayer.sendMessage(lang.msg("menu.peace_agreement.notification.terms", "terms" to peaceTerms))
                            if (offering != null) {
                                onlinePlayer.sendMessage(lang.msg("menu.peace_agreement.feedback.offering", "offering" to offering.totalValue))
                            }
                        }
                    }
                }
            }

            open()
        } else {
            player.sendMessage(lang.msg("menu.peace_agreement.feedback.send_failed"))
        }
    }

    private fun showWarSelectionForPeace() {
        // Show active wars that can be used for peace proposals
        player.closeInventory()
        try {
            val activeWars = warService.getWarsForGuild(guild.id).filter { it.isActive }
            if (activeWars.isEmpty()) {
                player.sendMessage(lang.msg("menu.peace_agreement.feedback.not_at_war_anyone"))
                return
            }
            player.sendMessage(lang.msg("menu.peace_agreement.selection.header"))
            activeWars.forEach { war ->
                val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyId)
                val enemyName = enemyGuild?.name ?: lang.raw("general.unknown")
                player.sendMessage(lang.msg("menu.peace_agreement.selection.row", "guild" to enemyName))
            }
            player.sendMessage(lang.msg("menu.peace_agreement.selection.command"))
        } catch (e: Exception) {
            logger.error("Failed to load active wars for peace proposal for guild ${guild.id}", e)
            player.sendMessage(lang.msg("menu.peace_agreement.feedback.war_load_failed"))
        }
    }

    private fun showPeaceHistory() {
        // Show recently ended wars (peace history)
        player.closeInventory()
        try {
            val warHistory = warService.getWarHistory(guild.id, 10)
            if (warHistory.isEmpty()) {
                player.sendMessage(lang.msg("menu.peace_agreement.history.none"))
                return
            }
            player.sendMessage(lang.msg("menu.peace_agreement.history.header"))
            warHistory.forEach { war ->
                val enemyId = if (war.declaringGuildId == guild.id) war.defendingGuildId else war.declaringGuildId
                val enemyGuild = guildService.getGuild(enemyId)
                val enemyName = enemyGuild?.name ?: lang.raw("general.unknown")
                val status = if (war.isActive) lang.raw("menu.peace_agreement.history.active") else lang.raw("menu.peace_agreement.history.ended")
                player.sendMessage(lang.msg("menu.peace_agreement.history.row", "guild" to enemyName, "status" to status))
            }
        } catch (e: Exception) {
            logger.error("Failed to load peace history for guild ${guild.id}", e)
            player.sendMessage(lang.msg("menu.peace_agreement.feedback.history_load_failed"))
        }
    }

    // ChatInputHandler implementation
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "peace_terms" -> {
                if (input.lowercase() == "cancel") {
                    player.sendMessage(lang.msg("menu.peace_agreement.input.terms.cancelled"))
                } else {
                    peaceTerms = input
                    player.sendMessage(lang.msg("menu.peace_agreement.input.terms.set", "terms" to input))
                }
                inputMode = null
                currentWarId?.let { showPeaceProposalMenu(it) }
            }
            "offering_money" -> {
                if (input.lowercase() == "cancel") {
                    player.sendMessage(lang.msg("menu.peace_agreement.input.money.cancelled"))
                } else {
                    val amount = input.toIntOrNull()
                    if (amount != null && amount >= 0) {
                        offeringMoney = amount
                        player.sendMessage(lang.msg("menu.peace_agreement.input.money.set", "amount" to amount))
                    } else {
                        player.sendMessage(lang.msg("menu.peace_agreement.input.invalid_amount"))
                        return
                    }
                }
                inputMode = null
                currentWarId?.let { showPeaceProposalMenu(it) }
            }
            "offering_exp" -> {
                if (input.lowercase() == "cancel") {
                    player.sendMessage(lang.msg("menu.peace_agreement.input.exp.cancelled"))
                } else {
                    val amount = input.toIntOrNull()
                    if (amount != null && amount >= 0) {
                        offeringExp = amount
                        player.sendMessage(lang.msg("menu.peace_agreement.input.exp.set", "amount" to amount))
                    } else {
                        player.sendMessage(lang.msg("menu.peace_agreement.input.invalid_amount"))
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
            "peace_terms" -> player.sendMessage(lang.msg("menu.peace_agreement.input.terms.cancelled"))
            "offering_money" -> player.sendMessage(lang.msg("menu.peace_agreement.input.money.cancelled"))
            "offering_exp" -> player.sendMessage(lang.msg("menu.peace_agreement.input.exp.cancelled"))
        }
        inputMode = null
        currentWarId?.let { showPeaceProposalMenu(it) }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
