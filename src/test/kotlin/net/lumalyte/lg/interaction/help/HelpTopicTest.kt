package net.lumalyte.lg.interaction.help

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HelpTopicTest {

    @Test
    fun `HelpCommandEntry stores syntax, blurb and prefill`() {
        val entry = HelpCommandEntry(
            syntax = "/g sethome [name]",
            blurb = "Set a guild home at your current location.",
            prefill = "/g sethome ",
        )
        assertEquals("/g sethome [name]", entry.syntax)
        assertEquals("Set a guild home at your current location.", entry.blurb)
        assertEquals("/g sethome ", entry.prefill)
    }

    @Test
    fun `HelpTopic exposes slug, display name, summary, commands`() {
        val topic = HelpTopic(
            slug = "homes",
            displayName = "Homes",
            summary = "Set and visit guild homes.",
            commands = listOf(
                HelpCommandEntry("/g sethome [name]", "Set a home.", "/g sethome "),
                HelpCommandEntry("/g home [name]", "Visit a home.", "/g home "),
            ),
        )
        assertEquals("homes", topic.slug)
        assertEquals("Homes", topic.displayName)
        assertEquals(2, topic.commands.size)
    }

    @Test
    fun `HelpTopic rejects an invalid slug`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            HelpTopic(
                slug = "Bad Slug!",
                displayName = "Bad",
                summary = "x",
                commands = emptyList(),
            )
        }
        assertTrue("slug" in ex.message!!.lowercase())
    }

    @Test
    fun `HelpTopic rejects an empty display name`() {
        assertFailsWith<IllegalArgumentException> {
            HelpTopic(slug = "x", displayName = "", summary = "y", commands = emptyList())
        }
    }
}
