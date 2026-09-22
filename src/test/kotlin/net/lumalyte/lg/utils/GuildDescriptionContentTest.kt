package net.lumalyte.lg.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.lumalyte.lg.domain.entities.Guild
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildDescriptionContentTest {
    @Test
    fun `description boundary is consistently 200 characters`() {
        val max = "a".repeat(GuildDescriptionContent.MAX_LENGTH)
        val tooLong = max + "a"

        assertNull(GuildDescriptionContent.validationFailure(max))
        assertEquals(
            GuildDescriptionContent.Failure.TooLong(201),
            GuildDescriptionContent.validationFailure(tooLong),
        )
        assertDoesNotThrow { guild(max) }
        assertThrows(IllegalArgumentException::class.java) { guild(tooLong) }
    }

    @Test
    fun `interactive MiniMessage event tags are rejected`() {
        listOf("click", "hover", "insertion").forEach { tag ->
            val description = "<$tag:test>unsafe</$tag>"
            assertEquals(
                GuildDescriptionContent.Failure.InteractiveTag(tag),
                GuildDescriptionContent.validationFailure(description),
            )
        }
    }

    @Test
    fun `discord invite urls are the only external urls made clickable`() {
        val discord = "https://discord.gg/Badgers-123"
        val ordinary = "https://example.com/not-clickable"
        val rendered = GuildDescriptionContent.render(
            "<gold>Recruiting:</gold> $discord $ordinary"
        )
        val clicks = descendants(rendered)
            .mapNotNull { it.clickEvent() }
            .toList()

        assertEquals(1, clicks.size)
        assertEquals(ClickEvent.Action.OPEN_URL, clicks.single().action())
        assertEquals(discord, clicks.single().value())
        assertEquals(
            "Recruiting: $discord $ordinary",
            GuildDescriptionContent.plainText("<gold>Recruiting:</gold> $discord $ordinary"),
        )
    }

    @Test
    fun `both supported discord invite hosts are detected without duplicates`() {
        val first = "https://discord.gg/abc_DEF-123"
        val second = "https://discord.com/invite/Second-2"
        val description = "$first $second $first"

        assertEquals(
            listOf(first, second),
            GuildDescriptionContent.discordInvites(description),
        )
    }

    @Test
    fun `renderer does not honor stored arbitrary click tags`() {
        val rendered = GuildDescriptionContent.render(
            "<click:open_url:'https://example.com'>unsafe</click>"
        )
        assertTrue(descendants(rendered).none { it.clickEvent() != null })
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        component.children().forEach { child ->
            yieldAll(descendants(child))
        }
    }

    private fun guild(description: String) = Guild(
        id = UUID.randomUUID(),
        name = "Description Test",
        description = description,
        createdAt = Instant.EPOCH,
    )
}
