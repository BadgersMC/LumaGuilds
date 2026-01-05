package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
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

class GuildMemberListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild): Menu, KoinComponent {

    private val memberService: MemberService by inject()
    private val rankService: RankService by inject()

    override fun open() {
        val gui = ChestGui(6, "§6${guild.name} - Members")
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { event ->
            if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                event.isCancelled = true
            }
        }

        val staticPane = StaticPane(0, 5, 9, 1)
        val paginatedPane = PaginatedPane(0, 0, 9, 5)

        // Get all guild members
        val members = memberService.getGuildMembers(guild.id).sortedBy { member ->
            val rank = rankService.getPlayerRank(member.playerId, guild.id)
            rank?.priority ?: Int.MAX_VALUE
        }

        // Add member items to paginated pane
        val memberItems = members.map { member ->
            val rank = rankService.getPlayerRank(member.playerId, guild.id)
            val offlinePlayer = Bukkit.getOfflinePlayer(member.playerId)
            val playerName = offlinePlayer.name ?: "Unknown Player"

            val memberItem = ItemStack(Material.PLAYER_HEAD)
                .name("§f$playerName")
                .lore("§7Rank: §f${rank?.name ?: "Member"}")

            val joinFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            memberItem.lore("§7Joined: §f${member.joinedAt.atZone(java.time.ZoneId.systemDefault()).format(joinFormatter)}")

            if (offlinePlayer.isOnline) {
                memberItem.lore("§aCurrently Online")
            } else {
                offlinePlayer.lastPlayed.takeIf { it > 0 }?.let { lastPlayed ->
                    val lastSeenDate = java.time.Instant.ofEpochMilli(lastPlayed)
                        .atZone(java.time.ZoneId.systemDefault())
                    memberItem.lore("§7Last seen: §f${lastSeenDate.format(joinFormatter)}")
                }
            }

            memberItem.setData(
                DataComponentTypes.PROFILE,
                ResolvableProfile.resolvableProfile().uuid(member.playerId).build()
            )

            GuiItem(memberItem)
        }

        // Populate pages with members (45 per page = 9x5 grid)
        paginatedPane.populateWithGuiItems(memberItems)

        // Navigation buttons
        if (paginatedPane.pages > 1) {
            // Previous page button
            val prevButton = ItemStack(Material.ARROW)
                .name("§e⬅ Previous Page")
                .lore("§7Page ${paginatedPane.page + 1} of ${paginatedPane.pages}")

            val prevGuiItem = GuiItem(prevButton) {
                if (paginatedPane.page > 0) {
                    paginatedPane.page--
                    gui.update()
                }
            }
            staticPane.addItem(prevGuiItem, 0, 0)

            // Next page button
            val nextButton = ItemStack(Material.ARROW)
                .name("§eNext Page ➡")
                .lore("§7Page ${paginatedPane.page + 1} of ${paginatedPane.pages}")

            val nextGuiItem = GuiItem(nextButton) {
                if (paginatedPane.page < paginatedPane.pages - 1) {
                    paginatedPane.page++
                    gui.update()
                }
            }
            staticPane.addItem(nextGuiItem, 8, 0)
        }

        // Member count display
        val infoItem = ItemStack(Material.PLAYER_HEAD)
            .name("§6Total Members: §f${members.size}")
            .lore("§7Guild: §f${guild.name}")
        staticPane.addItem(GuiItem(infoItem), 4, 0)

        // Back button
        val backButton = ItemStack(Material.BARRIER)
            .name("§c⬅ BACK")
            .lore("§7Return to guild info")

        val backGuiItem = GuiItem(backButton) {
            menuNavigator.goBack()
        }
        staticPane.addItem(backGuiItem, 7, 0)

        gui.addPane(paginatedPane)
        gui.addPane(staticPane)
        gui.show(player)
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
