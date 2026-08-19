package net.lumalyte.lg.interaction.menus.guild

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
) : Menu {

    override fun open() {
        val playerId = player.uniqueId

        // Security check
        if (memberService.getMember(playerId, guild.id) == null) {
            player.sendMessage("§c❌ You cannot access the dashboard for a guild you're not a member of!")
            menuNavigator.goBack()
            return
        }

        // Refresh guild data
        guild = guildService.getGuild(guild.id) ?: run {
            player.sendMessage("§c❌ Guild no longer exists.")
            menuNavigator.goBack()
            return
        }

        val gui = ChestGui(3, "§0§8⚔ §7${guild.name}")
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { e -> e.isCancelled = true }
        gui.setOnBottomClick { e ->
            val click = e.click
            if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT ||
                click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT
            ) e.isCancelled = true
        }
        gui.addPane(pane)

        // Fill background first (nav buttons overwrite their slots)
        fillBackground(pane)

        // Guild info display at top center
        addGuildInfoDisplay(pane, 4, 0)

        // Row 1 (y=1): Information, Members, Ranks, Economy
        addNavButton(pane, 0, 1, "lg_nav_info", Material.KNOWLEDGE_BOOK, "§9Information",
            "§7Guild details, statistics,", "§7and performance metrics") {
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 2, 1, "lg_nav_members", Material.PLAYER_HEAD, "§bMembers",
            "§7Member list, management,", "§7invite, kick, and promote") {
            menuNavigator.openMenu(menuFactory.createGuildMemberManagementMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 4, 1, "lg_nav_ranks", Material.IRON_SWORD, "§6Ranks",
            "§7Create, edit, and manage", "§7guild ranks and permissions") {
            menuNavigator.openMenu(menuFactory.createGuildRankManagementMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 6, 1, "lg_nav_economy", Material.GOLD_BLOCK, "§6Economy",
            "§7Bank, vault, budget,", "§7and transaction history") {
            menuNavigator.openMenu(menuFactory.createGuildBankMenu(menuNavigator, player, guild))
        }

        // Row 2 (y=2): Settings, Progression, Diplomacy, Warfare
        addNavButton(pane, 0, 2, "lg_nav_settings", Material.COMMAND_BLOCK, "§eSettings",
            "§7Guild name, tag, emoji,", "§7home, mode, and more") {
            menuNavigator.openMenu(menuFactory.createGuildSettingsMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 2, 2, "lg_nav_progression", Material.EXPERIENCE_BOTTLE, "§aProgression",
            "§7Guild leveling, XP,", "§7and unlocked perks") {
            menuNavigator.openMenu(menuFactory.createGuildProgressionMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 4, 2, "lg_nav_diplomacy", Material.BOOK, "§dDiplomacy",
            "§7Alliances, enemies,", "§7truces, and relations") {
            menuNavigator.openMenu(menuFactory.createGuildRelationsMenu(menuNavigator, player, guild))
        }

        addNavButton(pane, 6, 2, "lg_nav_warfare", Material.DIAMOND_SWORD, "§4War & Party",
            "§7Declare war, manage", "§7parties, and conflicts") {
            menuNavigator.openMenu(menuFactory.createGuildWarManagementMenu(menuNavigator, player, guild))
        }

        gui.show(player)
    }

    /**
     * Fills all slots with the 3-row background texture.
     * Nav buttons are added after this, so they overwrite their positions.
     */
    private fun fillBackground(pane: StaticPane) {
        val bgItem = NexoItemProvider.getItemStack("lg_bg_3_row")
            ?: ItemStack.of(Material.GRAY_STAINED_GLASS_PANE).name(" ")

        for (x in 0..8) {
            for (y in 0..2) {
                pane.addItem(GuiItem(bgItem.clone()) { it.isCancelled = true }, x, y)
            }
        }
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
        val lore = java.util.ArrayList<String>()
        for (line in loreLines) lore.add(line)
        meta.lore = lore
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
            .name("§f⚔ §7$displayName")
        val lore = java.util.ArrayList<String>().apply {
            add("§7Members: §f$memberCount")
            add("§7Ranks: §f$rankCount")
            add("§7Balance: §6$${guild.bankBalance}")
            add("")
            add("§7Select a category below")
            add("§7to manage your guild")
        }
        val meta = item.itemMeta ?: return
        meta.lore = lore
        item.itemMeta = meta

        pane.addItem(GuiItem(item) { it.isCancelled = true }, x, y)
    }
}