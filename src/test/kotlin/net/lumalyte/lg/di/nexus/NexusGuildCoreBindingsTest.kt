package net.lumalyte.lg.di.nexus

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.core.NexusContext
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.GuildInvitationRepository
import net.lumalyte.lg.application.persistence.InviteBlockRepository
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.application.services.DurableInviteBlockService
import net.lumalyte.lg.application.services.InviteBlockService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.infrastructure.persistence.storage.Storage
import net.lumalyte.lg.infrastructure.services.InviteBlockServiceImpl
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NexusGuildCoreBindingsTest {
    @TempDir
    lateinit var tempDir: File

    private fun fixture(claimsEnabled: Boolean): GuildNexusFoundation {
        val plugin = mockk<LumaGuilds>()
        every { plugin.dataFolder } returns tempDir
        every { plugin.config } returns YamlConfiguration()
        every { plugin.logger } returns Logger.getLogger("NexusGuildCoreBindingsTest")
        every { plugin.metadata } returns null
        return GuildNexusFoundation.prepare(plugin, mockk<Storage<*>>(), claimsEnabled)
    }

    @Test
    fun `guild factory count matches Koin after collapsing two redundant interface aliases`() {
        val standalone = NexusContext.create()
        val withClaims = NexusContext.create()
        try {
            standalone.registerGuildCoreBindings(claimsEnabled = false)
            withClaims.registerGuildCoreBindings(claimsEnabled = true)
            assertEquals(32, standalone.getBeanNames().size)
            assertEquals(33, withClaims.getBeanNames().size)
            assertTrue("guildRolePermissionResolver" in withClaims.getBeanNames())
        } finally {
            standalone.close()
            withClaims.close()
        }
    }

    @Test
    fun `guild core registrations are lazy and include the legacy service contracts`() {
        fixture(claimsEnabled = false).use { foundation ->
            val context = foundation.context
            val guildNames = context.getBeanNames().filter {
                it in setOf(
                    "guildRepository", "rankRepository", "memberRepository",
                    "relationRepository", "guildInvitationRepository", "inviteBlockServiceImpl",
                    "guildService", "rankService", "memberService", "relationService",
                    "guildBannerService", "auditService", "membershipHistoryRepository",
                    "guildCosmeticUnlockRepository", "guildCosmeticUnlockService",
                    "bannermanRenderService", "bannermanListeners", "strikesConfig",
                    "strikeService", "penaltyService"
                )
            }
            assertEquals(20, guildNames.size)
            assertFalse("guildRolePermissionResolver" in context.getBeanNames())

            // Explicit singleton verification with a no-IO service: no DB gets touched.
            val overrides = context.getBean<AdminOverrideService>()
            assertSame(overrides, context.getBean<AdminOverrideService>())
        }
    }

    @Test
    fun `the invite block implementation is indexed under both contracts as one singleton`() {
        NexusContext.create().also { context ->
            try {
                val blocks = mockk<InviteBlockRepository>()
                val members = mockk<MemberService>()
                val invitations = mockk<GuildInvitationRepository>()
                context.registerBean("inviteBlockServiceImpl", InviteBlockServiceImpl::class) {
                    InviteBlockServiceImpl(blocks, members, invitations)
                }
                val standard = context.getBean<InviteBlockService>()
                val durable = context.getBean<DurableInviteBlockService>()
                assertSame(standard as Any, durable as Any)
                assertSame(standard as Any, context.getBean<InviteBlockServiceImpl>() as Any)
                assertTrue("inviteBlockServiceImpl" in context.getBeanNames())
            } finally {
                context.close()
            }
        }
    }
}
