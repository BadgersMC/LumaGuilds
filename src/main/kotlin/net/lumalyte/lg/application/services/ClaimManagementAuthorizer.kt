package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.RankPermission
import java.util.UUID

/**
 * Authoritative claim-management access for personal and guild-owned claims.
 *
 * The original personal owner remains authorized after conversion, while guild
 * members are authorized from the current guild/rank state rather than the
 * historical claim.playerId.
 */
class ClaimManagementAuthorizer(
    private val memberService: MemberService,
) {
    fun hasPermission(playerId: UUID, claim: Claim, permission: RankPermission): Boolean {
        if (playerId == claim.playerId) return true
        val guildId = claim.teamId ?: return false
        return memberService.hasPermission(playerId, guildId, permission)
    }

    fun hasAnyManagementPermission(playerId: UUID, claim: Claim): Boolean =
        MANAGEMENT_PERMISSIONS.any { hasPermission(playerId, claim, it) }

    private companion object {
        val MANAGEMENT_PERMISSIONS = setOf(
            RankPermission.MANAGE_CLAIMS,
            RankPermission.MANAGE_FLAGS,
            RankPermission.MANAGE_PERMISSIONS,
            RankPermission.DELETE_CLAIMS,
        )
    }
}
