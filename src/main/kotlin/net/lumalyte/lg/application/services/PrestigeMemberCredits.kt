package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.PrestigeCreditLedger
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

/** Operator settings for REQ-154. Disabled unless configured. */
data class PrestigeMemberCreditSettings(
    val enabled: Boolean = false,
    val minTenure: Duration = Duration.ofHours(24),
    val commands: List<String> = emptyList(),
    val firstReachCommands: List<String> = emptyList(),
)

/** Runs one console command; the platform adapter owns threading. */
fun interface PrestigeCreditDispatcher {
    fun dispatch(command: String)
}

/**
 * Credits online guild members for their guild's applied prestiges (REQ-154).
 * Eligibility is re-derived from prestige history and membership on every call,
 * and the ledger is written only after a prestige's commands all dispatched.
 */
class PrestigeMemberCredits(
    private val members: MemberRepository,
    private val history: PrestigeHistory,
    private val ledger: PrestigeCreditLedger,
    private val settings: () -> PrestigeMemberCreditSettings,
    private val clock: () -> Instant = Instant::now,
    private val dispatcher: PrestigeCreditDispatcher,
) {
    private val logger = LoggerFactory.getLogger(PrestigeMemberCredits::class.java)

    /** Credit one online player for everything they are owed. */
    fun deliver(playerId: UUID, playerName: String) {
        val current = settings()
        if (!current.enabled) return
        val delivered = ledger.delivered(playerId)
        for (guildId in members.getGuildsByPlayer(playerId)) {
            val member = members.getByPlayerAndGuild(playerId, guildId) ?: continue
            val earliest = member.joinedAt.plus(current.minTenure)
            val owed = history.history(guildId).filter { it.transactionId !in delivered && !it.appliedAt.isBefore(earliest) }
            for (record in owed) credit(playerId, playerName, record, current)
        }
    }

    /** After a guild prestiges, credit every member who is online now; [nameOf] returns null when offline. */
    fun deliverToOnlineMembers(guildId: UUID, nameOf: (UUID) -> String?) {
        if (!settings().enabled) return
        for (member in members.getByGuild(guildId)) {
            val name = nameOf(member.playerId) ?: continue
            deliver(member.playerId, name)
        }
    }

    private fun credit(playerId: UUID, playerName: String, record: PrestigeRecord, current: PrestigeMemberCreditSettings) {
        val first = history.firstToReach(record.tier)?.transactionId == record.transactionId
        val commands = current.commands + if (first) current.firstReachCommands else emptyList()
        try {
            for (command in commands) {
                dispatcher.dispatch(
                    command.replace("{uuid}", playerId.toString())
                        .replace("{player}", playerName)
                        .replace("{tier}", record.tier.toString())
                )
            }
        } catch (error: Exception) {
            logger.warn("Prestige credit for $playerId and ${record.transactionId} failed; it will retry on their next join", error)
            return
        }
        ledger.record(playerId, record.transactionId, clock())
    }
}
