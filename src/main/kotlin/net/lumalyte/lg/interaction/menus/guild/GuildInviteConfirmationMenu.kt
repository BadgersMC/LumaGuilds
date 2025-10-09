package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.AntiDupeUtil
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildInviteConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                 private val guild: Guild, private val targetPlayer: Player, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Confirm Invite - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 3)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Info display
        addInfoDisplay(pane, 0, 0)

        // Player info
        addPlayerInfo(pane, 2, 0)

        // Action buttons
        addConfirmButton(pane, 4, 1)
        addCancelButton(pane, 6, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addInfoDisplay(pane: StaticPane, x: Int, y: Int) {
        val infoItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<green>📨 SEND INVITATION")
            .addAdventureLore(player, messageService, "<gray>Send an invitation to join")
            .addAdventureLore(player, messageService, "<gray>the guild.")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>The player will receive a message")
            .addAdventureLore(player, messageService, "<yellow>with instructions to accept.")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<gray>They can use:")
            .addAdventureLore(player, messageService, "<gray>/guild join ${guild.name}")

        pane.addItem(GuiItem(infoItem), x, y)
    }

    private fun addPlayerInfo(pane: StaticPane, x: Int, y: Int) {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        // Set skull owner
        meta.owningPlayer = targetPlayer
        head.itemMeta = meta

        val playerItem = head.setAdventureName(player, messageService, "<green>👤 ${targetPlayer.name}")
            .addAdventureLore(player, messageService, "<gray>Player: <white>${targetPlayer.name}")
            .addAdventureLore(player, messageService, "<gray>Status: <green>Online")
            .addAdventureLore(player, messageService, "<gray>")
            .addAdventureLore(player, messageService, "<yellow>Will receive invitation")

        pane.addItem(GuiItem(playerItem), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val confirmItem = ItemStack(Material.GREEN_WOOL)
            .setAdventureName(player, messageService, "<green>✅ SEND INVITE")
            .addAdventureLore(player, messageService, "<gray>Send invitation to player")
            .addAdventureLore(player, messageService, "<gray>Click to proceed")

        val confirmGuiItem = GuiItem(confirmItem) {
            sendInvite()
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.RED_WOOL)
            .setAdventureName(player, messageService, "<red>❌ CANCEL")
            .addAdventureLore(player, messageService, "<gray>Return to invite menu")
            .addAdventureLore(player, messageService, "<gray>No invitation will be sent")

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun sendInvite() {
        // Check if player is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            AdventureMenuHelper.sendMessage(player, messageService, "<red>❌ ${targetPlayer.name} is already in your guild!")
            menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
            return
        }

        // Send invitation message
        AdventureMenuHelper.sendMessage(player, messageService, "<green>✅ Invitation sent to ${targetPlayer.name}!")
        targetPlayer.sendMessage("<gold>📨 Guild Invitation")
        targetPlayer.sendMessage("<gray>${player.name} invited you to join <white>${guild.name}")
        targetPlayer.sendMessage("<gray>Type <green>/guild join ${guild.name} <gray>to accept")
        targetPlayer.sendMessage("<gray>Or <red>/guild decline ${guild.name} <gray>to decline")

        // TODO: Store invitation in database for later acceptance
        // For now, just show the message

        // Return to member management menu
        menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
    }}

