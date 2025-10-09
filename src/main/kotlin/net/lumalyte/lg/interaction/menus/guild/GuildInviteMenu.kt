package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.listeners.ChatInputListener
import net.lumalyte.lg.interaction.listeners.ChatInputHandler
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildInviteMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private var inputMode = false

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Invite Player - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Info display
        addInfoDisplay(pane, 0, 0)

        // Online players list (simplified)
        addOnlinePlayersList(pane, 2, 0)

        // Manual invite button
        addManualInviteButton(pane, 4, 1)

        // Back button
        addBackButton(pane, 8, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addInfoDisplay(pane: StaticPane, x: Int, y: Int) {
        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<white>📋 INVITE PLAYERS")
            .addAdventureLore(player, messageService, "<gray>Invite players to join your guild")
            .addAdventureLore(player, messageService, "<gray>Click online players or use manual invite")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Players must accept the invitation")

        pane.addItem(GuiItem(infoItem), x, y)
    }

    private fun addOnlinePlayersList(pane: StaticPane, x: Int, y: Int) {
        // Get online players (excluding current guild members)
        val onlinePlayers = Bukkit.getOnlinePlayers()
            .filter { it != player }
            .filter { !memberService.isPlayerInGuild(it.uniqueId, guild.id) }
            .take(5) // Limit to 5 for display

        if (onlinePlayers.isEmpty()) {
            val noPlayersItem = ItemStack(Material.BARRIER)
                .setAdventureName(player, messageService, "<red>❌ NO PLAYERS AVAILABLE")
                .addAdventureLore(player, messageService, "<gray>No online players to invite")
                .addAdventureLore(player, messageService, "<gray>Use manual invite instead")

            pane.addItem(GuiItem(noPlayersItem), x, y)
            return
        }

        // Display up to 5 online players
        for ((index, onlinePlayer) in onlinePlayers.withIndex()) {
            if (index >= 5) break

            val playerHead = ItemStack(Material.PLAYER_HEAD)
            val meta = playerHead.itemMeta
            if (meta is org.bukkit.inventory.meta.SkullMeta) {
                meta.owningPlayer = onlinePlayer
                playerHead.itemMeta = meta
            }

            playerHead.setAdventureName(player, messageService, "<green>👤 ${onlinePlayer.name}")
                .addAdventureLore(player, messageService, "<gray>Click to invite this player")
                .addAdventureLore(player, messageService, "<gray>They will receive an invitation")

            val playerGuiItem = GuiItem(playerHead) {
                val menuFactory = MenuFactory()
                menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, onlinePlayer))
            }
            pane.addItem(playerGuiItem, x + index, y)
        }
    }

    private fun addManualInviteButton(pane: StaticPane, x: Int, y: Int) {
        val manualItem = ItemStack(Material.WRITABLE_BOOK)
            .setAdventureName(player, messageService, "<white>✏️ MANUAL INVITE")
            .addAdventureLore(player, messageService, "<gray>Type a player name to invite")
            .addAdventureLore(player, messageService, "<gray>Works for offline players too")

        if (inputMode) {
            manualItem.setAdventureName(player, messageService, "<yellow>⏳ WAITING FOR INPUT...")
                .addAdventureLore(player, messageService, "<gray>Type player name in chat")
                .addAdventureLore(player, messageService, "<gray>Type 'cancel' to stop")
        } else {
            manualItem.addAdventureLore(player, messageService, "<gray>Click to enter player name")
        }

        val manualGuiItem = GuiItem(manualItem) {
            if (!inputMode) {
                startChatInput()
            } else {
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Already waiting for input. Type player name or 'cancel'.")
            }
        }
        pane.addItem(manualGuiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>⬅️ BACK")
            .addAdventureLore(player, messageService, "<gray>Return to member management")

        val backGuiItem = GuiItem(backItem) {
            if (inputMode) {
                chatInputListener.stopInputMode(player)
                AdventureMenuHelper.sendMessage(player, messageService, "<gray>Invite input cancelled.")
            }
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }

    private fun invitePlayer(targetPlayer: Player) {
        // Use confirmation menu instead of directly inviting
        val menuFactory = MenuFactory()
        menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer))
    }

    private fun startChatInput() {
        inputMode = true
        chatInputListener.startInputMode(player, this)

        player.closeInventory()
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>=== MANUAL PLAYER INVITE ===")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type the name of the player to invite:")
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Type 'cancel' to stop input mode")
        AdventureMenuHelper.sendMessage(player, messageService, "<gold>============================")
    }

    // ChatInputHandler methods
    override fun onChatInput(player: Player, input: String) {
        inputMode = false

        if (input.equals("cancel", ignoreCase = true)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<gray>Invite cancelled.")
            open()
            return
        }

        // Find player by name
        val targetPlayer = Bukkit.getPlayer(input)
        if (targetPlayer == null) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ Player '$input' is not online!")
            open()
            return
        }

        if (targetPlayer == player) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ You cannot invite yourself!")
            open()
            return
        }

        invitePlayer(targetPlayer)
        open()
    }

    override fun onCancel(player: Player) {
        inputMode = false
        AdventureMenuHelper.sendMessage(player, messageService, "<gray>Invite input cancelled.")
        open()
    }}

