package net.lumalyte.lg.di.runtime

import net.badgersmc.nexus.commands.annotations.Command
import net.lumalyte.lg.interaction.commands.nexus.NexusGuildCommands
import net.lumalyte.lg.interaction.commands.nexus.NexusPartyChatCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimAddFlagCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimDescriptionCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimInfoCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimListCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimMenuCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimOverrideCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimPartitionsCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimRemoveCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimRemoveFlagCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimRenameCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimToolCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimTrustListCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusFullscreenAnnounceCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusQuickAllyChatCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusQuickAnnounceCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusQuickGuildChatCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusQuickModChatCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusTrustAllCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusTrustCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusUntrustAllCommand
import net.lumalyte.lg.interaction.commands.nexus.NexusUntrustCommand
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/** A top-level command label and its aliases. */
internal data class CommandRoot(
    val name: String,
    val aliases: Set<String> = emptySet(),
    val claimsOnly: Boolean = false,
) {
    val labels: Set<String> get() = aliases + name
}

/** Central parity evidence for one root; only the integrator promotes a root to [Proven]. */
internal sealed interface RootParity {
    data object Proven : RootParity
    data class Unproven(val reason: String) : RootParity
}

internal class CommandParityReport(
    val missing: List<CommandRoot>,
    val missingAliases: Map<String, Set<String>>,
    val unproven: Map<String, String>,
    val unexpected: List<CommandRoot>,
) {
    val proven: Boolean get() = missing.isEmpty() && missingAliases.isEmpty() && unproven.isEmpty() && unexpected.isEmpty()

    fun describe(): String = buildList {
        missing.forEach { add("missing Nexus root /${it.name} (${it.labels.sorted().joinToString("|")})") }
        missingAliases.forEach { (root, aliases) -> add("/$root lacks aliases ${aliases.sorted().joinToString(", ")}") }
        unproven.forEach { (root, reason) -> add("/$root parity unproven: $reason") }
        unexpected.forEach { add("unexpected Nexus root /${it.name} has no legacy owner") }
    }.joinToString(separator = System.lineSeparator())
}

/** Thrown before storage or Nexus activation when a legacy command root lacks proven parity. */
internal class CommandCutoverBlocked(message: String) : IllegalStateException(message)

internal fun evaluateCommandParity(
    required: List<CommandRoot>,
    provided: List<CommandRoot>,
    status: Map<String, RootParity>,
): CommandParityReport {
    val byName = provided.groupBy { it.name }.mapValues { (_, roots) -> roots.flatMap { it.aliases }.toSet() }
    val missing = required.filter { it.name !in byName }
    val missingAliases = required.filter { it.name in byName }
        .associate { it.name to (it.aliases - byName.getValue(it.name)) }
        .filterValues { it.isNotEmpty() }
    val unproven = required.filter { it.name in byName }
        .mapNotNull { root ->
            when (val parity = status[root.name]) {
                RootParity.Proven -> null
                is RootParity.Unproven -> root.name to parity.reason
                null -> root.name to "no parity evidence recorded"
            }
        }
        .toMap()
    val requiredNames = required.map { it.name }.toSet()
    val unexpected = byName.keys.filterNot { it in requiredNames }.map { CommandRoot(it, byName.getValue(it)) }
    return CommandParityReport(missing, missingAliases, unproven, unexpected)
}

/** The explicit Nexus command classes owned by command-family ports. Integrated centrally. */
internal object NexusCommandCatalog {
    private val shared: List<KClass<*>> = listOf(
        NexusGuildCommands::class,
        NexusPartyChatCommand::class,
        NexusQuickGuildChatCommand::class,
        NexusQuickAllyChatCommand::class,
        NexusQuickModChatCommand::class,
        NexusQuickAnnounceCommand::class,
        NexusFullscreenAnnounceCommand::class,
    )

    private val claims: List<KClass<*>> = listOf(
        NexusClaimToolCommand::class,
        NexusClaimMenuCommand::class,
        NexusClaimInfoCommand::class,
        NexusClaimRenameCommand::class,
        NexusClaimListCommand::class,
        NexusClaimPartitionsCommand::class,
        NexusTrustCommand::class,
        NexusUntrustCommand::class,
        NexusClaimRemoveCommand::class,
        NexusClaimOverrideCommand::class,
        NexusClaimTrustListCommand::class,
        NexusTrustAllCommand::class,
        NexusUntrustAllCommand::class,
        NexusClaimDescriptionCommand::class,
        NexusClaimAddFlagCommand::class,
        NexusClaimRemoveFlagCommand::class,
    )

    fun classes(claimsEnabled: Boolean): List<KClass<*>> = if (claimsEnabled) shared + claims else shared

    fun providedRoots(claimsEnabled: Boolean): List<CommandRoot> =
        classes(claimsEnabled).map { rootOf(it) }
            .groupBy { it.name }
            .map { (name, annotations) ->
                CommandRoot(name, annotations.flatMap { it.aliases.toList() }.toSet(), claimsOnly = classes(false).none { rootOf(it).name == name })
            }

    internal fun rootOf(command: KClass<*>): Command =
        requireNotNull(command.findAnnotation<Command>()) { "${command.simpleName} is not a Nexus @Command" }
}

/** Legacy ACF roots that Nexus must replace, and per-root parity evidence. */
internal object GuildCommandParity {
    val legacyRoots: List<CommandRoot> = listOf(
        CommandRoot("guild", setOf("g")),
        CommandRoot("pc", setOf("pchat", "partychat")),
        CommandRoot("gc"),
        CommandRoot("gac"),
        CommandRoot("gmc"),
        CommandRoot("ga"),
        CommandRoot("gfa"),
        CommandRoot("claim", claimsOnly = true),
        CommandRoot("claimlist", claimsOnly = true),
        CommandRoot("claimmenu", claimsOnly = true),
        CommandRoot("claimoverride", claimsOnly = true),
    )

    /**
     * Per-root evidence. A root becomes [RootParity.Proven] only when the central integrator has
     * route, permission, completion and live Paper 26.2 dispatch evidence for the pinned Nexus.
     */
    val status: Map<String, RootParity> = mapOf(
        "guild" to RootParity.Unproven(
            "guild routes are present; live Paper dispatch has not been verified",
        ),
        "pc" to RootParity.Unproven("party chat requires the root-argument fix and live Paper dispatch evidence"),
        "gc" to RootParity.Unproven(PINNED_ROOT_ARGUMENT_DEFECT),
        "gac" to RootParity.Unproven(PINNED_ROOT_ARGUMENT_DEFECT),
        "gmc" to RootParity.Unproven(PINNED_ROOT_ARGUMENT_DEFECT),
        "ga" to RootParity.Unproven(PINNED_ROOT_ARGUMENT_DEFECT),
        "gfa" to RootParity.Unproven(PINNED_ROOT_ARGUMENT_DEFECT),
        "claimlist" to RootParity.Unproven(PINNED_ROOT_ARGUMENT_DEFECT),
        "claim" to RootParity.Unproven(
            "completions, flag-enum parser feedback and Paper 26.2 dispatch/permission parity are unverified",
        ),
        "claimmenu" to RootParity.Unproven("no Paper 26.2 dispatch/permission evidence"),
        "claimoverride" to RootParity.Unproven("no Paper 26.2 dispatch/permission evidence"),
    )

    fun requiredRoots(claimsEnabled: Boolean): List<CommandRoot> = legacyRoots.filter { claimsEnabled || !it.claimsOnly }

    fun evaluate(claimsEnabled: Boolean): CommandParityReport =
        evaluateCommandParity(requiredRoots(claimsEnabled), NexusCommandCatalog.providedRoots(claimsEnabled), status)

    /** Fails closed: no ACF fallback and no partial command tree may go live. */
    fun requireProven(claimsEnabled: Boolean) {
        val report = evaluate(claimsEnabled)
        if (!report.proven) {
            throw CommandCutoverBlocked("Nexus command parity is not proven; refusing to activate LumaGuilds: " + report.describe())
        }
    }

    private const val PINNED_ROOT_ARGUMENT_DEFECT =
        "root/default @Arg routes do not parse at pinned Nexus 5ae8b642 (fix eb55c12a is unpublished)"
}
