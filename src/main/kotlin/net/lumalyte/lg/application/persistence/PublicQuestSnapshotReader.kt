package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.entities.GuildQuestProgress
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import java.util.UUID

data class PublicQuestCapture(
    val week: WeeklyQuestSet?,
    val progress: Map<String, List<GuildQuestProgress>> = emptyMap(),
    val guildNames: Map<UUID, String> = emptyMap(),
)

/** One consistent read. Implementations must not call claim/reward operations. */
interface PublicQuestSnapshotReader {
    fun readPublicQuestSnapshot(): PublicQuestCapture
}
