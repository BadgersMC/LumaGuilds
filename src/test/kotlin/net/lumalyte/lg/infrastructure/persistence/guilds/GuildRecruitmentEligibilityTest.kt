package net.lumalyte.lg.infrastructure.persistence.guilds

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.infrastructure.services.GuildRecruitmentEligibilityBukkit
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GuildRecruitmentEligibilityTest : RewardSqlTestFixture() {
    @Test fun `optional reviewed block contract refuses target members without blocking unrelated guilds`() {
        val storage = openStorage()
        val player = UUID.randomUUID()
        val target = UUID.randomUUID()
        val guild = UUID.randomUUID()
        val members = mockk<MemberService>(relaxed = true)
        every { members.getGuildMembers(guild) } returns setOf(Member(target, guild, UUID.randomUUID(), Instant.now()))
        val eligibility = GuildRecruitmentEligibilityBukkit(storage, members) { false }
        assertTrue(eligibility.allows(player, guild))
        storage.connection.executeUpdate(
            "CREATE TABLE player_invite_blocks (owner_id VARCHAR(36), target_id VARCHAR(36), created_at BIGINT)",
        )
        storage.connection.executeUpdate("INSERT INTO player_invite_blocks VALUES (?, ?, 0)", player.toString(), target.toString())
        assertFalse(eligibility.allows(player, guild))
        assertTrue(eligibility.allows(player, UUID.randomUUID()))
        assertTrue(eligibility.allows(target, guild))
        assertFalse(GuildRecruitmentEligibilityBukkit(storage, members) { true }.allows(player, guild))
    }

    @Test fun `malformed block data fails closed rather than permitting admission`() {
        val storage = openStorage()
        val player = UUID.randomUUID()
        storage.connection.executeUpdate("CREATE TABLE player_invite_blocks (owner_id VARCHAR(36), target_id VARCHAR(36))")
        storage.connection.executeUpdate("INSERT INTO player_invite_blocks VALUES (?, 'invalid')", player.toString())
        val eligibility = GuildRecruitmentEligibilityBukkit(storage, mockk(relaxed = true)) { false }
        assertFailsWith<IllegalArgumentException> { eligibility.allows(player, UUID.randomUUID()) }
    }
}
