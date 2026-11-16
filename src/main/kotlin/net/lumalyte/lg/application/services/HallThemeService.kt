package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.HallDisplayRepository
import net.lumalyte.lg.domain.rewards.HallDisplay
import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * REQ-260/261: Hall of Prestige themes. A guild may own one theme per prestige; the theme is
 * normally chosen on the prestige screen, and any pick not made there (staff grants, guilds that
 * prestiged before themes existed) can be made later from Hall Display.
 */
class HallThemeService(
    private val repository: HallDisplayRepository,
    private val prestigeCount: (UUID) -> Int?,
    private val canManage: (actor: UUID, guildId: UUID) -> Boolean,
    private val isMember: (playerId: UUID, guildId: UUID) -> Boolean,
    private val clock: () -> Instant = Instant::now,
) {
    enum class Outcome { DONE, NOT_ALLOWED, NO_PICKS, NOT_OWNED, NOT_A_MEMBER, FAILED }

    private val logger = LoggerFactory.getLogger(HallThemeService::class.java)

    fun display(guildId: UUID): HallDisplay? = try {
        val owned = repository.owned(guildId)
        val prestiges = prestigeCount(guildId) ?: 0
        HallDisplay(owned, (prestiges - owned.size).coerceAtLeast(0), repository.choices(guildId), repository.skin(guildId))
    } catch (e: Exception) {
        logger.warn("Could not read the Hall display of guild $guildId", e)
        null
    }

    /** Themes still available to pick, in display order. */
    fun available(guildId: UUID): List<HallTheme> = display(guildId)?.unowned ?: emptyList()

    /** A pick from Hall Display; needs an unused pick and permission to manage the guild's look. */
    fun earn(actor: UUID, guildId: UUID, theme: HallTheme): Outcome {
        if (!canManage(actor, guildId)) return Outcome.NOT_ALLOWED
        return earnFor(guildId, theme)
    }

    /** REQ-260: the theme chosen on the prestige screen, granted once the prestige has applied. */
    fun earnFromPrestige(guildId: UUID, theme: HallTheme): Outcome = earnFor(guildId, theme)

    private fun earnFor(guildId: UUID, theme: HallTheme): Outcome = try {
        val limit = prestigeCount(guildId) ?: return Outcome.FAILED
        if (repository.earn(guildId, theme, limit, clock())) Outcome.DONE else Outcome.NO_PICKS
    } catch (e: Exception) {
        logger.warn("Could not grant Hall theme $theme to guild $guildId", e)
        Outcome.FAILED
    }

    fun wear(actor: UUID, guildId: UUID, slot: HallSlot, theme: HallTheme?): Outcome = change(actor, guildId) { display ->
        if (theme != null && theme !in display.owned) return@change Outcome.NOT_OWNED
        repository.choose(guildId, slot, theme)
        Outcome.DONE
    }

    /** Sets every slot to one owned theme. */
    fun wearAll(actor: UUID, guildId: UUID, theme: HallTheme): Outcome = change(actor, guildId) { display ->
        if (theme !in display.owned) return@change Outcome.NOT_OWNED
        HallSlot.entries.forEach { repository.choose(guildId, it, theme) }
        Outcome.DONE
    }

    /** The statue wears this member's skin; null goes back to the guild owner. */
    fun setSkin(actor: UUID, guildId: UUID, playerId: UUID?): Outcome = change(actor, guildId) {
        if (playerId != null && !isMember(playerId, guildId)) return@change Outcome.NOT_A_MEMBER
        repository.setSkin(guildId, playerId)
        Outcome.DONE
    }

    fun disbanded(guildId: UUID) {
        runCatching { repository.deleteAll(guildId) }.onFailure { logger.warn("Could not clear the Hall display of guild $guildId", it) }
    }

    private fun change(actor: UUID, guildId: UUID, block: (HallDisplay) -> Outcome): Outcome {
        if (!canManage(actor, guildId)) return Outcome.NOT_ALLOWED
        val display = display(guildId) ?: return Outcome.FAILED
        return try {
            block(display)
        } catch (e: Exception) {
            logger.warn("Could not change the Hall display of guild $guildId", e)
            Outcome.FAILED
        }
    }
}
