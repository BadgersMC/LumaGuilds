package net.lumalyte.lg.infrastructure.services

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
