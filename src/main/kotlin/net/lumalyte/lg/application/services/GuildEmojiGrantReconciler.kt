package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.EmojiGrantRepository
import net.lumalyte.lg.domain.entities.EmojiPermissionGrant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class EmojiGrantReconciliationResult(
    val granted: Int = 0,
    val revoked: Int = 0,
    val failed: Int = 0,
) {
    val successful: Boolean get() = failed == 0

    operator fun plus(other: EmojiGrantReconciliationResult) = EmojiGrantReconciliationResult(
        granted = granted + other.granted,
        revoked = revoked + other.revoked,
        failed = failed + other.failed,
    )
}

class GuildEmojiGrantReconciler(
    private val guildService: GuildService,
    private val memberService: MemberService,
    private val repository: EmojiGrantRepository,
    private val gateway: EmojiPermissionGateway,
) {
    private val lock = ReentrantLock()

    fun reconcileAll(mappings: Map<String, String>): EmojiGrantReconciliationResult = lock.withLock {
        val desired = buildMap<Pair<UUID, UUID>, EmojiPermissionGrant> {
            guildService.getAllGuilds().forEach { guild ->
                val permission = mappings[guild.name.lowercase(Locale.ROOT)] ?: return@forEach
                memberService.getGuildMembers(guild.id).forEach { member ->
                    put(member.playerId to guild.id, EmojiPermissionGrant(member.playerId, guild.id, permission))
                }
            }
        }
        reconcileDesired(desired, repository.getAll())
    }

    fun reconcileMembership(
        playerId: UUID,
        guildId: UUID,
        permission: String?,
    ): EmojiGrantReconciliationResult = lock.withLock {
        val desired = permission?.let { EmojiPermissionGrant(playerId, guildId, it) }
        reconcileDesired(
            desired = listOfNotNull(desired).associateBy { it.playerId to it.guildId },
            recordedGrants = listOfNotNull(repository.getForPlayerAndGuild(playerId, guildId)),
        )
    }

    fun removeMembership(playerId: UUID, guildId: UUID): EmojiGrantReconciliationResult = lock.withLock {
        reconcileDesired(
            desired = emptyMap(),
            recordedGrants = listOfNotNull(repository.getForPlayerAndGuild(playerId, guildId)),
        )
    }

    fun reconcileGuild(guildId: UUID, permission: String?): EmojiGrantReconciliationResult = lock.withLock {
        val desired = if (permission == null) {
            emptyMap()
        } else {
            memberService.getGuildMembers(guildId).associate { member ->
                (member.playerId to guildId) to EmojiPermissionGrant(member.playerId, guildId, permission)
            }
        }
        reconcileDesired(desired, repository.getForGuild(guildId))
    }

    fun removeGuild(guildId: UUID): EmojiGrantReconciliationResult = lock.withLock {
        reconcileDesired(emptyMap(), repository.getForGuild(guildId))
    }

    private fun reconcileDesired(
        desired: Map<Pair<UUID, UUID>, EmojiPermissionGrant>,
        recordedGrants: List<EmojiPermissionGrant>,
    ): EmojiGrantReconciliationResult {
        val recorded = recordedGrants.associateBy { it.playerId to it.guildId }
        var result = EmojiGrantReconciliationResult()

        recorded.filterKeys { it !in desired }.values.forEach { grant ->
            result += revokeAndDelete(grant)
        }
        recorded.keys.intersect(desired.keys).forEach { key ->
            val oldGrant = recorded.getValue(key)
            val newGrant = desired.getValue(key)
            if (oldGrant.permission != newGrant.permission) {
                val revoked = revokeAndDelete(oldGrant)
                result += revoked
                if (revoked.successful) result += grantAndRecord(newGrant)
            }
        }
        desired.filterKeys { it !in recorded }.values.forEach { grant ->
            result += grantAndRecord(grant)
        }
        return result
    }

    private fun grantAndRecord(grant: EmojiPermissionGrant): EmojiGrantReconciliationResult {
        if (!gateway.grant(grant.playerId, grant.permission)) return EmojiGrantReconciliationResult(failed = 1)
        if (!repository.upsert(grant)) {
            gateway.revoke(grant.playerId, grant.permission)
            return EmojiGrantReconciliationResult(failed = 1)
        }
        return EmojiGrantReconciliationResult(granted = 1)
    }

    private fun revokeAndDelete(grant: EmojiPermissionGrant): EmojiGrantReconciliationResult {
        val anotherOwnedGrantRequiresPermission = repository.getAll().any {
            it.playerId == grant.playerId &&
                it.permission == grant.permission &&
                it.guildId != grant.guildId
        }
        if (!anotherOwnedGrantRequiresPermission && !gateway.revoke(grant.playerId, grant.permission)) {
            return EmojiGrantReconciliationResult(failed = 1)
        }
        if (!repository.delete(grant.playerId, grant.guildId)) return EmojiGrantReconciliationResult(failed = 1)
        return EmojiGrantReconciliationResult(revoked = if (anotherOwnedGrantRequiresPermission) 0 else 1)
    }
}
