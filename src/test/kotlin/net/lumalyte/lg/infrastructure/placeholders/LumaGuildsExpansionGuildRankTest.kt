package net.lumalyte.lg.infrastructure.placeholders

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Rank
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.assertEquals

class LumaGuildsExpansionGuildRankTest {
    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()
    private val rankService = mockk<RankService>()
    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<GuildService> { guildService }
                    single<MemberService> { memberService }
                    single<RankService> { rankService }
                }
            )
        }
    }

    @AfterEach
    fun cleanup() {
        stopKoin()
    }

    @Test
    fun `guild rank placeholder resolves current rank on every request`() {
        val playerId = UUID.randomUUID()
        val guildId = UUID.randomUUID()
        val ownerRankId = UUID.randomUUID()
        val officerRankId = UUID.randomUUID()
        val player = mockk<Player>()
        every { player.uniqueId } returns playerId
        every { memberService.getPlayerGuilds(playerId) } returns setOf(guildId)
        every { guildService.getGuild(guildId) } returns mockk<Guild>(relaxed = true)
        every {
            memberService.getPlayerRankId(playerId, guildId)
        } returnsMany listOf(ownerRankId, officerRankId)
        every { rankService.getRank(ownerRankId) } returns
            Rank(ownerRankId, guildId, "Owner")
        every { rankService.getRank(officerRankId) } returns
            Rank(officerRankId, guildId, "Officer")

        val expansion = LumaGuildsExpansion()

        assertEquals("Owner", expansion.onPlaceholderRequest(player, "guild_rank"))
        assertEquals("Officer", expansion.onPlaceholderRequest(player, "guild_rank"))
        verify(exactly = 2) {
            memberService.getPlayerRankId(playerId, guildId)
        }
    }
}
