package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.domain.rewards.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class GuildChronicleServiceTest {
    private val guild = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val catalog = RewardCatalog.chapterTwo()
    private var member = true
    private var manager = true
    private val repository = object : ChronicleGoalRepository {
        var goal = ChronicleGoal(null, 0)
        override fun read(guildId: UUID) = goal
        override fun compareAndSet(guildId: UUID, expected: ChronicleGoal, rewardId: String?): Boolean {
            if (goal != expected) return false
            goal = ChronicleGoal(rewardId, goal.revision + 1)
            return true
        }
    }
    private fun service(cat: RewardCatalog = catalog) = GuildChronicleService(
        cat, repository, { _, _ -> member }, { _, _ -> manager },
        { GuildRewardRead.Available(26, 0, RewardEntitlementResolver(cat).resolve(26, RewardOwnership())) },
    )

    @Test fun `all ten windows cover every level and reward exactly once`() {
        val all = (0..9).flatMap { service().read(actor, guild, it, ChronicleBranch.ALL)!!.levels }
        assertEquals((1..100).toList(), all.map { it.level })
        assertEquals(catalog.rewards.map { it.id }, all.flatMap { it.offers }.map { it.reward.id })
        assertEquals(30, all.sumOf { it.offers.size })
        assertTrue(all.flatMap { it.offers }.any { it.reward.id == "ally-homes" })
        assertTrue(all.flatMap { it.offers }.any { it.reward.id == "fee-5" })
        val exported = org.json.JSONArray(all.map { tier -> org.json.JSONObject()
            .put("level", tier.level).put("bank", tier.automaticBankCapacity)
            .put("rewards", org.json.JSONArray(tier.offers.map { offer -> org.json.JSONObject()
                .put("id", offer.reward.id).put("name", offer.reward.name).put("level", offer.reward.level)
                .put("price", offer.reward.price).put("status", offer.status.name)
                .put("effect", offer.reward.effect.toString())
                .put("branch", ChronicleBranch.entries.first { it != ChronicleBranch.ALL && it.includes(offer.reward.effect) }.name)
                .put("permanent", offer.reward.permanentOnPurchase) })) })
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/chronicle-preview-catalog.json"), exported.toString())
    }

    @Test fun `branches partition rewards without dropping automatic benefits`() {
        val ids = ChronicleBranch.entries.filter { it != ChronicleBranch.ALL }.flatMap { branch ->
            (0..9).flatMap { service().read(actor, guild, it, branch)!!.levels.flatMap { it.offers } }
        }.map { it.reward.id }
        assertEquals(catalog.rewards.map { it.id }.sorted(), ids.sorted())
        assertEquals(100, (0..9).sumOf { service().read(actor, guild, it, ChronicleBranch.TREASURY)!!.levels.size })
    }

    @Test fun `custom catalog remains authoritative`() {
        val custom = RewardCatalog((1..100).map { level -> catalog.level(level).let {
            it.copy(rewards = it.rewards.map { reward -> reward.copy(name = "Custom ${reward.id}", price = 1) })
        } })
        assertTrue(service(custom).read(actor, guild, 9, ChronicleBranch.ALL)!!.levels.flatMap { it.offers }
            .all { it.reward.name.startsWith("Custom ") && it.reward.price == 1L })
    }

    @Test fun `reopening sees saved goal while stale callbacks cannot replace or clear it`() {
        val original = service().read(actor, guild, 0, ChronicleBranch.ALL)!!.goal
        assertEquals(ChronicleGoalWrite.SAVED, service().track(actor, guild, original, "fee-5"))
        assertEquals("fee-5", service().read(actor, guild, 9, ChronicleBranch.ALL)!!.goal.rewardId)
        assertEquals(ChronicleGoalWrite.CONFLICT, service().track(actor, guild, original, null))
        assertEquals("fee-5", repository.goal.rewardId)
    }

    @Test fun `revoked permission membership and invalid targets never write`() {
        val goal = repository.goal
        manager = false
        assertNotNull(service().read(actor, guild, 0, ChronicleBranch.ALL))
        assertEquals(ChronicleGoalWrite.DENIED, service().track(actor, guild, goal, "bank-1"))
        manager = true
        assertEquals(ChronicleGoalWrite.INVALID, service().track(actor, guild, goal, "missing"))
        member = false
        assertNull(service().read(actor, guild, 0, ChronicleBranch.ALL))
        assertEquals(ChronicleGoalWrite.DENIED, service().track(actor, guild, goal, null))
        assertEquals(goal, repository.goal)
    }

    @Test fun `storage failure is unavailable rather than an empty saved goal`() {
        val broken = object : ChronicleGoalRepository {
            override fun read(guildId: UUID): ChronicleGoal = error("offline")
            override fun compareAndSet(guildId: UUID, expected: ChronicleGoal, rewardId: String?): Boolean = error("offline")
        }
        val svc = GuildChronicleService(catalog, broken, { _, _ -> true }, { _, _ -> true },
            { GuildRewardRead.Available(1, 0, RewardEntitlementResolver(catalog).resolve(1, RewardOwnership())) })
        assertNull(svc.read(actor, guild, 0, ChronicleBranch.ALL))
        assertEquals(ChronicleGoalWrite.UNAVAILABLE, svc.track(actor, guild, ChronicleGoal(null, 0), "bank-1"))
    }
}
