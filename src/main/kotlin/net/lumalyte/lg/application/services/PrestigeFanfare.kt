package net.lumalyte.lg.application.services

import net.lumalyte.lg.config.PrestigeFanfareConfig
import net.lumalyte.lg.domain.rewards.FanfareAudience
import net.lumalyte.lg.domain.rewards.FanfareCue
import net.lumalyte.lg.domain.rewards.PrestigeCompletion
import net.lumalyte.lg.domain.rewards.PrestigeFanfareScore
import net.lumalyte.lg.domain.rewards.PrestigeRankUp
import org.slf4j.LoggerFactory
import java.util.UUID

/** Where the fanfare plays; the Bukkit adapter maps this to the scheduler and players. */
interface FanfareStage {
    fun online(): Collection<UUID>
    fun isOnline(player: UUID): Boolean
    /** Runs [task] on the server thread [ticks] from now. */
    fun later(ticks: Int, task: () -> Unit)
    fun play(player: UUID, cue: FanfareCue)
    fun title(player: UUID, tier: Int, guildName: String)

    /** REQ-252: whether [player] can see the rank-up animation (Java with the pack's frames). */
    fun animated(player: UUID): Boolean = false

    /** REQ-251: shows one animation frame; [guildName] is the subtitle, or null before it appears. */
    fun frame(player: UUID, tier: Int, frame: Int, guildName: String?, last: Boolean) {}
}

/**
 * REQ-164: plays the prestige fanfare after each newly committed prestige. The
 * prestige service only notifies on a new commit, so a replayed receipt never
 * plays it again. Any failure is logged and contained.
 */
class PrestigeFanfare(
    private val settings: () -> PrestigeFanfareConfig,
    private val members: (UUID) -> Collection<UUID>,
    private val guildName: (UUID) -> String?,
    private val stage: FanfareStage,
) : GuildPrestigeNotifier {
    private val logger = LoggerFactory.getLogger(PrestigeFanfare::class.java)

    override fun prestiged(completion: PrestigeCompletion) {
        runCatching { start(completion) }
            .onFailure { logger.warn("Prestige fanfare failed for guild ${completion.guildId}", it) }
    }

    private fun start(completion: PrestigeCompletion) {
        val config = settings()
        if (!config.enabled) return
        val online = stage.online().toSet()
        val guildMembers = members(completion.guildId).toSet()
        val listening = guildMembers intersect online
        val name = guildName(completion.guildId)
        val animated = if (config.memberTitle && config.memberAnimation && name != null) {
            listening.filter(stage::animated).toSet()
        } else {
            emptySet()
        }
        val plain = listening - animated
        schedule(plain, PrestigeFanfareScore.cues(completion.tier, FanfareAudience.MEMBERS))
        if (config.memberTitle && name != null) stage.later(0) {
            plain.filter(stage::isOnline).forEach { stage.title(it, completion.tier, name) }
        }
        if (animated.isNotEmpty() && name != null) {
            schedule(animated, PrestigeRankUp.memberCues(completion.tier))
            animate(animated, completion.tier, name)
        }
        if (config.serverWide) {
            schedule(online - guildMembers, PrestigeFanfareScore.cues(completion.tier, FanfareAudience.SERVER))
        }
    }

    /** REQ-251: one frame per tick, the guild name joining once the title has wiped in. */
    private fun animate(players: Set<UUID>, tier: Int, name: String) {
        val subtitleFrame = PrestigeRankUp.subtitleFrame(tier)
        for (frame in 0 until PrestigeRankUp.FRAMES) {
            stage.later(frame) {
                val subtitle = if (frame >= subtitleFrame) name else null
                val last = frame == PrestigeRankUp.FRAMES - 1
                players.filter(stage::isOnline).forEach { stage.frame(it, tier, frame, subtitle, last) }
            }
        }
    }

    private fun schedule(players: Set<UUID>, cues: List<FanfareCue>) {
        if (players.isEmpty()) return
        cues.groupBy { it.tick }.toSortedMap().forEach { (tick, batch) ->
            stage.later(tick) {
                for (player in players) {
                    if (!stage.isOnline(player)) continue
                    batch.forEach { stage.play(player, it) }
                }
            }
        }
    }
}
