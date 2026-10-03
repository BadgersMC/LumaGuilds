package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.GuildProgression
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GuildDiscordProfileServiceTest {
    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()
    private val rankService = mockk<RankService>()
    private val progressionRepository = mockk<ProgressionRepository>()
    private val prestigeService = mockk<GuildPrestigeService>()
    private val relationService = mockk<RelationService>()

    @Test
    fun `all guilds can be resolved without discord link state`() {
        val guildId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val ownerRank = Rank(UUID.randomUUID(), guildId, "&aOwner", priority = 0)
        val memberRank = Rank(UUID.randomUUID(), guildId, "Members", priority = 10)
        val guild = Guild(
            id = guildId,
            name = "The Peaceful Collective",
            description = "<green>Friendly to new players</green>",
            level = 41,
            mode = GuildMode.PEACEFUL,
            createdAt = Instant.parse("2024-08-09T00:00:00Z"),
            isOpen = true,
        )

        every { guildService.getAllGuilds() } returns setOf(guild)
        every { memberService.getGuildMembers(guildId) } returns setOf(
            Member(ownerId, guildId, ownerRank.id, Instant.EPOCH),
            Member(memberId, guildId, memberRank.id, Instant.EPOCH),
        )
        every { memberService.getMemberLimit(guildId) } returns 100
        every { rankService.listRanks(guildId) } returns setOf(memberRank, ownerRank)
        every { progressionRepository.getGuildProgression(guildId) } returns GuildProgression(
            guildId = guildId,
            totalExperience = 50_000,
            currentLevel = 42,
            experienceThisLevel = 3_056,
            experienceForNextLevel = 14_950,
        )
        every { prestigeService.overview(guildId) } returns null
        every { relationService.getGuildRelations(guildId) } returns emptySet()

        val names = mapOf(ownerId to "BadgersMC", memberId to "NewPlayer")
        val service = service { names[it] }

        val result = assertIs<GuildDiscordProfileLookup.Found>(
            service.lookup("the peaceful collective")
        )
        val profile = result.profile

        assertEquals("The Peaceful Collective", profile.name)
        assertEquals("Friendly to new players", profile.description)
        assertEquals(42, profile.level)
        assertEquals(50_000, profile.totalExperience)
        assertEquals(100, profile.memberLimit)
        assertEquals(listOf("Owner", "Members"), profile.rankGroups.map { it.name })
        assertEquals(listOf("BadgersMC"), profile.rankGroups.first().members)
        assertEquals(true, profile.recruiting)
        assertEquals(GuildMode.PEACEFUL, profile.mode)
    }

    @Test
    fun `partial lookup reports ambiguity instead of guessing`() {
        val first = Guild(
            id = UUID.randomUUID(),
            name = "Peace Keepers",
            createdAt = Instant.EPOCH,
        )
        val second = Guild(
            id = UUID.randomUUID(),
            name = "Peace Makers",
            createdAt = Instant.EPOCH,
        )
        every { guildService.getAllGuilds() } returns setOf(first, second)

        val result = assertIs<GuildDiscordProfileLookup.Ambiguous>(
            service { null }.lookup("peace")
        )

        assertEquals(listOf("Peace Keepers", "Peace Makers"), result.matches)
    }

    private fun service(
        playerNameResolver: (UUID) -> String?,
    ) = GuildDiscordProfileService(
        guildService = guildService,
        memberService = memberService,
        rankService = rankService,
        progressionRepository = progressionRepository,
        prestigeService = prestigeService,
        relationService = relationService,
        playerNameResolver = playerNameResolver,
    )
}
