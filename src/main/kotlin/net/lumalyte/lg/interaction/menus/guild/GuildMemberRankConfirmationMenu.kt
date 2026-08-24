package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.utils.MenuTitleBuilder

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

class GuildMemberRankConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetMember: Member,
    private val newRank: Rank
) : Menu, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()
    private val lang: LangService by inject()

    private val logger = LoggerFactory.getLogger(GuildMemberRankConfirmationMenu::class.java)

    override fun open() {
        val gui = ChestGui(3, MenuTitleBuilder.build(
            guild.guiTheme,
            3,
            lang.guiTitle("menu.guild_confirmation.rank_change.title"),
        ))
        val pane = StaticPane(0, 0, 9, 3)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT || guiEvent.click == ClickType.SHIFT_RIGHT) {
                guiEvent.isCancelled = true
            }
        }

        // Add member info
        addMemberInfo(pane)

        // Add rank change info
        addRankChangeInfo(pane)

        // Add confirmation buttons
        addConfirmationButtons(pane)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun addMemberInfo(pane: StaticPane) {
        // Member head
        val headItem = createMemberHead()
        pane.addItem(GuiItem(headItem), 0, 0)

        // Member info
        val playerName = Bukkit.getOfflinePlayer(targetMember.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")
        val infoItem = ItemStack.of(Material.PAPER)
            .name(lang.gui("menu.guild_confirmation.rank_change.item.member.name"))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.member.lore.player", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.member.lore.guild", "guild" to guild.name))

        pane.addItem(GuiItem(infoItem), 1, 0)
    }

    private fun addRankChangeInfo(pane: StaticPane) {
        val currentRank = rankService.getRank(targetMember.rankId)

        // Current rank
        val currentRankItem = if (currentRank != null) {
            ItemStack.of(Material.RED_CONCRETE)
                .name(lang.gui("menu.guild_confirmation.rank_change.item.current.name"))
                .lore(lang.gui("menu.guild_confirmation.rank_change.item.rank_lore.rank", "rank" to currentRank.name))
                .lore(lang.gui("menu.guild_confirmation.rank_change.item.rank_lore.priority", "priority" to currentRank.priority))
        } else {
            ItemStack.of(Material.BARRIER)
                .name(lang.gui("menu.guild_confirmation.rank_change.item.current.error"))
        }

        // New rank
        val newRankItem = ItemStack.of(Material.GREEN_CONCRETE)
            .name(lang.gui("menu.guild_confirmation.rank_change.item.new.name"))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.rank_lore.rank", "rank" to newRank.name))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.rank_lore.priority", "priority" to newRank.priority))

        // Change direction indicator
        val isPromotion = newRank.priority < (currentRank?.priority ?: 0)
        val changeDirection = if (isPromotion) {
            lang.gui("menu.guild_confirmation.rank_change.direction.promotion")
        } else {
            lang.gui("menu.guild_confirmation.rank_change.direction.demotion")
        }

        val summaryItem = ItemStack.of(Material.BOOK)
            .name(lang.gui("menu.guild_confirmation.rank_change.item.summary.name"))
            .lore(changeDirection)
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.summary.lore.from", "rank" to (currentRank?.name ?: lang.raw("general.unknown"))))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.summary.lore.to", "rank" to newRank.name))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.summary.lore.priority_change", "change" to ((currentRank?.priority ?: 0) - newRank.priority)))
            .lore("")
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.summary.lore.permissions", "permission_count" to newRank.permissions.size))

        pane.addItem(GuiItem(currentRankItem), 3, 0)
        pane.addItem(GuiItem(summaryItem), 4, 0)
        pane.addItem(GuiItem(newRankItem), 5, 0)
    }

    private fun addConfirmationButtons(pane: StaticPane) {
        // Confirm button
        val confirmItem = ItemStack.of(Material.GREEN_WOOL)
            .name(lang.gui("menu.guild_confirmation.rank_change.item.confirm.name"))
            .lore(lang.gui(
                "menu.guild_confirmation.rank_change.item.confirm.lore.player",
                "player" to (Bukkit.getOfflinePlayer(targetMember.playerId).name ?: lang.raw("general.unknown")),
            ))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.confirm.lore.rank", "rank" to newRank.name))

        val confirmGuiItem = GuiItem(confirmItem) {
            performRankChange()
        }
        pane.addItem(confirmGuiItem, 2, 2)

        // Cancel button
        val cancelItem = ItemStack.of(Material.RED_WOOL)
            .name(lang.gui("menu.guild_confirmation.rank_change.item.cancel.name"))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.cancel.lore"))

        val cancelGuiItem = GuiItem(cancelItem) {
            menuNavigator.goBack()
        }
        pane.addItem(cancelGuiItem, 6, 2)
    }

    private fun performRankChange() {
        try {
            val success = memberService.changeMemberRank(
                targetMember.playerId,
                guild.id,
                newRank.id,
                player.uniqueId
            )

            if (success) {
                val targetName = Bukkit.getOfflinePlayer(targetMember.playerId).name
                    ?: lang.raw("menu.guild_confirmation.common.unknown_player")
                val currentRank = rankService.getRank(targetMember.rankId)

                val successMessage = if (newRank.priority < (currentRank?.priority ?: 0)) {
                    lang.msg("menu.guild_confirmation.rank_change.feedback.promoted", "player" to targetName)
                } else {
                    lang.msg("menu.guild_confirmation.rank_change.feedback.demoted", "player" to targetName)
                }

                player.sendMessage(successMessage)
                player.sendMessage(lang.msg("menu.guild_confirmation.rank_change.feedback.new_rank", "rank" to newRank.name))

                // Notify the target player if they're online
                val targetPlayer = Bukkit.getPlayer(targetMember.playerId)
                if (targetPlayer != null && targetPlayer.isOnline) {
                    targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.rank_change.feedback.target", "guild" to guild.name))
                    targetPlayer.sendMessage(lang.msg("menu.guild_confirmation.rank_change.feedback.new_rank", "rank" to newRank.name))
                }

                // Return to member management menu
                menuNavigator.goBack()
            } else {
                player.sendMessage(lang.msg("menu.guild_confirmation.rank_change.feedback.failure"))
            }
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            player.sendMessage(lang.msg("menu.guild_confirmation.rank_change.feedback.error", "error" to (e.message ?: lang.raw("general.unknown"))))
            logger.error("Error changing member rank", e)
        }
    }

    private fun createMemberHead(): ItemStack {
        val head = ItemStack.of(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        val playerName = Bukkit.getOfflinePlayer(targetMember.playerId).name
            ?: lang.raw("menu.guild_confirmation.common.unknown_player")

        // Set skull owner using Craftatar API URL
        try {
            val skullMeta = meta as SkullMeta
            // Use Craftatar API for player heads
            val textureUrl = "https://craftatar.com/avatars/${targetMember.playerId}?size=64&default=MHF_Steve&overlay"
            // Note: In a real implementation, you'd need to set the skull texture properly
            // This is a simplified version - you'd need skull texture utilities
        } catch (e: Exception) {
            // Menu operation - catching all exceptions to prevent UI failure
            // Fallback if skull texture setting fails
        }

        head.itemMeta = meta

        return head.name(lang.gui("menu.guild_confirmation.rank_change.item.head.name", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.head.lore.player", "player" to playerName))
            .lore(lang.gui("menu.guild_confirmation.rank_change.item.head.lore.confirming"))
    }

    override fun passData(data: Any?) {
        // Handle data passed back from sub-menus if needed
    }
}
