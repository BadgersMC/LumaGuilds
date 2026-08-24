package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
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
import java.util.*

class GuildMemberManagementMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                                private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private lateinit var memberPane: StaticPane
    private var currentPage = 0
    private val itemsPerPage = 45 // 9x5 grid

    override fun open() {
        // Create 6x9 double chest GUI
        val gui = ChestGui(6, MenuTitleBuilder.build(
            guild.guiTheme,
            6,
            lang.legacy("menu.member_management.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Initialize member display pane
        memberPane = StaticPane(0, 0, 9, 5)
        updateMemberDisplay()

        // Add navigation buttons
        addNavigationButtons(pane)

        // Add action buttons
        addInviteButton(pane, 1, 5)
        addPromoteDemoteButton(pane, 3, 5)
        addKickButton(pane, 5, 5)
        addBackButton(pane, 7, 5)

        gui.addPane(memberPane)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun updateMemberDisplay() {
        val allMembers = memberService.getGuildMembers(guild.id).sortedBy { it.playerId }

        // Calculate pagination
        val totalPages = (allMembers.size + itemsPerPage - 1) / itemsPerPage
        if (currentPage >= totalPages) {
            currentPage = maxOf(0, totalPages - 1)
        }

        // Get members for current page
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allMembers.size)
        val pageMembers = allMembers.subList(startIndex, endIndex)

        // Clear existing items
        // Note: StaticPane doesn't have a clear method, so we'll recreate it
        val newPane = StaticPane(0, 0, 9, 5)

        // Add member items to the pane
        for ((index, member) in pageMembers.withIndex()) {
            val x = index % 9
            val y = index / 9
            val memberItem = createMemberHead(member)
            val guiItem = GuiItem(memberItem) {
                // Open member details menu for promote/demote
                openMemberDetails(member)
            }
            newPane.addItem(guiItem, x, y)
        }

        // Replace the pane (this is a simplified approach)
        // In a real implementation, you'd need to properly replace the pane in the GUI
        memberPane = newPane
    }

    private fun createMemberHead(member: Member): ItemStack {
        val head = ItemStack.of(Material.PLAYER_HEAD)

        head.setData(
            DataComponentTypes.PROFILE,
            ResolvableProfile.resolvableProfile().uuid(member.playerId).build())
        val meta = head.itemMeta as SkullMeta

        // Try to get player name from online players or cache
        val playerName = Bukkit.getOfflinePlayer(member.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")


        head.itemMeta = meta

        return head.name(lang.legacy("menu.member_management.item.member.name", "player" to playerName))
            .lore(lang.legacy("menu.member_management.item.member.lore.player", "player" to playerName))
            .lore(lang.legacy("menu.member_management.item.member.lore.joined", "joined" to member.joinedAt))
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.member_management.item.member.lore.action"))
    }

    private fun addNavigationButtons(pane: StaticPane) {
        val allMembers = memberService.getGuildMembers(guild.id)
        val totalPages = (allMembers.size + itemsPerPage - 1) / itemsPerPage

        // Previous page button
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.member_management.item.previous.name"))
            .lore(lang.legacy("menu.member_management.item.previous.lore"))

        val prevGuiItem = GuiItem(prevItem) {
            if (currentPage > 0) {
                currentPage--
                open() // Reopen menu to refresh display
            }
        }
        pane.addItem(prevGuiItem, 0, 5)

        // Next page button
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.member_management.item.next.name"))
            .lore(lang.legacy("menu.member_management.item.next.lore"))

        val nextGuiItem = GuiItem(nextItem) {
            if (currentPage < totalPages - 1) {
                currentPage++
                open() // Reopen menu to refresh display
            }
        }
        pane.addItem(nextGuiItem, 8, 5)

        // Page indicator
        val pageItem = ItemStack.of(Material.PAPER)
            .name(lang.legacy(
                "menu.member_management.item.page.name",
                "current" to currentPage + 1,
                "total" to maxOf(1, totalPages),
            ))
            .lore(lang.legacy("menu.member_management.item.page.lore"))

        pane.addItem(GuiItem(pageItem), 4, 5)
    }

    private fun addInviteButton(pane: StaticPane, x: Int, y: Int) {
        val inviteItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.legacy("menu.member_management.item.invite.name"))
            .lore(lang.legacy("menu.member_management.item.invite.lore.description"))
            .lore(lang.legacy("menu.member_management.item.invite.lore.requirement"))

        val inviteGuiItem = GuiItem(inviteItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.member_management.feedback.no_invite_permission"))
            }
        }
        pane.addItem(inviteGuiItem, x, y)
    }

    private fun addPromoteDemoteButton(pane: StaticPane, x: Int, y: Int) {
        val promoteItem = ItemStack.of(Material.GOLDEN_APPLE)
            .name(lang.legacy("menu.member_management.item.rank_change.name"))
            .lore(lang.legacy("menu.member_management.item.rank_change.lore.description"))
            .lore(lang.legacy("menu.member_management.item.rank_change.lore.requirement"))

        val promoteGuiItem = GuiItem(promoteItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                player.sendMessage(lang.msg("menu.member_management.feedback.select_member_for_rank_change"))
                // The member heads will handle the click events
            } else {
                player.sendMessage(lang.msg("menu.member_management.feedback.no_rank_permission"))
            }
        }
        pane.addItem(promoteGuiItem, x, y)
    }

    private fun openMemberDetails(member: Member) {
        if (!memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
            player.sendMessage(lang.msg("menu.member_management.feedback.no_rank_permission"))
            return
        }

        // Open member rank management menu
        menuNavigator.openMenu(menuFactory.createGuildMemberRankMenu(menuNavigator, player, guild, member))
    }

    private fun addKickButton(pane: StaticPane, x: Int, y: Int) {
        val kickItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.legacy("menu.member_management.item.kick.name"))
            .lore(lang.legacy("menu.member_management.item.kick.lore.description"))
            .lore(lang.legacy("menu.member_management.item.kick.lore.requirement"))

        val kickGuiItem = GuiItem(kickItem) {
            if (memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)) {
                menuNavigator.openMenu(menuFactory.createGuildKickMenu(menuNavigator, player, guild))
            } else {
                player.sendMessage(lang.msg("menu.member_management.feedback.no_kick_permission"))
            }
        }
        pane.addItem(kickGuiItem, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.BARRIER)
            .name(lang.legacy("menu.member_management.item.back.name"))
            .lore(lang.legacy("menu.member_management.item.back.lore"))

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        }
        pane.addItem(backGuiItem, x, y)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

