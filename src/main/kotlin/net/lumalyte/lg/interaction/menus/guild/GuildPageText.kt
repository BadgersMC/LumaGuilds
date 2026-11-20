package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.domain.values.GuildCardFact
import net.lumalyte.lg.domain.values.LastSeen
import net.lumalyte.lg.domain.values.MemberActionDenial
import net.lumalyte.lg.infrastructure.i18n.gui

/** REQ-192 to REQ-195: guild page lines that depend on a value, with every key written out. */
internal object GuildPageText {
    fun fact(lang: LangService, fact: GuildCardFact, value: Boolean): Component {
        val shown = if (value) lang.gui("guild_page.card.yes") else lang.gui("guild_page.card.no")
        return when (fact) {
            GuildCardFact.HOME -> lang.gui("guild_page.card.fact.home", "value" to shown)
            GuildCardFact.BANNER -> lang.gui("guild_page.card.fact.banner", "value" to shown)
            GuildCardFact.VAULT -> lang.gui("guild_page.card.fact.vault", "value" to shown)
            GuildCardFact.SHOP -> lang.gui("guild_page.card.fact.shop", "value" to shown)
        }
    }

    fun lastSeen(lang: LangService, seen: LastSeen?): Component = when (seen?.unit) {
        null -> lang.gui("guild_page.roster.last_seen.unknown")
        LastSeen.Unit.MINUTES -> lang.gui("guild_page.roster.last_seen.minutes", "amount" to seen.amount)
        LastSeen.Unit.HOURS -> lang.gui("guild_page.roster.last_seen.hours", "amount" to seen.amount)
        LastSeen.Unit.DAYS -> lang.gui("guild_page.roster.last_seen.days", "amount" to seen.amount)
    }

    fun denial(lang: LangService, reason: MemberActionDenial): Component = when (reason) {
        MemberActionDenial.NO_PERMISSION -> lang.gui("guild_page.member.denied.no_permission")
        MemberActionDenial.SELF -> lang.gui("guild_page.member.denied.self")
        MemberActionDenial.NOT_OUTRANKED -> lang.gui("guild_page.member.denied.not_outranked")
        MemberActionDenial.TOP_RANK_FOR_VIEWER -> lang.gui("guild_page.member.denied.top_rank_for_viewer")
        MemberActionDenial.LOWEST_RANK -> lang.gui("guild_page.member.denied.lowest_rank")
    }
}
