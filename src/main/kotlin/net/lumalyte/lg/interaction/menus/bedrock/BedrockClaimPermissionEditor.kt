package net.lumalyte.lg.interaction.menus.bedrock

import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.UUID

/** Applies the difference between the displayed and submitted Bedrock permission toggles. */
class BedrockClaimPermissionEditor(
    private val grantWide: (UUID, ClaimPermission) -> Boolean,
    private val revokeWide: (UUID, ClaimPermission) -> Boolean,
    private val grantPlayer: (UUID, UUID, ClaimPermission) -> Boolean,
    private val revokePlayer: (UUID, UUID, ClaimPermission) -> Boolean
) {
    fun saveWide(claimId: UUID, current: Set<ClaimPermission>, submitted: Set<ClaimPermission>): Boolean {
        for (permission in submitted - current) if (!grantWide(claimId, permission)) return false
        for (permission in current - submitted) if (!revokeWide(claimId, permission)) return false
        return true
    }

    fun savePlayer(
        claimId: UUID,
        playerId: UUID,
        current: Set<ClaimPermission>,
        submitted: Set<ClaimPermission>
    ): Boolean {
        for (permission in submitted - current) if (!grantPlayer(claimId, playerId, permission)) return false
        for (permission in current - submitted) if (!revokePlayer(claimId, playerId, permission)) return false
        return true
    }
}
