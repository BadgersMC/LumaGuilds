package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
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
import net.lumalyte.lg.utils.AntiDupeUtil
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
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildPartyManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                              private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        // Check if parties are enabled
        val mainConfig = configService.loadConfig()
        if (!mainConfig.partiesEnabled) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Parties are disabled on this server!")
            return
        }

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Party Management - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
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
        val activeParties = partyService.getActivePartiesForGuild(guild.id)

        if (activeParties.isEmpty()) {
            val noPartiesItem = ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>No Active Parties")
                .addAdventureLore(player, messageService, "<gray>Your guild is not in any parties")
                .addAdventureLore(player, messageService, "<gray>Create one by sending requests!")
            pane.addItem(GuiItem(noPartiesItem), 0, 0)
        } else {
            // Display first active party
            val party = activeParties.first()
            val partyItem = ItemStack(Material.FIREWORK_ROCKET)
                .name("<aqua>Active Party: ${party.name ?: "Unnamed"}")
                .addAdventureLore(player, messageService, "<gray>Members: <white>${party.guildIds.size} guilds")
                .lore("<gray>Created: <white>${party.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))}")
                .lore("<gray>Expires: <white>${party.expiresAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Never"}")

            val guiItem = GuiItem(partyItem) {
                // Open detailed party management
                openPartyDetailsMenu(party)
            }
            pane.addItem(guiItem, 0, 0)

            // Show party member count if more than one party
            if (activeParties.size > 1) {
                val morePartiesItem = ItemStack(Material.BOOK)
                    .setAdventureName(player, messageService, "<yellow>+${activeParties.size - 1} More Parties")
                    .addAdventureLore(player, messageService, "<gray>Click to view all parties")
                pane.addItem(GuiItem(morePartiesItem) {
                    openPartyListMenu()
                }, 1, 0)
            }
        }
    }

    private fun addPartyRequestsSection(pane: StaticPane) {
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        val outgoingRequests = partyService.getPendingRequestsFromGuild(guild.id)

        // Incoming requests
        val incomingItem = ItemStack(if (incomingRequests.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .setAdventureName(player, messageService, "<green>Incoming Requests")
            .addAdventureLore(player, messageService, "<gray>Party invitations to join")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${incomingRequests.size}")

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 3, 1)

        // Outgoing requests
        val outgoingItem = ItemStack(if (outgoingRequests.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<yellow>Outgoing Requests")
            .addAdventureLore(player, messageService, "<gray>Your party's sent invitations")
            .addAdventureLore(player, messageService, "<gray>Count: <white>${outgoingRequests.size}")

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, 5, 1)
    }

    private fun addPartyActionsSection(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        // Send party request (Admin+ only)
        val sendRequestItem = ItemStack(if (canManageParties) Material.FIREWORK_STAR else Material.BARRIER)
            .name(if (canManageParties) "<green>Send Party Request" else "<red>❌ Send Party Request")
            .lore(if (canManageParties) {
                listOf("<gray>Invite another guild to a party", "<gray>Create new parties or join existing ones")
            } else {
                listOf("<red>Requires Admin+ permission", "<gray>Only administrators can send party requests")
            })

        val sendRequestGuiItem = GuiItem(sendRequestItem) {
            if (canManageParties) {
                openSendPartyRequestMenu()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You need Admin+ permission to send party requests!")
            }
        }
        pane.addItem(sendRequestGuiItem, 0, 2)

        // Create new party (Admin+ only)
        val createPartyItem = ItemStack(if (canManageParties) Material.NETHER_STAR else Material.BARRIER)
            .name(if (canManageParties) "<gold>Create New Party" else "<red>❌ Create New Party")
            .lore(if (canManageParties) {
                listOf("<gray>Start a fresh party", "<gray>Invite guilds to coordinate events")
            } else {
                listOf("<red>Requires Admin+ permission", "<gray>Only administrators can create parties")
            })

        val createPartyGuiItem = GuiItem(createPartyItem) {
            if (canManageParties) {
                menuNavigator.openMenu(menuFactory.createPartyCreationMenu(menuNavigator, player, guild))
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You need Admin+ permission to create parties!")
            }
        }
        pane.addItem(createPartyGuiItem, 2, 2)
    }

    private fun addPartySettingsSection(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        // Party access settings (Admin+ only)
        val accessSettingsItem = ItemStack(if (canManageParties) Material.COMMAND_BLOCK else Material.BARRIER)
            .name(if (canManageParties) "<aqua>Party Access Settings" else "<red>❌ Party Access Settings")
            .addAdventureLore(player, messageService, "<gray>Configure who can join parties")
            .addAdventureLore(player, messageService, "<gray>Default: All guild members")
            .lore(if (canManageParties) "<gray>Click to configure rank restrictions" else "<red>Requires Admin+ permission")

        val accessSettingsGuiItem = GuiItem(accessSettingsItem) {
            if (canManageParties) {
                openPartyAccessSettingsMenu()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You need Admin+ permission to manage party access!")
            }
        }
        pane.addItem(accessSettingsGuiItem, 0, 3)

        // Party permissions info
        val permissionsItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<yellow>Party Permissions")
            .addAdventureLore(player, messageService, "<gray>□ View Invites: <white>All members")
            .addAdventureLore(player, messageService, "<gray>✓ Accept Invites: <white>Admin+ only")
            .addAdventureLore(player, messageService, "<gray>✉ Send Invites: <white>Admin+ only")
            .addAdventureLore(player, messageService, "<gray>§ Manage Settings: <white>Admin+ only")
            .addAdventureLore(player, messageService, "<gray>∩ Join Parties: <white>All members (or restricted ranks)")

        pane.addItem(GuiItem(permissionsItem), 2, 3)

        // Quick info about invite-only system
        val infoItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .setAdventureName(player, messageService, "<gold>ℹ️ Invite-Only System")
            .addAdventureLore(player, messageService, "<gray>All parties are invite-only")
            .addAdventureLore(player, messageService, "<gray>No public party browser")
            .addAdventureLore(player, messageService, "<gray>Parties coordinate guild events")
            .addAdventureLore(player, messageService, "<gray>Rank restrictions available")

        pane.addItem(GuiItem(infoItem), 4, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<yellow>Back to Control Panel")
            .addAdventureLore(player, messageService, "<gray>Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openPartyDetailsMenu(party: Party) {
        val detailsMenu = GuildPartyDetailsMenu(menuNavigator, player, guild, party, messageService)
        detailsMenu.open()
    }

    private fun openPartyListMenu() {
        val listMenu = GuildPartyListMenu(menuNavigator, player, guild, messageService)
        listMenu.open()
    }

    private fun openIncomingRequestsMenu() {
        val incomingRequests = partyService.getPendingRequestsForGuild(guild.id)
        if (incomingRequests.isEmpty()) {
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>No incoming party requests!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Your guild hasn't received any party invitations.")
            return
        }

        // Create incoming requests menu
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<green><green>Incoming Party Requests"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        var row = 0
        var col = 0

        incomingRequests.forEach { request ->
            val fromGuild = guildService.getGuild(request.fromGuildId)
            if (fromGuild != null) {
                val requestItem = ItemStack(Material.PAPER)
                    .setAdventureName(player, messageService, "<green>✉ Invitation from <white>${fromGuild.name}")
                    .lore("<gray>Message: <white>${request.message ?: "No message"}")
                    .lore("")
                    .addAdventureLore(player, messageService, "<yellow>Click to accept")
                    .addAdventureLore(player, messageService, "<red>Shift+Click to decline")

                val guiItem = GuiItem(requestItem) { event ->
                    when (event.click) {
                        ClickType.LEFT -> {
                            // Accept request
                            val party = partyService.acceptPartyRequest(request.id, guild.id, player.uniqueId)
                            if (party != null) {
                                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Party invitation accepted!")
                                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Your guild has joined the party.")
                                open() // Refresh menu
                            } else {
                                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to accept party invitation")
                            }
                        }
                        ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                            // Reject request
                            val success = partyService.rejectPartyRequest(request.id, guild.id, player.uniqueId)
                            if (success) {
                                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Party invitation rejected")
                                open() // Refresh menu
                            } else {
                                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to reject party invitation")
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
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>⬅️ Back")
            .addAdventureLore(player, messageService, "<gray>Return to party management")

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
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>No outgoing party requests!")
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Your guild hasn't sent any party invitations.")
            return
        }

        // Create outgoing requests menu
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<yellow><yellow>Outgoing Party Requests"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        var row = 0
        var col = 0

        outgoingRequests.forEach { request ->
            val toGuild = guildService.getGuild(request.toGuildId)
            if (toGuild != null) {
                val requestItem = ItemStack(Material.WRITABLE_BOOK)
                    .setAdventureName(player, messageService, "<yellow>✉ Invitation to <white>${toGuild.name}")
                    .lore("<gray>Message: <white>${request.message ?: "No message"}")
                    .lore("")
                    .addAdventureLore(player, messageService, "<red>Shift+Click to cancel")

                val guiItem = GuiItem(requestItem) { event ->
                    when (event.click) {
                        ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                            // Cancel request
                            val success = partyService.cancelPartyRequest(request.id, guild.id, player.uniqueId)
                            if (success) {
                                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Party invitation cancelled")
                                open() // Refresh menu
                            } else {
                                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to cancel party invitation")
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
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<red>⬅️ Back")
            .addAdventureLore(player, messageService, "<gray>Return to party management")

        val backGuiItem = GuiItem(backItem) {
            open() // Return to main party management menu
        }
        pane.addItem(backGuiItem, 4, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun openSendPartyRequestMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Send party request menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would allow you to invite other guilds to parties.")
    }

    private fun openCreatePartyMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Create party menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would allow you to create a new party and invite guilds.")
    }

    private fun openPartyAccessSettingsMenu() {
        AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Party access settings menu coming soon!")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>This would allow you to restrict party access by rank.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>For example: Only Officers and above can join parties.")
    }}

