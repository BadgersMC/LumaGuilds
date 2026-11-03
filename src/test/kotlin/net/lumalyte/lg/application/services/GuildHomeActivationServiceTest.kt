package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildHomeActivationRepository
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuildHomeActivationServiceTest {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val home = GuildHome(UUID.randomUUID(), Position3D(5, 65, 5))

    @Test
    fun `saved home is inactive until activation state exists`() {
        val repository = mockk<GuildHomeActivationRepository>()
        every { repository.isActive(guildId, "main") } returns false
        val service = service(repository)

        assertFalse(service.isActive(guildId, "main"))
    }

    @Test
    fun `legacy payment credit reactivates saved home without a second debit`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        every { repository.isActive(guildId, "main") } returns false
        every { repository.availableCredits(guildId) } returns 1
        every { repository.activateUsingCredit(guildId, "main") } returns true
        val service = service(repository, costs)

        val result = service.activateSavedHome(UUID.randomUUID(), guildId, "main", actorId)

        assertEquals(HomeActivationCostResult.Applied(0), result)
        verify(exactly = 0) { costs.activateHome(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `inactive legacy home uses next active ordinal when no credit remains`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        val tx = UUID.randomUUID()
        every { repository.isActive(guildId, "outer") } returns false
        every { repository.availableCredits(guildId) } returns 0
        every { repository.activeCount(guildId) } returns 2
        every { repository.activate(guildId, "outer", tx) } returns true
        every { costs.activateHome(tx, guildId, actorId, 3, false, any()) } answers {
            val activate = arg<() -> Boolean>(5)
            assertTrue(activate())
            HomeActivationCostResult.Applied(400)
        }
        val service = service(repository, costs)

        val result = service.activateSavedHome(tx, guildId, "outer", actorId)

        assertEquals(HomeActivationCostResult.Applied(400), result)
    }

    @Test
    fun `moving an already active home persists without charging again`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        every { repository.isActive(guildId, "main") } returns true
        every { repository.activeCount(guildId) } returns 4
        every { costs.activateHome(any(), guildId, actorId, 4, true, any()) } answers {
            val persist = arg<() -> Boolean>(5)
            assertTrue(persist())
            HomeActivationCostResult.Applied(0)
        }
        var persisted = false
        val service = service(repository, costs)

        val result = service.persistLocation(
            UUID.randomUUID(), guildId, "main", actorId, existedBefore = true, proposedHome = home,
        ) { persisted = true; true }

        assertIs<HomeActivationCostResult.Applied>(result)
        assertEquals(0, result.cost)
        assertTrue(persisted)
    }

    @Test
    fun `over-cap Java and Bedrock home attempts fail before any charge or mutation`() {
        listOf("new-java-home", "home16").forEach { newName ->
            val repository = mockk<GuildHomeActivationRepository>()
            val costs = mockk<GuildCostService>()
            var persisted = false
            val service = service(repository, costs, maySetHome = { _, name, _, _, _ -> name != newName })

            val result = service.persistLocation(
                UUID.randomUUID(), guildId, newName, actorId, existedBefore = false, proposedHome = home,
            ) { persisted = true; true }

            assertEquals(HomeActivationCostResult.ActivationFailed(compensated = true), result)
            assertFalse(persisted)
            verify(exactly = 0) { costs.activateHome(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { repository.isActive(any(), any()) }
        }
    }

    @Test
    fun `revoked MANAGE_HOME actor cannot debit for a new or existing location`() {
        listOf(false, true).forEach { existedBefore ->
            val repository = mockk<GuildHomeActivationRepository>()
            val costs = mockk<GuildCostService>()
            var persisted = false
            var observedActor: UUID? = null
            var observedPriorExistence: Boolean? = null
            val service = service(repository, costs, maySetHome = { _, _, actor, existed, _ ->
                observedActor = actor
                observedPriorExistence = existed
                false // actor lost MANAGE_HOME after opening a stale form
            })

            val result = service.persistLocation(
                UUID.randomUUID(), guildId, "home1", actorId, existedBefore, proposedHome = home,
            ) { persisted = true; true }

            assertEquals(HomeActivationCostResult.ActivationFailed(compensated = true), result)
            assertEquals(actorId, observedActor)
            assertEquals(existedBefore, observedPriorExistence)
            assertFalse(persisted)
            verify(exactly = 0) { costs.activateHome(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { repository.isActive(any(), any()) }
        }
    }

    @Test
    fun `revoked actor cannot spend legacy credit or pay to activate saved home`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        val service = service(repository, costs, maySetHome = { _, _, _, _, _ -> false })

        val result = service.activateSavedHome(UUID.randomUUID(), guildId, "main", actorId)

        assertEquals(HomeActivationCostResult.ActivationFailed(compensated = true), result)
        verify(exactly = 0) { repository.activateUsingCredit(any(), any()) }
        verify(exactly = 0) { repository.isActive(any(), any()) }
        verify(exactly = 0) { costs.activateHome(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `legacy saved home and relocated Nether home activate without claim infrastructure`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        val tx = UUID.randomUUID()
        every { repository.isActive(guildId, "main") } returns false
        every { repository.availableCredits(guildId) } returnsMany listOf(1, 0)
        every { repository.activateUsingCredit(guildId, "main") } returns true
        every { repository.activeCount(guildId) } returns 1
        every { repository.activate(guildId, "main", tx) } returns true
        every { costs.activateHome(tx, guildId, actorId, 2, false, any()) } answers {
            assertTrue(arg<() -> Boolean>(5)())
            HomeActivationCostResult.Applied(100)
        }
        var examinedHome: GuildHome? = null
        val service = service(repository, costs, maySetHome = { _, _, _, _, candidate ->
            examinedHome = candidate
            true // Permission/cap preflight allows home; there is no claims dependency.
        })

        assertEquals(
            HomeActivationCostResult.Applied(0),
            service.activateSavedHome(UUID.randomUUID(), guildId, "main", actorId),
        )
        assertEquals(null, examinedHome)
        var movedPersisted = false
        val netherHome = home.copy(worldId = UUID.randomUUID(), position = Position3D(-120, 75, 40))
        assertEquals(
            HomeActivationCostResult.Applied(100),
            service.persistLocation(
                tx, guildId, "main", actorId, existedBefore = true, proposedHome = netherHome,
            ) { movedPersisted = true; true },
        )
        assertEquals(netherHome, examinedHome)
        assertTrue(movedPersisted)
    }

    @Test
    fun `capacity preflight rejection stops gold and legacy credit consumption`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        var persisted = false
        var receivedHome: GuildHome? = null
        val service = service(repository, costs, maySetHome = { _, _, _, _, candidate ->
            receivedHome = candidate
            false // Stale capacity or MANAGE_HOME authorization from a previously opened menu
        })
        val result = service.persistLocation(
            UUID.randomUUID(), guildId, "home2", actorId, existedBefore = false, proposedHome = home,
        ) { persisted = true; true }

        assertEquals(HomeActivationCostResult.ActivationFailed(compensated = true), result)
        assertEquals(home, receivedHome)
        assertFalse(persisted)
        verify(exactly = 0) { repository.isActive(any(), any()) }
        verify(exactly = 0) { repository.activateUsingCredit(any(), any()) }
        verify(exactly = 0) { costs.activateHome(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `permission lookup errors fail closed before the gold debit`() {
        val repository = mockk<GuildHomeActivationRepository>()
        val costs = mockk<GuildCostService>()
        val service = service(repository, costs, maySetHome = { _, _, _, _, _ ->
            throw IllegalStateException("permission storage unavailable")
        })
        var persisted = false

        val result = service.persistLocation(
            UUID.randomUUID(), guildId, "home2", actorId, existedBefore = false, proposedHome = home,
        ) { persisted = true; true }

        assertEquals(HomeActivationCostResult.ActivationFailed(compensated = true), result)
        assertFalse(persisted)
        verify(exactly = 0) { costs.activateHome(any(), any(), any(), any(), any(), any()) }
    }

    private fun service(
        repository: GuildHomeActivationRepository,
        costs: GuildCostService = mockk(relaxed = true),
        maySetHome: (UUID, String, UUID, Boolean, GuildHome?) -> Boolean = { _, _, _, _, _ -> true }
    ) = GuildHomeActivationService(
        repository,
        costs,
        maySetHome,
    ) {
        MainConfig(
            guild = GuildConfig(homeActivationBaseCost = 100, homeActivationScale = 2.0),
            chapterTwoGoldCostsEnabled = true,
        )
    }
}
