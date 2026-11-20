package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import net.lumalyte.lg.domain.values.PrestigeNumeral
import org.slf4j.LoggerFactory
import java.util.UUID

/** REQ-188: operator settings; off until a webhook URL is configured. */
data class PrestigeDiscordSettings(
    val enabled: Boolean = false,
    val webhookUrl: String = "",
    val username: String = "Enthusia",
    val avatarUrl: String = "",
)

/** Embed text with `{guild}`, `{tier}`, `{player}` and `{reward}` placeholders (plain text, not MiniMessage). */
data class PrestigeDiscordText(
    val title: String,
    val actor: String,
    val kept: String,
    val first: String,
    val footer: String,
)

/**
 * REQ-188: posts one Discord embed per newly committed prestige. [send] receives
 * the webhook URL and JSON body and owns threading and timeouts; any failure is
 * logged and never reaches the prestige.
 */
class PrestigeDiscordPost(
    private val settings: () -> PrestigeDiscordSettings,
    private val text: () -> PrestigeDiscordText,
    private val guildName: (UUID) -> String?,
    private val playerName: (UUID) -> String?,
    private val rewardName: (String) -> String?,
    private val send: (url: String, json: String) -> Unit,
) : GuildPrestigeNotifier {
    private val logger = LoggerFactory.getLogger(PrestigeDiscordPost::class.java)

    override fun prestiged(completion: PrestigeCompletion) {
        try {
            val current = settings()
            if (!current.enabled || current.webhookUrl.isBlank()) return
            send(current.webhookUrl.trim(), body(completion, current, text()))
        } catch (error: Exception) {
            logger.warn("Discord prestige post failed for guild ${completion.guildId}", error)
        }
    }

    internal fun body(completion: PrestigeCompletion, current: PrestigeDiscordSettings, words: PrestigeDiscordText): String {
        val tier = PrestigeNumeral.of(completion.tier)
        val guild = guildName(completion.guildId) ?: completion.guildId.toString()
        val player = playerName(completion.actorId) ?: "Staff"
        val reward = rewardName(completion.retainedRewardId) ?: completion.retainedRewardId
        fun fill(template: String) = template.replace("{guild}", guild).replace("{tier}", tier)
            .replace("{player}", player).replace("{reward}", reward)
        val lines = buildList {
            add(fill(words.actor))
            add(fill(words.kept))
            if (completion.firstToReachTier) add(fill(words.first))
        }
        val embed = buildString {
            append("{\"title\":").append(json(fill(words.title)))
            append(",\"description\":").append(json(lines.joinToString("\n")))
            append(",\"color\":").append(TIER_COLOURS.getOrElse(completion.tier - 1) { TIER_COLOURS.last() })
            append(",\"footer\":{\"text\":").append(json(fill(words.footer))).append("}")
            append(",\"timestamp\":").append(json(completion.appliedAt.toString()))
            append("}")
        }
        return buildString {
            append("{\"username\":").append(json(current.username))
            if (current.avatarUrl.isNotBlank()) append(",\"avatar_url\":").append(json(current.avatarUrl.trim()))
            append(",\"allowed_mentions\":{\"parse\":[]}")
            append(",\"embeds\":[").append(embed).append("]}")
        }
    }

    private fun json(value: String): String = buildString {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private companion object {
        // Prestige I-VI: gold, gold, silver, silver, yellow, purple (matches the badge colours).
        val TIER_COLOURS = listOf(0xFFAA00, 0xFFAA00, 0xAAAAAA, 0xAAAAAA, 0xFFFF55, 0xFF55FF)
    }
}
