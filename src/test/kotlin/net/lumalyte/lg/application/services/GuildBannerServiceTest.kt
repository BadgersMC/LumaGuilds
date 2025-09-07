package net.lumalyte.lg.application.services

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class GuildBannerServiceTest {
    
    private lateinit var guildId: UUID
    private lateinit var playerId: UUID
    private lateinit var bannerData: BannerDesignData
    
    @BeforeEach
    fun setUp() {
        guildId = UUID.randomUUID()
        playerId = UUID.randomUUID()
        bannerData = BannerDesignData(
            baseColor = BannerColor.BLUE,
            patterns = listOf(
                BannerPattern("STRIPE_TOP", BannerColor.WHITE),
                BannerPattern("BORDER", BannerColor.RED)
            )
        )
    }
    
    @Test
    fun `should validate banner data correctly`() {
        // Valid banner data
        val validBannerData = BannerDesignData(
            baseColor = BannerColor.RED,
            patterns = listOf(
                BannerPattern("CROSS", BannerColor.WHITE),
                BannerPattern("STRIPE_LEFT", BannerColor.BLUE)
            )
        )
        assertTrue(validBannerData.isValid())
        
        // Invalid banner data - too many patterns
        val invalidBannerData = BannerDesignData(
            baseColor = BannerColor.GREEN,
            patterns = List(7) { BannerPattern("STRIPE_TOP", BannerColor.WHITE) }
        )
        assertFalse(invalidBannerData.isValid())
    }
    
    @Test
    fun `should validate banner colors correctly`() {
        assertTrue(BannerColor.RED.isValid())
        assertTrue(BannerColor.BLUE.isValid())
        assertTrue(BannerColor.WHITE.isValid())
        assertTrue(BannerColor.BLACK.isValid())
    }
    
    @Test
    fun `should validate banner patterns correctly`() {
        val validPattern = BannerPattern("STRIPE_TOP", BannerColor.WHITE)
        assertTrue(validPattern.isValid())
        
        val validPattern2 = BannerPattern("CROSS", BannerColor.RED)
        assertTrue(validPattern2.isValid())
    }
    
    @Test
    fun `should create banner design data with correct properties`() {
        assertEquals(BannerColor.BLUE, bannerData.baseColor)
        assertEquals(2, bannerData.patterns.size)
        assertTrue(bannerData.isValid())
    }
    
    @Test
    fun `should create banner pattern with correct properties`() {
        val pattern = BannerPattern("STRIPE_TOP", BannerColor.WHITE)
        assertEquals("STRIPE_TOP", pattern.type)
        assertEquals(BannerColor.WHITE, pattern.color)
        assertTrue(pattern.isValid())
    }
    
    @Test
    fun `should handle empty patterns correctly`() {
        val emptyPatternBanner = BannerDesignData(
            baseColor = BannerColor.RED,
            patterns = emptyList()
        )
        assertTrue(emptyPatternBanner.isValid())
    }
    
    @Test
    fun `should handle maximum pattern limit correctly`() {
        val maxPatternBanner = BannerDesignData(
            baseColor = BannerColor.GREEN,
            patterns = List(6) { BannerPattern("STRIPE_TOP", BannerColor.WHITE) }
        )
        assertTrue(maxPatternBanner.isValid())
        
        val overLimitBanner = BannerDesignData(
            baseColor = BannerColor.GREEN,
            patterns = List(7) { BannerPattern("STRIPE_TOP", BannerColor.WHITE) }
        )
        assertFalse(overLimitBanner.isValid())
    }
}
