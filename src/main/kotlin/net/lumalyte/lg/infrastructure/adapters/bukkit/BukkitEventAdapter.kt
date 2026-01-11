package net.lumalyte.lg.infrastructure.adapters.bukkit

import net.lumalyte.lg.domain.events.DomainEvent
import net.lumalyte.lg.domain.events.DomainEventBus
import net.lumalyte.lg.domain.events.DomainEventHandler
import org.bukkit.Bukkit
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Adapter that bridges domain events to Bukkit's event system.
 * This allows domain events to be published as Bukkit events,
 * enabling existing Bukkit plugins to listen to them.
 */
class BukkitDomainEventBus : DomainEventBus {

    private val handlers: MutableMap<Class<*>, MutableList<DomainEventHandler<*>>> = mutableMapOf()

    override fun <T : DomainEvent> publish(event: T) {
        // First, notify domain event handlers
        val eventHandlers = handlers[event::class.java] ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        eventHandlers.forEach { handler ->
            try {
                (handler as DomainEventHandler<T>).handle(event)
            } catch (e: Exception) {
                System.err.println("Error handling domain event ${event.eventName()}: ${e.message}")
                e.printStackTrace()
            }
        }

        // Then, publish to Bukkit's event system
        val bukkitEvent = BukkitDomainEventWrapper(event)
        Bukkit.getPluginManager().callEvent(bukkitEvent)

        // If the Bukkit event was cancelled, cancel the domain event
        if (bukkitEvent.isCancelled && event.isCancellable()) {
            event.cancel()
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
}

/**
 * Wrapper that makes a DomainEvent compatible with Bukkit's event system.
 * This allows domain events to be fired through Bukkit's event bus.
 */
class BukkitDomainEventWrapper<T : DomainEvent>(
    val domainEvent: T
) : Event(), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled || domainEvent.isCancelled

    override fun setCancelled(cancel: Boolean) {
        if (!domainEvent.isCancellable()) {
            throw UnsupportedOperationException(
                "Cannot cancel non-cancellable event ${domainEvent.eventName()}"
            )
        }
        this.cancelled = cancel
        if (cancel) {
            domainEvent.cancel()
        }
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}

/**
 * Adapter for legacy domain events that directly extend Bukkit Event.
 * This helps migrate existing Bukkit-based domain events to the new system.
 */
abstract class LegacyBukkitDomainEvent : Event(), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    /**
     * Converts this legacy event to a proper DomainEvent.
     * Subclasses should override this when migrating.
     */
    abstract fun toDomainEvent(): DomainEvent?

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}
