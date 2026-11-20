package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.nexus.i18n.LangService
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.CenteredRow
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.MenuIcons
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Locale keys stay literal so LocaleContractTest can see them.
@Suppress("StringLiteralDuplication", "LibraryEntitiesShouldNotBePublic")
class GuildPartyManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                              private var guild: Guild): Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        // Check if parties are enabled
        val mainConfig = configService.loadConfig()
        if (!mainConfig.partiesEnabled) {
            player.sendMessage(lang.msg("menu.party.management.feedback.disabled"))
            return
        }

        val heading = lang.guiTitle("menu.party.management.title", "guild" to guild.name)
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(guild.guiTheme, ROWS, heading))
        val pane = StaticPane(0, 0, COLUMNS, ROWS)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // REQ-205: parties on top, the three actions and the explainer below.
        addPartiesRow(pane)
        addActionsRow(pane)
        addBackButton(pane, BACK_SLOT, ROWS - 1)

        gui.show(player)
    }

    private fun addPartiesRow(pane: StaticPane) {
        val parties = partyService.getActivePartiesForGuild(guild.id)
            .filter { !it.isPlayerBanned(player.uniqueId) }
            .sortedBy { it.createdAt }
        if (parties.isEmpty()) {
            val empty = NexoItemProvider.getItemStackOrFallback("lg_no_parties") { ItemStack.of(Material.BARRIER) }
                .name(lang.gui("menu.party.management.empty.name"))
                .lore(lang.gui("menu.party.management.empty.lore"))
                .lore(lang.gui("menu.party.management.empty.hint"))
            pane.addItem(GuiItem(empty), 4, 0)
            return
        }
        val dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy")
        val shown = parties.take(9)
        CenteredRow.slots(shown.size).zip(shown).forEach { (x, party) ->
            val created = party.createdAt.atZone(ZoneId.systemDefault()).format(dateFormat)
            val expires = party.expiresAt?.atZone(ZoneId.systemDefault())?.format(dateFormat) ?: lang.raw("menu.party.management.never")
            val item = NexoItemProvider.getItemStackOrFallback("lg_party") { ItemStack.of(Material.FIREWORK_ROCKET) }
                .name(lang.gui("menu.party.management.active.name", "party" to (party.name ?: lang.raw("menu.party.management.unnamed"))))
                .lore(lang.gui("menu.party.management.active.members", "count" to party.guildIds.size))
                .lore(lang.gui("menu.party.management.active.created", "date" to created))
                .lore(lang.gui("menu.party.management.active.expires", "date" to expires))
                .lore(lang.gui("menu.common.blank"))
                .lore(lang.gui("menu.party.management.active.open"))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(GuildPartyPageMenu(menuNavigator, player, guild, party.id)) }, x, 0)
        }
    }

    private fun addActionsRow(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)
        val createName = if (canManageParties) lang.gui("menu.party.management.create.name") else lang.gui("menu.party.management.create.locked_name")
        val createItem = permissionIcon(canManageParties, "lg_party_create", Material.NETHER_STAR).name(createName)
        if (canManageParties) {
            createItem.lore(lang.gui("menu.party.management.create.lore")).lore(lang.gui("menu.party.management.create.hint"))
        } else {
            createItem.lore(lang.gui("menu.party.management.permission.required")).lore(lang.gui("menu.party.management.create.locked_lore"))
        }
        pane.addItem(GuiItem(createItem) {
            if (canManageParties) {
                menuNavigator.openMenu(menuFactory.createPartyCreationMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.party.management.feedback.create_permission"))
            }
        }, CREATE_SLOT, 1)

        val incoming = partyService.getPendingRequestsForGuild(guild.id)
        val incomingItem = MenuIcons.requests(incoming = true, count = incoming.size)
            .name(lang.gui("menu.party.management.incoming.name"))
            .lore(lang.gui("menu.party.management.incoming.lore"))
            .lore(lang.gui("menu.party.management.request_count", "count" to incoming.size))
        pane.addItem(GuiItem(incomingItem) { openIncomingRequestsMenu() }, INCOMING_SLOT, 1)

        val outgoing = partyService.getPendingRequestsFromGuild(guild.id)
        val outgoingItem = MenuIcons.requests(incoming = false, count = outgoing.size)
            .name(lang.gui("menu.party.management.outgoing.name"))
            .lore(lang.gui("menu.party.management.outgoing.lore"))
            .lore(lang.gui("menu.party.management.request_count", "count" to outgoing.size))
        pane.addItem(GuiItem(outgoingItem) { openOutgoingRequestsMenu() }, OUTGOING_SLOT, 1)

        val infoItem = NexoItemProvider.getItemStackOrFallback("lg_party_info") { ItemStack.of(Material.KNOWLEDGE_BOOK) }
            .name(lang.gui("menu.party.management.info.name"))
            .lore(lang.gui("menu.party.management.info.invite_only"))
            .lore(lang.gui("menu.party.management.permissions.view"))
            .lore(lang.gui("menu.party.management.permissions.manage"))
            .lore(lang.gui("menu.party.management.permissions.join"))
        pane.addItem(GuiItem(infoItem), INFO_SLOT, 1)
    }

    private fun permissionIcon(allowed: Boolean, id: String, material: Material): ItemStack {
        return NexoItemProvider.getItemStackOrFallback(if (allowed) id else "${id}_locked") {
            ItemStack.of(if (allowed) material else Material.BARRIER)
        }
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.party.management.back.name"))
            .lore(lang.gui("menu.party.management.back.lore"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openIncomingRequestsMenu() {
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        if (incomingRequests.isEmpty()) {
            player.sendMessage(lang.msg("menu.party.management.feedback.no_incoming"))
            player.sendMessage(lang.msg("menu.party.management.feedback.no_incoming_description"))
            return
        }

        // Create incoming requests menu
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.party.management.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        var row = 0
        var col = 0

        incomingRequests.forEach { request ->
            val fromGuild = guildService.getGuild(request.fromGuildId)
            if (fromGuild != null) {
                val requestItem = ItemStack.of(Material.PAPER)
                    .name(lang.gui("menu.party.management.incoming_request.name", "guild" to fromGuild.name))
                    .lore(lang.gui("menu.party.management.request_message", "message" to (request.message ?: lang.gui("menu.party.management.no_message"))))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.party.management.incoming_request.accept"))
                    .lore(lang.gui("menu.party.management.incoming_request.decline"))

                val guiItem = GuiItem(requestItem) { event ->
                    when (event.click) {
                        ClickType.LEFT -> {
                            // Accept request
                            val party = partyService.acceptPartyRequest(request.id, guild.id, player.uniqueId)
                            if (party != null) {
                                player.sendMessage(lang.msg("menu.party.management.feedback.accepted"))
                                player.sendMessage(lang.msg("menu.party.management.feedback.joined"))
                                open() // Refresh menu
                            } else {
                                player.sendMessage(lang.msg("menu.party.management.feedback.accept_failed"))
                            }
                        }
                        ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                            // Reject request
                            val success = partyService.rejectPartyRequest(request.id, guild.id, player.uniqueId)
                            if (success) {
                                player.sendMessage(lang.msg("menu.party.management.feedback.rejected"))
                                open() // Refresh menu
                            } else {
                                player.sendMessage(lang.msg("menu.party.management.feedback.reject_failed"))
                            }
                        }
                        else -> {}
                    }
                }

                pane.addItem(guiItem, col, row)

                col++
                if (col >= 9) {
                    col = 0
                    row++
                    if (row >= 5) return@forEach // Limit to prevent overflow
                }
            }
        }

        // Back button
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.party.management.request_back.name"))
            .lore(lang.gui("menu.party.management.request_back.lore"))

        val backGuiItem = GuiItem(backItem) {
            open() // Return to main party management menu
        }
        pane.addItem(backGuiItem, 4, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun openOutgoingRequestsMenu() {
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)
        if (outgoingRequests.isEmpty()) {
            player.sendMessage(lang.msg("menu.party.management.feedback.no_outgoing"))
            player.sendMessage(lang.msg("menu.party.management.feedback.no_outgoing_description"))
            return
        }

        // Create outgoing requests menu
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.party.management.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        var row = 0
        var col = 0

        outgoingRequests.forEach { request ->
            val toGuild = guildService.getGuild(request.toGuildId)
            if (toGuild != null) {
                val requestItem = ItemStack.of(Material.WRITABLE_BOOK)
                    .name(lang.gui("menu.party.management.outgoing_request.name", "guild" to toGuild.name))
                    .lore(lang.gui("menu.party.management.request_message", "message" to (request.message ?: lang.gui("menu.party.management.no_message"))))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.party.management.outgoing_request.cancel"))

                val guiItem = GuiItem(requestItem) { event ->
                    when (event.click) {
                        ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                            // Cancel request
                            val success = partyService.cancelPartyRequest(request.id, guild.id, player.uniqueId)
                            if (success) {
                                player.sendMessage(lang.msg("menu.party.management.feedback.cancelled"))
                                open() // Refresh menu
                            } else {
                                player.sendMessage(lang.msg("menu.party.management.feedback.cancel_failed"))
                            }
                        }
                        else -> {}
                    }
                }

                pane.addItem(guiItem, col, row)

                col++
                if (col >= 9) {
                    col = 0
                    row++
                    if (row >= 5) return@forEach // Limit to prevent overflow
                }
            }
        }

        // Back button
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.party.management.request_back.name"))
            .lore(lang.gui("menu.party.management.request_back.lore"))

        val backGuiItem = GuiItem(backItem) {
            open() // Return to main party management menu
        }
        pane.addItem(backGuiItem, 4, 5)

        gui.addPane(pane)
        gui.show(player)
    }




    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }

    private companion object {
        const val CREATE_SLOT = 2
        const val INCOMING_SLOT = 4
        const val OUTGOING_SLOT = 6
        const val INFO_SLOT = 8
        const val ROWS = 3
        const val COLUMNS = 9
        const val BACK_SLOT = 4
    }
}
