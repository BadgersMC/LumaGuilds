package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

class GuildRelationsMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                        private var guild: Guild): Menu, KoinComponent {

    private val relationService: RelationService by inject()
    private val guildService: GuildService by inject()
    private val memberService: net.lumalyte.lg.application.services.MemberService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()
    private val lang: LangService by inject()

    override fun open() {
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.guild_relations.title", "guild" to guild.name)))
        val pane = StaticPane(0, 0, 9, 6)
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
        addBackButton(pane, 4, 5)

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
            .name(lang.legacy("menu.guild_relations.overview.allies.name"))
            .lore(lang.legacy("menu.guild_relations.overview.allies.description"))
            .lore(lang.legacy("menu.guild_relations.count", "count" to allies))
            .lore(lang.legacy("menu.guild_relations.overview.allies.support"))

        val alliesGuiItem = GuiItem(alliesItem) {
            openAlliesListMenu()
        }
        pane.addItem(alliesGuiItem, 0, 0)

        // Enemies
        val enemiesItem = ItemStack.of(if (enemies > 0) Material.REDSTONE else Material.GRAY_DYE)
            .name(lang.legacy("menu.guild_relations.overview.enemies.name"))
            .lore(lang.legacy("menu.guild_relations.overview.enemies.description"))
            .lore(lang.legacy("menu.guild_relations.count", "count" to enemies))
            .lore(lang.legacy("menu.guild_relations.overview.enemies.warfare"))

        val enemiesGuiItem = GuiItem(enemiesItem) {
            openEnemiesListMenu()
        }
        pane.addItem(enemiesGuiItem, 2, 0)

        // Truces
        val trucesItem = ItemStack.of(if (truces > 0) Material.CLOCK else Material.GRAY_DYE)
            .name(lang.legacy("menu.guild_relations.overview.truces.name"))
            .lore(lang.legacy("menu.guild_relations.overview.truces.description"))
            .lore(lang.legacy("menu.guild_relations.count", "count" to truces))
            .lore(lang.legacy("menu.guild_relations.overview.truces.expiration"))

        val trucesGuiItem = GuiItem(trucesItem) {
            openTrucesListMenu()
        }
        pane.addItem(trucesGuiItem, 4, 0)

        // Diplomatic Status
        val statusItem = ItemStack.of(Material.BOOK)
            .name(lang.legacy("menu.guild_relations.overview.status.name"))
            .lore(lang.legacy("menu.guild_relations.overview.status.description"))
            .lore(lang.legacy("menu.guild_relations.overview.status.summary", "allies" to allies, "enemies" to enemies, "truces" to truces))

        val statusGuiItem = GuiItem(statusItem) {
            openDiplomaticStatusMenu()
        }
        pane.addItem(statusGuiItem, 6, 0)
    }

    private fun addRelationRequestsSection(pane: StaticPane) {
        val incomingRequests = relationService.getIncomingRequests(guild.id)
        val outgoingRequests = relationService.getOutgoingRequests(guild.id)

        // Incoming requests
        val incomingItem = ItemStack.of(if (incomingRequests.isEmpty()) Material.GRAY_DYE else Material.PAPER)
            .name(lang.legacy("menu.guild_relations.requests.incoming.name"))
            .lore(lang.legacy("menu.guild_relations.requests.incoming.description"))
            .lore(lang.legacy("menu.guild_relations.count", "count" to incomingRequests.size))
            .lore(lang.legacy("menu.guild_relations.requests.incoming.proposals"))

        val incomingGuiItem = GuiItem(incomingItem) {
            openIncomingRequestsMenu()
        }
        pane.addItem(incomingGuiItem, 1, 1)

        // Outgoing requests
        val outgoingItem = ItemStack.of(if (outgoingRequests.isEmpty()) Material.GRAY_DYE else Material.WRITABLE_BOOK)
            .name(lang.legacy("menu.guild_relations.requests.outgoing.name"))
            .lore(lang.legacy("menu.guild_relations.requests.outgoing.description"))
            .lore(lang.legacy("menu.guild_relations.count", "count" to outgoingRequests.size))
            .lore(lang.legacy("menu.guild_relations.requests.outgoing.awaiting"))

        val outgoingGuiItem = GuiItem(outgoingItem) {
            openOutgoingRequestsMenu()
        }
        pane.addItem(outgoingGuiItem, 3, 1)
    }

    private fun addDiplomaticActionsSection(pane: StaticPane) {
        // Request Alliance
        val allianceItem = ItemStack.of(Material.GOLDEN_APPLE)
            .name(lang.legacy("menu.guild_relations.action.alliance.name"))
            .lore(lang.legacy("menu.guild_relations.action.alliance.description"))
            .lore(lang.legacy("menu.guild_relations.action.acceptance"))
            .lore(lang.legacy("menu.guild_relations.action.alliance.support"))

        val allianceGuiItem = GuiItem(allianceItem) {
            if (!memberService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RELATIONS)) {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_manage_permission"))
                return@GuiItem
            }
            openRequestAllianceMenu()
        }
        pane.addItem(allianceGuiItem, 0, 2)

        // Request Truce
        val truceItem = ItemStack.of(Material.WHITE_BANNER)
            .name(lang.legacy("menu.guild_relations.action.truce.name"))
            .lore(lang.legacy("menu.guild_relations.action.truce.description"))
            .lore(lang.legacy("menu.guild_relations.action.truce.temporary"))
            .lore(lang.legacy("menu.guild_relations.action.acceptance"))

        val truceGuiItem = GuiItem(truceItem) {
            if (!memberService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.MANAGE_RELATIONS)) {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_manage_permission"))
                return@GuiItem
            }
            openRequestTruceMenu()
        }
        pane.addItem(truceGuiItem, 2, 2)

        // Declare Enemy
        val enemyItem = ItemStack.of(Material.IRON_SWORD)
            .name(lang.legacy("menu.guild_relations.action.enemy.name"))
            .lore(lang.legacy("menu.guild_relations.action.enemy.description"))
            .lore(lang.legacy("menu.guild_relations.action.enemy.no_acceptance"))
            .lore(lang.legacy("menu.guild_relations.action.enemy.hostile"))

        val enemyGuiItem = GuiItem(enemyItem) {
            if (!memberService.hasPermission(player.uniqueId, guild.id, net.lumalyte.lg.domain.entities.RankPermission.DECLARE_WAR)) {
                player.sendMessage(lang.msg("menu.guild_relations.feedback.no_enemy_permission"))
                return@GuiItem
            }
            openDeclareEnemyMenu()
        }
        pane.addItem(enemyGuiItem, 4, 2)
    }

    private fun addRelationDetailsSection(pane: StaticPane) {
        // Diplomatic History
        val historyItem = ItemStack.of(Material.KNOWLEDGE_BOOK)
            .name(lang.legacy("menu.guild_relations.details.history.name"))
            .lore(lang.legacy("menu.guild_relations.details.history.description"))
            .lore(lang.legacy("menu.guild_relations.details.history.track"))
            .lore(lang.legacy("menu.guild_relations.details.history.learn"))

        val historyGuiItem = GuiItem(historyItem) {
            openDiplomaticHistoryMenu()
        }
        pane.addItem(historyGuiItem, 0, 3)

        // Neutral Guilds
        val neutralItem = ItemStack.of(Material.BOOKSHELF)
            .name(lang.legacy("menu.guild_relations.details.neutral.name"))
            .lore(lang.legacy("menu.guild_relations.details.neutral.description"))
            .lore(lang.legacy("menu.guild_relations.details.neutral.browse"))
            .lore(lang.legacy("menu.guild_relations.details.neutral.partners"))

        val neutralGuiItem = GuiItem(neutralItem) {
            openNeutralGuildsMenu()
        }
        pane.addItem(neutralGuiItem, 2, 3)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.guild_home.back.name"))
            .lore(lang.legacy("menu.guild_home.back.description"))

        val guiItem = GuiItem(backItem) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
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
}

