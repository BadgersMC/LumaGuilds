package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.WarService
import net.lumalyte.lg.application.services.WarNotificationService
import net.lumalyte.lg.application.services.ChapterTwoGuildAwardService
import net.lumalyte.lg.application.services.ProgressionService
import net.lumalyte.lg.domain.values.ExperienceSource
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.api.events.GuildWarDeclaredEvent
import net.lumalyte.lg.api.events.GuildWarEndEvent
import org.bukkit.Bukkit
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WarServiceBukkit(
    private val configService: ConfigService,
    private val bankService: net.lumalyte.lg.application.services.BankService,
    private val progressionRepository: ProgressionRepository,
    private val progressionConfigService: ProgressionConfigService,
    private val chapterTwoGuildAwardService: ChapterTwoGuildAwardService? = null,
    private val progressionService: ProgressionService,
    private val warRepository: net.lumalyte.lg.application.persistence.WarRepository,
    private val warPayments: net.lumalyte.lg.application.services.WarPaymentService,
    private val memberService: MemberService,
    private val guildRepository: GuildRepository,
    private val seasonalElo: SeasonalEloCoordinator? = null,
    private val warNotifications: WarNotificationService? = null,
    private val memberRepository: net.lumalyte.lg.application.persistence.MemberRepository? = null,
) : WarService {

    private val logger = LoggerFactory.getLogger(WarServiceBukkit::class.java)

    // SQL is authoritative. These read-only views retain existing query semantics, never mutable caches.
    private val wars: Map<UUID, War> get() = warRepository.getAll().mapNotNull { it.war }.associateBy { it.id }
    private val warDeclarations: Map<UUID, WarDeclaration> get() = warRepository.getAll()
        .mapNotNull { it.declaration }.filter { !it.accepted && !it.rejected }.associateBy { it.id }

    init {
        warRepository.getAll() // Corrupt/unavailable persistence must fail initialization.
        require(warNotifications == null || memberRepository != null) {
            "War notification publishing requires a member repository for durable recipient snapshots"
        }
    }

    private fun persist(record: DurableWarRecord) {
        check(warRepository.save(record)) { "War state changed concurrently: ${record.id}" }
    }

    private fun snapshotNotificationRecipients(guildId: UUID): Set<UUID> =
        memberRepository?.getByGuild(guildId)?.mapTo(linkedSetOf()) { it.playerId } ?: emptySet()

    private fun saveWar(war: War, expectResolutionNotification: Boolean = false) {
        val current = warRepository.get(war.id)
        var recipients = current?.notificationRecipients ?: WarNotificationRecipients()
        if (expectResolutionNotification) {
            val winner = requireNotNull(war.winner)
            val loser = requireNotNull(war.loser)
            recipients = recipients.copy(
                victory = snapshotNotificationRecipients(winner),
                defeat = snapshotNotificationRecipients(loser),
            )
        }
        val updated = current?.copy(
            war = war,
            notificationRecipients = recipients,
            resolutionNotificationExpected =
                current.resolutionNotificationExpected || expectResolutionNotification,
        ) ?: DurableWarRecord(
            war.id,
            war = war,
            notificationRecipients = recipients,
            resolutionNotificationExpected = expectResolutionNotification,
        )
        persist(updated)
    }

    private fun saveDeclaration(declaration: WarDeclaration) {
        persist(DurableWarRecord(
            declaration.id,
            declaration = declaration,
            notificationRecipients = WarNotificationRecipients(
                declarationSent = snapshotNotificationRecipients(declaration.declaringGuildId),
                declarationReceived = snapshotNotificationRecipients(declaration.defendingGuildId),
            ),
            declarationNotificationExpected = true,
        ))
    }

    private fun removeDeclaration(id: UUID): WarDeclaration? {
        val record = warRepository.get(id) ?: return null
        val declaration = record.declaration?.takeIf { !it.accepted && !it.rejected } ?: return null
        if (record.paymentPhase in setOf(WarPaymentPhase.FUNDING, WarPaymentPhase.REFUNDING, WarPaymentPhase.REVIEW)) return null
        val accepted = record.war?.isActive == true
        persist(record.copy(declaration = declaration.copy(accepted = accepted, rejected = !accepted)))
        return declaration
    }

    private fun saveStats(stats: WarStats) {
        persist(requireNotNull(warRepository.get(stats.warId)).copy(stats = stats))
    }

    // Tracks per-killer kill timestamps per victim, for anti-farming enforcement
    // (REQ-008: kill_cooldown_minutes + same_player_kill_limit).
    private val killTracking = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, MutableList<Instant>>>()
    private val peaceAgreements = ConcurrentHashMap<UUID, PeaceAgreement>()

    private fun declarationCooldownEnd(
        guildId: UUID,
        snapshot: Collection<DurableWarRecord>,
        cooldownHours: Int,
    ): Instant? = snapshot.asSequence()
        .mapNotNull { it.declaration }
        .filter { it.declaringGuildId == guildId }
        .map {
            it.declarationCooldownUntil
                ?: it.declaredAt.plus(Duration.ofHours(cooldownHours.coerceAtLeast(0).toLong()))
        }
        .maxOrNull()

    private fun farmingCooldownEnd(
        guildId: UUID,
        snapshot: Collection<DurableWarRecord>,
        cooldownHours: Int,
    ): Instant? = snapshot.asSequence()
        .mapNotNull { it.war }
        .filter { it.isEnded && it.winner == guildId && it.endedAt != null }
        .mapNotNull { war ->
            when {
                war.farmingCooldownGuildId == guildId -> war.farmingCooldownUntil
                war.farmingCooldownGuildId == null ->
                    war.endedAt?.plus(Duration.ofHours(cooldownHours.coerceAtLeast(0).toLong()))
                else -> null
            }
        }
        .maxOrNull()

    private fun activeWarCount(guildId: UUID, knownWars: Collection<War>): Int =
        knownWars.count { it.isActive && guildId in setOf(it.declaringGuildId, it.defendingGuildId) }

    private fun objectivesFitKillTarget(objectives: Set<WarObjective>, killTarget: Int): Boolean =
        objectives.all { objective ->
            objective.targetValue > 0 &&
                (objective.type != ObjectiveType.KILLS || objective.targetValue <= killTarget)
        }

    private fun participantsAreHostile(firstGuildId: UUID, secondGuildId: UUID): Boolean {
        val first = guildRepository.getById(firstGuildId) ?: return false
        val second = guildRepository.getById(secondGuildId) ?: return false
        return first.mode == GuildMode.HOSTILE && second.mode == GuildMode.HOSTILE
    }

    /**
     * Creates a war declaration that requires acceptance (REQ-024: no auto-accept).
     */
    @Synchronized
    override fun createWarDeclaration(
        declaringGuildId: UUID,
        defendingGuildId: UUID,
        duration: Duration,
        objectives: Set<WarObjective>,
        wagerAmount: Int,
        terms: String?,
        actorId: UUID,
        rated: Boolean,
    ): WarDeclaration? {
        if (!canPlayerManageWars(actorId, declaringGuildId)) {
            logger.warn("Player $actorId attempted to declare war for guild $declaringGuildId without permission")
            return null
        }
        return try {
            if (!participantsAreHostile(declaringGuildId, defendingGuildId)) {
                logger.warn("Cannot create war declaration - both guilds must be hostile")
                return null
            }

            val snapshot = warRepository.getAll()
            val knownWars = snapshot.mapNotNull { it.war }
            val config = configService.loadConfig()
            val now = Instant.now()

            val declarationCooldownEnd = declarationCooldownEnd(
                declaringGuildId,
                snapshot,
                config.combat.warDeclarationCooldownHours,
            )
            if (declarationCooldownEnd != null && now.isBefore(declarationCooldownEnd)) {
                logger.warn("Cannot create war declaration - guild $declaringGuildId is on declaration cooldown until $declarationCooldownEnd")
                return null
            }

            for (guildId in setOf(declaringGuildId, defendingGuildId)) {
                val farmingCooldownEnd = farmingCooldownEnd(
                    guildId,
                    snapshot,
                    config.combat.warFarmingCooldownHours,
                )
                if (farmingCooldownEnd != null && now.isBefore(farmingCooldownEnd)) {
                    logger.warn("Cannot create war declaration - guild $guildId is on war farming cooldown until $farmingCooldownEnd")
                    return null
                }
            }

            val existingWar = knownWars.find { it.isActive &&
                setOf(it.declaringGuildId, it.defendingGuildId) == setOf(declaringGuildId, defendingGuildId) }
            if (existingWar != null) {
                logger.warn("Cannot create war declaration - active war already exists between guilds $declaringGuildId and $defendingGuildId")
                return null
            }

            val existingDeclaration = snapshot.mapNotNull { it.declaration }.filter { !it.accepted && !it.rejected }.find {
                (it.declaringGuildId == declaringGuildId && it.defendingGuildId == defendingGuildId) ||
                (it.declaringGuildId == defendingGuildId && it.defendingGuildId == declaringGuildId)
            }
            if (existingDeclaration != null) {
                logger.warn("Cannot create war declaration - pending declaration already exists between guilds $declaringGuildId and $defendingGuildId")
                return null
            }

            if (!objectivesFitKillTarget(objectives, config.combat.warKillWinTarget)) {
                logger.warn("Cannot create war declaration - objective target is invalid or exceeds global kill target")
                return null
            }

            for (guildId in setOf(declaringGuildId, defendingGuildId)) {
                val currentWars = activeWarCount(guildId, knownWars)
                val maxWars = maxWarsForGuild(guildId, config.combat.maxSimultaneousWars)
                if (currentWars >= maxWars) {
                    logger.warn("Guild $guildId has reached war limit: $currentWars/$maxWars")
                    return null
                }
            }

            val ratedChapterId = if (rated) {
                if (progressionRepository.getGuildProgression(declaringGuildId)?.currentLevel != 100 ||
                    progressionRepository.getGuildProgression(defendingGuildId)?.currentLevel != 100
                ) {
                    logger.debug("Rated war declaration rejected because both guilds are not current-run level 100")
                    return null
                }
                seasonalElo?.currentRatedChapterId() ?: run {
                    logger.debug("Rated war declaration rejected because seasonal Elo is not active in a scheduled chapter")
                    return null
                }
            } else {
                null
            }
            val declaredAt = Instant.now()
            val declaration = WarDeclaration(
                declaringGuildId = declaringGuildId,
                defendingGuildId = defendingGuildId,
                proposedDuration = effectiveWarDuration(duration),
                objectives = objectives,
                terms = terms,
                wagerAmount = wagerAmount,
                declaredAt = declaredAt,
                ratedChapterId = ratedChapterId,
                declarationCooldownUntil = declaredAt.plus(
                    Duration.ofHours(config.combat.warDeclarationCooldownHours.coerceAtLeast(0).toLong())
                ),
            )

            saveDeclaration(declaration)
            runCatching { warNotifications?.declarationCreated(declaration) }
                .onFailure { logger.error("Failed to publish war declaration notifications ${declaration.id}", it) }

            logger.info("War declaration created by guild $declaringGuildId against guild $defendingGuildId with wager $wagerAmount")
            declaration
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error creating war declaration between $declaringGuildId and $defendingGuildId", e)
            null
        }
    }

    @Synchronized
    override fun acceptWarDeclaration(declarationId: UUID, actorId: UUID): War? {
        val declaration = warRepository.get(declarationId)?.declaration?.takeIf { !it.accepted && !it.rejected } ?: return null
        if (!canPlayerManageWars(actorId, declaration.defendingGuildId)) {
            logger.warn("Player $actorId attempted to accept war declaration $declarationId without permission for guild ${declaration.defendingGuildId}")
            return null
        }
        return acceptWarDeclarationInternal(declarationId, actorId)
    }

    private fun acceptWarDeclarationInternal(declarationId: UUID, actorId: UUID): War? {
        return try {
            var record = warRepository.get(declarationId) ?: return null
            val declaration = record.declaration?.takeIf { !it.accepted && !it.rejected } ?: return null
            if (!declaration.isValid) return null
            val alreadyEscrowed = record.paymentPhase == WarPaymentPhase.ESCROWED
            // Once escrow is durably funded this is crash recovery. Every acceptance
            // policy check was already satisfied before money moved, so re-validating
            // hostile mode, limits, objectives, cooldowns, or rated eligibility here
            // could strand funded escrow after a restart.
            if (!alreadyEscrowed) {
                if (!participantsAreHostile(declaration.declaringGuildId, declaration.defendingGuildId)) {
                    logger.warn("Cannot accept war declaration $declarationId - both guilds must remain hostile")
                    return null
                }
                val snapshot = warRepository.getAll()
                val knownWars = snapshot.mapNotNull { it.war }
                val combat = configService.loadConfig().combat
                if (!objectivesFitKillTarget(declaration.objectives, combat.warKillWinTarget)) {
                    logger.warn("Cannot accept war declaration $declarationId - objective target is no longer valid")
                    return null
                }
                for (guildId in setOf(declaration.declaringGuildId, declaration.defendingGuildId)) {
                    val farmingEnd = farmingCooldownEnd(guildId, snapshot, combat.warFarmingCooldownHours)
                    if (farmingEnd != null && Instant.now().isBefore(farmingEnd)) {
                        logger.warn("Cannot accept war declaration $declarationId - guild $guildId is on farming cooldown")
                        return null
                    }
                    val currentWars = activeWarCount(guildId, knownWars)
                    val maxWars = maxWarsForGuild(guildId, combat.maxSimultaneousWars)
                    if (currentWars >= maxWars) {
                        logger.warn("Cannot accept war declaration $declarationId - guild $guildId has reached war limit")
                        return null
                    }
                }
                if (declaration.isRated) {
                    val currentRatedChapter = seasonalElo?.currentRatedChapterId()
                    if (currentRatedChapter != declaration.ratedChapterId ||
                        progressionRepository.getGuildProgression(declaration.declaringGuildId)?.currentLevel != 100 ||
                        progressionRepository.getGuildProgression(declaration.defendingGuildId)?.currentLevel != 100
                    ) {
                        logger.debug("Rated war acceptance rejected because chapter or level-100 eligibility changed")
                        return null
                    }
                }
            }
            val acceptanceDeclaringRecipients = snapshotNotificationRecipients(declaration.declaringGuildId)
            val acceptanceDefendingRecipients = snapshotNotificationRecipients(declaration.defendingGuildId)
            record = requireNotNull(warRepository.get(declarationId))
            if (record.war?.isEnded == true || record.war?.status == WarStatus.CANCELLED) return null
            if (record.war == null) {
                val pending = War(id = declarationId, declaringGuildId = declaration.declaringGuildId,
                    defendingGuildId = declaration.defendingGuildId, duration = declaration.proposedDuration,
                    objectives = declaration.objectives, ratedChapterId = declaration.ratedChapterId)
                persist(record.copy(war = pending, stats = WarStats(declarationId)))
            }
            if (declaration.wagerAmount > 0 && createWager(declarationId, declaration.wagerAmount, declaration.wagerAmount) == null) {
                return null
            }
            record = requireNotNull(warRepository.get(declarationId))
            val active = requireNotNull(record.war).copy(status = WarStatus.ACTIVE, startedAt = Instant.now())
            persist(record.copy(
                war = active,
                declaration = declaration.copy(accepted = true),
                notificationRecipients = record.notificationRecipients.copy(
                    acceptanceDeclaring = acceptanceDeclaringRecipients,
                    acceptanceDefending = acceptanceDefendingRecipients,
                ),
                acceptanceNotificationExpected = true,
            ))
            Bukkit.getPluginManager().callEvent(GuildWarDeclaredEvent(active.declaringGuildId, active.defendingGuildId, actorId))
            runCatching { warNotifications?.warAccepted(active) }
                .onFailure { logger.error("Failed to publish war acceptance notifications ${active.id}", it) }
            active
        } catch (error: Exception) {
            logger.error("Error accepting durable war declaration $declarationId", error)
            null
        }
    }

    override fun rejectWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            val declaration = warRepository.get(declarationId)?.declaration?.takeIf { !it.accepted && !it.rejected } ?: return false
            if (!canPlayerManageWars(actorId, declaration.defendingGuildId)) {
                logger.warn("Player $actorId attempted to reject war declaration $declarationId without permission for guild ${declaration.defendingGuildId}")
                return false
            }
            removeDeclaration(declarationId) != null
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error rejecting war declaration: $declarationId", e)
            false
        }
    }

    override fun cancelWarDeclaration(declarationId: UUID, actorId: UUID): Boolean {
        return try {
            val declaration = warRepository.get(declarationId)?.declaration?.takeIf { !it.accepted && !it.rejected } ?: return false
            if (!canPlayerManageWars(actorId, declaration.declaringGuildId)) {
                logger.warn("Player $actorId attempted to cancel war declaration $declarationId without permission for guild ${declaration.declaringGuildId}")
                return false
            }
            removeDeclaration(declarationId) != null
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error canceling war declaration: $declarationId", e)
            false
        }
    }

    @Synchronized
    override fun endWar(warId: UUID, winnerGuildId: UUID, peaceTerms: String?, actorId: UUID): Boolean {
        val war = getWar(warId) ?: return false
        if (winnerGuildId !in setOf(war.declaringGuildId, war.defendingGuildId)) return false
        val losingGuildId =
            if (winnerGuildId == war.declaringGuildId) war.defendingGuildId else war.declaringGuildId
        if (!canPlayerManageWars(actorId, losingGuildId)) {
            logger.warn(
                "Player $actorId attempted to end war $warId without permission for losing guild $losingGuildId"
            )
            return false
        }
        return endWarInternal(warId, winnerGuildId, peaceTerms)
    }

    private fun endWarInternal(warId: UUID, winnerGuildId: UUID, peaceTerms: String?): Boolean =
        finishWarInternal(warId, winnerGuildId, peaceTerms) != null

    private fun finishWarInternal(warId: UUID, winnerGuildId: UUID?, peaceTerms: String?): War? {
        return try {
            val war = getWar(warId) ?: return null
            if (!war.isActive) return null
            if (winnerGuildId != null &&
                winnerGuildId !in setOf(war.declaringGuildId, war.defendingGuildId)
            ) {
                return null
            }

            val loser = when (winnerGuildId) {
                war.declaringGuildId -> war.defendingGuildId
                war.defendingGuildId -> war.declaringGuildId
                else -> null
            }
            val endedAt = Instant.now()
            val farmingCooldownUntil = winnerGuildId?.let {
                endedAt.plus(
                    Duration.ofHours(
                        configService.loadConfig().combat.warFarmingCooldownHours.coerceAtLeast(0).toLong()
                    )
                )
            }
            val ended = war.copy(
                status = WarStatus.ENDED,
                endedAt = endedAt,
                winner = winnerGuildId,
                loser = loser,
                peaceTerms = peaceTerms,
                farmingCooldownGuildId = winnerGuildId,
                farmingCooldownUntil = farmingCooldownUntil,
            )
            // Durable VICTORY/DEFEAT recovery requires winner/loser snapshots.
            // Draws and mutual peace currently have no winner/loser notification kind.
            saveWar(ended, expectResolutionNotification = winnerGuildId != null)
            rateResolvedWar(ended)
            resolveWager(warId, winnerGuildId)
            if (winnerGuildId != null && !ended.isRated) {
                awardWarExperience(winnerGuildId)
            }
            Bukkit.getPluginManager().callEvent(
                GuildWarEndEvent(
                    warId,
                    winnerGuildId,
                    loser,
                    war.declaringGuildId,
                    war.defendingGuildId,
                )
            )
            runCatching { warNotifications?.warEnded(ended) }
                .onFailure { logger.error("Failed to publish war resolution notifications $warId", it) }
            ended
        } catch (error: Exception) {
            logger.error("Error ending durable war $warId", error)
            null
        }
    }

    @Synchronized
    override fun endWarAsDraw(warId: UUID, reason: String?, actorId: UUID): Boolean {
        val war = getWar(warId) ?: return false
        if (!canActorManageWar(actorId, war)) {
            logger.warn("Player $actorId attempted to end war $warId as a draw without permission")
            return false
        }
        return endWarAsDrawInternal(warId, reason)
    }

    private fun endWarAsDrawInternal(warId: UUID, reason: String?): Boolean {
        val ended = finishWarInternal(warId, null, reason ?: "War ended in a draw") ?: return false
        logger.info("War ended as draw: ${ended.id}, reason: $reason")
        return true
    }

    @Synchronized
    override fun cancelWar(warId: UUID, actorId: UUID): Boolean {
        return try {
            val war = getWar(warId) ?: return false
            if (!canActorManageWar(actorId, war)) {
                logger.warn("Player $actorId attempted to cancel war $warId without permission")
                return false
            }
            if (war.status == WarStatus.ENDED || war.status == WarStatus.CANCELLED) return false
            val canceledWar = war.copy(status = WarStatus.CANCELLED)
            saveWar(canceledWar)
            resolveWager(warId, null)
            logger.info("War canceled: $warId")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error canceling war: $warId", e)
            false
        }
    }

    override fun getWar(warId: UUID): War? {
        return warRepository.get(warId)?.war
    }

    override fun getActiveWars(): List<War> {
        return wars.values.filter { it.isActive }
    }

    override fun getWarsForGuild(guildId: UUID): List<War> {
        return wars.values.filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }
    }

    override fun getPendingDeclarationsForGuild(guildId: UUID): List<WarDeclaration> {
        return warDeclarations.values.filter { it.defendingGuildId == guildId }
    }

    override fun getDeclarationsByGuild(guildId: UUID): List<WarDeclaration> {
        return warDeclarations.values.filter { it.declaringGuildId == guildId }
    }

    override fun getWarStats(warId: UUID): WarStats {
        return warRepository.get(warId)?.stats ?: WarStats(warId)
    }

    override fun getWarKillWinTarget(): Int =
        configService.loadConfig().combat.warKillWinTarget.also {
            check(it > 0) { "combat.war_kill_win_target must be positive" }
        }

    override fun updateWarStats(stats: WarStats): Boolean {
        return try {
            saveStats(stats)
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error updating war stats for war: ${stats.warId}", e)
            false
        }
    }

    @Synchronized
    override fun recordOpposingGuildKill(
        warId: UUID,
        killerGuildId: UUID,
        victimGuildId: UUID,
    ): net.lumalyte.lg.application.services.WarKillCounterUpdate? {
        val record = warRepository.get(warId) ?: return null
        val war = record.war ?: return null
        if (!war.isActive || killerGuildId == victimGuildId) return null

        val declaringKill = killerGuildId == war.declaringGuildId &&
            victimGuildId == war.defendingGuildId
        val defendingKill = killerGuildId == war.defendingGuildId &&
            victimGuildId == war.declaringGuildId
        if (!declaringKill && !defendingKill) return null

        val current = record.stats ?: WarStats(warId)
        val persistedWinners = listOf(war.declaringGuildId, war.defendingGuildId)
            .filter { guildId -> killVictoryTerms(war, current, guildId) != null }
        if (persistedWinners.isNotEmpty()) {
            if (persistedWinners.size == 1) {
                val pendingWinner = persistedWinners.single()
                if (!resolveReachedKillTarget(warId, pendingWinner)) {
                    logger.error("Failed to recover persisted kill-target victory for war $warId")
                }
            } else {
                logger.error(
                    "War $warId has conflicting persisted global kill-target victories; refusing another kill"
                )
            }
            return null
        }

        val updated = if (declaringKill) {
            current.copy(
                declaringGuildKills = Math.addExact(current.declaringGuildKills, 1),
                defendingGuildDeaths = Math.addExact(current.defendingGuildDeaths, 1),
                lastUpdated = Instant.now(),
            )
        } else {
            current.copy(
                defendingGuildKills = Math.addExact(current.defendingGuildKills, 1),
                declaringGuildDeaths = Math.addExact(current.declaringGuildDeaths, 1),
                lastUpdated = Instant.now(),
            )
        }
        persist(record.copy(stats = updated))

        val target = getWarKillWinTarget()
        val winner = killerGuildId.takeIf { killVictoryTerms(war, updated, killerGuildId) != null }
        return net.lumalyte.lg.application.services.WarKillCounterUpdate(updated, target, winner)
    }

    @Synchronized
    override fun resolveReachedKillTarget(warId: UUID, winnerGuildId: UUID): Boolean {
        val record = warRepository.get(warId) ?: return false
        val war = record.war ?: return false
        if (!war.isActive) return false

        val terms = killVictoryTerms(
            war,
            record.stats ?: WarStats(warId),
            winnerGuildId,
        ) ?: return false
        return endWarInternal(warId, winnerGuildId, terms)
    }

    @Synchronized
    override fun reconcilePendingKillVictories(): Int =
        reconcilePendingKillVictories(warRepository.getAll())

    private fun reconcilePendingKillVictories(records: Iterable<DurableWarRecord>): Int {
        var resolved = 0
        records.forEach { record ->
            val war = record.war?.takeIf { it.isActive } ?: return@forEach
            val stats = record.stats ?: return@forEach
            val candidates = listOf(war.declaringGuildId, war.defendingGuildId)
                .mapNotNull { guildId ->
                    killVictoryTerms(war, stats, guildId)?.let { terms -> guildId to terms }
                }

            when (candidates.size) {
                0 -> Unit
                1 -> {
                    val (winner, terms) = candidates.single()
                    if (endWarInternal(war.id, winner, terms)) resolved++
                }
                else -> logger.error(
                    "War ${war.id} has conflicting persisted kill victories; refusing automatic resolution"
                )
            }
        }
        return resolved
    }

    private fun killVictoryTerms(war: War, stats: WarStats, guildId: UUID): String? {
        val killerKills = when (guildId) {
            war.declaringGuildId -> stats.declaringGuildKills
            war.defendingGuildId -> stats.defendingGuildKills
            else -> return null
        }
        val target = getWarKillWinTarget()
        if (killerKills >= target) {
            return "Victory achieved by reaching the global war kill target ($killerKills/$target)"
        }

        val objectiveTarget = war.objectives
            .asSequence()
            .filter { it.type == ObjectiveType.KILLS && it.targetValue > 0 }
            .map { it.targetValue }
            .minOrNull()
        return objectiveTarget
            ?.takeIf { killerKills >= it }
            ?.let { "Victory achieved through kill objective ($killerKills/$it)" }
    }

    @Synchronized
    override fun addObjectiveProgress(warId: UUID, objectiveId: UUID, progress: Int): Boolean {
        if (progress <= 0) return false
        return try {
            val record = warRepository.get(warId) ?: return false
            val war = record.war?.takeIf { it.isActive } ?: return false
            val objective = war.objectives.firstOrNull { it.id == objectiveId } ?: return false
            if (objective.completed || objective.isCompleted) return true

            val currentValue = minOf(
                objective.targetValue,
                Math.addExact(objective.currentValue, progress),
            )
            val completed = currentValue >= objective.targetValue
            val updated = objective.copy(
                currentValue = currentValue,
                completed = completed,
                completedAt = if (completed) objective.completedAt ?: Instant.now() else objective.completedAt,
            )
            val updatedObjectives = war.objectives.map {
                if (it.id == objectiveId) updated else it
            }.toSet()
            persist(record.copy(war = war.copy(objectives = updatedObjectives)))
            logger.info("Objective progress persisted: war=$warId, objective=$objectiveId, progress=$currentValue/${objective.targetValue}")
            true
        } catch (e: Exception) {
            logger.error("Error adding objective progress for war: $warId", e)
            false
        }
    }

    override fun getWinLossRatio(guildId: UUID): Double {
        return try {
            val warHistory = getWarHistory(guildId, 100)
            val wins = warHistory.count { it.winner == guildId }
            val losses = warHistory.count { it.loser == guildId }

            if (losses == 0) {
                if (wins > 0) Double.MAX_VALUE else 0.0
            } else {
                wins.toDouble() / losses.toDouble()
            }
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error getting win/loss ratio for guild: $guildId", e)
            0.0
        }
    }

    /**
     * Enforce the configured war duration cap (REQ-008): a war cannot last
     * longer than `combat.war_duration_hours`.
     */
    private fun effectiveWarDuration(requested: Duration): Duration =
        effectiveWarDuration(requested, configService.loadConfig().combat.warDurationHours)

    /**
     * The effective max simultaneous wars for a guild (REQ-008): the config
     * `max_simultaneous_wars` base, refined upward by progression war-slot
     * rewards at the guild's reached levels.
     */
    private fun maxWarsForGuild(guildId: UUID, configMax: Int): Int {
        val progression = progressionRepository.getGuildProgression(guildId)
        val progressionConfig = progressionConfigService.getProgressionConfig()
        return maxWarsForGuild(
            currentLevel = progression?.currentLevel,
            configMax = configMax,
            levelRewards = progressionConfig.getActiveLevelRewards()
        )
    }

    private fun awardWarExperience(winnerGuildId: UUID?) {
        if (winnerGuildId == null) return
        try {
            val level = progressionRepository.getGuildProgression(winnerGuildId)?.currentLevel ?: return
            chapterTwoGuildAwardService?.awardPreCapWarWin(winnerGuildId, level)
        } catch (e: Exception) {
            logger.error("Failed to award pre-cap war win XP to guild $winnerGuildId", e)
        }
    }

    /**
     * Awards the configured kill XP (REQ-008) to the killer's guild for a war kill.
     */
    override fun awardWarKillExperience(killerGuildId: UUID, killerId: UUID) {
        val killXp = configService.loadConfig().combat.killExperience
        if (killXp > 0) {
            progressionService.awardPlayerExperience(
                killerGuildId,
                killerId,
                killXp,
                ExperienceSource.PLAYER_KILL,
            )
        }
    }

    /**
     * Records a war kill for anti-farming enforcement and returns whether the
     * kill should be treated as farming (REQ-008: `kill_cooldown_minutes` +
     * `same_player_kill_limit`). Returns true when the same killer has already
     * exceeded the per-victim kill limit within the cooldown window — callers
     * should suppress XP/rewards for such kills.
     */
    override fun recordWarKillAndCheckFarming(killerId: UUID, victimId: UUID): Boolean {
        val combat = configService.loadConfig().combat
        if (combat.killCooldownMinutes <= 0 || combat.samePlayerKillLimit <= 0) {
            // Anti-farming disabled — always allow
            return false
        }

        maybePruneKillTracking()
        val now = Instant.now()
        val cooldownStart = now.minusSeconds(combat.killCooldownMinutes * 60L)

        val byVictim = killTracking.computeIfAbsent(killerId) { ConcurrentHashMap() }
        val kills = byVictim.computeIfAbsent(victimId) { mutableListOf() }

        synchronized(kills) {
            // Drop timestamps outside the cooldown window
            kills.removeAll { it.isBefore(cooldownStart) }
            kills.add(now)

            // Farming when the kill limit is exceeded within the window
            return kills.size > combat.samePlayerKillLimit
        }
    }

    /**
     * Bounds `killTracking` on a long-running server: once the map exceeds a
     * sane size, prune entries whose cooldown window has elapsed.
     */
    private fun maybePruneKillTracking() {
        if (killTracking.size > MAX_TRACKED_KILLERS) {
            pruneKillTracking()
        }
    }

    /**
     * Prunes empty killer/victim entries so `killTracking` stays bounded on a
     * long-running server (REQ-008 anti-farming state). Call whenever a cooldown
     * window has fully elapsed for a pair.
     */
    fun pruneKillTracking(now: Instant = Instant.now()) {
        val combat = configService.loadConfig().combat
        if (combat.killCooldownMinutes <= 0) return
        val cooldownStart = now.minusSeconds(combat.killCooldownMinutes * 60L)

        killTracking.forEach { (killerId, byVictim) ->
            byVictim.forEach { (victimId, kills) ->
                synchronized(kills) {
                    kills.removeAll { it.isBefore(cooldownStart) }
                    if (kills.isEmpty()) byVictim.remove(victimId)
                }
            }
            if (byVictim.isEmpty()) killTracking.remove(killerId)
        }
    }

    /**
     * Checks whether a guild is at or above the configured max simultaneous wars
     * (REQ-008), refined upward by progression war slots — consistent with the
     * check in `createWarDeclaration`.
     */
    override fun canGuildDeclareWar(guildId: UUID): Boolean {
        if (guildRepository.getById(guildId)?.mode != GuildMode.HOSTILE) return false
        val snapshot = warRepository.getAll()
        val combat = configService.loadConfig().combat
        val now = Instant.now()
        if (declarationCooldownEnd(guildId, snapshot, combat.warDeclarationCooldownHours)
                ?.let(now::isBefore) == true
        ) return false
        if (farmingCooldownEnd(guildId, snapshot, combat.warFarmingCooldownHours)
                ?.let(now::isBefore) == true
        ) return false

        val activeWars = activeWarCount(guildId, snapshot.mapNotNull { it.war })
        val maxWars = maxWarsForGuild(guildId, combat.maxSimultaneousWars)
        return activeWars < maxWars
    }

    override fun canPlayerManageWars(playerId: UUID, guildId: UUID): Boolean =
        memberService.hasPermission(playerId, guildId, RankPermission.DECLARE_WAR)

    private fun canActorManageWar(actorId: UUID, war: War): Boolean =
        canPlayerManageWars(actorId, war.declaringGuildId) ||
            canPlayerManageWars(actorId, war.defendingGuildId)

    override fun getCurrentWarBetweenGuilds(guildA: UUID, guildB: UUID): War? {
        return wars.values.find {
            it.isActive &&
            ((it.declaringGuildId == guildA && it.defendingGuildId == guildB) ||
             (it.declaringGuildId == guildB && it.defendingGuildId == guildA))
        }
    }

    @Synchronized
    override fun processExpiredWars(): Int {
        val snapshot = warRepository.getAll().associateBy { it.id }.toMutableMap()
        var processedCount = reconcilePendingKillVictories(snapshot.values)
        // Only resume acceptance already recorded as fully funded. Never start a fresh charge
        // from a timer or revive expired consent; other incomplete phases remain held.
        snapshot.values.toList().filter {
            it.war?.status == WarStatus.DECLARED && it.paymentPhase == WarPaymentPhase.ESCROWED &&
                it.declaration?.isValid == true
        }.forEach {
            acceptWarDeclarationInternal(it.id, SYSTEM_ACTOR)
            warRepository.get(it.id)?.let { updated -> snapshot[it.id] = updated }
        }
        // Retry seasonal rating for durable completed outcomes. The Elo repository's war-id receipt
        // makes this idempotent, so a transient database failure cannot permanently lose a rated result.
        snapshot.values.mapNotNull { it.war }
            .filter { it.isEnded && it.isRated }
            .forEach(::rateResolvedWar)

        // Retry durable, already-chosen outcomes even when the ended war is no longer active.
        snapshot.values.toList().filter { it.wager != null && (it.war?.isEnded == true || it.war?.status == WarStatus.CANCELLED) &&
            it.paymentPhase !in setOf(WarPaymentPhase.SETTLED, WarPaymentPhase.REVIEW) }
            .forEach {
                resolveWager(it.id, it.war?.winner)
                warRepository.get(it.id)?.let { updated -> snapshot[it.id] = updated }
            }
        val now = Instant.now()

        // Process expired declarations
        val expiredDeclarations = snapshot.values.mapNotNull { it.declaration }.filter { !it.accepted && !it.rejected && it.expiresAt.isBefore(now) }
        expiredDeclarations.forEach { removeDeclaration(it.id) }
        processedCount += expiredDeclarations.size

        // Process expired wars with draw logic.
        // REQ-008: honour `war_end_grace_period_minutes` — a war is not force-ended
        // until duration + grace period have both elapsed.
        val graceSeconds = configService.loadConfig().combat.warEndGracePeriodMinutes * 60L
        val expiredWars = snapshot.values.mapNotNull { it.war }.filter { war ->
            war.isActive && war.startedAt != null &&
                war.startedAt!!.plus(war.duration).plusSeconds(graceSeconds).isBefore(now)
        }
        for (war in expiredWars) {
            val reason = if (checkForDrawCondition(snapshot.getValue(war.id))) {
                "War expired with no clear winner"
            } else {
                "War expired without a resolvable victory"
            }
            if (endWarAsDrawInternal(war.id, reason)) {
                logger.info("War ${war.id} ended as draw due to expiration")
                processedCount++
            }
        }

        return processedCount
    }

    override fun validateObjectives(objectives: Set<WarObjective>): Boolean =
        objectives.isNotEmpty() &&
            objectives.size <= 5 &&
            objectivesFitKillTarget(objectives, getWarKillWinTarget())

    override fun getWarHistory(guildId: UUID, limit: Int): List<War> {
        return wars.values
            .filter { it.declaringGuildId == guildId || it.defendingGuildId == guildId }
            .sortedByDescending { it.declaredAt }
            .take(limit)
    }

    /**
     * Checks if a war should end in a draw based on kill objectives.
     * Returns true if it's a draw situation.
     */
    fun checkForDrawCondition(warId: UUID): Boolean =
        warRepository.get(warId)?.let(::checkForDrawCondition) ?: false

    private fun checkForDrawCondition(record: DurableWarRecord): Boolean {
        val war = record.war ?: return false
        val stats = record.stats ?: return false
        
        // Check if war has expired
        if (war.isExpired) {
            val killObjective = war.objectives.firstOrNull { it.type == ObjectiveType.KILLS }
            if (killObjective != null) {
                // Check kill counts
                return when {
                    // No kills at all - draw
                    stats.declaringGuildKills == 0 && stats.defendingGuildKills == 0 -> true
                    // Equal kills - draw
                    stats.declaringGuildKills == stats.defendingGuildKills -> true
                    // Neither guild reached target - draw
                    stats.declaringGuildKills < killObjective.targetValue && 
                    stats.defendingGuildKills < killObjective.targetValue -> true
                    else -> false
                }
            }
            // No objectives or expired - draw
            return true
        }
        return false
    }


    @Synchronized
    override fun createWager(warId: UUID, declaringGuildWager: Int, defendingGuildWager: Int): WarWager? {
        return try {
            var record = warRepository.get(warId) ?: return null
            val war = record.war ?: return null
            if (war.isEnded || war.status == WarStatus.CANCELLED || declaringGuildWager < 0 || defendingGuildWager < 0 ||
                declaringGuildWager.toLong() + defendingGuildWager > Int.MAX_VALUE) return null
            val previous = record.wager
            if (previous != null && (previous.declaringGuildWager != declaringGuildWager ||
                    previous.defendingGuildWager != defendingGuildWager)) return null
            if (previous == null) {
                persist(record.copy(wager = WarWager(warId = warId, declaringGuildId = war.declaringGuildId,
                    defendingGuildId = war.defendingGuildId, declaringGuildWager = declaringGuildWager,
                    defendingGuildWager = defendingGuildWager), paymentPhase = WarPaymentPhase.FUNDING))
            }
            if (!warPayments.fund(warId)) return null
            warRepository.get(warId)?.wager
        } catch (error: Exception) {
            logger.error("Error funding durable wager $warId", error)
            null
        }
    }

    @Synchronized
    override fun resolveWager(warId: UUID, winnerGuildId: UUID?): WarWager? {
        return try {
            val record = warRepository.get(warId) ?: return null
            val war = record.war ?: return null
            if (war.isEnded && war.winner != winnerGuildId) return null
            if (!warPayments.settle(warId, winnerGuildId)) return null
            warRepository.get(warId)?.wager
        } catch (error: Exception) {
            logger.error("Error settling durable wager $warId", error)
            null
        }
    }

    override fun getWager(warId: UUID): WarWager? {
        return warRepository.get(warId)?.takeIf {
            it.paymentPhase in setOf(WarPaymentPhase.ESCROWED, WarPaymentPhase.SETTLING, WarPaymentPhase.SETTLED)
        }?.wager
    }

    // Peace Agreement Methods
    override fun proposePeaceAgreement(
        warId: UUID,
        proposingGuildId: UUID,
        peaceTerms: String,
        offering: PeaceOffering?
    ): PeaceAgreement? {
        return try {
            val war = getWar(warId)
            if (war == null || !war.isActive) {
                logger.warn("Cannot propose peace for inactive or non-existent war $warId")
                return null
            }

            // Determine the target guild (the one that didn't propose)
            val targetGuildId = if (war.declaringGuildId == proposingGuildId) {
                war.defendingGuildId
            } else {
                war.declaringGuildId
            }

            val agreement = PeaceAgreement(
                warId = warId,
                proposingGuildId = proposingGuildId,
                targetGuildId = targetGuildId,
                peaceTerms = peaceTerms,
                offering = offering
            )

            peaceAgreements[agreement.id] = agreement
            logger.info("Peace agreement ${agreement.id} proposed for war $warId")
            agreement
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error proposing peace agreement", e)
            null
        }
    }

    override fun acceptPeaceAgreement(agreementId: UUID, acceptingGuildId: UUID): War? {
        return try {
            val agreement = peaceAgreements[agreementId]
            if (agreement == null || !agreement.isValid || agreement.targetGuildId != acceptingGuildId) {
                logger.warn("Cannot accept invalid peace agreement $agreementId")
                return null
            }

            val war = getWar(agreement.warId)
            if (war == null || !war.isActive) {
                logger.warn("Cannot accept peace for inactive war ${agreement.warId}")
                return null
            }

            val endedWar = finishWarInternal(
                war.id,
                winnerGuildId = null,
                peaceTerms = agreement.peaceTerms,
            ) ?: return null
            peaceAgreements[agreementId] = agreement.copy(accepted = true, acceptedAt = Instant.now())

            logger.info("Peace agreement accepted, war ${war.id} ended")
            endedWar
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error accepting peace agreement", e)
            null
        }
    }

    override fun rejectPeaceAgreement(agreementId: UUID, rejectingGuildId: UUID): Boolean {
        return try {
            val agreement = peaceAgreements[agreementId]
            if (agreement == null || !agreement.isValid || agreement.targetGuildId != rejectingGuildId) {
                logger.warn("Cannot reject invalid peace agreement $agreementId")
                return false
            }

            peaceAgreements[agreementId] = agreement.copy(rejected = true)
            logger.info("Peace agreement $agreementId rejected")
            true
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error rejecting peace agreement", e)
            false
        }
    }

    override fun getPeaceAgreementsForWar(warId: UUID): List<PeaceAgreement> {
        return peaceAgreements.values.filter { it.warId == warId }
    }

    override fun getPendingPeaceAgreementsForGuild(guildId: UUID): List<PeaceAgreement> {
        return peaceAgreements.values.filter { it.targetGuildId == guildId && it.isValid }
    }

    private fun rateResolvedWar(war: War) {
        val chapterId = war.ratedChapterId ?: return
        val coordinator = seasonalElo ?: return
        try {
            val draw = war.winner == null
            val firstScore = when {
                draw -> 0.5
                war.winner == war.declaringGuildId -> 1.0
                else -> 0.0
            }
            val secondScore = when {
                draw -> 0.5
                war.winner == war.defendingGuildId -> 1.0
                else -> 0.0
            }
            when (val result = coordinator.rateWar(
                war.id,
                chapterId,
                war.declaringGuildId,
                war.defendingGuildId,
                firstScore,
                secondScore,
                war.endedAt?.toEpochMilli() ?: System.currentTimeMillis(),
            )) {
                is net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult.Rated ->
                    logger.info("Rated war ${war.id}: ${result.firstBefore}->${result.firstAfter}, ${result.secondBefore}->${result.secondAfter}")
                net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult.RematchGuarded ->
                    logger.debug("War ${war.id} completed inside seasonal Elo rematch window; unrated")
                net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult.Ineligible ->
                    logger.debug("War ${war.id} is not eligible for seasonal Elo")
                net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult.Frozen ->
                    logger.debug("War ${war.id} seasonal Elo is frozen by chapter state")
                net.lumalyte.lg.infrastructure.persistence.migrations.SeasonalWarRatingResult.Replayed -> Unit
            }
        } catch (error: Exception) {
            logger.warn("Seasonal Elo update failed for resolved war ${war.id}; reconciliation will retry", error)
        }
    }

    // Daily War Costs
    // NOTE: This method is deprecated - DailyWarCostsServiceBukkit handles actual cost deduction
    // This just returns the count of affected guilds for backward compatibility
    override fun applyDailyWarCosts(): Int {
        return try {
            val activeWars = wars.values.filter { it.isActive }
            val affectedGuilds = mutableSetOf<UUID>()

            for (war in activeWars) {
                affectedGuilds.add(war.declaringGuildId)
                affectedGuilds.add(war.defendingGuildId)
            }

            logger.debug("${affectedGuilds.size} guilds in active wars (actual costs applied by DailyWarCostsService)")

            affectedGuilds.size
        } catch (e: Exception) {
            // In-memory operation - catching runtime exceptions from state validation
            logger.error("Error counting guilds in active wars", e)
            0
        }
    }

    // War cooldowns are durable metadata on declarations/resolved wars. Older
    // records are reconstructed from their persisted timestamps by the helpers above.
    override fun isGuildInWarFarmingCooldown(guildId: UUID): Boolean =
        getGuildWarFarmingCooldownEnd(guildId)?.let { Instant.now().isBefore(it) } == true

    override fun getGuildWarFarmingCooldownEnd(guildId: UUID): Instant? {
        val combat = configService.loadConfig().combat
        return farmingCooldownEnd(guildId, warRepository.getAll(), combat.warFarmingCooldownHours)
    }

    @Synchronized
    override fun updateGuildWarFarmingCooldown(guildId: UUID, endTime: Instant): Boolean {
        return try {
            val record = warRepository.getAll()
                .filter { it.war?.isEnded == true && it.war?.winner == guildId }
                .maxByOrNull { it.war?.endedAt ?: Instant.MIN }
                ?: return false
            val war = requireNotNull(record.war)
            persist(
                record.copy(
                    war = war.copy(
                        farmingCooldownGuildId = guildId,
                        farmingCooldownUntil = endTime,
                    )
                )
            )
            logger.info("Updated durable war farming cooldown for guild $guildId until $endTime")
            true
        } catch (e: Exception) {
            logger.error("Error updating durable war farming cooldown", e)
            false
        }
    }

    override fun isGuildOnWarDeclarationCooldown(guildId: UUID): Boolean =
        getWarDeclarationCooldownEnd(guildId)?.let { Instant.now().isBefore(it) } == true

    override fun getWarDeclarationCooldownEnd(guildId: UUID): Instant? {
        val combat = configService.loadConfig().combat
        return declarationCooldownEnd(guildId, warRepository.getAll(), combat.warDeclarationCooldownHours)
    }

    @Synchronized
    override fun recordWarDeclaration(guildId: UUID) {
        try {
            val record = warRepository.getAll()
                .filter { it.declaration?.declaringGuildId == guildId }
                .maxByOrNull { it.declaration?.declaredAt ?: Instant.MIN }
                ?: return
            val declaration = requireNotNull(record.declaration)
            if (declaration.declarationCooldownUntil != null) return
            val cooldownEnd = declaration.declaredAt.plus(
                Duration.ofHours(
                    configService.loadConfig().combat.warDeclarationCooldownHours.coerceAtLeast(0).toLong()
                )
            )
            persist(record.copy(declaration = declaration.copy(declarationCooldownUntil = cooldownEnd)))
            logger.info("Recorded durable war declaration cooldown for guild $guildId until $cooldownEnd")
        } catch (e: Exception) {
            logger.error("Error recording durable war declaration cooldown for guild $guildId", e)
        }
    }

    companion object {
        private val SYSTEM_ACTOR = UUID(0, 0)

        /** Anti-farming tracking bound: prune once this many killers are tracked. */
        private const val MAX_TRACKED_KILLERS = 10_000

        /**
         * REQ-008: caps a requested war duration at `combat.war_duration_hours`.
         */
        fun effectiveWarDuration(requested: Duration, configWarDurationHours: Int): Duration {
            val configDuration = Duration.ofHours(configWarDurationHours.toLong())
            return if (requested > configDuration) configDuration else requested
        }

        /**
         * REQ-008: effective max simultaneous wars = config `max_simultaneous_wars`
         * base, refined upward by the highest progression war-slot reward among the
         * levels the guild has reached. `levelRewards` is keyed by level number.
         */
        fun maxWarsForGuild(currentLevel: Int?, configMax: Int, levelRewards: Map<Int, net.lumalyte.lg.config.LevelRewardConfig>): Int {
            if (currentLevel == null) return configMax
            var maxWars = configMax
            for (level in 1..currentLevel) {
                val wars = levelRewards[level]?.warSlots ?: configMax
                if (wars > maxWars) maxWars = wars
            }
            return maxWars
        }
    }
}
