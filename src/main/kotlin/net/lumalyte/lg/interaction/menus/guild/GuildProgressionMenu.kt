package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.inventoryframework.addPane

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.infrastructure.i18n.rewardStatus

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.GuildRewardRead
import net.lumalyte.lg.domain.rewards.RewardOfferStatus
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.values.CapPeriod
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuItemBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Guild Progression Menu — shows guild level, daily XP caps per source, and rewards.
 *
 * 6-row layout with a dedicated sidebar and a row-major source grid.
 *
 * Row 0: [     Guild Level + XP bar + today's total     ][Back][Close]
 * Row 1: [Rank] ─── 24-slot paginated source grid ───────
 * Row 2: [Srcs]
 * Row 3: [Perks]
 * Row 4: [Prestige]
 * Row 5: [                                        ][Prev][Next]
 */
class GuildProgressionMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val progressionService: ProgressionService,
    private val menuFactory: MenuFactory,
    private val menuItemBuilder: MenuItemBuilder,
    private val configService: ConfigService
) : Menu, KoinComponent {

    private val lang: LangService by inject()
    private val rewardPurchases: GuildRewardPurchaseService by inject()
    private val prestigeService: GuildPrestigeService by inject()

    private var currentPage = 0
    private var rewardState: GuildRewardRead = GuildRewardRead.Disabled
    private val itemsPerPage = 24

    /** Six content columns; the sidebar, header and navigation never share these slots. */
    private val gridSlots = listOf(
        11, 12, 13, 14, 15, 16,
        20, 21, 22, 23, 24, 25,
        29, 30, 31, 32, 33, 34,
        38, 39, 40, 41, 42, 43
    )

    override fun open() {
        val playerId = player.uniqueId

        if (memberService.getMember(playerId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.guild_progression.feedback.no_access"))
            menuNavigator.goBack()
            return
        }

        rewardState = progressionService.getRewardState(guild.id)
        if (rewardState == GuildRewardRead.Unavailable) {
            player.sendMessage(lang.msg("chapter_two_rewards.unavailable"))
            menuNavigator.goBack()
            return
        }

        // Fetch fresh progression data
        val progression = progressionService.let {
            repoProgression()
        } ?: run {
            player.sendMessage(lang.msg("menu.guild_progression.feedback.load_failed"))
            menuNavigator.goBack()
            return
        }

        val sourceUsage = progressionService.getSourceUsage(guild.id)
            .filter { it.source != ExperienceSource.ADMIN_BONUS }
        val totalPages = ((sourceUsage.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.guiTitle("menu.guild_progression.title", "page" to currentPage + 1, "pages" to totalPages)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            val click = e.click
            if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ||
                click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
            ) e.isCancelled = true
        }
        gui.addPane(pane)

        // ---- Row 0: Guild level header ----
        addGuildLevelHeader(pane, progression, sourceUsage)

        // ---- Left sidebar (cols 0, rows 1-4) ----
        addRankInfo(pane, 0, 1)
        addSourcesInfo(pane, 0, 2)
        addPerksInfo(pane, 0, 3)
        addPrestigeInfo(pane, 0, 4)

        // ---- Back / Close (top right) ----
        addBackButton(pane, 8, 0)
        addCloseButton(pane, 8, 1)

        // ---- Page navigation (row 5) ----
        if (currentPage > 0) addPreviousPageButton(pane, 7, 5)
        if (currentPage + 1 < totalPages) addNextPageButton(pane, 8, 5)

        // ---- Source grid (paginated) ----
        val pageSources = sourceUsage.drop(currentPage * itemsPerPage).take(itemsPerPage)
        for ((index, usage) in pageSources.withIndex()) {
            if (index >= gridSlots.size) break
            val slot = gridSlots[index]
            val x = slot % 9
            val y = slot / 9
            addSourceItem(pane, x, y, usage)
        }

        gui.show(player)
    }

    private fun repoProgression(): GuildProgressionDisplay? {
        val repo = org.koin.core.context.GlobalContext.get()
            .get<net.lumalyte.lg.application.persistence.ProgressionRepository>()
        val prog = repo.getGuildProgression(guild.id) ?: return null
        val (currentXp, neededXp) = progressionService.getLevelProgress(prog.totalExperience)
        val chapterTwo = rewardState as? GuildRewardRead.Available
        val level = chapterTwo?.level ?: progressionService.getLevelFromExperience(prog.totalExperience)
        val purchasedCount = chapterTwo?.entitlements?.offers?.count {
            it.status == net.lumalyte.lg.domain.rewards.RewardOfferStatus.PURCHASED ||
                it.status == net.lumalyte.lg.domain.rewards.RewardOfferStatus.PERMANENT
        } ?: progressionService.getUnlockedPerks(guild.id).size
        return GuildProgressionDisplay(level, prog.totalExperience, currentXp, neededXp, purchasedCount)
    }

    private fun addGuildLevelHeader(pane: StaticPane, prog: GuildProgressionDisplay, sourceUsage: List<SourceUsageView>) {
        val (_, totalXp, currentXp, neededXp, perksCount) = prog
        val percent = if (neededXp > 0) (currentXp.toDouble() / neededXp.toDouble() * 100).toInt() else 0
        val totalToday = sourceUsage.filter { it.period == CapPeriod.DAILY }.sumOf { it.awardedXp }

        val bars = buildProgressBar(percent, 20)
        val item = NexoItemProvider.getItemStackOrFallback("lg_level") {
            ItemStack.of(Material.EXPERIENCE_BOTTLE)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.level.name", "level" to prog.level))
            val lore = mutableListOf(
                lang.gui("menu.guild_progression.level.progress", "current" to currentXp, "needed" to neededXp, "percent" to percent),
                lang.gui("menu.guild_progression.level.bar", "bar" to bars),
                lang.gui("menu.guild_progression.level.today", "xp" to totalToday),
                Component.empty(),
                lang.gui("menu.guild_progression.level.perks", "count" to perksCount),
                lang.gui("menu.guild_progression.level.total", "xp" to totalXp)
            )
            meta.lore(lore)
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, 4, 0)
    }

    private fun buildProgressBar(percent: Int, length: Int): Component {
        val filled = (percent * length / 100).coerceIn(0, length)
        val empty = length - filled
        val color = when {
            percent >= 100 -> "green"
            percent >= 70 -> "yellow"
            percent >= 40 -> "gold"
            else -> "red"
        }
        val filledBar = "█".repeat(filled)
        val emptyBar = "█".repeat(empty)
        return when (color) {
            "green" -> lang.gui("menu.guild_progression.bar.green", "filled" to filledBar, "empty" to emptyBar)
            "yellow" -> lang.gui("menu.guild_progression.bar.yellow", "filled" to filledBar, "empty" to emptyBar)
            "gold" -> lang.gui("menu.guild_progression.bar.gold", "filled" to filledBar, "empty" to emptyBar)
            else -> lang.gui("menu.guild_progression.bar.red", "filled" to filledBar, "empty" to emptyBar)
        }
    }

    private fun addSourceItem(pane: StaticPane, x: Int, y: Int, usage: SourceUsageView) {
        val source = usage.source
        val cap = usage.capXp
        val usedXp = usage.awardedXp
        val percent = if (cap != null && cap > 0) (usedXp.toDouble() / cap.toDouble() * 100).toInt().coerceAtMost(100) else 0

        val nexoId = sourceToPresentationIconId(source, usage.pool)
        val material = sourceToMaterial(source)
        val name = sourcePoolDisplayName(usage.pool)

        val bars = buildProgressBar(percent, 10)
        val state = when {
            percent >= 100 -> "capped"
            percent >= 80 -> "near_cap"
            percent >= 40 -> "moderate"
            else -> "available"
        }

        val item = NexoItemProvider.getItemStackOrFallback(nexoId) {
            ItemStack.of(material)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.source.name", "source" to name))
            val lore = mutableListOf<Component>()
            if (cap != null) {
                val progress = when (state) {
                    "capped" -> lang.gui("menu.guild_progression.source.progress.capped", "bar" to bars, "percent" to percent)
                    "near_cap" -> lang.gui("menu.guild_progression.source.progress.near_cap", "bar" to bars, "percent" to percent)
                    "moderate" -> lang.gui("menu.guild_progression.source.progress.moderate", "bar" to bars, "percent" to percent)
                    else -> lang.gui("menu.guild_progression.source.progress.available", "bar" to bars, "percent" to percent)
                }
                lore.add(progress)
                lore.add(lang.gui("menu.guild_progression.source.today", "today" to usedXp, "cap" to cap))
            } else {
                lore.add(lang.gui("menu.guild_progression.source.tracked", "xp" to usedXp))
            }
            meta.lore(lore)
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addRankInfo(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_nav_ranks") {
            ItemStack.of(Material.GOLD_INGOT)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.rank.name"))
            meta.lore(listOf(lang.gui("menu.guild_progression.rank.description"), lang.gui("menu.guild_progression.rank.scope")))
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addSourcesInfo(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_sources") {
            ItemStack.of(Material.BOOK)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.sources.name"))
            meta.lore(listOf(
                lang.gui("menu.guild_progression.sources.description"),
                lang.gui("menu.guild_progression.sources.bank"),
                lang.gui("menu.guild_progression.sources.war"),
                lang.gui("menu.guild_progression.sources.invites"),
                lang.gui("menu.guild_progression.sources.kills"),
                lang.gui("menu.guild_progression.sources.farming"),
                lang.gui("menu.guild_progression.sources.mining"),
                lang.gui("menu.guild_progression.sources.crafting"),
                lang.gui("menu.guild_progression.sources.brewing"),
                lang.gui("menu.guild_progression.sources.enchanting"),
                lang.gui("menu.guild_progression.sources.claiming")
            ))
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addPerksInfo(pane: StaticPane, x: Int, y: Int) {
        val chapterTwo = rewardState as? GuildRewardRead.Available
        if (chapterTwo != null) {
            val item = ItemStack.of(Material.DIAMOND).also { it.editMeta { meta ->
                meta.displayName(lang.gui("chapter_two_rewards.title"))
                meta.lore(listOf(lang.gui("chapter_two_rewards.explanation"), lang.gui("chapter_two_rewards.view")))
            } }
            pane.addItem(GuiItem(item) { event -> event.isCancelled = true; openRewardCatalog() }, x, y)
            return
        }
        val perks = progressionService.getUnlockedPerks(guild.id)
        val item = NexoItemProvider.getItemStackOrFallback("lg_reward") {
            ItemStack.of(Material.DIAMOND)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.perks.name"))
            val lore = mutableListOf<Component>()
            if (perks.isEmpty()) {
                lore.add(lang.gui("menu.guild_progression.perks.none"))
                lore.add(lang.gui("menu.guild_progression.perks.hint"))
            } else {
                for (perk in perks) {
                    lore.add(lang.gui("menu.guild_progression.perks.entry", "perk" to perkToDisplayName(perk)))
                }
            }
            meta.lore(lore)
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun openRewardCatalog() {
        if (memberService.getMember(player.uniqueId, guild.id) == null) return
        val state = progressionService.getRewardState(guild.id) as? GuildRewardRead.Available
        if (state == null) {
            player.sendMessage(lang.msg("chapter_two_rewards.unavailable"))
            return
        }
        val gui = ChestGui(6, lang.guiTitle("chapter_two_rewards.title"))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnGlobalClick { it.isCancelled = true }
        state.entitlements.offers.forEachIndexed { index, offer ->
            val item = ItemStack.of(Material.BOOK).also { it.editMeta { meta ->
                meta.displayName(lang.gui("chapter_two_rewards.name", "reward" to offer.reward.name))
                meta.lore(buildList {
                    add(lang.gui("chapter_two_rewards.level_price", "level" to offer.reward.level, "price" to offer.reward.price))
                    add(lang.rewardStatus(offer.status))
                    if (offer.status == RewardOfferStatus.AVAILABLE)
                        add(lang.gui("chapter_two_rewards.purchase.select"))
                })
            } }
            pane.addItem(GuiItem(item) {
                it.isCancelled = true
                if (offer.status != RewardOfferStatus.AVAILABLE) return@GuiItem
                val quote = rewardPurchases.quote(player.uniqueId, guild.id, offer.reward.id)
                if (quote == null) player.sendMessage(lang.msg("chapter_two_rewards.purchase.no_quote"))
                else GuildRewardPurchaseMenu(player, quote, offer.reward.name, ::openRewardCatalog).open()
            }, index % 9, index / 9)
        }
        val benefits = state.entitlements
        val summary = ItemStack.of(Material.GOLD_INGOT).also { it.editMeta { meta ->
            meta.displayName(lang.gui("chapter_two_rewards.title"))
            meta.lore(listOf(
                lang.gui("chapter_two_rewards.capacity_bank", "capacity" to benefits.bankCapacity),
                lang.gui("chapter_two_rewards.capacity_home_member", "homes" to benefits.homeCapacity, "members" to benefits.memberCapacity),
                lang.gui("chapter_two_rewards.multipliers", "cooldown" to benefits.homeCooldownMultiplier, "fee" to benefits.withdrawalFeeMultiplier)))
        } }
        pane.addItem(GuiItem(summary) { it.isCancelled = true }, 4, 5)
        val back = ItemStack.of(Material.ARROW).name(lang.gui("chapter_two_rewards.back"))
        pane.addItem(GuiItem(back) { it.isCancelled = true; open() }, 8, 5)
        gui.addPane(pane)
        gui.show(player)
    }

    private fun addPrestigeInfo(pane: StaticPane, x: Int, y: Int) {
        val overview = prestigeService.overview(guild.id)
        val item = NexoItemProvider.getItemStackOrFallback("lg_prestige") {
            ItemStack.of(Material.NETHER_STAR)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.prestige.name"))
            val lore = mutableListOf<Component>(
                lang.gui("menu.guild_progression.prestige.description"),
                lang.gui("menu.guild_progression.prestige.rewards"),
                lang.gui("menu.guild_progression.prestige.requirement"),
                Component.empty(),
            )
            when {
                overview == null -> lore += lang.gui("menu.guild_progression.prestige.state.unavailable")
                !overview.enabled -> lore += lang.gui("menu.guild_progression.prestige.state.disabled")
                overview.prestigeCount >= overview.maxPrestigeCount ->
                    lore += lang.gui(
                        "menu.guild_progression.prestige.state.maximum",
                        "count" to overview.prestigeCount,
                        "max" to overview.maxPrestigeCount,
                    )
                overview.currentLevel < 100 -> lore += lang.gui(
                    "menu.guild_progression.prestige.state.level",
                    "level" to overview.currentLevel,
                )
                overview.choices.isEmpty() ->
                    lore += lang.gui("menu.guild_progression.prestige.state.no_choices")
                else -> {
                    lore += lang.gui(
                        "menu.guild_progression.prestige.state.ready",
                        "count" to overview.prestigeCount,
                        "max" to overview.maxPrestigeCount,
                        "fee" to (overview.nextFee ?: 0L),
                        "choices" to overview.choices.size,
                    )
                    lore += lang.gui("menu.guild_progression.prestige.action")
                }
            }
            meta.lore(lore)
        }}
        pane.addItem(GuiItem(item) { event ->
            event.isCancelled = true
            val fresh = prestigeService.overview(guild.id) ?: return@GuiItem
            if (fresh.enabled && fresh.currentLevel == 100 &&
                fresh.prestigeCount < fresh.maxPrestigeCount && fresh.choices.isNotEmpty()
            ) {
                openPrestigeSelection()
            }
        }, x, y)
    }

    private fun openPrestigeSelection() {
        val overview = prestigeService.overview(guild.id)
        if (overview == null || !overview.enabled || overview.currentLevel != 100 ||
            overview.prestigeCount >= overview.maxPrestigeCount || overview.choices.isEmpty()
        ) {
            open()
            return
        }

        val gui = ChestGui(3, lang.guiTitle("menu.guild_progression.prestige.selection.title"))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnGlobalClick { it.isCancelled = true }

        overview.choices.take(18).forEachIndexed { index, reward ->
            val item = NexoItemProvider.getItemStackOrFallback("lg_reward") {
                ItemStack.of(Material.DIAMOND)
            }.also { stack -> stack.editMeta { meta ->
                meta.displayName(lang.gui(
                    "menu.guild_progression.prestige.selection.reward.name",
                    "reward" to reward.name,
                ))
                meta.lore(listOf(
                    lang.gui("menu.guild_progression.prestige.selection.reward.level", "level" to reward.level),
                    lang.gui("menu.guild_progression.prestige.selection.reward.retain"),
                    lang.gui(
                        "menu.guild_progression.prestige.selection.reward.fee",
                        "fee" to (overview.nextFee ?: 0L),
                    ),
                    Component.empty(),
                    lang.gui("menu.guild_progression.prestige.selection.reward.action"),
                ))
            } }
            pane.addItem(GuiItem(item) { event ->
                event.isCancelled = true
                val quote = prestigeService.quote(player.uniqueId, guild.id, reward.id)
                if (quote == null) {
                    player.sendMessage(lang.msg("menu.guild_progression.prestige.feedback.quote_failed"))
                    open()
                } else {
                    GuildPrestigeConfirmationMenu(player, quote, reward.name, ::openPrestigeSelection, ::open).open()
                }
            }, index % 9, index / 9)
        }

        val back = NexoItemProvider.getItemStackOrFallback("lg_back") {
            ItemStack.of(Material.ARROW)
        }.name(lang.gui("menu.guild_progression.prestige.selection.back"))
        pane.addItem(GuiItem(back) { event ->
            event.isCancelled = true
            open()
        }, 8, 2)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
            ItemStack.of(Material.ARROW).name(lang.gui("menu.guild_progression.navigation.back_fallback"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.back")) }}
        pane.addItem(GuiItem(item) { menuNavigator.goBack() }, x, y)
    }

    private fun addCloseButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_close") {
            ItemStack.of(Material.BARRIER).name(lang.gui("menu.guild_progression.navigation.close"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.close")) }}
        pane.addItem(GuiItem(item) { menuNavigator.clearMenuStack(); player.closeInventory() }, x, y)
    }

    private fun addPreviousPageButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
            ItemStack.of(Material.ARROW).name(lang.gui("menu.guild_progression.navigation.previous_fallback"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.previous")) }}
        pane.addItem(GuiItem(item) { currentPage--; open() }, x, y)
    }

    private fun addNextPageButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_next") {
            ItemStack.of(Material.ARROW).name(lang.gui("menu.guild_progression.navigation.next_fallback"))
        }.also { it.editMeta { meta -> meta.displayName(lang.gui("menu.guild_progression.navigation.next")) }}
        pane.addItem(GuiItem(item) { currentPage++; open() }, x, y)
    }

    private fun sourceToPresentationIconId(source: ExperienceSource, pool: String): String = when (pool) {
        "ORE" -> "lg_ore"
        "CRAFTING" -> "lg_crafting"
        else -> sourceToIconId(source)
    }

    private fun sourceToIconId(source: ExperienceSource): String = when (source) {
        ExperienceSource.BANK_DEPOSIT -> "lg_deposit"
        ExperienceSource.MEMBER_JOINED, ExperienceSource.QUALIFIED_RECRUIT -> "lg_qualified_recruit"
        ExperienceSource.WAR_WON, ExperienceSource.PRE_CAP_WAR_WIN -> "lg_war_victory"
        ExperienceSource.WAR_LOST -> "lg_war_defeat"
        ExperienceSource.PLAYER_KILL, ExperienceSource.MOB_KILL -> "lg_combat"
        ExperienceSource.CROP_BREAK -> "lg_farming"
        ExperienceSource.BLOCK_BREAK -> "lg_mining"
        ExperienceSource.BLOCK_PLACE -> "lg_block_place"
        ExperienceSource.CRAFTING -> "lg_crafting"
        ExperienceSource.SMELTING -> "lg_smelting"
        ExperienceSource.FISHING -> "lg_fishing"
        ExperienceSource.ENCHANTING -> "lg_enchanting"
        ExperienceSource.BREWING -> "lg_brewing"
        ExperienceSource.EXPLORATION_MILESTONE -> "lg_exploration"
        ExperienceSource.COAL_ORE,
        ExperienceSource.COPPER_ORE,
        ExperienceSource.IRON_ORE,
        ExperienceSource.LAPIS_ORE,
        ExperienceSource.REDSTONE_ORE,
        ExperienceSource.GOLD_ORE,
        ExperienceSource.NETHER_QUARTZ_ORE,
        ExperienceSource.DIAMOND_ORE,
        ExperienceSource.EMERALD_ORE,
        ExperienceSource.ANCIENT_DEBRIS -> "lg_ore"
        ExperienceSource.CRAFT_COMMON -> "lg_craft_common"
        ExperienceSource.CRAFT_UTILITY -> "lg_craft_utility"
        ExperienceSource.CRAFT_EQUIPMENT -> "lg_craft_equipment"
        ExperienceSource.CRAFT_RARE -> "lg_craft_rare"
        ExperienceSource.ENDER_DRAGON_KILL -> "lg_ender_dragon"
        ExperienceSource.WITHER_KILL -> "lg_wither"
        ExperienceSource.ELDER_GUARDIAN_KILL -> "lg_elder_guardian"
        ExperienceSource.WARDEN_KILL -> "lg_warden"
        ExperienceSource.CLAIM_CREATED -> "lg_claiming"
        ExperienceSource.CLAIM_DESTROYED -> "lg_claim_removed"
        ExperienceSource.WEEKLY_ACTIVITY -> "lg_weekly_activity"
        ExperienceSource.ADMIN_BONUS -> "lg_admin_bonus"
    }

    private fun sourceToMaterial(source: ExperienceSource): Material = when (source) {
        ExperienceSource.BANK_DEPOSIT -> Material.GOLD_NUGGET
        ExperienceSource.MEMBER_JOINED, ExperienceSource.QUALIFIED_RECRUIT -> Material.PLAYER_HEAD
        ExperienceSource.WAR_WON, ExperienceSource.PRE_CAP_WAR_WIN -> Material.DIAMOND_SWORD
        ExperienceSource.WAR_LOST -> Material.STONE_SWORD
        ExperienceSource.PLAYER_KILL -> Material.IRON_SWORD
        ExperienceSource.MOB_KILL -> Material.ROTTEN_FLESH
        ExperienceSource.CROP_BREAK -> Material.WHEAT
        ExperienceSource.BLOCK_BREAK -> Material.STONE_PICKAXE
        ExperienceSource.BLOCK_PLACE -> Material.STONE
        ExperienceSource.CRAFTING,
        ExperienceSource.CRAFT_COMMON,
        ExperienceSource.CRAFT_UTILITY,
        ExperienceSource.CRAFT_EQUIPMENT,
        ExperienceSource.CRAFT_RARE -> Material.CRAFTING_TABLE
        ExperienceSource.SMELTING -> Material.FURNACE
        ExperienceSource.FISHING -> Material.FISHING_ROD
        ExperienceSource.ENCHANTING -> Material.ENCHANTING_TABLE
        ExperienceSource.BREWING -> Material.BREWING_STAND
        ExperienceSource.EXPLORATION_MILESTONE -> Material.COMPASS
        ExperienceSource.COAL_ORE -> Material.COAL_ORE
        ExperienceSource.COPPER_ORE -> Material.COPPER_ORE
        ExperienceSource.IRON_ORE -> Material.IRON_ORE
        ExperienceSource.LAPIS_ORE -> Material.LAPIS_ORE
        ExperienceSource.REDSTONE_ORE -> Material.REDSTONE_ORE
        ExperienceSource.GOLD_ORE -> Material.GOLD_ORE
        ExperienceSource.NETHER_QUARTZ_ORE -> Material.NETHER_QUARTZ_ORE
        ExperienceSource.DIAMOND_ORE -> Material.DIAMOND_ORE
        ExperienceSource.EMERALD_ORE -> Material.EMERALD_ORE
        ExperienceSource.ANCIENT_DEBRIS -> Material.ANCIENT_DEBRIS
        ExperienceSource.ENDER_DRAGON_KILL -> Material.DRAGON_HEAD
        ExperienceSource.WITHER_KILL -> Material.NETHER_STAR
        ExperienceSource.ELDER_GUARDIAN_KILL -> Material.PRISMARINE_SHARD
        ExperienceSource.WARDEN_KILL -> Material.ECHO_SHARD
        ExperienceSource.CLAIM_CREATED, ExperienceSource.CLAIM_DESTROYED -> Material.GOLDEN_SHOVEL
        ExperienceSource.WEEKLY_ACTIVITY, ExperienceSource.ADMIN_BONUS -> Material.NETHER_STAR
    }

    private fun sourceToDisplayName(source: ExperienceSource): Component = when (source) {
        ExperienceSource.BANK_DEPOSIT -> lang.gui("menu.guild_progression.source.names.bank_deposit")
        ExperienceSource.MEMBER_JOINED -> lang.gui("menu.guild_progression.source.names.member_joined")
        ExperienceSource.WAR_WON -> lang.gui("menu.guild_progression.source.names.war_won")
        ExperienceSource.WAR_LOST -> lang.gui("menu.guild_progression.source.names.war_lost")
        ExperienceSource.PLAYER_KILL -> lang.gui("menu.guild_progression.source.names.player_kill")
        ExperienceSource.MOB_KILL -> lang.gui("menu.guild_progression.source.names.mob_kill")
        ExperienceSource.CROP_BREAK -> lang.gui("menu.guild_progression.source.names.crop_break")
        ExperienceSource.BLOCK_BREAK -> lang.gui("menu.guild_progression.source.names.block_break")
        ExperienceSource.BLOCK_PLACE -> lang.gui("menu.guild_progression.source.names.block_place")
        ExperienceSource.CRAFTING -> lang.gui("menu.guild_progression.source.names.crafting")
        ExperienceSource.SMELTING -> lang.gui("menu.guild_progression.source.names.smelting")
        ExperienceSource.FISHING -> lang.gui("menu.guild_progression.source.names.fishing")
        ExperienceSource.ENCHANTING -> lang.gui("menu.guild_progression.source.names.enchanting")
        ExperienceSource.CLAIM_CREATED -> lang.gui("menu.guild_progression.source.names.claim_created")
        ExperienceSource.CLAIM_DESTROYED -> lang.gui("menu.guild_progression.source.names.claim_destroyed")
        ExperienceSource.WEEKLY_ACTIVITY -> lang.gui("menu.guild_progression.source.names.weekly_activity")
        ExperienceSource.ADMIN_BONUS -> lang.gui("menu.guild_progression.source.names.admin_bonus")
        else -> Component.text(source.name.lowercase().replace('_', ' '))
    }

    private fun perkToDisplayName(perk: net.lumalyte.lg.domain.values.PerkType): Component = when (perk) {
        net.lumalyte.lg.domain.values.PerkType.HIGHER_BANK_BALANCE -> lang.gui("menu.guild_progression.perks.names.higher_bank_balance")
        net.lumalyte.lg.domain.values.PerkType.BANK_INTEREST -> lang.gui("menu.guild_progression.perks.names.bank_interest")
        net.lumalyte.lg.domain.values.PerkType.INCREASED_BANK_LIMIT -> lang.gui("menu.guild_progression.perks.names.increased_bank_limit")
        net.lumalyte.lg.domain.values.PerkType.REDUCED_WITHDRAWAL_FEES -> lang.gui("menu.guild_progression.perks.names.reduced_withdrawal_fees")
        net.lumalyte.lg.domain.values.PerkType.ADDITIONAL_HOMES -> lang.gui("menu.guild_progression.perks.names.additional_homes")
        net.lumalyte.lg.domain.values.PerkType.TELEPORT_COOLDOWN_REDUCTION -> lang.gui("menu.guild_progression.perks.names.teleport_cooldown_reduction")
        net.lumalyte.lg.domain.values.PerkType.HOME_TELEPORT_SOUND_EFFECTS -> lang.gui("menu.guild_progression.perks.names.home_teleport_sound_effects")
        net.lumalyte.lg.domain.values.PerkType.SPECIAL_PARTICLES -> lang.gui("menu.guild_progression.perks.names.special_particles")
        net.lumalyte.lg.domain.values.PerkType.ANNOUNCEMENT_SOUND_EFFECTS -> lang.gui("menu.guild_progression.perks.names.announcement_sound_effects")
        net.lumalyte.lg.domain.values.PerkType.WAR_DECLARATION_SOUND_EFFECTS -> lang.gui("menu.guild_progression.perks.names.war_declaration_sound_effects")
        net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_BLOCKS -> lang.gui("menu.guild_progression.perks.names.increased_claim_blocks")
        net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_COUNT -> lang.gui("menu.guild_progression.perks.names.increased_claim_count")
        net.lumalyte.lg.domain.values.PerkType.FASTER_CLAIM_REGEN -> lang.gui("menu.guild_progression.perks.names.faster_claim_regen")
        net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS -> lang.gui("menu.guild_progression.perks.names.ally_home_access")
    }

    private data class GuildProgressionDisplay(
        val level: Int,
        val totalXp: Int,
        val currentXp: Int,
        val neededXp: Int,
        val unlockedPerks: Int
    )
}

internal fun sourcePoolDisplayName(pool: String): Component =
    Component.text(pool.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })

