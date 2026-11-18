package net.lumalyte.lg.infrastructure.persistence.guilds

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class MemberPlaytimeStoreSQLTest : RewardSqlTestFixture() {
    @Test
    fun `REQ-300 stored playtime survives a restart and the latest value wins`() {
        val storage = openStorage()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val store = MemberPlaytimeStoreSQL(storage)
        store.save(alice, 100L)
        store.save(alice, 250L)
        store.save(bob, 72_000L * 5_000)
        store.close()
        assertEquals(mapOf(alice to 250L, bob to 72_000L * 5_000), MemberPlaytimeStoreSQL(storage).loadAll())
    }
}
