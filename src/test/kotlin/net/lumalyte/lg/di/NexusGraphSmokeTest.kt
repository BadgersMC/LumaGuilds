package net.lumalyte.lg.di

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.di.nexus.GuildNexusFoundation
import net.lumalyte.lg.infrastructure.persistence.migrations.SQLiteMigrations
import net.lumalyte.lg.infrastructure.persistence.storage.SQLiteStorage
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockbukkit.mockbukkit.MockBukkit
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class NexusGraphSmokeTest {
    @TempDir lateinit var directory: Path

    @BeforeEach fun startServer() { MockBukkit.mock() }
    @AfterEach fun stopServer() { MockBukkit.unmock() }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `every production Nexus definition resolves against migrated storage`(claimsEnabled: Boolean) {
        if (claimsEnabled) MockBukkit.createMockPlugin("PlaceholderAPI")
        withMigratedGraphDatabase(directory, claimsEnabled) { plugin, storage ->
            GuildNexusFoundation.prepare(plugin, storage, claimsEnabled).use { foundation ->
                val context = foundation.context
                val names = context.getBeanNames()
                assertEquals(claimsEnabled, "createClaim" in names)
                assertEquals(claimsEnabled, "lumaGuildsExpansion" in names)
                for (name in names) {
                    try {
                        context.getBean(name)
                    } catch (failure: Throwable) {
                        throw AssertionError("Cannot construct Nexus bean '$name' with claimsEnabled=$claimsEnabled", failure)
                    }
                }
                assertSame<Any>(context.getBean<InviteBlockService>(), context.getBean<DurableInviteBlockService>())
            }
        }
    }
}

internal fun withMigratedGraphDatabase(
    directory: Path,
    claimsEnabled: Boolean,
    run: (LumaGuilds, SQLiteStorage) -> Unit,
) {
    val plugin = mockk<LumaGuilds>(relaxed = true)
    every { plugin.server } returns Bukkit.getServer()
    every { plugin.dataFolder } returns directory.toFile()
    every { plugin.config } returns YamlConfiguration().apply { set("claims_enabled", claimsEnabled) }
    every { plugin.logger } returns Logger.getLogger("LumaGuilds-GraphTest")
    every { plugin.getComponentLogger() } returns ComponentLogger.logger("LumaGuilds-GraphTest")
    val storage = SQLiteStorage(directory.toFile())
    try {
        storage.connection.connection.use { SQLiteMigrations(plugin, it, claimsEnabled).migrate() }
        run(plugin, storage)
    } finally {
        storage.connection.close()
    }
}
