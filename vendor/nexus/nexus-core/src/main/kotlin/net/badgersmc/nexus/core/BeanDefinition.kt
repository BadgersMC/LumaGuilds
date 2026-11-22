package net.badgersmc.nexus.core

import net.badgersmc.nexus.annotations.ScopeType
import kotlin.reflect.KClass

/**
 * Holds metadata about a registered bean.
 */
data class BeanDefinition(
    val name: String,
    val type: KClass<*>,
    val scope: ScopeType = ScopeType.SINGLETON,
    val factory: () -> Any
)
