package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.achievements.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.infrastructure.services.GuildAchievementRuntime
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.*

internal class GuildAchievementsMenuTest : RecruitmentDialogFixture() {
    @Test fun `inspector sections stay compact and tracking preserves the selected milestone`() {
        val actor = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Mastery", createdAt = Instant.EPOCH)
        every { player.uniqueId } returns actor
        every { player.isOnline } returns true
        every { guilds.getGuild(guild.id) } returns guild
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        val members = mockk<MemberService>()
        every { members.getMember(actor, guild.id) } returns mockk()
        val runtime = mockk<GuildAchievementRuntime>()
        val nodes = (1..6).map { i -> GuildAchievement("milestone_$i", null, "Milestone $i",
            listOf("Earn milestone $i"), "EVENT", null, 3, 0, emptyList(), "Foundations", "BOOK", "TASK", i.toFloat(), 0f) }
        every { runtime.engine } returns GuildAchievementEngine(nodes)
        var record = AchievementRecord()
        every { runtime.read(player) } answers { record }
        every { runtime.track(player, any()) } answers { record = record.copy(tracked = secondArg()); true }
        loadKoinModules(module { single { runtime }; single { members } })
        val nav = MenuNavigator(player)
        nav.openMenu(GuildAchievementsMenu(nav, player, guild, presenter))
        val plain = PlainTextComponentSerializer.plainText()
        assertEquals(1, renderedPage!!.items.size)
        assertEquals(5, renderedPage!!.diagram.size) // Four actual branch rows plus inspector tabs.
        renderedPage!!.diagram.first()[1].action()
        assertEquals(1, renderedPage!!.items.size)
        assertTrue(plain.serialize(renderedPage!!.items.single().text).contains("Earn milestone 1"))
        renderedPage!!.actions[1].action() // Track first milestone; Next is the first action.
        assertEquals("milestone_1", record.tracked)
        renderedPage!!.diagram.first()[2].action()
        assertEquals(1, renderedPage!!.items.size)
        renderedPage!!.actions.first().action() // Next branch page.
        assertEquals(3, renderedPage!!.diagram.size)
        assertEquals("milestone_1", record.tracked)
    }
}
