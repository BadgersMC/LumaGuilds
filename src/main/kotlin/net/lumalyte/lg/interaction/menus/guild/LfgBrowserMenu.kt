package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.GuiTheme
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Menu for browsing guilds available via LFG (Looking For Guild).
 * Shows all open guilds with available member slots.
 */
class LfgBrowserMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player
) : Menu, KoinComponent {

    private val lfgService: LfgService by inject()
    private val memberService: MemberService by inject()
    private val configService: ConfigService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(GuiTheme.NEUTRAL, 6, lang.guiTitle("menu.lfg_browser.title")))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ||
                guiEvent.click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        val availableGuilds = lfgService.getAvailableGuilds()

        if (availableGuilds.isEmpty()) {
            // Show "no guilds" message
            val emptyPane = StaticPane(0, 0, 9, 6)
            addNoGuildsMessage(emptyPane)
            gui.addPane(emptyPane)
        } else {
            // Show paginated guild list
            val paginatedPane = PaginatedPane(0, 0, 9, 5)
            populateGuildList(paginatedPane, availableGuilds)
            gui.addPane(paginatedPane)

            // Add navigation buttons
            val navPane = StaticPane(0, 5, 9, 1)
            addNavigationButtons(navPane, paginatedPane)
            gui.addPane(navPane)
        }

        gui.show(player)
    }

    private fun populateGuildList(paginatedPane: PaginatedPane, guilds: List<Guild>) {
        val config = configService.loadConfig()
        val maxMembers = config.guild.maxMembersPerGuild

        for ((index, guild) in guilds.withIndex()) {
            val page = index / 45  // 45 slots per page (9x5)
            val guildItem = createGuildItem(guild, maxMembers)
            val guildGuiItem = GuiItem(guildItem) {
                player.closeInventory()
                // Open join requirements menu
                menuNavigator.openMenu(menuFactory.createJoinRequirementsMenu(menuNavigator, player, guild))
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
            }
            paginatedPane.addPane(page, StaticPane(index % 9, (index / 9) % 5, 1, 1).apply {
                addItem(guildGuiItem, 0, 0)
            })
        }
    }

    private fun createGuildItem(guild: Guild, maxMembers: Int): ItemStack {
        val memberCount = memberService.getMemberCount(guild.id)
        val isPeaceful = guild.mode == net.lumalyte.lg.domain.entities.GuildMode.PEACEFUL
        val joinRequirement = lfgService.getJoinRequirement(guild)

        val item = ItemStack.of(Material.WHITE_BANNER)
            .name(lang.gui("menu.lfg_browser.guild.name", "guild" to guild.name))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.lfg_browser.guild.level", "level" to guild.level))
            .lore(lang.gui("menu.lfg_browser.guild.members", "count" to memberCount, "maximum" to maxMembers))
            .lore(
                if (isPeaceful) {
                    lang.gui("menu.lfg_browser.guild.mode.peaceful")
                } else {
                    lang.gui("menu.lfg_browser.guild.mode.hostile")
                }
            )

        if (joinRequirement != null) {
            item.lore(lang.gui("menu.common.blank"))
            item.lore(lang.gui("menu.lfg_browser.guild.fee.required", "amount" to joinRequirement.amount, "currency" to formatCurrencyName(joinRequirement.currencyName)))
        } else {
            item.lore(lang.gui("menu.common.blank"))
            item.lore(lang.gui("menu.lfg_browser.guild.fee.none"))
        }

        item.lore(lang.gui("menu.common.blank"))
        item.lore(lang.gui("menu.lfg_browser.guild.action"))

        return item
    }

    private fun addNoGuildsMessage(pane: StaticPane) {
        val noGuildsItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.lfg_browser.empty.name"))
            .lore(lang.gui("menu.lfg_browser.empty.description"))
            .lore(lang.gui("menu.lfg_browser.empty.detail"))
            .lore(lang.gui("menu.common.blank"))
            .lore(lang.gui("menu.lfg_browser.empty.hint"))
            .lore(lang.gui("menu.lfg_browser.empty.action"))

        pane.addItem(GuiItem(noGuildsItem), 4, 2)
    }

    private fun addNavigationButtons(pane: StaticPane, paginatedPane: PaginatedPane) {
        // Previous page button
        val prevItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.lfg_browser.navigation.previous.name"))
            .lore(lang.gui("menu.lfg_browser.navigation.previous.description"))

        val prevGuiItem = GuiItem(prevItem) {
            if (paginatedPane.page > 0) {
                paginatedPane.page = paginatedPane.page - 1
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                open() // Refresh menu
            }
        }
        pane.addItem(prevGuiItem, 3, 0)

        // Close button
        val closeItem = ItemStack.of(Material.BARRIER)
            .name(lang.gui("menu.lfg_browser.navigation.close.name"))
            .lore(lang.gui("menu.lfg_browser.navigation.close.description"))

        val closeGuiItem = GuiItem(closeItem) {
            player.closeInventory()
            player.sendMessage(lang.msg("menu.lfg_browser.feedback.closed"))
        }
        pane.addItem(closeGuiItem, 4, 0)

        // Next page button
        val nextItem = ItemStack.of(Material.ARROW)
            .name(lang.gui("menu.lfg_browser.navigation.next.name"))
            .lore(lang.gui("menu.lfg_browser.navigation.next.description"))

        val nextGuiItem = GuiItem(nextItem) {
            if (paginatedPane.page < paginatedPane.pages - 1) {
                paginatedPane.page = paginatedPane.page + 1
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1.0f, 1.0f)
                open() // Refresh menu
            }
        }
        pane.addItem(nextGuiItem, 5, 0)
    }

    private fun formatCurrencyName(name: String): String {
        return name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    override fun passData(data: Any?) {
        // No data passing needed
    }
}
