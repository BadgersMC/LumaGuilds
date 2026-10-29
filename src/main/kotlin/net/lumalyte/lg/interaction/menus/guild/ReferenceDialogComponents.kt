package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.guildDialogIconLabel
import net.lumalyte.lg.infrastructure.i18n.gui
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
    right: List<Component>? = null,
    section: Component? = null,
): GuildDialogItem =
    GuildDialogItem(
        NexoItemProvider.getItemStackOrFallback(icon) { ItemStack.of(Material.PAPER) },
        Component.join(JoinConfiguration.newlines(), lines),
        lines,
        action,
        right,
        section,
    )

internal fun questProgressBar(
    current: Long,
    target: Long,
    cells: Int = 12,
): Component {
    val fraction = if (target <= 0) 0.0 else (current.coerceAtLeast(0).toDouble() / target).coerceIn(0.0, 1.0)
    val filled = (fraction * cells).toInt()
    return Component.text("▰".repeat(filled), NamedTextColor.GOLD)
        .append(Component.text("▱".repeat(cells - filled), NamedTextColor.GRAY))
}

internal fun referenceRewardEffect(lang: net.badgersmc.nexus.i18n.LangService,
    value: net.lumalyte.lg.domain.rewards.RewardEffect): Component = when (value) {
    is net.lumalyte.lg.domain.rewards.RewardEffect.BankCapacity -> lang.gui("chronicle.bank_effect", "amount" to value.amount)
    is net.lumalyte.lg.domain.rewards.RewardEffect.HomeCapacity -> lang.gui("chronicle.home_effect", "amount" to value.amount)
    is net.lumalyte.lg.domain.rewards.RewardEffect.HomeCooldown -> lang.gui("chronicle.cooldown_effect", "percent" to (value.multiplier * 100).toInt())
    is net.lumalyte.lg.domain.rewards.RewardEffect.WithdrawalFee -> lang.gui("chronicle.fee_effect", "percent" to (value.multiplier * 100).toInt())
    net.lumalyte.lg.domain.rewards.RewardEffect.AllyHomes -> lang.gui("chronicle.ally_effect")
}
