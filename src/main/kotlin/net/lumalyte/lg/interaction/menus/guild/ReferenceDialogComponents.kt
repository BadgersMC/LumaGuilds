package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.rewards.PrestigePerk
import net.lumalyte.lg.domain.rewards.PrestigePreview
import net.lumalyte.lg.domain.rewards.PrestigeSummary
import net.lumalyte.lg.domain.rewards.PrestigeSummaryLine
import net.lumalyte.lg.domain.values.PrestigeNumeral
import net.lumalyte.lg.infrastructure.i18n.bedrock
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
        .append(Component.text("▱".repeat(cells - filled), NamedTextColor.DARK_GRAY))
}

internal fun referenceRewardEffect(lang: net.badgersmc.nexus.i18n.LangService,
    value: net.lumalyte.lg.domain.rewards.RewardEffect): Component = when (value) {
    is net.lumalyte.lg.domain.rewards.RewardEffect.BankCapacity -> lang.gui("chronicle.bank_effect", "amount" to value.amount)
    is net.lumalyte.lg.domain.rewards.RewardEffect.HomeCapacity -> lang.gui("chronicle.home_effect", "amount" to value.amount)
    is net.lumalyte.lg.domain.rewards.RewardEffect.HomeCooldown -> lang.gui("chronicle.cooldown_effect", "percent" to (value.multiplier * 100).toInt(),
        "seconds" to net.lumalyte.lg.domain.values.HomeTravelTimes.countdownSeconds(value.multiplier))
    is net.lumalyte.lg.domain.rewards.RewardEffect.WithdrawalFee -> lang.gui("chronicle.fee_effect", "percent" to (value.multiplier * 100).toInt())
    net.lumalyte.lg.domain.rewards.RewardEffect.AllyHomes -> lang.gui("chronicle.ally_effect")
}

/** The same reward effect text as [referenceRewardEffect], as plain text for Bedrock forms. */
internal fun bedrockRewardEffect(lang: net.badgersmc.nexus.i18n.LangService,
    value: net.lumalyte.lg.domain.rewards.RewardEffect): String = when (value) {
    is net.lumalyte.lg.domain.rewards.RewardEffect.BankCapacity -> lang.bedrock("chronicle.bank_effect", "amount" to value.amount)
    is net.lumalyte.lg.domain.rewards.RewardEffect.HomeCapacity -> lang.bedrock("chronicle.home_effect", "amount" to value.amount)
    is net.lumalyte.lg.domain.rewards.RewardEffect.HomeCooldown -> lang.bedrock("chronicle.cooldown_effect", "percent" to (value.multiplier * 100).toInt(),
        "seconds" to net.lumalyte.lg.domain.values.HomeTravelTimes.countdownSeconds(value.multiplier))
    is net.lumalyte.lg.domain.rewards.RewardEffect.WithdrawalFee -> lang.bedrock("chronicle.fee_effect", "percent" to (value.multiplier * 100).toInt())
    net.lumalyte.lg.domain.rewards.RewardEffect.AllyHomes -> lang.bedrock("chronicle.ally_effect")
}

/** REQ-179 to REQ-181: confirmation lines for the perks a prestige tier starts. */
/** REQ-249: the prestige confirmation summary as Java lore lines. */
internal fun prestigeSummaryLines(
    lang: net.badgersmc.nexus.i18n.LangService,
    preview: PrestigePreview,
    reward: String,
    effect: net.lumalyte.lg.domain.rewards.RewardEffect?,
): List<net.kyori.adventure.text.Component> =
    PrestigeSummary.lines(preview, effect != null).map { line ->
        when (line) {
            is PrestigeSummaryLine.Heading -> lang.gui("menu.guild_progression.prestige.confirmation.summary.name", "tier" to PrestigeNumeral.of(line.tier))
            PrestigeSummaryLine.FinalPrestige -> lang.gui("menu.guild_progression.prestige.confirmation.summary.final")
            PrestigeSummaryLine.Gap -> net.kyori.adventure.text.Component.empty()
            PrestigeSummaryLine.EarnHeading -> lang.gui("menu.guild_progression.prestige.confirmation.summary.earn_heading")
            is PrestigeSummaryLine.Badge -> lang.gui("menu.guild_progression.prestige.confirmation.summary.badge", "tier" to PrestigeNumeral.of(line.tier))
            PrestigeSummaryLine.RetainedReward -> lang.gui("menu.guild_progression.prestige.confirmation.summary.reward", "reward" to reward)
            PrestigeSummaryLine.RetainedEffect -> effect?.let { referenceRewardEffect(lang, it) } ?: net.kyori.adventure.text.Component.empty()
            is PrestigeSummaryLine.HomeSlot -> lang.gui("menu.guild_progression.prestige.confirmation.summary.home_slot", "before" to line.before, "after" to line.after)
            is PrestigeSummaryLine.Perk -> when (line.perk) {
                PrestigePerk.SECOND_ALLY_HOME -> lang.gui("menu.guild_progression.prestige.confirmation.summary.perk_second_ally_home")
                PrestigePerk.EXTRA_STALL -> lang.gui("menu.guild_progression.prestige.confirmation.summary.perk_extra_stall")
                PrestigePerk.SECOND_VAULT -> lang.gui("menu.guild_progression.prestige.confirmation.summary.perk_second_vault")
            }
            is PrestigeSummaryLine.MemberLimit -> lang.gui("menu.guild_progression.prestige.confirmation.summary.member_limit", "limit" to line.limit)
            is PrestigeSummaryLine.DiscordRole -> lang.gui("menu.guild_progression.prestige.confirmation.summary.discord_role", "level" to line.minimumLevel)
            is PrestigeSummaryLine.CarriedXp -> lang.gui("menu.guild_progression.prestige.confirmation.summary.carry", "xp" to summaryNumber(line.experience.toLong()), "level" to line.level)
            is PrestigeSummaryLine.MemberAdvancement -> lang.gui("menu.guild_progression.prestige.confirmation.summary.member_advancement", "hours" to line.hours, "tier" to PrestigeNumeral.of(line.tier))
            is PrestigeSummaryLine.FirstOnServer -> lang.gui("menu.guild_progression.prestige.confirmation.summary.first", "tier" to PrestigeNumeral.of(line.tier))
            is PrestigeSummaryLine.Announced -> when {
                line.discord && line.fanfare -> lang.gui("menu.guild_progression.prestige.confirmation.summary.announced_discord_fanfare")
                line.discord -> lang.gui("menu.guild_progression.prestige.confirmation.summary.announced_discord")
                line.fanfare -> lang.gui("menu.guild_progression.prestige.confirmation.summary.announced_fanfare")
                else -> lang.gui("menu.guild_progression.prestige.confirmation.summary.announced")
            }
            PrestigeSummaryLine.CostHeading -> lang.gui("menu.guild_progression.prestige.confirmation.summary.cost_heading")
            is PrestigeSummaryLine.Fee -> lang.gui("menu.guild_progression.prestige.confirmation.summary.fee", "fee" to summaryNumber(line.fee))
            is PrestigeSummaryLine.LevelReset -> if (line.xpKept) lang.gui("menu.guild_progression.prestige.confirmation.summary.reset_carry") else lang.gui("menu.guild_progression.prestige.confirmation.summary.reset")
            PrestigeSummaryLine.RewardsReset -> lang.gui("menu.guild_progression.prestige.confirmation.summary.rewards_reset")
            PrestigeSummaryLine.KeepHeading -> lang.gui("menu.guild_progression.prestige.confirmation.summary.keep_heading")
            PrestigeSummaryLine.Kept -> lang.gui("menu.guild_progression.prestige.confirmation.summary.permanent")
            PrestigeSummaryLine.Overflow -> lang.gui("menu.guild_progression.prestige.confirmation.summary.overflow")
        }
    }

/** REQ-249: the same summary as plain Bedrock form lines. */
internal fun bedrockPrestigeSummaryLines(
    lang: net.badgersmc.nexus.i18n.LangService,
    preview: PrestigePreview,
    reward: String,
    effect: net.lumalyte.lg.domain.rewards.RewardEffect?,
): List<String> =
    PrestigeSummary.lines(preview, effect != null).map { line ->
        when (line) {
            is PrestigeSummaryLine.Heading -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.name", "tier" to PrestigeNumeral.of(line.tier))
            PrestigeSummaryLine.FinalPrestige -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.final")
            PrestigeSummaryLine.Gap -> ""
            PrestigeSummaryLine.EarnHeading -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.earn_heading")
            is PrestigeSummaryLine.Badge -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.badge", "tier" to PrestigeNumeral.of(line.tier))
            PrestigeSummaryLine.RetainedReward -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.reward", "reward" to reward)
            PrestigeSummaryLine.RetainedEffect -> effect?.let { bedrockRewardEffect(lang, it) } ?: ""
            is PrestigeSummaryLine.HomeSlot -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.home_slot", "before" to line.before, "after" to line.after)
            is PrestigeSummaryLine.Perk -> when (line.perk) {
                PrestigePerk.SECOND_ALLY_HOME -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.perk_second_ally_home")
                PrestigePerk.EXTRA_STALL -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.perk_extra_stall")
                PrestigePerk.SECOND_VAULT -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.perk_second_vault")
            }
            is PrestigeSummaryLine.MemberLimit -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.member_limit", "limit" to line.limit)
            is PrestigeSummaryLine.DiscordRole -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.discord_role", "level" to line.minimumLevel)
            is PrestigeSummaryLine.CarriedXp -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.carry", "xp" to summaryNumber(line.experience.toLong()), "level" to line.level)
            is PrestigeSummaryLine.MemberAdvancement -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.member_advancement", "hours" to line.hours, "tier" to PrestigeNumeral.of(line.tier))
            is PrestigeSummaryLine.FirstOnServer -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.first", "tier" to PrestigeNumeral.of(line.tier))
            is PrestigeSummaryLine.Announced -> when {
                line.discord && line.fanfare -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.announced_discord_fanfare")
                line.discord -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.announced_discord")
                line.fanfare -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.announced_fanfare")
                else -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.announced")
            }
            PrestigeSummaryLine.CostHeading -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.cost_heading")
            is PrestigeSummaryLine.Fee -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.fee", "fee" to summaryNumber(line.fee))
            is PrestigeSummaryLine.LevelReset -> if (line.xpKept) lang.bedrock("menu.guild_progression.prestige.confirmation.summary.reset_carry") else lang.bedrock("menu.guild_progression.prestige.confirmation.summary.reset")
            PrestigeSummaryLine.RewardsReset -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.rewards_reset")
            PrestigeSummaryLine.KeepHeading -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.keep_heading")
            PrestigeSummaryLine.Kept -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.permanent")
            PrestigeSummaryLine.Overflow -> lang.bedrock("menu.guild_progression.prestige.confirmation.summary.overflow")
        }
    }

private fun summaryNumber(value: Long): String = "%,d".format(java.util.Locale.ROOT, value)
