package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.domain.entities.Guild

/** Concise public summary; complete details remain on the profile and hover quote. */
internal fun RecruitmentController.browseCardSummary(guild: Guild): List<Component> {
    val profile = service.listings.profile(guild)
    val interests =
        profile.interests.joinToString(", ") { interest ->
            val key =
                when (interest.lowercase()) {
                    "building" -> "recruitment.building"
                    "pvp" -> "recruitment.pvp"
                    "quests" -> "recruitment.category_quests"
                    "social" -> "recruitment.category_social"
                    else -> null
                }
            key?.let(lang::raw) ?: interest
        }.ifBlank { lang.raw("recruitment.any") }
    val online = members.getGuildMembers(guild.id).count { visibleRecruiter(it.playerId) }
    val summary =
        lang.raw("guild_dialogs.board.summary")
            .replace("%activity%", interests)
            .replace("%spaces%", service.listings.spaces(guild.id).toString())
            .replace("%online%", online.toString())
    val fee = lfg.getJoinRequirement(guild)
    val price =
        when {
            fee != null ->
                lang.raw("guild_dialogs.board.fee")
                    .replace("%amount%", fee.amount.toString()).replace("%currency%", fee.currencyName)
            guild.joinFeeEnabled && guild.joinFeeAmount > 0 -> lang.raw("recruitment.fee_unavailable")
            else -> lang.raw("guild_dialogs.board.no_fee")
        }
    return cardText(guild.name, NamedTextColor.GOLD) + cardText(summary, NamedTextColor.GRAY) +
        Component.empty() + cardText(mode(profile.mode), NamedTextColor.WHITE) + cardText(price, NamedTextColor.GOLD)
}

private const val SUMMARY_LINE_LENGTH = 30

private fun cardText(
    text: String,
    color: NamedTextColor,
): List<Component> =
    text.codePoints().toArray().toList().chunked(SUMMARY_LINE_LENGTH).map { points ->
        Component.text(buildString { points.forEach(::appendCodePoint) }, color)
    }
