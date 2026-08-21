package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.infrastructure.i18n.LocaleSourceScanner
import net.lumalyte.lg.interaction.menus.guild.TagEditorMenu
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class TagValidationLocalizationTest {

    @TempDir
    lateinit var dataFolder: Path

    private lateinit var server: ServerMock
    private lateinit var lang: LangService
    private lateinit var configService: ConfigService

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        lang = LangService(
            object : LangHost {
                override val dataFolder: File = this@TagValidationLocalizationTest.dataFolder.toFile()
                override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
            },
            Locale("en_US"),
            LumaGuildsLang::class.java,
        )
        configService = mockk {
            every { loadConfig() } returns MainConfig()
        }
        stopKoin()
        startKoin {
            modules(module {
                single { lang }
                single { configService }
            })
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        MockBukkit.unmock()
    }

    @Test
    fun `java tag editor localizes typed interactive failure`() {
        val player = server.addPlayer()
        val menu = TagEditorMenu(mockk(relaxed = true), player, guild())
        val validate = TagEditorMenu::class.java.getDeclaredMethod("validateTag", String::class.java)
            .apply { isAccessible = true }

        val result = validate.invoke(menu, "<click:run_command:'/op me'>Click") as String?

        assertEquals(
            lang.legacy("command.guild.tag.validation.interactive", "tag" to "click"),
            result,
        )
    }

    @Test
    fun `both tag editor adapters reference localized typed failures`() {
        val sourceRoot = Path.of(System.getProperty("user.dir"))
            .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus")
        val adapters = listOf(
            sourceRoot.resolve("guild/TagEditorMenu.kt"),
            sourceRoot.resolve("bedrock/BedrockTagEditorMenu.kt"),
        )
        assertEquals(
            listOf(
                setOf(
                    "command.guild.tag.validation.interactive",
                    "command.guild.tag.validation.inappropriate",
                ),
                setOf(
                    "command.guild.tag.validation.interactive",
                    "command.guild.tag.validation.inappropriate",
                ),
            ),
            adapters.map { LocaleSourceScanner.scan(it).literalKeys.intersect(
                setOf(
                    "command.guild.tag.validation.interactive",
                    "command.guild.tag.validation.inappropriate",
                ),
            ) },
        )
    }

    private fun guild(): Guild = Guild(
        id = UUID.randomUUID(),
        name = "Starlight",
        createdAt = Instant.parse("2026-08-20T12:00:00Z"),
    )
}
