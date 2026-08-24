package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
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
    private val lang: LangService by inject()

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.guiTitle("menu.guild_confirmation.kick.title", "guild" to guild.name),
        ))
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
        val warningItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.guild_confirmation.kick.item.warning.name"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.irreversible"))
            .lore("")
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.removed_line_1"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.removed_line_2"))
            .lore("")
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.loss_header"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.bank"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.claims"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.warning.lore.permissions"))

        pane.addItem(GuiItem(warningItem), x, y)
    }

    private fun addMemberInfo(pane: StaticPane, x: Int, y: Int) {
        val head = ItemStack.of(Material.PLAYER_HEAD)

        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(memberToKick.playerId).build())

        val meta = head.itemMeta as SkullMeta

        // Try to get player name from all players
        val playerName = Bukkit.getOfflinePlayer(memberToKick.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")

        head.itemMeta = meta

        val memberItem = head.name(lang.gui("menu.guild_confirmation.kick.item.player.name", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.kick.item.player.lore.player", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.kick.item.player.lore.joined", "joined" to memberToKick.joinedAt))
            .lore("")
            .lore(lang.gui("menu.guild_confirmation.kick.item.player.lore.result"))

        pane.addItem(GuiItem(memberItem), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val confirmItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.gui("menu.guild_confirmation.kick.item.confirm.name"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.confirm.lore.line_1"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.confirm.lore.line_2"))

        val confirmGuiItem = GuiItem(confirmItem) {
            performKick()
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.gui("menu.guild_confirmation.kick.item.cancel.name"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.cancel.lore.line_1"))
            .lore(lang.gui("menu.guild_confirmation.kick.item.cancel.lore.line_2"))

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun performKick() {
        val targetPlayer = Bukkit.getPlayer(memberToKick.playerId)
        val targetName = Bukkit.getOfflinePlayer(memberToKick.playerId)?.name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")

        // Perform the kick
        val success = memberService.removeMember(memberToKick.playerId, guild.id, player.uniqueId)

        if (success) {
            player.sendMessage(lang.msg("menu.guild_confirmation.kick.feedback.success", "player" to targetName, "guild" to guild.name))

            // Notify the kicked player if they're online
            if (targetPlayer != null) {
                targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.kick.feedback.target", "guild" to guild.name, "player" to player.name))
            }

            // Return to member management menu
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        } else {
            player.sendMessage(lang.msg("menu.guild_confirmation.kick.feedback.failure", "player" to targetName))
            menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
        }
    }

    override fun passData(data: Any?) {
        // No data passing needed for confirmation menu
    }
}

