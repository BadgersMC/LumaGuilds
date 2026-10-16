package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.GuildRecruitmentListings
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import net.lumalyte.lg.utils.GuiTheme
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class GuildPresentationDialogTest : RecruitmentDialogFixture() {
    @Test
    fun previewIsTemporaryAndApplyRechecksHolidayUnlockAndPermission() {
        val id = UUID.randomUUID()
        val guild = Guild(id, "Frames", createdAt = Instant.EPOCH)
        val listings = mockk<GuildRecruitmentListings>()
        val service = mockk<GuildRecruitmentService>()
        every { service.listings } returns listings
        every { listings.profile(guild) } returns RecruitmentProfile.legacy(id, false)
        every { guilds.hasPermission(any(), id, RankPermission.MANAGE_GUILD_SETTINGS) } returns true
        every { player.isOnline } returns true
        var holidayUnlocked = true
        val dialog =
            GuildPresentationSettingsDialog(
                player, { guild }, guilds, service, lang,
                { true }, { !it.requiresUnlock || holidayUnlocked }, {}, {}, presenter,
            )
        every { lang.msg(any(), *anyVararg()) } answers { net.kyori.adventure.text.Component.text(firstArg<String>()) }
        dialog.open()
        renderedPage!!.actions.first().action()
        val christmas = GuiTheme.SELECTABLE.indexOf(GuiTheme.CHRISTMAS)
        renderedPage!!.items[christmas].onClick!!.action()
        assertEquals(GuiTheme.CHRISTMAS, renderedPage!!.theme)
        verify(exactly = 0) { guilds.setGuiTheme(any(), any(), any()) }
        holidayUnlocked = false
        assertFailsWith<IllegalStateException> { renderedPage!!.actions.single().action() }
        verify(exactly = 0) { guilds.setGuiTheme(any(), any(), any()) }
        holidayUnlocked = true
        every { guilds.setGuiTheme(id, GuiTheme.CHRISTMAS, any()) } returns true
        renderedPage!!.actions.single().action()
        verify(exactly = 1) { guilds.setGuiTheme(id, GuiTheme.CHRISTMAS, any()) }
    }

    @Test
    fun presentationWritesReauthorizeAndCannotChangeRecruitmentMechanics() {
        val id = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val guild = Guild(id, "Settings", createdAt = Instant.EPOCH)
        val directory = mockk<RecruitmentDirectoryRepository>(relaxed = true)
        val members = mockk<MemberService>()
        val current = RecruitmentProfile.legacy(id, true)
        every { guilds.getGuild(id) } returns guild
        every { directory.profile(id) } returns current
        var allowed = true
        every { guilds.hasPermission(actor, id, RankPermission.MANAGE_GUILD_SETTINGS) } answers { allowed }
        val listings = GuildRecruitmentListings(directory, guilds, members)
        listings.savePresentation(actor, id) { it.copy(showBanner = false) }
        verify(exactly = 1) { directory.savePresentation(current.copy(showBanner = false), setOf("showBanner")) }
        allowed = false
        assertFailsWith<IllegalArgumentException> {
            listings.savePresentation(
                actor,
                id,
            ) { it.copy(publicListing = false) }
        }
        allowed = true
        assertFailsWith<IllegalArgumentException> {
            listings.savePresentation(actor, id) { it.copy(pitch = "Not a preference") }
        }
        every { guilds.getAllGuilds() } returns setOf(guild)
        every { directory.profile(id) } returns current.copy(publicListing = false)
        assertTrue(listings.browse().isEmpty())
    }
}
