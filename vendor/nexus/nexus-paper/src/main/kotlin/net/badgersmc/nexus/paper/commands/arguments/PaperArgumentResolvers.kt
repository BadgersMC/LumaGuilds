package net.badgersmc.nexus.paper.commands.arguments

import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.entity.Player
import kotlin.reflect.KClass

object PaperArgumentResolvers {
    private val resolvers = mutableMapOf<KClass<*>, PaperArgumentResolver<*>>()

    init {
        // String — captures a single word (stops at spaces). Use a custom resolver with
        // StringArgumentType.greedyString() if you need multi-word arguments.
        register(object : PaperArgumentResolver<String> {
            override val type = String::class
            override fun argumentType(): ArgumentType<String> = StringArgumentType.word()
            override fun extract(ctx: CommandContext<*>, name: String): String =
                StringArgumentType.getString(ctx, name)
        })
        register(object : PaperArgumentResolver<Int> {
            override val type = Int::class
            override fun argumentType(): ArgumentType<Int> = IntegerArgumentType.integer()
            override fun extract(ctx: CommandContext<*>, name: String): Int =
                IntegerArgumentType.getInteger(ctx, name)
        })
        register(object : PaperArgumentResolver<Long> {
            override val type = Long::class
            override fun argumentType(): ArgumentType<Long> = LongArgumentType.longArg()
            override fun extract(ctx: CommandContext<*>, name: String): Long =
                LongArgumentType.getLong(ctx, name)
        })
        register(object : PaperArgumentResolver<Double> {
            override val type = Double::class
            override fun argumentType(): ArgumentType<Double> = DoubleArgumentType.doubleArg()
            override fun extract(ctx: CommandContext<*>, name: String): Double =
                DoubleArgumentType.getDouble(ctx, name)
        })
        register(object : PaperArgumentResolver<Boolean> {
            override val type = Boolean::class
            override fun argumentType(): ArgumentType<Boolean> = BoolArgumentType.bool()
            override fun extract(ctx: CommandContext<*>, name: String): Boolean =
                BoolArgumentType.getBool(ctx, name)
        })
        // Player — uses Paper's built-in player selector.
        // ArgumentTypes.player() returns ArgumentType<PlayerSelectorArgumentResolver>.
        // We cast to ArgumentType<Player> to satisfy the interface, but extraction actually goes
        // through PlayerSelectorArgumentResolver.resolve() — the ArgumentType generic doesn't match
        // the extracted type, which is unavoidable with Paper's selector API.
        register(object : PaperArgumentResolver<Player> {
            override val type = Player::class
            @Suppress("UNCHECKED_CAST")
            override fun argumentType(): ArgumentType<Player> =
                ArgumentTypes.player() as ArgumentType<Player>
            override fun extract(ctx: CommandContext<*>, name: String): Player {
                val source = ctx.source as CommandSourceStack
                val resolver = ctx.getArgument(name, PlayerSelectorArgumentResolver::class.java)
                return resolver.resolve(source).firstOrNull()
                    ?: throw IllegalArgumentException("No player found for argument '$name'")
            }
        })
    }

    fun <T : Any> register(resolver: PaperArgumentResolver<T>) {
        resolvers[resolver.type] = resolver
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): PaperArgumentResolver<T>? =
        resolvers[type] as? PaperArgumentResolver<T>

    fun hasResolver(type: KClass<*>): Boolean = resolvers.containsKey(type)
}
