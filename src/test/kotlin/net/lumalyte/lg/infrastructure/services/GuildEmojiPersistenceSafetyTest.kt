package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** Regression coverage for guild emoji validation and display. */
internal class GuildEmojiPersistenceSafetyTest {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val repository = mockk<GuildRepository>(relaxed = true)
    private val menuGlyph = ResolvedNexoGlyph("ꐘ", "nexo:default", false)
    private val emojiService = NexoEmojiService(mockk(), NexoGlyphResolver { menuGlyph })

    private fun service(): GuildServiceBukkit {
        every { repository.getById(guildId) } returns Guild(guildId, "Vegas", createdAt = Instant.EPOCH)
        every { repository.update(any()) } returns true
        val service = spyk(guildService())
        every { service.hasPermission(actorId, guildId, any()) } returns true
        return service
    }

    private fun guildService(): GuildServiceBukkit {
        val service =
            GuildServiceBukkit(
                guildRepository = repository,
                rankRepository = mockk(relaxed = true),
                memberRepository = mockk(relaxed = true),
                rankService = mockk(relaxed = true),
                memberService = mockk(relaxed = true),
                nexoEmojiService = emojiService,
                vaultService = mockk(relaxed = true),
                hologramService = mockk(relaxed = true),
                relationRepository = mockk(relaxed = true),
                historyRepository = mockk(relaxed = true),
                adminOverrideService = mockk(relaxed = true),
            )
        return service
    }

    /** Even an authorized direct caller cannot persist a menu glyph. */
    @Test
    fun rejectsMenuPersistence() {
        assertFalse(service().setEmoji(guildId, ":guild_bg_enthusia_6_row:", actorId))
        verify(exactly = 0) { repository.update(any()) }
    }

    /** Clearing an emoji remains possible without Nexo or a valid prior glyph. */
    @Test
    fun allowsEmojiRemoval() {
        assertTrue(service().setEmoji(guildId, null, actorId))
        verify { repository.update(match { it.emoji == null }) }
    }
}
