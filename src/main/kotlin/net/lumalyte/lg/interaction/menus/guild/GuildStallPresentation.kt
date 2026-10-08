package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildStallInfo
import net.lumalyte.lg.application.services.GuildStallPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import java.time.Instant

/** Localized display text; navigation and current-member checks stay in the menu. */
internal class GuildStallPresentation(private val lang: LangService) {
    fun metadata(stall: GuildStallInfo): List<Component> {
        return listOf(
            lang.gui(
                "guild_stall.location",
                "world" to Component.text(stall.world),
                "location" to Component.text(stall.coordinates ?: stall.region),
            ),
            lang.gui("guild_stall.state", "state" to stateText(stall.state)),
            lang.gui(
                "guild_stall.rent",
                "amount" to stall.rent,
                "interval" to
                    net.lumalyte.lg.utils.QuestDisplayFormatter
                        .duration(java.time.Duration.ofSeconds(stall.intervalSeconds)),
            ),
            lang.gui("guild_stall.due", "time" to instantText(stall.nextRentAt)),
            lang.gui("guild_stall.grace", "time" to instantText(stall.graceEndsAt)),
            lang.gui("guild_stall.members", "count" to stall.members.size),
        )
    }

    private fun instantText(value: Instant?): Component =
        value?.let { Component.text(it.toString()) } ?: lang.gui("guild_stall.unknown")

    private fun stateText(state: String): Component {
        return when (state) {
            "OWNED" -> lang.gui("guild_stall.active")
            "GRACE" -> lang.gui("guild_stall.grace_state")
            "MODERATION_HOLD" -> lang.gui("guild_stall.held")
            else -> lang.gui("guild_stall.inactive")
        }
    }

    fun permissionText(permission: GuildStallPermission): Component {
        return when (permission) {
            GuildStallPermission.MANAGE_SHOPS -> lang.gui("guild_stall.manage")
            GuildStallPermission.ACCESS_SHOP_CHESTS -> lang.gui("guild_stall.chests")
            GuildStallPermission.EDIT_SHOP_STOCK -> lang.gui("guild_stall.stock")
            GuildStallPermission.MODIFY_SHOP_PRICES -> lang.gui("guild_stall.prices")
        }
    }
}
