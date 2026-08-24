package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Guild Dashboard — the hub-and-spoke entry point for guild management.
 *
 * A 3-row menu with 8 navigation category icons and a guild info display.
 * Replaces the old 6-row flat control panel.
 */
class GuildDashboard(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
    private val guildService: GuildService,
    private val rankService: RankService,
    private val memberService: MemberService,
    private val menuFactory: MenuFactory
) : Menu, KoinComponent {
    private val lang: LangService by inject()

    override fun open() {
        val playerId = player.uniqueId

        // Security check
        if (memberService.getMember(playerId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.dashboard.feedback.not_member"))
            menuNavigator.goBack()
            return
        }

        // Refresh guild data
        guild = guildService.getGuild(guild.id) ?: run {
            player.sendMessage(lang.msg("menu.dashboard.feedback.guild_missing"))
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.legacy("menu.dashboard.title", "guild" to guild.name),
        ))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            val click = e.click
            if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ||
                click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
            ) e.isCancelled = true
        }
        gui.addPane(pane)

        // Guild info display at top center
        addGuildInfoDisplay(pane, 4, 0)

        // Row 1 (y=1): Information, Members, Ranks, Economy
        addNavButton(pane, 0, 1, "lg_nav_info", Material.KNOWLEDGE_BOOK,
            lang.legacy("menu.dashboard.item.information.name"),
            lang.legacy("menu.dashboard.item.information.lore.line_1"),
            lang.legacy("menu.dashboard.item.information.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 2, 1, "lg_nav_members", Material.PLAYER_HEAD,
            lang.legacy("menu.dashboard.item.members.name"),
            lang.legacy("menu.dashboard.item.members.lore.line_1"),
            lang.legacy("menu.dashboard.item.members.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 4, 1, "lg_nav_ranks", Material.IRON_SWORD,
            lang.legacy("menu.dashboard.item.ranks.name"),
            lang.legacy("menu.dashboard.item.ranks.lore.line_1"),
            lang.legacy("menu.dashboard.item.ranks.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 6, 1, "lg_nav_economy", Material.GOLD_BLOCK,
            lang.legacy("menu.dashboard.item.economy.name"),
            lang.legacy("menu.dashboard.item.economy.lore.line_1"),
            lang.legacy("menu.dashboard.item.economy.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }

        // Row 2 (y=2): Settings, Progression, Diplomacy, Warfare
        addNavButton(pane, 0, 2, "lg_nav_settings", Material.COMMAND_BLOCK,
            lang.legacy("menu.dashboard.item.settings.name"),
            lang.legacy("menu.dashboard.item.settings.lore.line_1"),
            lang.legacy("menu.dashboard.item.settings.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 2, 2, "lg_nav_progression", Material.EXPERIENCE_BOTTLE,
            lang.legacy("menu.dashboard.item.progression.name"),
            lang.legacy("menu.dashboard.item.progression.lore.line_1"),
            lang.legacy("menu.dashboard.item.progression.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildProgressionMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 4, 2, "lg_nav_diplomacy", Material.BOOK,
            lang.legacy("menu.dashboard.item.diplomacy.name"),
            lang.legacy("menu.dashboard.item.diplomacy.lore.line_1"),
            lang.legacy("menu.dashboard.item.diplomacy.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 6, 2, "lg_nav_warfare", Material.DIAMOND_SWORD,
            lang.legacy("menu.dashboard.item.warfare.name"),
            lang.legacy("menu.dashboard.item.warfare.lore.line_1"),
            lang.legacy("menu.dashboard.item.warfare.lore.line_2")) {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }

        gui.show(player)
    }

    /**
     * Creates a navigation category button with Nexo icon + fallback.
     */
    private fun addNavButton(
        pane: StaticPane,
        x: Int,
        y: Int,
        nexoId: String,
        fallbackMaterial: Material,
        displayName: String,
        vararg loreLines: String,
        action: () -> Unit
    ) {
        val item = NexoItemProvider.getItemStackOrFallback(nexoId) {
            ItemStack.of(fallbackMaterial).name(displayName)
        }

        val meta = item.itemMeta ?: return
        meta.setDisplayName(displayName)
        meta.lore = loreLines.toList()
        item.itemMeta = meta

        pane.addItem(GuiItem(item) { action() }, x, y)
    }

    /**
     * Guild identity display at the top center: name, emoji, member count, balance.
     */
    private fun addGuildInfoDisplay(pane: StaticPane, x: Int, y: Int) {
        val emoji = guildService.getEmoji(guild.id)
        val memberCount = memberService.getMemberCount(guild.id)
        val rankCount = rankService.listRanks(guild.id).size

        val displayName = if (emoji != null) "$emoji ${guild.name}" else guild.name

        val item = ItemStack.of(Material.BELL)
            .name(lang.legacy("menu.dashboard.item.guild_info.name", "display_name" to displayName))
        val lore = java.util.ArrayList<String>().apply {
            add(lang.legacy("menu.dashboard.item.guild_info.lore.members", "member_count" to memberCount))
            add(lang.legacy("menu.dashboard.item.guild_info.lore.ranks", "rank_count" to rankCount))
            add(lang.legacy("menu.dashboard.item.guild_info.lore.balance", "balance" to guild.bankBalance))
            add("")
            add(lang.legacy("menu.dashboard.item.guild_info.lore.prompt_line_1"))
            add(lang.legacy("menu.dashboard.item.guild_info.lore.prompt_line_2"))
        }
        val meta = item.itemMeta ?: return
        meta.lore = lore
        item.itemMeta = meta

        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }
}
