package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

internal class RecruitmentBrowseCardTest : RecruitmentDialogFixture() {
    @Test fun `card wrapping preserves words and handles long Unicode names`() {
        val lines = wrapRecruitmentText("Building, PVPing, Base Hunting, Griefing - 2 spaces - 0 online")
        assertTrue(lines.joinToString(" ").contains("0 online"))
        assertTrue(lines.all { it.codePointCount(0, it.length) <= 30 })
        assertTrue(wrapRecruitmentText("😀".repeat(31)).map { it.codePointCount(0, it.length) } == listOf(30, 1))
    }

    @Test fun `currency labels are readable while custom names are preserved`() {
        assertTrue(recruitmentCurrencyLabel("RAW_GOLD") == "raw gold")
        assertTrue(recruitmentCurrencyLabel("Guild Crowns") == "Guild Crowns")
    }
    @Test
    fun summaryDoesNotCountHiddenMembersOrMisreportUnavailableFeeAsFree() {
        val guild =
            Guild(
                UUID.randomUUID(),
                "Everlights",
                createdAt = Instant.EPOCH,
                joinFeeEnabled = true,
                joinFeeAmount = 100,
            )
        val service = mockk<GuildRecruitmentService>(relaxed = true)
        val members = mockk<MemberService>()
        val lfg = mockk<LfgService>()
        loadKoinModules(
            module {
                single { service }
                single { members }
                single { lfg }
            },
        )
        every { service.listings.profile(guild) } returns RecruitmentProfile(guild.id, RecruitmentMode.IN_GAME)
        every { service.listings.spaces(guild.id) } returns 6
        val visible = requireNotNull(MockBukkit.getMock()).addPlayer()
        val hidden = requireNotNull(MockBukkit.getMock()).addPlayer()
        every { player.canSee(visible) } returns true
        every { player.canSee(hidden) } returns false
        every { members.getGuildMembers(guild.id) } returns
            setOf(visible, hidden).map {
                Member(it.uniqueId, guild.id, UUID.randomUUID(), Instant.EPOCH)
            }.toSet()
        every { lfg.getJoinRequirement(guild) } returns null
        every { lang.raw("guild_dialogs.board.summary") } returns "%activity% - %spaces% spaces - %online% online"
        val text =
            controller.browseCardSummary(guild).joinToString(" ") {
                PlainTextComponentSerializer.plainText().serialize(it)
            }
        assertTrue(text.contains("6 spaces - 1 online"))
        assertTrue(text.contains("recruitment.fee_unavailable"))
        assertTrue(!text.contains("guild_dialogs.board.no_fee"))
    }
}
