package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.koin.core.context.*
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildServiceCreationTest {
    private val guilds = mockk<GuildRepository>(relaxed = true)
    private val ranks = mockk<RankRepository>(relaxed = true)
    private val members = mockk<MemberRepository>(relaxed = true)
    private val rankService = mockk<RankService>()
    private val memberService = mockk<MemberService>()
    private val config = mockk<ConfigService>()
    private val owner = UUID.randomUUID()
    private val rankId = UUID.randomUUID()

    @BeforeEach fun setup() {
        stopKoin()
        MockBukkit.mock()
        every { config.loadConfig() } returns MainConfig()
        startKoin { modules(module { single { config } }) }
        every { guilds.addCreated(any(), owner) } returns true
        every { guilds.remove(any()) } returns true
        every { rankService.createDefaultRanks(any(), owner) } returns true
        every { ranks.getHighestRank(any()) } answers { Rank(rankId, firstArg(), "Owner", 0, emptySet()) }
        every { members.getByPlayerAndGuild(owner, any()) } returns null
    }
    @AfterEach fun cleanup() { stopKoin(); unmockkAll(); MockBukkit.unmock() }

    private fun service() = GuildServiceBukkit(
        guilds, ranks, members, rankService, memberService,
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
    )

    @Test fun missingOwnerRollsBackInsteadOfReportingSuccess() {
        every { memberService.addMember(owner, any(), rankId) } returns null
        assertNull(service().createGuild("No Owner", owner, null))
        verify(exactly = 1) { guilds.remove(any()) }
        verify(exactly = 1) { ranks.removeByGuild(any()) }
    }

    @Test fun exceptionBeforeOwnerWriteCanBeProvenRolledBack() {
        every { memberService.addMember(owner, any(), rankId) } throws IllegalStateException("admission failed")
        assertNull(service().createGuild("No Admission", owner, null))
        verify(exactly = 1) { guilds.remove(any()) }
    }

    @Test fun durableOwnerOrFailedCleanupRemainsUncertain() {
        every { memberService.addMember(owner, any(), rankId) } throws IllegalStateException("observer failed")
        every { members.getByPlayerAndGuild(owner, any()) } answers { Member(owner, secondArg(), rankId, Instant.now()) }
        assertFailsWith<IllegalStateException> { service().createGuild("Durable Owner", owner, null) }
        verify(exactly = 0) { guilds.remove(any()) }
        every { members.getByPlayerAndGuild(owner, any()) } returns null
        every { guilds.remove(any()) } returns false
        assertFailsWith<IllegalStateException> { service().createGuild("Cleanup Failed", owner, null) }
    }

    @Test fun commandAdvertisedNameCharactersAreAcceptedByService() {
        every { memberService.addMember(owner, any(), rankId) } answers { Member(owner, secondArg(), rankId, Instant.now()) }
        assertNotNull(service().createGuild("Mr-PTC & Friends' Guild", owner, null))
        assertNull(service().createGuild("<red>Bad", owner, null))
    }
}
