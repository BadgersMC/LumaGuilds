package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.rewards.*
import net.lumalyte.lg.interaction.menus.MenuNavigator
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.*

internal class GuildChronicleMenuTest : RecruitmentDialogFixture() {
    @Test fun `new menu instance reads committed goal and branch and range actions stay in dialogs`() {
        val guild = Guild(UUID.randomUUID(), "Chronicle", createdAt = Instant.EPOCH)
        every { guilds.getGuild(guild.id) } returns guild
        val actor = UUID.randomUUID()
        every { player.uniqueId } returns actor
        every { player.isOnline } returns true
        val members = mockk<MemberService>()
        every { members.getMember(actor, guild.id) } returns mockk()
        every { members.hasPermission(actor, guild.id, any()) } returns false
        val repository = object : ChronicleGoalRepository {
            var saved = ChronicleGoal(null, 0)
            override fun read(guildId: UUID) = saved
            override fun compareAndSet(guildId: UUID, expected: ChronicleGoal, rewardId: String?): Boolean {
                if (saved != expected) return false
                saved = ChronicleGoal(rewardId, saved.revision + 1)
                return true
            }
        }
        val catalog = RewardCatalog.chapterTwo()
        val service = GuildChronicleService(catalog, repository, { _, _ -> true }, { _, _ -> true },
            { GuildRewardRead.Available(1, 0, RewardEntitlementResolver(catalog).resolve(1, RewardOwnership())) })
        val config = mockk<ConfigService>()
        every { config.loadConfig() } returns net.lumalyte.lg.config.MainConfig()
        every { lang.msg(any(), *anyVararg()) } answers { net.kyori.adventure.text.Component.text(firstArg<String>()) }
        loadKoinModules(module {
            single { service }; single { members }; single { config }
            single { mockk<GuildRewardPurchaseService>() }
        })
        val nav = MenuNavigator(player)
        nav.openMenu(GuildChronicleMenu(nav, player, guild, presenter))
        assertEquals(4, renderedPage!!.bodyActions.size)
        renderedPage!!.items.first().onClick!!.action()
        renderedPage!!.actions.first().action() // Track; no purchase permission granted.
        assertEquals("bank-1", repository.saved.rewardId)
        nav.openMenu(GuildChronicleMenu(nav, player, guild, presenter))
        assertEquals(4, renderedPage!!.actions.size) // Later, tracked goal, automatic benefits, prestige details.
        renderedPage!!.actions.first().action()
        assertEquals(5, renderedPage!!.actions.size) // Earlier and later are both present.
        renderedPage!!.bodyActions[2].action() // Travel, same range and saved goal.
        assertEquals("bank-1", repository.saved.rewardId)
        renderedPage!!.actions[2].action() // Tracked goal, despite selected branch/range.
        assertEquals(1, renderedPage!!.actions.size) // Untrack only, no purchase authority.
        renderedPage!!.actions.first().action()
        assertNull(repository.saved.rewardId)
        var returns = 0
        every { guilds.getGuild(guild.id) } returns guild.copy(guiTheme = net.lumalyte.lg.utils.GuiTheme.FROSTBOUND)
        GuildChronicleMenu(nav, player, guild, presenter, initialRewardId = "bank-1",
            returnToBrowser = { returns++ }).open()
        assertEquals(net.lumalyte.lg.utils.GuiTheme.FROSTBOUND, renderedPage!!.theme)
        assertEquals(1, renderedPage!!.actions.size) // Track only; viewing grants no purchase permission.
        renderedPage!!.back.action()
        assertEquals(1, returns)
    }
}
