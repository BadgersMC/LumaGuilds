package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.api.GuildShopLookup
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildBanService
import net.lumalyte.lg.application.services.GuildInfoPrestige
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.domain.entities.VaultStatus
import net.lumalyte.lg.domain.values.GuildCardFact
import net.lumalyte.lg.domain.values.GuildCardFacts
import net.lumalyte.lg.domain.values.GuildRoster
import net.lumalyte.lg.domain.values.LastSeen
import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.lumalyte.lg.domain.values.RosterEntry
import net.lumalyte.lg.domain.values.RosterSort
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.interaction.menus.GuildInfoRelationResolver
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.ColorCodeUtils
import net.lumalyte.lg.utils.GuildDescriptionContent
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.RankNameContent
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * REQ-191 to REQ-196: the combined guild page. The top row describes the guild, the next four
 * rows are its members (36 per page) and the bottom row pages, sorts and manages the roster.
 * Anyone can open it; what each viewer sees follows REQ-192.
 */
class GuildInfoMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private var guild: Guild,
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val relationService: RelationService by inject()
    private val bankService: BankService by inject()
    private val warService: WarService by inject()
    private val banService: GuildBanService by inject()
    private val menuFactory: MenuFactory by inject()
    private val lang: LangService by inject()
    private val nexoEmojiService: NexoEmojiService by inject()
    private val prestigeHistory: net.lumalyte.lg.application.persistence.PrestigeHistory by inject()
    private val configService: net.lumalyte.lg.application.services.ConfigService by inject()

    private var page = 0
    private var sort = RosterSort.RANK

    override fun open() {
        // Public guild info is viewable by non-members. Re-resolve the guild so a menu kept
        // across a disband cannot show stale data.
        guild = guildService.getGuild(guild.id) ?: run {
            player.sendMessage(lang.msg("menu.guild_info.feedback.guild_missing"))
            menuNavigator.goBack()
            return
        }
        val isMember = memberService.getMember(player.uniqueId, guild.id) != null
        val canManage = isMember && memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_MEMBERS)

        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.guild_info.title", "guild" to guild.name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 6)

        val members = memberService.getGuildMembers(guild.id)
        val ranks = rankService.listRanks(guild.id).associateBy { it.id }
        val entries = members.map { member ->
            RosterEntry(
                member.playerId,
                Bukkit.getOfflinePlayer(member.playerId).name ?: lang.raw("menu.guild_info.fallback.unknown_player"),
                ranks[member.rankId]?.priority ?: Int.MAX_VALUE,
                Bukkit.getPlayer(member.playerId) != null,
            )
        }
        val ordered = GuildRoster.order(entries, sort)
        val pages = GuildRoster.pageCount(ordered.size)
        page = page.coerceIn(0, pages - 1)

        pane.addItem(descriptionItem(), 2, 0)
        pane.addItem(levelItem(), 3, 0)
        pane.addItem(guildCard(members, entries.count { it.online }), 4, 0)
        pane.addItem(prestigeItem(), 5, 0)
        pane.addItem(relationsItem(), 6, 0)

        val contributions = if (isMember) runCatching { bankService.getMemberContributions(guild.id) }.getOrDefault(emptyList())
            .associate { it.playerId to it.netContribution } else emptyMap()
        val byId = members.associateBy { it.playerId }
        GuildRoster.page(ordered, page).forEachIndexed { index, entry ->
            val member = byId.getValue(entry.playerId)
            pane.addItem(rosterHead(entry, member, ranks[member.rankId]?.name, contributions[entry.playerId], isMember), index % 9, 1 + index / 9)
        }

        if (page > 0) pane.addItem(navItem("lg_page_prev", lang.gui("guild_page.nav.previous")) { page--; open() }, 0, 5)
        pane.addItem(
            GuiItem(
                ItemStack.of(Material.PAPER)
                    .name(lang.gui("guild_page.nav.page", "page" to page + 1, "pages" to pages))
                    .lore(lang.gui("guild_page.nav.page_count", "count" to ordered.size)),
            ),
            1, 5,
        )
        pane.addItem(sortItem(), 2, 5)
        pane.addItem(navItem("lg_back", lang.gui("guild_page.nav.back")) { menuNavigator.goBack() }, 4, 5)
        if (canManage) {
            pane.addItem(
                navItem("lg_invite", lang.gui("guild_page.nav.invite")) {
                    menuNavigator.openMenu(menuFactory.createGuildInviteMenu(menuNavigator, player, guild))
                },
                6, 5,
            )
            val bans = banService.bans(guild.id).size
            val bansItem = NexoItemProvider.getItemStackOrFallback("lg_ban") { ItemStack.of(Material.BARRIER) }
                .name(lang.gui("guild_page.nav.bans"))
                .lore(lang.gui("guild_page.nav.bans_count", "count" to bans))
            pane.addItem(GuiItem(bansItem) { menuNavigator.openMenu(GuildBannedPlayersMenu(menuNavigator, player, guild)) }, 7, 5)
        }
        if (page < pages - 1) pane.addItem(navItem("lg_page_next", lang.gui("guild_page.nav.next")) { page++; open() }, 8, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun navItem(id: String, title: Component, action: () -> Unit): GuiItem =
        GuiItem(NexoItemProvider.getItemStackOrFallback(id) { ItemStack.of(Material.ARROW) }.name(title)) { action() }

    private fun sortItem(): GuiItem {
        val order = when (sort) {
            RosterSort.RANK -> lang.raw("guild_page.nav.order.rank")
            RosterSort.ONLINE -> lang.raw("guild_page.nav.order.online")
            RosterSort.NAME -> lang.raw("guild_page.nav.order.name")
        }
        val item = NexoItemProvider.getItemStackOrFallback("lg_sort") { ItemStack.of(Material.COMPARATOR) }
            .name(lang.gui("guild_page.nav.sort", "order" to order))
            .lore(lang.gui("guild_page.nav.sort_hint"))
        return GuiItem(item) {
            sort = sort.next()
            page = 0
            open()
        }
    }

    private fun descriptionItem(): GuiItem {
        val rawDescription = guild.description
        val item = NexoItemProvider.getItemStackOrFallback("lg_description") { ItemStack.of(Material.WRITABLE_BOOK) }
            .name(lang.gui("menu.guild_info.description.name"))
        if (rawDescription.isNullOrBlank()) return GuiItem(item.lore(lang.gui("guild_page.description.none")))
        val rendered = GuildDescriptionContent.render(rawDescription)
        item.lore(lang.gui("menu.guild_info.description.value", "description" to rendered))
        if (GuildDescriptionContent.discordInvites(rawDescription).isEmpty()) return GuiItem(item)
        item.lore(lang.gui("menu.guild_info.description.invite_hint"))
        return GuiItem(item) { player.sendMessage(lang.msg("menu.guild_info.description.chat_prefix").append(rendered)) }
    }

    private fun levelItem(): GuiItem = GuiItem(
        NexoItemProvider.getItemStackOrFallback("lg_level") { ItemStack.of(Material.EXPERIENCE_BOTTLE) }
            .name(lang.gui("guild_page.level.name", "level" to guild.level))
            .lore(lang.gui("guild_page.level.members", "limit" to memberService.getMemberLimit(guild.id))),
    )

    private fun guildCard(members: Set<Member>, online: Int): GuiItem {
        val leaderRank = rankService.listRanks(guild.id).minByOrNull { it.priority }
        val leader = members.firstOrNull { it.rankId == leaderRank?.id }?.let { Bukkit.getOfflinePlayer(it.playerId).name }
            ?: lang.raw("menu.guild_info.fallback.unknown_player")
        val item = NexoItemProvider.getItemStackOrFallback("lg_nav_info") { ItemStack.of(Material.NAME_TAG) }
            .name(lang.gui("guild_page.card.name", "guild" to guild.name))
        prestigeLine()?.let(item::lore)
        guild.tag?.let { item.lore(lang.gui("menu.guild_info.overview.tag", "tag" to ColorCodeUtils.toMiniMessage(it))) }
        guild.emoji?.let { item.lore(lang.gui("menu.guild_info.overview.emoji", "emoji" to nexoEmojiService.emojiToFontTag(it))) }
        item.lore(lang.gui("guild_page.card.leader", "leader" to leader))
            .lore(lang.gui("guild_page.card.members", "count" to members.size, "limit" to memberService.getMemberLimit(guild.id), "online" to online))
            .lore(lang.gui("menu.guild_info.overview.mode", "mode" to modeName()))
            .lore(lang.gui("menu.guild_info.overview.founded", "date" to date(guild.createdAt)))
            .lore(lang.gui("guild_page.card.balance", "balance" to bankService.getBalance(guild.id)))
        cardFacts().lines().forEach { (fact, value) -> item.lore(GuildPageText.fact(lang, fact, value)) }
        // REQ-237: wars come last, under their own heading.
        val wars = activeWarLines()
        if (wars.isNotEmpty()) {
            item.lore(lang.gui("menu.common.blank")).lore(lang.gui("menu.guild_info.overview.active_wars"))
            wars.forEach(item::lore)
        }
        return GuiItem(item)
    }

    /** REQ-237: the prestige badge, tier and level under the guild's name; level alone before any prestige. */
    private fun prestigeLine(): net.kyori.adventure.text.Component? {
        val prestige = GuildInfoPrestige.of(prestigeHistory, guild.id) as? GuildInfoPrestige.Prestiged
            ?: return lang.gui("menu.guild_info.overview.level_only", "level" to guild.level)
        val line = lang.gui("menu.guild_info.overview.prestige", "tier" to PrestigeNumeral.of(prestige.current), "level" to guild.level)
        return withBadge(prestige.current, line)
    }

    /** The tier's prestige badge (the same glyph as the tab list), or null when none is configured. */
    private fun badge(tier: Int): net.kyori.adventure.text.Component? {
        val badges = runCatching { configService.loadConfig().progression.prestige.badges }.getOrNull()
            ?: net.lumalyte.lg.config.DEFAULT_PRESTIGE_BADGES
        val raw = net.lumalyte.lg.infrastructure.placeholders.PrestigePlaceholder.badge(tier, badges).takeIf { it.isNotEmpty() } ?: return null
        val mm = net.lumalyte.lg.infrastructure.placeholders.PrestigePlaceholder.withFonts(
            net.lumalyte.lg.utils.ColorCodeUtils.toMiniMessage(raw)) { runCatching { nexoEmojiService.glyphFont(it) }.getOrNull() }
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<!italic><white>$mm</white>")
    }

    /** REQ-237: the badge in front of a prestige line, so tiers line up like the mockup. */
    private fun withBadge(tier: Int, line: net.kyori.adventure.text.Component): net.kyori.adventure.text.Component =
        badge(tier)?.let { net.kyori.adventure.text.Component.text().append(it).append(net.kyori.adventure.text.Component.space()).append(line).build() } ?: line

    private fun cardFacts(): GuildCardFacts = GuildCardFacts(
        home = guild.homes.homeNames.isNotEmpty(),
        banner = guild.banner != null,
        vault = guild.vaultChestLocation != null && guild.vaultStatus == VaultStatus.AVAILABLE,
        shop = guildShopLookup()?.let { lookup -> runCatching { lookup.ownsShop(guild.id) }.getOrNull() },
    )

    private fun guildShopLookup(): GuildShopLookup? =
        runCatching { Bukkit.getServicesManager().load(GuildShopLookup::class.java) }.getOrNull()

    private fun activeWarLines(): List<Component> {
        return warService.getWarsForGuild(guild.id).filter { it.isActive }.map { war ->
            val target = warService.killTargetFor(war)
            val declaring = war.declaringGuildId == guild.id
            val opponentId = if (declaring) war.defendingGuildId else war.declaringGuildId
            val stats = warService.getWarStats(war.id)
            lang.gui(
                "menu.guild_info.statistics.war_kills",
                "opponent" to (guildService.getGuild(opponentId)?.name ?: opponentId.toString().take(8)),
                "kills" to if (declaring) stats.declaringGuildKills else stats.defendingGuildKills,
                "target" to target,
            )
        }
    }

    private fun prestigeItem(): GuiItem {
        val prestige = GuildInfoPrestige.of(prestigeHistory, guild.id)
        val item = PrestigeIcon.item((prestige as? GuildInfoPrestige.Prestiged)?.current ?: 0)
            .name(lang.gui("menu.guild_info.prestige.name"))
        when (prestige) {
            GuildInfoPrestige.None -> item.lore(lang.gui("menu.guild_info.prestige.none"))
            GuildInfoPrestige.Unavailable -> item.lore(lang.gui("menu.guild_info.prestige.unavailable"))
            is GuildInfoPrestige.Prestiged -> {
                item.lore(withBadge(prestige.current, lang.gui("menu.guild_info.prestige.current", "tier" to PrestigeNumeral.of(prestige.current))))
                prestige.entries.forEach { entry ->
                    val tier = PrestigeNumeral.of(entry.tier)
                    val at = date(entry.appliedAt)
                    item.lore(withBadge(entry.tier,
                        if (entry.serverFirst) lang.gui("menu.guild_info.prestige.entry_first", "tier" to tier, "date" to at)
                        else lang.gui("menu.guild_info.prestige.entry", "tier" to tier, "date" to at),
                    ))
                }
            }
        }
        return GuiItem(item)
    }

    private fun relationsItem(): GuiItem {
        val relations = relationService.getGuildRelations(guild.id)
        val allies = GuildInfoRelationResolver.resolve(guild.id, RelationType.ALLY, relations, guildService::getGuild).size
        val enemies = GuildInfoRelationResolver.resolve(guild.id, RelationType.ENEMY, relations, guildService::getGuild).size
        val truces = relations.count {
            it.type == RelationType.TRUCE && it.isActive() && guildService.getGuild(it.getOtherGuild(guild.id)) != null
        }
        val item = NexoItemProvider.getItemStackOrFallback("lg_alliance") { ItemStack.of(Material.FILLED_MAP) }
            .name(lang.gui("guild_page.relations.name"))
            .lore(lang.gui("guild_page.relations.allies", "count" to allies))
            .lore(lang.gui("guild_page.relations.enemies", "count" to enemies))
            .lore(lang.gui("guild_page.relations.truces", "count" to truces))
            .lore(lang.gui("guild_page.relations.hint"))
        return GuiItem(item) { menuNavigator.openMenu(GuildRelationsOverviewMenu(menuNavigator, player, guild)) }
    }

    private fun rosterHead(entry: RosterEntry, member: Member, rankName: String?, contribution: Int?, viewerIsMember: Boolean): GuiItem {
        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(entry.playerId).build())
        head.name(lang.gui("guild_page.roster.name", "player" to entry.name))
            .lore(lang.gui("guild_page.roster.rank", "rank" to RankNameContent.miniMessage(rankName ?: lang.raw("menu.guild_info.fallback.member"))))
            .lore(if (entry.online) lang.gui("guild_page.roster.online") else lastSeenLine(entry.playerId))
            .lore(lang.gui("guild_page.roster.joined", "date" to date(member.joinedAt)))
        if (viewerIsMember && contribution != null) head.lore(lang.gui("guild_page.roster.contribution", "amount" to contribution))
        head.lore(lang.gui("guild_page.roster.open"))
        return GuiItem(head) { menuNavigator.openMenu(GuildMemberPageMenu(menuNavigator, player, guild, entry.playerId)) }
    }

    private fun lastSeenLine(playerId: UUID): Component {
        return GuildPageText.lastSeen(lang, LastSeen.of(System.currentTimeMillis(), Bukkit.getOfflinePlayer(playerId).lastSeen))
    }

    private fun date(at: Instant): String =
        at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(lang.raw("menu.guild_info.overview.date_format")))

    private fun modeName(): Component = when (guild.mode.name) {
        "PEACEFUL" -> lang.gui("menu.guild_info.overview.modes.peaceful")
        else -> lang.gui("menu.guild_info.overview.modes.hostile")
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
