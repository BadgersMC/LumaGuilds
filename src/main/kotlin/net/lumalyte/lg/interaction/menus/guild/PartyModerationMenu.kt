package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
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
    private val lang: LangService by inject()

    private var currentPage = 0
    private val itemsPerPage = 36 // 9x4 grid for players

    override fun open() {
        // Check permission
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)) {
            player.sendMessage(lang.msg("menu.party.moderation.feedback.no_permission"))
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.party.moderation.title", "channel" to (party.name ?: lang.raw("menu.party.moderation.channel")))))
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
            val noPlayersItem = ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.party.moderation.empty.name"))
                .lore(lang.gui("menu.party.moderation.empty.lore"))
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
        val head = ItemStack.of(Material.PLAYER_HEAD)

        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(playerId).build()
        )

        val meta = head.itemMeta as SkullMeta
        val playerName = Bukkit.getOfflinePlayer(playerId).name ?: lang.raw("menu.party.moderation.unknown_player")
        head.itemMeta = meta

        // Check moderation status
        val isMuted = party.isPlayerMuted(playerId)
        val isBanned = party.isPlayerBanned(playerId)

        val displayName = when {
            isBanned -> lang.gui("menu.party.moderation.player.name_banned", "player" to playerName)
            isMuted -> lang.gui("menu.party.moderation.player.name_muted", "player" to playerName)
            else -> lang.gui("menu.party.moderation.player.name_ok", "player" to playerName)
        }

        return head.name(displayName)
            .lore(lang.gui("menu.party.moderation.player.player", "player" to playerName))
            .lore(lang.gui("menu.common.blank"))
            .apply {
                if (isBanned) {
                    lore(lang.gui("menu.party.moderation.player.banned"))
                    lore(lang.gui("menu.party.moderation.player.banned_lore"))
                } else if (isMuted) {
                    val expiration = party.mutedPlayers[playerId]
                    if (expiration != null) {
                        val remaining = java.time.Duration.between(java.time.Instant.now(), expiration)
                        lore(lang.gui("menu.party.moderation.player.muted"))
                        lore(lang.gui("menu.party.moderation.player.expires", "hours" to remaining.toHours(), "minutes" to remaining.toMinutes() % 60))
                    } else {
                        lore(lang.gui("menu.party.moderation.player.permanently_muted"))
                    }
                } else {
                    lore(lang.gui("menu.party.moderation.player.normal"))
                }
            }
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.party.moderation.player.click"))
    }

    private fun addModerationStatus(pane: StaticPane) {
        // Muted players count
        val activeMutes = party.getActiveMutes()
        val mutedItem = ItemStack.of(Material.BELL)
            .name(lang.gui("menu.party.moderation.muted.name", "count" to activeMutes.size))
            .lore(lang.gui("menu.party.moderation.muted.lore"))
            .apply {
                activeMutes.entries.take(5).forEach { (playerId, expiration) ->
                    val name = Bukkit.getOfflinePlayer(playerId).name ?: lang.raw("menu.party.moderation.unknown")
                    val expText = expiration?.let { lang.gui("menu.party.moderation.muted.until", "expiration" to it) }
                        ?: lang.gui("menu.party.moderation.muted.permanent")
                    lore(lang.gui("menu.party.moderation.muted.row", "player" to name, "expiration" to expText))
                }
                if (activeMutes.size > 5) {
                    lore(lang.gui("menu.party.moderation.more", "count" to activeMutes.size - 5))
                }
            }
        pane.addItem(GuiItem(mutedItem), 2, 4)

        // Banned players count
        val bannedItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.party.moderation.banned.name", "count" to party.bannedPlayers.size))
            .lore(lang.gui("menu.party.moderation.banned.lore"))
            .apply {
                party.bannedPlayers.take(5).forEach { playerId ->
                    val name = Bukkit.getOfflinePlayer(playerId).name ?: lang.raw("menu.party.moderation.unknown")
                    lore(lang.gui("menu.party.moderation.banned.row", "player" to name))
                }
                if (party.bannedPlayers.size > 5) {
                    lore(lang.gui("menu.party.moderation.more", "count" to party.bannedPlayers.size - 5))
                }
            }
        pane.addItem(GuiItem(bannedItem), 6, 4)

        // Channel info
        val restrictionStatus = if (party.hasRoleRestrictions()) {
            lang.raw("menu.party.moderation.info.restricted")
        } else {
            lang.raw("menu.party.moderation.info.open")
        }
        val infoItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.party.moderation.info.name"))
            .lore(lang.gui("menu.party.moderation.info.channel", "channel" to (party.name ?: lang.raw("menu.party.moderation.unnamed"))))
            .lore(lang.gui("menu.party.moderation.info.guilds", "count" to party.guildIds.size))
            .lore(lang.gui("menu.party.moderation.info.restrictions", "status" to restrictionStatus))
        pane.addItem(GuiItem(infoItem), 4, 4)
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val onlineMembers = getOnlinePartyMembers().filter { it != player.uniqueId }
        val totalPages = maxOf(1, (onlineMembers.size + itemsPerPage - 1) / itemsPerPage)

        // Previous page button
        if (currentPage > 0) {
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.party.moderation.previous.name"))
                .lore(lang.gui("menu.party.moderation.previous.lore"))
            pane.addItem(GuiItem(prevItem) {
                currentPage--
                open()
            }, 0, 5)
        }

        // Next page button
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.party.moderation.next.name"))
                .lore(lang.gui("menu.party.moderation.next.lore"))
            pane.addItem(GuiItem(nextItem) {
                currentPage++
                open()
            }, 8, 5)
        }

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.party.moderation.page", "page" to currentPage + 1, "total_pages" to totalPages))
        pane.addItem(GuiItem(pageItem), 2, 5)

        // Back button
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.party.moderation.back.name"))
            .lore(lang.gui("menu.party.moderation.back.lore"))
        pane.addItem(GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildPartyManagementMenu(menuNavigator, player, guild))
        }, 4, 5)

        // Refresh button
        val refreshItem = ItemStack.of(Material.SUNFLOWER)
            .name(lang.gui("menu.party.moderation.refresh.name"))
            .lore(lang.gui("menu.party.moderation.refresh.lore"))
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
