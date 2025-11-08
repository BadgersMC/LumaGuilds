package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.interaction.menus.Menu
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.MenuFactory
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GuildMemberListMenu(private val menuNavigator: MenuNavigator, private val player: Player,
                         private var guild: Guild): Menu, KoinComponent {

    private val menuFactory: MenuFactory by inject()
    private val memberService: MemberService by inject()

    override fun open() {
        player.sendMessage("§eMember List menu coming soon!")

        // Security check: Only allow players who are in the guild and have permission to access control panel
        if (memberService.getMember(player.uniqueId, guild.id) != null && memberService.hasPermission(player.uniqueId, guild.id, RankPermission.MANAGE_GUILD_SETTINGS)) {
            menuNavigator.openMenu(menuFactory.createGuildControlPanelMenu(menuNavigator, player, guild))
        } else if (memberService.getMember(player.uniqueId, guild.id) != null) {
            // For regular members without the permission
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        } else {
            // If not a member, go back to the info menu instead
            menuNavigator.openMenu(menuFactory.createGuildInfoMenu(menuNavigator, player, guild))
        }
    }

    override fun passData(data: Any?) {
        guild = data as? Guild ?: return
    }
}
