package net.lumalyte.lg.infrastructure.listeners

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
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
                        val killUpdate = warService.recordOpposingGuildKill(
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

                        // A decisive kill is persisted first, but the terminal war transition
                        // intentionally happens after the kill event, XP, and player feedback.
                        killUpdate.winnerGuildId?.let { winner ->
                            if (!warService.resolveReachedKillTarget(war.id, winner)) {
                                logger.error("Failed to resolve decisive kill for war ${war.id}")
                            }
                        }

                        // Only count the kill once (for the first matching war found).
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // Event listener - catching all exceptions to prevent listener failure
            logger.error("Error tracking war kill", e)
        }
    }
}
