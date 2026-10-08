// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Seasonal icon naming and which style a player sees (REQ-121). */
internal class SeasonalIconsTest {
    /** Holiday styles name their variants `<id>_<style>`. */
    @Test
    fun holidayStylesNameVariants() {
        assertEquals("lg_back_halloween", SeasonalIcons.variantId("lg_back", GuiTheme.HALLOWEEN))
        assertEquals("lg_nav_info_christmas", SeasonalIcons.variantId("lg_nav_info", GuiTheme.CHRISTMAS))
    }

    /** Every other style keeps the normal icons. */
    @Test
    fun otherStylesHaveNoVariants() {
        GuiTheme.entries.filterNot { it.seasonalIcons }.forEach {
            assertNull(SeasonalIcons.variantId("lg_back", it), it.name)
        }
    }

    /** Only the holiday styles re-skin icons, and both are earned. */
    @Test
    fun seasonalStylesMatch() {
        assertEquals(listOf(GuiTheme.HALLOWEEN, GuiTheme.CHRISTMAS), GuiTheme.entries.filter { it.seasonalIcons })
        assertEquals(GuiTheme.entries.filter { it.seasonalIcons }, GuiTheme.entries.filter { it.requiresUnlock })
    }

    /** A member of a seasonal-style guild sees that style; others see normal icons. */
    @Test
    fun styleForGuildThemes() {
        assertEquals(GuiTheme.HALLOWEEN, SeasonalIcons.styleFor(listOf(GuiTheme.ENTHUSIA, GuiTheme.HALLOWEEN)))
        assertNull(SeasonalIcons.styleFor(listOf(GuiTheme.ENTHUSIA, GuiTheme.VANILLA)))
        assertNull(SeasonalIcons.styleFor(emptyList()))
    }

    /** Pdc key matches the raw custom_data path the packet hook reads. */
    @Test
    fun pdcKeyMatchesRawPath() {
        assertEquals(SeasonalIcons.PDC_KEY, SeasonalIcons.ICON_KEY.toString())
    }
}
