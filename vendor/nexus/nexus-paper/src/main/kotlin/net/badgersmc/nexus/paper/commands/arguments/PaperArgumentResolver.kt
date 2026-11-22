package net.badgersmc.nexus.paper.commands.arguments

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import kotlin.reflect.KClass

/** Maps a Kotlin type to a Brigadier ArgumentType. */
interface PaperArgumentResolver<T : Any> {
    val type: KClass<T>

    /** Return the Brigadier ArgumentType for this type. */
    fun argumentType(): ArgumentType<T>

    /** Extract the resolved value from the Brigadier context by argument name. */
    fun extract(context: CommandContext<*>, name: String): T
}
