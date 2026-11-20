package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.values.RelationRow
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.GuildInfoRelationResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
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

/**
 * REQ-196: allies, enemies and active truces on their own rows. Each row starts with a label and
 * shows up to 8 guilds; more opens the full relation browser (truces have none, so they cap at 8).
 */
class GuildRelationsOverviewMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val relations = relationService.getGuildRelations(guild.id)
        val allies = GuildInfoRelationResolver.resolve(guild.id, RelationType.ALLY, relations, guildService::getGuild).map { it.guild }
        val enemies = GuildInfoRelationResolver.resolve(guild.id, RelationType.ENEMY, relations, guildService::getGuild).map { it.guild }
        val truces = relations.filter { it.type == RelationType.TRUCE && it.isActive() }
            .mapNotNull { guildService.getGuild(it.getOtherGuild(guild.id)) }
            .sortedBy { it.name.lowercase() }

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("guild_page.relations_title", "guild" to guild.name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)

        row(pane, 1, "lg_alliance", Material.LIME_BANNER, lang.gui("guild_page.relation_rows.allies"), allies, RelationType.ALLY)
        row(pane, 2, "lg_enemy", Material.RED_BANNER, lang.gui("guild_page.relation_rows.enemies"), enemies, RelationType.ENEMY)
        row(pane, 3, "lg_truce", Material.WHITE_BANNER, lang.gui("guild_page.relation_rows.truces"), truces, null)

        pane.addItem(
            GuiItem(NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }.name(lang.gui("guild_page.nav.back"))) {
                menuNavigator.goBack()
            },
            4, 5,
        )
        gui.addPane(pane)
        gui.show(player)
    }

    @Suppress("LongParameterList")
    private fun row(pane: StaticPane, y: Int, icon: String, fallback: Material, label: net.kyori.adventure.text.Component, guilds: List<Guild>, browse: RelationType?) {
        val shown = RelationRow.of(guilds)
        val labelItem = NexoItemProvider.getItemStackOrFallback(icon) { ItemStack.of(fallback) }.name(label)
        if (guilds.isEmpty()) labelItem.lore(lang.gui("guild_page.relation_rows.none"))
        pane.addItem(GuiItem(labelItem), 0, y)
        shown.shown.forEachIndexed { index, other ->
            val item = NexoItemProvider.getItemStackOrFallback(icon) { ItemStack.of(fallback) }
                .name(lang.gui("guild_page.relation_rows.guild", "guild" to other.name))
                .lore(lang.gui("guild_page.relation_rows.view"))
            val x = 1 + index
            // The ninth slot of a full row becomes "and N more" when there are more guilds.
            if (x == 8 && shown.more > 0 && browse != null) return@forEachIndexed
            pane.addItem(GuiItem(item) { menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, other)) }, x, y)
        }
        if (shown.more > 0 && browse != null) {
            val more = ItemStack.of(Material.PAPER)
                .name(lang.gui("guild_page.relation_rows.more", "count" to shown.more + 1))
                .lore(lang.gui("guild_page.relation_rows.more_hint"))
            pane.addItem(GuiItem(more) {
                menuNavigator.openMenu(menuFactory.createGuildRelationBrowserMenu(menuNavigator, player, guild, browse))
            }, 8, y)
        }
    }

    override fun passData(data: Any?) = Unit
}
