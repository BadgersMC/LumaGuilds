package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.WarRepository

class ChapterRatedWarReconciliationGate(
    private val warRepository: WarRepository,
    private val seasonalElo: SeasonalEloCoordinator,
) {
    fun reconcile(chapterId: String, cutoffAt: Long) {
        val chapterWars = warRepository.getAll()
            .mapNotNull { it.war }
            .filter { it.ratedChapterId == chapterId }

        chapterWars
            .filter { it.isEnded && !seasonalElo.isWarRatingSettled(it.id) }
            .forEach { seasonalElo.rateResolvedWar(it) }

        chapterWars
            .filter { !it.isEnded && !seasonalElo.isWarRatingSettled(it.id) }
            .forEach { seasonalElo.markWarUnratedForChapterEnd(it, cutoffAt) }

        val unresolved = chapterWars
            .filterNot { seasonalElo.isWarRatingSettled(it.id) }
            .map { it.id }

        check(unresolved.isEmpty()) {
            "Chapter rollover blocked: ${unresolved.size} rated war result(s) " +
                "are not durably reconciled: ${unresolved.joinToString()}"
        }
    }
}
