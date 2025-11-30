package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.WarStats
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Tracks PvP kills during guild wars and updates war statistics.
 * This listener ensures that wars can progress and end based on kill objectives.
 */
class WarKillTrackingListener : Listener, KoinComponent {

    private val warService: WarService by inject()
    private val memberService: MemberService by inject()

    private val logger = LoggerFactory.getLogger(WarKillTrackingListener::class.java)

    companion object {
        /**
         * Well-known UUID representing the system actor for automated war endings.
         * This allows for consistent tracking and auditing of system actions.
         */
        private val SYSTEM_ACTOR = java.util.UUID(0, 0) // 00000000-0000-0000-0000-000000000000
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        try {
            val victim = event.entity
            val killer = victim.killer

            // Must be a player-caused death (not environmental)
            if (killer !is Player) {
                return
            }

            // Don't track self-kills
            if (killer.uniqueId == victim.uniqueId) {
                return
            }

            // Get both players' guilds
            val killerGuilds = memberService.getPlayerGuilds(killer.uniqueId)
            val victimGuilds = memberService.getPlayerGuilds(victim.uniqueId)

            // Both players must be in guilds
            if (killerGuilds.isEmpty() || victimGuilds.isEmpty()) {
                return
            }

            // Check if there's an active war between any of their guilds
            for (killerGuild in killerGuilds) {
                for (victimGuild in victimGuilds) {
                    val war = warService.getCurrentWarBetweenGuilds(killerGuild, victimGuild)

                    if (war != null && war.isActive) {
                        // Found an active war - update stats
                        val currentStats = warService.getWarStats(war.id)

                        // Determine which guild is the killer's and update stats
                        val updatedStats = if (war.declaringGuildId == killerGuild) {
                            // Declaring guild got a kill
                            currentStats.copy(
                                declaringGuildKills = currentStats.declaringGuildKills + 1,
                                defendingGuildDeaths = currentStats.defendingGuildDeaths + 1,
                                lastUpdated = Instant.now()
                            )
                        } else {
                            // Defending guild got a kill
                            currentStats.copy(
                                defendingGuildKills = currentStats.defendingGuildKills + 1,
                                declaringGuildDeaths = currentStats.declaringGuildDeaths + 1,
                                lastUpdated = Instant.now()
                            )
                        }

                        // Update the war stats
                        if (warService.updateWarStats(updatedStats)) {
                            logger.info("War kill recorded: ${killer.name} (guild $killerGuild) killed ${victim.name} (guild $victimGuild) in war ${war.id}")

                            // Notify both players
                            killer.sendMessage("§6⚔ WAR KILL! §7You killed §c${victim.name}§7!")
                            victim.sendMessage("§c☠ You were killed by §6${killer.name}§c in the war!")

                            // Check if any kill objectives are met
                            checkAndCompleteKillObjectives(war.id, updatedStats)
                        } else {
                            logger.error("Failed to update war stats for war ${war.id}")
                        }

                        // Only count the kill once (for the first matching war found)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.error("Error tracking war kill", e)
        }
    }

    /**
     * Checks if any kill objectives are met and ends the war if so.
     */
    private fun checkAndCompleteKillObjectives(warId: java.util.UUID, stats: WarStats) {
        try {
            val war = warService.getWar(warId) ?: return

            // Check if there are any kill objectives
            val killObjectives = war.objectives.filter { it.type == ObjectiveType.KILLS }

            for (objective in killObjectives) {
                // Check if declaring guild reached the kill target
                if (stats.declaringGuildKills >= objective.targetValue) {
                    logger.info("War ${war.id}: Declaring guild reached kill objective (${stats.declaringGuildKills}/${objective.targetValue})")
                    warService.endWar(
                        warId = war.id,
                        winnerGuildId = war.declaringGuildId,
                        peaceTerms = "Victory achieved through kill objective (${stats.declaringGuildKills} kills)",
                        actorId = SYSTEM_ACTOR
                    )
                    return
                }

                // Check if defending guild reached the kill target
                if (stats.defendingGuildKills >= objective.targetValue) {
                    logger.info("War ${war.id}: Defending guild reached kill objective (${stats.defendingGuildKills}/${objective.targetValue})")
                    warService.endWar(
                        warId = war.id,
                        winnerGuildId = war.defendingGuildId,
                        peaceTerms = "Victory achieved through kill objective (${stats.defendingGuildKills} kills)",
                        actorId = SYSTEM_ACTOR
                    )
                    return
                }
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.error("Error checking kill objectives for war $warId", e)
        }
    }
}
