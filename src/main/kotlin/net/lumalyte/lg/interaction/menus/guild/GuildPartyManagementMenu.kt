package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.nexus.i18n.LangService
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.party.management.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 1: Current Parties
        addCurrentPartiesSection(pane)

        // Row 2: Party Requests
        addPartyRequestsSection(pane)

        // Row 3: Actions
        addPartyActionsSection(pane)

        // Row 4-5: Party Settings
        addPartySettingsSection(pane)

        // Row 6: Navigation
        addBackButton(pane, 4, 5)

        gui.show(player)
    }

    private fun addCurrentPartiesSection(pane: StaticPane) {
        val allActiveParties = partyService.getActivePartiesForGuild(guild.id)
        // Filter out parties the player is banned from
        val activeParties = allActiveParties.filter { party ->
            !party.isPlayerBanned(player.uniqueId)
        }.toSet()

        if (activeParties.isEmpty()) {
            val noPartiesItem = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.party.management.empty.name"))
                .lore(lang.gui("menu.party.management.empty.lore"))
                .lore(lang.gui("menu.party.management.empty.hint"))
            pane.addItem(GuiItem(noPartiesItem), 0, 0)
        } else {
            // Display first active party
            val party = activeParties.first()
            val partyItem = ItemStack.of(Material.FIREWORK_ROCKET)
                .name(lang.gui("menu.party.management.active.name", "party" to (party.name ?: lang.gui("menu.party.management.unnamed"))))
                .lore(lang.gui("menu.party.management.active.members", "count" to party.guildIds.size))
                .lore(lang.gui("menu.party.management.active.created", "date" to party.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))))
                .lore(lang.gui("menu.party.management.active.expires", "date" to (party.expiresAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: lang.gui("menu.party.management.never"))))

            val guiItem = GuiItem(partyItem) {
                // Open detailed party management
                openPartyDetailsMenu(party)
            }
            pane.addItem(guiItem, 0, 0)

            // Show party member count if more than one party
            if (activeParties.size > 1) {
                val morePartiesItem = ItemStack.of(Material.BOOK)
                    .name(lang.gui("menu.party.management.more.name", "count" to activeParties.size - 1))
                    .lore(lang.gui("menu.party.management.more.lore"))
                pane.addItem(GuiItem(morePartiesItem) {
                    openPartyListMenu()
                }, 1, 0)
            }

            // Add moderation button for each party (if player has permission)
            val canModerate = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)
            if (canModerate) {
                val moderateItem = ItemStack.of(Material.ANVIL)
                    .name(lang.gui("menu.party.management.moderate.name"))
                    .lore(lang.gui("menu.party.management.moderate.lore"))
                    .lore(lang.gui("menu.party.management.moderate.channel", "channel" to (party.name ?: lang.gui("menu.party.management.this_channel"))))
                    .lore(lang.gui("menu.common.blank"))
                    .lore(lang.gui("menu.party.management.moderate.click"))

                pane.addItem(GuiItem(moderateItem) {
                    openModerationMenu(party)
                }, 8, 0)
            }
        }
    }

    private fun addPartyRequestsSection(pane: StaticPane) {
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)

        // Incoming requests
        val incomingItem = ItemStack.of(if (incomingRequests.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .name(lang.gui("menu.party.management.incoming.name"))
            .lore(lang.gui("menu.party.management.incoming.lore"))
            .lore(lang.gui("menu.party.management.request_count", "count" to incomingRequests.size))

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 3, 1)

        // Outgoing requests
        val outgoingItem = ItemStack.of(if (outgoingRequests.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name(lang.gui("menu.party.management.outgoing.name"))
            .lore(lang.gui("menu.party.management.outgoing.lore"))
            .lore(lang.gui("menu.party.management.request_count", "count" to outgoingRequests.size))

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, 5, 1)
    }

    private fun addPartyActionsSection(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        // Send party request (Admin+ only)
        val sendRequestName = if (canManageParties) lang.gui("menu.party.management.send.name") else lang.gui("menu.party.management.send.locked_name")
        val sendRequestLore = if (canManageParties) {
            listOf(lang.gui("menu.party.management.send.lore"), lang.gui("menu.party.management.send.hint"))
        } else {
            listOf(lang.gui("menu.party.management.permission.required"), lang.gui("menu.party.management.send.locked_lore"))
        }
        val sendRequestItem = ItemStack.of(if (canManageParties) Material.FIREWORK_STAR else Material.BARRIER)
            .name(sendRequestName)
            .also { item -> sendRequestLore.forEach { item.lore(it) } }

        val sendRequestGuiItem = GuiItem(sendRequestItem) {
            if (canManageParties) {
                openSendPartyRequestMenu()
            } else {
                player.sendMessage(lang.msg("menu.party.management.feedback.send_permission"))
            }
        }
        pane.addItem(sendRequestGuiItem, 0, 2)

        // Create new party (Admin+ only)
        val createName = if (canManageParties) lang.gui("menu.party.management.create.name") else lang.gui("menu.party.management.create.locked_name")
        val createLore = if (canManageParties) {
            listOf(lang.gui("menu.party.management.create.lore"), lang.gui("menu.party.management.create.hint"))
        } else {
            listOf(lang.gui("menu.party.management.permission.required"), lang.gui("menu.party.management.create.locked_lore"))
        }
        val createPartyItem = ItemStack.of(if (canManageParties) Material.NETHER_STAR else Material.BARRIER)
            .name(createName)
            .also { item -> createLore.forEach { item.lore(it) } }

        val createPartyGuiItem = GuiItem(createPartyItem) {
            if (canManageParties) {
                menuNavigator.openMenu(menuFactory.createPartyCreationMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.party.management.feedback.create_permission"))
            }
        }
        pane.addItem(createPartyGuiItem, 2, 2)
    }

    private fun addPartySettingsSection(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        // Party access settings (Admin+ only)
        val accessName = if (canManageParties) lang.gui("menu.party.management.access.name") else lang.gui("menu.party.management.access.locked_name")
        val accessAction = if (canManageParties) lang.gui("menu.party.management.access.action") else lang.gui("menu.party.management.permission.required")
        val accessSettingsItem = ItemStack.of(if (canManageParties) Material.COMMAND_BLOCK else Material.BARRIER)
            .name(accessName)
            .lore(lang.gui("menu.party.management.access.lore"))
            .lore(lang.gui("menu.party.management.access.default"))
            .lore(accessAction)

        val accessSettingsGuiItem = GuiItem(accessSettingsItem) {
            if (canManageParties) {
                openPartyAccessSettingsMenu()
            } else {
                player.sendMessage(lang.msg("menu.party.management.feedback.access_permission"))
            }
        }
        pane.addItem(accessSettingsGuiItem, 0, 3)

        // Party permissions info
        val permissionsItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.party.management.permissions.name"))
            .lore(lang.gui("menu.party.management.permissions.view"))
            .lore(lang.gui("menu.party.management.permissions.accept"))
            .lore(lang.gui("menu.party.management.permissions.send"))
            .lore(lang.gui("menu.party.management.permissions.manage"))
            .lore(lang.gui("menu.party.management.permissions.join"))

        pane.addItem(GuiItem(permissionsItem), 2, 3)

        // Quick info about invite-only system
        val infoItem = ItemStack.of(Material.KNOWLEDGE_BOOK)
            .name(lang.gui("menu.party.management.info.name"))
            .lore(lang.gui("menu.party.management.info.invite_only"))
            .lore(lang.gui("menu.party.management.info.no_browser"))
            .lore(lang.gui("menu.party.management.info.events"))
            .lore(lang.gui("menu.party.management.info.restrictions"))

        pane.addItem(GuiItem(infoItem), 4, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.party.management.back.name"))
            .lore(lang.gui("menu.party.management.back.lore"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openPartyDetailsMenu(party: Party) {
        player.sendMessage(lang.msg("menu.party.management.feedback.details_coming_soon"))
        player.sendMessage(lang.msg("menu.party.management.feedback.details_description"))
    }

    private fun openModerationMenu(party: Party) {
        menuNavigator.openMenu(PartyModerationMenu(menuNavigator, player, guild, party))
    }

    private fun openPartyListMenu() {
        player.sendMessage(lang.msg("menu.party.management.feedback.list_coming_soon"))
        player.sendMessage(lang.msg("menu.party.management.feedback.list_description"))
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
        val backItem = ItemStack.of(Material.ARROW)
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
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.party.management.request_back.name"))
            .lore(lang.gui("menu.party.management.request_back.lore"))

        val backGuiItem = GuiItem(backItem) {
            open() // Return to main party management menu
        }
        pane.addItem(backGuiItem, 4, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun openSendPartyRequestMenu() {
        player.sendMessage(lang.msg("menu.party.management.feedback.send_coming_soon"))
        player.sendMessage(lang.msg("menu.party.management.feedback.send_description"))
    }

    private fun openCreatePartyMenu() {
        player.sendMessage(lang.msg("menu.party.management.feedback.create_coming_soon"))
        player.sendMessage(lang.msg("menu.party.management.feedback.create_description"))
    }

    private fun openPartyAccessSettingsMenu() {
        player.sendMessage(lang.msg("menu.party.management.feedback.access_coming_soon"))
        player.sendMessage(lang.msg("menu.party.management.feedback.access_description"))
        player.sendMessage(lang.msg("menu.party.management.feedback.access_example"))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

