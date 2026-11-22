package net.lumalyte.lg.di.nexus

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.actions.player.DoesPlayerHaveClaimOverride
import net.lumalyte.lg.application.actions.player.visualisation.GetVisualiserMode
import net.lumalyte.lg.application.persistence.PlayerStateRepository
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NexusClaimsBindingsTest {
    @TempDir
    lateinit var tempDir: File

    private fun createFixture(claimsEnabled: Boolean): GuildNexusFoundation {
        val plugin = mockk<LumaGuilds>()
        every { plugin.dataFolder } returns tempDir
        every { plugin.config } returns YamlConfiguration()
        every { plugin.logger } returns Logger.getLogger("NexusClaimsBindingsTest")
        every { plugin.metadata } returns null
        return GuildNexusFoundation.prepare(plugin, mockk<Storage<*>>(), claimsEnabled)
    }

    @Test
    fun `claim bindings remain conditional and preserve legacy registration count`() {
        createFixture(claimsEnabled = false).use { disabled ->
            createFixture(claimsEnabled = true).use { enabled ->
                val disabledNames = disabled.context.getBeanNames()
                val enabledNames = enabled.context.getBeanNames()
                assertTrue("configService" in disabledNames)
                assertFalse("createClaim" in disabledNames)
                assertFalse("playerStateRepository" in disabledNames)
                val enabledOnly = enabledNames - disabledNames
                val claimListeners = setOf(
                    "claimAnchorListener", "claimDestructionListener", "editToolListener", "editToolVisualisingListener",
                    "harvestReplantListener", "moveToolListener", "partitionUpdateListener", "playerClaimProtectionListener",
                    "claimPvpProtectionListener", "toolRemovalListener", "worldClaimProtectionListener", "closeInventoryListener",
                )
                assertTrue(claimListeners.all { it in enabledOnly }, "claim listeners must remain conditional")
                // Shared collaborator for Nexus claim commands; conditional like the listeners.
                val claimCommandCollaborators = setOf("nexusClaimAccess")
                assertTrue(claimCommandCollaborators.all { it in enabledOnly }, "claim command collaborators must remain conditional")
                val originalBindings = enabledOnly - claimListeners - claimCommandCollaborators
                assertEquals(81, originalBindings.size) // 80 claimsModule + 1 guild-claims integration
                assertTrue("guildRolePermissionResolver" in originalBindings)
                assertEquals(80, (originalBindings - "guildRolePermissionResolver").size)
                assertTrue("createClaim" in enabledNames)
                assertTrue("adminOverrideListener" in enabledNames)
                assertTrue("playerStateRepository" in enabledNames)
            }
        }
    }

    @Test
    fun `Nexus constructs claim actions with the same shared player state repository`() {
        createFixture(claimsEnabled = true).use { foundation ->
            val context = foundation.context
            val stateRepository = context.getBean<PlayerStateRepository>()
            val action = context.getBean<DoesPlayerHaveClaimOverride>()
            val visualiser = context.getBean<GetVisualiserMode>()
            val playerId = UUID.randomUUID()

            assertSame(stateRepository, context.getBean<PlayerStateRepository>())
            assertSame(action, context.getBean<DoesPlayerHaveClaimOverride>())
            action.execute(playerId)
            assertNotNull(stateRepository.get(playerId))
            visualiser.execute(playerId)
            assertNotNull(stateRepository.get(playerId))

            // Other actions are registered lazily and cannot touch the DB until needed.
            assertTrue("createClaim" in context.getBeanNames())
        }
    }
}
