package net.lumalyte.lg.interaction.menus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.guild.TagEditorMenu
import net.lumalyte.lg.utils.GuildTagValidationMessages
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class TagValidationLocalizationTest {

    private lateinit var server: ServerMock
    private lateinit var lang: LangService
    private lateinit var configService: ConfigService

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        lang = mockk {
            every {
                legacy("command.guild.tag.validation.interactive", "tag" to "click")
            } returns "SENTINEL interactive click"
            every {
                legacy("command.guild.tag.validation.inappropriate")
            } returns "SENTINEL inappropriate"
        }
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
            "SENTINEL interactive click",
            result,
        )
    }

    @Test
    fun `shared tag failure renderer uses locale output for every typed failure`() {
        assertEquals(
            "SENTINEL interactive click",
            GuildTagValidationMessages.legacy(
                lang,
                net.lumalyte.lg.utils.GuildTagValidator.Failure.InteractiveTag("click"),
            ),
        )
        assertEquals(
            "SENTINEL inappropriate",
            GuildTagValidationMessages.legacy(
                lang,
                net.lumalyte.lg.utils.GuildTagValidator.Failure.InappropriateContent,
            ),
        )
    }

    @Test
    fun `bedrock validation result carries localized sentinel output`() {
        val result = GuildTagValidationMessages.invalid(
            lang,
            net.lumalyte.lg.utils.GuildTagValidator.Failure.InteractiveTag("click"),
        )

        assertEquals(false, result.isValid)
        assertEquals("SENTINEL interactive click", result.errorMessage)
    }

    @Test
    fun `both tag editor adapters delegate failures without recreating english`() {
        val sourceRoot = Path.of(System.getProperty("user.dir"))
            .resolve("src/main/kotlin/net/lumalyte/lg/interaction/menus")
        val adapters = listOf(
            sourceRoot.resolve("guild/TagEditorMenu.kt"),
            sourceRoot.resolve("bedrock/BedrockTagEditorMenu.kt"),
        )
        val sources = adapters.map { java.nio.file.Files.readString(it) }

        assertEquals(true, "GuildTagValidationMessages.legacy(lang, it)" in sources[0])
        assertEquals(
            true,
            "return@getValidator GuildTagValidationMessages.invalid(lang, it)" in sources[1],
        )
        assertEquals(
            listOf(emptyList(), emptyList()),
            sources.map { source ->
                listOf(
                    "Guild tags cannot contain interactive",
                    "Guild tag contains inappropriate content",
                ).filter(source::contains)
            },
        )
    }

    private fun guild(): Guild = Guild(
        id = UUID.randomUUID(),
        name = "Starlight",
        createdAt = Instant.parse("2026-08-20T12:00:00Z"),
    )
}
