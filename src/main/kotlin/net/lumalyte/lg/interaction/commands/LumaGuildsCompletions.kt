package net.lumalyte.lg.interaction.commands

/**
 * REQ-189: tab completion for `/lumaguilds`. Every argument offers its known
 * values, or one `<hint>` naming what to type; typing filters the list, so a hint
 * disappears as soon as the sender starts typing. [allowed] answers permission
 * checks, with `op` standing for operator-only subcommands.
 */
internal class LumaGuildsCompletions(
    private val allowed: (String) -> Boolean,
    private val guildNames: () -> List<String>,
    private val prestige: (String) -> PrestigeFacts?,
) {
    /** What the prestige subcommands can offer for one guild. */
    data class PrestigeFacts(val count: Int, val rewardChoices: List<String>, val tiers: List<Int>)

    fun complete(args: List<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        val typed = args.last()
        val options = if (args.size == 1) subcommands() else argument(args[0].lowercase(), args)
        return options.filter { it.startsWith(typed, ignoreCase = true) }
    }

    private fun subcommands(): List<String> = SUBCOMMANDS.filter { (_, permission) -> permission == null || allowed(permission) ||
        (permission == ADMIN_OR_OP && (allowed(OP) || allowed(ADMIN)))
    }.map { it.first }

    private fun usable(sub: String): Boolean = sub in subcommands()

    private fun argument(sub: String, args: List<String>): List<String> {
        if (!usable(sub)) return emptyList()
        val index = args.size - 1
        return when (sub) {
            "xp" -> when (index) {
                1 -> listOf("give")
                2 -> guildNames()
                3 -> listOf("<amount>")
                else -> emptyList()
            }
            "disband" -> when (index) {
                1 -> guildNames()
                2 -> listOf("confirm")
                else -> emptyList()
            }
            "migrate" -> if (index == 1) listOf("confirm") else emptyList()
            "warcutover" -> if (index == 1) listOf("CONFIRM") else emptyList()
            "chapter" -> when (index) {
                1 -> listOf("status", "backup", "postpone", "retry", "force")
                2 -> listOf("<chapter-id>")
                3 -> if (args[1].equals("postpone", true)) listOf("<ISO-8601-time>") else emptyList()
                else -> emptyList()
            }
            "owner" -> when (index) {
                1 -> listOf("recover")
                2 -> listOf("<guild-uuid>")
                3 -> listOf("<member-uuid>")
                4 -> listOf("confirm")
                5 -> listOf("<token>")
                else -> emptyList()
            }
            "spawnbanner" -> when (index) {
                1 -> listOf("1", "2", "3", "refresh", "list")
                2 -> if (args[1].toIntOrNull() != null) SPAWN_BANNER_CATEGORIES else emptyList()
                else -> emptyList()
            }
            "prestige" -> prestigeArgument(args, index)
            "sounds" -> if (index == 1) listOf("all", "stop") + net.lumalyte.lg.domain.values.GuildSoundCatalog.ids else emptyList()
            "hall" -> when (index) {
                1 -> HallStaffCommand.SUBCOMMANDS
                2 -> if (args[1].equals("pedestal", true)) HallStaffCommand.PEDESTAL_ACTIONS else emptyList()
                3 -> if (args[1].equals("pedestal", true)) listOf("<number>") else emptyList()
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    private fun prestigeArgument(args: List<String>, index: Int): List<String> {
        val action = args.getOrNull(1)?.lowercase()
        if (index == 1) return PrestigeStaffCommand.SUBCOMMANDS
        if (action !in PrestigeStaffCommand.SUBCOMMANDS) return emptyList()
        if (action == "preview") return when (index) {
            2 -> (1..6).map(Int::toString)
            3 -> guildNames()
            else -> emptyList()
        }
        if (index == 2) return guildNames()
        if (action == "info") return emptyList()
        val facts = prestige(args[2])
        return when (action) {
            "set" -> when (index) {
                3 -> facts?.let { f -> (0 until f.count).map(Int::toString) }?.ifEmpty { null } ?: listOf("<count>")
                4 -> listOf("confirm")
                else -> emptyList()
            }
            "grant" -> when (index) {
                3 -> facts?.rewardChoices?.ifEmpty { null } ?: listOf("<reward>")
                4 -> listOf("confirm")
                else -> emptyList()
            }
            "rerun" -> when (index) {
                3 -> (facts?.tiers?.distinct()?.sorted()?.map(Int::toString) ?: emptyList()) + "confirm"
                4 -> if (args[3].equals("confirm", true)) emptyList() else listOf("confirm")
                else -> emptyList()
            }
            else -> emptyList()
        }
    }

    companion object {
        const val OP = "op"
        private const val ADMIN = "bellclaims.admin"
        private const val ADMIN_OR_OP = "op|bellclaims.admin"

        private val SUBCOMMANDS: List<Pair<String, String?>> = listOf(
            "reload" to OP,
            "progressionreload" to OP,
            "xp" to "lumaguilds.admin.xp",
            "disband" to OP,
            "migrate" to OP,
            "chapter" to ADMIN_OR_OP,
            "override" to ADMIN,
            "owner" to GuildOwnerRecoveryCommand.PERMISSION,
            "spawnbanner" to ADMIN_OR_OP,
            "warcutover" to ADMIN_OR_OP,
            "prestige" to PrestigeStaffCommand.PERMISSION,
            "sounds" to GuildSoundsCommand.PERMISSION,
            "hall" to HallStaffCommand.PERMISSION,
            "help" to null,
        )

        private val SPAWN_BANNER_CATEGORIES: List<String> =
            net.lumalyte.lg.domain.entities.SpawnBannerCategory.entries.map { it.commandName }
    }
}
