package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.audience.Audience
import org.bukkit.entity.Player
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class GuildDialogCallbacksTest {
    private val owner = UUID.randomUUID()
    private val player = mockk<Player>(relaxed = true) { every { uniqueId } returns owner }
    private val queue = mutableListOf<() -> Unit>()
    private var valid = true
    private var time = 1L
    private var failures = 0
    private val state = GuildDialogSession(owner, 100L) { valid }
    private val revision = state.beginPage(0L)
    private val callbacks = GuildDialogCallbacks(player, state, revision, { failures++ }, { queue += it }, { time })

    @Test
    fun schedulesBeforeCheckingAndConsumesOnlyOnceAcrossReplays() {
        var actions = 0
        repeat(2) { callbacks.submit(player) { actions++ } }
        assertEquals(0, actions)
        queue.forEach { it() }
        assertEquals(1, actions)
    }

    @Test
    fun foreignAudienceCannotScheduleOrConsumeOwnerPage() {
        val foreign = mockk<Player> { every { uniqueId } returns UUID.randomUUID() }
        callbacks.submit(foreign) { error("Foreign action") }
        callbacks.submit(Audience.empty()) { error("Non-player action") }
        assertEquals(0, queue.size)
        var actions = 0
        callbacks.submit(player) { actions++ }
        queue.single()()
        assertEquals(1, actions)
    }

    @Test
    fun rechecksAuthorizationWhenScheduledWorkRuns() {
        callbacks.submit(player) { error("Revoked action") }
        valid = false
        queue.single()()
        assertEquals(0, failures)
    }

    @Test
    fun replacedAndExpiredPagesCannotExecute() {
        callbacks.submit(player) { error("Old page") }
        state.beginPage(1L)
        queue.single()()
        queue.clear()
        val expired = GuildDialogCallbacks(player, state, 2, { failures++ }, { queue += it }, { 101L })
        expired.submit(player) { error("Expired page") }
        queue.single()()
        assertEquals(0, failures)
    }

    @Test
    fun failureClosesAndCannotReplay() {
        callbacks.submit(player) { error("Adapter failure") }
        callbacks.submit(player) { error("Replay") }
        queue.forEach { it() }
        assertEquals(1, failures)
        verify(exactly = 1) { player.closeDialog() }
    }
}
