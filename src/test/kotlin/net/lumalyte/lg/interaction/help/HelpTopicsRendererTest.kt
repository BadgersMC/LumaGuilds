package net.lumalyte.lg.interaction.help

import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HelpTopicsRendererTest {

    @TempDir
    lateinit var dataFolder: Path

    private val renderer = HelpTopicsRenderer
    private val lang: LangService
        get() = LangService(
            object : LangHost {
                override val dataFolder: File = this@HelpTopicsRendererTest.dataFolder.toFile()
                override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
            },
            Locale("en_US"),
            LumaGuildsLang::class.java,
        )

    @Test
    fun `topic menu renders a non-empty component`() {
        val component = renderer.renderTopicMenu(lang)
        assertTrue(component != Component.empty())
    }

    @Test
    fun `topic menu lists every topic in HelpTopics`() {
        val rendered = renderer.renderTopicMenu(lang).toPlainText()
        HelpTopics.all.forEach { topic ->
            assertTrue(
                lang.msg(topic.menuKey).toPlainText() in rendered,
                "Topic menu is missing localized row '${topic.menuKey}'",
            )
        }
    }

    @Test
    fun `each topic entry has a click event running the help command`() {
        val rendered = renderer.renderTopicMenu(lang)
        HelpTopics.all.forEach { topic ->
            val match = rendered.findRunCommandClick("/g help ${topic.slug}")
            assertNotNull(match, "No RUN_COMMAND click for /g help ${topic.slug}")
        }
    }

    @Test
    fun `topic menu includes a wiki link at the bottom`() {
        val rendered = renderer.renderTopicMenu(lang).toPlainText()
        assertTrue(HelpTopics.WIKI_BASE_URL in rendered)
    }

    @Test
    fun `topic page header includes the topic display name`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes, lang).toPlainText()
        assertTrue("Help · Homes" in rendered)
    }

    @Test
    fun `topic page lists every command syntax for that topic`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes, lang).toPlainText()
        assertTrue("/g sethome [name]" in rendered)
        assertTrue("/g removeallyhome" in rendered)
    }

    @Test
    fun `command entries with a prefill use SUGGEST_COMMAND click`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes, lang)
        val sethomeClick = rendered.findSuggestCommandClick("/g sethome ")
        assertNotNull(sethomeClick, "No SUGGEST_COMMAND click prefilling '/g sethome '")
    }

    @Test
    fun `topic page includes deep link to matching wiki URL`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes, lang).toPlainText()
        assertTrue("${HelpTopics.WIKI_BASE_URL}/homes/" in rendered)
    }

    @Test
    fun `topic page has a Back to topics action`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes, lang)
        val back = rendered.findRunCommandClick("/g help")
        assertNotNull(back, "No RUN_COMMAND click for '/g help' (Back action)")
    }
}

private fun Component.toPlainText(): String =
    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(this)

private fun Component.allComponents(): List<Component> =
    listOf(this) + children().flatMap { it.allComponents() }

private fun Component.findRunCommandClick(command: String): Component? =
    allComponents().firstOrNull {
        val ce = it.clickEvent()
        ce?.action() == ClickEvent.Action.RUN_COMMAND && ce.value() == command
    }

private fun Component.findSuggestCommandClick(value: String): Component? =
    allComponents().firstOrNull {
        val ce = it.clickEvent()
        ce?.action() == ClickEvent.Action.SUGGEST_COMMAND && ce.value() == value
    }
