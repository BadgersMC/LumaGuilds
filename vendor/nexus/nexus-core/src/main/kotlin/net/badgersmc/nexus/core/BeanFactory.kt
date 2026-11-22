package net.badgersmc.nexus.core

import kotlinx.coroutines.runBlocking
import net.badgersmc.nexus.annotations.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.primaryConstructor

/**
 * Factory for creating and managing bean instances.
 * Handles dependency resolution, injection, and lifecycle callbacks.
 */
class BeanFactory(
    private val registry: ComponentRegistry
) {

    /**
     * Get or create a bean by name.
     */
    fun getBean(name: String): Any {
        val definition = registry.getDefinition(name)
            ?: throw NexusException("No bean found with name: $name")

        return when (definition.scope) {
            ScopeType.SINGLETON -> getSingletonBean(name, definition)
            ScopeType.PROTOTYPE -> createPrototypeBean(definition)
        }
    }

    /**
     * Get or create a bean by type.
     */
    fun <T : Any> getBean(type: KClass<T>): T {
        val definitions = registry.getDefinitionsByType(type)

        when (definitions.size) {
            0 -> throw NexusException("No bean found of type: ${type.simpleName}")
            1 -> {
                @Suppress("UNCHECKED_CAST")
                return getBean(definitions[0].name) as T
            }
            else -> throw NexusException(
                "Multiple beans found of type ${type.simpleName}: ${definitions.map { it.name }}"
            )
        }
    }

    /**
     * Get a singleton bean, creating it if necessary.
     */
    private fun getSingletonBean(name: String, definition: BeanDefinition): Any {
        return registry.getSingleton(name) ?: synchronized(this) {
            registry.getSingleton(name) ?: run {
                val instance = createBean(definition)
                registry.putSingleton(name, instance)
                invokePostConstruct(instance)
                instance
            }
        }
    }

    /**
     * Create a new prototype bean instance.
     */
    private fun createPrototypeBean(definition: BeanDefinition): Any {
        val instance = createBean(definition)
        invokePostConstruct(instance)
        return instance
    }

    /**
     * Create a bean instance using its factory or constructor.
     */
    private fun createBean(definition: BeanDefinition): Any {
        return try {
            definition.factory()
        } catch (e: Exception) {
            throw NexusException("Failed to create bean: ${definition.name}", e)
        }
    }

    /**
     * Invoke @PostConstruct methods on a bean.
     * Supports both regular and suspend functions.
     */
    private fun invokePostConstruct(bean: Any) {
        bean::class.functions
            .filter { it.findAnnotation<PostConstruct>() != null }
            .forEach { method ->
                try {
                    invokeLifecycleMethod(bean, method)
                } catch (e: Exception) {
                    throw NexusException("Failed to invoke @PostConstruct on ${bean::class.simpleName}", e)
                }
            }
    }

    /**
     * Invoke @PreDestroy methods on a bean.
     * Supports both regular and suspend functions.
     */
    fun invokePreDestroy(bean: Any) {
        bean::class.functions
            .filter { it.findAnnotation<PreDestroy>() != null }
            .forEach { method ->
                try {
                    invokeLifecycleMethod(bean, method)
                } catch (e: Exception) {
                    // Log but don't throw during shutdown
                    System.err.println("Failed to invoke @PreDestroy on ${bean::class.simpleName}: ${e.message}")
                }
            }
    }

    /**
     * Invoke a lifecycle method, handling both regular and suspend functions.
     * Suspend functions are bridged using runBlocking.
     */
    private fun invokeLifecycleMethod(bean: Any, method: KFunction<*>) {
        if (method.isSuspend) {
            runBlocking {
                method.callSuspend(bean)
            }
        } else {
            method.call(bean)
        }
    }

    /**
     * Create a bean factory function that resolves constructor dependencies.
     */
    fun <T : Any> createFactory(klass: KClass<T>): () -> T {
        return {
            val constructor = klass.primaryConstructor
                ?: throw NexusException("No primary constructor found for ${klass.simpleName}")

            val args = constructor.parameters.map { param ->
                val qualifier = param.findAnnotation<Qualifier>()
                val paramType = param.type.classifier as? KClass<*>
                    ?: throw NexusException("Cannot resolve type for parameter ${param.name}")

                if (qualifier != null) {
                    getBean(qualifier.value)
                } else {
                    getBean(paramType)
                }
            }.toTypedArray()

            constructor.call(*args)
        }
    }
}

/**
 * Exception thrown when Nexus encounters an error.
 */
class NexusException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
