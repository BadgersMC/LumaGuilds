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
    private fun openPersonalTree(available: Boolean): MenuNavigator {
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        val actor = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Mastery", createdAt = Instant.EPOCH)
        every { player.uniqueId } returns actor
        every { player.isOnline } returns true
        every { guilds.getGuild(guild.id) } returns guild
        val members = mockk<MemberService>()
        every { members.getMember(actor, guild.id) } returns mockk()
        val runtime = mockk<GuildAchievementRuntime>()
        every { runtime.engine } returns GuildAchievementEngine(listOf(GuildAchievement(
            "one", null, "A complete milestone title that must remain visible", emptyList(),
            "EVENT", null, 1, 0, emptyList(), "Foundations", "BOOK", "TASK", 0f, 0f,
        )))
        every { runtime.read(player) } returns AchievementRecord()
        every { runtime.projectionAvailable() } returns available
        loadKoinModules(module { single { members }; single { runtime } })
        val observedPresenter = net.lumalyte.lg.infrastructure.services.GuildDialogPresenter { p, page, valid, failure ->
            net.lumalyte.lg.infrastructure.services.GuildDialogTransitions.shown(p)
            presenter.show(p, page, valid, failure)
        }
        return MenuNavigator(player).also { it.openMenu(GuildAchievementsMenu(it, player, guild, observedPresenter)) }
    }

    @Test fun `available native tree handoff closes dialog and invalidates old callbacks`() {
        val nav = openPersonalTree(true)
        val token = nav.currentNavigationToken()
        renderedPage!!.actions.last().action()
        val message = io.mockk.slot<Component>()
        io.mockk.verify(exactly = 1) { player.closeDialog() }
        io.mockk.verify(exactly = 1) { player.sendMessage(capture(message)) }
        fun hasBinding(component: Component): Boolean =
            component is net.kyori.adventure.text.KeybindComponent && component.keybind() == "key.advancements" ||
                component.children().any(::hasBinding)
        assertTrue(hasBinding(message.captured))
        assertFalse(nav.isNavigationCurrent(token))
    }

    @Test fun `unavailable tree keeps authoritative dialog and its callbacks available`() {
        val nav = openPersonalTree(false)
        val token = nav.currentNavigationToken()
        val original = renderedPage
        net.lumalyte.lg.infrastructure.services.GuildDialogTransitions.dispatch(player, renderedPage!!.actions.last().action)
        io.mockk.verify(exactly = 0) { player.closeDialog() }
        assertTrue(nav.isNavigationCurrent(token))
        assertNotSame(original, renderedPage)
    }

    @Test fun `branch links retain complete milestone titles instead of hardcoded ellipsis`() {
        openPersonalTree(false)
        val text = PlainTextComponentSerializer.plainText().serialize(renderedPage!!.diagram[1].last().label)
        assertTrue(text.contains("A complete milestone title that must remain visible"))
    }

    @Test fun `guild badges page independently and return to personal tree without reward writes`() {
        val actor = UUID.randomUUID()
        val guild = Guild(UUID.randomUUID(), "Mastery", createdAt = Instant.EPOCH)
        every { player.uniqueId } returns actor
        every { player.isOnline } returns true
        every { guilds.getGuild(guild.id) } returns guild
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
        val members = mockk<MemberService>()
        every { members.getMember(actor, guild.id) } returns mockk()
        val kills = mockk<net.lumalyte.lg.application.services.KillService>()
        every { kills.getGuildKillStats(guild.id) } returns net.lumalyte.lg.domain.entities.GuildKillStats(guild.id, 1)
        val wars = mockk<net.lumalyte.lg.application.services.WarService>()
        every { wars.getWarHistory(guild.id, 100) } returns emptyList()
        val runtime = mockk<GuildAchievementRuntime>()
        every { runtime.engine } returns GuildAchievementEngine(listOf(GuildAchievement("one", null, "Personal", emptyList(),
            "EVENT", null, 1, 0, emptyList(), "Foundations", "BOOK", "TASK", 0f, 0f)))
        every { runtime.read(player) } returns AchievementRecord()
        loadKoinModules(module { single { members }; single { kills }; single { wars }; single { runtime } })
        val nav = MenuNavigator(player)
        nav.openMenu(GuildAchievementsMenu(nav, player, guild, presenter, guildMilestones = true))
        val plain = PlainTextComponentSerializer.plainText()
        assertEquals(5, renderedPage!!.items.size)
        assertTrue(plain.serialize(renderedPage!!.items.first().text).contains("First Blood"))
        renderedPage!!.actions.first().action()
        assertTrue(plain.serialize(renderedPage!!.items.first().text).contains("Champion"))
        renderedPage!!.actions.last().action()
        assertEquals(1, renderedPage!!.items.size)
        assertTrue(plain.serialize(renderedPage!!.items.first().text).contains("Personal"))
        io.mockk.verify(exactly = 0) { runtime.track(any(), any()) }
    }

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
