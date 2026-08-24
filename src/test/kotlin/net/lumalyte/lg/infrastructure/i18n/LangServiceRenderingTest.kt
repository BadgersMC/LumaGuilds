package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LangServiceRenderingTest {

    @TempDir
    lateinit var dataFolder: Path

    private val plainText = PlainTextComponentSerializer.plainText()

    @Test
    fun `claim message renders a named claim placeholder`() {
        val rendered = langService().msg(
            "command.claim.remove.success",
            "claim" to "North Farm",
        )

        assertEquals(
            "Partition has been removed for claim North Farm.",
            plainText.serialize(rendered),
        )
    }

    @Test
    fun `partition location renders four named coordinate placeholders`() {
        val rendered = langService().msg(
            "menu.edit_tool.item.partition.lore.location",
            "lower_x" to 10,
            "lower_z" to 20,
            "upper_x" to 30,
            "upper_z" to 40,
        )

        assertEquals(
            "Lower (10, 20) | Upper (30, 40)",
            plainText.serialize(rendered),
        )
    }

    @Test
    fun `trust message renders named permission player and claim placeholders`() {
        val rendered = langService().msg(
            "command.claim.trust.success",
            "permission" to "Build",
            "player" to "Ada",
            "claim" to "North Farm",
        )

        assertEquals(
            "Permission Build has been assigned to player Ada in claim North Farm.",
            plainText.serialize(rendered),
        )
    }

    private fun langService(): LangService = LangService(
        object : LangHost {
            override val dataFolder: File = this@LangServiceRenderingTest.dataFolder.toFile()
            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
        },
        Locale("en_US"),
        LumaGuildsLang::class.java,
    )
}
