package net.lumalyte.lg.di.nexus

import net.badgersmc.nexus.core.NexusContext
import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.infrastructure.i18n.LocaleCompatibilityMigrator
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import net.lumalyte.lg.infrastructure.services.TeleportationService
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * The initial, isolated Nexus foundation for the Koin-to-Nexus migration.
 *
 * This is intentionally NOT activated by LumaGuilds.onEnable yet. Startup, storage migrations,
 * listener registration and the ACF command tree must move together in the final cutover.
 * Running two live containers would risk duplicate lifecycle owners and schedulers.
 */
internal class GuildNexusFoundation private constructor(
    val context: NexusContext
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    companion object {
        /**
         * Called only after LumaGuilds config, storage and database migrations have succeeded.
         * Bean definitions are lazy; the existing locale migrator runs on first LangService use.
         */
        fun prepare(plugin: LumaGuilds, storage: Storage<*>, claimsEnabled: Boolean = true): GuildNexusFoundation {
            val context = NexusContext.create(
                classLoader = plugin.javaClass.classLoader,
                contextName = "LumaGuilds"
            )
            try {
                context.registerBean("plugin", LumaGuilds::class, plugin)
                context.registerBean("pluginContract", Plugin::class, plugin)
                context.registerBean("dataFolder", File::class, plugin.dataFolder)
                context.registerBean("bukkitConfig", FileConfiguration::class, plugin.config)
                context.registerBean("pluginLogger", Logger::class, plugin.logger)
                context.registerBean("storage", Storage::class, storage)

                context.registerBean("configService", ConfigService::class) {
                    // Reload-aware, like the original Koin coreModule config provider.
                    ConfigServiceBukkit { plugin.config }
                }
                context.registerBean("mainConfig", MainConfig::class) {
                    context.getBean<ConfigService>().loadConfig()
                }
                context.registerBean("langService", LangService::class) {
                    LocaleCompatibilityMigrator.migrate(plugin.dataFolder, plugin.logger)
                    LangService(LangHost.of(plugin), Locale("en_US"), LumaGuildsLang::class.java)
                }

                context.registerCoreServiceBindings(plugin)

                context.registerBean("asyncTaskService", AsyncTaskService::class) {
                    AsyncTaskService(
                        scope = context.getBean(),
                        virtualDispatcher = requireNotNull(context.dispatchers).virtualThread,
                        plugin = context.getBean<Plugin>()
                    )
                }
                context.registerBean("teleportationService", TeleportationService::class) {
                    TeleportationService(context.getBean<Plugin>(), context.getBean<LangService>())
                }
                if (claimsEnabled) context.registerClaimBindings()
                context.registerGuildCoreBindings(claimsEnabled)
                context.registerSocialBindings()
                context.registerProgressionBindings()
                context.registerProgressionListenerBindings()

                // Nexus owns and closes its CoroutineScope and virtual-thread dispatchers.
                // Do not create a second pluginScope / ExecutorService here.
                return GuildNexusFoundation(context)
            } catch (failure: Exception) {
                context.close()
                throw failure
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            context.close()
        }
    }
}
