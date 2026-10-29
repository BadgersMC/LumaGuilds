package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class GuildDialogCardInteractionTest {
    @Test
    fun everyPanelAndTextComponentSharesOneEventAndTooltip() {
        val content =
            Component.text("\uE500").font(Key.key("lumaguilds:dialogs"))
                .append(Component.text(" "))
                .append(Component.text("Everlights").append(Component.text("100 coins; apply in game")))
        val event = ClickEvent.runCommand("/unused-test-event")
        val action = GuildDialogAction(Component.text("Open"), Component.text("100 coins; apply in game")) {}
        var registrations = 0
        val decorated =
            clickableGuildDialogCard(content, action) {
                registrations++
                event
            }
        val nodes = flatten(decorated)
        assertEquals(1, registrations)
        assertTrue(nodes.all { it.clickEvent() == event })
        assertTrue(nodes.all { it.hoverEvent()?.value() == action.tooltip })
        assertEquals(content.font(), decorated.font())
    }

    @Test
    fun informationOnlyItemRegistersNothingAndRetainsItsComponent() {
        val content = Component.text("Information")
        val actual = clickableGuildDialogCard(content, null) { error("No event for information") }
        assertSame(content, actual)
    }

    private fun flatten(value: Component): List<Component> = listOf(value) + value.children().flatMap(::flatten)
}
