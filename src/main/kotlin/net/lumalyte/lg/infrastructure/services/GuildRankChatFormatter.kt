package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.utils.RankNameContent

internal object GuildRankChatFormatter {
    const val RANK_PLACEHOLDER = "%lumaguilds_guild_rank%"
    const val DEFAULT_RANK_FORMAT = "&8[&b<rank>&8]&f "

    private val playerTokens = listOf(
        "{player}",
        "%player_nickname%",
        "%player_displayname%",
        "%player_name%",
    )

    fun decorate(
        format: String?,
        rankFormatTemplate: String? = null,
    ): String? {
        if (format.isNullOrEmpty()) return format
        if (format.contains(RANK_PLACEHOLDER, ignoreCase = true)) return format

        val playerToken = playerTokens.firstOrNull { format.contains(it) }
            ?: return format
        val rankFormat = normalizeRankFormat(rankFormatTemplate)

        return format.replaceFirst(playerToken, "$rankFormat$playerToken")
    }
    fun render(format: String, rankName: String?, visible: Boolean, template: String? = null): String {
        if (!visible || rankName == null) {
            val withoutPrefix = format.replace(normalizeRankFormat(template), "")
            return withoutPrefix.replace(
                Regex("\\[[^\\[\\]\\r\\n]*%lumaguilds_guild_rank%[^\\[\\]\\r\\n]*]\\s*", RegexOption.IGNORE_CASE),
                "",
            ).replace(RANK_PLACEHOLDER, "", ignoreCase = true)
        }
        return format.replace(RANK_PLACEHOLDER, RankNameContent.legacy(rankName) + "§r", ignoreCase = true)
    }

    private fun normalizeRankFormat(template: String?): String {
        val configured = template
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_RANK_FORMAT

        return when {
            configured.contains(RANK_PLACEHOLDER, ignoreCase = true) -> configured
            configured.contains("<rank>") -> configured.replace("<rank>", RANK_PLACEHOLDER)
            else -> DEFAULT_RANK_FORMAT.replace("<rank>", RANK_PLACEHOLDER)
        }
    }
}
