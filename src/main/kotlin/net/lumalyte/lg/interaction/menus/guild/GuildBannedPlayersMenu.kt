package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildBanService
import net.lumalyte.lg.application.services.UnbanResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
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

/** REQ-194: the guild's bans, newest first, 45 per page; clicking a head unbans. */
class GuildBannedPlayersMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val banService: GuildBanService by inject()
    private val lang: LangService by inject()
    private var page = 0

    override fun open() {
        val bans = banService.bans(guild.id)
        val pages = maxOf(1, (bans.size + PAGE_SIZE - 1) / PAGE_SIZE)
        page = page.coerceIn(0, pages - 1)
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("guild_page.bans_title", "guild" to guild.name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)
        val format = DateTimeFormatter.ofPattern(lang.raw("menu.guild_info.overview.date_format"))
        val unknown = lang.raw("menu.guild_info.fallback.unknown_player")

        if (bans.isEmpty()) {
            pane.addItem(GuiItem(ItemStack.of(Material.PAPER).name(lang.gui("guild_page.bans.empty"))), 4, 2)
        }
        bans.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { index, ban ->
            val name = Bukkit.getOfflinePlayer(ban.playerId).name ?: unknown
            val head = ItemStack.of(Material.PLAYER_HEAD)
            head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(ban.playerId).build())
            head.name(lang.gui("guild_page.bans.entry", "player" to name))
                .lore(lang.gui("guild_page.bans.by", "by" to (Bukkit.getOfflinePlayer(ban.bannedBy).name ?: unknown)))
                .lore(lang.gui("guild_page.bans.date", "date" to ban.bannedAt.atZone(ZoneId.systemDefault()).format(format)))
                .lore(lang.gui("guild_page.bans.unban"))
            pane.addItem(GuiItem(head) {
                val ok = banService.unban(player.uniqueId, guild.id, ban.playerId) == UnbanResult.UNBANNED
                player.sendMessage(
                    if (ok) lang.msg("guild_page.bans.unbanned", "player" to name) else lang.msg("guild_page.bans.failed", "player" to name),
                )
                open()
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
