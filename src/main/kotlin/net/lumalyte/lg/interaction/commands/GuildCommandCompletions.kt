package net.lumalyte.lg.interaction.commands

import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.accessibleHomeNames
import net.lumalyte.lg.domain.entities.RelationType
import net.lumalyte.lg.infrastructure.services.GuildInvitationManager
import net.lumalyte.lg.infrastructure.services.NexoEmojiService
import net.lumalyte.lg.utils.ColorCodeUtils
import net.lumalyte.lg.utils.GuildResolver
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Framework-free `/guild` completion sources shared by the transitional ACF completion ids
 * (`@allplayers`, `@guildmembers`, ...) and the inactive Nexus suggestion providers.
 * Collaborators resolve lazily on first completion, as the ACF lambdas did.
 */
internal class GuildCommandCompletions(
    guildService: Lazy<GuildService>,
    memberService: Lazy<MemberService>,
    relationService: Lazy<RelationService>,
    nexoEmojiService: Lazy<NexoEmojiService>,
) {
    private val guildService by guildService
    private val memberService by memberService
    private val relationService by relationService
    private val nexoEmojiService by nexoEmojiService

    /** `@allplayers`: every online player name. */
    fun allPlayers(): List<String> = Bukkit.getOnlinePlayers().map { it.name }

    /** ACF built-in `@players`: online players visible to the sender. */
    fun visiblePlayers(sender: CommandSender): List<String> =
        Bukkit.getOnlinePlayers().filter { sender !is Player || sender.canSee(it) }.map { it.name }

    /** `@guildmembers`: other members of the player's guild, including offline names. */
    fun guildMembers(player: Player): List<String> {
        val guild = guildService.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return emptyList()
        return memberService.getGuildMembers(guild.id)
            .filter { it.playerId != player.uniqueId }
            .mapNotNull { Bukkit.getOfflinePlayer(it.playerId).name }
    }

    /** `@guilds`. */
    fun guilds(): List<String> = GuildResolver.suggestions(guildService)

    /** `@pendinginvites`: guilds that invited the executing player. */
    fun pendingInvites(player: Player): List<String> =
        GuildInvitationManager.getInvites(player.uniqueId)
            .map { (_, guildName) -> ColorCodeUtils.stripAllFormatting(guildName).trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    /** `@guildsorplayers`: guild names or online player names (used by `/g info`). */
    fun guildsOrPlayers(): List<String> {
        val guildNames = GuildResolver.suggestions(guildService)
        val playerNames = Bukkit.getOnlinePlayers().mapNotNull { it.name }
        return (guildNames + playerNames).distinct().sorted()
    }

    /**
     * `@allyguilds`: ally-homes the player may teleport to — the own guild's (gated by
     * canUseOwnAllyHome) plus every active ALLY with an ally-home passing canUseAllyHome.
     */
    fun allyGuilds(player: Player): List<String> {
        val sourceGuild = guildService.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return emptyList()
        val results = mutableListOf<String>()
        if (sourceGuild.allyHome != null && guildService.canUseOwnAllyHome(player.uniqueId, sourceGuild.id)) {
            results.add(sourceGuild.name)
        }
        relationService.getGuildRelationsByType(sourceGuild.id, RelationType.ALLY).mapNotNullTo(results) { rel ->
            val other = guildService.getGuild(rel.getOtherGuild(sourceGuild.id)) ?: return@mapNotNullTo null
            if (other.allyHome == null) return@mapNotNullTo null
            if (!guildService.canUseAllyHome(player.uniqueId, sourceGuild.id, other.id)) return@mapNotNullTo null
            other.name
        }
        return results.distinct().sorted()
    }

    /** `@guildhomes`: every home of the player's guild. */
    fun guildHomes(player: Player): List<String> {
        val guild = guildService.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return emptyList()
        return guildService.getHomes(guild.id).homeNames.toList()
    }

    /** `@guildaccessiblehomes`: homes the player's current rank may use (server-thread caches). */
    fun accessibleHomes(player: Player): List<String> {
        val guild = guildService.getPlayerGuilds(player.uniqueId).firstOrNull() ?: return emptyList()
        return guildService.accessibleHomeNames(player.uniqueId, guild.id)
    }

    /** `@unlockedemojis`: `:name:` for every emoji the player may use. */
    fun unlockedEmojis(player: Player): List<String> {
        val guildId = guildService.getPlayerGuilds(player.uniqueId).firstOrNull()?.id
        return nexoEmojiService.getPlayerUnlockedEmojis(player, guildId).map { ":$it:" }
    }

    companion object {
        val ON_OFF = listOf("on", "off")
        val ON_OFF_TOGGLE = listOf("on", "off", "toggle")
        val GUILD_MODES = listOf("peaceful", "hostile")
    }
}
