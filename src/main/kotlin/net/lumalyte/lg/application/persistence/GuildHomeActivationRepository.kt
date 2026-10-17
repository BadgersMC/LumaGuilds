package net.lumalyte.lg.application.persistence

import java.util.UUID

interface GuildHomeActivationRepository {
    fun isActive(guildId: UUID, homeName: String): Boolean
    fun activeCount(guildId: UUID): Int
    fun availableCredits(guildId: UUID): Int
    fun activate(guildId: UUID, homeName: String, transactionId: UUID?): Boolean
    fun activateUsingCredit(guildId: UUID, homeName: String): Boolean
    fun remove(guildId: UUID, homeName: String): Boolean
    fun removeAll(guildId: UUID): Boolean
}
