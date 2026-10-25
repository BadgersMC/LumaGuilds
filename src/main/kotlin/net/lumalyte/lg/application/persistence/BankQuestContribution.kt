package net.lumalyte.lg.application.persistence

import java.util.UUID

/** Canonical bank deposit and quest identities for one atomic progress operation. */
@Suppress("LibraryEntitiesShouldNotBePublic") // Public repository port request shared with persistence adapters.
data class BankQuestContribution(
    /** Confirmed gold operation identity. */
    val transactionId: UUID,
    /** Original quest-set identity. */
    val weekId: String,
    /** Quest that may consume this contribution. */
    val questId: String,
    /** Guild receiving the contribution. */
    val guildId: UUID,
    /** Actor recorded on the confirmed operation. */
    val actorId: UUID,
    /** Required count checked against the retained quest definition. */
    val targetCount: Long,
)
