package net.lumalyte.lg.di.runtime

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.api.GuildLookup
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.infrastructure.persistence.storage.SqlDialect
import org.bukkit.Server
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.UUID
import kotlin.test.assertEquals

/**
 * REQ-NEXUS-CUTOVER-2/3: external Bukkit contracts are published once each, backed by the active
 * graph's singletons; the durable invite gate closes before services are unregistered.
 */
class GuildServicePublicationTest {
    private val registrations = mutableListOf<Pair<Class<*>, Any>>()
    private val services = mockk<ServicesManager> {
        every { register(any<Class<Any>>(), any(), any(), any()) } answers {
            assertEquals(ServicePriority.Normal, arg<ServicePriority>(3))
            registrations += arg<Class<*>>(0) to arg<Any>(1)
        }
    }
    private val plugin = mockk<LumaGuilds> {
        every { server } returns mockk<Server> { every { servicesManager } returns this@GuildServicePublicationTest.services }
    }
    private val guildService = mockk<GuildService>(relaxed = true)
    private val memberService = mockk<MemberService>(relaxed = true)
    private val durable = mockk<DurableInviteBlockService>(relaxed = true)
    private val graph = mockk<GuildRuntimeGraph>(relaxed = true) {
        every { guildService } returns this@GuildServicePublicationTest.guildService
        every { memberService } returns this@GuildServicePublicationTest.memberService
        every { durableInviteBlockService } returns durable
    }

    @ParameterizedTest
    @EnumSource(SqlDialect::class)
    fun `each external contract is published exactly once from graph singletons`(dialect: SqlDialect) {
        publishGuildServices(plugin, graph, dialect, RuntimeShutdown { _, _ -> })

        val expected = listOfNotNull(
            "GuildLookup", "GuildShopXpApi", "GuildVisualLookup", "GuildAllianceLookup", "GuildCosmeticUnlocks",
            "GuildInviteBlocks", "GuildInviteBlocksV2".takeIf { dialect == SqlDialect.SQLITE },
        )
        assertEquals(expected, registrations.map { it.first.simpleName })

        val player = UUID.randomUUID()
        val lookup = registrations.single { it.first == GuildLookup::class.java }.second as GuildLookup
        lookup.getPlayerGuildIds(player)
        verify(exactly = 1) { memberService.getPlayerGuilds(player) }
    }

    @ParameterizedTest
    @EnumSource(SqlDialect::class)
    fun `durable invite gate closes once and before services unregister`(dialect: SqlDialect) {
        val order = mutableListOf<String>()
        every { durable.close() } answers { order += "invite gate" }
        val shutdown = RuntimeShutdown { _, _ -> }
        shutdown.register(ShutdownStage.SERVICES, "services") { order += "unregister services" }

        publishGuildServices(plugin, graph, dialect, shutdown)
        shutdown.close()
        shutdown.close()

        assertEquals(listOf("invite gate", "unregister services"), order)
    }
}
