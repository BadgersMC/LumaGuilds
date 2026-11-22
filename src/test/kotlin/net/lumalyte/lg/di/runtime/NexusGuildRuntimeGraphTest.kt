package net.lumalyte.lg.di.runtime

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.annotations.ScopeType
import net.badgersmc.nexus.core.ComponentRegistry
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.di.nexus.GuildNexusFoundation
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REQ-NEXUS-CUTOVER-2: every collaborator the runtime resolves is one singleton definition in the
 * active graph, with claim collaborators absent when claims are disabled. Checked from bean
 * definitions so no Bukkit-dependent instance is constructed.
 */
class NexusGuildRuntimeGraphTest {
    @TempDir
    lateinit var tempDir: File

    private fun foundation(claimsEnabled: Boolean): GuildNexusFoundation {
        val plugin = mockk<LumaGuilds>()
        every { plugin.dataFolder } returns tempDir
        every { plugin.config } returns YamlConfiguration()
        every { plugin.logger } returns Logger.getLogger("NexusGuildRuntimeGraphTest")
        every { plugin.metadata } returns null
        return GuildNexusFoundation.prepare(plugin, mockk<Storage<*>>(), claimsEnabled)
    }

    private fun NexusContext.registry(): ComponentRegistry =
        NexusContext::class.java.getDeclaredField("registry").apply { isAccessible = true }.get(this) as ComponentRegistry

    private fun assertSingletonDefinitions(context: NexusContext, graphType: KClass<*>) {
        val contextOwned = setOf("claims", "scope", "virtualDispatcher")
        val problems = graphType.memberProperties.filterNot { it.name in contextOwned }.mapNotNull { property ->
            val type = property.returnType.classifier as KClass<*>
            val definitions = context.registry().getDefinitionsByType(type)
            val optional = property.returnType.isMarkedNullable
            when {
                definitions.size > 1 -> "${property.name}: ambiguous ${definitions.map { it.name }}"
                definitions.isEmpty() && !optional -> "${property.name}: no ${type.simpleName} definition"
                definitions.any { it.scope != ScopeType.SINGLETON } -> "${property.name}: not a singleton"
                else -> null
            }
        }
        assertEquals(emptyList(), problems)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `runtime collaborators resolve to exactly one singleton`(claimsEnabled: Boolean) {
        foundation(claimsEnabled).use { foundation ->
            assertSingletonDefinitions(foundation.context, GuildRuntimeGraph::class)
            if (claimsEnabled) assertSingletonDefinitions(foundation.context, ClaimRuntimeGraph::class)
        }
    }

    @Test
    fun `claims disabled exposes no claim collaborators and resolves no permission resolver`() {
        foundation(claimsEnabled = false).use { foundation ->
            val graph = NexusGuildRuntimeGraph(foundation.context, claimsEnabled = false)

            assertNull(graph.claims)
            assertNull(graph.guildRolePermissionResolver)
        }
    }

    @Test
    fun `claims enabled exposes claim listeners`() {
        foundation(claimsEnabled = true).use { foundation ->
            assertNotNull(NexusGuildRuntimeGraph(foundation.context, claimsEnabled = true).claims)
        }
    }

    @Test
    fun `absent Apollo plugin leaves every optional Apollo collaborator unresolved`() {
        foundation(claimsEnabled = true).use { foundation ->
            val graph = NexusGuildRuntimeGraph(foundation.context, claimsEnabled = true)

            assertNull(graph.lunarClientService)
            assertNull(graph.guildTeamService)
            assertNull(graph.guildTeamListener)
            assertNull(graph.guildNotificationService)
            assertNull(graph.guildNotificationListener)
            assertNull(graph.guildRichPresenceListener)
        }
    }

    @Test
    fun `command catalog constructor dependencies are uniquely registered`() {
        listOf(true, false).forEach { claimsEnabled ->
            foundation(claimsEnabled).use { foundation ->
                val missing = NexusCommandCatalog.classes(claimsEnabled).flatMap { command ->
                    val constructor = requireNotNull(command.constructors.singleOrNull()) { "${command.simpleName} needs one constructor" }
                    constructor.parameters.mapNotNull { parameter ->
                        val type = parameter.type.classifier as KClass<*>
                        val count = foundation.context.registry().getDefinitionsByType(type).size
                        if (count == 1) null else "${command.simpleName}.${parameter.name}: $count ${type.simpleName} definitions"
                    }
                }
                assertTrue(missing.isEmpty(), "claimsEnabled=$claimsEnabled\n${missing.joinToString("\n")}")
            }
        }
    }
}
