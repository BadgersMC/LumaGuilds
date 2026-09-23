package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildListEntry
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuiTheme
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GuildListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
) : Menu, KoinComponent {
    private val guildListService: GuildListService by inject()
    private val memberService: MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    private var currentPage = 0
    private var sortKey = GuildListSortKey.ALL_TIME_ACTIVE

    override fun open() {
        val pageSize = guildListService.configuredPageSize()
        val page = guildListService.getPage(
            page = currentPage,
            pageSize = pageSize,
            sortKey = sortKey,
            ascending = sortKey.defaultAscending,
        )
        currentPage = page.page

        val gui = ChestGui(
            6,
            MenuTitleBuilder.build(
                GuiTheme.NEUTRAL,
                6,
                lang.guiTitle("menu.guild_list.title"),
            ),
        )
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { event ->
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                event.isCancelled = true
            }
        }

        val content = StaticPane(0, 0, 9, 4)
        page.entries.forEachIndexed { index, entry ->
            content.addItem(
                GuiItem(createGuildItem(entry)) {
                    menuNavigator.openMenu(
                        menuFactory.createGuildInfoMenu(menuNavigator, player, entry.guild)
                    )
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                },
                index % 9,
                index / 9,
            )
        }
        gui.addPane(content)

        val sortPane = StaticPane(0, 4, 9, 1)
        addSortButton(sortPane, 1, Material.CLOCK, GuildListSortKey.ALL_TIME_ACTIVE)
        addSortButton(sortPane, 3, Material.IRON_SWORD, GuildListSortKey.WEEKLY_ACTIVE)
        addSortButton(sortPane, 5, Material.EXPERIENCE_BOTTLE, GuildListSortKey.GUILD_LEVEL)
        addSortButton(sortPane, 7, Material.WRITABLE_BOOK, GuildListSortKey.CREATED_AT)
        gui.addPane(sortPane)

        val nav = StaticPane(0, 5, 9, 1)
        if (page.page > 0) {
            nav.addItem(GuiItem(
                ItemStack.of(Material.ARROW)
                    .name(lang.gui("menu.guild_list.navigation.previous.name"))
            ) {
                currentPage--
                open()
            }, 2, 0)
        }
        nav.addItem(
            GuiItem(
                ItemStack.of(Material.PAPER)
                    .name(lang.gui(
                        "menu.guild_list.navigation.page.name",
                        "page" to page.page + 1,
                        "pages" to page.totalPages,
                    ))
                    .lore(lang.gui(
                        "menu.guild_list.navigation.page.lore",
                        "total" to page.totalCount,
                    ))
            ),
            4,
            0,
        )
        if (page.page < page.totalPages - 1) {
            nav.addItem(GuiItem(
                ItemStack.of(Material.ARROW)
                    .name(lang.gui("menu.guild_list.navigation.next.name"))
            ) {
                currentPage++
                open()
            }, 6, 0)
        }

        nav.addItem(GuiItem(
            ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.guild_list.navigation.close.name"))
        ) {
            player.closeInventory()
        }, 8, 0)
        gui.addPane(nav)
        gui.show(player)
    }

    private fun createGuildItem(entry: GuildListEntry): ItemStack {
        val guild = entry.guild
        val item = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.guild_list.guild.name", "guild" to guild.name))
            .lore(lang.gui("menu.guild_list.guild.level", "level" to guild.level))
            .lore(lang.gui(
                "menu.guild_list.guild.members",
                "count" to memberService.getMemberCount(guild.id),
            ))
            .lore(lang.gui(
                "menu.guild_list.guild.created",
                "date" to CREATED_DATE.format(guild.createdAt),
            ))

        when (sortKey) {
            GuildListSortKey.ALL_TIME_ACTIVE ->
                item.lore(lang.gui(
                    "menu.guild_list.guild.activity_score",
                    "activity_score" to entry.sortValue,
                ))

            GuildListSortKey.WEEKLY_ACTIVE -> {
                item.lore(lang.gui(
                    "menu.guild_list.guild.activity_score",
                    "activity_score" to entry.sortValue,
                ))
                item.lore(lang.gui(
                    "menu.guild_list.guild.unique_pvp_kills",
                    "count" to entry.uniquePvpKills,
                ))
            }

            GuildListSortKey.GUILD_LEVEL,
            GuildListSortKey.CREATED_AT -> Unit
        }
        return item
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.guild_list.guild.action"))
    }

    private fun addSortButton(
        pane: StaticPane,
        x: Int,
        material: Material,
        key: GuildListSortKey,
    ) {
        val active = key == sortKey
        val item = ItemStack.of(material)
            .name(sortName(key))
            .lore(
                if (active) {
                    lang.gui("menu.guild_list.sort.active")
                } else {
                    lang.gui("menu.guild_list.sort.select")
                }
            )
        pane.addItem(GuiItem(item) {
            if (!active) {
                sortKey = key
                currentPage = 0
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                open()
            }
        }, x, 0)
    }

    private fun sortName(key: GuildListSortKey) = when (key) {
        GuildListSortKey.ALL_TIME_ACTIVE ->
            lang.gui("menu.guild_list.sort.all_time_active.name")
        GuildListSortKey.WEEKLY_ACTIVE ->
            lang.gui("menu.guild_list.sort.weekly_active.name")
        GuildListSortKey.GUILD_LEVEL ->
            lang.gui("menu.guild_list.sort.guild_level.name")
        GuildListSortKey.CREATED_AT ->
            lang.gui("menu.guild_list.sort.created_at.name")
    }

    override fun passData(data: Any?) = Unit

    companion object {
        private val CREATED_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    }
}
