package net.lumalyte.lg.interaction

import io.mockk.mockkClass
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

/**
 * Builds a class through its explicit primary constructor without Koin (REQ-NEXUS-DI-1..3).
 *
 * `Lazy<T>` parameters receive tracked lazies (overrides by `T`, otherwise relaxed mocks),
 * `() -> T` providers receive counting providers, `*Dependencies` bundles are built recursively the
 * same way, and other parameters receive overrides or relaxed mocks. Optional parameters keep their
 * defaults. Every created lazy/provider is recorded so tests can prove nothing resolved eagerly.
 */
internal class ExplicitWiring(private val overrides: Map<KClass<*>, Any?> = emptyMap()) {
    val lazies = mutableListOf<Pair<String, Lazy<*>>>()
    val providerCalls = mutableMapOf<String, Int>()

    fun <T : Any> build(type: KClass<T>, extra: Map<KClass<*>, Any?> = emptyMap()): T {
        val constructor = requireNotNull(type.primaryConstructor) { "${type.simpleName} has no primary constructor" }
        val args = mutableMapOf<KParameter, Any?>()
        constructor.parameters.forEach { parameter ->
            if (parameter.isOptional) return@forEach
            args[parameter] = argument(type, parameter, extra)
        }
        return constructor.callBy(args)
    }

    private fun argument(owner: KClass<*>, parameter: KParameter, extra: Map<KClass<*>, Any?>): Any? {
        val classifier = parameter.type.classifier as KClass<*>
        val label = "${owner.simpleName}.${parameter.name}"
        return when {
            classifier == Lazy::class -> {
                val inner = parameter.type.arguments.single().type!!.classifier as KClass<*>
                val nullable = parameter.type.arguments.single().type!!.isMarkedNullable
                lazy { value(inner, nullable, extra) }.also { lazies += label to it }
            }
            classifier == Function0::class -> {
                val inner = parameter.type.arguments.single().type!!.classifier as KClass<*>
                val nullable = parameter.type.arguments.single().type!!.isMarkedNullable
                val provider: () -> Any? = {
                    providerCalls[label] = (providerCalls[label] ?: 0) + 1
                    value(inner, nullable, extra)
                }
                provider
            }
            classifier.simpleName?.endsWith("Dependencies") == true -> build(classifier, extra)
            else -> value(classifier, parameter.type.isMarkedNullable, extra)
        }
    }

    private fun value(type: KClass<*>, nullable: Boolean, extra: Map<KClass<*>, Any?>): Any? = when {
        type in extra -> extra[type]
        type in overrides -> overrides[type]
        nullable -> null
        else -> mockkClass(type, relaxed = true)
    }

    fun initialized(): List<String> = lazies.filter { it.second.isInitialized() }.map { it.first }
}
