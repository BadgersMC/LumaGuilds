package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.util.UUID
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
}
