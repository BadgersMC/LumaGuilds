package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.config.AdvancementXpConfig
import net.lumalyte.lg.domain.values.AdvancementXpEligibility
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.net.InetAddress
import java.util.UUID

/** REQ-302: reads online players' connections for [AdvancementXpEligibility]. Server thread only. */
internal object AdvancementXpConnections {
    fun check(config: () -> AdvancementXpConfig, members: MemberRepository): (Player, UUID) -> AdvancementXpEligibility.Decision =
        { participant, guildId ->
            val settings = config()
            if (!settings.sharedConnectionCheck) AdvancementXpEligibility.Decision.ELIGIBLE
            else {
                val online = Bukkit.getOnlinePlayers().associateBy({ it.uniqueId }, { address(it) })
                val counts = online.values.filterNotNull().groupingBy { it }.eachCount()
                val guildOnline = members.getByGuild(guildId).map { it.playerId }.filter { it in online }.toSet()
                AdvancementXpEligibility.decide(
                    actor = participant.uniqueId,
                    onlineMembers = guildOnline + participant.uniqueId,
                    address = { id -> if (id == participant.uniqueId) address(participant) else online[id] },
                    playersOn = { counts[it] ?: 0 },
                    gatewayPlayers = settings.gatewayPlayers,
                )
            }
        }

    /**
     * The player's IP as text, or null when it can't identify them: a missing address, or a loopback or
     * wildcard one (a proxy that doesn't forward real addresses). IPv4-mapped IPv6 is folded to IPv4.
     */
    fun address(player: Player): String? = player.address?.address?.let(::canonical)

    internal fun canonical(address: InetAddress): String? {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isMulticastAddress) return null
        val bytes = address.address
        val mapped = bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
        return if (mapped) InetAddress.getByAddress(bytes.copyOfRange(12, 16)).hostAddress else address.hostAddress
    }
}
