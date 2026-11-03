package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldResult
import org.slf4j.Logger
import java.util.UUID

internal fun MemberService.completeFreeAdmission(
    player: UUID,
    guild: Guild,
    rank: UUID,
    logger: Logger,
): LfgJoinResult {
    return if (addMember(player, guild.id, rank) == null) {
        logger.error("Failed to add player $player to guild ${guild.id}")
        LfgJoinResult.Error("Failed to add you to the guild")
    } else {
        logger.info("Player $player joined guild ${guild.name} via LFG")
        LfgJoinResult.Success("You have joined ${guild.name}!")
    }
}

/** Retains the canonical compensated-payment outcome and staff reconciliation reference. */
internal fun GuildGoldResult.admissionFeedback(guild: Guild, logger: Logger): LfgJoinResult {
    return when (this) {
        is GuildGoldResult.Applied -> LfgJoinResult.Success("You have joined ${guild.name}!")
        is GuildGoldResult.Rejected ->
            if (reason == net.lumalyte.lg.domain.gold.GuildGoldRejection.DUPLICATE_PENDING) {
                LfgJoinResult.Error("Join payment requires staff review. Do not pay again.")
            } else {
                LfgJoinResult.PaymentRejected(reason)
            }
        is GuildGoldResult.Failed -> failedAdmissionFeedback(logger)
    }
}

private fun GuildGoldResult.Failed.failedAdmissionFeedback(logger: Logger): LfgJoinResult {
    logger.error("Paid guild admission {} did not complete; compensated={}", transactionId, compensationSucceeded)
    return if (compensationSucceeded) {
        LfgJoinResult.PaymentRefunded(transactionId)
    } else {
        LfgJoinResult.PaymentUncertain(transactionId)
    }
}
