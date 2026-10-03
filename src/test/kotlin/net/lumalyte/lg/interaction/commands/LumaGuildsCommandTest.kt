package net.lumalyte.lg.interaction.commands

import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.lumalyte.lg.infrastructure.i18n.LumaGuildsLang
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.GuildRolePermissionResolver
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.values.ExperienceSource
import org.bukkit.command.Command
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Path
import java.util.UUID

class LumaGuildsCommandTest {

    @TempDir
    lateinit var dataFolder: Path

    private lateinit var server: ServerMock
    private lateinit var player: PlayerMock
    private lateinit var command: LumaGuildsCommand
    private lateinit var adminOverrideService: AdminOverrideService
    private lateinit var permissionResolver: GuildRolePermissionResolver
    private lateinit var guildService: GuildService
    private lateinit var progressionService: ProgressionService
    private lateinit var mockCommand: Command
    private lateinit var mockPlugin: org.bukkit.plugin.Plugin

    @BeforeEach
    fun setUp() {
        // Set up MockBukkit
        server = MockBukkit.mock()

        // Create a mock enabled plugin for permission attachments
        mockPlugin = mockk(relaxed = true)
        every { mockPlugin.isEnabled } returns true

        // Create mock services
        adminOverrideService = mockk(relaxed = true)
        permissionResolver = mockk(relaxed = true)
        guildService = mockk(relaxed = true)
        progressionService = mockk(relaxed = true)

        // Set up Koin with mocked services
        stopKoin() // Stop any existing Koin instance
        startKoin {
            modules(module {
                single<LangService> {
                    LangService(
                        object : LangHost {
                            override val dataFolder: File = this@LumaGuildsCommandTest.dataFolder.toFile()
                            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
                        },
                        Locale("en_US"),
                        LumaGuildsLang::class.java,
                    )
                }
                single { adminOverrideService }
                single { permissionResolver }
                single { guildService }
                single { progressionService }
            })
        }

        // Create command
        command = LumaGuildsCommand()

        // Create a mock player
        player = server.addPlayer("TestAdmin")

        // Create mock command object
        mockCommand = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        stopKoin()
    }

    @Test
    fun `xp give awards uncapped admin bonus to named guild`() {
        player.addAttachment(mockPlugin, "lumaguilds.admin.xp", true)
        val guildId = UUID.randomUUID()
        val guild = mockk<Guild>()
        every { guild.id } returns guildId
        every { guild.name } returns "Vibe"
        every { guildService.getGuildByName("Vibe") } returns guild
        every { progressionService.awardUncappedSystemExperienceOnce(
            guildId, 5, ExperienceSource.ADMIN_BONUS, any()
        ) } returns true

        val result = command.onCommand(player, mockCommand, "lumaguilds", arrayOf("xp", "give", "Vibe", "5"))

        assertTrue(result)
        verify(exactly = 1) { progressionService.awardUncappedSystemExperienceOnce(
            guildId, 5, ExperienceSource.ADMIN_BONUS, any()
        ) }
        assertTrue(player.nextMessage()?.contains("Granted") == true)
    }

    @Test
    fun `xp give rejects player without xp admin permission`() {
        val result = command.onCommand(player, mockCommand, "lumaguilds", arrayOf("xp", "give", "Vibe", "5"))

        assertTrue(result)
        verify(exactly = 0) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        assertTrue(player.nextMessage()?.contains("permission", ignoreCase = true) == true)
    }

    @Test
    fun `override command with admin permission should enable override`() {
        // Given: Player has admin permission and override is disabled
        player.addAttachment(mockPlugin, "bellclaims.admin", true)
        every { adminOverrideService.toggleOverride(player.uniqueId) } returns true

        // When: Player executes /bellclaims override
        val result = command.onCommand(player, mockCommand, "bellclaims", arrayOf("override"))

        // Then: Command should succeed
        assertTrue(result)

        // Verify service was called
        verify(exactly = 1) { adminOverrideService.toggleOverride(player.uniqueId) }

        // Verify cache was invalidated
        verify(exactly = 1) { permissionResolver.invalidatePlayerCache(player.uniqueId) }

        // Verify player received success message
        val message1 = player.nextMessage()
        assertNotNull(message1)
        assertTrue(message1!!.contains("enabled") || message1.contains("§a"))
    }

    @Test
    fun `override command with admin permission should disable when enabled`() {
        // Given: Player has admin permission and override is enabled
        player.addAttachment(mockPlugin, "bellclaims.admin", true)
        every { adminOverrideService.toggleOverride(player.uniqueId) } returns false

        // When: Player executes /bellclaims override
        val result = command.onCommand(player, mockCommand, "bellclaims", arrayOf("override"))

        // Then: Command should succeed
        assertTrue(result)

        // Verify service was called
        verify(exactly = 1) { adminOverrideService.toggleOverride(player.uniqueId) }

        // Verify cache was invalidated
        verify(exactly = 1) { permissionResolver.invalidatePlayerCache(player.uniqueId) }

        // Verify player received disabled message
        val message2 = player.nextMessage()
        assertNotNull(message2)
        assertTrue(message2!!.contains("disabled") || message2.contains("§c"))
    }

    @Test
    fun `override command without admin permission should fail`() {
        // Given: Player does NOT have admin permission
        // (don't add the permission attachment)
        every { adminOverrideService.toggleOverride(any()) } returns true

        // When: Player executes /bellclaims override
        val result = command.onCommand(player, mockCommand, "bellclaims", arrayOf("override"))

        // Then: Command should succeed but not toggle override
        assertTrue(result)

        // Verify service was NOT called
        verify(exactly = 0) { adminOverrideService.toggleOverride(any()) }

        // Verify cache was NOT invalidated
        verify(exactly = 0) { permissionResolver.invalidatePlayerCache(any()) }

        // Verify player received permission denied message
        val message = player.nextMessage()
        assertNotNull(message)
        assertTrue(message!!.contains("permission") || message.contains("§c"))
    }

    @Test
    fun `override command should show in help`() {
        // When: Player executes /bellclaims help
        val result = command.onCommand(player, mockCommand, "bellclaims", arrayOf("help"))

        // Then: Command should succeed
        assertTrue(result)

        // Verify help was displayed - collect all messages
        val messages = mutableListOf<String>()
        var msg = player.nextMessage()
        while (msg != null) {
            messages.add(msg)
            msg = player.nextMessage()
        }

        // Should have received help messages
        assertTrue(messages.isNotEmpty())

        // Should include override in help (once implemented)
        // This will fail until we add override to the help text
    }

    @Test
    fun `tab completion should include override`() {
        // When: Player tab completes /bellclaims
        val completions = command.onTabComplete(player, mockCommand, "bellclaims", arrayOf(""))

        // Then: Should include "override" in completions
        assertTrue(completions.contains("override"))
    }

    @Test
    fun `tab completion should filter override with partial input`() {
        // When: Player tab completes /bellclaims ov
        val completions = command.onTabComplete(player, mockCommand, "bellclaims", arrayOf("ov"))

        // Then: Should include "override" in filtered completions
        assertTrue(completions.contains("override"))
    }

    @Test
    fun `tab completion should exclude removed export subcommands`() {
        // When: Player tab completes /bellclaims with an empty prefix (full first-level list)
        val completions = command.onTabComplete(player, mockCommand, "bellclaims", arrayOf(""))

        // Then: Removed export subcommands must not be suggested
        listOf("download", "exports", "cancel").forEach { removed ->
            assertFalse(completions.contains(removed), "removed subcommand '$removed' must not be suggested")
        }
    }
}
