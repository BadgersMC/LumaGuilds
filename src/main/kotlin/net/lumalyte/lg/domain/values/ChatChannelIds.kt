package net.lumalyte.lg.domain.values

/**
 * RoseChat channel IDs shared across commands and the toggle listener.
 * Must match the channel IDs configured in RoseChat's channels.yml.
 */
object ChatChannelIds {
    /** RoseChat channel for guild-only messages. */
    const val GUILD = "guild"

    /** RoseChat channel for messages visible to allied guilds. */
    const val ALLY = "guild-ally"

    /** RoseChat channel for moderator-only messages. */
    const val MODCHAT = "guild-modchat"
}
