package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
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

class GuildInviteConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                 private val guild: Guild, private val targetPlayer: Player): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(3, "§6Confirm Invite - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

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
            .name("§a📨 SEND INVITATION")
            .lore("§7Send an invitation to join")
            .lore("§7the guild.")
            .lore("§7")
            .lore("§eThe player will receive a message")
            .lore("§ewith instructions to accept.")
            .lore("§7")
            .lore("§7They can use:")
            .lore("§7/guild join ${guild.name}")

        pane.addItem(GuiItem(infoItem), x, y)
    }

    private fun addPlayerInfo(pane: StaticPane, x: Int, y: Int) {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        // Set skull owner
        meta.owningPlayer = targetPlayer
        head.itemMeta = meta

        val playerItem = head.name("§a👤 ${targetPlayer.name}")
            .lore("§7Player: §f${targetPlayer.name}")
            .lore("§7Status: §aOnline")
            .lore("§7")
            .lore("§eWill receive invitation")

        pane.addItem(GuiItem(playerItem), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val confirmItem = ItemStack(Material.GREEN_WOOL)
            .name("§a✅ SEND INVITE")
            .lore("§7Send invitation to player")
            .lore("§7Click to proceed")

        val confirmGuiItem = GuiItem(confirmItem) {
            sendInvite()
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.RED_WOOL)
            .name("§c❌ CANCEL")
            .lore("§7Return to invite menu")
            .lore("§7No invitation will be sent")

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.openMenu(GuildInviteMenu(menuNavigator, player, guild))
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun sendInvite() {
        // Check if player is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage("§c❌ ${targetPlayer.name} is already in your guild!")
            menuNavigator.openMenu(GuildInviteMenu(menuNavigator, player, guild))
            return
        }

        // Send invitation message
        player.sendMessage("§a✅ Invitation sent to ${targetPlayer.name}!")
        targetPlayer.sendMessage("§6📨 Guild Invitation")
        targetPlayer.sendMessage("§7${player.name} invited you to join §f${guild.name}")
        targetPlayer.sendMessage("§7Type §a/guild join ${guild.name} §7to accept")
        targetPlayer.sendMessage("§7Or §c/guild decline ${guild.name} §7to decline")

        // TODO: Store invitation in database for later acceptance
        // For now, just show the message

        // Return to member management menu
        menuNavigator.openMenu(GuildMemberManagementMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        // No data passing needed for confirmation menu
    }
}
