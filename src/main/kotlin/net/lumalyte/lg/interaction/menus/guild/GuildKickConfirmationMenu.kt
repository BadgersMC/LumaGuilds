package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildKickConfirmationMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                               private val guild: Guild, private val memberToKick: Member): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(3, "§6Confirm Kick - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Warning display
        addWarningDisplay(pane, 0, 0)

        // Member info
        addMemberInfo(pane, 2, 0)

        // Action buttons
        addConfirmButton(pane, 4, 1)
        addCancelButton(pane, 6, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addWarningDisplay(pane: StaticPane, x: Int, y: Int) {
        val warningItem = ItemStack(Material.BARRIER)
            .name("§c⚠ KICK CONFIRMATION")
            .lore("§cThis action cannot be undone!")
            .lore("§7")
            .lore("§7The player will be immediately")
            .lore("§7removed from the guild.")
            .lore("§7")
            .lore("§eThey will lose access to:")
            .lore("§e• Guild bank")
            .lore("§e• Guild claims")
            .lore("§e• Guild permissions")

        pane.addItem(GuiItem(warningItem), x, y)
    }

    private fun addMemberInfo(pane: StaticPane, x: Int, y: Int) {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        // Try to get player name from online players
        val playerName = Bukkit.getPlayer(memberToKick.playerId)?.name ?: "Unknown Player"

        // Set skull owner
        try {
            val skullMeta = meta as SkullMeta
            val onlinePlayer = Bukkit.getPlayer(memberToKick.playerId)
            if (onlinePlayer != null) {
                skullMeta.owningPlayer = onlinePlayer
            }
        } catch (e: Exception) {
            // Fallback if skull texture setting fails
        }

        head.itemMeta = meta

        val memberItem = head.name("§f👤 $playerName")
            .lore("§7Player: §f$playerName")
            .lore("§7Joined: §f${memberToKick.joinedAt}")
            .lore("§7")
            .lore("§cThis player will be kicked")

        pane.addItem(GuiItem(memberItem), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val confirmItem = ItemStack(Material.RED_WOOL)
            .name("§c✅ CONFIRM KICK")
            .lore("§cPermanently remove from guild")
            .lore("§7Click to proceed")

        val confirmGuiItem = GuiItem(confirmItem) {
            performKick()
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack(Material.GREEN_WOOL)
            .name("§a❌ CANCEL")
            .lore("§7Return to member list")
            .lore("§7No changes will be made")

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun performKick() {
        val targetPlayer = Bukkit.getPlayer(memberToKick.playerId)
        val targetName = targetPlayer?.name ?: "Unknown Player"

        // Perform the kick
        val success = memberService.removeMember(memberToKick.playerId, guild.id, player.uniqueId)

        if (success) {
            player.sendMessage("§a✅ Successfully kicked $targetName from ${guild.name}!")

            // Notify the kicked player if they're online
            if (targetPlayer != null) {
                targetPlayer.sendMessage("§c❌ You have been kicked from ${guild.name} by ${player.name}")
            }

            // Return to member management menu
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            player.sendMessage("§c❌ Failed to kick $targetName. Check permissions.")
            menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
        }
    }

    override fun passData(data: Any?) {
        // No data passing needed for confirmation menu
    }
}

