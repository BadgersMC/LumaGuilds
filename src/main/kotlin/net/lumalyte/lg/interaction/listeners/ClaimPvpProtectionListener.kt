package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.services.CombatService
import net.lumalyte.lg.application.services.GuildService
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Enforces PvP rules inside guild claims (REQ-007).
 *
 * When a player damages another player, the victim's location is resolved to a
 * claim; if the claim is owned by a guild, `CombatService.canAttack` decides
 * whether the attack is legal (peaceful-mode territory PvP disabled, hostile
 * relations, same-guild rules). Registered only when claims are enabled.
 *
 * The decision pipeline is split into internal functions so it can be tested
 * without Bukkit server state: `resolveTerritoryGuild` (location → guild claim)
 * and `shouldCancelPvp` (attacker/victim/territory → cancel?).
 */
class ClaimPvpProtectionListener : Listener, KoinComponent {

    private val combatService: CombatService by inject()
    private val getClaimAtPosition: GetClaimAtPosition by inject()
    private val guildService: GuildService by inject()

    private val logger = LoggerFactory.getLogger(ClaimPvpProtectionListener::class.java)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerDamage(event: EntityDamageByEntityEvent) {
        // Only player-vs-player damage is governed by claim territory PvP rules
        val victim = event.entity as? Player ?: return
        val damager = event.damager ?: return
        val attacker = resolveAttacker(damager) ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        when (val resolution = resolveTerritoryGuild(
            victim.world.uid,
            victim.location.blockX,
            victim.location.blockZ
        )) {
            // No claim — territory rules don't apply
            TerritoryResolution.NoClaim -> Unit

            // Claim storage unavailable — fail closed: never allow damage we
            // cannot verify is outside a protected claim.
            TerritoryResolution.StorageError -> {
                logger.error(
                    "Claim PvP lookup failed at ${victim.world.uid} (${victim.location.blockX}, ${victim.location.blockZ}) " +
                        "- cancelling damage from ${attacker.name} to ${victim.name} (fail closed)"
                )
                event.isCancelled = true
            }

            is TerritoryResolution.GuildClaim ->
                if (shouldCancelPvp(attacker.uniqueId, victim.uniqueId, resolution.guildId)) {
                    logger.debug(
                        "Claim PvP blocked: ${attacker.name} -> ${victim.name} in claim of guild ${resolution.guildId}"
                    )
                    event.isCancelled = true
                }
        }
    }

    /**
     * Outcome of resolving the guild that owns the claim at a block position.
     */
    internal sealed class TerritoryResolution {
        /** No claim exists at the position — territory rules don't apply. */
        object NoClaim : TerritoryResolution()

        /** Claim storage failed — the lookup could not be completed. */
        object StorageError : TerritoryResolution()

        /** A guild-owned claim exists at the position. */
        data class GuildClaim(val guildId: UUID) : TerritoryResolution()
    }

    /**
     * Resolves the attacker from a direct hit or a projectile's shooter.
     * Returns null when there is no player actor (e.g. environmental damage).
     */
    internal fun resolveAttacker(damager: Any): Player? = when (damager) {
        is Player -> damager
        is Projectile -> damager.shooter as? Player
        else -> null
    }

    /**
     * Resolves the guild that owns the claim at the given block position, or
     * NoClaim when there is no claim / no guild ownership. Territory PvP rules
     * only apply to guild-owned claims. StorageError is kept distinct so
     * callers can fail closed.
     */
    internal fun resolveTerritoryGuild(worldId: UUID, blockX: Int, blockZ: Int): TerritoryResolution {
        val claimResult = getClaimAtPosition.execute(
            worldId,
            net.lumalyte.lg.domain.values.Position2D(blockX, blockZ)
        )
        return when (claimResult) {
            is GetClaimAtPositionResult.Success -> {
                val territoryGuildId = claimResult.claim.teamId
                if (territoryGuildId != null && guildService.getGuild(territoryGuildId) != null) {
                    TerritoryResolution.GuildClaim(territoryGuildId)
                } else {
                    TerritoryResolution.NoClaim
                }
            }
            GetClaimAtPositionResult.NoClaimFound -> TerritoryResolution.NoClaim
            GetClaimAtPositionResult.StorageError -> TerritoryResolution.StorageError
        }
    }

    /**
     * Decides whether the attack must be cancelled in the given territory.
     */
    internal fun shouldCancelPvp(attackerId: UUID, victimId: UUID, territoryGuildId: UUID): Boolean =
        !combatService.canAttack(attackerId, victimId, territoryGuildId)
}
