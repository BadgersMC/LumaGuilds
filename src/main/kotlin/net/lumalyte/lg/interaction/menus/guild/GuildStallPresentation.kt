package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.GuildStallInfo
import net.lumalyte.lg.application.services.GuildStallPermission
import net.lumalyte.lg.infrastructure.i18n.gui
import java.time.Duration
import java.time.Instant

/** Localized display text; navigation and current-member checks stay in the menu. */
internal class GuildStallPresentation(
    private val lang: LangService,
    private val now: () -> Instant = Instant::now,
) {
    /** REQ-258: readable rent times ("in 2d 4h"), and the grace line only during a grace period. */
    fun metadata(stall: GuildStallInfo): List<Component> = buildList {
        add(lang.gui(
            "guild_stall.location",
            "world" to Component.text(stall.world),
            "location" to Component.text(stall.coordinates ?: stall.region),
        ))
        add(lang.gui("guild_stall.state", "state" to stateText(stall.state)))
        add(lang.gui("guild_stall.rent", "amount" to stall.rent, "interval" to every(stall.intervalSeconds)))
        stall.nextRentAt?.let { due ->
            val left = Duration.between(now(), due)
            add(if (left.isNegative || left.isZero) lang.gui("guild_stall.due_now")
                else lang.gui("guild_stall.due", "time" to compact(left)))
        }
        if (stall.state == "GRACE") stall.graceEndsAt?.let { ends ->
            add(lang.gui("guild_stall.grace", "time" to compact(Duration.between(now(), ends).coerceAtLeast(Duration.ZERO))))
        }
        add(lang.gui("guild_stall.members", "count" to stall.members.size))
    }

    private fun every(seconds: Long): Component = when {
        seconds == DAY -> lang.gui("guild_stall.every_day")
        seconds == WEEK -> lang.gui("guild_stall.every_week")
        else -> lang.gui("guild_stall.every", "time" to compact(Duration.ofSeconds(seconds)))
    }

    /** Two largest units: "2d 4h", "3h 12m", "5m". */
    private fun compact(value: Duration): String {
        val days = value.toDays()
        val hours = value.toHours() % 24
        val minutes = value.toMinutes() % 60
        return when {
            days > 0 -> if (hours > 0) "${days}d ${hours}h" else "${days}d"
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            else -> "${minutes.coerceAtLeast(1)}m"
        }
    }

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

    private companion object {
        const val DAY = 86_400L
        const val WEEK = 604_800L
    }
}
