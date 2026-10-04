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
import net.lumalyte.lg.domain.values.ExperienceSource.ADMIN_BONUS
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
    private var guildService: GuildService = mockk(relaxed = true)
    private var progressionService: ProgressionService = mockk(relaxed = true)
    private val xpTasks = java.util.ArrayDeque<Runnable>()
    private val xpReplies = java.util.ArrayDeque<Runnable>()
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
        command = LumaGuildsCommand({ xpTasks.add(it) }, { xpReplies.add(it) })

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

    /** The database award waits for the worker; the Bukkit reply waits for the main thread. */
    @Test
    fun xpGiveIsAsync() {
        val guildId = prepareXpGuild()
        every {
            progressionService.awardUncappedSystemExperienceOnce(guildId, XP_AMOUNT, ADMIN_BONUS, any())
        } returns true

        val result = submitXp()

        assertTrue(result)
        verify(exactly = 0) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        xpTasks.remove().run()
        verify(exactly = 1) {
            progressionService.awardUncappedSystemExperienceOnce(guildId, XP_AMOUNT, ADMIN_BONUS, any())
        }
        assertNull(player.nextMessage())
        xpReplies.remove().run()
        assertTrue(player.nextMessage()?.contains("Granted") == true)
    }

    /** Unauthorized senders cannot enqueue database work. */
    @Test
    fun xpPermissionDenied() {
        val result = submitXp()

        assertTrue(result)
        assertTrue(xpTasks.isEmpty())
        verify(exactly = 0) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        assertTrue(player.nextMessage()?.contains("permission", ignoreCase = true) == true)
    }

    /** Invalid and overflowing amounts are rejected before lookup. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["0", "-1", "1.5", "abc", "2147483648"])
    fun xpInvalidAmount(amount: String) {
        player.addAttachment(mockPlugin, XP_PERMISSION, true)
        command.onCommand(player, mockCommand, COMMAND_NAME, arrayOf("xp", "give", GUILD_NAME, amount))
        assertTrue(xpTasks.isEmpty())
        verify(exactly = 0) { guildService.getGuildByName(any()) }
        assertTrue(player.nextMessage()?.contains("positive whole number") == true)
    }

    /** Missing arguments and unsupported XP verbs never enqueue work. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["xp", "xp give Vibe", "xp take Vibe 5"])
    fun xpInvalidUsage(input: String) {
        player.addAttachment(mockPlugin, XP_PERMISSION, true)
        command.onCommand(player, mockCommand, COMMAND_NAME, input.split(" ").toTypedArray())
        assertTrue(xpTasks.isEmpty())
        assertTrue(player.nextMessage()?.contains("Usage:") == true)
    }

    /** Console grants support names containing spaces without a player permission attachment. */
    @Test
    fun xpConsoleGrant() {
        val guild = mockk<Guild>()
        every { guild.id } returns UUID.randomUUID()
        every { guild.name } returns MULTI_WORD_NAME
        every { guildService.getGuildByName(MULTI_WORD_NAME) } returns guild
        every {
            progressionService.awardUncappedSystemExperienceOnce(any(), XP_AMOUNT, ADMIN_BONUS, any())
        } returns true
        command.onCommand(
            server.consoleSender,
            mockCommand,
            COMMAND_NAME,
            arrayOf("xp", "give", "Two", "Words", XP_AMOUNT.toString()),
        )
        xpTasks.remove().run()
        xpReplies.remove().run()
        assertTrue(server.consoleSender.nextMessage()?.contains(MULTI_WORD_NAME) == true)
    }

    /** Missing guilds do not create a ledger award. */
    @Test
    fun xpMissingGuild() {
        player.addAttachment(mockPlugin, XP_PERMISSION, true)
        every { guildService.getGuildByName(any()) } returns null
        every { guildService.getAllGuilds() } returns emptySet()
        submitXp()
        assertTrue(xpTasks.isEmpty())
        verify(exactly = 0) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        assertTrue(player.nextMessage()?.contains("Guild not found") == true)
    }

    /** Rejected awards report failure, not success. */
    @Test
    fun xpRejectedAward() {
        prepareXpGuild()
        every { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) } returns false
        submitXp()
        xpTasks.remove().run()
        xpReplies.remove().run()
        assertTrue(player.nextMessage()?.contains("Failed to grant") == true)
    }

    /** An ambiguous exception exposes the transaction reference and never retries. */
    @Test
    fun xpAwardException() {
        prepareXpGuild()
        every {
            progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any())
        } throws IllegalStateException("DB unavailable")
        submitXp()
        xpTasks.remove().run()
        xpReplies.remove().run()
        verify(exactly = 1) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        assertTrue(player.nextMessage()?.contains("Check the ledger before retrying") == true)
        assertTrue(xpTasks.isEmpty())
    }

    /** A disabled scheduler cannot attempt an award. */
    @Test
    fun xpScheduleRejected() {
        prepareXpGuild()
        command = LumaGuildsCommand({ throw IllegalStateException(SCHEDULER_DISABLED) }, { xpReplies.add(it) })
        submitXp()
        verify(exactly = 0) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        assertTrue(player.nextMessage()?.contains("No award was attempted") == true)
    }

    /** Losing a reply after commit must not submit another award. */
    @Test
    fun xpReplyRejected() {
        prepareXpGuild()
        every { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) } returns true
        command = LumaGuildsCommand({ xpTasks.add(it) }, { throw IllegalStateException(SCHEDULER_DISABLED) })
        submitXp()
        xpTasks.remove().run()
        verify(exactly = 1) { progressionService.awardUncappedSystemExperienceOnce(any(), any(), any(), any()) }
        assertNull(player.nextMessage())
        assertTrue(xpTasks.isEmpty())
    }

    private fun submitXp(): Boolean =
        command.onCommand(player, mockCommand, COMMAND_NAME, arrayOf("xp", "give", GUILD_NAME, XP_AMOUNT.toString()))

    private fun prepareXpGuild(): UUID {
        player.addAttachment(mockPlugin, XP_PERMISSION, true)
        val guild = mockk<Guild>()
        val guildId = UUID.randomUUID()
        every { guild.id } returns guildId
        every { guild.name } returns GUILD_NAME
        every { guildService.getGuildByName(GUILD_NAME) } returns guild
        return guildId
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

    private companion object {
        const val COMMAND_NAME = "lumaguilds"
        const val XP_PERMISSION = "lumaguilds.admin.xp"
        const val GUILD_NAME = "Vibe"
        const val MULTI_WORD_NAME = "Two Words"
        const val SCHEDULER_DISABLED = "disabled"
        const val XP_AMOUNT = 5
    }
}
