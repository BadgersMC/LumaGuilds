package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.guildDialogIconLabel
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

internal fun referenceAction(
    label: Component,
    icon: String,
    action: () -> Unit,
): GuildDialogAction = GuildDialogAction(guildDialogIconLabel(label, icon), Component.empty(), action)

internal fun referenceItem(
    icon: String,
    lines: List<Component>,
    action: GuildDialogAction? = null,
): GuildDialogItem =
    GuildDialogItem(
        NexoItemProvider.getItemStackOrFallback(icon) { ItemStack.of(Material.PAPER) },
        Component.join(JoinConfiguration.newlines(), lines),
        lines,
        action,
    )

internal fun questProgressBar(
    current: Long,
    target: Long,
    cells: Int = 12,
): Component {
    val fraction = if (target <= 0) 0.0 else (current.coerceAtLeast(0).toDouble() / target).coerceIn(0.0, 1.0)
    val filled = (fraction * cells).toInt()
    return Component.text("▰".repeat(filled), NamedTextColor.GOLD)
        .append(Component.text("▱".repeat(cells - filled), NamedTextColor.DARK_GRAY))
}
