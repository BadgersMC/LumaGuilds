package net.lumalyte.lg.infrastructure.services

import io.mockk.*
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.PlayerNotificationPreferenceRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Member
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildLoginNotificationServiceBukkitTest {
    private val memberService = mockk<MemberService>()
    private val preferences = mockk<PlayerNotificationPreferenceRepository>()
    private val lang = mockk<LangService>()
    private val toastSender = mockk<ToastSender>()
    private val icon = mockk<ItemStack>()
    private val guildId = UUID.randomUUID()
    private val rankId = UUID.randomUUID()
    private val joinerId = UUID.randomUUID()
    private val recipientId = UUID.randomUUID()
    private val optedOutId = UUID.randomUUID()
    private val offlineId = UUID.randomUUID()
    private val joiner = player(joinerId, "BadgersMC", true)
    private val recipient = player(recipientId, "Guildmate", true)
    private val optedOut = player(optedOutId, "Muted", true)
    private val offline = player(offlineId, "Offline", false)
    private var now = 20_000L

    @BeforeEach
    fun setup() {
        clearAllMocks(answers = false)
        every { memberService.getPlayerGuilds(joinerId) } returns setOf(guildId)
        every { memberService.getGuildMembers(guildId) } returns setOf(
            member(joinerId),
            member(recipientId),
            member(optedOutId),
            member(offlineId),
        )
        every {
            preferences.guildLoginStates(setOf(recipientId, optedOutId))
        } returns mapOf(
            recipientId to true,
            optedOutId to false,
        )
        every { lang.msg("notification.guild.member_login.title", *anyVararg()) } returns
            Component.text("BadgersMC is online")
        every { lang.msg("notification.guild.member_login.description") } returns
            Component.text("A guild member joined")
        every { lang.msg("notification.guild.member_login.fallback", *anyVararg()) } returns
            Component.text("BadgersMC is now online")
        every {
            toastSender.show(any(), any(), any(), any(), any(), ToastFrame.TASK)
        } returns true
    }

    @Test
    fun `join toast reaches only online opted-in guildmates`() {
        service().onPlayerJoin(joinerId)

        verify(exactly = 1) {
            toastSender.show(
                recipient,
                any(),
                any(),
                any(),
                icon,
                ToastFrame.TASK,
            )
        }
        verify(exactly = 0) {
            toastSender.show(match { it === joiner || it === optedOut || it === offline }, any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `shared guildmate across memberships receives only one toast`() {
        val secondGuildId = UUID.randomUUID()
        every { memberService.getPlayerGuilds(joinerId) } returns setOf(guildId, secondGuildId)
        every { memberService.getGuildMembers(secondGuildId) } returns setOf(
            member(joinerId, secondGuildId),
            member(recipientId, secondGuildId),
        )

        service().onPlayerJoin(joinerId)

        verify(exactly = 1) {
            toastSender.show(recipient, any(), any(), any(), icon, ToastFrame.TASK)
        }
    }

    @Test
    fun `startup grace suppresses reconnect wave`() {
        now = 10_000L
        service(startupAt = 0L).onPlayerJoin(joinerId)

        verify(exactly = 0) {
            toastSender.show(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `rapid reconnect is announced once until cooldown expires`() {
        val service = service()
        service.onPlayerJoin(joinerId)
        now += 1_000L
        service.onPlayerJoin(joinerId)
        now += GuildLoginNotificationServiceBukkit.RECONNECT_COOLDOWN_MILLIS
        service.onPlayerJoin(joinerId)

        verify(exactly = 2) {
            toastSender.show(recipient, any(), any(), any(), icon, ToastFrame.TASK)
        }
    }

    @Test
    fun `action bar and quiet sound are used when toast cannot be shown`() {
        every {
            toastSender.show(any(), any(), any(), any(), any(), ToastFrame.TASK)
        } returns false
        val location = mockk<Location>()
        every { recipient.location } returns location
        every { recipient.sendActionBar(any<Component>()) } just Runs
        every {
            recipient.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.35f)
        } just Runs

        service().onPlayerJoin(joinerId)

        verify(exactly = 1) { recipient.sendActionBar(any<Component>()) }
        verify(exactly = 1) {
            recipient.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.35f)
        }
    }

    private fun service(startupAt: Long = 0L): GuildLoginNotificationServiceBukkit {
        val players = mapOf(
            joinerId to joiner,
            recipientId to recipient,
            optedOutId to optedOut,
            offlineId to offline,
        )
        return GuildLoginNotificationServiceBukkit(
            memberService = memberService,
            preferences = preferences,
            lang = lang,
            toastSender = toastSender,
            startupAtMillis = startupAt,
            nowMillis = { now },
            playerLookup = players::get,
            headFactory = { icon },
        )
    }
    private fun member(playerId: UUID, memberGuildId: UUID = guildId) = Member(
        playerId = playerId,
        guildId = memberGuildId,
        rankId = rankId,
        joinedAt = Instant.EPOCH,
    )

    private fun player(id: UUID, name: String, online: Boolean): Player =
        mockk<Player> {
            every { uniqueId } returns id
            every { this@mockk.name } returns name
            every { isOnline } returns online
        }
}
