package net.lumalyte.lg.interaction.commands.nexus

import net.badgersmc.nexus.paper.commands.PaperCommandDefinition
import net.badgersmc.nexus.paper.commands.PaperCommandScanner

/** Nexus validates every argument type during scanning, before the caller filters disabled roots. */
internal fun scanGuildCommandDefinitions(
    classLoader: ClassLoader = NexusClaimToolCommand::class.java.classLoader,
): List<PaperCommandDefinition> {
    registerClaimCommandArguments()
    return PaperCommandScanner().scanCommands(NexusClaimToolCommand::class.java.packageName, classLoader)
}
