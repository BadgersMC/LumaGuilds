package net.lumalyte.lg.integrations.axkoth

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.lang.reflect.Proxy

/** Registers the optional AxKoth team hook without linking AxKoth classes at compile time. */
internal object AxKothTeamHookRegistrar {
    private const val API_CLASS = "com.artillexstudios.axkoth.api.AxKothAPI"
    private const val TEAM_HOOK_CLASS = "com.artillexstudios.axkoth.hooks.teams.TeamHook"
    private const val PROVIDER_NAME = "LumaGuilds"

    fun register(plugin: Plugin, axKothPlugin: Plugin, hook: LumaGuildsHook) {
        val classLoader = axKothPlugin.javaClass.classLoader
        val teamHookClass = Class.forName(TEAM_HOOK_CLASS, true, classLoader)
        require(teamHookClass.isInterface) { "AxKoth TeamHook is no longer an interface" }

        val proxy =
            Proxy.newProxyInstance(classLoader, arrayOf(teamHookClass)) { proxyInstance, method, args ->
                when {
                    method.declaringClass == Any::class.java ->
                        when (method.name) {
                            "toString" -> "LumaGuildsAxKothTeamHook"
                            "hashCode" -> System.identityHashCode(proxyInstance)
                            "equals" -> proxyInstance === args?.firstOrNull()
                            else -> throw UnsupportedOperationException("Unsupported Object method: ${method.name}")
                        }
                    method.name == "setup" -> hook.setup().let { null }
                    method.name == "getName" -> PROVIDER_NAME
                    method.name == "getTeamOfPlayer" -> hook.getTeamOfPlayer(args?.single() as Player)
                    method.name == "getTeamByName" -> hook.getTeamByName(args?.single() as String)
                    method.name == "getTeamMembers" -> hook.getTeamMembers(args?.single() as String)
                    else -> throw UnsupportedOperationException("Unsupported AxKoth TeamHook method: ${method.name}")
                }
            }

        val apiClass = Class.forName(API_CLASS, true, classLoader)
        val registerMethod = apiClass.getMethod("registerTeamHook", Plugin::class.java, teamHookClass)
        registerMethod.invoke(null, plugin, proxy)
    }
}
