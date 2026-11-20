package net.lumalyte.lg.interaction.menus.guild

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.BanResult
import net.lumalyte.lg.application.services.GuildBanService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.gui
import net.lumalyte.lg.infrastructure.i18n.guiTitle
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.utils.MenuTitleBuilder
import net.lumalyte.lg.utils.NexoItemProvider
import net.lumalyte.lg.utils.inventoryframework.StaticPane
import net.lumalyte.lg.utils.inventoryframework.addPane
import net.lumalyte.lg.utils.lore
import net.lumalyte.lg.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/** REQ-193/REQ-194: asks before banning; the ban service checks the rules again on confirm. */
class GuildBanConfirmationMenu(
    private val menuNavigator: MenuNavigator,
    private val player: Player,
    private val guild: Guild,
    private val targetId: UUID,
) : Menu, KoinComponent {

    private val banService: GuildBanService by inject()
    private val lang: LangService by inject()

    override fun open() {
        val name = Bukkit.getOfflinePlayer(targetId).name ?: lang.raw("menu.guild_info.fallback.unknown_player")
        val check = banService.check(player.uniqueId, guild.id, targetId)
        val gui = ChestGui(3, MenuTitleBuilder.build(guild.guiTheme, 3, lang.guiTitle("guild_ban.confirm.title", "player" to name)))
        gui.setOnTopClick { it.isCancelled = true }
        gui.setOnBottomClick { if (it.click == ClickType.SHIFT_LEFT || it.click == ClickType.SHIFT_RIGHT) it.isCancelled = true }
        val pane = StaticPane(0, 0, 9, 3)

        val head = ItemStack.of(Material.PLAYER_HEAD)
        head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(targetId).build())
        pane.addItem(GuiItem(head.name(lang.gui("guild_page.roster.name", "player" to name))), 4, 0)

        val wasMember = (check as? BanResult.Banned)?.wasMember ?: true
        val confirm = NexoItemProvider.getItemStackOrFallback("lg_confirm_danger") { ItemStack.of(Material.RED_WOOL) }
            .name(lang.gui("guild_ban.confirm.confirm", "player" to name))
            .lore(if (wasMember) lang.gui("guild_ban.confirm.member_lore") else lang.gui("guild_ban.confirm.outsider_lore"))
            .lore(lang.gui("guild_ban.confirm.undo_lore"))
        pane.addItem(GuiItem(confirm) { confirm(name) }, 3, 1)

        val cancel = NexoItemProvider.getItemStackOrFallback("lg_cancel") { ItemStack.of(Material.GREEN_WOOL) }
            .name(lang.gui("guild_ban.confirm.cancel"))
        pane.addItem(GuiItem(cancel) { menuNavigator.goBack() }, 5, 1)

        gui.addPane(pane)
        gui.show(player)
    }

    private fun confirm(name: String) {
        val result = banService.ban(player.uniqueId, guild.id, targetId)
        player.sendMessage(GuildBanMessages.ban(lang, result, name))
        if (result is BanResult.Banned) {
            Bukkit.getPlayer(targetId)?.sendMessage(lang.msg("guild_ban.notify.target", "guild" to guild.name))
            menuNavigator.returnTo(GuildInfoMenu::class.java) { GuildInfoMenu(menuNavigator, player, guild) }
        } else {
            menuNavigator.goBack()
        }
    }

    override fun passData(data: Any?) = Unit
}
