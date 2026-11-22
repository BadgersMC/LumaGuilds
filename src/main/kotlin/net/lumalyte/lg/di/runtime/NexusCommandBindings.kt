package net.lumalyte.lg.di.runtime

import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.interaction.commands.nexus.NexusClaimAccess

/**
 * Shared collaborators that Nexus command classes receive by constructor type when the Paper
 * registry builds them. Lazy singletons; claim collaborators exist only when claims are enabled.
 */
internal fun NexusContext.registerCommandBindings(claimsEnabled: Boolean) {
    if (!claimsEnabled) return
    registerBean("nexusClaimAccess", NexusClaimAccess::class) {
        NexusClaimAccess(getBean(), getBean(), getBean(), getBean(), getBean())
    }
}
