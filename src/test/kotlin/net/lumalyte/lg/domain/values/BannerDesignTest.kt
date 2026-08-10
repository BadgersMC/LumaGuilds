package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerDesignTest {

    @Test
    fun `valid design with a known pattern type is accepted`() {
        val design = BannerDesignData(
            baseColor = BannerColor.RED,
            patterns = listOf(BannerPattern("STRIPE_TOP", BannerColor.WHITE))
        )
        assertTrue(design.isValid())
    }

    @Test
    fun `design with no patterns is valid`() {
        val design = BannerDesignData(baseColor = BannerColor.BLUE)
        assertTrue(design.isValid())
    }

    @Test
    fun `blank pattern type is rejected`() {
        val design = BannerDesignData(
            baseColor = BannerColor.RED,
            patterns = listOf(BannerPattern("   ", BannerColor.WHITE))
        )
        assertFalse(design.isValid())
    }

    @Test
    fun `more than six patterns is rejected`() {
        val design = BannerDesignData(
            baseColor = BannerColor.RED,
            patterns = List(7) { BannerPattern("STRIPE_TOP", BannerColor.WHITE) }
        )
        assertFalse(design.isValid())
    }

    @Test
    fun `six patterns is the limit and is accepted`() {
        val design = BannerDesignData(
            baseColor = BannerColor.RED,
            patterns = List(6) { BannerPattern("STRIPE_TOP", BannerColor.WHITE) }
        )
        assertTrue(design.isValid())
    }
}
