package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.interaction.menus.GuildInfoRelationResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildRelationBrowserMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val relationType: RelationType,
) : Menu, KoinComponent {
    init {
        require(relationType == RelationType.ALLY || relationType == RelationType.ENEMY) {
            "GuildRelationBrowserMenu only supports ALLY or ENEMY"
        }
    }

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    override fun open() {
        val entries = GuildInfoRelationResolver.resolve(
            guild.id,
            relationType,
            relationService.getGuildRelations(guild.id),
            guildService::getGuild,
        )
        val totalPages = GuildInfoRelationResolver.totalPages(entries.size, ITEMS_PER_PAGE)
        currentPage = currentPage.coerceIn(0, totalPages - 1)
        val pageEntries = GuildInfoRelationResolver.page(entries, currentPage, ITEMS_PER_PAGE)

        val title = if (relationType == RelationType.ALLY) {
            lang.guiTitle("menu.guild_info.relation_browser.allies.title", "guild" to guild.name)
        } else {
            lang.guiTitle("menu.guild_info.relation_browser.enemies.title", "guild" to guild.name)
        }
        val gui = ChestGui(
            6,
            MenuTitleBuilder.build(guild.guiTheme, 6, title),
        )
        val contentPane = StaticPane(1, 0, 7, 4)
        val controlsPane = StaticPane(0, 0, 9, 6)

        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { event ->
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                event.isCancelled = true
            }
        }

        if (pageEntries.isEmpty()) {
            val emptyName = if (relationType == RelationType.ALLY) {
                lang.gui("menu.guild_info.relation_browser.allies.empty")
            } else {
                lang.gui("menu.guild_info.relation_browser.enemies.empty")
            }
            val empty = ItemStack.of(Material.BARRIER)
                .name(emptyName)
            contentPane.addItem(GuiItem(empty), 3, 1)
        } else {
            pageEntries.forEachIndexed { index, entry ->
                val item = createGuildItem(entry.guild)
                contentPane.addItem(
                    GuiItem(item) {
                        menuNavigator.openMenu(
                            menuFactory.createGuildInfoMenu(menuNavigator, player, entry.guild)
                        )
                    },
                    index % 7,
                    index / 7,
                )
            }
        }

        addNavigation(controlsPane, entries.size, totalPages)
        gui.addPane(contentPane)
        gui.addPane(controlsPane)
        gui.show(player)
    }
    private fun createGuildItem(otherGuild: Guild): ItemStack {
        val banner = GuildBannerItemResolver.resolve(otherGuild)

        val displayName = if (relationType == RelationType.ALLY) {
            lang.gui("menu.guild_info.relation_browser.allies.guild_name", "guild" to otherGuild.name)
        } else {
            lang.gui("menu.guild_info.relation_browser.enemies.guild_name", "guild" to otherGuild.name)
        }
        val mode = if (otherGuild.mode == GuildMode.PEACEFUL) {
            lang.gui("menu.guild_info.relation_browser.mode.peaceful")
        } else {
            lang.gui("menu.guild_info.relation_browser.mode.hostile")
        }

        return banner
            .name(displayName)
            .lore(
                lang.gui(
                    "menu.guild_info.relation_browser.members",
                    "count" to memberService.getMemberCount(otherGuild.id),
                )
            )
            .lore(lang.gui("menu.guild_info.relation_browser.level", "level" to otherGuild.level))
            .lore(lang.gui("menu.guild_info.relation_browser.mode.line", "mode" to mode))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_info.relation_browser.open"))
    }

    private fun addNavigation(pane: StaticPane, totalItems: Int, totalPages: Int) {
        if (currentPage > 0) {
            val previous = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_info.relation_browser.previous.name"))
                .lore(lang.gui("menu.guild_info.relation_browser.previous.description"))
            pane.addItem(
                GuiItem(previous) {
                    currentPage--
                    open()
                },
                0,
                4,
            )
        }

        val indicator = ItemStack.of(Material.PAPER)
            .name(
                lang.gui(
                    "menu.guild_info.relation_browser.page.name",
                    "page" to currentPage + 1,
                    "pages" to totalPages,
                )
            )
            .lore(lang.gui("menu.guild_info.relation_browser.page.total", "count" to totalItems))
        pane.addItem(GuiItem(indicator), 4, 4)

        if (currentPage < totalPages - 1) {
            val next = ItemStack.of(Material.ARROW)
                .name(lang.gui("menu.guild_info.relation_browser.next.name"))
                .lore(lang.gui("menu.guild_info.relation_browser.next.description"))
            pane.addItem(
                GuiItem(next) {
                    currentPage++
                    open()
                },
                8,
                4,
            )
        }

        val back = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.guild_info.relation_browser.back.name"))
            .lore(lang.gui("menu.guild_info.relation_browser.back.description"))
        pane.addItem(GuiItem(back) { menuNavigator.goBack() }, 4, 5)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: guild
    }

    companion object {
        internal const val ITEMS_PER_PAGE = 28
    }
}
