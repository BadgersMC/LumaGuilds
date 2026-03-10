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
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildInviteMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                     private var guild: Guild): Menu, KoinComponent, ChatInputHandler {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val chatInputListener: ChatInputListener by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    private var inputMode = false

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(3, "§6Invite Player - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

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
        val infoItem = ItemStack.of(Material.BOOK)
            .name("§f📋 INVITE PLAYERS")
            .lore("§7Invite players to join your guild")
            .lore("§7Click online players or use manual invite")
            .lore("§7")
            .lore("§ePlayers must accept the invitation")

        pane.addItem(GuiItem(infoItem), x, y)
    }

    private fun addOnlinePlayersList(pane: StaticPane, x: Int, y: Int) {
        // Get online players (excluding current guild members)
        val onlinePlayers = Bukkit.getOnlinePlayers()
            .filter { it != player }
            .filter { !memberService.isPlayerInGuild(it.uniqueId, guild.id) }
            .take(5) // Limit to 5 for display

        if (onlinePlayers.isEmpty()) {
            val noPlayersItem = ItemStack.of(Material.BARRIER)
                .name("§c❌ NO PLAYERS AVAILABLE")
                .lore("§7No online players to invite")
                .lore("§7Use manual invite instead")

            pane.addItem(GuiItem(noPlayersItem), x, y)
            return
        }

        // Display up to 5 online players
        for ((index, onlinePlayer) in onlinePlayers.withIndex()) {
            if (index >= 5) break

            val playerHead = ItemStack.of(Material.PLAYER_HEAD)
            val meta = playerHead.itemMeta
            if (meta is org.bukkit.inventory.meta.SkullMeta) {
                meta.owningPlayer = onlinePlayer
                playerHead.itemMeta = meta
            }

            playerHead.name("§a👤 ${onlinePlayer.name}")
                .lore("§7Click to invite this player")
                .lore("§7They will receive an invitation")

            val playerGuiItem = GuiItem(playerHead) {
                // menuFactory already injected
                menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, onlinePlayer))
            }
            pane.addItem(playerGuiItem, x + index, y)
        }
    }

    private fun addManualInviteButton(pane: StaticPane, x: Int, y: Int) {
        val manualItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name("§f✏ MANUAL INVITE")
            .lore("§7Type a player name to invite")
            .lore("§7Works for offline players too")

        if (inputMode) {
            manualItem.name("§e⏳ WAITING FOR INPUT...")
                .lore("§7Type player name in chat")
                .lore("§7Type 'cancel' to stop")
        } else {
            manualItem.lore("§7Click to enter player name")
        }

        val manualGuiItem = GuiItem(manualItem) {
            if (!inputMode) {
                startChatInput()
            } else {
                player.sendMessage("§7Already waiting for input. Type player name or 'cancel'.")
            }
        }
        pane.addItem(manualGuiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name("§c⬅ BACK")
            .lore("§7Return to member management")

        val backGuiItem = GuiItem(backItem) {
            if (inputMode) {
                chatInputListener.stopInputMode(player)
                player.sendMessage("§7Invite input cancelled.")
            }
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }

    private fun invitePlayer(targetPlayer: Player) {
        // Use confirmation menu instead of directly inviting
        // menuFactory already injected
        menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, targetPlayer))
    }

    private fun startChatInput() {
        inputMode = true
        chatInputListener.startInputMode(player, this)

        player.closeInventory()
        player.sendMessage("§6=== MANUAL PLAYER INVITE ===")
        player.sendMessage("§7Type the name of the player to invite:")
        player.sendMessage("§7Type 'cancel' to stop input mode")
        player.sendMessage("§6============================")
    }

    // ChatInputHandler methods
    override fun onChatInput(player: Player, input: String) {
        inputMode = false

        if (input.equals("cancel", ignoreCase = true)) {
            player.sendMessage("§7Invite cancelled.")
            open()
            return
        }

        // Find player by name
        val targetPlayer = Bukkit.getPlayer(input)
        if (targetPlayer == null) {
            player.sendMessage("§c❌ Player '$input' is not online!")
            open()
            return
        }

        if (targetPlayer == player) {
            player.sendMessage("§c❌ You cannot invite yourself!")
            open()
            return
        }

        invitePlayer(targetPlayer)
        // Don't call open() here - invitePlayer already opens the confirmation menu
    }

    override fun onCancel(player: Player) {
        inputMode = false
        player.sendMessage("§7Invite input cancelled.")
        open()
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

