package net.lumalyte.lg.interaction.menus.guild

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

    override fun open() {
        val gui = ChestGui(6, "§4§lPenalties - ${GuildResolver.displayName(guild)}")
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
            .name("§f${GuildResolver.displayName(guild)}")
            .lore("§7Level: §f${guild.level}")
            .lore("§7Strikes: §e${strikeService.countByGuild(guild.id)}")
            .lore("§7Threshold: §f${strikesConfig.threshold}")
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
            val lifted = if (strike.active) "" else " §8(lifted)"
            val item = ItemStack.of(material)
                .name("§c${strike.punishmentType.uppercase()}$lifted")
                .lore("§7Player: §f${strike.playerName ?: strike.playerUuid.toString().take(8)}")
                .lore("§7Reason: §f${strike.reason?.take(60) ?: "—"}")
                .lore("§7By: §f${strike.executorName ?: "?"} §8• §7${formatDate(strike.issuedAt)}")
            pane.addItem(GuiItem(item), 1 + index, 0)
        }

        // Penalty action buttons (rows 3-4)
        addPenaltyButton(pane, 1, 3, Material.GOLD_INGOT, "§6⬇ Level Reduction",
            "§7Remove §e${strikesConfig.penalties.levelReductionLevels}§7 level(s)",
            PenaltyType.LEVEL_REDUCTION)
        addPenaltyButton(pane, 3, 3, Material.EXPERIENCE_BOTTLE, "§b⬇ EXP Reduction",
            "§7Remove §e${strikesConfig.penalties.expReductionAmount}§7 XP",
            PenaltyType.EXP_REDUCTION)
        addPenaltyButton(pane, 5, 3, Material.NAME_TAG, "§d🔇 Guild Mute",
            "§7Mute guild chat for §e${strikesConfig.penalties.guildMuteDurationMillis / 3_600_000}h§7",
            PenaltyType.GUILD_MUTE)
        addPenaltyButton(pane, 7, 3, Material.TNT, "§c💥 Disband Guild",
            "§cPermanently disbands the guild",
            PenaltyType.DISBAND)

        // Recent penalties (row 5)
        val recentPenalties = penaltyService.getByGuild(guild.id).take(3)
        if (recentPenalties.isNotEmpty()) {
            val penItem = ItemStack.of(Material.BOOK)
                .name("§7Recent penalties")
            recentPenalties.forEach { p ->
                penItem.lore("§7- §f${p.type.name.replace('_', ' ')} §8by ${p.actorName} §8(${formatDate(p.createdAt)})")
            }
            pane.addItem(GuiItem(penItem), 1, 5)
        }

        // Back / close
        val backItem = ItemStack.of(Material.ARROW)
            .name("§a← Back to strikes")
            .lore("§7Close this menu")
        pane.addItem(GuiItem(backItem) { player.closeInventory() }, 7, 5)

        gui.addPane(pane)
        gui.show(player)
    }

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
            .lore("§7")
            .lore("§eClick to confirm")
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
}
