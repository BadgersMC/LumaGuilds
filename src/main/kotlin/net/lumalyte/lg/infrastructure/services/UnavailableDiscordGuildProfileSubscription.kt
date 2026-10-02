package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.DiscordGuildProfileSubscription

class UnavailableDiscordGuildProfileSubscription : DiscordGuildProfileSubscription {
    override fun subscribe() = Unit
    override fun unsubscribe() = Unit
}
