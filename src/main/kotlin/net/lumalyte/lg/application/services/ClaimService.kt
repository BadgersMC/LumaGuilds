package net.lumalyte.lg.application.services

import net.lumalyte.lg.domain.entities.Claim
import org.bukkit.World
import java.util.UUID

/**
 * Stub implementation of ClaimService for Brigadier command migration.
 * TODO: Replace with full implementation when claim system is ready.
 */
interface ClaimService {
    fun getClaimAtPosition(world: World, x: Int, y: Int, z: Int): Claim?
    fun canPlayerManageClaim(playerId: UUID, claim: Claim): Boolean
    fun getTrustedPlayers(claim: Claim): List<UUID>
    fun listPlayerClaims(playerId: UUID): List<Claim>
}

/**
 * Stub implementation that returns null/empty results.
 */
class ClaimServiceStub : ClaimService {
    override fun getClaimAtPosition(world: World, x: Int, y: Int, z: Int): Claim? = null
    override fun canPlayerManageClaim(playerId: UUID, claim: Claim): Boolean = false
    override fun getTrustedPlayers(claim: Claim): List<UUID> = emptyList()
    override fun listPlayerClaims(playerId: UUID): List<Claim> = emptyList()
}
