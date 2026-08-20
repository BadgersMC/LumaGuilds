package net.lumalyte.lg.infrastructure

import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class ChatInfoBuilderTest {

    @TempDir
    lateinit var dataFolder: Path

    @Test
    fun `localized index and page formatting remain native components`() {
        val localeDirectory = Files.createDirectories(dataFolder.resolve("lang"))
        Files.writeString(
            localeDirectory.resolve("en_US.yml"),
            """
            command:
              info_box:
                index: '<red><index>. <text></red>'
                paged: '<green>Page <current_page>/<total_pages></green>'
            """.trimIndent(),
        )
        val lang = LangService(
            object : LangHost {
                override val dataFolder: File = this@ChatInfoBuilderTest.dataFolder.toFile()
                override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
            },
            Locale("en_US"),
            LumaGuildsLang::class.java,
        )
        val builder = ChatInfoBuilder(lang, UUID.randomUUID(), "Claims")

        builder.addIndexed(2, "North Farm")
        val rendered = builder.createPaged(1, 3)

        assertEquals(
            "----- Claims -----\n2. North Farm\n-----Page 1/3",
            PlainTextComponentSerializer.plainText().serialize(rendered),
        )
    }
}
