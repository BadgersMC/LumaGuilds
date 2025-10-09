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
import net.lumalyte.lg.utils.AntiDupeUtil
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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.utils.AdventureMenuHelper
import net.lumalyte.lg.application.services.MessageService
import net.lumalyte.lg.utils.setAdventureName
import net.lumalyte.lg.utils.addAdventureLore

class GuildInfoMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                   private var guild: Guild, private val messageService: MessageService): Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val relationService: RelationService by inject()
    private val menuFactory: net.lumalyte.lg.interaction.menus.MenuFactory by inject()

    override fun open() {
        val gui = ChestGui(6, AdventureMenuHelper.createMenuTitle(player, messageService, "<gold><gold>Guild Info - ${guild.name}"))
        val pane = StaticPane(0, 0, 9, 6)
        // CRITICAL SECURITY: Prevent item duplication exploits with targeted protection
        AntiDupeUtil.protect(gui)

        // Basic guild information
        addGuildOverview(pane, 0, 0)

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
            .setAdventureName(player, messageService, "<gold>Guild Overview")
            .addAdventureLore(player, messageService, "<gray>Name: <white>${guild.name}")
            .addAdventureLore(player, messageService, "<gray>Level: <yellow>${guild.level}")
            .addAdventureLore(player, messageService, "<gray>Mode: <white>${guild.mode.name.lowercase().replaceFirstChar { it.uppercase() }}")

        if (guild.emoji != null) {
            overviewItem.addAdventureLore(player, messageService, "<gray>Emoji: <white>${guild.emoji}")
        }

        if (guild.description != null) {
            overviewItem.addAdventureLore(player, messageService, "<gray>Description: <white>${parseMiniMessageForDisplay(guild.description)}")
        }

        if (guild.tag != null) {
            overviewItem.addAdventureLore(player, messageService, "<gray>Tag: <white>${guild.tag}")
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        overviewItem.addAdventureLore(player, messageService, "<gray>Founded: <white>${guild.createdAt.atZone(java.time.ZoneId.systemDefault()).format(formatter)}")

        pane.addItem(GuiItem(overviewItem), x, y)
    }

    private fun addMembersSection(pane: StaticPane, x: Int, y: Int) {
        val memberCount = memberService.getMemberCount(guild.id)
        val membersItem = ItemStack(Material.PLAYER_HEAD)
            .setAdventureName(player, messageService, "<aqua>Members (<white>$memberCount<aqua>)")
            .addAdventureLore(player, messageService, "<gray>Click to view member list")

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
                    .setAdventureName(player, messageService, "<white>$playerName")
                    .lore("<gray>Rank: <white>${rank?.name ?: "Member"}")

                pane.addItem(GuiItem(memberItem), x, y + 1 + index)
            }
        }

        if (members.size > 3) {
            val moreItem = ItemStack(Material.PAPER)
                .setAdventureName(player, messageService, "<gray>... and ${members.size - 3} more")
                .addAdventureLore(player, messageService, "<gray>Click members button above to see all")
            pane.addItem(GuiItem(moreItem), x, y + 4)
        }
    }

    private fun addRelationsSection(pane: StaticPane, x: Int, y: Int) {
        val relations = relationService.getGuildRelations(guild.id)

        // Allies
        val allies = relations.filter { it.type == RelationType.ALLY }
        val alliesItem = ItemStack(Material.LIME_BANNER)
            .setAdventureName(player, messageService, "<green>Allies (<white>${allies.size}<green>)")

        if (allies.isNotEmpty()) {
            allies.take(3).forEach { relation ->
                val allyId = relation.getOtherGuild(guild.id)
                val allyGuild = guildService.getGuild(allyId)
                alliesItem.lore("<gray>• <white>${allyGuild?.name ?: "Unknown"}")
            }
            if (allies.size > 3) {
                alliesItem.addAdventureLore(player, messageService, "<gray>... and ${allies.size - 3} more")
            }
        } else {
            alliesItem.addAdventureLore(player, messageService, "<gray>No allies")
        }

        pane.addItem(GuiItem(alliesItem), x, y)

        // Enemies/Wars
        val enemies = relations.filter { it.type == RelationType.ENEMY }
        val enemiesItem = ItemStack(Material.RED_BANNER)
            .setAdventureName(player, messageService, "<red>Wars (<white>${enemies.size}<red>)")

        if (enemies.isNotEmpty()) {
            enemies.take(3).forEach { relation ->
                val enemyId = relation.getOtherGuild(guild.id)
                val enemyGuild = guildService.getGuild(enemyId)
                enemiesItem.lore("<gray>• <white>${enemyGuild?.name ?: "Unknown"}")
            }
            if (enemies.size > 3) {
                enemiesItem.addAdventureLore(player, messageService, "<gray>... and ${enemies.size - 3} more")
            }
        } else {
            enemiesItem.addAdventureLore(player, messageService, "<gray>No active wars")
        }

        pane.addItem(GuiItem(enemiesItem), x, y + 1)
    }

    private fun addStatisticsSection(pane: StaticPane, x: Int, y: Int) {
        val statsItem = ItemStack(Material.BOOK)
            .setAdventureName(player, messageService, "<yellow>Statistics")
            .addAdventureLore(player, messageService, "<gray>Bank Balance: <gold>$${guild.bankBalance}")
            .addAdventureLore(player, messageService, "<gray>Level: <yellow>${guild.level}")

        if (guild.home != null) {
            statsItem.addAdventureLore(player, messageService, "<gray>Has Guild Home: <green>Yes")
        } else {
            statsItem.addAdventureLore(player, messageService, "<gray>Has Guild Home: <red>No")
        }

        if (guild.banner != null) {
            statsItem.addAdventureLore(player, messageService, "<gray>Has Banner: <green>Yes")
        } else {
            statsItem.addAdventureLore(player, messageService, "<gray>Has Banner: <red>No")
        }

        pane.addItem(GuiItem(statsItem), x, y)

        // Progression info
        val progressionItem = ItemStack(Material.EXPERIENCE_BOTTLE)
            .setAdventureName(player, messageService, "<light_purple>Progression")
            .addAdventureLore(player, messageService, "<gray>Level: <yellow>${guild.level}")
            .addAdventureLore(player, messageService, "<gray>Next Level: <yellow>${guild.level + 1}")

        pane.addItem(GuiItem(progressionItem), x, y + 1)
    }

    private fun addBackButton(pane: StaticPane, x: Int, y: Int) {
        val backItem = ItemStack(Material.BARRIER)
            .setAdventureName(player, messageService, "<red>⬅️ BACK")
            .addAdventureLore(player, messageService, "<gray>Return to previous menu")

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
            val plainText = PlainTextComponentSerializer.plainText().serialize(component)
            plainText
        } catch (e: Exception) {
            description // Fallback to raw text if parsing fails
        }
    }}

