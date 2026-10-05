package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import net.lumalyte.lg.utils.MenuIcons
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

// Locale keys stay literal so LocaleContractTest can see them.
@Suppress("StringLiteralDuplication", "LibraryEntitiesShouldNotBePublic")
class GuildRelationsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private var guild: Guild): Menu, KoinComponent {

    private val relationService: RelationService by inject()
    private val guildService: GuildService by inject()
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val heading = lang.guiTitle("menu.guild_relations.title", "guild" to guild.name)
        val gui = ChestGui(ROWS, MenuTitleBuilder.build(guild.guiTheme, ROWS, heading))
        val pane = StaticPane(0, 0, COLUMNS, ROWS)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }
        gui.addPane(pane)

        // Row 1: Current Relations Overview
        addRelationsOverviewSection(pane)

        // Row 2: Relation Requests
        addRelationRequestsSection(pane)

        // Row 3: Diplomatic Actions
        addDiplomaticActionsSection(pane)

        // Row 4-5: Relation Details/History
        addRelationDetailsSection(pane)

        // Row 6: Navigation
        addBackButton(pane, BACK_SLOT, ROWS - 1)

        gui.show(player)
    }

    private fun addRelationsOverviewSection(pane: StaticPane) {
        val relations = relationService.getGuildRelations(guild.id)

        // Count relations by type
        val allies = relations.count { it.type == RelationType.ALLY && it.isActive() }
        val enemies = relations.count { it.type == RelationType.ENEMY && it.isActive() }
        val truces = relations.count { it.type == RelationType.TRUCE && it.isActive() }

        // Allies
        val alliesItem = ItemStack.of(if (allies > 0) Material.DIAMOND else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.overview.allies.name"))
            .lore(lang.gui("menu.guild_relations.overview.allies.description"))
            .lore(lang.gui("menu.guild_relations.count", "count" to allies))
            .lore(lang.gui("menu.common.click.open"))

        val alliesGuiItem = GuiItem(alliesItem) {
            openAlliesListMenu()
        }
        pane.addItem(alliesGuiItem, 1, 0)

        // Enemies
        val enemiesItem = ItemStack.of(if (enemies > 0) Material.REDSTONE else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.overview.enemies.name"))
            .lore(lang.gui("menu.guild_relations.overview.enemies.description"))
            .lore(lang.gui("menu.guild_relations.count", "count" to enemies))
            .lore(lang.gui("menu.common.click.open"))

        val enemiesGuiItem = GuiItem(enemiesItem) {
            openEnemiesListMenu()
        }
        pane.addItem(enemiesGuiItem, ENEMIES_SLOT, 0)

        // Truces
        val trucesItem = ItemStack.of(if (truces > 0) Material.CLOCK else Material.GRAY_DYE)
            .name(lang.gui("menu.guild_relations.overview.truces.name"))
            .lore(lang.gui("menu.guild_relations.overview.truces.description"))
            .lore(lang.gui("menu.guild_relations.count", "count" to truces))
            .lore(lang.gui("menu.common.click.open"))

        val trucesGuiItem = GuiItem(trucesItem) {
            openTrucesListMenu()
        }
        pane.addItem(trucesGuiItem, TRUCES_SLOT, 0)

        // Diplomatic Status
        val statusItem = NexoItemProvider.getItemStackOrFallback("lg_nav_diplomacy") { ItemStack.of(Material.BOOK) }
            .name(lang.gui("menu.guild_relations.overview.status.name"))
            .lore(lang.gui("menu.guild_relations.overview.status.description"))
            .lore(lang.gui("menu.guild_relations.overview.status.summary", "allies" to allies, "enemies" to enemies, "truces" to truces))

        val statusGuiItem = GuiItem(statusItem) {
            openDiplomaticStatusMenu()
        }
        pane.addItem(statusGuiItem, STATUS_SLOT, 0)
    }

    private fun addRelationRequestsSection(pane: StaticPane) {
        val incomingRequests = relationService.getIncomingRequests(guild.id)
        val outgoingRequests = relationService.getOutgoingRequests(guild.id)

        // Incoming requests
        val incomingItem =
            MenuIcons.requests(incoming = true, count = incomingRequests.size)
                .name(lang.gui("menu.guild_relations.requests.incoming.name"))
                .lore(lang.gui("menu.guild_relations.requests.incoming.description"))
                .lore(lang.gui("menu.guild_relations.count", "count" to incomingRequests.size))
                .lore(lang.gui("menu.common.click.open"))

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 2, 2)

        // Outgoing requests
        val outgoingItem =
            MenuIcons.requests(incoming = false, count = outgoingRequests.size)
                .name(lang.gui("menu.guild_relations.requests.outgoing.name"))
                .lore(lang.gui("menu.guild_relations.requests.outgoing.description"))
                .lore(lang.gui("menu.guild_relations.count", "count" to outgoingRequests.size))
                .lore(lang.gui("menu.common.click.open"))

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, OUTGOING_SLOT, 2)
    }

    private fun addDiplomaticActionsSection(pane: StaticPane) {
        // Request Alliance
        val allianceItem = NexoItemProvider.getItemStackOrFallback("lg_alliance") { ItemStack.of(Material.GOLDEN_APPLE) }
            .name(lang.gui("menu.guild_relations.action.alliance.name"))
            .lore(lang.gui("menu.guild_relations.action.alliance.description"))
            .lore(lang.gui("menu.guild_relations.action.acceptance"))
            .lore(lang.gui("menu.common.click.choose_guild"))

        val allianceGuiItem = GuiItem(allianceItem) {
            if (!memberService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RELATIONS)) {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_manage_permission"))
                return@GuiItem
            }
            openRequestAllianceMenu()
        }
        pane.addItem(allianceGuiItem, 1, 1)

        // Request Truce
        val truceItem = NexoItemProvider.getItemStackOrFallback("lg_truce") { ItemStack.of(Material.WHITE_BANNER) }
            .name(lang.gui("menu.guild_relations.action.truce.name"))
            .lore(lang.gui("menu.guild_relations.action.truce.description"))
            .lore(lang.gui("menu.common.click.choose_guild"))
            .lore(lang.gui("menu.guild_relations.action.acceptance"))

        val truceGuiItem = GuiItem(truceItem) {
            if (!memberService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RELATIONS)) {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_manage_permission"))
                return@GuiItem
            }
            openRequestTruceMenu()
        }
        pane.addItem(truceGuiItem, TRUCE_ACTION_SLOT, 1)

        // Declare Enemy
        val enemyItem = NexoItemProvider.getItemStackOrFallback("lg_enemy") { ItemStack.of(Material.IRON_SWORD) }
            .name(lang.gui("menu.guild_relations.action.enemy.name"))
            .lore(lang.gui("menu.guild_relations.action.enemy.description"))
            .lore(lang.gui("menu.guild_relations.action.enemy.no_acceptance"))
            .lore(lang.gui("menu.common.click.choose_guild"))

        val enemyGuiItem = GuiItem(enemyItem) {
            if (!memberService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.DECLARE_WAR)) {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_enemy_permission"))
                return@GuiItem
            }
            openDeclareEnemyMenu()
        }
        pane.addItem(enemyGuiItem, ENEMY_ACTION_SLOT, 1)
    }

    private fun addRelationDetailsSection(pane: StaticPane) {
        // Diplomatic History
        val historyItem = NexoItemProvider.getItemStackOrFallback("lg_relations_history") { ItemStack.of(Material.KNOWLEDGE_BOOK) }
            .name(lang.gui("menu.guild_relations.details.history.name"))
            .lore(lang.gui("menu.guild_relations.details.history.description"))
            .lore(lang.gui("menu.common.click.open"))

        val historyGuiItem = GuiItem(historyItem) {
            openDiplomaticHistoryMenu()
        }
        pane.addItem(historyGuiItem, HISTORY_SLOT, 2)

        // Neutral Guilds
        val neutralItem = NexoItemProvider.getItemStackOrFallback("lg_peace") { ItemStack.of(Material.BOOKSHELF) }
            .name(lang.gui("menu.guild_relations.details.neutral.name"))
            .lore(lang.gui("menu.guild_relations.details.neutral.description"))
            .lore(lang.gui("menu.common.click.open"))

        val neutralGuiItem = GuiItem(neutralItem) {
            openNeutralGuildsMenu()
        }
        pane.addItem(neutralGuiItem, NEUTRAL_ACTION_SLOT, 1)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = NexoItemProvider.getItemStackOrFallback("lg_back") { ItemStack.of(Material.ARROW) }
            .name(lang.gui("menu.guild_home.back.name"))
            .lore(lang.gui("menu.guild_home.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }
        pane.addItem(guiItem, x, y)
    }

    private fun openAlliesListMenu() {
        menuNavigator.openMenu(menuFactory.createAlliesListMenu(menuNavigator, player, guild))
    }

    private fun openEnemiesListMenu() {
        menuNavigator.openMenu(menuFactory.createEnemiesListMenu(menuNavigator, player, guild))
    }

    private fun openTrucesListMenu() {
        // Get active truces
        val truces = relationService.getGuildRelationsByType(guild.id, net.lumalyte.lg.domain.entities.RelationType.TRUCE)
            .filter { it.isActive() }

        if (truces.isEmpty()) {
            player.sendMessage(lang.msg("menu.guild_relations.truces.none"))
            return
        }

        player.sendMessage(lang.msg("menu.guild_relations.truces.header"))
        truces.forEach { relation ->
            val otherGuildId = relation.getOtherGuild(guild.id)
            val otherGuild = guildService.getGuild(otherGuildId)
            if (otherGuild != null && relation.expiresAt != null) {
                val remaining = java.time.Duration.between(java.time.Instant.now(), relation.expiresAt)
                val days = remaining.toDays()
                val hours = remaining.toHours() % 24
                player.sendMessage(lang.msg("menu.guild_relations.truces.row", "guild" to otherGuild.name, "days" to days, "hours" to hours))
            }
        }
    }

    private fun openDiplomaticStatusMenu() {
        val allies = relationService.getGuildRelationsByType(guild.id, net.lumalyte.lg.domain.entities.RelationType.ALLY).count { it.isActive() }
        val enemies = relationService.getGuildRelationsByType(guild.id, net.lumalyte.lg.domain.entities.RelationType.ENEMY).count { it.isActive() }
        val truces = relationService.getGuildRelationsByType(guild.id, net.lumalyte.lg.domain.entities.RelationType.TRUCE).count { it.isActive() }

        player.sendMessage(lang.msg("menu.guild_relations.status.header"))
        player.sendMessage(lang.msg("menu.guild_relations.status.allies", "count" to allies))
        player.sendMessage(lang.msg("menu.guild_relations.status.enemies", "count" to enemies))
        player.sendMessage(lang.msg("menu.guild_relations.status.truces", "count" to truces))
        player.sendMessage(lang.msg("menu.guild_relations.status.incoming", "count" to relationService.getIncomingRequests(guild.id).size))
        player.sendMessage(lang.msg("menu.guild_relations.status.outgoing", "count" to relationService.getOutgoingRequests(guild.id).size))
    }

    private fun openIncomingRequestsMenu() {
        menuNavigator.openMenu(menuFactory.createIncomingRequestsMenu(menuNavigator, player, guild))
    }

    private fun openOutgoingRequestsMenu() {
        menuNavigator.openMenu(menuFactory.createOutgoingRequestsMenu(menuNavigator, player, guild))
    }

    private fun openRequestAllianceMenu() {
        menuNavigator.openMenu(menuFactory.createAllianceRequestMenu(menuNavigator, player, guild))
    }

    private fun openRequestTruceMenu() {
        menuNavigator.openMenu(menuFactory.createTruceRequestMenu(menuNavigator, player, guild))
    }

    private fun openDeclareEnemyMenu() {
        menuNavigator.openMenu(menuFactory.createEnemyDeclarationMenu(menuNavigator, player, guild))
    }

    private fun openDiplomaticHistoryMenu() {
        player.sendMessage(lang.msg("menu.guild_relations.feedback.history_placeholder"))
    }

    private fun openNeutralGuildsMenu() {
        val allGuilds = guildService.getAllGuilds().filter { it.id != guild.id }
        val neutralGuilds = allGuilds.filter { otherGuild ->
            relationService.getRelationType(guild.id, otherGuild.id) == net.lumalyte.lg.domain.entities.RelationType.NEUTRAL
        }

        if (neutralGuilds.isEmpty()) {
            player.sendMessage(lang.msg("menu.guild_relations.neutral.none"))
            return
        }

        player.sendMessage(lang.msg("menu.guild_relations.neutral.header"))
        neutralGuilds.take(10).forEach { otherGuild ->
            val memberCount = memberService.getMemberCount(otherGuild.id)
            player.sendMessage(lang.msg("menu.guild_relations.neutral.row", "guild" to otherGuild.name, "count" to memberCount))
        }
        if (neutralGuilds.size > 10) {
            player.sendMessage(lang.msg("menu.guild_relations.neutral.more", "count" to neutralGuilds.size - 10))
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }

    private companion object {
        const val ENEMY_ACTION_SLOT = 5
        const val HISTORY_SLOT = 4
        const val NEUTRAL_ACTION_SLOT = 7
        const val ROWS = 4
        const val COLUMNS = 9
        const val BACK_SLOT = 4
        const val ENEMIES_SLOT = 3
        const val TRUCE_ACTION_SLOT = 3
        const val TRUCES_SLOT = 5
        const val STATUS_SLOT = 7
        const val OUTGOING_SLOT = 6
    }
}
