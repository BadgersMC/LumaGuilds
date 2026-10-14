package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.persistence.GuildRecruitmentRepository
import net.lumalyte.lg.application.persistence.RecruitmentDirectoryRepository
import net.lumalyte.lg.application.services.GuildRecruitmentService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.LfgService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentPolicy
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val MEMBER_CAPACITY = 10
private const val ORIGINAL_PITCH = "Existing listing"

/** Exercises screen actions against the real permission-checking application service. */
internal class RecruitmentControllerFlowTest {
    private val actor = UUID.randomUUID()
    private val guild = Guild(UUID.randomUUID(), "Builders", createdAt = Instant.EPOCH)
    private val guilds = mockk<GuildService>(relaxed = true)
    private val members = mockk<MemberService>(relaxed = true)
    private val directory = mockk<RecruitmentDirectoryRepository>(relaxed = true)
    private val applications = mockk<GuildRecruitmentRepository>(relaxed = true)
    private val lfg = mockk<LfgService>(relaxed = true)
    private val lang = mockk<LangService>(relaxed = true)
    private val player = mockk<Player>(relaxed = true)
    private var allowed = true
    private var published = RecruitmentProfile(guild.id, RecruitmentMode.IN_GAME, ORIGINAL_PITCH)
    private lateinit var controller: RecruitmentController

    @BeforeEach
    fun prepare() {
        every { player.uniqueId } returns actor
        every { guilds.getGuild(guild.id) } returns guild
        every { guilds.getPlayerGuilds(actor) } returns setOf(guild)
        every { members.hasPermission(actor, guild.id, any()) } answers { allowed }
        every { directory.profile(guild.id) } answers { published }
        every { directory.save(any()) } answers {
            published = firstArg()
            Unit
        }
        every { lang.raw(any()) } answers { firstArg() }
        val service =
            GuildRecruitmentService(
                applications,
                directory,
                guilds,
                members,
                lfg,
                { RecruitmentPolicy() },
                { _, _ -> true },
            )
        startKoin {
            modules(
                module {
                    single { service }
                    single { guilds }
                    single { members }
                    single { directory }
                    single { lfg }
                    single { lang }
                },
            )
        }
        controller = RecruitmentController(player, {})
    }

    @AfterEach
    fun release() {
        stopKoin()
    }

    private fun completeDraft(): List<RecruitmentButton> {
        controller.view().items.single { it.label == "recruitment.setup" }.action()
        controller.view().items.single { it.label == "recruitment.mode.apply" }.action()
        listOf("Welcome", "Be respectful", "Building", "Evenings", "English").forEach(controller::acceptInput)
        return controller.view().items
    }

    @Test
    fun discardPreservesListing() {
        val preview = completeDraft()
        verify(exactly = 0) { directory.save(any()) }
        assertEquals(ORIGINAL_PITCH, published.pitch)
        preview.single { it.label == "recruitment.discard" }.action()
        assertEquals(ORIGINAL_PITCH, published.pitch)
        verify(exactly = 0) { directory.save(any()) }
    }

    @Test
    fun draftRequiresPublication() {
        val preview = completeDraft()
        preview.single { it.label == "recruitment.publish" }.action()
        assertEquals("Welcome", published.pitch)
        assertEquals(RecruitmentMode.IN_GAME, published.mode)
        verify(exactly = 1) { directory.save(any()) }
        verify(exactly = 0) { lfg.joinGuild(any(), any()) }
    }

    @Test
    fun revokedEditorCannotPublish() {
        val preview = completeDraft()
        allowed = false
        assertFailsWith<IllegalArgumentException> { preview.single { it.label == "recruitment.publish" }.action() }
        assertEquals(ORIGINAL_PITCH, published.pitch)
        verify(exactly = 0) { directory.save(any()) }
    }

    @Test
    fun hiddenRecruiterStaysPrivate() {
        val hiddenGuild = Guild(UUID.randomUUID(), "Z Hidden", createdAt = Instant.EPOCH)
        val hiddenId = UUID.randomUUID()
        val hidden = mockk<Player>(relaxed = true)
        every { hidden.isOnline } returns true
        every { player.canSee(hidden) } returns false
        every { guilds.getAllGuilds() } returns setOf(guild, hiddenGuild)
        every { directory.profile(hiddenGuild.id) } returns RecruitmentProfile(hiddenGuild.id, RecruitmentMode.IN_GAME)
        every { members.getMemberLimit(any()) } returns MEMBER_CAPACITY
        every { members.getGuildMembers(guild.id) } returns emptySet()
        every { members.getGuildMembers(hiddenGuild.id) } returns
            setOf(
                Member(hiddenId, hiddenGuild.id, UUID.randomUUID(), Instant.EPOCH),
            )
        every { members.hasPermission(hiddenId, hiddenGuild.id, RankPermission.MANAGE_RECRUITMENT) } returns true
        mockkStatic(Bukkit::class)
        try {
            every { Bukkit.getPlayer(hiddenId) } returns hidden
            assertEquals(listOf("Builders", "Z Hidden"), controller.browse().map { it.label })
        } finally {
            unmockkStatic(Bukkit::class)
        }
    }
}
