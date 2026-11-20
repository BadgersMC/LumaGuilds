package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.PrestigeAdministration
import net.lumalyte.lg.application.persistence.PrestigeCreditLedger
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.domain.rewards.PrestigeAdjustmentResult
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** REQ-185, REQ-187: corrections refresh standings; reruns forget then credit. */
class PrestigeStaffServiceTest {
    private val guild = UUID.randomUUID()
    private val history = mockk<PrestigeHistory>()
    private val admin = mockk<PrestigeAdministration>()
    private val ledger = mockk<PrestigeCreditLedger>(relaxed = true)
    private val standings = mockk<GuildPrestigeStandings>(relaxed = true)
    private val credited = mutableListOf<UUID>()
    private val service = PrestigeStaffService(mockk(), mockk(), history, admin, ledger, mockk(), standings, mockk(),
        { credited += it }, { Instant.EPOCH })
    private fun record(tier: Int) = PrestigeRecord(UUID.randomUUID(), guild, UUID.randomUUID(), tier, "bank-1", Instant.EPOCH)

    @Test
    fun `an applied correction drops the cached standing`() {
        val actor = UUID.randomUUID()
        every { admin.setPrestigeCount(guild, 2, actor, Instant.EPOCH) } returns PrestigeAdjustmentResult.Applied(0, 2)
        service.setCount(actor, guild, 2)
        verify { standings.forget(guild) }
    }

    @Test
    fun `rerun forgets only the chosen tier and credits online members`() {
        val first = record(1)
        val second = record(2)
        every { history.history(guild) } returns listOf(first, second)
        assertEquals(listOf(second), service.rerun(guild, 2))
        verify { ledger.forget(setOf(second.transactionId)) }
        assertEquals(listOf(guild), credited)
    }

    @Test
    fun `rerun with nothing applied does nothing`() {
        every { history.history(guild) } returns emptyList()
        assertEquals(emptyList(), service.rerun(guild, null))
        verify(exactly = 0) { ledger.forget(any()) }
        assertEquals(emptyList(), credited)
    }
}
