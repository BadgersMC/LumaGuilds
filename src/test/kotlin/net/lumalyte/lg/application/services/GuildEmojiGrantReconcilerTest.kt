package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.EmojiGrantRepository
import net.lumalyte.lg.domain.entities.EmojiPermissionGrant
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GuildEmojiGrantReconcilerTest {
    private val guildId = UUID.randomUUID()
    private val playerOne = UUID.randomUUID()
    private val playerTwo = UUID.randomUUID()
    private val guild = Guild(id = guildId, name = "Badgers", createdAt = Instant.EPOCH)
    private val guildService = mockk<GuildService>()
    private val memberService = mockk<MemberService>()
    private lateinit var ledger: InMemoryEmojiGrantRepository
    private lateinit var gateway: RecordingEmojiPermissionGateway
    private lateinit var reconciler: GuildEmojiGrantReconciler

    @BeforeEach
    fun setUp() {
        ledger = InMemoryEmojiGrantRepository()
        gateway = RecordingEmojiPermissionGateway()
        every { guildService.getAllGuilds() } returns setOf(guild)
        every { memberService.getGuildMembers(guildId) } returns setOf(member(playerOne), member(playerTwo))
        reconciler = GuildEmojiGrantReconciler(guildService, memberService, ledger, gateway)
    }

    @Test
    fun `initial mapping grants every current member and records ownership`() {
        val result = reconciler.reconcileAll(mapOf("badgers" to "enthusia.emoji.badger"))

        assertEquals(setOf(playerOne, playerTwo), ledger.getForGuild(guildId).map { it.playerId }.toSet())
        assertEquals(2, result.granted)
        assertEquals(0, result.failed)
    }

    @Test
    fun `unrecorded matching permission is never revoked`() {
        gateway.externallyOwned += playerOne to "enthusia.emoji.badger"

        reconciler.reconcileAll(emptyMap())

        assertEquals(emptyList(), gateway.operations)
    }

    @Test
    fun `replacement revokes old permission before granting new`() {
        ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.old"))
        every { memberService.getGuildMembers(guildId) } returns setOf(member(playerOne))

        reconciler.reconcileAll(mapOf("badgers" to "enthusia.emoji.new"))

        assertEquals(
            listOf(
                "revoke:$playerOne:enthusia.emoji.old",
                "grant:$playerOne:enthusia.emoji.new",
            ),
            gateway.operations,
        )
        assertEquals("enthusia.emoji.new", ledger.getForPlayerAndGuild(playerOne, guildId)?.permission)
    }

    @Test
    fun `config removal revokes and deletes recorded ownership`() {
        ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.badger"))

        reconciler.reconcileAll(emptyMap())

        assertEquals(listOf("revoke:$playerOne:enthusia.emoji.badger"), gateway.operations)
        assertNull(ledger.getForPlayerAndGuild(playerOne, guildId))
    }

    @Test
    fun `failed revoke retains ownership for retry`() {
        ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.badger"))
        gateway.failRevokes += "enthusia.emoji.badger"

        val result = reconciler.reconcileAll(emptyMap())

        assertEquals(1, result.failed)
        assertEquals("enthusia.emoji.badger", ledger.getForPlayerAndGuild(playerOne, guildId)?.permission)
    }

    @Test
    fun `failed grant creates no ownership row`() {
        every { memberService.getGuildMembers(guildId) } returns setOf(member(playerOne))
        gateway.failGrants += "enthusia.emoji.badger"

        val result = reconciler.reconcileAll(mapOf("badgers" to "enthusia.emoji.badger"))

        assertEquals(1, result.failed)
        assertNull(ledger.getForPlayerAndGuild(playerOne, guildId))
    }

    @Test
    fun `ledger write failure compensates successful external grant`() {
        every { memberService.getGuildMembers(guildId) } returns setOf(member(playerOne))
        ledger.failNextUpsert = true

        reconciler.reconcileAll(mapOf("badgers" to "enthusia.emoji.badger"))

        assertEquals(
            listOf(
                "grant:$playerOne:enthusia.emoji.badger",
                "revoke:$playerOne:enthusia.emoji.badger",
            ),
            gateway.operations,
        )
        assertNull(ledger.getForPlayerAndGuild(playerOne, guildId))
    }

    @Test
    fun `member join grants current mapping`() {
        reconciler.reconcileMembership(playerOne, guildId, "enthusia.emoji.badger")

        assertEquals("enthusia.emoji.badger", ledger.getForPlayerAndGuild(playerOne, guildId)?.permission)
    }

    @Test
    fun `member leave revokes recorded grant without current config`() {
        ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.removed"))

        reconciler.removeMembership(playerOne, guildId)

        assertEquals(listOf("revoke:$playerOne:enthusia.emoji.removed"), gateway.operations)
        assertNull(ledger.getForPlayerAndGuild(playerOne, guildId))
    }

    @Test
    fun `guild reconciliation replaces members and removes former member grants`() {
        val formerMember = UUID.randomUUID()
        ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.old"))
        ledger.upsert(EmojiPermissionGrant(formerMember, guildId, "enthusia.emoji.old"))

        reconciler.reconcileGuild(guildId, "enthusia.emoji.new")

        assertEquals(
            setOf(
                EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.new"),
                EmojiPermissionGrant(playerTwo, guildId, "enthusia.emoji.new"),
            ),
            ledger.getForGuild(guildId).toSet(),
        )
        val playerOperations = gateway.operations.filter { it.contains(playerOne.toString()) }
        assertEquals(
            listOf(
                "revoke:$playerOne:enthusia.emoji.old",
                "grant:$playerOne:enthusia.emoji.new",
            ),
            playerOperations,
        )
    }

    @Test
    fun `guild removal revokes ledger after guild record is gone`() {
        ledger.upsert(EmojiPermissionGrant(playerOne, guildId, "enthusia.emoji.badger"))
        ledger.upsert(EmojiPermissionGrant(playerTwo, guildId, "enthusia.emoji.badger"))

        reconciler.removeGuild(guildId)

        assertEquals(emptyList(), ledger.getForGuild(guildId))
        assertEquals(2, gateway.operations.count { it.startsWith("revoke:") })
    }

    private fun member(playerId: UUID) = Member(playerId, guildId, UUID.randomUUID(), Instant.EPOCH)
}

private class InMemoryEmojiGrantRepository : EmojiGrantRepository {
    private val values = linkedMapOf<Pair<UUID, UUID>, EmojiPermissionGrant>()
    var failNextUpsert = false

    override fun getAll(): List<EmojiPermissionGrant> = values.values.toList()
    override fun getForGuild(guildId: UUID): List<EmojiPermissionGrant> = values.values.filter { it.guildId == guildId }
    override fun getForPlayerAndGuild(playerId: UUID, guildId: UUID): EmojiPermissionGrant? = values[playerId to guildId]
    override fun upsert(grant: EmojiPermissionGrant): Boolean {
        if (failNextUpsert) {
            failNextUpsert = false
            return false
        }
        values[grant.playerId to grant.guildId] = grant
        return true
    }
    override fun delete(playerId: UUID, guildId: UUID): Boolean {
        values.remove(playerId to guildId)
        return true
    }
}

private class RecordingEmojiPermissionGateway : EmojiPermissionGateway {
    val operations = mutableListOf<String>()
    val externallyOwned = mutableSetOf<Pair<UUID, String>>()
    val failGrants = mutableSetOf<String>()
    val failRevokes = mutableSetOf<String>()

    override fun grant(playerId: UUID, permission: String): Boolean {
        operations += "grant:$playerId:$permission"
        return permission !in failGrants
    }

    override fun revoke(playerId: UUID, permission: String): Boolean {
        operations += "revoke:$playerId:$permission"
        return permission !in failRevokes
    }
}
