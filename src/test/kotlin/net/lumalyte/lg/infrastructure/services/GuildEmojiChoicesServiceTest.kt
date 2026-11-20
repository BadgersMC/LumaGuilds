package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.GuildEmojiRules
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** REQ-216 to REQ-218: the picker and saving go by the approved list, not by the player's emoji permissions. */
internal class GuildEmojiChoicesServiceTest {
    private val vibe = Guild(UUID.randomUUID(), "Vibe", emoji = ":donor_head_abc:", createdAt = Instant.EPOCH)
    private val other = Guild(UUID.randomUUID(), "WeOnTop", createdAt = Instant.EPOCH)
    private val config = mockk<ConfigService>()
    private val everyPermission = mockk<Player>(relaxed = true).also { every { it.hasPermission(any<String>()) } returns true }
    private val noPermission = mockk<Player>(relaxed = true).also { every { it.hasPermission(any<String>()) } returns false }
    private val service = NexoEmojiService(
        config,
        NexoGlyphResolver { name -> ResolvedNexoGlyph("", "minecraft:default", true, name) },
        null,
        { id -> listOf(vibe, other).firstOrNull { it.id == id } },
    )

    init {
        every { config.loadConfig() } returns MainConfig().apply {
            guild.emojiChoices = GuildEmojiRules(listOf("sheesh", "skull"), mapOf("vibe" to "vibe_logo"))
        }
    }

    @Test
    fun pickerListsTheApprovedEmojisWhateverThePlayerHolds() {
        assertEquals(listOf("sheesh", "skull"), service.getPlayerUnlockedEmojis(everyPermission, other.id))
        assertEquals(listOf("sheesh", "skull"), service.getPlayerUnlockedEmojis(noPermission, other.id))
        assertEquals(listOf("vibe_logo", "sheesh", "skull"), service.getPlayerUnlockedEmojis(noPermission, vibe.id))
    }

    @Test
    fun savingFollowsTheSameRules() {
        assertTrue(service.hasGuildEmojiPermission(noPermission, ":skull:", other.id))
        assertFalse(service.hasGuildEmojiPermission(everyPermission, ":donor_head_abc:", other.id))
        assertFalse(service.hasGuildEmojiPermission(everyPermission, ":vibe_logo:", other.id))
        assertTrue(service.hasGuildEmojiPermission(noPermission, ":vibe_logo:", vibe.id))
        assertTrue(service.hasGuildEmojiPermission(noPermission, ":donor_head_abc:", vibe.id), "Vibe keeps its current emoji")
    }
}
