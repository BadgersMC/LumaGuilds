package net.lumalyte.lg.di.nexus

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Logger
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GuildNexusFoundationTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `foundation registers production contracts and delegates coroutine ownership to Nexus`() {
        val plugin = mockk<LumaGuilds>()
        val config = YamlConfiguration()
        val storage = mockk<Storage<*>>()
        every { plugin.dataFolder } returns tempDir
        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getLogger("GuildNexusFoundationTest")

        val foundation = GuildNexusFoundation.prepare(plugin, storage)
        val context = foundation.context
        val scope = context.getBean<CoroutineScope>()
        try {
            assertSame(plugin, context.getBean<LumaGuilds>())
            assertSame(plugin, context.getBean<JavaPlugin>())
            assertSame(plugin, context.getBean<Plugin>())
            assertSame(storage, context.getBean<Storage<*>>())
            assertSame(config, context.getBean<FileConfiguration>())
            assertSame(tempDir, context.getBean<File>())
            assertTrue(context.getBean<ConfigService>() is ConfigServiceBukkit)
            assertSame(context.scope, scope)
            assertTrue(scope.isActive)
        } finally {
            foundation.close()
        }
        assertFalse(scope.isActive)
        // Shutdown is intentionally idempotent for plugin disable/failure cleanup.
        foundation.close()
    }
}
