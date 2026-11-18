package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.domain.values.MemberPlaytimeLedger
import net.lumalyte.lg.domain.values.MemberPlaytimeStore
import org.bukkit.Bukkit
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * REQ-300: guild combined playtime from stored per-member values. Reading an offline player's
 * statistics loads and decodes their stats file on the server thread, so those reads only happen in a
 * small, time-boxed backfill (each player once, then never again while they stay offline). Online
 * players are read live; a player's value is stored when they join and when they leave.
 *
 * Every method runs on the server thread.
 */
internal class GuildPlaytime(
    private val plugin: JavaPlugin,
    private val members: MemberRepository,
    private val store: MemberPlaytimeStore,
    private val budgetNanos: Long = 2_000_000L,
    private val maxReadsPerRun: Int = 4,
    private val readOffline: (UUID) -> Long = { Bukkit.getOfflinePlayer(it).getStatistic(Statistic.PLAY_ONE_MINUTE).toLong() },
) {
    private val ledger by lazy { MemberPlaytimeLedger(store) }
    private var backfill: BukkitTask? = null

    fun start() {
        Bukkit.getOnlinePlayers().forEach { record(it) }
        ledger.enqueue(members.getAll().map { it.playerId }.distinct())
        val queued = ledger.pending
        if (queued > 0) plugin.logger.info("Reading stored playtime for $queued guild member(s) in the background")
        val started = System.nanoTime()
        var reads = 0
        backfill = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (ledger.pending == 0) return@Runnable
            reads += ledger.drain({ id -> live(id) ?: readOffline(id) }, budgetNanos, maxReadsPerRun)
            if (ledger.pending == 0) plugin.logger.info(
                "Stored playtime ready: read $reads member(s) in ${(System.nanoTime() - started) / 1_000_000_000L}s")
        }, 40L, 2L)
    }

    fun stop() {
        backfill?.cancel()
        backfill = null
        Bukkit.getOnlinePlayers().forEach { record(it) }
        (store as? net.lumalyte.lg.infrastructure.persistence.guilds.MemberPlaytimeStoreSQL)?.close()
    }

    /** Stores [player]'s current playtime (call on join and on quit). */
    fun record(player: Player) = ledger.record(player.uniqueId, ticks(player))

    /** Each member's play ticks, or null until every member's value is known. */
    fun memberTicks(guildId: UUID): List<Long>? = ledger.ticks(members.getByGuild(guildId).map { it.playerId }, ::live)

    private fun live(id: UUID): Long? = Bukkit.getPlayer(id)?.let(::ticks)

    private fun ticks(player: Player): Long = player.getStatistic(Statistic.PLAY_ONE_MINUTE).toLong()
}
