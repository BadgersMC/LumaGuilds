package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.services.LfgJoinResult
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.gold.GuildGoldResult
import org.slf4j.Logger

/** Retains the canonical compensated-payment outcome and staff reconciliation reference. */
internal fun GuildGoldResult.admissionFeedback(guild: Guild, logger: Logger): LfgJoinResult {
    return when (this) {
        is GuildGoldResult.Applied -> LfgJoinResult.Success("You have joined ${guild.name}!")
        is GuildGoldResult.Rejected -> LfgJoinResult.Error("Join fee was not accepted ($reason)")
        is GuildGoldResult.Failed -> failedAdmissionFeedback(logger)
    }
}

private fun GuildGoldResult.Failed.failedAdmissionFeedback(logger: Logger): LfgJoinResult {
    logger.error("Paid guild admission {} did not complete; compensated={}", transactionId, compensationSucceeded)
    val message = if (compensationSucceeded) {
        "Join failed; your payment was returned"
    } else {
        "Join payment requires staff review. Do not pay again. Reference: $transactionId"
    }
    return LfgJoinResult.Error(message)
}
