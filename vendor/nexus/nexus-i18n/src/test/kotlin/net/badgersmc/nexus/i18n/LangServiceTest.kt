package net.badgersmc.nexus.i18n

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [LangService]. Uses an isolated [URLClassLoader] to serve
 * bundled-resource YAML files, and a hand-rolled JavaPlugin stand-in to expose
 * dataFolder + classloader. The langClass argument carries the `@LangFile`
 * annotation under test.
 */
class LangServiceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dataFolder: File
    private lateinit var resourceRoot: File
    private lateinit var classLoader: URLClassLoader
    private lateinit var langClass: Class<*>

    @LangFile(resourcePrefix = "lang", defaultLocale = "en_US")
    private class TestLangMarker

    @BeforeEach
    fun setUp() {
        dataFolder = File(tempDir, "plugin-data").also { it.mkdirs() }
        resourceRoot = Files.createTempDirectory("nexus-i18n-test-").toFile()
        // Parent must be set so that loading the YamlConfiguration / Adventure
        // classes works; tests run from the same VM classloader.
        classLoader = URLClassLoader(
            arrayOf<URL>(resourceRoot.toURI().toURL()),
            LangServiceTest::class.java.classLoader
        )
        // Reload the marker class through the isolated classloader so the
        // ResourceExtractor's `langClass.classLoader` finds our stubbed
        // resources.
        langClass = Class.forName(TestLangMarker::class.java.name, true, classLoader)
    }

    @AfterEach
    fun tearDown() {
        classLoader.close()
        resourceRoot.deleteRecursively()
    }

    private fun stub(path: String, content: String) {
        val target = File(resourceRoot, path.trimStart('/'))
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    private fun newService(locale: String = "en_US"): LangService {
        val host = TestLangHost(dataFolder, classLoader)
        return LangService(host, Locale(locale), langClass)
    }

    @Test
    fun `msg resolves a top-level key`() {
        stub("lang/en_US.yml", "greeting: \"<green>Hello\"")
        val lang = newService()

        val out = PlainTextComponentSerializer.plainText().serialize(lang.msg("greeting"))

        assertEquals("Hello", out)
    }

    @Test
    fun `msg resolves nested dotted keys`() {
        stub("lang/en_US.yml", """
            admin:
              import:
                result: "<gray>created=<green><created></green>"
        """.trimIndent())
        val lang = newService()

        val out = PlainTextComponentSerializer.plainText().serialize(
            lang.msg("admin.import.result", "created" to 3)
        )

        assertEquals("created=3", out)
    }

    @Test
    fun `prefix placeholder expands from prefix key`() {
        stub("lang/en_US.yml", """
            prefix: "<gold>[Foo]"
            hello: "<prefix> world"
        """.trimIndent())
        val lang = newService()

        val out = PlainTextComponentSerializer.plainText().serialize(lang.msg("hello"))

        assertEquals("[Foo] world", out)
    }

    @Test
    fun `missing key returns its own name as text`() {
        stub("lang/en_US.yml", "greeting: \"hi\"")
        val lang = newService()

        val out = PlainTextComponentSerializer.plainText().serialize(lang.msg("does.not.exist"))

        assertEquals("does.not.exist", out)
    }

    @Test
    fun `legacy serialises MiniMessage to legacy section codes`() {
        stub("lang/en_US.yml", "greeting: \"<red>Hello\"")
        val lang = newService()

        val out = lang.legacy("greeting")

        assertEquals(LegacyComponentSerializer.SECTION_CHAR.toString() + "cHello", out)
    }

    @Test
    fun `raw returns unrendered template without MiniMessage parsing`() {
        stub("lang/en_US.yml", "greeting: \"<red>Hello\"")
        val lang = newService()

        assertEquals("<red>Hello", lang.raw("greeting"))
    }

    @Test
    fun `bundled defaults overlay missing keys when user file is partial`() {
        // Two locale entries — en_US shipped has both keys, user file only one.
        stub("lang/en_US.yml", """
            greeting: "<green>shipped"
            farewell: "<red>shipped bye"
        """.trimIndent())
        // Pre-create user file with only one key
        val userFile = File(dataFolder, "lang/en_US.yml").apply {
            parentFile.mkdirs()
            writeText("greeting: \"<green>user override\"")
        }

        val lang = newService()

        assertEquals("user override", PlainTextComponentSerializer.plainText().serialize(lang.msg("greeting")))
        assertEquals("shipped bye", PlainTextComponentSerializer.plainText().serialize(lang.msg("farewell")))
        assertTrue(userFile.exists())
    }

    @Test
    fun `reload picks up edits to the user file`() {
        stub("lang/en_US.yml", "greeting: \"<green>shipped\"")
        val lang = newService()

        val userFile = File(dataFolder, "lang/en_US.yml")
        userFile.writeText("greeting: \"<green>edited\"")
        lang.reload()

        assertEquals("edited", PlainTextComponentSerializer.plainText().serialize(lang.msg("greeting")))
    }
}
