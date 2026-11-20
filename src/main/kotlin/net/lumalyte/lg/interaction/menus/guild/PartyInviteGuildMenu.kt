package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PartyService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/** REQ-206: pick a guild that is not in the party; it joins only when it accepts (#43's consent flow). */
class PartyInviteGuildMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val partyId: UUID,
) : Menu, KoinComponent {
    private val partyService: PartyService by inject()
    private val guildService: GuildService by inject()
    private val lang: LangService by inject()
    private var page = 0

    override fun open() {
        val party = partyService.getParty(partyId) ?: run { menuNavigator.goBack(); return }
        val pending = partyService.getPendingRequestsFromGuild(guild.id).map { it.toGuildId }.toSet()
        val guilds = guildService.getAllGuilds().filter { !party.includesGuild(it.id) }.sortedBy { it.name.lowercase() }
        val pages = maxOf(1, (guilds.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pages - 1)
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("party_page.invite.title")))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        guilds.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { index, target ->
            val invited = target.id in pending
            val item = GuildBannerItemResolver.resolveForDisplay(target)
                .name(lang.gui("party_page.guild.name", "guild" to target.name))
                .lore(if (invited) lang.gui("party_page.invite.already") else lang.gui("party_page.invite.click"))
            pane.addItem(GuiItem(item) {
                if (invited) return@GuiItem
                val sent = partyService.inviteToParty(partyId, guild.id, target.id, player.uniqueId) != null
                player.sendMessage(
                    if (sent) lang.msg("party_page.feedback.invited", "guild" to target.name)
                    else lang.msg("party_page.feedback.invite_failed", "guild" to target.name),
                )
                if (sent) menuNavigator.goBack() else open()
            }, index % 9, index / 9)
        }
        if (page > 0) pane.addItem(nav("lg_page_prev", lang.gui("guild_page.nav.previous")) { page--; open() }, 0, 5)
        pane.addItem(nav("lg_back", lang.gui("guild_page.nav.back")) { menuNavigator.goBack() }, 4, 5)
        if (page < pages - 1) pane.addItem(nav("lg_page_next", lang.gui("guild_page.nav.next")) { page++; open() }, 8, 5)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun nav(id: String, title: net.kyori.adventure.text.Component, action: () -> Unit) =
        GuiItem(NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(Material.ARROW) }.name(title)) { action() }

    override fun passData(data: Any?) = Unit

    private companion object {
        const val PAGE_SIZE = 45
    }
}
