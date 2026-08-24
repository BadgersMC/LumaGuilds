package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.utils.MenuTitleBuilder
import net.badgersmc.nexus.i18n.LangService

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.PenaltyService
import net.lumalyte.lg.application.services.StrikeService
import net.lumalyte.lg.config.StrikesConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.PenaltyType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.GuildResolver
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Admin penalty GUI — shows a guild's strike ledger and offers the four
 * penalty actions (Level Reduction, EXP Reduction, Guild Mute, Disband),
 * each gated behind a confirmation menu.
 *
 * Permission: lumaguilds.admin.strikes (checked by the command).
 */
class GuildStrikePenaltyMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val strikesConfig: StrikesConfig,
) : Menu, KoinComponent {

    private val strikeService: StrikeService by inject()
    private val penaltyService: PenaltyService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val guildName = GuildResolver.displayName(guild)
        val gui = ChestGui(6, MenuTitleBuilder.build(guild.guiTheme, 6, lang.legacy("menu.guild_strike_penalty.title", "guild" to guildName)))
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { event -> event.isCancelled = true }
        gui.setOnBottomClick { event ->
            if (event.click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ||
                event.click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
            ) {
                event.isCancelled = true
            }
        }

        // Guild info header (row 0)
        val infoItem = ItemStack.of(Material.NAME_TAG)
            .name(lang.legacy("menu.guild_strike_penalty.guild.name", "guild" to guildName))
            .lore(lang.legacy("menu.guild_strike_penalty.guild.level", "level" to guild.level))
            .lore(lang.legacy("menu.guild_strike_penalty.guild.strikes", "count" to strikeService.countByGuild(guild.id)))
            .lore(lang.legacy("menu.guild_strike_penalty.guild.threshold", "threshold" to strikesConfig.threshold))
        pane.addItem(GuiItem(infoItem), 0, 0)

        // Strike ledger (rows 0-2, slots 1-7). Newest strikes on the left.
        val strikes = strikeService.getByGuild(guild.id).take(7)
        strikes.forEachIndexed { index, strike ->
            val material = when (strike.punishmentType.uppercase()) {
                "BAN" -> Material.BARRIER
                "MUTE" -> Material.WHITE_WOOL
                "KICK" -> Material.LEATHER_BOOTS
                else -> Material.PAPER
            }
            val item = ItemStack.of(material)
                .name(
                    if (strike.active) {
                        lang.legacy("menu.guild_strike_penalty.strike.name.active", "type" to strike.punishmentType.uppercase())
                    } else {
                        lang.legacy("menu.guild_strike_penalty.strike.name.lifted", "type" to strike.punishmentType.uppercase())
                    }
                )
                .lore(lang.legacy("menu.guild_strike_penalty.strike.player", "player" to (strike.playerName ?: strike.playerUuid.toString().take(8))))
                .lore(lang.legacy("menu.guild_strike_penalty.strike.reason", "reason" to (strike.reason?.take(60) ?: lang.raw("menu.guild_strike_penalty.fallback.no_reason"))))
                .lore(lang.legacy("menu.guild_strike_penalty.strike.executor", "executor" to (strike.executorName ?: lang.raw("menu.guild_strike_penalty.fallback.unknown_executor")), "date" to formatDate(strike.issuedAt)))
            pane.addItem(GuiItem(item), 1 + index, 0)
        }

        // Penalty action buttons (rows 3-4)
        addPenaltyButton(pane, 1, 3, Material.GOLD_INGOT, lang.legacy("menu.guild_strike_penalty.action.level.name"),
            lang.legacy("menu.guild_strike_penalty.action.level.description", "levels" to strikesConfig.penalties.levelReductionLevels),
            PenaltyType.LEVEL_REDUCTION)
        addPenaltyButton(pane, 3, 3, Material.EXPERIENCE_BOTTLE, lang.legacy("menu.guild_strike_penalty.action.experience.name"),
            lang.legacy("menu.guild_strike_penalty.action.experience.description", "amount" to strikesConfig.penalties.expReductionAmount),
            PenaltyType.EXP_REDUCTION)
        addPenaltyButton(pane, 5, 3, Material.NAME_TAG, lang.legacy("menu.guild_strike_penalty.action.mute.name"),
            lang.legacy("menu.guild_strike_penalty.action.mute.description", "hours" to formatHours(strikesConfig.penalties.guildMuteDurationMillis)),
            PenaltyType.GUILD_MUTE)
        addPenaltyButton(pane, 7, 3, Material.TNT, lang.legacy("menu.guild_strike_penalty.action.disband.name"),
            lang.legacy("menu.guild_strike_penalty.action.disband.description"),
            PenaltyType.DISBAND)

        // Recent penalties (row 5)
        val recentPenalties = penaltyService.getByGuild(guild.id).take(3)
        if (recentPenalties.isNotEmpty()) {
            val penItem = ItemStack.of(Material.BOOK)
                .name(lang.legacy("menu.guild_strike_penalty.recent.name"))
            recentPenalties.forEach { p ->
                penItem.lore(lang.legacy("menu.guild_strike_penalty.recent.entry", "type" to penaltyTypeName(p.type), "actor" to p.actorName, "date" to formatDate(p.createdAt)))
            }
            pane.addItem(GuiItem(penItem), 1, 5)
        }

        // Back / close
        val backItem = ItemStack.of(Material.ARROW)
            .name(lang.legacy("menu.guild_strike_penalty.close.name"))
            .lore(lang.legacy("menu.guild_strike_penalty.close.description"))
        pane.addItem(GuiItem(backItem) { player.closeInventory() }, 7, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun formatHours(millis: Long): String = "%.1f".format(millis / 3_600_000.0)

    private fun addPenaltyButton(
        pane: StaticPane,
        x: Int,
        y: Int,
        material: Material,
        title: String,
        description: String,
        type: PenaltyType,
    ) {
        val item = ItemStack.of(material)
            .name(title)
            .lore(description)
            .lore(lang.legacy("menu.common.blank"))
            .lore(lang.legacy("menu.guild_strike_penalty.action.confirm"))
        pane.addItem(GuiItem(item) {
            menuNavigator.openMenu(
                GuildStrikePenaltyConfirmMenu(menuNavigator, player, guild, type, strikesConfig)
            )
        }, x, y)
    }

    private fun formatDate(instant: java.time.Instant): String {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .format(instant.atZone(java.time.ZoneId.systemDefault()))
    }

    private fun penaltyTypeName(type: PenaltyType): String = when (type) {
        PenaltyType.LEVEL_REDUCTION -> lang.raw("menu.guild_strike_penalty.type.level_reduction")
        PenaltyType.EXP_REDUCTION -> lang.raw("menu.guild_strike_penalty.type.exp_reduction")
        PenaltyType.GUILD_MUTE -> lang.raw("menu.guild_strike_penalty.type.guild_mute")
        PenaltyType.DISBAND -> lang.raw("menu.guild_strike_penalty.type.disband")
    }
}
