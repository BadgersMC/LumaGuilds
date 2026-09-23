package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.DiscordAccountLinkSubscription

class UnavailableDiscordAccountLinkSubscription : DiscordAccountLinkSubscription {
    override fun subscribe() = Unit
    override fun unsubscribe() = Unit
}
