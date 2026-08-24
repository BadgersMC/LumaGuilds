package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

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
import net.lumalyte.lg.utils.findPlayerByName
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
    private val lang: LangService by inject()

    private var inputMode = false

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.legacy("menu.guild_invite.title", "guild" to guild.name)))
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
            .name(lang.legacy("menu.guild_invite.info.name"))
            .lore(lang.legacy("menu.guild_invite.info.description"))
            .lore(lang.legacy("menu.guild_invite.info.instructions"))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_invite.info.warning"))

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
                .name(lang.legacy("menu.guild_invite.online.empty.name"))
                .lore(lang.legacy("menu.guild_invite.online.empty.description"))
                .lore(lang.legacy("menu.guild_invite.online.empty.alternative"))

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

            playerHead.name(lang.legacy("menu.guild_invite.online.player.name", "player" to onlinePlayer.name))
                .lore(lang.legacy("menu.guild_invite.online.player.action"))
                .lore(lang.legacy("menu.guild_invite.online.player.result"))

            val playerGuiItem = GuiItem(playerHead) {
                // menuFactory already injected
                menuNavigator.openMenu(menuFactory.createGuildInviteConfirmationMenu(menuNavigator, player, guild, onlinePlayer))
            }
            pane.addItem(playerGuiItem, x + index, y)
        }
    }

    private fun addManualInviteButton(pane: StaticPane, x: Int, y: Int) {
        val manualItem = ItemStack.of(Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.guild_invite.manual.name"))
            .lore(lang.legacy("menu.guild_invite.manual.description"))
            .lore(lang.legacy("menu.guild_invite.manual.offline"))

        if (inputMode) {
            manualItem.name(lang.legacy("menu.guild_invite.manual.waiting.name"))
                .lore(lang.legacy("menu.guild_invite.manual.waiting.instructions"))
                .lore(lang.legacy("menu.guild_invite.manual.waiting.cancel"))
        } else {
            manualItem.lore(lang.legacy("menu.guild_invite.manual.action"))
        }

        val manualGuiItem = GuiItem(manualItem) {
            if (!inputMode) {
                startChatInput()
            } else {
                player.sendMessage(lang.msg("menu.guild_invite.feedback.already_waiting"))
            }
        }
        pane.addItem(manualGuiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.guild_invite.back.name"))
            .lore(lang.legacy("menu.guild_invite.back.description"))

        val backGuiItem = GuiItem(backItem) {
            if (inputMode) {
                chatInputListener.stopInputMode(player)
                player.sendMessage(lang.msg("menu.guild_invite.feedback.input_cancelled"))
            }
            menuNavigator.goBack()
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
        player.sendMessage(lang.msg("menu.guild_invite.chat.header"))
        player.sendMessage(lang.msg("menu.guild_invite.chat.instructions"))
        player.sendMessage(lang.msg("menu.guild_invite.chat.cancel"))
        player.sendMessage(lang.msg("menu.guild_invite.chat.footer"))
    }

    // ChatInputHandler methods
    override fun onChatInput(player: Player, input: String) {
        inputMode = false

        if (input.equals("cancel", ignoreCase = true)) {
            player.sendMessage(lang.msg("menu.guild_invite.feedback.cancelled"))
            open()
            return
        }

        // Find player by name — uses Floodgate-aware lookup so Bedrock names work without the dot prefix
        val targetPlayer = findPlayerByName(input)
        if (targetPlayer == null) {
            player.sendMessage(lang.msg("menu.guild_invite.feedback.not_online", "player" to input))
            open()
            return
        }

        if (targetPlayer == player) {
            player.sendMessage(lang.msg("menu.guild_invite.feedback.self_invite"))
            open()
            return
        }

        invitePlayer(targetPlayer)
        // Don't call open() here - invitePlayer already opens the confirmation menu
    }

    override fun onCancel(player: Player) {
        inputMode = false
        player.sendMessage(lang.msg("menu.guild_invite.feedback.input_cancelled"))
        open()
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

