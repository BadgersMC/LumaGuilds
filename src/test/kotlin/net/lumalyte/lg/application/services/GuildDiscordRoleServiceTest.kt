package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildDiscordRoleRepository
import net.lumalyte.lg.config.DiscordGuildRolesConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildDiscordRoleLink
import net.lumalyte.lg.domain.entities.Member
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuildDiscordRoleServiceTest {
    private val now = Instant.parse("2026-09-20T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val guildId = UUID.randomUUID()
    private val playerOne = UUID.randomUUID()
    private val playerTwo = UUID.randomUUID()
    private val rankId = UUID.randomUUID()

    @Test
    fun `guild creation creates a durable Discord role immediately`() {
        val fixture = fixture(guild(level = 1))

        val result = fixture.service.reconcileGuild(guildId).join()

        assertEquals(1, result.guildsReconciled)
        assertEquals(1, result.rolesCreated)
        assertEquals(1, fixture.gateway.ensureCalls)
        val persisted = assertNotNull(fixture.repository.get(guildId))
        assertEquals(FakeGateway.ROLE_ID, persisted.discordRoleId)
        assertEquals(now, persisted.unlockedAt)
    }

    @Test
    fun `guild creation grants the role immediately to Discord-linked current members`() {
        val fixture = fixture(
            guild(level = 1),
            members = setOf(member(playerOne), member(playerTwo)),
        )

        val result = fixture.service.reconcileGuild(guildId).join()

        assertEquals(1, result.guildsReconciled)
        assertEquals(1, result.rolesCreated)
        assertEquals(2, result.memberRolesApplied)
        assertEquals(setOf(playerOne, playerTwo), fixture.gateway.granted.toSet())
        assertNotNull(fixture.repository.get(guildId))
    }

    @Test
    fun `persisted role link is reused regardless of guild level`() {
        val fixture = fixture(guild(level = 1), members = setOf(member(playerOne)))
        fixture.repository.upsert(
            GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now.minusSeconds(60))
        )
        fixture.gateway.createOnEnsure = false

        val result = fixture.service.reconcileGuild(guildId).join()

        assertEquals(1, result.guildsReconciled)
        assertEquals(0, result.rolesCreated)
        assertEquals(listOf(playerOne), fixture.gateway.granted)
    }
    @Test
    fun `member join and removal dynamically grant and revoke the persisted guild role`() {
        val fixture = fixture(guild(level = 50))
        fixture.repository.upsert(GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now))
        fixture.gateway.createOnEnsure = false

        val joined = fixture.service.memberJoined(guildId, playerOne).join()
        val removed = fixture.service.memberRemoved(guildId, playerOne).join()

        assertEquals(1, joined.memberRolesApplied)
        assertEquals(1, removed.memberRolesRemoved)
        assertEquals(listOf(playerOne), fixture.gateway.granted)
        assertEquals(listOf(playerOne), fixture.gateway.revoked)
    }

    @Test
    fun `reconciliation revokes stale role holders missed during an outage`() {
        val fixture = fixture(
            guild(level = 50),
            members = setOf(member(playerOne)),
        )
        fixture.repository.upsert(GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now))
        fixture.gateway.createOnEnsure = false
        fixture.gateway.unexpectedRoleMembers += playerTwo

        val result = fixture.service.reconcileGuild(guildId).join()

        assertEquals(1, result.memberRolesRemoved)
        assertEquals(listOf(playerTwo), fixture.gateway.revoked)
        assertTrue(fixture.gateway.unexpectedRoleMembers.isEmpty())
        assertEquals(listOf(playerOne), fixture.gateway.granted)
    }

    @Test
    fun `simultaneous callers claim in flight slot before gateway role creation`() {
        val fixture = fixture(guild(level = 50))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.gateway.ensureEntered = entered
        fixture.gateway.ensureRelease = release
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first = CompletableFuture.supplyAsync(
                { fixture.service.memberJoined(guildId, playerOne).join() },
                executor,
            )
            assertTrue(entered.await(5, TimeUnit.SECONDS))

            val secondStarted = CountDownLatch(1)
            val second = CompletableFuture.supplyAsync(
                {
                    secondStarted.countDown()
                    fixture.service.memberJoined(guildId, playerTwo).join()
                },
                executor,
            )
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            Thread.sleep(100)
            assertEquals(1, fixture.gateway.ensureCalls)

            release.countDown()
            first.join()
            second.join()
            assertEquals(1, fixture.gateway.ensureCalls)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `two concurrent joins share one role creation`() {
        val fixture = fixture(guild(level = 50))
        val pendingEnsure = CompletableFuture<DiscordRoleEnsureResult>()
        fixture.gateway.ensureOverride = pendingEnsure

        val first = fixture.service.memberJoined(guildId, playerOne)
        val second = fixture.service.memberJoined(guildId, playerTwo)

        assertEquals(1, fixture.gateway.ensureCalls)
        pendingEnsure.complete(DiscordRoleEnsureResult(FakeGateway.ROLE_ID, created = true))

        assertEquals(1, first.join().rolesCreated)
        assertEquals(1, second.join().rolesCreated)
        assertEquals(1, fixture.gateway.ensureCalls)
        assertEquals(setOf(playerOne, playerTwo), fixture.gateway.granted.toSet())
        assertNotNull(fixture.repository.get(guildId))
    }

    @Test
    fun `new Discord role is deleted when durable link persistence fails`() {
        val fixture = fixture(guild(level = 50))
        fixture.repository.failUpsert = true

        val result = fixture.service.memberJoined(guildId, playerOne).join()

        assertEquals(1, result.failures)
        assertEquals(listOf(FakeGateway.ROLE_ID), fixture.gateway.deleted)
        assertNull(fixture.repository.get(guildId))
        assertTrue(fixture.gateway.granted.isEmpty())
    }

    @Test
    fun `startup reconciliation removes role links for guilds that no longer exist`() {
        val repository = FakeRepository().apply {
            upsert(GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now))
        }
        val fixture = fixture(guild = null, repository = repository)

        val result = fixture.service.reconcileAll().join()

        assertEquals(0, result.failures)
        assertEquals(listOf(FakeGateway.ROLE_ID), fixture.gateway.deleted)
        assertNull(repository.get(guildId))
    }

    @Test
    fun `linking Discord after joining grants every unlocked guild role`() {
        val fixture = fixture(
            guild(level = 50),
            members = setOf(member(playerOne)),
        )
        fixture.repository.upsert(GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now))
        fixture.gateway.createOnEnsure = false

        val result = fixture.service.discordAccountLinked(playerOne).join()

        assertEquals(1, result.memberRolesApplied)
        assertEquals(listOf(playerOne), fixture.gateway.granted)
    }

    @Test
    fun `unlink event revokes by captured Discord id even after account mapping disappears`() {
        val fixture = fixture(
            guild(level = 50),
            members = setOf(member(playerOne)),
        )
        fixture.repository.upsert(GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now))

        val result = fixture.service.discordAccountUnlinked(playerOne, "222222222222222222").join()

        assertEquals(1, result.memberRolesRemoved)
        assertEquals(listOf("222222222222222222"), fixture.gateway.revokedDiscordIds)
        assertTrue(fixture.gateway.revoked.isEmpty())
    }

    @Test
    fun `guild rename reuses role id and asks gateway to update role name`() {
        val fixture = fixture(guild(name = "New Badgers", level = 50))
        fixture.repository.upsert(GuildDiscordRoleLink(guildId, FakeGateway.ROLE_ID, now))
        fixture.gateway.createOnEnsure = false

        fixture.service.guildRenamed(guildId).join()

        assertEquals(1, fixture.gateway.ensureRequests.size)
        assertEquals(FakeGateway.ROLE_ID, fixture.gateway.ensureRequests.single().first)
        assertEquals("Guild • New Badgers", fixture.gateway.ensureRequests.single().second)
    }

    @Test
    fun `role name rendering strips line breaks and enforces Discord length limit`() {
        val fixture = fixture(guild(level = 50))

        val rendered = fixture.service.renderRoleName(
            "[Guild] <guild>",
            "Badgers\n" + "x".repeat(150),
        )

        assertTrue(rendered.startsWith("[Guild] Badgers "))
        assertEquals(100, rendered.length)
        assertTrue('\n' !in rendered)
    }
    private fun fixture(
        guild: Guild?,
        members: Set<Member> = emptySet(),
        repository: FakeRepository = FakeRepository(),
        enabled: Boolean = true,
    ): Fixture {
        val configService = mockk<ConfigService>()
        every { configService.loadConfig() } returns MainConfig(
            discordGuildRoles = DiscordGuildRolesConfig(
                enabled = enabled,
                roleNameFormat = "Guild • <guild>",
            ),
        )

        val guildService = mockk<GuildService>()
        every { guildService.getGuild(guildId) } returns guild
        every { guildService.getAllGuilds() } returns if (guild == null) emptySet() else setOf(guild)

        val memberService = mockk<MemberService>()
        every { memberService.getGuildMembers(guildId) } returns members
        every { memberService.getPlayerGuilds(any()) } answers {
            val playerId = firstArg<UUID>()
            if (members.any { it.playerId == playerId }) setOf(guildId) else emptySet()
        }

        val gateway = FakeGateway()
        return Fixture(
            GuildDiscordRoleService(
                configService,
                guildService,
                memberService,
                repository,
                gateway,
                clock,
            ),
            repository,
            gateway,
        )
    }

    private fun guild(name: String = "Badgers", level: Int) = Guild(
        id = guildId,
        name = name,
        level = level,
        createdAt = now.minusSeconds(3600),
    )

    private fun member(playerId: UUID) = Member(
        playerId = playerId,
        guildId = guildId,
        rankId = rankId,
        joinedAt = now.minusSeconds(300),
    )

    private data class Fixture(
        val service: GuildDiscordRoleService,
        val repository: FakeRepository,
        val gateway: FakeGateway,
    )

    private class FakeRepository : GuildDiscordRoleRepository {
        private val links = linkedMapOf<UUID, GuildDiscordRoleLink>()
        var failUpsert: Boolean = false

        override fun get(guildId: UUID): GuildDiscordRoleLink? = links[guildId]
        override fun getAll(): List<GuildDiscordRoleLink> = links.values.toList()

        override fun upsert(link: GuildDiscordRoleLink): Boolean {
            if (failUpsert) return false
            links[link.guildId] = link
            return true
        }

        override fun delete(guildId: UUID): Boolean {
            links.remove(guildId)
            return true
        }
    }
    private class FakeGateway : DiscordGuildRoleGateway {
        companion object {
            const val ROLE_ID = "123456789012345678"
        }

        var available = true
        var createOnEnsure = true
        private val ensureCallCounter = AtomicInteger()
        val ensureCalls: Int get() = ensureCallCounter.get()
        var ensureOverride: CompletableFuture<DiscordRoleEnsureResult>? = null
        var ensureEntered: CountDownLatch? = null
        var ensureRelease: CountDownLatch? = null
        val unexpectedRoleMembers = linkedSetOf<UUID>()
        val ensureRequests = mutableListOf<Pair<String?, String>>()
        val granted = mutableListOf<UUID>()
        val revoked = mutableListOf<UUID>()
        val revokedDiscordIds = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        override fun isAvailable(): Boolean = available

        override fun ensureRole(
            existingRoleId: String?,
            roleName: String,
        ): CompletableFuture<DiscordRoleEnsureResult> {
            ensureCallCounter.incrementAndGet()
            ensureRequests += existingRoleId to roleName
            ensureEntered?.countDown()
            ensureRelease?.await(5, TimeUnit.SECONDS)
            ensureOverride?.let { return it }
            return CompletableFuture.completedFuture(
                DiscordRoleEnsureResult(
                    existingRoleId ?: ROLE_ID,
                    created = existingRoleId == null && createOnEnsure,
                )
            )
        }

        override fun grantRole(
            playerId: UUID,
            roleId: String,
        ): CompletableFuture<DiscordMemberRoleResult> {
            granted += playerId
            return CompletableFuture.completedFuture(DiscordMemberRoleResult.APPLIED)
        }

        override fun revokeRole(
            playerId: UUID,
            roleId: String,
        ): CompletableFuture<DiscordMemberRoleResult> {
            revoked += playerId
            return CompletableFuture.completedFuture(DiscordMemberRoleResult.REMOVED)
        }

        override fun revokeRoleByDiscordId(
            discordId: String,
            roleId: String,
        ): CompletableFuture<DiscordMemberRoleResult> {
            revokedDiscordIds += discordId
            return CompletableFuture.completedFuture(DiscordMemberRoleResult.REMOVED)
        }

        override fun revokeUnexpectedRoleMembers(
            roleId: String,
            allowedPlayerIds: Set<UUID>,
        ): CompletableFuture<Int> {
            val stale = unexpectedRoleMembers.filter { it !in allowedPlayerIds }
            revoked += stale
            unexpectedRoleMembers.removeAll(stale.toSet())
            return CompletableFuture.completedFuture(stale.size)
        }

        override fun deleteRole(roleId: String): CompletableFuture<Boolean> {
            deleted += roleId
            return CompletableFuture.completedFuture(true)
        }
    }
}
