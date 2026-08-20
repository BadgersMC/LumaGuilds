package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder

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
) : Menu {

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
            player.sendMessage("§c❌ You cannot access this menu!")
            menuNavigator.goBack()
            return
        }

        // Fetch fresh progression data
        val progression = progressionService.let {
            repoProgression()
        } ?: run {
            player.sendMessage("§c❌ Could not load progression data.")
            menuNavigator.goBack()
            return
        }

        val totalPages = (trackableSources.size + itemsPerPage - 1) / itemsPerPage
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, "§0§8⭐ §7Guild Progression §8• Page ${currentPage + 1}/$totalPages"))
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
            meta.setDisplayName("§6§l⭐ Guild Level ${prog.level}")
            val lore = mutableListOf(
                "§7XP: §e$currentXp §7/ §e$neededXp  §8($percent%)",
                "§7$bars",
                "§7Today: §e+$totalToday XP",
                "",
                "§7Unlocked Perks: §a$perksCount",
                "§7Total XP: §e${totalXp}"
            )
            meta.lore = lore
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, 4, 0)
    }

    private fun buildProgressBar(percent: Int, length: Int): String {
        val filled = (percent * length / 100).coerceIn(0, length)
        val empty = length - filled
        val color = when {
            percent >= 100 -> "§a"
            percent >= 70 -> "§e"
            percent >= 40 -> "§6"
            else -> "§c"
        }
        return "$color${"█".repeat(filled)}§7${"█".repeat(empty)}"
    }

    private fun addSourceItem(pane: StaticPane, x: Int, y: Int, source: ExperienceSource, todayXp: Int) {
        val cap = progressionService.getDailyCap(source)
        val percent = if (cap > 0) (todayXp.toDouble() / cap.toDouble() * 100).toInt().coerceAtMost(100) else 0

        val nexoId = sourceToIconId(source)
        val material = sourceToMaterial(source)
        val name = sourceToDisplayName(source)

        val bars = buildProgressBar(percent, 10)
        val color = when {
            percent >= 100 -> "§c" // capped
            percent >= 80 -> "§e" // near cap
            percent >= 40 -> "§6" // moderate
            else -> "§a" // plenty of room
        }

        val item = NexoItemProvider.getItemStackOrFallback(nexoId) {
            ItemStack.of(material)
        }.also { it.editMeta { meta ->
            meta.setDisplayName("§f$name")
            val lore = mutableListOf<String>()
            if (cap > 0) {
                lore.add("$color$bars §7$percent%")
                lore.add("§7Today: §e$todayXp §7/ §e$cap §7max")
            } else {
                lore.add("§7Tracked: §e$todayXp XP")
            }
            meta.lore = lore
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addRankInfo(pane: StaticPane, x: Int, y: Int) {
        val item = ItemStack.of(Material.GOLD_INGOT).also { it.editMeta { meta ->
            meta.setDisplayName("§6Guild Rank")
            meta.lore = listOf("§7Your guild's standing", "§7among all guilds")
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addSourcesInfo(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_sources") {
            ItemStack.of(Material.BOOK)
        }.also { it.editMeta { meta ->
            meta.setDisplayName("§eXP Sources")
            meta.lore = listOf(
                "§7How to earn guild XP:",
                "§7• §f💰 Bank deposits",
                "§7• §f⚔ War victories",
                "§7• §f👥 Member invites",
                "§7• §f🗡 Player/mob kills",
                "§7• §f🌾 Farming & fishing",
                "§7• §f⛏ Mining & building",
                "§7• §f🔨 Crafting & smelting",
                "§7• §f🧪 Brewing & enchanting",
                "§7• §f✨ Enchanting",
                "§7• §f🏞 Claiming land"
            )
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addPerksInfo(pane: StaticPane, x: Int, y: Int) {
        val perks = progressionService.getUnlockedPerks(guild.id)
        val item = NexoItemProvider.getItemStackOrFallback("lg_reward") {
            ItemStack.of(Material.DIAMOND)
        }.also { it.editMeta { meta ->
            meta.setDisplayName("§aUnlocked Perks")
            val lore = mutableListOf<String>()
            if (perks.isEmpty()) {
                lore.add("§7No perks unlocked yet")
                lore.add("§7Earn XP to level up!")
            } else {
                for (perk in perks) {
                    lore.add("§a✓ §f${perkToDisplayName(perk)}")
                }
            }
            meta.lore = lore
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addPrestigeInfo(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_prestige") {
            ItemStack.of(Material.NETHER_STAR)
        }.also { it.editMeta { meta ->
            meta.setDisplayName("§dPrestige")
            meta.lore = listOf(
                "§7Reset your guild's level",
                "§7for exclusive rewards.",
                "§7Requires Level §d25",
                "",
                "§8Coming in a future update"
            )
        }}
        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
            ItemStack.of(Material.ARROW).name("§7Back")
        }.also { it.editMeta { meta -> meta.setDisplayName("§a← Back") }}
        pane.addItem(GuiItem(item) { menuNavigator.goBack() }, x, y)
    }

    private fun addCloseButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_close") {
            ItemStack.of(Material.BARRIER).name("§cClose")
        }.also { it.editMeta { meta -> meta.setDisplayName("§cClose") }}
        pane.addItem(GuiItem(item) { menuNavigator.clearMenuStack(); player.closeInventory() }, x, y)
    }

    private fun addPreviousPageButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_prev") {
            ItemStack.of(Material.ARROW).name("§e← Previous")
        }.also { it.editMeta { meta -> meta.setDisplayName("§e← Previous Page") }}
        pane.addItem(GuiItem(item) { currentPage--; open() }, x, y)
    }

    private fun addNextPageButton(pane: StaticPane, x: Int, y: Int) {
        val item = NexoItemProvider.getItemStackOrFallback("lg_page_next") {
            ItemStack.of(Material.ARROW).name("§eNext →")
        }.also { it.editMeta { meta -> meta.setDisplayName("§eNext Page →") }}
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

    private fun sourceToDisplayName(source: ExperienceSource): String = when (source) {
        ExperienceSource.BANK_DEPOSIT -> "Bank Deposits"
        ExperienceSource.MEMBER_JOINED -> "Member Invites"
        ExperienceSource.WAR_WON -> "War Victories"
        ExperienceSource.WAR_LOST -> "War Participation"
        ExperienceSource.PLAYER_KILL -> "Player Kills"
        ExperienceSource.MOB_KILL -> "Mob Kills"
        ExperienceSource.CROP_BREAK -> "Farming"
        ExperienceSource.BLOCK_BREAK -> "Mining"
        ExperienceSource.BLOCK_PLACE -> "Building"
        ExperienceSource.CRAFTING -> "Crafting"
        ExperienceSource.SMELTING -> "Smelting"
        ExperienceSource.FISHING -> "Fishing"
        ExperienceSource.ENCHANTING -> "Enchanting"
        ExperienceSource.CLAIM_CREATED -> "Claiming Land"
        ExperienceSource.CLAIM_DESTROYED -> "Claims"
        ExperienceSource.WEEKLY_ACTIVITY -> "Weekly Activity"
        ExperienceSource.ADMIN_BONUS -> "Admin Bonus"
    }

    private fun perkToDisplayName(perk: net.lumalyte.lg.domain.values.PerkType): String = when (perk) {
        net.lumalyte.lg.domain.values.PerkType.HIGHER_BANK_BALANCE -> "Higher Bank Limit"
        net.lumalyte.lg.domain.values.PerkType.BANK_INTEREST -> "Bank Interest"
        net.lumalyte.lg.domain.values.PerkType.INCREASED_BANK_LIMIT -> "Increased Bank Limit"
        net.lumalyte.lg.domain.values.PerkType.REDUCED_WITHDRAWAL_FEES -> "Reduced Fees"
        net.lumalyte.lg.domain.values.PerkType.ADDITIONAL_HOMES -> "Extra Homes"
        net.lumalyte.lg.domain.values.PerkType.TELEPORT_COOLDOWN_REDUCTION -> "Faster Teleports"
        net.lumalyte.lg.domain.values.PerkType.HOME_TELEPORT_SOUND_EFFECTS -> "Home Teleport SFX"
        net.lumalyte.lg.domain.values.PerkType.SPECIAL_PARTICLES -> "Particle Effects"
        net.lumalyte.lg.domain.values.PerkType.ANNOUNCEMENT_SOUND_EFFECTS -> "Announcement SFX"
        net.lumalyte.lg.domain.values.PerkType.WAR_DECLARATION_SOUND_EFFECTS -> "War Declaration SFX"
        net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_BLOCKS -> "More Claim Blocks"
        net.lumalyte.lg.domain.values.PerkType.INCREASED_CLAIM_COUNT -> "More Claims"
        net.lumalyte.lg.domain.values.PerkType.FASTER_CLAIM_REGEN -> "Faster Claim Regen"
        net.lumalyte.lg.domain.values.PerkType.CUSTOM_BANNER_COLORS -> "Custom Banner Colors"
        net.lumalyte.lg.domain.values.PerkType.ANIMATED_EMOJIS -> "Animated Emojis"
        net.lumalyte.lg.domain.values.PerkType.ALLY_HOME_ACCESS -> "Ally Home Access"
    }

    private data class GuildProgressionDisplay(
        val level: Int,
        val totalXp: Int,
        val currentXp: Int,
        val neededXp: Int,
        val unlockedPerks: Int
    )
}