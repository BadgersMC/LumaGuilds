package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.lumalyte.lg.infrastructure.services.CLICKABLE_CARDS
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.utils.GuiTheme
import org.bukkit.inventory.ItemStack

/** Quests keep information in cards; navigation and the ranking chooser remain native buttons. */
internal fun guildQuestDialogPage(
    title: Component,
    header: ItemStack,
    quests: List<Pair<ItemStack, GuildDialogAction>>,
    controls: List<GuildDialogAction>,
    back: GuildDialogAction,
    theme: GuiTheme,
    clickableCards: Boolean = CLICKABLE_CARDS,
): GuildDialogPage {
    val headerLines = listOfNotNull(header.itemMeta.displayName()) + (header.lore() ?: emptyList())
    val items =
        listOf(GuildDialogItem(header, Component.join(JoinConfiguration.newlines(), headerLines))) +
            quests.map { (item, action) ->
                val lines = listOfNotNull(item.itemMeta.displayName()) + (item.lore() ?: emptyList())
                GuildDialogItem(
                    item,
                    Component.join(JoinConfiguration.newlines(), lines),
                    cardLines = lines,
                    onClick = if (clickableCards) action else null,
                )
            }
    return GuildDialogPage(
        title,
        items,
        (if (clickableCards) emptyList() else quests.map { it.second }) + controls,
        back,
        theme = theme,
    )
}
