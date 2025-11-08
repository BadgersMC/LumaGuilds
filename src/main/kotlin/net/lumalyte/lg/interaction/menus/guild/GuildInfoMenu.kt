package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.format.DateTimeFormatter
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class GuildInfoMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(6, "§6Guild Info - ${guild.name}")
        val pane = StaticPane(0, 0, 9, 6)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Basic guild information
        addGuildOverview(pane, 0, 0)

        // Description section (if available)
        if (guild.description != null) {
            addDescriptionSection(pane, 1, 0)
        }

        // Members section
        addMembersSection(pane, 2, 0)

        // Relations section (allies/enemies)
        addRelationsSection(pane, 4, 0)

        // Statistics section
        addStatisticsSection(pane, 6, 0)

        // Back button
        addBackButton(pane, 8, 5)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addGuildOverview(pane: StaticPane, x: Int, y: Int) {
        val overviewItem = ItemStack(Material.SHIELD)
            .name("§6Guild Overview")
            .lore("§7Name: §f${guild.name}")
            .lore("§7Level: §e${guild.level}")
            .lore("§7Mode: §f${guild.mode.name.lowercase().replaceFirstChar { it.uppercase() }}")

        if (guild.emoji != null) {
            overviewItem.lore("§7Emoji: §f${guild.emoji}")
        }

        if (guild.tag != null) {
            overviewItem.lore("§7Tag: §f${guild.tag}")
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        overviewItem.lore("§7Founded: §f${guild.createdAt.atZone(java.time.ZoneId.systemDefault()).format(formatter)}")

        pane.addItem(GuiItem(overviewItem), x, y)
    }

    private fun addDescriptionSection(pane: StaticPane, x: Int, y: Int) {
        val descriptionItem = ItemStack(Material.WRITABLE_BOOK)
            .name("§6Description")

        // Parse and display the description with MiniMessage formatting
        val formattedDescription = parseMiniMessageForDisplay(guild.description)
        if (formattedDescription != null) {
            descriptionItem.lore("§r$formattedDescription")
        }

        pane.addItem(GuiItem(descriptionItem), x, y)
    }

    private fun addMembersSection(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val membersItem = ItemStack(Material.PLAYER_HEAD)
            .name("§bMembers (§f$memberCount§b)")
            .lore("§7Click to view member list")

        val membersGuiItem = GuiItem(membersItem) {
            menuNavigator.openMenu(menuFactory.createGuildMemberListMenu(menuNavigator, player, guild))
        }
        pane.addItem(membersGuiItem, x, y)

        // Show owner and a few key members
        val members = memberService.getGuildMembers(guild.id).take(4)
        members.forEachIndexed { index, member ->
            if (index < 3) { // Show only first 3 members to fit
                val rank = rankService.getPlayerRank(member.playerId, guild.id)
                val playerName = Bukkit.getPlayer(member.playerId)?.name ?: "Unknown Player"
                val memberItem = ItemStack(Material.PLAYER_HEAD)
                    .name("§f$playerName")
                    .lore("§7Rank: §f${rank?.name ?: "Member"}")

                pane.addItem(GuiItem(memberItem), x, y + 1 + index)
            }
        }

        if (members.size > 3) {
            val moreItem = ItemStack(Material.PAPER)
                .name("§7... and ${members.size - 3} more")
                .lore("§7Click members button above to see all")
            pane.addItem(GuiItem(moreItem), x, y + 4)
        }
    }

    private fun addRelationsSection(pane: StaticPane, x: Int, y: Int) {
        val relations = relationService.getGuildRelations(guild.id)

        // Allies
        val allies = relations.filter { it.type == RelationType.ALLY }
        val alliesItem = ItemStack(Material.LIME_BANNER)
            .name("§aAllies (§f${allies.size}§a)")

        if (allies.isNotEmpty()) {
            allies.take(3).forEach { relation ->
                val allyId = relation.getOtherGuild(guild.id)
                val allyGuild = guildService.getGuild(allyId)
                alliesItem.lore("§7• §f${allyGuild?.name ?: "Unknown"}")
            }
            if (allies.size > 3) {
                alliesItem.lore("§7... and ${allies.size - 3} more")
            }
        } else {
            alliesItem.lore("§7No allies")
        }

        pane.addItem(GuiItem(alliesItem), x, y)

        // Enemies/Wars
        val enemies = relations.filter { it.type == RelationType.ENEMY }
        val enemiesItem = ItemStack(Material.RED_BANNER)
            .name("§cWars (§f${enemies.size}§c)")

        if (enemies.isNotEmpty()) {
            enemies.take(3).forEach { relation ->
                val enemyId = relation.getOtherGuild(guild.id)
                val enemyGuild = guildService.getGuild(enemyId)
                enemiesItem.lore("§7• §f${enemyGuild?.name ?: "Unknown"}")
            }
            if (enemies.size > 3) {
                enemiesItem.lore("§7... and ${enemies.size - 3} more")
            }
        } else {
            enemiesItem.lore("§7No active wars")
        }

        pane.addItem(GuiItem(enemiesItem), x, y + 1)
    }

    private fun addStatisticsSection(pane: StaticPane, x: Int, y: Int) {
        val statsItem = ItemStack(Material.BOOK)
            .name("§eStatistics")
            .lore("§7Bank Balance: §6$${guild.bankBalance}")
            .lore("§7Level: §e${guild.level}")

        if (guild.home != null) {
            statsItem.lore("§7Has Guild Home: §aYes")
        } else {
            statsItem.lore("§7Has Guild Home: §cNo")
        }

        if (guild.banner != null) {
            statsItem.lore("§7Has Banner: §aYes")
        } else {
            statsItem.lore("§7Has Banner: §cNo")
        }

        pane.addItem(GuiItem(statsItem), x, y)

        // Progression info
        val progressionItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .name("§dProgression")
            .lore("§7Level: §e${guild.level}")
            .lore("§7Next Level: §e${guild.level + 1}")

        pane.addItem(GuiItem(progressionItem), x, y + 1)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .name("§c⬅ BACK")
            .lore("§7Return to previous menu")

        val backGuiItem = GuiItem(backItem) {
            menuNavigator.goBack()
        }
        pane.addItem(backGuiItem, x, y)
    }


    private fun parseMiniMessageForDisplay(description: String?): String? {
        if (description == null) return null
        return try {
            val miniMessage = MiniMessage.miniMessage()
            val component = miniMessage.deserialize(description)
            // Convert to legacy format (§ codes) for menu display
            val legacyText = LegacyComponentSerializer.legacySection().serialize(component)
            legacyText
        } catch (e: Exception) {
            description // Fallback to raw text if parsing fails
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}

