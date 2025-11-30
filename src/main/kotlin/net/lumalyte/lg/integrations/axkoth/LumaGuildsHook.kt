package net.lumalyte.lg.integrations.axkoth

import com.artillexstudios.axkoth.hooks.teams.TeamHook
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Hook implementation for AxKoth integration with LumaGuilds.
 * Allows AxKoth to recognize LumaGuilds as a team provider for KOTH events.
 *
 * Registration:
 * Since AxKoth is closed-source, register this hook via their API:
 * ```kotlin
 * AxKothAPI.registerTeamHook(plugin, LumaGuildsHook())
 * ```
 */
class LumaGuildsHook : TeamHook, KoinComponent {

    private val guildService: GuildService by inject()
    private val memberService: MemberService by inject()
    private val logger = LoggerFactory.getLogger(LumaGuildsHook::class.java)

    /**
     * Called by AxKoth when the hook is initialized.
     * Can be used for any setup logic.
     */
    override fun setup() {
        logger.info("LumaGuilds hook for AxKoth initialized successfully")
    }

    /**
     * Returns the name of this team provider.
     * Used by AxKoth for logging and identification.
     */
    override fun getName(): String {
        return "LumaGuilds"
    }

    /**
     * Gets the team/guild tag for a player.
     * Returns the guild tag if set, otherwise falls back to guild name.
     *
     * @param player The player to check
     * @return The guild tag (or name if tag is null) if player is in a guild, null otherwise
     */
    override fun getTeamOfPlayer(player: Player): String? {
        return try {
            val playerId = player.uniqueId

            // Get player's guild (players can only be in one guild at a time)
            val guilds = guildService.getPlayerGuilds(playerId)

            if (guilds.isEmpty()) {
                return null
            }

            val guild = guilds.first()
            // Return tag if set, otherwise return name
            guild.tag?.takeIf { it.isNotBlank() } ?: guild.name

        } catch (e: Exception) {
            // Integration hook - catching all exceptions for compatibility
            logger.error("Error getting guild for player ${player.name}", e)
            null
        }
    }

    /**
     * Gets all members of a team/guild.
     *
     * @param teamName The guild name
     * @return List of all guild members as OfflinePlayer, or empty list if guild not found
     */
    override fun getTeamMembers(teamName: String): List<OfflinePlayer> {
        return try {
            // Find guild by name
            val allGuilds = guildService.getAllGuilds()
            val guild = allGuilds.find { it.name.equals(teamName, ignoreCase = true) }

            if (guild == null) {
                logger.debug("Guild not found: $teamName")
                return emptyList()
            }

            // Get all guild members
            val members = memberService.getGuildMembers(guild.id)

            // Convert to OfflinePlayer list
            members.mapNotNull { member ->
                try {
                    Bukkit.getOfflinePlayer(member.playerId)
                } catch (e: Exception) {
            // Integration hook - catching all exceptions for compatibility
                    logger.warn("Could not get OfflinePlayer for UUID ${member.playerId}", e)
                    null
                }
            }

        } catch (e: Exception) {
            // Integration hook - catching all exceptions for compatibility
            logger.error("Error getting members for guild $teamName", e)
            emptyList()
        }
    }

    /**
     * Gets the team/guild name by exact name lookup.
     * This is used by AxKoth to validate team names.
     *
     * @param teamName The guild name to look up
     * @return The guild name if found (case-corrected), null otherwise
     */
    override fun getTeamByName(teamName: String): String? {
        return try {
            val allGuilds = guildService.getAllGuilds()
            val guild = allGuilds.find { it.name.equals(teamName, ignoreCase = true) }
            guild?.name
        } catch (e: Exception) {
            // Integration hook - catching all exceptions for compatibility
            logger.error("Error looking up guild by name: $teamName", e)
            null
        }
    }
}
