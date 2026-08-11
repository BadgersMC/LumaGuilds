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

/**
 * Enforces PvP rules inside guild claims (REQ-007).
 *
 * When a player damages another player, the victim's location is resolved to a
 * claim; if the claim is owned by a guild, `CombatService.canAttack` decides
 * whether the attack is legal (peaceful-mode territory PvP disabled, hostile
 * relations, same-guild rules). Registered only when claims are enabled.
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
        val attacker = when (damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> return
        } ?: return
        if (attacker.uniqueId == victim.uniqueId) return

        // Resolve the claim at the victim's location (if any)
        val claimResult = getClaimAtPosition.execute(
            victim.world.uid,
            net.lumalyte.lg.domain.values.Position2D(victim.location.blockX, victim.location.blockZ)
        )
        val claim = when (claimResult) {
            is GetClaimAtPositionResult.Success -> claimResult.claim
            else -> return // No claim — territory rules don't apply
        }

        // Claims may belong to a player or a guild (teamId). Territory PvP rules
        // only apply to guild-owned claims.
        val territoryGuildId = claim.teamId ?: return
        if (guildService.getGuild(territoryGuildId) == null) return

        if (!combatService.canAttack(attacker.uniqueId, victim.uniqueId, territoryGuildId)) {
            logger.debug(
                "Claim PvP blocked: ${attacker.name} -> ${victim.name} in claim of guild $territoryGuildId"
            )
            event.isCancelled = true
        }
    }
}
