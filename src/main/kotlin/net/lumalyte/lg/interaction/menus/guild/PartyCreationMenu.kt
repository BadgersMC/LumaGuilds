package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.nexus.i18n.LangService
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
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory,
    private val lang: LangService
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
            player.sendMessage(lang.msg("menu.party.creation.feedback.disabled"))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.party.creation.title", "guild" to guild.name)))
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
        val typeName = if (isPrivateParty) lang.legacy("menu.party.creation.type.private_name") else lang.legacy("menu.party.creation.type.public_name")
        val typeStatus = if (isPrivateParty) lang.raw("menu.party.creation.type.private_status") else lang.raw("menu.party.creation.type.public_status")
        val typeAction = if (isPrivateParty) lang.legacy("menu.party.creation.type.make_public") else lang.legacy("menu.party.creation.type.make_private")
        val privateItem = ItemStack.of(if (isPrivateParty) Material.RED_CONCRETE else Material.GREEN_CONCRETE)
            .name(typeName)
            .lore(lang.legacy("menu.party.creation.type.current", "type" to typeStatus))
            .lore(lang.raw("menu.common.blank"))
            .lore(lang.legacy("menu.party.creation.type.private_header"))
            .lore(lang.legacy("menu.party.creation.type.private_members"))
            .lore(lang.legacy("menu.party.creation.type.private_invites"))
            .lore(lang.legacy("menu.party.creation.type.private_management"))
            .lore(lang.raw("menu.common.blank"))
            .lore(lang.legacy("menu.party.creation.type.public_header"))
            .lore(lang.legacy("menu.party.creation.type.public_invites"))
            .lore(lang.legacy("menu.party.creation.type.public_coordination"))
            .lore(lang.raw("menu.common.blank"))
            .lore(typeAction)

        val privateGuiItem = GuiItem(privateItem) {
            if (config.party.allowPrivateParties) {
                isPrivateParty = !isPrivateParty
                if (isPrivateParty) {
                    // Clear any selected guilds when making private
                    selectedGuilds.clear()
                    selectedGuilds.add(guild.id)
                }
                val changedType = if (isPrivateParty) lang.raw("menu.party.creation.type.private") else lang.raw("menu.party.creation.type.public")
                player.sendMessage(lang.msg("menu.party.creation.feedback.type_changed", "type" to changedType))
                open() // Refresh menu
            } else {
                player.sendMessage(lang.msg("menu.party.creation.feedback.private_disabled"))
            }
        }
        pane.addItem(privateGuiItem, 1, 1)
    }

    private fun addPrivatePartyInfo(pane: StaticPane) {
        val infoItem = ItemStack.of(Material.SHIELD)
            .name(lang.legacy("menu.party.creation.private_info.name"))
            .lore(lang.legacy("menu.party.creation.private_info.first"))
            .lore(lang.legacy("menu.party.creation.private_info.second"))
            .lore(lang.raw("menu.common.blank"))
            .lore(lang.legacy("menu.party.creation.private_info.no_invites"))
            .lore(lang.legacy("menu.party.creation.private_info.management"))
            .lore(lang.legacy("menu.party.creation.private_info.members"))

        pane.addItem(GuiItem(infoItem), 1, 2)
    }

    private fun addPartyInfoSection(pane: StaticPane) {
        // Party name
        val currentName = if (partyName.isNotEmpty()) lang.legacy("menu.party.creation.name.value", "party" to partyName) else lang.legacy("menu.party.creation.name.not_set")
        val nameItem = ItemStack.of(Material.NAME_TAG)
            .name(lang.legacy("menu.party.creation.name.name"))
            .lore(lang.legacy("menu.party.creation.name.current", "value" to currentName))
            .lore(lang.raw("menu.common.blank"))
            .lore(lang.legacy("menu.party.creation.name.requirements"))
            .lore(lang.legacy("menu.party.creation.name.length"))
            .lore(lang.legacy("menu.party.creation.name.optional"))
            .lore(lang.raw("menu.common.blank"))

        if (inputMode == "name") {
            nameItem.name(lang.legacy("menu.party.creation.name.waiting"))
                .lore(lang.legacy("menu.party.creation.name.input_hint"))
                .lore(lang.legacy("menu.party.creation.name.cancel_hint"))
        } else {
            nameItem.lore(lang.legacy("menu.party.creation.name.click"))
        }

        val nameGuiItem = GuiItem(nameItem) {
            if (inputMode != "name") {
                startNameInput()
            } else {
                player.sendMessage(lang.msg("menu.party.creation.feedback.already_waiting"))
            }
        }
        pane.addItem(nameGuiItem, 1, 0)

        // Party summary
        val guilds = selectedGuilds.size
        val roles = restrictedRoles.size
        val roleSummary = if (roles == 0) lang.raw("menu.party.creation.summary.none") else lang.legacy("menu.party.creation.summary.roles", "count" to roles)
        val summaryItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.party.creation.summary.name"))
            .lore(lang.legacy("menu.party.creation.summary.guilds", "count" to guilds))
            .lore(lang.legacy("menu.party.creation.summary.restrictions", "roles" to roleSummary))
            .lore(lang.raw("menu.common.blank"))
            .lore(lang.legacy("menu.party.creation.summary.duration"))
            .lore(lang.legacy("menu.party.creation.summary.leader", "player" to player.name))

        pane.addItem(GuiItem(summaryItem), 7, 0)
    }

    private fun addGuildSelectionSection(pane: StaticPane) {
        // Display current guild
        val currentGuildItem = ItemStack.of(Material.GREEN_BANNER)
            .name(lang.legacy("menu.party.creation.guild.current_name", "guild" to guild.name))
            .lore(lang.legacy("menu.party.creation.guild.current_lore"))
            .lore(lang.legacy("menu.party.creation.guild.included"))
        pane.addItem(GuiItem(currentGuildItem), 1, 1)

        // Guild invitation button
        val selectedCount = selectedGuilds.size - 1 // Subtract 1 for current guild
        val inviteItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.party.creation.guild.invite_name", "count" to selectedCount))
            .lore(lang.legacy("menu.party.creation.guild.invite_lore"))
            .lore(lang.legacy("menu.party.creation.guild.invited_first"))
            .lore(lang.legacy("menu.party.creation.guild.invited_second"))

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
                    .name(lang.legacy("menu.party.creation.guild.selected_name", "guild" to selectedGuild.name))
                    .lore(lang.legacy("menu.party.creation.guild.selected_lore"))
                pane.addItem(GuiItem(previewItem), 5 + index, 1)
            }
        }

        // Show overflow indicator if more than 4 selected
        if (additionalGuilds.size > 4) {
            val overflowItem = ItemStack.of(Material.PAPER)
                .name(lang.legacy("menu.party.creation.guild.more", "count" to additionalGuilds.size - 4))
                .lore(lang.legacy("menu.party.creation.guild.more_lore"))
            pane.addItem(GuiItem(overflowItem), 8, 1)
        }
    }

    private fun addRoleRestrictionSection(pane: StaticPane) {
        val hasRestrictions = restrictedRoles.isNotEmpty()
        val restrictionStatus = if (hasRestrictions) lang.raw("menu.party.creation.restriction.enabled") else lang.raw("menu.party.creation.restriction.disabled")
        val restrictionItem = ItemStack.of(if (hasRestrictions) Material.REDSTONE_TORCH else Material.LEVER)
            .name(lang.legacy("menu.party.creation.restriction.name"))
            .lore(lang.legacy("menu.party.creation.restriction.status", "status" to restrictionStatus))
            .lore(lang.raw("menu.common.blank"))
            .lore(lang.legacy("menu.party.creation.restriction.first"))
            .lore(lang.legacy("menu.party.creation.restriction.second"))
            .lore(lang.raw("menu.common.blank"))

        if (hasRestrictions) {
            restrictionItem.lore(lang.legacy("menu.party.creation.restriction.disable"))
        } else {
            restrictionItem.lore(lang.legacy("menu.party.creation.restriction.enable"))
        }

        val restrictionGuiItem = GuiItem(restrictionItem) {
            if (hasRestrictions) {
                restrictedRoles.clear()
                roleSelectionMode = false
                player.sendMessage(lang.msg("menu.party.creation.feedback.restrictions_disabled"))
            } else {
                roleSelectionMode = true
                player.sendMessage(lang.msg("menu.party.creation.feedback.restrictions_enabled"))
            }
            open() // Refresh menu
        }
        pane.addItem(restrictionGuiItem, 1, 2)

        // Role selection button (always visible)
        val roleAction = if (roleSelectionMode) lang.legacy("menu.party.creation.roles.action") else lang.legacy("menu.party.creation.roles.enable_first")
        val selectRolesItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.party.creation.roles.name"))
            .lore(lang.legacy("menu.party.creation.roles.lore"))
            .lore(lang.legacy("menu.party.creation.roles.requirement"))
            .lore(lang.raw("menu.common.blank"))
            .lore(roleAction)

        val selectRolesGuiItem = GuiItem(selectRolesItem) {
            if (roleSelectionMode) {
                // Show role selection - this will be handled by refreshing the menu
                player.sendMessage(lang.msg("menu.party.creation.feedback.role_selection_enabled"))
            } else {
                player.sendMessage(lang.msg("menu.party.creation.feedback.enable_restrictions"))
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
            val rankName = if (isSelected) lang.legacy("menu.party.creation.roles.selected_name", "rank" to rank.name) else lang.legacy("menu.party.creation.roles.available_name", "rank" to rank.name)
            val rankAction = if (isSelected) lang.legacy("menu.party.creation.roles.remove") else lang.legacy("menu.party.creation.roles.add")
            val rankItem = ItemStack.of(if (isSelected) Material.LIME_CONCRETE else Material.RED_CONCRETE)
                .name(rankName)
                .lore(lang.legacy("menu.party.creation.roles.priority", "priority" to rank.priority))
                .lore(lang.legacy("menu.party.creation.roles.members", "count" to memberService.getMembersByRank(guild.id, rank.id).size))
                .lore(lang.raw("menu.common.blank"))
                .lore(rankAction)

            val rankGuiItem = GuiItem(rankItem) {
                if (isSelected) {
                    restrictedRoles.remove(rank.id)
                    player.sendMessage(lang.msg("menu.party.creation.feedback.role_removed", "rank" to rank.name))
                } else {
                    restrictedRoles.add(rank.id)
                    player.sendMessage(lang.msg("menu.party.creation.feedback.role_added", "rank" to rank.name))
                }
                open() // Refresh menu
            }
            pane.addItem(rankGuiItem, col, row)
        }
    }

    private fun addActionButtons(pane: StaticPane) {
        // Create party - allow single guild for private parties, or 2+ guilds for public
        val canCreate = if (isPrivateParty) selectedGuilds.size >= 1 else selectedGuilds.size >= 2
        val createName = if (canCreate) lang.legacy("menu.party.creation.action.create") else lang.legacy("menu.party.creation.action.cannot_create")
        val createItem = ItemStack.of(if (canCreate) Material.EMERALD_BLOCK else Material.GRAY_CONCRETE)
            .name(createName)
            .lore(lang.legacy("menu.party.creation.action.create_lore"))

        if (canCreate) {
            createItem.lore(lang.raw("menu.common.blank"))
                .lore(lang.legacy("menu.party.creation.action.ready"))
                .lore(lang.legacy("menu.party.creation.action.confirm"))
        } else {
            createItem.lore(lang.raw("menu.common.blank"))
            createItem.lore(if (isPrivateParty) lang.legacy("menu.party.creation.action.need_one") else lang.legacy("menu.party.creation.action.need_two"))
        }

        val createGuiItem = GuiItem(createItem) {
            if (canCreate) {
                createParty()
            } else {
                player.sendMessage(if (isPrivateParty) lang.msg("menu.party.creation.feedback.need_one") else lang.msg("menu.party.creation.feedback.need_two"))
            }
        }
        pane.addItem(createGuiItem, 1, 5)

        // Clear all
        val clearItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.party.creation.action.clear"))
            .lore(lang.legacy("menu.party.creation.action.clear_lore"))

        val clearGuiItem = GuiItem(clearItem) {
            partyName = ""
            selectedGuilds.clear()
            selectedGuilds.add(guild.id) // Keep current guild
            restrictedRoles.clear()
            player.sendMessage(lang.msg("menu.party.creation.feedback.cleared"))
            open() // Refresh menu
        }
        pane.addItem(clearGuiItem, 3, 5)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.party.creation.action.back"))
            .lore(lang.legacy("menu.party.creation.action.back_lore"))

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, 7, 5)
    }

    private fun createParty() {
        try {
            // Validate party name (no spaces allowed)
            if (partyName.contains(" ")) {
                player.sendMessage(lang.msg("menu.party.creation.feedback.name_spaces"))
                player.sendMessage(lang.msg("menu.party.creation.feedback.name_hint"))
                return
            }

            // Validate party creation requirements
            if (!isPrivateParty && selectedGuilds.size < 2) {
                player.sendMessage(lang.msg("menu.party.creation.feedback.invite_one"))
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
                player.sendMessage(lang.msg("menu.party.creation.feedback.created"))
                player.sendMessage(lang.msg("menu.party.creation.feedback.created_name", "party" to (party.name ?: lang.raw("menu.party.creation.unnamed"))))
                player.sendMessage(lang.msg("menu.party.creation.feedback.created_guilds", "count" to selectedGuilds.size))
                if (restrictedRoles.isNotEmpty()) {
                    player.sendMessage(lang.msg("menu.party.creation.feedback.created_roles", "count" to restrictedRoles.size))
                }
                player.sendMessage(lang.msg("menu.party.creation.feedback.expires"))

                // Send invites to selected guilds (only for public parties)
                if (!isPrivateParty) {
                    sendGuildInvites(createdParty as Party)
                } else {
                    player.sendMessage(lang.msg("menu.party.creation.feedback.private_created"))
                }

                // Return to party management
                menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.party.creation.feedback.create_failed"))
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.party.creation.feedback.create_error"))
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
                    player.sendMessage(lang.msg("menu.party.creation.feedback.invite_sent", "guild" to invitedGuild.name))

                    // Notify online members of the invited guild
                    val invitedMembers = memberService.getGuildMembers(guildId)
                    val server = org.bukkit.Bukkit.getServer()
                    invitedMembers.forEach { member ->
                        val onlinePlayer = server.getPlayer(member.playerId)
                        if (onlinePlayer != null && onlinePlayer.isOnline) {
                            onlinePlayer.sendMessage(lang.msg("menu.party.creation.feedback.invite_received", "guild" to guild.name))
                            onlinePlayer.sendMessage(lang.msg("menu.party.creation.feedback.invite_party", "party" to (party.name ?: lang.raw("menu.party.creation.unnamed"))))
                        }
                    }
                } else {
                    player.sendMessage(lang.msg("menu.party.creation.feedback.invite_failed", "guild" to invitedGuild.name))
                }
            }
        }

        if (successCount > 0) {
            if (successCount == 1) {
                player.sendMessage(lang.msg("menu.party.creation.feedback.invites_sent_one", "count" to successCount))
            } else {
                player.sendMessage(lang.msg("menu.party.creation.feedback.invites_sent_many", "count" to successCount))
            }
        }
    }

    private fun startNameInput() {
        inputMode = "name"
        chatInputListener.startInputMode(player, this)
        player.closeInventory()

        player.sendMessage(lang.msg("menu.party.creation.input.header"))
        player.sendMessage(lang.msg("menu.party.creation.input.prompt"))
        player.sendMessage(lang.msg("menu.party.creation.input.optional"))
        player.sendMessage(lang.msg("menu.party.creation.input.maximum"))
        player.sendMessage(lang.msg("menu.party.creation.input.blank"))
        player.sendMessage(lang.msg("menu.party.creation.input.cancel"))
        player.sendMessage(lang.msg("menu.party.creation.input.footer"))
    }

    // ChatInputHandler interface methods
    override fun onChatInput(player: Player, input: String) {
        when (inputMode) {
            "name" -> {
                val error = validatePartyName(input)
                if (error != null) {
                    player.sendMessage(lang.msg("menu.party.creation.feedback.invalid_name", "reason" to error))
                    player.sendMessage(lang.msg("menu.party.creation.feedback.retry_name"))
                    // Keep input mode active and reopen menu for retry
                } else {
                    partyName = input
                    inputMode = ""
                    player.sendMessage(lang.msg("menu.party.creation.feedback.name_set", "party" to input))
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
        player.sendMessage(lang.msg("menu.party.creation.feedback.input_cancelled"))

        // Reopen the menu
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LumaGuilds") ?: return // Plugin not found, cannot schedule task
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable {
            open()
        })
    }

    private fun validatePartyName(name: String): String? {
        if (name.length > 32) {
            return lang.legacy("menu.party.creation.validation.name_length", "current" to name.length)
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

