package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.entities.DiplomacyEntry
import net.lumalyte.lg.domain.entities.DiplomacyKind
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-203/REQ-204: history survives a restart and pages newest first. */
class DiplomacyHistorySQLTest : RewardSqlTestFixture() {
    @Test
    fun `entries are saved and paged newest first`() {
        val storage = openStorage()
        migrateProductionSchema(storage)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val t = Instant.parse("2026-11-10T10:00:00Z")
        val repo = DiplomacyHistorySQL(storage)
        val allied = DiplomacyEntry(a, b, DiplomacyKind.ALLIED, actor, t)
        val truce = DiplomacyEntry(a, b, DiplomacyKind.TRUCE_STARTED, actor, t.plusSeconds(60), days = 3)
        val lost = DiplomacyEntry(a, b, DiplomacyKind.WAR_LOST, null, t.plusSeconds(120))
        repo.record(listOf(allied, DiplomacyEntry(b, a, DiplomacyKind.ALLIED, actor, t)))
        repo.record(listOf(truce))
        repo.record(listOf(lost))
        closeStorage(storage)

        val reopened = DiplomacyHistorySQL(openStorage())
        assertEquals(3, reopened.count(a))
        assertEquals(1, reopened.count(b))
        assertEquals(listOf(lost, truce), reopened.page(a, 0, 2))
        assertEquals(listOf(allied), reopened.page(a, 2, 2))
    }
}
