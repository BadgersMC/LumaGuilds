package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * Verifies that GuildDashboard uses the correct Nexo item ID for filler slots.
 *
 * The filler must be a dedicated `lg_filler` item, NOT a background overlay
 * (`lg_bg_*`). Background overlays are full-menu textures intended for the
 * resource pack overlay system, not for individual inventory slots.
 */
internal class GuildDashboardFillerItemTest {

    private fun findConstantValue(): String? {
        // Look for a static String field with FILLER in its name
        for (field in GuildDashboard::class.java.declaredFields) {
            if (Modifier.isStatic(field.modifiers) &&
                Modifier.isPublic(field.modifiers) &&
                field.type == String::class.java &&
                field.name.uppercase().contains("FILLER")
            ) {
                field.isAccessible = true
                return field.get(null) as? String
            }
        }
        // Also check companion object if it exists
        for (inner in GuildDashboard::class.java.declaredClasses) {
            if (inner.simpleName == "Companion") {
                for (field in inner.declaredFields) {
                    if (Modifier.isPublic(field.modifiers) &&
                        field.type == String::class.java &&
                        field.name.uppercase().contains("FILLER")
                    ) {
                        field.isAccessible = true
                        return field.get(null) as? String
                    }
                }
            }
        }
        return null
    }

    @Test
    fun `GuildDashboard declares a FILLER_NEXO_ID constant`() {
        val value = findConstantValue()
        assertNotNull(value) {
            "GuildDashboard must declare a public static String constant with 'FILLER' in its name " +
            "(e.g. FILLER_NEXO_ID or FILLER_ITEM_ID) set to \"lg_filler\"."
        }
    }

    @Test
    fun `filler Nexo item ID is "lg_filler"`() {
        val value = findConstantValue()
        assertNotNull(value) {
            "GuildDashboard must declare a public static filler constant."
        }
        assert(value == "lg_filler") {
            "Expected filler Nexo item ID to be \"lg_filler\", but got \"$value\". " +
            "Background overlay IDs (lg_bg_*) are not valid filler items."
        }
    }

    @Test
    fun `filler Nexo item ID is not a background overlay ID`() {
        val value = findConstantValue()
        if (value != null) {
            assert(!value.startsWith("lg_bg_")) {
                "Filler item ID must not be a background overlay ID. Got: \"$value\" (starts with lg_bg_)."
            }
        }
    }
}