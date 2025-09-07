package net.lumalyte.lg.application.actions.claim.permission

import net.lumalyte.lg.application.persistence.ClaimPermissionRepository
import net.lumalyte.lg.domain.values.ClaimPermission
import java.util.*

class GetClaimPermissions(private val permissionRepository: ClaimPermissionRepository) {
    fun execute(claimId: UUID): List<ClaimPermission> {
        return permissionRepository.getByClaim(claimId).toList()
    }
}
