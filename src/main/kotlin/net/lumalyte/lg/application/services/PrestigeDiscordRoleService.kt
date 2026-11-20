package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.PrestigeAdministration
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.PrestigeRoleAwardRepository
import net.lumalyte.lg.config.DiscordPrestigeRolesConfig
import net.lumalyte.lg.domain.values.PrestigeRoleAward
import net.lumalyte.lg.domain.values.PrestigeRolePlan
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * REQ-298: Discord roles for guild prestige. When a guild first reaches a tier, everyone in it then earns
 * that tier's role for good (leaving later doesn't take it away). Each member holds only the role for
 * their highest earned tier. Unlinked members get theirs when they link their Discord account.
 */
class PrestigeDiscordRoleService(
    private val configService: ConfigService,
    private val awards: PrestigeRoleAwardRepository,
    private val history: PrestigeHistory,
    private val adjustments: PrestigeAdministration?,
    private val stints: MembershipHistoryRepository,
    private val memberService: MemberService,
    private val guildService: GuildService,
    private val gateway: DiscordGuildRoleGateway,
    private val executor: Executor,
    private val icon: (tier: Int, path: String) -> ByteArray? = { _, _ -> null },
) {
    private val logger = LoggerFactory.getLogger(PrestigeDiscordRoleService::class.java)

    private fun config(): DiscordPrestigeRolesConfig = configService.loadConfig().discordPrestigeRoles
    private fun active(): Boolean = config().let { it.enabled && it.roles.isNotEmpty() }

    /** A guild just reached [tier]: its members now earn that role. Recording runs even while Discord is down. */
    fun prestiged(guildId: UUID, tier: Int, at: Instant): CompletableFuture<Int> = async {
        if (!config().enabled) return@async 0
        // Only the first time: a tier re-reached after a staff lowering keeps the original roster.
        val first = PrestigeRolePlan.milestones(history.history(guildId), adjustments?.adjustments(guildId).orEmpty())[tier]
        if (first != null && first.isBefore(at)) return@async 0
        val roster = memberService.getGuildMembers(guildId).map { it.playerId }
        awards.record(roster.map { PrestigeRoleAward(it, guildId, tier, at) })
        roster.sumOf { sync(it) }
    }

    /** The member linked their Discord account: give them the role they earned. */
    fun linked(playerId: UUID): CompletableFuture<Int> = async { if (active()) sync(playerId) else 0 }

    /**
     * Startup and periodic catch-up: role icons, awards for prestiges earned before this existed (from
     * prestige history and membership history), then every awarded member's roles.
     */
    fun reconcileAll(): CompletableFuture<Int> = async {
        if (!active()) return@async 0
        val config = config()
        if (gateway.isAvailable()) icons(config)
        val added = backfill()
        if (added > 0) logger.info("Recorded $added prestige Discord role award(s) from history")
        if (!gateway.isAvailable()) return@async 0
        awards.players().sumOf { sync(it) }
    }

    internal fun backfill(): Int = guildService.getAllGuilds().sumOf { guild ->
        val reached = PrestigeRolePlan.milestones(
            history.history(guild.id),
            adjustments?.adjustments(guild.id).orEmpty(),
        )
        if (reached.isEmpty()) return@sumOf 0
        val guildStints = stints.getByGuild(guild.id)
        val current = memberService.getGuildMembers(guild.id).associate { it.playerId to it.joinedAt }
        awards.record(reached.flatMap { (tier, at) ->
            PrestigeRolePlan.rosterAt(at, guildStints, current).map { PrestigeRoleAward(it, guild.id, tier, at) }
        })
    }

    /** Gives the member their highest earned role and takes away their other prestige roles. Returns changes. */
    internal fun sync(playerId: UUID): Int {
        if (!gateway.isAvailable()) return 0
        val highest = awards.highestTier(playerId) ?: return 0
        val plan = PrestigeRolePlan.sync(highest, config().roles)
        val changes = listOfNotNull(plan.grant?.let { gateway.grantRole(playerId, it) }) +
            plan.revoke.map { gateway.revokeRole(playerId, it) }
        return changes.sumOf { future ->
            runCatching { future.join() }
                .onFailure { logger.warn("Prestige Discord role update failed for $playerId: ${it.message}") }
                .getOrNull()
                ?.let { if (it == DiscordMemberRoleResult.APPLIED || it == DiscordMemberRoleResult.REMOVED) 1 else 0 }
                ?: 0
        }
    }

    private fun icons(config: DiscordPrestigeRolesConfig) {
        if (config.iconPath.isBlank()) return
        config.roles.forEach { (tier, roleId) ->
            val png = icon(tier, config.iconPath.replace("<tier>", tier.toString())) ?: return@forEach
            runCatching { gateway.setRoleIconIfMissing(roleId, png).join() }
                .onSuccess { if (it) logger.info("Set the Prestige $tier Discord role icon") }
                .onFailure { logger.warn("Could not set the Prestige $tier Discord role icon: ${it.cause?.message ?: it.message}") }
        }
    }

    private fun <T> async(block: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(block, executor)
}
