package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.title.Title
import net.lumalyte.lg.domain.rewards.PrestigeRankUp
import net.lumalyte.lg.application.services.FanfareStage
import net.lumalyte.lg.domain.rewards.FanfareCue
import org.bukkit.Bukkit
import org.bukkit.SoundCategory
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.UUID

/** REQ-164: plays fanfare cues to players with the Bukkit scheduler. */
class PrestigeFanfareBukkit internal constructor(
    private val plugin: Plugin,
    private val lang: LangService,
    private val bedrock: (org.bukkit.entity.Player) -> Boolean = { false },
    private val resolver: NexoGlyphResolver = NexoPublicGlyphResolver,
) : FanfareStage {
    private val frameFont = Key.key(PrestigeRankUp.FONT)

    override fun online(): Collection<UUID> = Bukkit.getOnlinePlayers().map { it.uniqueId }

    override fun isOnline(player: UUID): Boolean = Bukkit.getPlayer(player)?.isOnline == true

    override fun later(ticks: Int, task: () -> Unit) {
        if (ticks <= 0) Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        else Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, ticks.toLong())
    }

    override fun play(player: UUID, cue: FanfareCue) {
        val target = Bukkit.getPlayer(player) ?: return
        target.playSound(target, cue.sound, SoundCategory.MASTER, cue.volume, cue.pitch)
    }

    override fun title(player: UUID, tier: Int, guildName: String) {
        val target = Bukkit.getPlayer(player) ?: return
        target.showTitle(
            Title.title(
                lang.msg("notification.progression.prestige.title", "tier" to PrestigeNumeral.of(tier)),
                lang.msg("notification.progression.prestige.subtitle", "guild" to guildName),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000)),
            )
        )
    }

    /** REQ-252: Java players, and only when the pack carries the rank-up frames. */
    override fun animated(player: UUID): Boolean {
        val target = Bukkit.getPlayer(player) ?: return false
        if (runCatching { bedrock(target) }.getOrDefault(false)) return false
        return resolveOptionalGlyph(resolver, PrestigeRankUp.CONTRACT_ID)
            ?.isDialogContract(PrestigeRankUp.CONTRACT_ID, PrestigeRankUp.CONTRACT_FONT) == true
    }

    /** REQ-251: each frame stays a few ticks so a late tick never blanks it; the last one holds, then fades. */
    override fun frame(player: UUID, tier: Int, frame: Int, guildName: String?, last: Boolean) {
        val target = Bukkit.getPlayer(player) ?: return
        val art = Component.text(PrestigeRankUp.char(tier, frame), NamedTextColor.WHITE).font(frameFont)
            .shadowColor(ShadowColor.none())
        val subtitle = if (guildName == null) Component.empty()
        else lang.msg("notification.progression.prestige.animation_subtitle", "guild" to guildName)
        val times = if (last) Title.Times.times(Duration.ZERO, Duration.ofMillis(2500), Duration.ofMillis(1000))
        else Title.Times.times(Duration.ZERO, Duration.ofMillis(200), Duration.ZERO)
        target.showTitle(Title.title(art, subtitle, times))
    }
}
