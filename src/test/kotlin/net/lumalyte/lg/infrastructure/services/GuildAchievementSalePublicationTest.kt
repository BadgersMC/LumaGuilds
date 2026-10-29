package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.GuildShopXpCompletion
import net.lumalyte.lg.application.persistence.GuildShopXpRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.ProgressionService
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

internal class GuildAchievementSalePublicationTest {
    @Test fun `paid completion publishes stable receipt while excluded and aborted outcomes never count`() {
        val repo = mockk<GuildShopXpRepository>()
        val progression = mockk<ProgressionService>(relaxed = true)
        val guild = UUID.randomUUID()
        val sale = UUID.randomUUID()
        val publications = mutableListOf<Pair<UUID, UUID>>()
        val subject = GuildShopXpProvider(repo, mockk<ConfigService>(relaxed = true), progression) { id, owner -> publications += id to owner }
        for (status in listOf("OWN_GUILD", "ABORTED", "STALE_RUN", "CHAPTER_FROZEN")) {
            every { repo.complete(sale) } returns GuildShopXpCompletion(status, guild)
            assertEquals(status, subject.complete(sale))
        }
        assertTrue(publications.isEmpty())
        for (status in listOf("AWARDED:5", "CAPPED", "COOLDOWN", "DISABLED")) {
            every { repo.complete(sale) } returns GuildShopXpCompletion(status, guild)
            assertEquals(status, subject.complete(sale))
        }
        assertEquals(List(4) { sale to guild }, publications)
    }
    @Test fun `achievement publication failure cannot turn a settled sale into a payment retry`() {
        val repo = mockk<GuildShopXpRepository>()
        val sale = UUID.randomUUID()
        every { repo.complete(sale) } returns GuildShopXpCompletion("CAPPED", UUID.randomUUID())
        val subject = GuildShopXpProvider(repo, mockk(relaxed = true), mockk(relaxed = true)) { _, _ -> error("injected achievement failure") }
        assertEquals("CAPPED", subject.complete(sale))
        verify(exactly = 1) { repo.complete(sale) }
    }
}
