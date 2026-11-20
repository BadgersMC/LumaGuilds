package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Party
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.values.CenteredRow
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
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
 * REQ-206: one party's page. Shows who is in it; offers Invite and Leave (Manage Parties) and
 * Moderate (Manage Relations). The party service checks permissions again on every action.
 */
class GuildPartyPageMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val partyId: UUID,
) : Menu, KoinComponent {
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()
    private var leaveArmed = false

    override fun open() {
        val party = partyService.getParty(partyId)?.takeIf { it.isActive() && it.includesGuild(guild.id) } ?: run {
            player.sendMessage(lang.msg("party_page.feedback.gone"))
            menuNavigator.goBack()
            return
        }
        val name = party.name ?: lang.raw("menu.party.management.unnamed")
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(guild.guiTheme, ROWS, lang.guiTitle("party_page.title", "party" to name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, ROWS)

        pane.addItem(GuiItem(card(party, name)), 4, 0)
        val guilds = party.guildIds.mapNotNull { guildService.getGuild(it) }.sortedBy { if (it.id == leaderGuild(party)) "" else it.name.lowercase() }
        val shown = guilds.take(9)
        CenteredRow.slots(shown.size).zip(shown).forEach { (x, member) ->
            val item = GuildBannerItemResolver.resolveForDisplay(member)
                .name(lang.gui("party_page.guild.name", "guild" to member.name))
                .lore(if (member.id == leaderGuild(party)) lang.gui("party_page.guild.started") else lang.gui("party_page.guild.member"))
                .lore(lang.gui("party_page.guild.view"))
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, member)) }, x, 1)
        }

        val canManageParties = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_PARTIES)
        val canModerate = memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_RELATIONS)
        if (canManageParties && !party.isPrivateParty()) {
            val invite = NexoItemProvider.getItemStackOrFallback("lg_invite") { ItemStack.of(Material.GREEN_WOOL) }
                .name(lang.gui("party_page.invite.name"))
                .lore(lang.gui("party_page.invite.lore"))
            pane.addItem(GuiItem(invite) { menuNavigator.openMenu(PartyInviteGuildMenu(menuNavigator, player, guild, party.id)) }, 2, 2)
        }
        if (canModerate) {
            val moderate = NexoItemProvider.getItemStackOrFallback("lg_party_moderate") { ItemStack.of(Material.ANVIL) }
                .name(lang.gui("menu.party.management.moderate.name"))
                .lore(lang.gui("menu.party.management.moderate.lore"))
            pane.addItem(GuiItem(moderate) { menuNavigator.openMenu(PartyModerationMenu(menuNavigator, player, guild, party)) }, 4, 2)
        }
        if (canManageParties) {
            val leave = NexoItemProvider.getItemStackOrFallback("lg_leave") { ItemStack.of(Material.IRON_DOOR) }
                .name(lang.gui("party_page.leave.name"))
                .lore(lang.gui("party_page.leave.lore", "party" to name))
                .lore(if (leaveArmed) lang.gui("party_page.leave.confirm") else lang.gui("party_page.leave.hint"))
            pane.addItem(GuiItem(leave) { leave(party, name) }, 6, 2)
        }
        pane.addItem(
            GuiItem(NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }.name(lang.gui("guild_page.nav.back"))) {
                menuNavigator.goBack()
            },
            4, 3,
        )
        gui.addPane(pane)
        gui.show(player)
    }

    private fun leaderGuild(party: Party): UUID? = memberService.getPlayerGuilds(party.leaderId).firstOrNull { party.includesGuild(it) }

    private fun card(party: Party, name: String): ItemStack {
        val format = DateTimeFormatter.ofPattern(lang.raw("menu.guild_info.overview.date_format"))
        val leader = Bukkit.getOfflinePlayer(party.leaderId).name ?: lang.raw("menu.guild_info.fallback.unknown_player")
        val item = NexoItemProvider.getItemStackOrFallback("lg_party") { ItemStack.of(Material.FIREWORK_ROCKET) }
            .name(lang.gui("party_page.card.name", "party" to name))
            .lore(lang.gui("party_page.card.leader", "leader" to leader))
            .lore(lang.gui("party_page.card.created", "date" to party.createdAt.atZone(ZoneId.systemDefault()).format(format)))
            .lore(
                party.expiresAt?.let { lang.gui("party_page.card.expires", "date" to it.atZone(ZoneId.systemDefault()).format(format)) }
                    ?: lang.gui("party_page.card.never"),
            )
        val ranks = party.restrictedRoles.orEmpty().mapNotNull { rankService.getRank(it)?.name }
        item.lore(
            if (ranks.isEmpty()) lang.gui("party_page.card.open_to_all")
            else lang.gui("party_page.card.open_to", "ranks" to RankNameContent.miniMessage(ranks.joinToString(", "))),
        )
        return item.lore(lang.gui("party_page.card.chat"))
    }

    private fun leave(party: Party, name: String) {
        if (!leaveArmed) {
            leaveArmed = true
            open()
            return
        }
        leaveArmed = false
        if (partyService.leaveParty(party.id, guild.id, player.uniqueId) != null || partyService.getParty(party.id)?.includesGuild(guild.id) == false) {
            player.sendMessage(lang.msg("party_page.feedback.left", "party" to name))
            menuNavigator.goBack()
        } else {
            player.sendMessage(lang.msg("party_page.feedback.leave_failed"))
            open()
        }
    }

    override fun passData(data: Any?) = Unit

    private companion object {
        const val ROWS = 4
    }
}
