package net.lumalyte.lg.interaction.help

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HelpTopicTest {

    @Test
    fun `HelpCommandEntry stores a stable command identifier`() {
        val entry = HelpCommandEntry("sethome")
        assertEquals("sethome", entry.id)
    }

    @Test
    fun `HelpTopic derives locale keys from its stable slug`() {
        val topic = HelpTopic(
            slug = "homes",
            commands = listOf(
                HelpCommandEntry("sethome"),
                HelpCommandEntry("home"),
            ),
        )
        assertEquals("command.guild.help.topics.homes.menu", topic.menuKey)
        assertEquals("command.guild.help.topics.homes.page", topic.pageKey)
        assertEquals(2, topic.commands.size)
    }

    @Test
    fun `HelpTopic rejects an invalid slug`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            HelpTopic(
                slug = "Bad Slug!",
                commands = emptyList(),
            )
        }
        assertTrue("slug" in ex.message!!.lowercase())
    }

    @Test
    fun `HelpCommandEntry rejects an invalid identifier`() {
        assertFailsWith<IllegalArgumentException> {
            HelpCommandEntry("Bad command!")
        }
    }
}
