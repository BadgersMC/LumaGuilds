package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import org.bukkit.entity.Player
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class NexoEmojiServiceTest {
    
    private lateinit var nexoEmojiService: NexoEmojiService
    private lateinit var mockPlayer: Player
    
    @BeforeEach
    fun setUp() {
        nexoEmojiService = NexoEmojiService()
        mockPlayer = mockk<Player>()
    }
    
    @Test
    fun `should validate valid emoji format`() {
        assertTrue(nexoEmojiService.isValidEmojiFormat(":catsmileysmile:"))
        assertTrue(nexoEmojiService.isValidEmojiFormat(":flushedwithglasses:"))
        assertTrue(nexoEmojiService.isValidEmojiFormat(":joobiamazed:"))
        assertTrue(nexoEmojiService.isValidEmojiFormat(":a:"))
    }
    
    @Test
    fun `should reject invalid emoji formats`() {
        assertFalse(nexoEmojiService.isValidEmojiFormat("catsmileysmile"))
        assertFalse(nexoEmojiService.isValidEmojiFormat(":catsmileysmile"))
        assertFalse(nexoEmojiService.isValidEmojiFormat("catsmileysmile:"))
        assertFalse(nexoEmojiService.isValidEmojiFormat("::"))
        assertFalse(nexoEmojiService.isValidEmojiFormat(":"))
        assertFalse(nexoEmojiService.isValidEmojiFormat(""))
    }
    
    @Test
    fun `should check individual emoji permissions correctly`() {
        every { mockPlayer.hasPermission("lumalyte.emoji.catsmileysmile") } returns true
        every { mockPlayer.name } returns "TestPlayer"
        
        assertTrue(nexoEmojiService.hasEmojiPermission(mockPlayer, ":catsmileysmile:"))
    }
    
    @Test
    fun `should reject if player lacks specific emoji permission`() {
        every { mockPlayer.hasPermission("lumalyte.emoji.catsmileysmile") } returns false
        every { mockPlayer.name } returns "TestPlayer"
        
        assertFalse(nexoEmojiService.hasEmojiPermission(mockPlayer, ":catsmileysmile:"))
    }
    
    @Test
    fun `should check different emojis with different permissions`() {
        every { mockPlayer.hasPermission("lumalyte.emoji.flushedwithglasses") } returns true
        every { mockPlayer.hasPermission("lumalyte.emoji.joobiamazed") } returns false
        every { mockPlayer.name } returns "TestPlayer"
        
        assertTrue(nexoEmojiService.hasEmojiPermission(mockPlayer, ":flushedwithglasses:"))
        assertFalse(nexoEmojiService.hasEmojiPermission(mockPlayer, ":joobiamazed:"))
    }
    
    @Test
    fun `should reject invalid emoji format for permission check`() {
        every { mockPlayer.name } returns "TestPlayer"
        
        assertFalse(nexoEmojiService.hasEmojiPermission(mockPlayer, "invalid"))
        assertFalse(nexoEmojiService.hasEmojiPermission(mockPlayer, ":invalid"))
        assertFalse(nexoEmojiService.hasEmojiPermission(mockPlayer, "invalid:"))
    }
    
    @Test
    fun `should format guild display name with emoji`() {
        val guildName = "TestGuild"
        val emoji = ":catsmileysmile:"
        
        val formatted = nexoEmojiService.formatGuildDisplayName(guildName, emoji)
        assertEquals(":catsmileysmile: TestGuild", formatted)
    }
    
    @Test
    fun `should format guild display name without emoji`() {
        val guildName = "TestGuild"
        
        val formatted = nexoEmojiService.formatGuildDisplayName(guildName, null)
        assertEquals("TestGuild", formatted)
    }
    
    @Test
    fun `should format guild display name with invalid emoji`() {
        val guildName = "TestGuild"
        val invalidEmoji = "invalid"
        
        val formatted = nexoEmojiService.formatGuildDisplayName(guildName, invalidEmoji)
        assertEquals("TestGuild", formatted)
    }
    
    @Test
    fun `should get emoji placeholder`() {
        assertEquals(":catsmileysmile:", nexoEmojiService.getEmojiPlaceholder(":catsmileysmile:"))
        assertEquals("", nexoEmojiService.getEmojiPlaceholder(null))
        assertEquals("", nexoEmojiService.getEmojiPlaceholder("invalid"))
    }
    
    @Test
    fun `should extract emoji name from placeholder`() {
        assertEquals("catsmileysmile", nexoEmojiService.extractEmojiName(":catsmileysmile:"))
        assertEquals("flushedwithglasses", nexoEmojiService.extractEmojiName(":flushedwithglasses:"))
        assertNull(nexoEmojiService.extractEmojiName("invalid"))
        assertNull(nexoEmojiService.extractEmojiName(""))
    }
    
    @Test
    fun `should create emoji placeholder from name`() {
        assertEquals(":catsmileysmile:", nexoEmojiService.createEmojiPlaceholder("catsmileysmile"))
        assertEquals(":test:", nexoEmojiService.createEmojiPlaceholder("test"))
    }
    
    @Test
    fun `should get emoji permission node`() {
        assertEquals("lumalyte.emoji.catsmileysmile", nexoEmojiService.getEmojiPermission(":catsmileysmile:"))
        assertEquals("lumalyte.emoji.flushedwithglasses", nexoEmojiService.getEmojiPermission(":flushedwithglasses:"))
        assertEquals("lumalyte.emoji.joobiamazed", nexoEmojiService.getEmojiPermission(":joobiamazed:"))
        assertNull(nexoEmojiService.getEmojiPermission("invalid"))
        assertNull(nexoEmojiService.getEmojiPermission(""))
    }
    
    @Test
    fun `should validate emoji existence`() {
        // For now, any valid format is considered to exist
        assertTrue(nexoEmojiService.doesEmojiExist(":catsmileysmile:"))
        assertTrue(nexoEmojiService.doesEmojiExist(":flushedwithglasses:"))
        assertFalse(nexoEmojiService.doesEmojiExist("invalid"))
    }
}
