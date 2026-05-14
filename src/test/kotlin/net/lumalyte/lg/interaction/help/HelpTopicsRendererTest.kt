package net.lumalyte.lg.interaction.help

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HelpTopicsRendererTest {

    private val renderer = HelpTopicsRenderer

    @Test
    fun `topic menu renders a non-empty component`() {
        val component = renderer.renderTopicMenu()
        assertTrue(component != Component.empty())
    }

    @Test
    fun `topic menu lists every topic in HelpTopics`() {
        val rendered = renderer.renderTopicMenu().toPlainText()
        HelpTopics.all.forEach { topic ->
            assertTrue(
                topic.displayName in rendered,
                "Topic menu is missing display name '${topic.displayName}'",
            )
        }
    }

    @Test
    fun `each topic entry has a click event running the help command`() {
        val rendered = renderer.renderTopicMenu()
        HelpTopics.all.forEach { topic ->
            val match = rendered.findRunCommandClick("/g help ${topic.slug}")
            assertNotNull(match, "No RUN_COMMAND click for /g help ${topic.slug}")
        }
    }

    @Test
    fun `topic menu includes a wiki link at the bottom`() {
        val rendered = renderer.renderTopicMenu().toPlainText()
        assertTrue(HelpTopics.WIKI_BASE_URL in rendered)
    }

    @Test
    fun `topic page header includes the topic display name`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes).toPlainText()
        assertTrue("Help · Homes" in rendered)
    }

    @Test
    fun `topic page lists every command syntax for that topic`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes).toPlainText()
        homes.commands.forEach { entry ->
            assertTrue(entry.syntax in rendered, "Missing syntax '${entry.syntax}'")
        }
    }

    @Test
    fun `command entries with a prefill use SUGGEST_COMMAND click`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes)
        val sethomeClick = rendered.findSuggestCommandClick("/g sethome ")
        assertNotNull(sethomeClick, "No SUGGEST_COMMAND click prefilling '/g sethome '")
    }

    @Test
    fun `topic page includes deep link to matching wiki URL`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes).toPlainText()
        assertTrue("${HelpTopics.WIKI_BASE_URL}/homes/" in rendered)
    }

    @Test
    fun `topic page has a Back to topics action`() {
        val homes = HelpTopics.bySlug("homes")!!
        val rendered = renderer.renderTopicPage(homes)
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
