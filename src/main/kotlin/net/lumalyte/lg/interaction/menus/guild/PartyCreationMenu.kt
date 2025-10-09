package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.PartyStatus
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
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
import java.time.Duration
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class PartyCreationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                       private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val partyService: PartyService by inject()
    private val rankService: RankService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    // Creation state
    private var partyName: String = ""
    private var selectedGuilds: MutableSet<UUID> = mutableSetOf(guild.id) // Always include current guild
    private var restrictedRoles: MutableSet<UUID> = mutableSetOf()
    private var inputMode: String = "" // "name"
    private var roleSelectionMode: Boolean = false // Whether we're in role selection mode
    private var isPrivateParty: Boolean = false // Whether this is a private guild-only party

    override fun open() {
        // Check if parties are enabled
        val mainConfig = configService.loadConfig()
        if (!mainConfig.partiesEnabled) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Parties are disabled on this server!")
            return
        }

        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Create New Party - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)
        gui.addPane(pane)

        // Row 0: Party info
        addPartyInfoSection(pane)

        // Row 1: Party Type Selection
        addPartyTypeSection(pane)

        // Row 2: Available guilds to invite (only if not private)
        if (!isPrivateParty) {
            addGuildSelectionSection(pane)
        } else {
            addPrivatePartyInfo(pane)
        }

        // Row 3: Role restrictions
        addRoleRestrictionSection(pane)

        // Row 4-5: Available roles
        addRoleSelectionSection(pane)

        // Row 5: Actions
        addActionButtons(pane)

        gui.show(player)
    }

    private fun addPartyTypeSection(pane: StaticPane) {
        // Private party toggle
        val config = getKoin().get<ConfigService>().loadConfig()
        val privateItem = ItemStack(if (isPrivateParty) Material.RED_CONCRETE else Material.GREEN_CONCRETE)
            .name("${if (isPrivateParty) "<red>🔒" else "<green>🌐"} Party Type")
            .lore("<gray>Current: <white>${if (isPrivateParty) "Private (Guild Only)" else "Public (Multi-Guild)"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Private parties:")
            .addAdventureLore(player, messageService, "<gray>• Only your guild members")
            .addAdventureLore(player, messageService, "<gray>• No guild invitations")
            .addAdventureLore(player, messageService, "<gray>• Simpler party management")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Public parties:")
            .addAdventureLore(player, messageService, "<gray>• Can invite other guilds")
            .addAdventureLore(player, messageService, "<gray>• More complex coordination")
            .addAdventureLore(player, messageService, "<gray>")
            .lore(if (isPrivateParty) "<yellow>Click to make public" else "<green>Click to make private")

        val privateGuiItem = GuiItem(privateItem) {
            if (config.party.allowPrivateParties) {
                isPrivateParty = !isPrivateParty
                if (isPrivateParty) {
                    // Clear any selected guilds when making private
                    selectedGuilds.clear()
                    selectedGuilds.add(guild.id)
                }
                player.sendMessage("<green>✅ Party type changed to ${if (isPrivateParty) "private" else "public"}")
                open() // Refresh menu
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Private parties are disabled in the configuration!")
            }
        }
        pane.addItem(privateGuiItem, 1, 1)
    }

    private fun addPrivatePartyInfo(pane: StaticPane) {
        val infoItem = ItemStack(Material.SHIELD)
            .setAdventureName(player, messageService, "<green>✅ Private Guild Party")
            .addAdventureLore(player, messageService, "<gray>This party will only include")
            .addAdventureLore(player, messageService, "<gray>members from your guild")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>• No external guild invitations")
            .addAdventureLore(player, messageService, "<gray>• Simpler management")
            .addAdventureLore(player, messageService, "<gray>• All guild members can join")

        pane.addItem(GuiItem(infoItem), 1, 2)
    }

    private fun addPartyInfoSection(pane: StaticPane) {
        // Party name
        val nameItem = ItemStack(Material.NAME_TAG)
            .setAdventureName(player, messageService, "<gold>📝 Party Name")
            .lore("<gray>Current: ${if (partyName.isNotEmpty()) "§f$partyName" else "<red>Not set"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Requirements:")
            .addAdventureLore(player, messageService, "<gray>• 1-32 characters")
            .addAdventureLore(player, messageService, "<gray>• Optional")
            .addAdventureLore(player, messageService, "<gray>")

        if (inputMode == "name") {
            nameItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR NAME INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type the party name in chat")
                .addAdventureLore(player, messageService, "<gray>Or click cancel to stop")
        } else {
            nameItem.addAdventureLore(player, messageService, "<yellow>Click to set party name")
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<yellow>Already waiting for name input. Type the name or click cancel.")
            }
        }
        pane.addItem(nameGuiItem, 1, 0)

        // Party summary
        val guilds = selectedGuilds.size
        val roles = restrictedRoles.size
        val summaryItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>📊 Party Summary")
            .addAdventureLore(player, messageService, "<gray>Guilds: <white>$guilds")
            .lore("<gray>Role restrictions: <white>${if (roles == 0) "None" else "$roles roles"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>Duration: <white>24 hours")
            .addAdventureLore(player, messageService, "<gray>Leader: <white>${player.name}")

        pane.addItem(GuiItem(summaryItem), 7, 0)
    }

    private fun addGuildSelectionSection(pane: StaticPane) {
        // Display current guild
        val currentGuildItem = ItemStack(Material.GREEN_BANNER)
            .setAdventureName(player, messageService, "<green>✅ ${guild.name}")
            .addAdventureLore(player, messageService, "<gray>Your current guild")
            .addAdventureLore(player, messageService, "<gray>Always included")
        pane.addItem(GuiItem(currentGuildItem), 1, 1)

        // Guild invitation button
        val selectedCount = selectedGuilds.size - 1 // Subtract 1 for current guild
        val inviteItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<gold>📋 INVITE GUILDS ($selectedCount selected)")
            .addAdventureLore(player, messageService, "<gray>Click to select guilds to invite")
            .addAdventureLore(player, messageService, "<gray>Selected guilds will be invited")
            .addAdventureLore(player, messageService, "<gray>when the party is created")

        val inviteGuiItem = GuiItem(inviteItem) {
            val menuFactory = MenuFactory()
            menuNavigator.openMenu(menuFactory.createGuildSelectionMenu(menuNavigator, player, guild, selectedGuilds))
        }
        pane.addItem(inviteGuiItem, 3, 1)

        // Show selected guilds preview (up to 4)
        val additionalGuilds = selectedGuilds.filter { it != guild.id }
        additionalGuilds.take(4).forEachIndexed { index, guildId ->
            val selectedGuild = guildService.getGuild(guildId)
            if (selectedGuild != null) {
                val previewItem = ItemStack(Material.LIME_BANNER)
                    .setAdventureName(player, messageService, "<green>✅ ${selectedGuild.name}")
                    .addAdventureLore(player, messageService, "<gray>Will be invited to party")
                pane.addItem(GuiItem(previewItem), 5 + index, 1)
            }
        }

        // Show overflow indicator if more than 4 selected
        if (additionalGuilds.size > 4) {
            val overflowItem = ItemStack(Material.PAPER)
                .setAdventureName(player, messageService, "<gray>... and ${additionalGuilds.size - 4} more")
                .addAdventureLore(player, messageService, "<gray>Click invite button to see all")
            pane.addItem(GuiItem(overflowItem), 8, 1)
        }
    }

    private fun addRoleRestrictionSection(pane: StaticPane) {
        val hasRestrictions = restrictedRoles.isNotEmpty()
        val restrictionItem = ItemStack(if (hasRestrictions) Material.REDSTONE_TORCH else Material.LEVER)
            .setAdventureName(player, messageService, "<gold>🔒 Role Restrictions")
            .lore("<gray>Status: <white>${if (hasRestrictions) "Enabled" else "Disabled"}")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>When enabled, only selected roles")
            .addAdventureLore(player, messageService, "<gray>can join the party")
            .addAdventureLore(player, messageService, "<gray>")

        if (hasRestrictions) {
            restrictionItem.addAdventureLore(player, messageService, "<red>Click to disable restrictions")
        } else {
            restrictionItem.addAdventureLore(player, messageService, "<green>Click to enable restrictions")
        }

        val restrictionGuiItem = GuiItem(restrictionItem) {
            if (hasRestrictions) {
                restrictedRoles.clear()
                roleSelectionMode = false
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Disabled role restrictions - all guild members can join")
            } else {
                roleSelectionMode = true
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Enabled role restrictions - select roles below")
            }
            open() // Refresh menu
        }
        pane.addItem(restrictionGuiItem, 1, 2)

        // Role selection button (always visible)
        val selectRolesItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<gold>👥 SELECT ROLES")
            .addAdventureLore(player, messageService, "<gray>Choose which roles can join")
            .addAdventureLore(player, messageService, "<gray>Only works when restrictions enabled")
            .addAdventureLore(player, messageService, "<gray>")
            .lore(if (roleSelectionMode) "<green>Click to select roles" else "<gray>Enable restrictions first")

        val selectRolesGuiItem = GuiItem(selectRolesItem) {
            if (roleSelectionMode) {
                // Show role selection - this will be handled by refreshing the menu
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Role selection enabled - select roles below")
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Enable role restrictions first!")
            }
            open() // Refresh menu to show role selection
        }
        pane.addItem(selectRolesGuiItem, 3, 2)
    }

    private fun addRoleSelectionSection(pane: StaticPane) {
        // Only show role selection if restrictions are enabled
        if (!roleSelectionMode) return

        val guildRanks = rankService.listRanks(guild.id).sortedBy { it.priority }

        guildRanks.forEachIndexed { index, rank ->
            if (index >= 21) return@forEachIndexed // Limit to fit in rows 3-4

            val row = 3 + (index / 7)
            val col = 1 + (index % 7)

            val isSelected = restrictedRoles.contains(rank.id)
            val rankItem = ItemStack(if (isSelected) Material.LIME_CONCRETE else Material.RED_CONCRETE)
                .name("${if (isSelected) "<green>✓" else "<red>✗"} ${rank.name}")
                .addAdventureLore(player, messageService, "<gray>Priority: <white>${rank.priority}")
                .addAdventureLore(player, messageService, "<gray>Members: <white>${memberService.getMembersByRank(guild.id, rank.id).size}")
                .addAdventureLore(player, messageService, "<gray>")
                .lore(if (isSelected) "<red>Click to remove from allowed roles" else "<green>Click to add to allowed roles")

            val rankGuiItem = GuiItem(rankItem) {
                if (isSelected) {
                    restrictedRoles.remove(rank.id)
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Removed ${rank.name} from allowed roles")
                } else {
                    restrictedRoles.add(rank.id)
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Added ${rank.name} to allowed roles")
                }
                open() // Refresh menu
            }
            pane.addItem(rankGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Create party
        val canCreate = selectedGuilds.size >= 2
        val createItem = ItemStack(if (canCreate) Material.EMERALD_BLOCK else Material.GRAY_CONCRETE)
            .name(if (canCreate) "<green>✅ Create Party" else "<red>❌ Cannot Create")
            .addAdventureLore(player, messageService, "<gray>Create the party with selected settings")

        if (canCreate) {
            createItem.addAdventureLore(player, messageService, "<gray>")
                .addAdventureLore(player, messageService, "<green>Ready to create party!")
                .addAdventureLore(player, messageService, "<gray>Click to confirm")
        } else {
            createItem.addAdventureLore(player, messageService, "<gray>")
            createItem.addAdventureLore(player, messageService, "<red>• Need at least 2 guilds")
        }

        val createGuiItem = GuiItem(createItem) {
            if (canCreate) {
                createParty()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Cannot create party - need at least 2 guilds!")
            }
        }
        pane.addItem(createGuiItem, 1, 5)

        // Clear all
        val clearItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>🗑️ Clear All")
            .addAdventureLore(player, messageService, "<gray>Reset all selections")

        val clearGuiItem = GuiItem(clearItem) {
            partyName = ""
            selectedGuilds.clear()
            selectedGuilds.add(guild.id) // Keep current guild
            restrictedRoles.clear()
            AdventureMenuHelper.sendMessage(player, messageService, "<yellow>🗑️ Cleared all selections!")
            open() // Refresh menu
        }
        pane.addItem(clearGuiItem, 3, 5)

        // Back button
        val backItem = ItemStack(Material.ARROW)
            .setAdventureName(player, messageService, "<gray>⬅️ Back")
            .addAdventureLore(player, messageService, "<gray>Return to party management")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 7, 5)
    }

    private fun createParty() {
        try {
            // Validate party creation requirements
            if (!isPrivateParty && selectedGuilds.size < 2) {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You must invite at least one other guild!")
                return
            }

            if (isPrivateParty && selectedGuilds.size != 1) {
                // Reset to just current guild for private parties
                selectedGuilds.clear()
                selectedGuilds.add(guild.id)
            }

            // Create the party
            val partyId = UUID.randomUUID()
            val config = getKoin().get<ConfigService>().loadConfig().party
            val expiresAt = java.time.Instant.now().plus(Duration.ofHours(config.defaultPartyDurationHours.toLong()))

            val party = Party(
                id = partyId,
                name = partyName.ifBlank { null },
                guildIds = selectedGuilds,
                leaderId = player.uniqueId,
                status = PartyStatus.ACTIVE,
                createdAt = java.time.Instant.now(),
                expiresAt = expiresAt,
                restrictedRoles = restrictedRoles.ifEmpty { null }
            )

            // Use PartyService to create the party
            val partyService = getKoin().get<PartyService>()
            val createdParty = partyService.createParty(party)

            if (createdParty != null) {
                AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Party created successfully!")
                player.sendMessage("<gray>Name: <white>${party.name ?: "Unnamed"}")
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Guilds: <white>${selectedGuilds.size}")
                if (restrictedRoles.isNotEmpty()) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Role restrictions: <white>${restrictedRoles.size} roles")
                }
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Expires in: <white>24 hours")

                // Send invites to selected guilds (only for public parties)
                if (!isPrivateParty) {
                    sendGuildInvites(createdParty as Party)
                } else {
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Private party created! All guild members can now join.")
                }

                // Return to party management
                menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Failed to create party!")
            }
        } catch (e: Exception) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Error creating party!")
            e.printStackTrace()
        }
    }

    private fun sendGuildInvites(party: Party) {
        val invitedGuilds = selectedGuilds.filter { it != guild.id }

        for (guildId in invitedGuilds) {
            val invitedGuild = guildService.getGuild(guildId)
            if (invitedGuild != null) {
                // Send invite notification (you could implement a proper invite system here)
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>✉ Invite sent to <white>${invitedGuild.name}")

                // TODO: Implement proper guild invite system with accept/decline
                // For now, just notify the player that invites were sent
            }
        }

        if (invitedGuilds.isNotEmpty()) {
            player.sendMessage("<green>✅ Sent ${invitedGuilds.size} guild invite${if (invitedGuilds.size != 1) "s" else ""}!")
        }
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== PARTY NAME INPUT ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type the party name in chat.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Leave blank for no name.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Maximum 32 characters.")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to stop input mode")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>========================")
    }

    // ChatInputHandler interface methods
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "name" -> {
                val error = validatePartyName(input)
                if (error != null) {
                    AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Invalid name: $error")
                    AdventureMenuHelper.sendMessage(player, messageService, "<gray>Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    partyName = input
                    inputMode = ""
                    AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Party name set to: '$input'")
                }
            }
        }

        // Reopen the menu
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }

    override fun onCancel(player: Player) {
        inputMode = ""
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Input cancelled.")

        // Reopen the menu
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }

    private fun validatePartyName(name: String): String? {
        if (name.length > 32) {
            return "Name must be 32 characters or less (current: ${name.length})"
        }
        return null
    }

    override fun passData(data: Any?) {
        when (data) {
            is Map<*, *> -> {
                // Handle data from GuildSelectionMenu
                val selectedGuildsData = data["selectedGuilds"] as? Set<*> ?: emptySet<Any>()
                selectedGuilds.clear()
                selectedGuilds.add(guild.id) // Always include current guild
                selectedGuildsData.forEach { guildId ->
                    if (guildId is UUID) {
                        selectedGuilds.add(guildId)
                    }
                }
            }
            else -> {
                // Default case - assume it's a Guild
                guild = data as? Guild ?: return
            }
        }
    }
}

