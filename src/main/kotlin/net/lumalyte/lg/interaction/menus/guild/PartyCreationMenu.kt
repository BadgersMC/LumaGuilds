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
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import java.time.Duration
import java.util.*

class PartyCreationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val partyService: PartyService,
    private val rankService: RankService,
    private val memberService: MemberService,
    private val chatInputListener: ChatInputListener,
    private val configService: ConfigService,
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory
): Menu, ChatInputHandler {

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
            player.sendMessage("§c❌ Parties are disabled on this server!")
            return
        }

        val gui = ChestGui(6, "§6Create New Party - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
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
        val config = configService.loadConfig()
        val privateItem = ItemStack(if (isPrivateParty) Material.RED_CONCRETE else Material.GREEN_CONCRETE)
            .name("${if (isPrivateParty) "§c🔒" else "§a🌐"} Party Type")
            .lore("§7Current: §f${if (isPrivateParty) "Private (Guild Only)" else "Public (Multi-Guild)"}")
            .lore("§7")
            .lore("§7Private parties:")
            .lore("§7• Only your guild members")
            .lore("§7• No guild invitations")
            .lore("§7• Simpler party management")
            .lore("§7")
            .lore("§7Public parties:")
            .lore("§7• Can invite other guilds")
            .lore("§7• More complex coordination")
            .lore("§7")
            .lore(if (isPrivateParty) "§eClick to make public" else "§aClick to make private")

        val privateGuiItem = GuiItem(privateItem) {
            if (config.party.allowPrivateParties) {
                isPrivateParty = !isPrivateParty
                if (isPrivateParty) {
                    // Clear any selected guilds when making private
                    selectedGuilds.clear()
                    selectedGuilds.add(guild.id)
                }
                player.sendMessage("§a✅ Party type changed to ${if (isPrivateParty) "private" else "public"}")
                open() // Refresh menu
            } else {
                player.sendMessage("§c❌ Private parties are disabled in the configuration!")
            }
        }
        pane.addItem(privateGuiItem, 1, 1)
    }

    private fun addPrivatePartyInfo(pane: StaticPane) {
        val infoItem = ItemStack.of(Material.SHIELD)
            .name("§a✅ Private Guild Party")
            .lore("§7This party will only include")
            .lore("§7members from your guild")
            .lore("§7")
            .lore("§7• No external guild invitations")
            .lore("§7• Simpler management")
            .lore("§7• All guild members can join")

        pane.addItem(GuiItem(infoItem), 1, 2)
    }

    private fun addPartyInfoSection(pane: StaticPane) {
        // Party name
        val nameItem = ItemStack.of(Material.NAME_TAG)
            .name("§6📝 Party Name")
            .lore("§7Current: ${if (partyName.isNotEmpty()) "§f$partyName" else "§cNot set"}")
            .lore("§7")
            .lore("§7Requirements:")
            .lore("§7• 1-32 characters")
            .lore("§7• Optional")
            .lore("§7")

        if (inputMode == "name") {
            nameItem.name("§e⏳ WAITING FOR NAME INPUT...")
                .lore("§7Type the party name in chat")
                .lore("§7Or click cancel to stop")
        } else {
            nameItem.lore("§eClick to set party name")
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                player.sendMessage("§eAlready waiting for name input. Type the name or click cancel.")
            }
        }
        pane.addItem(nameGuiItem, 1, 0)

        // Party summary
        val guilds = selectedGuilds.size
        val roles = restrictedRoles.size
        val summaryItem = ItemStack.of(Material.BOOK)
            .name("§6📊 Party Summary")
            .lore("§7Guilds: §f$guilds")
            .lore("§7Role restrictions: §f${if (roles == 0) "None" else "$roles roles"}")
            .lore("§7")
            .lore("§7Duration: §f24 hours")
            .lore("§7Leader: §f${player.name}")

        pane.addItem(GuiItem(summaryItem), 7, 0)
    }

    private fun addGuildSelectionSection(pane: StaticPane) {
        // Display current guild
        val currentGuildItem = ItemStack.of(Material.GREEN_BANNER)
            .name("§a✅ ${guild.name}")
            .lore("§7Your current guild")
            .lore("§7Always included")
        pane.addItem(GuiItem(currentGuildItem), 1, 1)

        // Guild invitation button
        val selectedCount = selectedGuilds.size - 1 // Subtract 1 for current guild
        val inviteItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name("§6📋 INVITE GUILDS ($selectedCount selected)")
            .lore("§7Click to select guilds to invite")
            .lore("§7Selected guilds will be invited")
            .lore("§7when the party is created")

        val inviteGuiItem = GuiItem(inviteItem) {
            menuNavigator.openMenu(menuFactory.createGuildSelectionMenu(menuNavigator, player, guild, selectedGuilds))
        }
        pane.addItem(inviteGuiItem, 3, 1)

        // Show selected guilds preview (up to 4)
        val additionalGuilds = selectedGuilds.filter { it != guild.id }
        additionalGuilds.take(4).forEachIndexed { index, guildId ->
            val selectedGuild = guildService.getGuild(guildId)
            if (selectedGuild != null) {
                val previewItem = ItemStack.of(Material.LIME_BANNER)
                    .name("§a✅ ${selectedGuild.name}")
                    .lore("§7Will be invited to party")
                pane.addItem(GuiItem(previewItem), 5 + index, 1)
            }
        }

        // Show overflow indicator if more than 4 selected
        if (additionalGuilds.size > 4) {
            val overflowItem = ItemStack.of(Material.PAPER)
                .name("§7... and ${additionalGuilds.size - 4} more")
                .lore("§7Click invite button to see all")
            pane.addItem(GuiItem(overflowItem), 8, 1)
        }
    }

    private fun addRoleRestrictionSection(pane: StaticPane) {
        val hasRestrictions = restrictedRoles.isNotEmpty()
        val restrictionItem = ItemStack(if (hasRestrictions) Material.REDSTONE_TORCH else Material.LEVER)
            .name("§6🔒 Role Restrictions")
            .lore("§7Status: §f${if (hasRestrictions) "Enabled" else "Disabled"}")
            .lore("§7")
            .lore("§7When enabled, only selected roles")
            .lore("§7can join the party")
            .lore("§7")

        if (hasRestrictions) {
            restrictionItem.lore("§cClick to disable restrictions")
        } else {
            restrictionItem.lore("§aClick to enable restrictions")
        }

        val restrictionGuiItem = GuiItem(restrictionItem) {
            if (hasRestrictions) {
                restrictedRoles.clear()
                roleSelectionMode = false
                player.sendMessage("§c❌ Disabled role restrictions - all guild members can join")
            } else {
                roleSelectionMode = true
                player.sendMessage("§a✅ Enabled role restrictions - select roles below")
            }
            open() // Refresh menu
        }
        pane.addItem(restrictionGuiItem, 1, 2)

        // Role selection button (always visible)
        val selectRolesItem = ItemStack.of(Material.BOOK)
            .name("§6👥 SELECT ROLES")
            .lore("§7Choose which roles can join")
            .lore("§7Only works when restrictions enabled")
            .lore("§7")
            .lore(if (roleSelectionMode) "§aClick to select roles" else "§7Enable restrictions first")

        val selectRolesGuiItem = GuiItem(selectRolesItem) {
            if (roleSelectionMode) {
                // Show role selection - this will be handled by refreshing the menu
                player.sendMessage("§a✅ Role selection enabled - select roles below")
            } else {
                player.sendMessage("§c❌ Enable role restrictions first!")
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
                .name("${if (isSelected) "§a✓" else "§c✗"} ${rank.name}")
                .lore("§7Priority: §f${rank.priority}")
                .lore("§7Members: §f${memberService.getMembersByRank(guild.id, rank.id).size}")
                .lore("§7")
                .lore(if (isSelected) "§cClick to remove from allowed roles" else "§aClick to add to allowed roles")

            val rankGuiItem = GuiItem(rankItem) {
                if (isSelected) {
                    restrictedRoles.remove(rank.id)
                    player.sendMessage("§c❌ Removed ${rank.name} from allowed roles")
                } else {
                    restrictedRoles.add(rank.id)
                    player.sendMessage("§a✅ Added ${rank.name} to allowed roles")
                }
                open() // Refresh menu
            }
            pane.addItem(rankGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Create party - allow single guild for private parties, or 2+ guilds for public
        val canCreate = if (isPrivateParty) selectedGuilds.size >= 1 else selectedGuilds.size >= 2
        val createItem = ItemStack(if (canCreate) Material.EMERALD_BLOCK else Material.GRAY_CONCRETE)
            .name(if (canCreate) "§a✅ Create Party" else "§c❌ Cannot Create")
            .lore("§7Create the party with selected settings")

        if (canCreate) {
            createItem.lore("§7")
                .lore("§aReady to create party!")
                .lore("§7Click to confirm")
        } else {
            createItem.lore("§7")
            createItem.lore(if (isPrivateParty) "§c• Need at least 1 guild" else "§c• Need at least 2 guilds")
        }

        val createGuiItem = GuiItem(createItem) {
            if (canCreate) {
                createParty()
            } else {
                player.sendMessage(if (isPrivateParty) "§c❌ Cannot create party - need at least 1 guild!" else "§c❌ Cannot create party - need at least 2 guilds!")
            }
        }
        pane.addItem(createGuiItem, 1, 5)

        // Clear all
        val clearItem = ItemStack.of(Material.BARRIER)
            .name("§c🗑 Clear All")
            .lore("§7Reset all selections")

        val clearGuiItem = GuiItem(clearItem) {
            partyName = ""
            selectedGuilds.clear()
            selectedGuilds.add(guild.id) // Keep current guild
            restrictedRoles.clear()
            player.sendMessage("§e🗑 Cleared all selections!")
            open() // Refresh menu
        }
        pane.addItem(clearGuiItem, 3, 5)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name("§7⬅ Back")
            .lore("§7Return to party management")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 7, 5)
    }

    private fun createParty() {
        try {
            // Validate party name (no spaces allowed)
            if (partyName.contains(" ")) {
                player.sendMessage("§c❌ Party names cannot contain spaces!")
                player.sendMessage("§7Use underscores instead (e.g., My_Party)")
                return
            }

            // Validate party creation requirements
            if (!isPrivateParty && selectedGuilds.size < 2) {
                player.sendMessage("§c❌ You must invite at least one other guild!")
                return
            }

            if (isPrivateParty && selectedGuilds.size != 1) {
                // Reset to just current guild for private parties
                selectedGuilds.clear()
                selectedGuilds.add(guild.id)
            }

            // Create the party
            val partyId = UUID.randomUUID()
            val config = configService.loadConfig().party
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
            val createdParty = partyService.createParty(party)

            if (createdParty != null) {
                player.sendMessage("§a✅ Party created successfully!")
                player.sendMessage("§7Name: §f${party.name ?: "Unnamed"}")
                player.sendMessage("§7Guilds: §f${selectedGuilds.size}")
                if (restrictedRoles.isNotEmpty()) {
                    player.sendMessage("§7Role restrictions: §f${restrictedRoles.size} roles")
                }
                player.sendMessage("§7Expires in: §f24 hours")

                // Send invites to selected guilds (only for public parties)
                if (!isPrivateParty) {
                    sendGuildInvites(createdParty as Party)
                } else {
                    player.sendMessage("§a✅ Private party created! All guild members can now join.")
                }

                // Return to party management
                menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage("§c❌ Failed to create party!")
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage("§c❌ Error creating party!")
            e.printStackTrace()
        }
    }

    private fun sendGuildInvites(party: Party) {
        val invitedGuilds = selectedGuilds.filter { it != guild.id }
        var successCount = 0

        for (guildId in invitedGuilds) {
            val invitedGuild = guildService.getGuild(guildId)
            if (invitedGuild != null) {
                // Send party invite through PartyService
                val invite = partyService.inviteToParty(party.id, guild.id, guildId, player.uniqueId)
                if (invite != null) {
                    successCount++
                    player.sendMessage("§7✉ Invite sent to §f${invitedGuild.name}")

                    // Notify online members of the invited guild
                    val invitedMembers = memberService.getGuildMembers(guildId)
                    val server = org.bukkit.Bukkit.getServer()
                    invitedMembers.forEach { member ->
                        val onlinePlayer = server.getPlayer(member.playerId)
                        if (onlinePlayer != null && onlinePlayer.isOnline) {
                            onlinePlayer.sendMessage("§6✉ ${guild.name} has invited your guild to a party!")
                            onlinePlayer.sendMessage("§7Party: ${party.name}")
                        }
                    }
                } else {
                    player.sendMessage("§c✗ Failed to invite ${invitedGuild.name}")
                }
            }
        }

        if (successCount > 0) {
            player.sendMessage("§a✅ Sent $successCount guild invite${if (successCount != 1) "s" else ""}!")
        }
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage("§6=== PARTY NAME INPUT ===")
        player.sendMessage("§7Type the party name in chat.")
        player.sendMessage("§7Leave blank for no name.")
        player.sendMessage("§7Maximum 32 characters.")
        player.sendMessage("§7")
        player.sendMessage("§7Type 'cancel' to stop input mode")
        player.sendMessage("§6========================")
    }

    // ChatInputHandler interface methods
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "name" -> {
                val error = validatePartyName(input)
                if (error != null) {
                    player.sendMessage("§c❌ Invalid name: $error")
                    player.sendMessage("§7Please try again or type 'cancel' to stop.")
                    // Keep input mode active and reopen menu for retry
                } else {
                    partyName = input
                    inputMode = ""
                    player.sendMessage("§a✅ Party name set to: '$input'")
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
        player.sendMessage("§7Input cancelled.")

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
            is Guild -> {
                guild = data
            }
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

