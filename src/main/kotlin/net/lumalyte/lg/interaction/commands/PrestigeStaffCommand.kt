package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.PrestigeStaffService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.PrestigeAdjustmentResult
import net.lumalyte.lg.domain.rewards.PrestigeResult
import net.lumalyte.lg.domain.values.PrestigeNumeral
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Logger

/**
 * REQ-184 to REQ-187: `/lumaguilds prestige info|set|grant|rerun`. Changes need a
 * trailing `confirm`; without it the command only previews. Every change is logged.
 */
internal class PrestigeStaffCommand(
    private val staff: () -> PrestigeStaffService,
    private val guilds: GuildService,
    private val lang: LangService,
    private val playerName: (UUID) -> String? = { org.bukkit.Bukkit.getOfflinePlayer(it).name },
    private val rewardName: (String) -> String = { it },
    private val audit: (String) -> Unit = { Logger.getLogger("LumaGuilds").info(it) },
    private val preview: (UUID, Int, String) -> Unit = { _, _, _ -> },
    private val ownGuildName: (UUID) -> String? = { null },
) {
    fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(lang.msg("admin.common.no_permission"))
            return
        }
        val sub = args.getOrNull(1)?.lowercase()
        if (sub == "preview") return preview(sender, args)
        val guild = args.getOrNull(2)?.let(::resolve)
        if (sub !in SUBCOMMANDS) return usage(sender)
        if (guild == null) {
            sender.sendMessage(lang.msg("admin.prestige.guild_not_found", "guild" to (args.getOrNull(2) ?: "")))
            return
        }
        val rest = args.drop(3)
        val confirmed = rest.lastOrNull().equals(CONFIRM, ignoreCase = true)
        val params = if (confirmed) rest.dropLast(1) else rest
        when (sub) {
            "info" -> info(sender, guild)
            "set" -> set(sender, guild, params.firstOrNull()?.toIntOrNull(), confirmed)
            "grant" -> grant(sender, guild, params.firstOrNull(), confirmed)
            "rerun" -> rerun(sender, guild, params.firstOrNull(), confirmed)
        }
    }

    /** REQ-253: `/lumaguilds prestige preview <tier> [guild]` plays the rank-up to the sender only. */
    private fun preview(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendMessage(lang.msg("admin.prestige.preview.players_only"))
        val tier = args.getOrNull(2)?.let { arg -> arg.toIntOrNull() ?: (ROMAN.indexOf(arg.uppercase()).takeIf { it > 0 }) }
        if (tier == null || tier !in 1..6) return sender.sendMessage(lang.msg("admin.prestige.preview.usage"))
        val name = args.getOrNull(3)?.let { resolve(it)?.name ?: it } ?: ownGuildName(player.uniqueId) ?: player.name
        preview(player.uniqueId, tier, name)
        sender.sendMessage(lang.msg("admin.prestige.preview.playing", "tier" to numeral(tier), "guild" to name))
    }

    private fun resolve(input: String): Guild? =
        runCatching { UUID.fromString(input) }.getOrNull()?.let(guilds::getGuild)
            ?: net.lumalyte.lg.utils.GuildResolver.resolveGuildByName(input, guilds)

    private fun usage(sender: CommandSender) = sender.sendMessage(lang.msg("admin.prestige.usage"))

    private fun info(sender: CommandSender, guild: Guild) {
        val info = staff().info(guild.id)
        if (info == null) {
            sender.sendMessage(lang.msg("admin.prestige.unavailable", "guild" to guild.name))
            return
        }
        sender.sendMessage(lang.msg("admin.prestige.info.header", "guild" to guild.name,
            "tier" to numeral(info.prestigeCount), "count" to info.prestigeCount, "max" to info.maxCount,
            "level" to (info.level?.toString() ?: "?")))
        sender.sendMessage(lang.msg("admin.prestige.info.kept",
            "rewards" to info.keptRewards.sorted().joinToString(", ") { rewardName(it) }.ifEmpty { "-" }))
        if (info.history.isEmpty()) sender.sendMessage(lang.msg("admin.prestige.info.no_history"))
        info.history.forEach {
            sender.sendMessage(lang.msg("admin.prestige.info.record", "tier" to numeral(it.tier),
                "date" to date(it.appliedAt), "reward" to rewardName(it.retainedRewardId), "player" to name(it.actorId)))
        }
        info.adjustments.forEach {
            sender.sendMessage(lang.msg("admin.prestige.info.adjustment", "date" to date(it.at),
                "old" to it.oldCount, "new" to it.newCount, "player" to name(it.actorId)))
        }
    }

    private fun set(sender: CommandSender, guild: Guild, count: Int?, confirmed: Boolean) {
        if (count == null) return usage(sender)
        val current = staff().info(guild.id)?.prestigeCount
        if (current != null && count > current) {
            sender.sendMessage(lang.msg("admin.prestige.set.raise", "guild" to guild.name))
            return
        }
        if (!confirmed) {
            val dropped = staff().rewardsDroppedBy(guild.id, count)
            sender.sendMessage(lang.msg("admin.prestige.set.preview", "guild" to guild.name,
                "old" to (current ?: "?"), "new" to count,
                "rewards" to dropped.joinToString(", ") { rewardName(it) }.ifEmpty { "-" },
                "confirm" to command(sender, "set ${guild.name} $count")))
            return
        }
        when (val result = staff().setCount(actor(sender), guild.id, count)) {
            is PrestigeAdjustmentResult.Applied -> {
                audit("[Prestige] ${sender.name} set ${guild.name} (${guild.id}) prestige ${result.oldCount} -> " +
                    "${result.newCount}, removed kept rewards ${result.removedRewards}")
                sender.sendMessage(lang.msg("admin.prestige.set.done", "guild" to guild.name,
                    "old" to result.oldCount, "new" to result.newCount,
                    "rewards" to result.removedRewards.joinToString(", ") { rewardName(it) }.ifEmpty { "-" }))
            }
            PrestigeAdjustmentResult.Unchanged -> sender.sendMessage(lang.msg("admin.prestige.set.unchanged", "guild" to guild.name))
            PrestigeAdjustmentResult.RaiseNeedsGrant -> sender.sendMessage(lang.msg("admin.prestige.set.raise", "guild" to guild.name))
            PrestigeAdjustmentResult.OutOfRange -> sender.sendMessage(lang.msg("admin.prestige.set.range",
                "max" to (staff().info(guild.id)?.maxCount ?: "?")))
            PrestigeAdjustmentResult.NotFound, PrestigeAdjustmentResult.Failed ->
                sender.sendMessage(lang.msg("admin.prestige.unavailable", "guild" to guild.name))
        }
    }

    private fun grant(sender: CommandSender, guild: Guild, rewardId: String?, confirmed: Boolean) {
        val choices = staff().grantChoices(guild.id)
        if (rewardId == null || choices.none { it.id == rewardId }) {
            if (choices.isEmpty()) {
                sender.sendMessage(lang.msg("admin.prestige.grant.none", "guild" to guild.name))
            } else {
                sender.sendMessage(lang.msg("admin.prestige.grant.choices", "guild" to guild.name,
                    "choices" to choices.joinToString(", ") { "${it.id} (${it.name})" }))
            }
            return
        }
        if (!confirmed) {
            sender.sendMessage(lang.msg("admin.prestige.grant.preview", "guild" to guild.name,
                "reward" to rewardName(rewardId), "confirm" to command(sender, "grant ${guild.name} $rewardId")))
            return
        }
        when (val result = staff().grant(actor(sender), guild.id, rewardId)) {
            is PrestigeResult.Applied -> {
                audit("[Prestige] ${sender.name} granted ${guild.name} (${guild.id}) Prestige ${result.prestigeCount} keeping $rewardId")
                sender.sendMessage(lang.msg("admin.prestige.grant.done", "guild" to guild.name,
                    "tier" to numeral(result.prestigeCount)))
            }
            is PrestigeResult.Rejected -> sender.sendMessage(lang.msg("admin.prestige.grant.rejected",
                "reason" to result.reason.name.lowercase().replace('_', ' ')))
            else -> sender.sendMessage(lang.msg("admin.prestige.unavailable", "guild" to guild.name))
        }
    }

    private fun rerun(sender: CommandSender, guild: Guild, tierArg: String?, confirmed: Boolean) {
        val tier = tierArg?.let { it.toIntOrNull() ?: ROMAN.indexOf(it.uppercase()).takeIf { i -> i > 0 } ?: return usage(sender) }
        val targets = staff().rerunTargets(guild.id, tier)
        if (targets.isEmpty()) {
            sender.sendMessage(lang.msg("admin.prestige.rerun.none", "guild" to guild.name))
            return
        }
        val tiers = targets.joinToString(", ") { numeral(it.tier) }
        if (!confirmed) {
            sender.sendMessage(lang.msg("admin.prestige.rerun.preview", "guild" to guild.name, "tiers" to tiers,
                "confirm" to command(sender, "rerun ${guild.name}${tier?.let { " $it" } ?: ""}")))
            return
        }
        staff().rerun(guild.id, tier)
        audit("[Prestige] ${sender.name} re-ran member rewards for ${guild.name} (${guild.id}) tiers $tiers")
        sender.sendMessage(lang.msg("admin.prestige.rerun.done", "guild" to guild.name, "tiers" to tiers))
    }

    private fun command(sender: CommandSender, rest: String) =
        "${if (sender is Player) "/" else ""}lumaguilds prestige $rest confirm"

    private fun actor(sender: CommandSender): UUID = (sender as? Player)?.uniqueId ?: CONSOLE

    private fun name(id: UUID): String = if (id == CONSOLE) "Console" else playerName(id) ?: id.toString()

    private fun numeral(tier: Int): String = if (tier <= 0) "0" else PrestigeNumeral.of(tier)

    private fun date(at: java.time.Instant): String = DATE.format(at.atZone(ZoneId.systemDefault()))

    companion object {
        const val PERMISSION = "lumaguilds.admin.prestige"
        val SUBCOMMANDS = listOf("info", "set", "grant", "rerun", "preview")
        val CONSOLE: UUID = UUID(0L, 0L)
        private const val CONFIRM = "confirm"
        private val ROMAN = listOf("", "I", "II", "III", "IV", "V", "VI")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
