package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.MemberService
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

    override fun open() {
        val gui = ChestGui(6, "§6Party Management - ${guild.name}")
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
        val activeParties = partyService.getActivePartiesForGuild(guild.id)

        if (activeParties.isEmpty()) {
            val noPartiesItem = ItemStack(Material.BARRIER)
                .name("§cNo Active Parties")
                .lore("§7Your guild is not in any parties")
                .lore("§7Create one by sending requests!")
            pane.addItem(GuiItem(noPartiesItem), 0, 0)
        } else {
            // Display first active party
            val party = activeParties.first()
            val partyItem = ItemStack(Material.FIREWORK_ROCKET)
                .name("§bActive Party: ${party.name ?: "Unnamed"}")
                .lore("§7Members: §f${party.guildIds.size} guilds")
                .lore("§7Created: §f${party.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))}")
                .lore("§7Expires: §f${party.expiresAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) ?: "Never"}")

            val guiItem = GuiItem(partyItem) {
                // Open detailed party management
                openPartyDetailsMenu(party)
            }
            pane.addItem(guiItem, 0, 0)

            // Show party member count if more than one party
            if (activeParties.size > 1) {
                val morePartiesItem = ItemStack(Material.BOOK)
                    .name("§e+${activeParties.size - 1} More Parties")
                    .lore("§7Click to view all parties")
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
            .name("§aIncoming Requests")
            .lore("§7Party invitations to join")
            .lore("§7Count: §f${incomingRequests.size}")

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 3, 1)

        // Outgoing requests
        val outgoingItem = ItemStack(if (outgoingRequests.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name("§eOutgoing Requests")
            .lore("§7Your party's sent invitations")
            .lore("§7Count: §f${outgoingRequests.size}")

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, 5, 1)
    }

    private fun addPartyActionsSection(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        // Send party request (Admin+ only)
        val sendRequestItem = ItemStack(if (canManageParties) Material.FIREWORK_STAR else Material.BARRIER)
            .name(if (canManageParties) "§aSend Party Request" else "§c❌ Send Party Request")
            .lore(if (canManageParties) {
                listOf("§7Invite another guild to a party", "§7Create new parties or join existing ones")
            } else {
                listOf("§cRequires Admin+ permission", "§7Only administrators can send party requests")
            })

        val sendRequestGuiItem = GuiItem(sendRequestItem) {
            if (canManageParties) {
                openSendPartyRequestMenu()
            } else {
                player.sendMessage("§c❌ You need Admin+ permission to send party requests!")
            }
        }
        pane.addItem(sendRequestGuiItem, 0, 2)

        // Create new party (Admin+ only)
        val createPartyItem = ItemStack(if (canManageParties) Material.NETHER_STAR else Material.BARRIER)
            .name(if (canManageParties) "§6Create New Party" else "§c❌ Create New Party")
            .lore(if (canManageParties) {
                listOf("§7Start a fresh party", "§7Invite guilds to coordinate events")
            } else {
                listOf("§cRequires Admin+ permission", "§7Only administrators can create parties")
            })

        val createPartyGuiItem = GuiItem(createPartyItem) {
            if (canManageParties) {
                menuNavigator.openMenu(PartyCreationMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage("§c❌ You need Admin+ permission to create parties!")
            }
        }
        pane.addItem(createPartyGuiItem, 2, 2)
    }

    private fun addPartySettingsSection(pane: StaticPane) {
        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)

        // Party access settings (Admin+ only)
        val accessSettingsItem = ItemStack(if (canManageParties) Material.COMMAND_BLOCK else Material.BARRIER)
            .name(if (canManageParties) "§bParty Access Settings" else "§c❌ Party Access Settings")
            .lore("§7Configure who can join parties")
            .lore("§7Default: All guild members")
            .lore(if (canManageParties) "§7Click to configure rank restrictions" else "§cRequires Admin+ permission")

        val accessSettingsGuiItem = GuiItem(accessSettingsItem) {
            if (canManageParties) {
                openPartyAccessSettingsMenu()
            } else {
                player.sendMessage("§c❌ You need Admin+ permission to manage party access!")
            }
        }
        pane.addItem(accessSettingsGuiItem, 0, 3)

        // Party permissions info
        val permissionsItem = ItemStack(Material.BOOK)
            .name("§eParty Permissions")
            .lore("§7📋 View Invites: §fAll members")
            .lore("§7✅ Accept Invites: §fAdmin+ only")
            .lore("§7📤 Send Invites: §fAdmin+ only")
            .lore("§7⚙️ Manage Settings: §fAdmin+ only")
            .lore("§7🎯 Join Parties: §fAll members (or restricted ranks)")

        pane.addItem(GuiItem(permissionsItem), 2, 3)

        // Quick info about invite-only system
        val infoItem = ItemStack(Material.KNOWLEDGE_BOOK)
            .name("§6ℹ️ Invite-Only System")
            .lore("§7All parties are invite-only")
            .lore("§7No public party browser")
            .lore("§7Parties coordinate guild events")
            .lore("§7Rank restrictions available")

        pane.addItem(GuiItem(infoItem), 4, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.ARROW)
            .name("§eBack to Control Panel")
            .lore("§7Return to guild management")

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(GuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openPartyDetailsMenu(party: Party) {
        player.sendMessage("§eParty details menu coming soon!")
        player.sendMessage("§7This would show detailed party information and management options.")
    }

    private fun openPartyListMenu() {
        player.sendMessage("§eParty list menu coming soon!")
        player.sendMessage("§7This would show all parties your guild is part of.")
    }

    private fun openIncomingRequestsMenu() {
        player.sendMessage("§eIncoming requests menu coming soon!")
        player.sendMessage("§7This would show party invitations your guild has received.")
    }

    private fun openOutgoingRequestsMenu() {
        player.sendMessage("§eOutgoing requests menu coming soon!")
        player.sendMessage("§7This would show party invitations your guild has sent.")
    }

    private fun openSendPartyRequestMenu() {
        player.sendMessage("§eSend party request menu coming soon!")
        player.sendMessage("§7This would allow you to invite other guilds to parties.")
    }

    private fun openCreatePartyMenu() {
        player.sendMessage("§eCreate party menu coming soon!")
        player.sendMessage("§7This would allow you to create a new party and invite guilds.")
    }

    private fun openPartyAccessSettingsMenu() {
        player.sendMessage("§eParty access settings menu coming soon!")
        player.sendMessage("§7This would allow you to restrict party access by rank.")
        player.sendMessage("§7For example: Only Officers and above can join parties.")
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
