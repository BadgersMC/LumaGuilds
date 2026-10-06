package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.GuildCosmeticUnlockService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-121: setGuiTheme enforces holiday theme ownership for every caller. */
class GuildServiceThemeUnlockTest {
    private val guildId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val ownerRankId = UUID.randomUUID()
    private lateinit var guildRepository: GuildRepository
    private lateinit var themeAccess: GuildCosmeticUnlockService

    private fun service(access: GuildCosmeticUnlockService?) = GuildServiceBukkit(
        guildRepository = guildRepository,
        rankRepository = mockk<RankRepository>(relaxed = true).also {
            every { it.getById(ownerRankId) } returns Rank(id = ownerRankId, guildId = guildId, name = "Owner", priority = 0, permissions = emptySet())
        },
        memberRepository = mockk<MemberRepository>(relaxed = true).also {
            every { it.getByPlayerAndGuild(ownerId, guildId) } returns Member(ownerId, guildId, ownerRankId, Instant.now())
        },
        rankService = mockk(relaxed = true),
        memberService = mockk(relaxed = true),
        nexoEmojiService = mockk(relaxed = true),
        vaultService = mockk(relaxed = true),
        hologramService = mockk(relaxed = true),
        relationRepository = mockk(relaxed = true),
        historyRepository = mockk(relaxed = true),
        adminOverrideService = mockk<AdminOverrideService>(relaxed = true).also { every { it.hasOverride(any()) } returns false },
        themeAccess = access,
    )

    @BeforeEach
    fun setUp() {
        guildRepository = mockk(relaxed = true)
        every { guildRepository.getById(guildId) } returns Guild(id = guildId, name = "Enthusiasts", createdAt = Instant.now())
        every { guildRepository.update(any()) } returns true
        themeAccess = mockk()
    }

    @Test
    fun `locked holiday theme is rejected`() {
        every { themeAccess.isThemeAvailable(guildId, GuiTheme.HALLOWEEN) } returns false
        assertFalse(service(themeAccess).setGuiTheme(guildId, GuiTheme.HALLOWEEN, ownerId))
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    @Test
    fun `owned holiday theme is applied`() {
        every { themeAccess.isThemeAvailable(guildId, GuiTheme.HALLOWEEN) } returns true
        assertTrue(service(themeAccess).setGuiTheme(guildId, GuiTheme.HALLOWEEN, ownerId))
        verify { guildRepository.update(match { it.guiTheme == GuiTheme.HALLOWEEN }) }
    }

    @Test
    fun `without an unlock ledger holiday themes fail closed and progression themes still work`() {
        assertFalse(service(null).setGuiTheme(guildId, GuiTheme.CHRISTMAS, ownerId))
        assertTrue(service(null).setGuiTheme(guildId, GuiTheme.EMBERSTONE, ownerId))
    }
}
