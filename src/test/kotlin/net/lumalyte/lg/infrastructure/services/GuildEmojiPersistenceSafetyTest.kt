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

class GuildEmojiPersistenceSafetyTest {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val repository = mockk<GuildRepository>(relaxed = true)
    private val emojiService = NexoEmojiService(mockk(), NexoGlyphResolver {
        ResolvedNexoGlyph("ꐘ", "nexo:default", false)
    })

    private fun service(): GuildServiceBukkit {
        every { repository.getById(guildId) } returns Guild(guildId, "Vegas", createdAt = Instant.EPOCH)
        every { repository.update(any()) } returns true
        val service = spyk(GuildServiceBukkit(
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
        ))
        every { service.hasPermission(actorId, guildId, any()) } returns true
        return service
    }

    @Test
    fun `authorized direct service caller cannot persist a menu glyph`() {
        assertFalse(service().setEmoji(guildId, ":guild_bg_enthusia_6_row:", actorId))
        verify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `authorized actor can still clear an invalid saved emoji`() {
        assertTrue(service().setEmoji(guildId, null, actorId))
        verify { repository.update(match { it.emoji == null }) }
    }
}
