package net.lumalyte.lg.infrastructure.services

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent

/** Local test switch: false restores explicit Open actions in both card consumers. */
internal const val CLICKABLE_CARDS = true

internal fun clickableGuildDialogCard(
    content: Component,
    action: GuildDialogAction?,
    event: (GuildDialogAction) -> ClickEvent<*>,
): Component {
    if (action == null) return content
    val click = event(action)

    fun decorate(component: Component): Component =
        component.clickEvent(click).hoverEvent(action.tooltip).children(component.children().map(::decorate))
    return decorate(content)
}
