package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.GuildInvitationManager
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
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
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        // Create 3x9 chest GUI
        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.legacy("menu.guild_confirmation.invite.title", "guild" to guild.name),
        ))
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
        val infoItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.guild_confirmation.invite.item.info.name"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.info.lore.line_1"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.info.lore.line_2"))
            .lore("")
            .lore(lang.legacy("menu.guild_confirmation.invite.item.info.lore.instructions_line_1"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.info.lore.instructions_line_2"))
            .lore("")
            .lore(lang.legacy("menu.guild_confirmation.invite.item.info.lore.command_label"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.info.lore.command", "guild" to guild.name))

        pane.addItem(GuiItem(infoItem), x, y)
    }

    private fun addPlayerInfo(pane: StaticPane, x: Int, y: Int) {
        val head = ItemStack.of(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        // Set skull owner
        meta.owningPlayer = targetPlayer
        head.itemMeta = meta

        val playerItem = head.name(lang.legacy("menu.guild_confirmation.invite.item.player.name", "player" to targetPlayer.name))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.player.lore.player", "player" to targetPlayer.name))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.player.lore.status"))
            .lore("")
            .lore(lang.legacy("menu.guild_confirmation.invite.item.player.lore.result"))

        pane.addItem(GuiItem(playerItem), x, y)
    }

    private fun addConfirmButton(pane: StaticPane, x: Int, y: Int) {
        val confirmItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.legacy("menu.guild_confirmation.invite.item.confirm.name"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.confirm.lore.line_1"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.confirm.lore.line_2"))

        val confirmGuiItem = GuiItem(confirmItem) {
            sendInvite()
        }
        pane.addItem(confirmGuiItem, x, y)
    }

    private fun addCancelButton(pane: StaticPane, x: Int, y: Int) {
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.legacy("menu.guild_confirmation.invite.item.cancel.name"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.cancel.lore.line_1"))
            .lore(lang.legacy("menu.guild_confirmation.invite.item.cancel.lore.line_2"))

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.goBack()
        }
        pane.addItem(cancelGuiItem, x, y)
    }

    private fun sendInvite() {
        // Check if player is already in a guild
        if (memberService.isPlayerInGuild(targetPlayer.uniqueId, guild.id)) {
            player.sendMessage(lang.msg("menu.guild_confirmation.invite.feedback.already_member", "player" to targetPlayer.name))
            menuNavigator.goBack()
            return
        }

        // Store the invitation
        GuildInvitationManager.addInvite(
            guildId = guild.id,
            guildName = guild.name,
            invitedPlayerId = targetPlayer.uniqueId,
            inviterPlayerId = player.uniqueId,
            inviterName = player.name
        )

        // Send invitation message
        player.sendMessage(lang.msg("menu.guild_confirmation.invite.feedback.sent", "player" to targetPlayer.name))
        player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f)

        targetPlayer.sendMessage("")
        targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.invite.feedback.received_header"))
        targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.invite.feedback.received", "player" to player.name, "guild" to guild.name))
        targetPlayer.sendMessage("")
        targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.invite.feedback.accept", "guild" to guild.name))
        targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.invite.feedback.decline", "guild" to guild.name))
        targetPlayer.sendMessage("")
        targetPlayer.playSound(targetPlayer.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f)

        // Return to member management menu
        menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
    }

    override fun passData(data: Any?) {
        // No data passing needed for confirmation menu
    }
}

