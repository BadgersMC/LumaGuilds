package net.lumalyte.lg.domain.services

import net.lumalyte.lg.domain.entities.QuestTarget

interface QuestTargetProvider {
    val namespace: String
    fun discoverTargets(): Collection<QuestTarget>
}

class QuestTargetCatalog(private val providers: List<QuestTargetProvider>) {
    fun discoverTargets(): List<QuestTarget> =
        providers.asSequence()
            .flatMap { it.discoverTargets().asSequence() }
            .distinctBy { target ->
                target.allowedActions.map { it.name }.sorted().joinToString(",") + "|" + target.id.lowercase()
            }
            .sortedWith(compareBy<QuestTarget>({ it.allowedActions.minOf { action -> action.name } }, { it.id }))
            .toList()
}
