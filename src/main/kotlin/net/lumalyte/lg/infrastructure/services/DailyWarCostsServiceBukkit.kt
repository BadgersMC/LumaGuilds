package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.DailyWarCostsService
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.domain.entities.Guild
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DailyWarCostsServiceBukkit(
    private val warService: WarService,
    private val guildService: GuildService,
    private val configService: ConfigService
) : DailyWarCostsService {

    private val logger = LoggerFactory.getLogger(DailyWarCostsServiceBukkit::class.java)

    // In-memory storage for tracking when costs were last applied
    // In production, this should be persisted to a database
    private val lastCostsApplied = ConcurrentHashMap<UUID, Instant>()

    override fun applyDailyWarCosts(): Int {
        val config = configService.loadConfig()
        val affectedGuilds = mutableSetOf<UUID>()

        try {
            val guildsInWars = getGuildsInActiveWars()

            for (guildId in guildsInWars) {
                val guild = guildService.getGuild(guildId)
                if (guild == null) {
                    logger.warn("Guild $guildId not found, skipping daily war costs")
                    continue
                }

                // Check if costs were already applied today
                if (wereCostsAppliedToday(guildId)) {
                    logger.debug("Daily war costs already applied today for guild ${guild.name}")
                    continue
                }

                // Calculate and apply costs
                val (expCost, moneyCost) = calculateDailyCosts(guild)

                var expDeducted = false
                var moneyDeducted = false

                // Apply EXP cost (if guild has enough)
                if (expCost > 0) {
                    // TODO: Implement EXP deduction from guild
                    // This would require integration with guild leveling system
                    logger.info("Would deduct $expCost EXP from guild ${guild.name}")
                    expDeducted = true // Placeholder
                }

                // Apply money cost (if guild has enough)
                if (moneyCost > 0) {
                    // TODO: Implement money deduction from guild bank
                    // This would require integration with bank system
                    logger.info("Would deduct $moneyCost coins from guild ${guild.name} bank")
                    moneyDeducted = true // Placeholder
                }

                if (expDeducted || moneyDeducted) {
                    affectedGuilds.add(guildId)
                    recordCostsApplied(guildId, Instant.now())

                    logger.info("Applied daily war costs to guild ${guild.name}: ${expCost} EXP, ${moneyCost} coins")
                }
            }

            logger.info("Applied daily war costs to ${affectedGuilds.size} guilds")
            return affectedGuilds.size

        } catch (e: Exception) {
            logger.error("Error applying daily war costs", e)
            return 0
        }
    }

    override fun wereCostsAppliedToday(guildId: UUID): Boolean {
        val lastApplied = lastCostsApplied[guildId]
        if (lastApplied == null) return false

        val lastAppliedDate = lastApplied.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()

        return lastAppliedDate == today
    }

    override fun recordCostsApplied(guildId: UUID, appliedAt: Instant): Boolean {
        return try {
            lastCostsApplied[guildId] = appliedAt
            true
        } catch (e: Exception) {
            logger.error("Error recording daily war costs application", e)
            false
        }
    }

    override fun getLastCostsAppliedTime(guildId: UUID): Instant? {
        return lastCostsApplied[guildId]
    }

    override fun calculateDailyCosts(guild: Guild): Pair<Int, Int> {
        val config = configService.loadConfig()
        val guildConfig = config.guild

        val expCost = guildConfig.dailyWarExpCost
        val moneyCost = guildConfig.dailyWarMoneyCost

        // Could implement scaling based on guild level, war duration, etc.
        // For now, use flat rates from config

        return Pair(expCost, moneyCost)
    }

    override fun getGuildsInActiveWars(): List<UUID> {
        val activeWars = warService.getActiveWars()
        val guildIds = mutableSetOf<UUID>()

        for (war in activeWars) {
            guildIds.add(war.declaringGuildId)
            guildIds.add(war.defendingGuildId)
        }

        return guildIds.toList()
    }
}
