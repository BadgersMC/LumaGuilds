package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.GuildDiscordRoleRepository
import net.lumalyte.lg.config.DiscordGuildRolesConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildDiscordRoleLink
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

data class DiscordGuildRoleSyncSummary(
    val guildsReconciled: Int = 0,
    val rolesCreated: Int = 0,
    val memberRolesApplied: Int = 0,
    val memberRolesRemoved: Int = 0,
    val skippedMembers: Int = 0,
    val failures: Int = 0,
) {
    operator fun plus(other: DiscordGuildRoleSyncSummary) = DiscordGuildRoleSyncSummary(
        guildsReconciled + other.guildsReconciled,
        rolesCreated + other.rolesCreated,
        memberRolesApplied + other.memberRolesApplied,
        memberRolesRemoved + other.memberRolesRemoved,
        skippedMembers + other.skippedMembers,
        failures + other.failures,
    )
}

class GuildDiscordRoleService(
    private val configService: ConfigService,
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val repository: GuildDiscordRoleRepository,
    private val gateway: DiscordGuildRoleGateway,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(GuildDiscordRoleService::class.java)
    private val ensureInFlight = ConcurrentHashMap<UUID, CompletableFuture<EnsuredRole>>()
    private val memberSyncInFlight =
        ConcurrentHashMap<MemberRoleKey, CompletableFuture<DiscordGuildRoleSyncSummary>>()

    fun reconcileAll(): CompletableFuture<DiscordGuildRoleSyncSummary> {
        val config = config()
        if (!config.enabled || !gateway.isAvailable()) return completed(DiscordGuildRoleSyncSummary())

        val liveGuilds = guildService.getAllGuilds().associateBy { it.id }
        val futures = mutableListOf<CompletableFuture<DiscordGuildRoleSyncSummary>>()

        repository.getAll().forEach { link ->
            val guild = liveGuilds[link.guildId]
            if (guild == null) {
                futures += deleteOrphan(link)
            } else {
                futures += reconcileGuild(guild.id)
            }
        }

        liveGuilds.values
            .filter { repository.get(it.id) == null }
            .forEach { futures += reconcileGuild(it.id) }

        return combine(futures)
    }

    fun reconcileGuild(guildId: UUID): CompletableFuture<DiscordGuildRoleSyncSummary> {
        val config = config()
        if (!config.enabled || !gateway.isAvailable()) return completed(DiscordGuildRoleSyncSummary())
        val guild = guildService.getGuild(guildId) ?: return completed(DiscordGuildRoleSyncSummary())

        return ensureRole(guild, config).thenCompose { ensured ->
            val allowedPlayerIds = memberService.getGuildMembers(guild.id)
                .mapTo(linkedSetOf()) { it.playerId }
            gateway.revokeUnexpectedRoleMembers(ensured.roleId, allowedPlayerIds)
                .thenCompose { removed ->
                    // Re-read after stale-holder cleanup. If a member joined while the
                    // cleanup was in flight, this post-cleanup grant repairs any role
                    // that the stale roster may just have revoked.
                    val memberFutures = memberService.getGuildMembers(guild.id).map { member ->
                        serializeMemberUpdate(guild.id, member.playerId) {
                            gateway.grantRole(member.playerId, ensured.roleId)
                                .handle { result, error -> memberResult(result, error, grant = true) }
                        }
                    }
                    combine(memberFutures).thenApply { memberSummary ->
                        memberSummary.copy(
                            guildsReconciled = memberSummary.guildsReconciled + 1,
                            rolesCreated = memberSummary.rolesCreated + if (ensured.created) 1 else 0,
                            memberRolesRemoved = memberSummary.memberRolesRemoved + removed,
                        )
                    }
                }
        }.exceptionally { error ->
            logger.warn("Discord role reconciliation failed for guild $guildId", unwrap(error))
            DiscordGuildRoleSyncSummary(failures = 1)
        }
    }

    fun memberJoined(
        guildId: UUID,
        playerId: UUID,
    ): CompletableFuture<DiscordGuildRoleSyncSummary> =
        serializeMemberUpdate(guildId, playerId) {
            val config = config()
            if (!config.enabled || !gateway.isAvailable()) {
                return@serializeMemberUpdate completed(DiscordGuildRoleSyncSummary())
            }
            val guild = guildService.getGuild(guildId)
                ?: return@serializeMemberUpdate completed(DiscordGuildRoleSyncSummary())

            ensureRole(guild, config)
                .thenCompose { ensured ->
                    gateway.grantRole(playerId, ensured.roleId).thenApply { result ->
                        memberResult(result, null, grant = true)
                            .copy(rolesCreated = if (ensured.created) 1 else 0)
                    }
                }
                .exceptionally { error ->
                    logger.warn(
                        "Failed to grant Discord guild role to player $playerId for guild $guildId",
                        unwrap(error),
                    )
                    DiscordGuildRoleSyncSummary(failures = 1)
                }
        }

    fun memberRemoved(
        guildId: UUID,
        playerId: UUID,
    ): CompletableFuture<DiscordGuildRoleSyncSummary> =
        serializeMemberUpdate(guildId, playerId) {
            val config = config()
            if (!config.enabled || !gateway.isAvailable()) {
                return@serializeMemberUpdate completed(DiscordGuildRoleSyncSummary())
            }
            val link = repository.get(guildId)
                ?: return@serializeMemberUpdate completed(DiscordGuildRoleSyncSummary())

            gateway.revokeRole(playerId, link.discordRoleId)
                .thenApply { memberResult(it, null, grant = false) }
                .exceptionally { error ->
                    logger.warn(
                        "Failed to revoke Discord guild role from player $playerId for guild $guildId",
                        unwrap(error),
                    )
                    DiscordGuildRoleSyncSummary(failures = 1)
                }
        }

    fun discordAccountLinked(playerId: UUID): CompletableFuture<DiscordGuildRoleSyncSummary> {
        val config = config()
        if (!config.enabled || !gateway.isAvailable()) return completed(DiscordGuildRoleSyncSummary())
        return combine(memberService.getPlayerGuilds(playerId).map { guildId ->
            memberJoined(guildId, playerId)
        })
    }

    fun discordAccountUnlinked(
        playerId: UUID,
        discordId: String,
    ): CompletableFuture<DiscordGuildRoleSyncSummary> {
        val config = config()
        if (!config.enabled || !gateway.isAvailable()) return completed(DiscordGuildRoleSyncSummary())
        val futures = memberService.getPlayerGuilds(playerId).mapNotNull { guildId ->
            val link = repository.get(guildId) ?: return@mapNotNull null
            serializeMemberUpdate(guildId, playerId) {
                gateway.revokeRoleByDiscordId(discordId, link.discordRoleId)
                    .handle { result, error -> memberResult(result, error, grant = false) }
            }
        }
        return combine(futures)
    }

    fun guildRenamed(guildId: UUID): CompletableFuture<DiscordGuildRoleSyncSummary> = reconcileGuild(guildId)

    fun guildDisbanded(guildId: UUID): CompletableFuture<DiscordGuildRoleSyncSummary> {
        val config = config()
        if (!config.enabled || !gateway.isAvailable()) return completed(DiscordGuildRoleSyncSummary())
        val link = repository.get(guildId) ?: return completed(DiscordGuildRoleSyncSummary())
        return gateway.deleteRole(link.discordRoleId).handle { deleted, error ->
            if (error != null || deleted != true || !repository.delete(guildId)) {
                logger.warn("Failed to fully clean Discord role for disbanded guild $guildId", error?.let(::unwrap))
                DiscordGuildRoleSyncSummary(failures = 1)
            } else {
                DiscordGuildRoleSyncSummary()
            }
        }
    }

    private fun ensureRole(guild: Guild, config: DiscordGuildRolesConfig): CompletableFuture<EnsuredRole> {
        ensureInFlight[guild.id]?.let { return it }

        val claimed = CompletableFuture<EnsuredRole>()
        val winner = ensureInFlight.putIfAbsent(guild.id, claimed)
        if (winner != null) return winner

        try {
            doEnsureRole(guild, config).whenComplete { ensured, error ->
                if (error == null) {
                    claimed.complete(ensured)
                } else {
                    claimed.completeExceptionally(unwrap(error))
                }
                ensureInFlight.remove(guild.id, claimed)
            }
        } catch (error: Throwable) {
            claimed.completeExceptionally(error)
            ensureInFlight.remove(guild.id, claimed)
        }
        return claimed
    }

    private fun doEnsureRole(guild: Guild, config: DiscordGuildRolesConfig): CompletableFuture<EnsuredRole> {
        val existing = repository.get(guild.id)
        val roleName = renderRoleName(config.roleNameFormat, guild.name)
        return gateway.ensureRole(existing?.discordRoleId, roleName).thenCompose { ensured ->
            val linkChanged = existing == null || existing.discordRoleId != ensured.roleId
            if (!linkChanged) {
                completed(EnsuredRole(ensured.roleId, ensured.created))
            } else {
                val link = GuildDiscordRoleLink(
                    guildId = guild.id,
                    discordRoleId = ensured.roleId,
                    unlockedAt = existing?.unlockedAt ?: clock.instant(),
                )
                if (repository.upsert(link)) {
                    completed(EnsuredRole(ensured.roleId, ensured.created))
                } else if (ensured.created) {
                    gateway.deleteRole(ensured.roleId).thenCompose {
                        failed(IllegalStateException("Discord role was created but its durable link could not be persisted"))
                    }
                } else {
                    failed(IllegalStateException("Discord role link could not be persisted"))
                }
            }
        }
    }

    private fun serializeMemberUpdate(
        guildId: UUID,
        playerId: UUID,
        operation: () -> CompletableFuture<DiscordGuildRoleSyncSummary>,
    ): CompletableFuture<DiscordGuildRoleSyncSummary> {
        val key = MemberRoleKey(guildId, playerId)
        var queued: CompletableFuture<DiscordGuildRoleSyncSummary>? = null

        memberSyncInFlight.compute(key) { _, previous ->
            val ready = previous
                ?.handle { _, _ -> Unit }
                ?: completed(Unit)
            val next = ready.thenCompose {
                try {
                    operation()
                } catch (error: Throwable) {
                    failed(error)
                }
            }
            queued = next
            next
        }

        val future = requireNotNull(queued)
        future.whenComplete { _, _ -> memberSyncInFlight.remove(key, future) }
        return future
    }

    private fun deleteOrphan(link: GuildDiscordRoleLink): CompletableFuture<DiscordGuildRoleSyncSummary> =
        gateway.deleteRole(link.discordRoleId).handle { deleted, error ->
            if (error == null && deleted == true && repository.delete(link.guildId)) {
                DiscordGuildRoleSyncSummary()
            } else {
                logger.warn("Failed to remove orphan Discord role link for guild ${link.guildId}", error?.let(::unwrap))
                DiscordGuildRoleSyncSummary(failures = 1)
            }
        }

    internal fun renderRoleName(format: String, guildName: String): String =
        format.replace("<guild>", guildName)
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .take(100)
            .ifEmpty { "Guild" }

    private fun memberResult(
        result: DiscordMemberRoleResult?,
        error: Throwable?,
        grant: Boolean,
    ): DiscordGuildRoleSyncSummary {
        if (error != null || result == null) return DiscordGuildRoleSyncSummary(failures = 1)
        return when (result) {
            DiscordMemberRoleResult.APPLIED -> DiscordGuildRoleSyncSummary(memberRolesApplied = 1)
            DiscordMemberRoleResult.REMOVED -> DiscordGuildRoleSyncSummary(memberRolesRemoved = 1)
            DiscordMemberRoleResult.ALREADY_PRESENT,
            DiscordMemberRoleResult.ALREADY_ABSENT,
            DiscordMemberRoleResult.PLAYER_UNLINKED,
            DiscordMemberRoleResult.PLAYER_NOT_IN_DISCORD_GUILD -> DiscordGuildRoleSyncSummary(skippedMembers = 1)
            DiscordMemberRoleResult.ROLE_MISSING -> {
                if (grant) {
                    logger.warn("Discord guild role disappeared during grant reconciliation")
                    DiscordGuildRoleSyncSummary(failures = 1)
                } else {
                    // If the role itself was deleted, the departing player cannot still hold it.
                    // Startup/guild reconciliation will recreate and relink the role if the guild remains eligible.
                    DiscordGuildRoleSyncSummary(skippedMembers = 1)
                }
            }
        }
    }

    private fun combine(futures: Collection<CompletableFuture<DiscordGuildRoleSyncSummary>>): CompletableFuture<DiscordGuildRoleSyncSummary> {
        if (futures.isEmpty()) return completed(DiscordGuildRoleSyncSummary())
        return CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
            futures.fold(DiscordGuildRoleSyncSummary()) { total, future -> total + future.join() }
        }
    }

    private fun config(): DiscordGuildRolesConfig = configService.loadConfig().discordGuildRoles

    private fun unwrap(error: Throwable): Throwable = error.cause ?: error

    private fun <T> completed(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)

    private fun <T> failed(error: Throwable): CompletableFuture<T> =
        CompletableFuture<T>().also { it.completeExceptionally(error) }

    private data class EnsuredRole(val roleId: String, val created: Boolean)
    private data class MemberRoleKey(val guildId: UUID, val playerId: UUID)
}
