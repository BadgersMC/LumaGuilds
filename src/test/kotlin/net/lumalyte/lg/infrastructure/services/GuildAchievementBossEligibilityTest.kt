package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.junit.jupiter.api.Test
import java.util.UUID

internal class GuildAchievementBossEligibilityTest {
    @Test
    fun dragonRequiresEndAndThreeMembersInThatWorld() {
        val runtime = mockk<GuildAchievementRuntime>(relaxed = true)
        val killer = mockk<Player>()
        val guild = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val bossId = UUID.randomUUID()
        val world = mockk<World>()
        val otherWorld = mockk<World>()
        val boss = mockk<LivingEntity>()
        every { killer.gameMode } returns GameMode.SURVIVAL
        every { killer.uniqueId } returns playerId
        every { runtime.guild(playerId) } returns guild
        every { boss.killer } returns killer
        every { boss.world } returns world
        every { boss.type } returns EntityType.ENDER_DRAGON
        every { boss.uniqueId } returns bossId
        val members = List(3) { mockk<Player>() }
        members.forEach { every { it.world } returns world }
        every { runtime.online(guild) } returns members
        val event = mockk<EntityDeathEvent>()
        every { event.entity } returns boss
        val listener = GuildAchievementListener(runtime)

        every { world.environment } returns World.Environment.NORMAL
        listener.death(event)
        verify(exactly = 0) { runtime.signal(any(), any(), any(), any(), any(), any()) }

        every { world.environment } returns World.Environment.THE_END
        every { members.last().world } returns otherWorld
        listener.death(event)
        verify(exactly = 0) { runtime.signal(any(), any(), any(), any(), any(), any()) }

        every { members.last().world } returns world
        listener.death(event)
        members.forEach { member ->
            verify(exactly = 1) {
                runtime.signal(member, "GUILD_COLLECTIVE_BOSS_KILL", "ENDER_DRAGON", receipt = "boss:$bossId")
            }
        }
    }
}
