package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.LastSeen
import net.lumalyte.lg.domain.values.MemberAction
import net.lumalyte.lg.domain.values.MemberActions
import net.lumalyte.lg.domain.values.RankStep
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.RankNameContent
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * REQ-193: one member's page. Viewers with Manage Members get Promote, Demote, Kick and Ban;
 * a button they cannot use is a grey dye that says why. The services check everything again
 * when a button is clicked.
 */
class GuildMemberPageMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val targetId: UUID,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        guild = guildService.getGuild(guild.id) ?: run {
            player.sendMessage(lang.msg("menu.guild_info.feedback.guild_missing"))
            menuNavigator.goBack()
            return
        }
        val target = memberService.getMember(targetId, guild.id) ?: run {
            player.sendMessage(lang.msg("guild_page.member.feedback.gone"))
            menuNavigator.goBack()
            return
        }
        val name = Bukkit.getOfflinePlayer(targetId).name ?: lang.raw("menu.guild_info.fallback.unknown_player")
        val ranks = rankService.listRanks(guild.id)
        val targetRank = ranks.firstOrNull { it.id == target.rankId }
        val viewerMember = memberService.getMember(player.uniqueId, guild.id)
        val viewerRank = viewerMember?.let { m -> ranks.firstOrNull { it.id == m.rankId } }
        val canManage = viewerMember != null && memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("guild_page.member_title", "player" to name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)

        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(targetId).build())
        head.name(lang.gui("guild_page.roster.name", "player" to name))
            .lore(lang.gui("guild_page.roster.rank", "rank" to RankNameContent.miniMessage(targetRank?.name ?: lang.raw("menu.guild_info.fallback.member"))))
            .lore(if (Bukkit.getPlayer(targetId) != null) lang.gui("guild_page.roster.online") else lastSeenLine())
            .lore(
                lang.gui(
                    "guild_page.roster.joined",
                    "date" to target.joinedAt.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern(lang.raw("menu.guild_info.overview.date_format"))),
                ),
            )
        pane.addItem(GuiItem(head), 4, 0)

        if (canManage && targetRank != null) {
            val actions = MemberActions.of(
                canManage = true,
                self = targetId == player.uniqueId,
                viewerPriority = viewerRank?.priority,
                targetPriority = targetRank.priority,
                ranks = ranks.map { RankStep(it.name, it.priority) },
            )
            pane.addItem(button("lg_promote", Material.GOLDEN_APPLE, actions.promote, rankTitle(true, actions.promote), lang.gui("guild_page.member.click_rank")) {
                changeRank(name, up = true)
            }, 2, 2)
            pane.addItem(button("lg_demote", Material.BOOK, actions.demote, rankTitle(false, actions.demote), lang.gui("guild_page.member.click_rank")) {
                changeRank(name, up = false)
            }, 3, 2)
            pane.addItem(button("lg_kick", Material.RED_WOOL, actions.kick, lang.gui("guild_page.member.kick"), lang.gui("guild_page.member.click"), lang.gui("guild_page.member.kick_lore")) {
                menuNavigator.openMenu(menuFactory.createGuildKickConfirmationMenu(menuNavigator, player, guild, target))
            }, 5, 2)
            pane.addItem(button("lg_ban", Material.BARRIER, actions.ban, lang.gui("guild_page.member.ban"), lang.gui("guild_page.member.click"), lang.gui("guild_page.member.ban_lore")) {
                menuNavigator.openMenu(GuildBanConfirmationMenu(menuNavigator, player, guild, targetId))
            }, 6, 2)
        }

        pane.addItem(
            GuiItem(NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }.name(lang.gui("guild_page.nav.back"))) {
                menuNavigator.goBack()
            },
            4, 5,
        )
        gui.addPane(pane)
        gui.show(player)
    }

    private fun rankTitle(promote: Boolean, action: MemberAction): Component {
        val rank = when (action) {
            is MemberAction.Allowed -> action.rankName
            is MemberAction.Denied -> action.rankName
        }?.let(RankNameContent::miniMessage)
        return when {
            promote && rank == null -> lang.gui("guild_page.member.promote_plain")
            promote -> lang.gui("guild_page.member.promote", "rank" to rank)
            rank == null -> lang.gui("guild_page.member.demote_plain")
            else -> lang.gui("guild_page.member.demote", "rank" to rank)
        }
    }

    @Suppress("LongParameterList")
    private fun button(
        id: String,
        fallback: Material,
        action: MemberAction,
        title: Component,
        hint: Component,
        vararg lore: Component,
        onClick: () -> Unit,
    ): GuiItem {
        val item = if (action is MemberAction.Allowed) {
            NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(fallback) }
        } else {
            ItemStack.of(Material.GRAY_DYE)
        }
        item.name(title)
        lore.forEach(item::lore)
        return when (action) {
            is MemberAction.Allowed -> GuiItem(item.lore(hint)) { onClick() }
            is MemberAction.Denied -> GuiItem(item.lore(GuildPageText.denial(lang, action.reason)))
        }
    }

    private fun changeRank(name: String, up: Boolean) {
        val ok = if (up) memberService.promoteMember(targetId, guild.id, player.uniqueId)
        else memberService.demoteMember(targetId, guild.id, player.uniqueId)
        if (!ok) {
            player.sendMessage(lang.msg("guild_page.member.feedback.failed"))
        } else {
            val rank = memberService.getMember(targetId, guild.id)?.let { rankService.getRank(it.rankId)?.name } ?: ""
            val shown = RankNameContent.miniMessage(rank)
            player.sendMessage(
                if (up) lang.msg("guild_page.member.feedback.promoted", "player" to name, "rank" to shown)
                else lang.msg("guild_page.member.feedback.demoted", "player" to name, "rank" to shown),
            )
        }
        open()
    }

    private fun lastSeenLine(): Component {
        return GuildPageText.lastSeen(lang, LastSeen.of(System.currentTimeMillis(), Bukkit.getOfflinePlayer(targetId).lastSeen))
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
