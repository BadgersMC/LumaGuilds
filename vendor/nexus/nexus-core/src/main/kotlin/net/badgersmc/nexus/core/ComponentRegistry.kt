package net.badgersmc.nexus.core

import net.badgersmc.nexus.annotations.ScopeType
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registry for all component definitions in the Nexus container.
 * Thread-safe storage for bean definitions and singleton instances.
 */
class ComponentRegistry {

    private val definitions = ConcurrentHashMap<String, BeanDefinition>()
    private val singletons = ConcurrentHashMap<String, Any>()
    private val typeIndex = ConcurrentHashMap<KClass<*>, MutableList<String>>()

    /**
     * Register a bean definition with the container.
     * Logs a warning if a bean with the same name already exists (last-write wins).
     * This allows external beans registered before scanning to be overridden by
     * scanned components if they share the same name — but warns so it's not silent.
     */
    fun register(definition: BeanDefinition) {
        val existing = definitions.putIfAbsent(definition.name, definition)
        if (existing != null) {
            if (existing.type == definition.type) {
                // Same type re-registered (e.g. config reload) — safe overwrite
                definitions[definition.name] = definition
            } else {
                throw IllegalArgumentException(
                    "Duplicate bean name '${definition.name}': " +
                    "${existing.type.simpleName} would be overwritten by ${definition.type.simpleName}. " +
                    "Use @Qualifier to disambiguate."
                )
            }
        }

        // Index by concrete type for lookup
        typeIndex.computeIfAbsent(definition.type) { mutableListOf() }.add(definition.name)

        // Also index by all interfaces the class implements,
        // so beans can be resolved by their interface type (e.g. PlaytimeSessionRepository)
        for (iface in definition.type.java.interfaces) {
            val ifaceKClass = iface.kotlin
            typeIndex.computeIfAbsent(ifaceKClass) { mutableListOf() }.add(definition.name)
        }

        // Also index by superclass (if not Any/Object)
        val superclass = definition.type.java.superclass
        if (superclass != null && superclass != Any::class.java && superclass != Object::class.java) {
            typeIndex.computeIfAbsent(superclass.kotlin) { mutableListOf() }.add(definition.name)
        }
    }

    /**
     * Get a bean definition by name.
     */
    fun getDefinition(name: String): BeanDefinition? {
        return definitions[name]
    }

    /**
     * Get all bean definitions of a specific type.
     */
    fun getDefinitionsByType(type: KClass<*>): List<BeanDefinition> {
        val names = typeIndex[type] ?: return emptyList()
        return names.mapNotNull { definitions[it] }
    }

    /**
     * Check if a bean with the given name exists.
     */
    fun contains(name: String): Boolean {
        return definitions.containsKey(name)
    }

    /**
     * Store a singleton instance.
     */
    fun putSingleton(name: String, instance: Any) {
        singletons[name] = instance
    }

    /**
     * Retrieve a singleton instance.
     */
    fun getSingleton(name: String): Any? {
        return singletons[name]
    }

    /**
     * Get all registered bean names.
     */
    fun getAllBeanNames(): Set<String> {
        return definitions.keys
    }

    /**
     * Get all singleton instances for lifecycle management.
     */
    fun getAllSingletons(): Collection<Any> {
        return singletons.values
    }

    /**
     * Clear all registrations (for testing or shutdown).
     */
    fun clear() {
        definitions.clear()
        singletons.clear()
        typeIndex.clear()
    }
}
