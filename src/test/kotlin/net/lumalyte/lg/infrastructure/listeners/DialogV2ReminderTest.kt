package net.lumalyte.lg.infrastructure.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.GuildRecruitmentListings
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.QuestService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.WeeklyQuestSet
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal class DialogV2ReminderTest {
    @Test
    fun remindersRequireOptInPermissionAndFinalHourAndConsumeOnlyOneReceipt() {
        val id = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val guild = Guild(id, "Reminders", createdAt = Instant.EPOCH)
        val player = mockk<Player>(relaxed = true)
        val directory = mockk<RecruitmentDirectoryRepository>()
        val recruitment = mockk<GuildRecruitmentService>()
        val listings = mockk<GuildRecruitmentListings>()
        val members = mockk<MemberService>()
        val guilds = mockk<GuildService>()
        val quests = mockk<QuestService>()
        val week = mockk<WeeklyQuestSet>()
        val lang = mockk<LangService>()
        var enabled = false
        var permitted = true
        var remaining = Duration.ofHours(2)
        every { player.uniqueId } returns actor
        every { player.hasPermission("lumaguilds.guild.quests") } answers { permitted }
        every { members.getPlayerGuilds(actor) } returns setOf(id)
        every { guilds.getGuild(id) } returns guild
        every { recruitment.listings } returns listings
        every { listings.profile(guild) } answers {
            RecruitmentProfile.legacy(id, false).copy(questReminders = enabled)
        }
        every { quests.activeQuestSet() } returns week
        every { week.weekId } returns "week"
        every { quests.timeRemaining(any()) } answers { remaining }
        every { lang.msg(any(), *anyVararg()) } returns Component.text("Reminder")
        var consumed = false
        every { directory.consumeQuestReminder(actor, id, "week") } answers { (!consumed).also { consumed = true } }
        val listener =
            GuildRecruitmentListener(
                mockk(), recruitment, mockk(), mockk(), mockk(),
                directory, members, guilds, quests, lang,
            )
        listener.remindQuests(player)
        enabled = true
        listener.remindQuests(player)
        permitted = false
        remaining = Duration.ofMinutes(30)
        listener.remindQuests(player)
        verify(exactly = 0) { directory.consumeQuestReminder(any(), any(), any()) }
        permitted = true
        listener.remindQuests(player)
        listener.remindQuests(player)
        verify(exactly = 1) { player.sendMessage(any<Component>()) }
    }
}
