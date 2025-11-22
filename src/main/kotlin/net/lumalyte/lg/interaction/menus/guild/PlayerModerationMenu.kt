package net.lumalyte.lg.interaction.menus.guild

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

    override fun open() {
        // Check permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage("§c You don't have permission to moderate players!")
            return
        }

        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown Player"
        val gui = ChestGui(3, "§6Moderate: $targetName")
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
        val head = ItemStack(Material.PLAYER_HEAD)
        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(targetPlayerId).build()
        )

        val meta = head.itemMeta as SkullMeta
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown Player"
        head.itemMeta = meta

        val isMuted = party.isPlayerMuted(targetPlayerId)
        val isBanned = party.isPlayerBanned(targetPlayerId)

        val playerItem = head.name("§6$targetName")
            .lore("§7Target player for moderation")
            .lore("")
            .apply {
                if (isBanned) {
                    lore("§c Status: BANNED")
                } else if (isMuted) {
                    val expiration = party.mutedPlayers[targetPlayerId]
                    if (expiration != null) {
                        val remaining = Duration.between(Instant.now(), expiration)
                        lore("§e Status: MUTED")
                        lore("§7Expires: §f${remaining.toHours()}h ${remaining.toMinutes() % 60}m")
                    } else {
                        lore("§e Status: PERMANENTLY MUTED")
                    }
                } else {
                    lore("§a Status: Normal")
                }
            }

        pane.addItem(GuiItem(playerItem), 4, 0)
    }

    private fun addModerationActions(pane: StaticPane) {
        val isMuted = party.isPlayerMuted(targetPlayerId)
        val isBanned = party.isPlayerBanned(targetPlayerId)
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"

        // Mute buttons (1h, 1d, 1w, permanent)
        if (!isMuted && !isBanned) {
            // 1 Hour mute
            val mute1hItem = ItemStack(Material.CLOCK)
                .name("§e Mute 1 Hour")
                .lore("§7Mute player for 1 hour")
                .lore("§7Player cannot send messages")
            pane.addItem(GuiItem(mute1hItem) {
                performMute(Duration.ofHours(1), "1 hour")
            }, 0, 1)

            // 1 Day mute
            val mute1dItem = ItemStack(Material.CLOCK)
                .name("§e Mute 1 Day")
                .lore("§7Mute player for 1 day")
                .lore("§7Player cannot send messages")
            pane.addItem(GuiItem(mute1dItem) {
                performMute(Duration.ofDays(1), "1 day")
            }, 1, 1)

            // 1 Week mute
            val mute1wItem = ItemStack(Material.CLOCK)
                .name("§e Mute 1 Week")
                .lore("§7Mute player for 1 week")
                .lore("§7Player cannot send messages")
            pane.addItem(GuiItem(mute1wItem) {
                performMute(Duration.ofDays(7), "1 week")
            }, 2, 1)

            // Permanent mute
            val mutePermItem = ItemStack(Material.BELL)
                .name("§c Permanent Mute")
                .lore("§7Mute player permanently")
                .lore("§7Player cannot send messages")
                .lore("§c Must be manually unmuted")
            pane.addItem(GuiItem(mutePermItem) {
                performMute(null, "permanent")
            }, 3, 1)
        }

        // Unmute button (only if muted)
        if (isMuted && !isBanned) {
            val unmuteItem = ItemStack(Material.LIME_DYE)
                .name("§a Unmute Player")
                .lore("§7Remove mute from player")
                .lore("§7Player can send messages again")
            pane.addItem(GuiItem(unmuteItem) {
                performUnmute()
            }, 1, 1)
        }

        // Ban button (only if not banned)
        if (!isBanned) {
            val banItem = ItemStack(Material.BARRIER)
                .name("§c Ban Player")
                .lore("§7Ban player from this channel")
                .lore("§7Player cannot see or access channel")
                .lore("§c This is a permanent action!")
            pane.addItem(GuiItem(banItem) {
                performBan()
            }, 5, 1)
        }

        // Unban button (only if banned)
        if (isBanned) {
            val unbanItem = ItemStack(Material.LIME_DYE)
                .name("§a Unban Player")
                .lore("§7Remove ban from player")
                .lore("§7Player can access channel again")
            pane.addItem(GuiItem(unbanItem) {
                performUnban()
            }, 5, 1)
        }

        // Kick button (only if not banned)
        if (!isBanned) {
            val kickItem = ItemStack(Material.IRON_BOOTS)
                .name("§c Kick Player")
                .lore("§7Kick and ban player from channel")
                .lore("§7Player is removed and banned")
                .lore("§c This bans the player!")
            pane.addItem(GuiItem(kickItem) {
                performKick()
            }, 7, 1)
        }
    }

    private fun performMute(duration: Duration?, durationText: String) {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"
        val result = partyService.mutePlayer(party.id, targetPlayerId, player.uniqueId, duration)

        if (result != null) {
            party = result
            player.sendMessage("§a Muted $targetName for $durationText")

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: "a channel"
                if (duration != null) {
                    targetPlayer.sendMessage("§c You have been muted in $channelName for $durationText")
                } else {
                    targetPlayer.sendMessage("§c You have been permanently muted in $channelName")
                }
            }

            open() // Refresh menu
        } else {
            player.sendMessage("§c Failed to mute $targetName")
        }
    }

    private fun performUnmute() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"
        val result = partyService.unmutePlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage("§a Unmuted $targetName")

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: "a channel"
                targetPlayer.sendMessage("§a You have been unmuted in $channelName")
            }

            open() // Refresh menu
        } else {
            player.sendMessage("§c Failed to unmute $targetName")
        }
    }

    private fun performBan() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"
        val result = partyService.banPlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage("§a Banned $targetName from channel")

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: "a channel"
                targetPlayer.sendMessage("§c You have been banned from $channelName")
            }

            open() // Refresh menu
        } else {
            player.sendMessage("§c Failed to ban $targetName")
        }
    }

    private fun performUnban() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"
        val result = partyService.unbanPlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage("§a Unbanned $targetName from channel")

            // Notify target player
            val targetPlayer = Bukkit.getPlayer(targetPlayerId)
            if (targetPlayer != null && targetPlayer.isOnline) {
                val channelName = party.name ?: "a channel"
                targetPlayer.sendMessage("§a You have been unbanned from $channelName")
            }

            open() // Refresh menu
        } else {
            player.sendMessage("§c Failed to unban $targetName")
        }
    }

    private fun performKick() {
        val targetName = Bukkit.getOfflinePlayer(targetPlayerId).name ?: "Unknown"
        val result = partyService.kickPlayer(party.id, targetPlayerId, player.uniqueId)

        if (result != null) {
            party = result
            player.sendMessage("§a Kicked $targetName from channel")
            // Note: kickPlayer already sends notification to target

            open() // Refresh menu
        } else {
            player.sendMessage("§c Failed to kick $targetName")
        }
    }

    private fun addBackButton(pane: StaticPane) {
        val backItem = ItemStack(Material.ARROW)
            .name("§c Back")
            .lore("§7Return to channel moderation")

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
