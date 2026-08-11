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

        val territoryGuildId = resolveTerritoryGuild(
            victim.world.uid,
            victim.location.blockX,
            victim.location.blockZ
        ) ?: return

        if (shouldCancelPvp(attacker.uniqueId, victim.uniqueId, territoryGuildId)) {
            logger.debug(
                "Claim PvP blocked: ${attacker.name} -> ${victim.name} in claim of guild $territoryGuildId"
            )
            event.isCancelled = true
        }
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
     * null when there is no claim / no guild ownership. Territory PvP rules
     * only apply to guild-owned claims.
     */
    internal fun resolveTerritoryGuild(worldId: UUID, blockX: Int, blockZ: Int): UUID? {
        val claimResult = getClaimAtPosition.execute(
            worldId,
            net.lumalyte.lg.domain.values.Position2D(blockX, blockZ)
        )
        val claim = when (claimResult) {
            is GetClaimAtPositionResult.Success -> claimResult.claim
            else -> return null // No claim — territory rules don't apply
        }
        val territoryGuildId = claim.teamId ?: return null
        if (guildService.getGuild(territoryGuildId) == null) return null
        return territoryGuildId
    }

    /**
     * Decides whether the attack must be cancelled in the given territory.
     */
    internal fun shouldCancelPvp(attackerId: UUID, victimId: UUID, territoryGuildId: UUID): Boolean =
        !combatService.canAttack(attackerId, victimId, territoryGuildId)
}
