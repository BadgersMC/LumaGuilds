package net.lumalyte.lg.infrastructure.services

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.api.Subscribe
import github.scarsz.discordsrv.api.events.AccountLinkedEvent
import github.scarsz.discordsrv.api.events.AccountUnlinkedEvent
import github.scarsz.discordsrv.api.events.DiscordReadyEvent
import net.lumalyte.lg.application.services.DiscordAccountLinkSubscription
import net.lumalyte.lg.application.services.DiscordAccountReference
import net.lumalyte.lg.application.services.DiscordGuildRoleSyncSummary
import net.lumalyte.lg.application.services.GuildDiscordRoleService
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class DiscordSrvAccountLinkSubscription(
    private val service: GuildDiscordRoleService,
    private val executor: ExecutorService,
) : DiscordAccountLinkSubscription {
    private val logger = LoggerFactory.getLogger(DiscordSrvAccountLinkSubscription::class.java)
    private val subscribed = AtomicBoolean(false)

    override fun subscribe() {
        if (subscribed.compareAndSet(false, true)) {
            DiscordSRV.api.subscribe(this)
        }
    }

    override fun unsubscribe() {
        if (subscribed.compareAndSet(true, false)) {
            DiscordSRV.api.unsubscribe(this)
        }
    }

    /** Reconciles managed guild roles once DiscordSRV has a live JDA session. */
    @Subscribe
    fun onDiscordReady(@Suppress("UNUSED_PARAMETER") event: DiscordReadyEvent) {
        observe(
            "DiscordSRV ready startup reconciliation",
            CompletableFuture
                .supplyAsync(service::reconcileAll, executor)
                .thenCompose { it },
        )
    }

    @Subscribe
    fun onAccountLinked(event: AccountLinkedEvent) {
        observe(
            "Discord account link player=${event.player.uniqueId}",
            service.discordAccountLinked(event.player.uniqueId),
        )
    }

    @Subscribe
    fun onAccountUnlinked(event: AccountUnlinkedEvent) {
        observe(
            "Discord account unlink player=${event.player.uniqueId}",
            service.discordAccountUnlinked(
                event.player.uniqueId,
                DiscordAccountReference(event.discordId),
            ),
        )
    }

    private fun observe(
        operation: String,
        future: CompletableFuture<DiscordGuildRoleSyncSummary>,
    ) {
        future.whenComplete { result, error ->
            if (error != null) {
                logger.warn("Discord guild-role sync failed for $operation", error.cause ?: error)
            } else if (result.failures > 0) {
                logger.warn("Discord guild-role sync completed with ${result.failures} failure(s) for $operation")
            }
        }
    }
}
