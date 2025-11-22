package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
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
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * Menu for moderating a party/channel - displaying online members and moderation status.
 */
class PartyModerationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private var party: Party
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val partyService: PartyService by inject()
    private val menuFactory: MenuFactory by inject()

    private var currentPage = 0
    private val itemsPerPage = 36 // 9x4 grid for players

    override fun open() {
        // Check permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage("§c You don't have permission to moderate this channel!")
            return
        }

        val gui = ChestGui(6, "§6Moderate: ${party.name ?: "Channel"}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 0-3: Online party members with status indicators
        addPlayerList(pane)

        // Row 4: Moderation status summary
        addModerationStatus(pane)

        // Row 5: Navigation
        addNavigationButtons(pane)

        gui.show(player)
    }

    private fun addPlayerList(pane: StaticPane) {
        // Get all online members from guilds in this party
        val onlineMembers = getOnlinePartyMembers()
            .filter { it != player.uniqueId } // Can't moderate yourself
            .sortedBy { Bukkit.getOfflinePlayer(it).name ?: "zzz" }

        // Calculate pagination
        val totalPages = maxOf(1, (onlineMembers.size + itemsPerPage - 1) / itemsPerPage)
        if (currentPage >= totalPages) {
            currentPage = maxOf(0, totalPages - 1)
        }

        // Get members for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, onlineMembers.size)
        val pageMembers = if (onlineMembers.isNotEmpty()) onlineMembers.subList(startIndex, endIndex) else emptyList()

        if (pageMembers.isEmpty()) {
            val noPlayersItem = ItemStack(Material.BARRIER)
                .name("§cNo Players Online")
                .lore("§7No other players are online in this channel")
            pane.addItem(GuiItem(noPlayersItem), 4, 1)
            return
        }

        // Add player items to rows 0-3
        for ((index, memberId) in pageMembers.withIndex()) {
            val x = index % 9
            val y = index / 9
            val memberItem = createPlayerItem(memberId)
            val guiItem = GuiItem(memberItem) {
                openPlayerModerationMenu(memberId)
            }
            pane.addItem(guiItem, x, y)
        }
    }

    private fun createPlayerItem(playerId: UUID): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)

        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(playerId).build()
        )

        val meta = head.itemMeta as SkullMeta
        val playerName = Bukkit.getOfflinePlayer(playerId).name ?: "Unknown Player"
        head.itemMeta = meta

        // Check moderation status
        val isMuted = party.isPlayerMuted(playerId)
        val isBanned = party.isPlayerBanned(playerId)

        val statusColor = when {
            isBanned -> "§c"
            isMuted -> "§e"
            else -> "§a"
        }

        val statusIcon = when {
            isBanned -> "§c[BANNED]"
            isMuted -> "§e[MUTED]"
            else -> "§a[OK]"
        }

        return head.name("$statusColor$playerName $statusIcon")
            .lore("§7Player: §f$playerName")
            .lore("")
            .apply {
                if (isBanned) {
                    lore("§c Status: BANNED")
                    lore("§7Cannot access this channel")
                } else if (isMuted) {
                    val expiration = party.mutedPlayers[playerId]
                    if (expiration != null) {
                        val remaining = java.time.Duration.between(java.time.Instant.now(), expiration)
                        lore("§e Status: MUTED")
                        lore("§7Expires in: §f${remaining.toHours()}h ${remaining.toMinutes() % 60}m")
                    } else {
                        lore("§e Status: PERMANENTLY MUTED")
                    }
                } else {
                    lore("§a Status: Normal")
                }
            }
            .lore("")
            .lore("§eClick to moderate this player")
    }

    private fun addModerationStatus(pane: StaticPane) {
        // Muted players count
        val activeMutes = party.getActiveMutes()
        val mutedItem = ItemStack(Material.BELL)
            .name("§e Muted Players: ${activeMutes.size}")
            .lore("§7Players currently muted in this channel")
            .apply {
                activeMutes.entries.take(5).forEach { (playerId, expiration) ->
                    val name = Bukkit.getOfflinePlayer(playerId).name ?: "Unknown"
                    val expText = expiration?.let { "until ${it}" } ?: "permanent"
                    lore("§7- §f$name §7($expText)")
                }
                if (activeMutes.size > 5) {
                    lore("§7... and ${activeMutes.size - 5} more")
                }
            }
        pane.addItem(GuiItem(mutedItem), 2, 4)

        // Banned players count
        val bannedItem = ItemStack(Material.BARRIER)
            .name("§c Banned Players: ${party.bannedPlayers.size}")
            .lore("§7Players banned from this channel")
            .apply {
                party.bannedPlayers.take(5).forEach { playerId ->
                    val name = Bukkit.getOfflinePlayer(playerId).name ?: "Unknown"
                    lore("§7- §f$name")
                }
                if (party.bannedPlayers.size > 5) {
                    lore("§7... and ${party.bannedPlayers.size - 5} more")
                }
            }
        pane.addItem(GuiItem(bannedItem), 6, 4)

        // Channel info
        val infoItem = ItemStack(Material.BOOK)
            .name("§b Channel Info")
            .lore("§7Name: §f${party.name ?: "Unnamed"}")
            .lore("§7Guilds: §f${party.guildIds.size}")
            .lore("§7Restrictions: §f${if (party.hasRoleRestrictions()) "Role-restricted" else "Open"}")
        pane.addItem(GuiItem(infoItem), 4, 4)
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val onlineMembers = getOnlinePartyMembers().filter { it != player.uniqueId }
        val totalPages = maxOf(1, (onlineMembers.size + itemsPerPage - 1) / itemsPerPage)

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW)
                .name("§f Previous Page")
                .lore("§7Go to previous page")
            pane.addItem(GuiItem(prevItem) {
                currentPage--
                open()
            }, 0, 5)
        }

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack(Material.ARROW)
                .name("§f Next Page")
                .lore("§7Go to next page")
            pane.addItem(GuiItem(nextItem) {
                currentPage++
                open()
            }, 8, 5)
        }

        // Page indicator
        val pageItem = ItemStack(Material.PAPER)
            .name("§f Page ${currentPage + 1}/$totalPages")
        pane.addItem(GuiItem(pageItem), 2, 5)

        // Back button
        val backItem = ItemStack(Material.BARRIER)
            .name("§c Back")
            .lore("§7Return to party management")
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }, 4, 5)

        // Refresh button
        val refreshItem = ItemStack(Material.SUNFLOWER)
            .name("§a Refresh")
            .lore("§7Reload player list and status")
        pane.addItem(GuiItem(refreshItem) {
            // Reload party data from service
            val updatedParty = partyService.getActivePartiesForGuild(guild.id)
                .find { it.id == party.id }
            if (updatedParty != null) {
                party = updatedParty
            }
            open()
        }, 6, 5)
    }

    private fun getOnlinePartyMembers(): List<UUID> {
        val onlineMembers = mutableListOf<UUID>()

        for (guildId in party.guildIds) {
            val guildMembers = memberService.getGuildMembers(guildId)
            for (member in guildMembers) {
                val onlinePlayer = Bukkit.getPlayer(member.playerId)
                if (onlinePlayer != null && onlinePlayer.isOnline) {
                    onlineMembers.add(member.playerId)
                }
            }
        }

        return onlineMembers.distinct()
    }

    private fun openPlayerModerationMenu(targetPlayerId: UUID) {
        menuNavigator.openMenu(PlayerModerationMenu(menuNavigator, player, guild, party, targetPlayerId))
    }

    override fun passData(data: Any?) {
        when (data) {
            is Guild -> guild = data
            is Party -> party = data
        }
    }
}
