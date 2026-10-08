package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.domain.values.GuildShopXpPolicy
import java.util.UUID

/**
 * Durable terminal result shared within Guilds.
 * @property status Versioned companion outcome string.
 * @property guildId Stall owner captured by the immutable quote.
 * @property level New level when this transaction caused a level increase.
 * @property awardedNow Whether this call committed a new award.
 */
internal data class GuildShopXpCompletion(
    val status: String,
    val guildId: UUID,
    val level: Int? = null,
    val awardedNow: Boolean = false,
)

/** Quotes eligibility before payment and consumes completed sales atomically with progression. */
internal interface GuildShopXpRepository {
    /** Captures immutable sale identity, membership, run and policy; conflicts fail explicitly. */
    fun prepare(sale: GuildShopXpSale, policy: GuildShopXpPolicy): String

    /** Returns the retained terminal decision, awarding a prepared qualifying sale at most once. */
    fun complete(id: UUID): GuildShopXpCompletion
}

/**
 * Stable pre-payment identity, independent of the quoted configurable policy.
 * @property id Durable Market transaction ID.
 * @property guild Stall owner receiving the reward.
 * @property buyer Customer whose membership is captured before payment.
 * @property occurredAt Sale timestamp in epoch milliseconds.
 */
internal data class GuildShopXpSale(val id: UUID, val guild: UUID, val buyer: UUID, val occurredAt: Long)
