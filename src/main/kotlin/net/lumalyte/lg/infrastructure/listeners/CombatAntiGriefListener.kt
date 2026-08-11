package net.lumalyte.lg.infrastructure.listeners

import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Anti-griefing enforcement (REQ-008, `combat.anti_griefing_enabled`).
 *
 * When enabled, explosive block destruction is suppressed for players who are
 * currently in an active war — warring factions cannot level each other's
 * builds with TNT/end crystals. Combat (entity damage) is unaffected; only
 * terrain griefing via explosions is blocked. When the knob is disabled, war
 * griefing behaves as before.
 *
 * Actor attribution: `EntityExplodeEvent` sources are resolved via
 * `TNTPrimed.source` (the entity that ignited the TNT) when available. When no
 * player source can be determined, the explosion is NOT attributed to anyone
 * and block damage proceeds (explicit no-actor policy).
 */
class CombatAntiGriefListener : Listener, KoinComponent {

    private val configService: ConfigService by inject()
    private val warService: WarService by inject()
    private val memberService: MemberService by inject()

    private val logger = LoggerFactory.getLogger(CombatAntiGriefListener::class.java)

    @EventHandler
    fun onEntityExplode(event: EntityExplodeEvent) {
        // Resolve the actor via explosion ownership (TNTPrimed.source), not proximity.
        val sourcePlayer = (event.entity as? TNTPrimed)?.source as? Player
            ?: return // No attributable player actor — explicit no-actor policy

        if (shouldBlockGriefing(sourcePlayer, event.blockList())) {
            event.blockList().clear()
        }
    }

    @EventHandler
    fun onBlockExplode(event: BlockExplodeEvent) {
        // BlockExplodeEvent has no actor metadata — no-actor policy: never block.
        // Player-caused TNT griefing is already handled by EntityExplodeEvent.
    }

    /**
     * Blocks the explosion's block damage when the flag is on and the player
     * belongs to a guild currently in an active war.
     */
    private fun shouldBlockGriefing(player: Player, blocks: List<Block>): Boolean {
        if (blocks.isEmpty()) return false
        if (!configService.loadConfig().combat.antiGriefingEnabled) return false

        val playerGuilds = memberService.getPlayerGuilds(player.uniqueId)
        if (playerGuilds.isEmpty()) return false

        val inActiveWar = playerGuilds.any { guildId ->
            warService.getWarsForGuild(guildId).any { it.isActive }
        }

        if (inActiveWar) {
            logger.debug("Anti-grief: suppressing explosion block damage by warring player ${player.name}")
        }
        return inActiveWar
    }
}
