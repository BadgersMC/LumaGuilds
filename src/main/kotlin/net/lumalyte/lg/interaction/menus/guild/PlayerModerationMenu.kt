package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Menu for moderating a specific player in a party/channel.
 * Provides mute, ban, kick, unmute, and unban options.
 */
class PlayerModerationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var party: Party,
    private val targetPlayerId: UUID
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val partyService: PartyService by inject()
    private val lang: LangService by inject()

    override fun open() {
        // Check permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage(lang.msg("menu.player_moderation.feedback.no_permission"))
            return
        }

        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown_player")
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.legacy("menu.player_moderation.title", "player" to targetName)))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 0: Player info
        addPlayerInfo(pane)

        // Row 1: Moderation actions
        addModerationActions(pane)

        // Row 2: Back button
        addBackButton(pane)

        gui.show(player)
    }

    private fun addPlayerInfo(pane: StaticPane) {
        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(targetPlayerId).build()
        )

        val meta = head.itemMeta as SkullMeta
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown_player")
        head.itemMeta = meta

        val isMuted = party.isPlayerMuted(targetPlayerId)
        val isBanned = party.isPlayerBanned(targetPlayerId)

        val playerItem = head.name(lang.legacy("menu.player_moderation.player.name", "player" to targetName))
            .lore(lang.legacy("menu.player_moderation.player.description"))
            .lore(lang.legacy("menu.common.blank"))
            .apply {
                if (isBanned) {
                    lore(lang.legacy("menu.player_moderation.player.status.banned"))
                } else if (isMuted) {
                    val expiration = party.mutedPlayers[targetPlayerId]
                    if (expiration != null) {
                        val remaining = Duration.between(Instant.now(), expiration)
                        lore(lang.legacy("menu.player_moderation.player.status.muted"))
                        lore(lang.legacy("menu.player_moderation.player.expires", "hours" to remaining.toHours(), "minutes" to remaining.toMinutes() % 60))
                    } else {
                        lore(lang.legacy("menu.player_moderation.player.status.permanently_muted"))
                    }
                } else {
                    lore(lang.legacy("menu.player_moderation.player.status.normal"))
                }
            }

        pane.addItem(GuiItem(playerItem), 4, 0)
    }

    private fun addModerationActions(pane: StaticPane) {
        val isMuted = party.isPlayerMuted(targetPlayerId)
        val isBanned = party.isPlayerBanned(targetPlayerId)
        // Mute buttons (1h, 1d, 1w, permanent)
        if (!isMuted && !isBanned) {
            // 1 Hour mute
            val mute1hItem = ItemStack.of(Material.CLOCK)
                .name(lang.legacy("menu.player_moderation.actions.mute_hour.name"))
                .lore(lang.legacy("menu.player_moderation.actions.mute_hour.description"))
                .lore(lang.legacy("menu.player_moderation.actions.mute.restriction"))
            pane.addItem(GuiItem(mute1hItem) {
                performMute(Duration.ofHours(1), lang.raw("menu.player_moderation.duration.hour"))
            }, 0, 1)

            // 1 Day mute
            val mute1dItem = ItemStack.of(Material.CLOCK)
                .name(lang.legacy("menu.player_moderation.actions.mute_day.name"))
                .lore(lang.legacy("menu.player_moderation.actions.mute_day.description"))
                .lore(lang.legacy("menu.player_moderation.actions.mute.restriction"))
            pane.addItem(GuiItem(mute1dItem) {
                performMute(Duration.ofDays(1), lang.raw("menu.player_moderation.duration.day"))
            }, 1, 1)

            // 1 Week mute
            val mute1wItem = ItemStack.of(Material.CLOCK)
                .name(lang.legacy("menu.player_moderation.actions.mute_week.name"))
                .lore(lang.legacy("menu.player_moderation.actions.mute_week.description"))
                .lore(lang.legacy("menu.player_moderation.actions.mute.restriction"))
            pane.addItem(GuiItem(mute1wItem) {
                performMute(Duration.ofDays(7), lang.raw("menu.player_moderation.duration.week"))
            }, 2, 1)

            // Permanent mute
            val mutePermItem = ItemStack.of(Material.BELL)
                .name(lang.legacy("menu.player_moderation.actions.mute_permanent.name"))
                .lore(lang.legacy("menu.player_moderation.actions.mute_permanent.description"))
                .lore(lang.legacy("menu.player_moderation.actions.mute.restriction"))
                .lore(lang.legacy("menu.player_moderation.actions.mute_permanent.warning"))
            pane.addItem(GuiItem(mutePermItem) {
                performMute(null, lang.raw("menu.player_moderation.duration.permanent"))
            }, 3, 1)
        }

        // Unmute button (only if muted)
        if (isMuted && !isBanned) {
            val unmuteItem = ItemStack.of(Material.LIME_DYE)
                .name(lang.legacy("menu.player_moderation.actions.unmute.name"))
                .lore(lang.legacy("menu.player_moderation.actions.unmute.description"))
                .lore(lang.legacy("menu.player_moderation.actions.unmute.result"))
            pane.addItem(GuiItem(unmuteItem) {
                performUnmute()
            }, 1, 1)
        }

        // Ban button (only if not banned)
        if (!isBanned) {
            val banItem = ItemStack.of(Material.BARRIER)
                .name(lang.legacy("menu.player_moderation.actions.ban.name"))
                .lore(lang.legacy("menu.player_moderation.actions.ban.description"))
                .lore(lang.legacy("menu.player_moderation.actions.ban.restriction"))
                .lore(lang.legacy("menu.player_moderation.actions.ban.warning"))
            pane.addItem(GuiItem(banItem) {
                performBan()
            }, 5, 1)
        }

        // Unban button (only if banned)
        if (isBanned) {
            val unbanItem = ItemStack.of(Material.LIME_DYE)
                .name(lang.legacy("menu.player_moderation.actions.unban.name"))
                .lore(lang.legacy("menu.player_moderation.actions.unban.description"))
                .lore(lang.legacy("menu.player_moderation.actions.unban.result"))
            pane.addItem(GuiItem(unbanItem) {
                performUnban()
            }, 5, 1)
        }

        // Kick button (only if not banned)
        if (!isBanned) {
            val kickItem = ItemStack.of(Material.IRON_BOOTS)
                .name(lang.legacy("menu.player_moderation.actions.kick.name"))
                .lore(lang.legacy("menu.player_moderation.actions.kick.description"))
                .lore(lang.legacy("menu.player_moderation.actions.kick.result"))
                .lore(lang.legacy("menu.player_moderation.actions.kick.warning"))
            pane.addItem(GuiItem(kickItem) {
                performKick()
            }, 7, 1)
        }
    }

    private fun performMute(duration: Duration?, durationText: String) {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown")
        val result = partyService.mutePlayer(party.id, targetPlayerId, player.uniqueId, duration)

        if (result != null) {
            party = result
            player.sendMessage(lang.msg("menu.player_moderation.feedback.muted", "player" to targetName, "duration" to durationText))

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: lang.raw("menu.player_moderation.fallback.channel")
                if (duration != null) {
                    targetPlayer.sendMessage(lang.msg("menu.player_moderation.notification.muted", "channel" to channelName, "duration" to durationText))
                } else {
                    targetPlayer.sendMessage(lang.msg("menu.player_moderation.notification.permanently_muted", "channel" to channelName))
                }
            }

            open() // Refresh menu
        } else {
            player.sendMessage(lang.msg("menu.player_moderation.feedback.mute_failed", "player" to targetName))
        }
    }

    private fun performUnmute() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown")
        val result = partyService.unmutePlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage(lang.msg("menu.player_moderation.feedback.unmuted", "player" to targetName))

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: lang.raw("menu.player_moderation.fallback.channel")
                targetPlayer.sendMessage(lang.msg("menu.player_moderation.notification.unmuted", "channel" to channelName))
            }

            open() // Refresh menu
        } else {
            player.sendMessage(lang.msg("menu.player_moderation.feedback.unmute_failed", "player" to targetName))
        }
    }

    private fun performBan() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown")
        val result = partyService.banPlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage(lang.msg("menu.player_moderation.feedback.banned", "player" to targetName))

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: lang.raw("menu.player_moderation.fallback.channel")
                targetPlayer.sendMessage(lang.msg("menu.player_moderation.notification.banned", "channel" to channelName))
            }

            open() // Refresh menu
        } else {
            player.sendMessage(lang.msg("menu.player_moderation.feedback.ban_failed", "player" to targetName))
        }
    }

    private fun performUnban() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown")
        val result = partyService.unbanPlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage(lang.msg("menu.player_moderation.feedback.unbanned", "player" to targetName))

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: lang.raw("menu.player_moderation.fallback.channel")
                targetPlayer.sendMessage(lang.msg("menu.player_moderation.notification.unbanned", "channel" to channelName))
            }

            open() // Refresh menu
        } else {
            player.sendMessage(lang.msg("menu.player_moderation.feedback.unban_failed", "player" to targetName))
        }
    }

    private fun performKick() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: lang.raw("menu.player_moderation.fallback.unknown")
        val result = partyService.kickPlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage(lang.msg("menu.player_moderation.feedback.kicked", "player" to targetName))
            // Note: kickPlayer already sends notification to target

            open() // Refresh menu
        } else {
            player.sendMessage(lang.msg("menu.player_moderation.feedback.kick_failed", "player" to targetName))
        }
    }

    private fun addBackButton(pane: StaticPane) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.player_moderation.back.name"))
            .lore(lang.legacy("menu.player_moderation.back.description"))

        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(PartyModerationMenu(menuNavigator, player, guild, party))
        }, 4, 2)
    }

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Party -> party = data
        }
    }
}
