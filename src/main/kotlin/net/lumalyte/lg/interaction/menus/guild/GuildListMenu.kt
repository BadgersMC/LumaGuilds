package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.GuildListEntry
import net.lumalyte.lg.application.services.GuildListPage
import net.lumalyte.lg.application.services.GuildListService
import net.lumalyte.lg.domain.entities.GuildListSortKey
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.GuildBannerItemResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuiTheme
import net.lumalyte.lg.utils.MenuTitleBuilder
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
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

class GuildListMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
) : Menu, KoinComponent {
    private val guildListService: GuildListService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()
    private val plugin: LumaGuilds by inject()

    private var currentPage = 0
    private var sortKey = GuildListSortKey.ALL_TIME_ACTIVE
    private val loadGeneration = AtomicLong()

    override fun open() {
        val generation = loadGeneration.incrementAndGet()
        val navigationToken = menuNavigator.currentNavigationToken()
        val requestedPage = currentPage
        val requestedSort = sortKey
        val pageSize = guildListService.configuredPageSize()

        guildListService.getPageAsync(
            page = requestedPage,
            pageSize = pageSize,
            sortKey = requestedSort,
            ascending = requestedSort.defaultAscending,
        ).whenComplete { page, error ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (
                    !player.isOnline ||
                    loadGeneration.get() != generation ||
                    !menuNavigator.isNavigationCurrent(navigationToken)
                ) return@Runnable
                if (error != null) {
                    plugin.logger.warning("Failed to load /g list for ${player.name}: ${error.message}")
                    return@Runnable
                }
                render(page, navigationToken)
            })
        }
    }

    private fun render(page: GuildListPage, navigationToken: Long) {
        currentPage = page.page

        val gui = ChestGui(
            6,
            MenuTitleBuilder.build(
                GuiTheme.DEFAULT,
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
        gui.setOnClose {
            if (menuNavigator.isNavigationCurrent(navigationToken)) {
                loadGeneration.incrementAndGet()
                menuNavigator.invalidateCurrentNavigation()
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
        val item =
            GuildBannerItemResolver.resolveForDisplay(guild)
                .name(lang.gui("menu.guild_list.guild.name", "guild" to guild.name))
                .lore(lang.gui("menu.guild_list.guild.level", "level" to guild.level))
                .lore(
                    lang.gui(
                        "menu.guild_list.guild.members",
                        "count" to entry.memberCount,
                    ),
                ).lore(
                    lang.gui(
                        "menu.guild_list.guild.created",
                        "date" to CREATED_DATE.format(guild.createdAt),
                    ),
                )

        val owners =
            entry.details.owners
                .joinToString(", ") {
                    org.bukkit.Bukkit
                        .getPlayer(it)
                        ?.name ?: it.toString()
                }.ifEmpty { lang.raw("community.directory.unknown") }
        item
            .lore(lang.gui("community.directory.owners", "owners" to owners))
            .lore(
                lang.gui(
                    "community.directory.allies",
                    "allies" to
                        entry.details.allies
                            .joinToString(", ")
                            .ifEmpty { lang.raw("community.directory.none") },
                ),
            ).lore(
                if (guild.isOpen) lang.gui("community.directory.recruitment_open") else lang.gui("community.directory.recruitment_closed"),
            )

        when (sortKey) {
            GuildListSortKey.ALL_TIME_ACTIVE -> {
                item.lore(
                    lang.gui(
                        "menu.guild_list.guild.activity_score",
                        "activity_score" to entry.sortValue,
                    ),
                )
            }

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
