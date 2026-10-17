package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankClaimPermissionProfileRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarBannerDefaultRankPermissionTest {
    @Test
    fun `new guild war management ranks can place war banners`() {
        val captured = mutableListOf<Rank>()
        val rankRepository = mockk<RankRepository>()
        every { rankRepository.add(capture(captured)) } returns true

        val service = RankServiceBukkit(
            rankRepository = rankRepository,
            memberRepository = mockk<MemberRepository>(relaxed = true),
            guildRepository = mockk<GuildRepository>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true),
            rankClaimPermissionProfiles = mockk<RankClaimPermissionProfileRepository>(relaxed = true),
        )

        assertTrue(service.createDefaultRanks(UUID.randomUUID(), UUID.randomUUID()))
        val byName = captured.associateBy { it.name }
        for (rankName in listOf("Owner", "Co-Owner", "Admin")) {
            assertTrue(
                RankPermission.PLACE_WAR_BANNER in requireNotNull(byName[rankName]).permissions,
                "$rankName should receive PLACE_WAR_BANNER by default",
            )
        }
    }

    @Test
    fun `default rank creation rolls back every inserted rank when profile persistence fails`() {
        val captured = mutableListOf<Rank>()
        val rankRepository = mockk<RankRepository>()
        val profiles = mockk<RankClaimPermissionProfileRepository>()
        every { rankRepository.add(capture(captured)) } returns true
        every { rankRepository.remove(any()) } returns true
        every { profiles.remove(any()) } returns true

        var profileCalls = 0
        every { profiles.getOrCreate(any(), any()) } answers {
            if (profileCalls++ == 0) secondArg()
            else throw IllegalStateException("profile storage unavailable")
        }

        val service = RankServiceBukkit(
            rankRepository = rankRepository,
            memberRepository = mockk<MemberRepository>(relaxed = true),
            guildRepository = mockk<GuildRepository>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true),
            rankClaimPermissionProfiles = profiles,
        )

        assertFalse(service.createDefaultRanks(UUID.randomUUID(), UUID.randomUUID()))
        val createdIds = captured.map { it.id }
        assertTrue(createdIds.size == 2)
        createdIds.forEach { rankId ->
            verify(exactly = 1) { rankRepository.remove(rankId) }
            verify(exactly = 1) { profiles.remove(rankId) }
        }
    }
}
