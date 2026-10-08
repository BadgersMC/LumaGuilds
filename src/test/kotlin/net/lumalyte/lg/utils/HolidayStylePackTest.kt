// Explicit fixture numbers document persisted coordinates, icon dimensions and approved boundaries.
@file:Suppress("MagicNumber")

package net.lumalyte.lg.utils

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO

/** The shipped Nexo pack has everything the holiday styles draw (REQ-121). */
internal class HolidayStylePackTest {
    private val pack = File("resourcepack/enthusia-icons/Nexo")
    private val holidayStyles = GuiTheme.entries.filter { it.requiresUnlock }

    private fun yaml(path: String) = YamlConfiguration.loadConfiguration(File(pack, path))

    private fun textureFile(texture: String, namespaceDir: String = "minecraft"): File {
        val (ns, rel) = if (":" in texture) texture.split(":", limit = 2) else listOf(namespaceDir, texture)
        return File(pack, "pack/assets/$ns/textures/$rel.png")
    }

    /** Every row count has a background glyph whose texture is a 256x256 canvas. */
    @Test
    fun backgroundsForEveryRow() {
        val glyphs = yaml("glyphs/lumaguilds_holiday_styles.yml")
        holidayStyles.forEach { style ->
            for (rows in 1..6) {
                val id = "guild_bg_${style.name.lowercase()}_${rows}_row"
                val texture = glyphs.getString("$id.texture")
                assertTrue(texture != null, "missing glyph $id")
                val image = ImageIO.read(textureFile(texture!!))
                assertEquals(256, image.width, id)
                assertEquals(256, image.height, id)
            }
        }
    }

    /** Glyph chars never collide across the pack. */
    @Test
    fun glyphCharsAreUnique() {
        val chars = File(pack, "glyphs").listFiles { f -> f.extension == "yml" }!!.flatMap { file ->
            val yaml = YamlConfiguration.loadConfiguration(file)
            yaml.getKeys(false).mapNotNull { yaml.getString("$it.char") }
        }
        assertEquals(chars.size, chars.toSet().size)
    }

    /** Swatches and seasonal icons resolve to real 16x16 textures. */
    @Test
    fun holidayItemsHaveTextures() {
        val items = yaml("items/lumaguilds_holiday_styles.yml")
        holidayStyles.forEach { assertTrue(items.contains("lg_theme_${it.name.lowercase()}"), it.name) }
        items.getKeys(false).forEach { id ->
            val image = ImageIO.read(textureFile(items.getString("$id.Pack.texture")!!))
            assertEquals(16, image.width, id)
            assertEquals(16, image.height, id)
        }
    }

    /** Each seasonal icon is the `<id>_<style>` variant of an existing icon. */
    @Test
    fun seasonalIconsVaryExistingIcons() {
        val base = existingIconIds()
        val seasonal =
            yaml("items/lumaguilds_holiday_styles.yml").getKeys(false).filterNot {
                it.startsWith("lg_theme_")
            }
        assertTrue(seasonal.isNotEmpty())
        seasonal.forEach { id ->
            val style = holidayStyles.first { id.endsWith("_" + it.name.lowercase()) }
            val iconId = id.removeSuffix("_" + style.name.lowercase())
            assertTrue(iconId in base, id)
            assertEquals(id, SeasonalIcons.variantId(iconId, style))
        }
    }
    private fun existingIconIds(): Set<String> {
        val base =
            yaml("items/lumaguilds_enthusia_icons.yml").getKeys(false) +
                YamlConfiguration.loadConfiguration(
                    File("resourcepack/enthusia-icons/server-kit/lg_enthusia_gui.yml"),
                ).getKeys(false) +
                YamlConfiguration.loadConfiguration(
                    File("resourcepack/enthusia-icons/server-kit/repoint-textures.yml"),
                ).getKeys(false)
            return base
        }
}
