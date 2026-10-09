// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-121: setGuiTheme enforces holiday theme ownership for every caller. */
internal class GuildServiceThemeUnlockTest {
    private val guildId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val ownerRankId = UUID.randomUUID()
    private var guildRepository: GuildRepository by kotlin.properties.Delegates.notNull()
    private var themeAccess: GuildCosmeticUnlockService by kotlin.properties.Delegates.notNull()

    private fun service(access: GuildCosmeticUnlockService?): GuildServiceBukkit {
        val instance =
            GuildServiceBukkit(
                guildRepository = guildRepository, rankRepository = ownerRanks(), memberRepository = ownerMembership(),
                rankService = mockk(relaxed = true), memberService = mockk(relaxed = true),
                nexoEmojiService = mockk(relaxed = true), vaultService = mockk(relaxed = true),
                hologramService = mockk(relaxed = true), relationRepository = mockk(relaxed = true),
                historyRepository = mockk(relaxed = true), adminOverrideService = noAdminOverride(),
                homeActivationService = mockk(relaxed = true), themeAccess = access,
            )
        return instance
    }

    private fun ownerRanks(): RankRepository {
        val ranks = mockk<RankRepository>(relaxed = true)
        every { ranks.getById(ownerRankId) } returns
            Rank(id = ownerRankId, guildId = guildId, name = "Owner", priority = 0, permissions = emptySet())
        return ranks
    }

    private fun ownerMembership(): MemberRepository {
        val members = mockk<MemberRepository>(relaxed = true)
        every { members.getByPlayerAndGuild(ownerId, guildId) } returns
            Member(ownerId, guildId, ownerRankId, Instant.now())
        return members
    }

    private fun noAdminOverride(): AdminOverrideService {
        val overrides = mockk<AdminOverrideService>(relaxed = true)
        every { overrides.hasOverride(any()) } returns false
        return overrides
    }

    /** Initialize the disposable fixture and service dependencies. */
    @BeforeEach
    fun setUp() {
        guildRepository = mockk(relaxed = true)
        every { guildRepository.getById(guildId) } returns
            Guild(id = guildId, name = "Enthusiasts", createdAt = Instant.now())
        every { guildRepository.update(any()) } returns true
        every { guildRepository.updateGuiTheme(guildId, any(), any()) } returns true
        themeAccess = mockk()
    }

    /** Locked holiday theme is rejected. */
    @DisplayName("locked holiday theme is rejected")
    @Test
    fun scenario1() {
        every { themeAccess.isThemeAvailable(guildId, GuiTheme.HALLOWEEN) } returns false
        assertFalse(service(themeAccess).setGuiTheme(guildId, GuiTheme.HALLOWEEN, ownerId))
        verify(exactly = 0) { guildRepository.updateGuiTheme(guildId, any(), any()) }
    }

    /** Owned holiday theme is applied. */
    @DisplayName("owned holiday theme is applied")
    @Test
    fun scenario2() {
        every { themeAccess.isThemeAvailable(guildId, GuiTheme.HALLOWEEN) } returns true
        assertTrue(service(themeAccess).setGuiTheme(guildId, GuiTheme.HALLOWEEN, ownerId))
        verify { guildRepository.updateGuiTheme(guildId, GuiTheme.ENTHUSIA, GuiTheme.HALLOWEEN) }
    }

    /** Without an unlock ledger holiday themes fail closed and progression themes still work. */
    @DisplayName("without an unlock ledger holiday themes fail closed and progression themes still work")
    @Test
    fun scenario3() {
        assertFalse(service(null).setGuiTheme(guildId, GuiTheme.CHRISTMAS, ownerId))
        assertTrue(service(null).setGuiTheme(guildId, GuiTheme.EMBERSTONE, ownerId))
    }
}
