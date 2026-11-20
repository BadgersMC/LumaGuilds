package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.DiplomacyHistory
import net.lumalyte.lg.domain.entities.DiplomacyEntry
import net.lumalyte.lg.domain.entities.DiplomacyKind
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/** REQ-203: turns a change into one entry per guild. Never throws: a failed write is logged. */
class DiplomacyLog(private val history: DiplomacyHistory, private val clock: Clock = Clock.systemUTC()) {
    private val logger = LoggerFactory.getLogger(DiplomacyLog::class.java)

    fun allied(a: UUID, b: UUID, actor: UUID?) = both(a, b, DiplomacyKind.ALLIED, DiplomacyKind.ALLIED, actor)

    fun allianceEnded(a: UUID, b: UUID, actor: UUID?) = both(a, b, DiplomacyKind.ALLIANCE_ENDED, DiplomacyKind.ALLIANCE_ENDED, actor)

    fun declaredEnemies(declaring: UUID, target: UUID, actor: UUID?) =
        both(declaring, target, DiplomacyKind.DECLARED_ENEMIES, DiplomacyKind.DECLARED_ENEMIES_BY, actor)

    fun truceStarted(a: UUID, b: UUID, actor: UUID?, days: Int) =
        both(a, b, DiplomacyKind.TRUCE_STARTED, DiplomacyKind.TRUCE_STARTED, actor, days)

    fun truceEnded(a: UUID, b: UUID) = both(a, b, DiplomacyKind.TRUCE_ENDED, DiplomacyKind.TRUCE_ENDED, null)

    fun peace(a: UUID, b: UUID, actor: UUID?) = both(a, b, DiplomacyKind.PEACE, DiplomacyKind.PEACE, actor)

    fun warEnded(declaring: UUID, defending: UUID, winner: UUID?) = when (winner) {
        null -> both(declaring, defending, DiplomacyKind.WAR_DRAWN, DiplomacyKind.WAR_DRAWN, null)
        declaring -> both(declaring, defending, DiplomacyKind.WAR_WON, DiplomacyKind.WAR_LOST, null)
        else -> both(declaring, defending, DiplomacyKind.WAR_LOST, DiplomacyKind.WAR_WON, null)
    }

    fun page(guildId: UUID, offset: Int, limit: Int): List<DiplomacyEntry> = runCatching { history.page(guildId, offset, limit) }
        .onFailure { logger.error("Unable to read diplomacy history for guild $guildId", it) }
        .getOrDefault(emptyList())

    fun count(guildId: UUID): Int = runCatching { history.count(guildId) }
        .onFailure { logger.error("Unable to count diplomacy history for guild $guildId", it) }
        .getOrDefault(0)

    @Suppress("LongParameterList")
    private fun both(a: UUID, b: UUID, forA: DiplomacyKind, forB: DiplomacyKind, actor: UUID?, days: Int? = null) {
        val at = clock.instant()
        runCatching { history.record(listOf(DiplomacyEntry(a, b, forA, actor, at, days), DiplomacyEntry(b, a, forB, actor, at, days))) }
            .onFailure { logger.error("Unable to save diplomacy history for $a and $b ($forA)", it) }
    }
}
