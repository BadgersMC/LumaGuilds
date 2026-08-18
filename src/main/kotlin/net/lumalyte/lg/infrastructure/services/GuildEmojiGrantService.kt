package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.ConfigService
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Manages automatic emoji permission grants for guild members based on
 * the [guild.emoji_grants] config section.
 *
 * When a guild name is mapped to a permission node, all current and future
 * members of that guild are granted the permission via LuckPerms console
 * commands. The permission is revoked when a member leaves or the guild is
 * disbanded.
 */
class GuildEmojiGrantService(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val configService: ConfigService,
    private val plugin: Plugin
) {
    private val logger = LoggerFactory.getLogger(GuildEmojiGrantService::class.java)

    /**
     * Returns the emoji permission node configured for a guild by its ID.
     * Loads the guild from the repository; returns null if the guild no longer exists.
     */
    fun resolveEmojiGrant(guildId: UUID): String? {
        val guild = guildService.getGuild(guildId) ?: return null
        return resolveEmojiGrantByName(guild.name)
    }

    /**
     * Returns the emoji permission node configured for a guild by its name.
     * Case-insensitive matching. Does not require the guild to exist in the repository,
     * so it can be called after a guild has been deleted (e.g. on disband).
     */
    fun resolveEmojiGrantByName(guildName: String): String? {
        val grants = configService.loadConfig().guild.emojiGrants
        return grants[guildName.lowercase()]
    }

    /**
     * Grants emoji permissions to all current members of every guild that
     * has a configured emoji grant. Called on plugin enable.
     */
    fun grantAll() {
        val grants = configService.loadConfig().guild.emojiGrants
        if (grants.isEmpty()) return

        val allGuilds = guildService.getAllGuilds()
        for (guild in allGuilds) {
            val permission = grants[guild.name.lowercase()] ?: continue
            val members = memberService.getGuildMembers(guild.id)
            for (member in members) {
                grantPermission(member.playerId, permission)
            }
        }
        logger.info("Processed emoji grants for ${allGuilds.count { grants.containsKey(it.name.lowercase()) }} guild(s)")
    }

    /**
     * Grants the configured emoji permission to all current members of a guild.
     */
    fun grantForGuild(guildId: UUID) {
        val permission = resolveEmojiGrant(guildId) ?: return
        val members = memberService.getGuildMembers(guildId)
        for (member in members) {
            grantPermission(member.playerId, permission)
        }
    }

    /**
     * Revokes the configured emoji permission from all current members of a guild.
     */
    fun revokeForGuild(guildId: UUID) {
        val permission = resolveEmojiGrant(guildId) ?: return
        val members = memberService.getGuildMembers(guildId)
        for (member in members) {
            revokePermission(member.playerId, permission)
        }
    }

    /**
     * Grants the configured emoji permission to a specific player for their guild.
     */
    fun grantForPlayer(playerId: UUID, guildId: UUID) {
        val permission = resolveEmojiGrant(guildId) ?: return
        grantPermission(playerId, permission)
    }

    /**
     * Revokes the configured emoji permission from a specific player for their guild.
     */
    fun revokeForPlayer(playerId: UUID, guildId: UUID) {
        val permission = resolveEmojiGrant(guildId) ?: return
        revokePermission(playerId, permission)
    }

    /**
     * Grants a raw permission node to a player via LuckPerms console command.
     * Runs on the server's main thread because Bukkit.dispatchCommand is not thread-safe.
     */
    fun grantPermission(playerId: UUID, permission: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val cmd = "lp user $playerId permission set $permission true"
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        })
    }

    /**
     * Revokes a raw permission node from a player via LuckPerms console command.
     * Runs on the server's main thread because Bukkit.dispatchCommand is not thread-safe.
     */
    fun revokePermission(playerId: UUID, permission: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val cmd = "lp user $playerId permission unset $permission"
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        })
    }
}