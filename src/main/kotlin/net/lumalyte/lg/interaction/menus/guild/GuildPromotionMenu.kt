package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.bukkit.plugin.Plugin

class GuildPromotionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild
) : Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val plugin: Plugin by inject()
    private val lang: LangService by inject()

    override fun open() {
        // Check permission first
        val hasPermission = rankService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RANKS)
        if (!hasPermission) {
            player.sendMessage(lang.msg("menu.guild_promotion.permission.denied"))
            player.sendMessage(lang.msg("menu.guild_promotion.permission.required"))
            menuNavigator.goBack()
            return
        }

        val ranks = rankService.listRanks(guild.id).sortedBy { it.priority }
        val rankById = ranks.associateBy { it.id }
        val members = memberService.getGuildMembers(guild.id).sortedBy { rankById[it.rankId]?.priority ?: Int.MAX_VALUE }

        if (members.isEmpty()) {
            player.sendMessage(lang.msg("menu.guild_promotion.feedback.no_members"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.guild_promotion.title", "guild" to guild.name)))
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            if (e.click == ClickType.SHIFT_LEFT || e.click == ClickType.SHIFT_RIGHT)
                e.isCancelled = true
        }

        // Paginated member grid (9x5 = 45 per page) + static nav row
        val paginatedPane = PaginatedPane(0, 0, 9, 5)
        val staticPane = StaticPane(0, 5, 9, 1)

        val memberItems = members.map { member ->
            val rank = rankById[member.rankId]
            val playerName = Bukkit.getOfflinePlayer(member.playerId).name ?: member.playerId.toString().take(8)
            val isOnline = Bukkit.getPlayer(member.playerId)?.isOnline == true

            val item = ItemStack.of(if (isOnline) Material.PLAYER_HEAD else Material.SKELETON_SKULL)
                .name(lang.legacy("menu.guild_promotion.member.name", "player" to playerName))
                .lore(lang.legacy("menu.guild_promotion.member.rank", "rank" to (rank?.name ?: lang.raw("menu.guild_promotion.fallback.unknown_rank"))))
                .lore(
                    if (isOnline) {
                        lang.legacy("menu.guild_promotion.member.status.online")
                    } else {
                        lang.legacy("menu.guild_promotion.member.status.offline")
                    }
                )
                .lore(lang.legacy("menu.common.blank"))
                .lore(lang.legacy("menu.guild_promotion.member.promote"))
                .lore(lang.legacy("menu.guild_promotion.member.demote"))

            GuiItem(item) {
                val rankIdx = ranks.indexOf(rank)
                if (it.click == ClickType.LEFT) {
                    // Promote
                    if (rankIdx >= 0 && rankIdx > 0) {
                        val success = memberService.promoteMember(member.playerId, guild.id, player.uniqueId)
                        if (success) {
                            // Fetch the exact rank the service assigned
                            val updatedMember = memberService.getMember(member.playerId, guild.id)
                            val newRankName = updatedMember?.let { m -> rankById[m.rankId]?.name } ?: ranks[rankIdx - 1].name
                            player.sendMessage(lang.msg("menu.guild_promotion.feedback.promoted", "player" to playerName, "rank" to newRankName))
                            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
                            reloadSafely()
                        } else {
                            player.sendMessage(lang.msg("menu.guild_promotion.feedback.promote_failed", "player" to playerName))
                        }
                    } else {
                        player.sendMessage(lang.msg("menu.guild_promotion.feedback.highest_rank", "player" to playerName))
                    }
                } else if (it.click == ClickType.RIGHT) {
                    // Demote
                    if (rankIdx >= 0 && rankIdx < ranks.size - 1) {
                        val success = memberService.demoteMember(member.playerId, guild.id, player.uniqueId)
                        if (success) {
                            // Fetch the exact rank the service assigned
                            val updatedMember = memberService.getMember(member.playerId, guild.id)
                            val newRankName = updatedMember?.let { m -> rankById[m.rankId]?.name } ?: ranks[rankIdx + 1].name
                            player.sendMessage(lang.msg("menu.guild_promotion.feedback.demoted", "player" to playerName, "rank" to newRankName))
                            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                            reloadSafely()
                        } else {
                            player.sendMessage(lang.msg("menu.guild_promotion.feedback.demote_failed", "player" to playerName))
                        }
                    } else {
                        player.sendMessage(lang.msg("menu.guild_promotion.feedback.lowest_rank", "player" to playerName))
                    }
                }
            }
        }

        paginatedPane.populateWithGuiItems(memberItems)

        // Navigation row (y=5)
        if (paginatedPane.pages > 1) {
            // Previous page
            val prevItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.guild_promotion.navigation.previous"))
                .lore(lang.legacy("menu.guild_promotion.navigation.page", "page" to paginatedPane.page + 1, "pages" to paginatedPane.pages))
            staticPane.addItem(GuiItem(prevItem) {
                if (paginatedPane.page > 0) {
                    paginatedPane.page--
                    gui.update()
                }
            }, 0, 0)

            // Next page
            val nextItem = ItemStack.of(Material.ARROW)
                .name(lang.legacy("menu.guild_promotion.navigation.next"))
                .lore(lang.legacy("menu.guild_promotion.navigation.page", "page" to paginatedPane.page + 1, "pages" to paginatedPane.pages))
            staticPane.addItem(GuiItem(nextItem) {
                if (paginatedPane.page < paginatedPane.pages - 1) {
                    paginatedPane.page++
                    gui.update()
                }
            }, 8, 0)
        }

        // Member count display
        val infoItem = ItemStack.of(Material.PLAYER_HEAD)
            .name(lang.legacy("menu.guild_promotion.info.members", "count" to members.size))
            .lore(lang.legacy("menu.guild_promotion.info.guild", "guild" to guild.name))
        staticPane.addItem(GuiItem(infoItem) { it.isCancelled = true }, 4, 0)

        // Back button
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.guild_promotion.navigation.back.name"))
            .lore(lang.legacy("menu.guild_promotion.navigation.back.description"))
        staticPane.addItem(GuiItem(backItem) { menuNavigator.goBack() }, 7, 0)

        gui.addPane(paginatedPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    /**
     * Reopens the menu on the next tick to avoid desyncing the cursor
     * during InventoryClickEvent dispatch.
     */
    private fun reloadSafely() {
        Bukkit.getScheduler().runTask(plugin, Runnable { open() })
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
