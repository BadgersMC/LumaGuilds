package net.lumalyte.lg.di.runtime

import net.badgersmc.nexus.paper.commands.PaperCommandRegistry
import net.lumalyte.lg.interaction.commands.nexus.scanGuildCommandDefinitions
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.di.nexus.nexusBedrockCacheStatsCommand
import net.lumalyte.lg.di.nexus.nexusLumaGuildsCommand
import net.lumalyte.lg.interaction.commands.admin.BankCreditCommand
import net.lumalyte.lg.interaction.commands.admin.RemoveVaultCommand
import net.lumalyte.lg.interaction.commands.admin.VaultRollbackCommand
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter

/**
 * The single command-registration boundary for the Nexus runtime.
 *
 * Command classes are owned by the command-family ports; this boundary only decides *which*
 * classes go live and refuses activation until [GuildCommandParity] proves every legacy root.
 * There is no ACF fallback and no partial tree.
 */
internal interface GuildCommandRegistration {
    /** Static pre-activation gate; throws [CommandCutoverBlocked] when parity is unproven. */
    fun verify(claimsEnabled: Boolean)

    /** Registers the complete command tree once, after the graph is active. */
    fun register(plugin: LumaGuilds, runtime: ActiveGuildRuntime, claimsEnabled: Boolean)
}

internal class NexusGuildCommandRegistration(
    private val parity: (Boolean) -> Unit = GuildCommandParity::requireProven,
) : GuildCommandRegistration {
    override fun verify(claimsEnabled: Boolean) = parity(claimsEnabled)

    override fun register(plugin: LumaGuilds, runtime: ActiveGuildRuntime, claimsEnabled: Boolean) {
        val catalog = NexusCommandCatalog.classes(claimsEnabled).toSet()
        val definitions = scanGuildCommandDefinitions(plugin.javaClass.classLoader)
            .filter { it.commandClass in catalog }
        val found = definitions.map { it.commandClass }.toSet()
        check(found == catalog) { "Nexus command catalog classes not found by the scanner: ${catalog - found}" }

        val graph = runtime.graph
        PaperCommandRegistry(
            plugin,
            graph.scope,
            runtime.foundation.context.getBeanFactory(),
            runtimeCommandSuggestions(
                runtime.foundation.context.getBean(),
                runtime.foundation.context.getBean(),
                claimsEnabled,
                Bukkit.getScheduler().getMainThreadExecutor(plugin),
            ),
        ).registerAll(definitions)
        plugin.logColored("✓ Nexus command tree registered (${definitions.size} classes)")

        registerAdministrativeCommands(plugin, runtime)
    }

    /** Plain Bukkit executors declared in plugin.yml; never ACF, so they keep their own handlers. */
    private fun registerAdministrativeCommands(plugin: LumaGuilds, runtime: ActiveGuildRuntime) {
        val context = runtime.foundation.context
        val graph = runtime.graph
        val lang = lazy { graph.lang }
        bind(plugin, "lumaguilds", context.nexusLumaGuildsCommand())
        plugin.getCommand("bedrockcachestats")?.setExecutor(context.nexusBedrockCacheStatsCommand())
        bind(plugin, "vaultrollback", VaultRollbackCommand(graph.guildService, graph.vaultBackupService, lang = lang))
        bind(plugin, "bankcredit", BankCreditCommand(graph.bankService, lang = lang))
        bind(plugin, "removevault", RemoveVaultCommand(graph.guildService, graph.guildVaultService, lang = lang))
        plugin.logColored("✓ Admin commands registered (/lumaguilds, /bellclaims, /vaultrollback, /bankcredit, /removevault)")
        plugin.logColored("✓ Bedrock cache stats command registered (/bedrockcachestats)")
    }

    private fun <T> bind(plugin: LumaGuilds, name: String, command: T) where T : CommandExecutor, T : TabCompleter {
        plugin.getCommand(name)?.let {
            it.setExecutor(command)
            it.tabCompleter = command
        }
    }
}
