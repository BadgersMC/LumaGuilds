package net.lumalyte.lg.di.nexus

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.AsyncTaskService
import net.lumalyte.lg.infrastructure.services.ConfigServiceBukkit
import net.lumalyte.lg.application.services.FormValidationService
import net.lumalyte.lg.interaction.commands.GuildCommandHandlers
import net.lumalyte.lg.interaction.commands.GuildCommandCompletions
import net.lumalyte.lg.interaction.commands.PartyChatHandler
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
        every { plugin.metadata } returns null

        val foundation = GuildNexusFoundation.prepare(plugin, storage)
        val context = foundation.context
        val scope = context.getBean<CoroutineScope>()
        try {
            assertSame(context.getBean<GuildCommandHandlers>(), context.getBean<GuildCommandHandlers>())
            assertSame(context.getBean<PartyChatHandler>(), context.getBean<PartyChatHandler>())
            assertSame(context.getBean<GuildCommandCompletions>(), context.getBean<GuildCommandCompletions>())
            assertSame(plugin, context.getBean<LumaGuilds>())
            assertSame(plugin, context.getBean<JavaPlugin>())
            assertSame(plugin, context.getBean<Plugin>())
            assertSame(storage, context.getBean<Storage<*>>())
            assertSame(config, context.getBean<FileConfiguration>())
            assertSame(tempDir, context.getBean<File>())
            assertTrue(context.getBean<ConfigService>() is ConfigServiceBukkit)
            assertSame(context.getBean<AsyncTaskService>(), context.getBean<AsyncTaskService>())
            assertSame(context.getBean<FormValidationService>(), context.getBean<FormValidationService>())
            assertTrue("menuFactory" in context.getBeanNames())
            assertTrue("formCacheService" in context.getBeanNames())
            assertTrue("bedrockLocalizationService" in context.getBeanNames())
            // Registration is lazy until the rest of the progression graph is migrated.
            assertTrue("progressionEventListener" in context.getBeanNames())
            assertTrue("guildRecruitmentService" in context.getBeanNames())
            assertTrue("chatService" in context.getBeanNames())
            assertTrue("guildRewardService" in context.getBeanNames())
            assertTrue("progressionService" in context.getBeanNames())
            assertTrue("guildGoldService" in context.getBeanNames())
            assertTrue("bankService" in context.getBeanNames())
            assertTrue("guildVaultService" in context.getBeanNames())
            assertTrue("vaultInventoryManager" in context.getBeanNames())
            assertTrue("warService" in context.getBeanNames())
            assertTrue("questService" in context.getBeanNames())
            assertTrue("leaderboardService" in context.getBeanNames())
            assertTrue("questProgressListener" in context.getBeanNames())
            assertTrue("guildListService" in context.getBeanNames())
            assertTrue("webApiServer" in context.getBeanNames())
            assertTrue("discordAccountLinkSubscription" in context.getBeanNames())
            assertTrue("discordGuildProfileSubscription" in context.getBeanNames())
            assertTrue("lumaGuildsExpansion" in context.getBeanNames())
            assertTrue("blockProvenanceOperationQueue" in context.getBeanNames())
            assertFalse("playerMetadataService" in context.getBeanNames())
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
