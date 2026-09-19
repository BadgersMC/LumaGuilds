package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.ObjectiveType
import net.lumalyte.lg.domain.entities.WarStats
import net.lumalyte.lg.api.events.GuildWarKillEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Tracks PvP kills during guild wars and updates war statistics.
 * This listener ensures that wars can progress and end based on kill objectives.
 */
class WarKillTrackingListener : Listener, KoinComponent {

    private val warService: WarService by inject()
    private val memberService: MemberService by inject()
    private val lang: LangService by inject()

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
                        val counter = warService.recordOpposingGuildKill(
                            warId = war.id,
                            killerGuildId = killerGuild,
                            victimGuildId = victimGuild,
                        ) ?: continue

                        // Anti-farming suppresses XP only; the real opposing-guild kill
                        // still counts toward the persisted war-resolution target.
                        val isFarming = warService.recordWarKillAndCheckFarming(killer.uniqueId, victim.uniqueId)

                        Bukkit.getPluginManager().callEvent(
                            GuildWarKillEvent(war.id, killer.uniqueId, victim.uniqueId, killerGuild, victimGuild)
                        )
                        logger.info(
                            "War kill recorded: ${killer.name} (guild $killerGuild) killed " +
                                "${victim.name} (guild $victimGuild) in war ${war.id}"
                        )

                        if (!isFarming) {
                            warService.awardWarKillExperience(killerGuild, killer.uniqueId)
                        }

                        killer.sendMessage(lang.msg("notification.war.kill.killer", "victim" to victim.name))
                        victim.sendMessage(lang.msg("notification.war.kill.victim", "killer" to killer.name))

                        // The global kill target may already have ended the war.
                        if (counter.winnerGuildId == null) {
                            checkAndCompleteKillObjectives(war.id, counter.stats)
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
