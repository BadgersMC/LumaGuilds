package net.lumalyte.lg.infrastructure.hytale.adapters

import net.lumalyte.lg.domain.events.DomainEventBus
import net.lumalyte.lg.domain.events.SimpleDomainEventBus

/**
 * Factory to create the event bus for Hytale
 *
 * For now, we use the simple in-memory implementation.
 * In the future, we could bridge domain events to Hytale's EventRegistry
 * to allow other plugins to listen to LumaGuilds events.
 */
fun createHytaleEventBus(): DomainEventBus {
    return SimpleDomainEventBus()
}

// TODO (Phase 4): Bridge specific domain events to Hytale events
// Example:
// class GuildCreatedHytaleEvent(val guildId: UUID) : IEvent<Void>
//
// fun bridgeDomainEventsToHytale(
//     domainBus: DomainEventBus,
//     hytaleEventBus: IEventBus
// ) {
//     domainBus.subscribe(GuildCreatedEvent::class.java) { domainEvent ->
//         val hytaleEvent = GuildCreatedHytaleEvent(domainEvent.guildId)
//         hytaleEventBus.dispatch(GuildCreatedHytaleEvent::class.java, hytaleEvent)
//     }
// }
