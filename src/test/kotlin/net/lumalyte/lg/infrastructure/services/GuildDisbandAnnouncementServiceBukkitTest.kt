package net.lumalyte.lg.infrastructure.services

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class GuildDisbandAnnouncementServiceBukkitTest {
    private val memberService = mockk<MemberService>()
    private val lang = mockk<LangService>()
    private val toastSender = mockk<ToastSender>()
    private val icon = mockk<ItemStack>()
    private val disbandedGuild = Guild(
        id = UUID.randomUUID(),
        name = "Badgers",
        createdAt = Instant.EPOCH,
    )
    private val allyGuildId = UUID.randomUUID()
    private val enemyGuildId = UUID.randomUUID()
    private val rankId = UUID.randomUUID()
    private val formerId = UUID.randomUUID()
    private val allyPlayer = player(UUID.randomUUID(), true)
    private val enemyPlayer = player(UUID.randomUUID(), true)
    private val sharedPlayer = player(UUID.randomUUID(), true)
    private val offlinePlayer = player(UUID.randomUUID(), false)

    @BeforeEach
    fun setup() {
        clearAllMocks(answers = false)
        every { lang.msg("notification.guild.disband.broadcast", *anyVararg()) } returns
            Component.text("Badgers disbanded")
        every { lang.msg("notification.guild.disband.toast.ally.title", *anyVararg()) } returns
            Component.text("Ally disbanded: Badgers")
        every { lang.msg("notification.guild.disband.toast.ally.description", *anyVararg()) } returns
            Component.text("Alliance ended")
        every { lang.msg("notification.guild.disband.toast.enemy.title", *anyVararg()) } returns
            Component.text("Enemy disbanded: Badgers")
        every { lang.msg("notification.guild.disband.toast.enemy.description", *anyVararg()) } returns
            Component.text("Enemy no longer exists")
        every { lang.msg("notification.guild.disband.toast.fallback", *anyVararg()) } returns
            Component.text("Badgers disbanded")
        every { toastSender.show(any(), any(), any(), any(), any(), any()) } returns true
    }

    @Test
    fun `broadcasts globally and toasts online ally and enemy members`() {
        every { memberService.getGuildMembers(allyGuildId) } returns setOf(
            member(allyPlayer.uniqueId, allyGuildId),
            member(offlinePlayer.uniqueId, allyGuildId),
            member(formerId, allyGuildId),
        )
        every { memberService.getGuildMembers(enemyGuildId) } returns setOf(
            member(enemyPlayer.uniqueId, enemyGuildId),
        )
        val broadcasts = mutableListOf<Component>()
        val service = service(
            players = mapOf(
                allyPlayer.uniqueId to allyPlayer,
                enemyPlayer.uniqueId to enemyPlayer,
                offlinePlayer.uniqueId to offlinePlayer,
            ),
            broadcasts = broadcasts,
        )

        service.announce(
            disbandedGuild,
            setOf(formerId),
            mapOf(allyGuildId to RelationType.ALLY, enemyGuildId to RelationType.ENEMY),
        )

        assertEquals(1, broadcasts.size)
        verify(exactly = 1) {
            toastSender.show(
                allyPlayer, any(), any(), any(), icon, ToastFrame.TASK,
            )
        }
        verify(exactly = 1) {
            toastSender.show(
                enemyPlayer, any(), any(), any(), icon, ToastFrame.CHALLENGE,
            )
        }
        verify(exactly = 0) {
            toastSender.show(
                offlinePlayer, any(), any(), any(), any(), any(),
            )
        }
        verify(exactly = 2) {
            toastSender.show(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `shared recipient is deduplicated and enemy relation wins`() {
        every { memberService.getGuildMembers(allyGuildId) } returns
            setOf(member(sharedPlayer.uniqueId, allyGuildId))
        every { memberService.getGuildMembers(enemyGuildId) } returns
            setOf(member(sharedPlayer.uniqueId, enemyGuildId))
        val service = service(mapOf(sharedPlayer.uniqueId to sharedPlayer))

        service.announce(
            disbandedGuild,
            emptySet(),
            mapOf(allyGuildId to RelationType.ALLY, enemyGuildId to RelationType.ENEMY),
        )

        verify(exactly = 1) {
            toastSender.show(
                sharedPlayer, any(), any(), any(), icon, ToastFrame.CHALLENGE,
            )
        }
        verify(exactly = 1) {
            toastSender.show(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `unrelated relation types do not resolve recipients`() {
        val truceGuildId = UUID.randomUUID()
        val service = service(emptyMap())

        service.announce(
            disbandedGuild,
            emptySet(),
            mapOf(truceGuildId to RelationType.TRUCE),
        )

        verify(exactly = 0) { memberService.getGuildMembers(truceGuildId) }
        verify(exactly = 0) {
            toastSender.show(any(), any(), any(), any(), any(), any())
        }
    }

    private fun service(
        players: Map<UUID, Player>,
        broadcasts: MutableList<Component> = mutableListOf(),
    ) = GuildDisbandAnnouncementServiceBukkit(
        memberService = memberService,
        lang = lang,
        toastSender = toastSender,
        playerLookup = players::get,
        broadcaster = broadcasts::add,
        iconFactory = { icon },
    )

    private fun member(playerId: UUID, guildId: UUID) = Member(
        playerId = playerId,
        guildId = guildId,
        rankId = rankId,
        joinedAt = Instant.EPOCH,
    )

    private fun player(id: UUID, online: Boolean): Player =
        mockk {
            every { uniqueId } returns id
            every { isOnline } returns online
        }
}
