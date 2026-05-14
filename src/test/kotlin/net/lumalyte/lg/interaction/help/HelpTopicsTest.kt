package net.lumalyte.lg.interaction.help

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpTopicsTest {

    @Test
    fun `all 13 player topics are registered`() {
        val expected = setOf(
            "guilds", "ranks", "homes", "alliances", "war",
            "chat", "identity", "progression", "vault", "mode",
            "shop", "lfg", "bedrock",
        )
        assertEquals(expected, HelpTopics.all.map { it.slug }.toSet())
    }

    @Test
    fun `topics are returned in display order`() {
        val firstFour = HelpTopics.all.take(4).map { it.slug }
        assertEquals(listOf("guilds", "homes", "ranks", "chat"), firstFour)
    }

    @Test
    fun `bySlug returns the matching topic`() {
        val homes = HelpTopics.bySlug("homes")
        assertNotNull(homes)
        assertEquals("Homes", homes.displayName)
    }

    @Test
    fun `bySlug is case-insensitive`() {
        assertNotNull(HelpTopics.bySlug("HOMES"))
        assertNotNull(HelpTopics.bySlug("Homes"))
    }

    @Test
    fun `bySlug returns null for unknown slug`() {
        assertNull(HelpTopics.bySlug("nonexistent"))
    }

    @Test
    fun `every topic has at least one command`() {
        HelpTopics.all.forEach { topic ->
            assertTrue(topic.commands.isNotEmpty(), "Topic ${topic.slug} has no commands")
        }
    }

    @Test
    fun `every topic summary fits the 140-char wiki front-matter limit`() {
        HelpTopics.all.forEach { topic ->
            assertTrue(
                topic.summary.length <= 140,
                "Topic ${topic.slug} summary is ${topic.summary.length} chars",
            )
        }
    }
}
