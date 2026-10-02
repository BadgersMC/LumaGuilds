package net.lumalyte.lg.application.services

/**
 * Lifecycle seam for the optional DiscordSRV public guild browser.
 */
interface DiscordGuildProfileSubscription {
    fun subscribe()
    fun unsubscribe()
}
