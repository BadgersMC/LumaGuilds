package net.lumalyte.lg.infrastructure.services

/**
 * Channel types for [LumaGuildsChannel], matching the `channel-type` values
 * defined in RoseChat's `channels.yml`:
 * - `GUILD`    → guild-only messages
 * - `ALLY`     → messages visible to allied guilds
 * - `MODCHAT`  → moderator-only guild messages
 */
enum class LumaGuildsChannelType {
    GUILD,
    ALLY,
    MODCHAT
}
