package net.lumalyte.lg.domain.events

import java.time.Instant
import java.util.*

/**
 * Base class for all domain events.
 * Domain events represent things that have happened in the domain that
 * other parts of the system might be interested in.
 *
 * This is a platform-agnostic event system that doesn't depend on
 * Bukkit, Hytale, or any other game engine's event system.
 */
abstract class DomainEvent {
    /**
     * Unique identifier for this event occurrence
     */
    val eventId: UUID = UUID.randomUUID()

    /**
     * When this event occurred
     */
    val occurredAt: Instant = Instant.now()

    /**
     * Whether this event has been cancelled.
     * Cancellable events can be cancelled by event handlers.
     */
    var isCancelled: Boolean = false
        private set

    /**
     * Cancels this event if it is cancellable.
     * @throws UnsupportedOperationException if the event is not cancellable
     */
    fun cancel() {
        if (!isCancellable()) {
            throw UnsupportedOperationException("Event ${this::class.simpleName} is not cancellable")
        }
        isCancelled = true
    }

    /**
     * Returns whether this event can be cancelled.
     * Override this in subclasses to make events cancellable.
     */
    open fun isCancellable(): Boolean = false

    /**
     * Returns the name of this event type
     */
    fun eventName(): String = this::class.simpleName ?: "UnknownEvent"
}

/**
 * Interface for handling domain events.
 * Implement this to listen for and react to domain events.
 */
fun interface DomainEventHandler<T : DomainEvent> {
    /**
     * Handle a domain event
     * @param event The event to handle
     */
    fun handle(event: T)
}

/**
 * Domain event dispatcher/bus.
 * This provides a platform-agnostic event system that can be adapted
 * to work with Bukkit events, Hytale events, or any other event system.
 */
interface DomainEventBus {
    /**
     * Publishes a domain event to all registered handlers
     * @param event The event to publish
     */
    fun <T : DomainEvent> publish(event: T)

    /**
     * Registers a handler for a specific event type
     * @param eventType The class of events to handle
     * @param handler The handler function
     */
    fun <T : DomainEvent> subscribe(eventType: Class<T>, handler: DomainEventHandler<T>)

    /**
     * Registers a handler for a specific event type (Kotlin convenience)
     */
    fun <T : DomainEvent> subscribe(eventType: Class<T>, handler: (T) -> Unit) {
        subscribe(eventType, DomainEventHandler { handler(it) })
    }
}

/**
 * Simple in-memory implementation of DomainEventBus
 */
class SimpleDomainEventBus : DomainEventBus {
    private val handlers: MutableMap<Class<*>, MutableList<DomainEventHandler<*>>> = mutableMapOf()

    override fun <T : DomainEvent> publish(event: T) {
        val eventHandlers = handlers[event::class.java] ?: return

        @Suppress("UNCHECKED_CAST")
        eventHandlers.forEach { handler ->
            try {
                (handler as DomainEventHandler<T>).handle(event)
            } catch (e: Exception) {
                // Log error but don't stop other handlers
                System.err.println("Error handling event ${event.eventName()}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun <T : DomainEvent> subscribe(eventType: Class<T>, handler: DomainEventHandler<T>) {
        handlers.computeIfAbsent(eventType) { mutableListOf() }.add(handler)
    }

    /**
     * Clears all registered handlers
     */
    fun clear() {
        handlers.clear()
    }

    /**
     * Returns the number of handlers registered for a specific event type
     */
    fun handlerCount(eventType: Class<*>): Int {
        return handlers[eventType]?.size ?: 0
    }
}

/**
 * Kotlin extension for easier event subscription
 */
inline fun <reified T : DomainEvent> DomainEventBus.subscribe(noinline handler: (T) -> Unit) {
    subscribe(T::class.java, handler)
}
