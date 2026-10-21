package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.*

internal class GuildPrestigeInformationTest : RecruitmentDialogFixture() {
    @Test fun `locked prestige still explains milestones and marks retained perks separately from purchased homes`() {
        val prestige = mockk<GuildPrestigeService>()
        val purchases = mockk<GuildRewardPurchaseService>()
        val guild = Guild(UUID.randomUUID(), "Legacy", createdAt = Instant.EPOCH)
        val actor = UUID.randomUUID()
        val members = mockk<MemberService>()
        every { player.uniqueId } returns actor
        every { player.isOnline } returns true
        every { lang.msg(any(), *anyVararg()) } answers { net.kyori.adventure.text.Component.text(firstArg<String>()) }
        every { guilds.getGuild(guild.id) } returns guild
        every { members.getMember(actor, guild.id) } returns mockk()
        val catalog = RewardCatalog.chapterTwo()
        val owners = RewardOwnership(permanent = setOf("bank-1", "home-1"), prestigeCount = 1)
        val goals = mockk<ChronicleGoalRepository>()
        every { goals.read(guild.id) } returns ChronicleGoal(null, 0)
        val service = GuildChronicleService(catalog, goals, { _, _ -> true }, { _, _ -> false }, {
            GuildRewardRead.Available(26, 2, RewardEntitlementResolver(catalog).resolve(26, owners))
        })
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns net.lumalyte.lg.config.MainConfig()
        every { prestige.overview(guild.id) } returns PrestigeOverview(true, 26, 1, 6, 20000, emptyList())
        loadKoinModules(module { single { service }; single { members }; single { config }; single { prestige }; single { purchases } })
        var reviews = 0
        val nav = MenuNavigator(player)
        nav.openMenu(GuildChronicleMenu(nav, player, guild, presenter, initialPrestige = true, reviewPrestige = { reviews++ }))
        val plain = PlainTextComponentSerializer.plainText()
        renderedPage!!.bodyActions[1].action()
        assertEquals(6, renderedPage!!.items.count { plain.serialize(it.text).contains("chronicle.prestige.milestone") })
        renderedPage!!.bodyActions[2].action()
        val view = renderedPage!!
        val retained = view.items.filter { plain.serialize(it.text).contains("chronicle.prestige.retained") }
        assertEquals(1, retained.size)
        assertTrue(plain.serialize(retained.single().text).contains("Bank Extension 1"))
        assertFalse(view.actions.any { plain.serialize(it.label).contains("chronicle.prestige.review") })
        assertEquals(0, reviews)
        verify(exactly = 0) { prestige.quote(any(), any(), any()) }
        verify(exactly = 0) { prestige.confirm(any(), any()) }
        verify(exactly = 0) { purchases.quote(any(), any(), any()) }
    }
}
