package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.ExperienceSource
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
 * 6-row layout modeled after AuraSkills LevelProgressionMenu.
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

    private var currentPage = 0
    private val itemsPerPage = 24

    /** Source grid slots (AuraSkills track pattern). */
    private val gridSlots = listOf(
        9, 18, 27, 36, 37, 38, 29, 20, 11, 12, 13, 22,
        31, 40, 41, 42, 33, 24, 15, 16, 17, 26, 35, 44
    )

    /** Sources that count toward the daily cap and should appear in the grid. */
    private val trackableSources = ExperienceSource.entries.filter { it != ExperienceSource.WEEKLY_ACTIVITY && it != ExperienceSource.ADMIN_BONUS && it != ExperienceSource.CLAIM_DESTROYED }

    override fun open() {
        val playerId = player.uniqueId

        if (memberService.getMember(playerId, guild.id) == null) {
            player.sendMessage(lang.msg("menu.guild_progression.feedback.no_access"))
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

        val totalPages = (trackableSources.size + itemsPerPage - 1) / itemsPerPage
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
        addGuildLevelHeader(pane, progression)

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
        val dailyXp = progressionService.getDailySourceXp(guild.id)
        val pageSources = trackableSources.drop(currentPage * itemsPerPage).take(itemsPerPage)
        for ((index, source) in pageSources.withIndex()) {
            if (index >= gridSlots.size) break
            val slot = gridSlots[index]
            val x = slot % 9
            val y = slot / 9
            addSourceItem(pane, x, y, source, dailyXp[source] ?: 0)
        }

        gui.show(player)
    }

    private fun repoProgression(): GuildProgressionDisplay? {
        val repo = org.koin.core.context.GlobalContext.get()
            .get<net.lumalyte.lg.application.persistence.ProgressionRepository>()
        val prog = repo.getGuildProgression(guild.id) ?: return null
        val (currentXp, neededXp) = progressionService.getLevelProgress(prog.totalExperience)
        val level = progressionService.getLevelFromExperience(prog.totalExperience)
        val unlockedPerks = progressionService.getUnlockedPerks(guild.id)
        return GuildProgressionDisplay(level, prog.totalExperience, currentXp, neededXp, unlockedPerks.size)
    }

    private fun addGuildLevelHeader(pane: StaticPane, prog: GuildProgressionDisplay) {
        val (_, totalXp, currentXp, neededXp, perksCount) = prog
        val percent = if (neededXp > 0) (currentXp.toDouble() / neededXp.toDouble() * 100).toInt() else 0
        val totalToday = progressionService.getDailySourceXp(guild.id).values.sum()

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

    private fun addSourceItem(pane: StaticPane, x: Int, y: Int, source: ExperienceSource, todayXp: Int) {
        val cap = progressionService.getDailyCap(source)
        val percent = if (cap > 0) (todayXp.toDouble() / cap.toDouble() * 100).toInt().coerceAtMost(100) else 0

        val nexoId = sourceToIconId(source)
        val material = sourceToMaterial(source)
        val name = sourceToDisplayName(source)

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
            if (cap > 0) {
                val progress = when (state) {
                    "capped" -> lang.gui("menu.guild_progression.source.progress.capped", "bar" to bars, "percent" to percent)
                    "near_cap" -> lang.gui("menu.guild_progression.source.progress.near_cap", "bar" to bars, "percent" to percent)
                    "moderate" -> lang.gui("menu.guild_progression.source.progress.moderate", "bar" to bars, "percent" to percent)
                    else -> lang.gui("menu.guild_progression.source.progress.available", "bar" to bars, "percent" to percent)
                }
                lore.add(progress)
                lore.add(lang.gui("menu.guild_progression.source.today", "today" to todayXp, "cap" to cap))
            } else {
                lore.add(lang.gui("menu.guild_progression.source.tracked", "xp" to todayXp))
            }
            meta.lore(lore)
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addRankInfo(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.GOLD_INGOT).also { it.editMeta { meta ->
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

    private fun addPrestigeInfo(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_prestige") {
            ItemStack.of(Material.NETHER_STAR)
        }.also { it.editMeta { meta ->
            meta.displayName(lang.gui("menu.guild_progression.prestige.name"))
            meta.lore(listOf(
                lang.gui("menu.guild_progression.prestige.description"),
                lang.gui("menu.guild_progression.prestige.rewards"),
                lang.gui("menu.guild_progression.prestige.requirement"),
                Component.empty(),
                lang.gui("menu.guild_progression.prestige.coming_soon")
            ))
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
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

    private fun sourceToIconId(source: ExperienceSource): String = when (source) {
        ExperienceSource.BANK_DEPOSIT -> "lg_deposit"
        ExperienceSource.MEMBER_JOINED -> "lg_invite"
        ExperienceSource.WAR_WON -> "lg_war_stats"
        ExperienceSource.WAR_LOST -> "lg_war_stats"
        ExperienceSource.PLAYER_KILL -> "lg_combat"
        ExperienceSource.MOB_KILL -> "lg_combat"
        ExperienceSource.CROP_BREAK -> "lg_farming"
        ExperienceSource.BLOCK_BREAK -> "lg_mining"
        ExperienceSource.BLOCK_PLACE -> "lg_mining"
        ExperienceSource.CRAFTING -> "lg_crafting"
        ExperienceSource.SMELTING -> "lg_crafting"
        ExperienceSource.FISHING -> "lg_farming"
        ExperienceSource.ENCHANTING -> "lg_enchanting"
        ExperienceSource.CLAIM_CREATED -> "lg_claiming"
        ExperienceSource.CLAIM_DESTROYED -> "lg_claiming"
        ExperienceSource.WEEKLY_ACTIVITY -> "lg_reward"
        ExperienceSource.ADMIN_BONUS -> "lg_reward"
    }

    private fun sourceToMaterial(source: ExperienceSource): Material = when (source) {
        ExperienceSource.BANK_DEPOSIT -> Material.GOLD_NUGGET
        ExperienceSource.MEMBER_JOINED -> Material.PLAYER_HEAD
        ExperienceSource.WAR_WON -> Material.DIAMOND_SWORD
        ExperienceSource.WAR_LOST -> Material.STONE_SWORD
        ExperienceSource.PLAYER_KILL -> Material.IRON_SWORD
        ExperienceSource.MOB_KILL -> Material.ROTTEN_FLESH
        ExperienceSource.CROP_BREAK -> Material.WHEAT
        ExperienceSource.BLOCK_BREAK -> Material.STONE_PICKAXE
        ExperienceSource.BLOCK_PLACE -> Material.STONE
        ExperienceSource.CRAFTING -> Material.CRAFTING_TABLE
        ExperienceSource.SMELTING -> Material.FURNACE
        ExperienceSource.FISHING -> Material.FISHING_ROD
        ExperienceSource.ENCHANTING -> Material.ENCHANTING_TABLE
        ExperienceSource.CLAIM_CREATED -> Material.GOLDEN_SHOVEL
        ExperienceSource.CLAIM_DESTROYED -> Material.GOLDEN_SHOVEL
        ExperienceSource.WEEKLY_ACTIVITY -> Material.NETHER_STAR
        ExperienceSource.ADMIN_BONUS -> Material.NETHER_STAR
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
        net.lumalyte.lg.domain.values.PerkType.CUSTOM_BANNER_COLORS -> lang.gui("menu.guild_progression.perks.names.custom_banner_colors")
        net.lumalyte.lg.domain.values.PerkType.ANIMATED_EMOJIS -> lang.gui("menu.guild_progression.perks.names.animated_emojis")
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

