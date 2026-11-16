package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.persistence.GuildHomeAccessRepository
import net.lumalyte.lg.application.persistence.GuildHomeAccessState
import net.lumalyte.lg.application.persistence.GuildHomeAuditPreview
import net.lumalyte.lg.application.persistence.VerifiedGuildHomeGrant
import java.util.UUID

/**
 * Explicit legacy-only adapter for pre-v51 service tests.
 * Never wired in production; the runtime constructor requires a real access repository.
 */
internal object LegacyHomeAccessTestAdapter : GuildHomeAccessRepository {
    override fun state(guildId: UUID, homeName: String) = GuildHomeAccessState.UNRECONCILED
    override fun isManaged(guildId: UUID) = false
    override fun previewReconciliation(guildId: UUID) =
        GuildHomeAuditPreview(guildId, false, emptyMap(), emptyList(), emptySet())
    override fun prepareEmptyGuild(guildId: UUID) = false
    override fun canGrantNewHome(guildId: UUID, totalCapacity: Int) = false
    override fun willUsePaidOutpostForNewHome(guildId: UUID, totalCapacity: Int) = false
    override fun grantNewHome(guildId: UUID, homeName: String, totalCapacity: Int) = false
    override fun removeGrant(guildId: UUID, homeName: String) = false
    override fun removeAllGrants(guildId: UUID) = false
    override fun reconcileVerified(
        guildId: UUID,
        grants: Map<String, VerifiedGuildHomeGrant>,
        provenance: String,
        reviewer: UUID,
    ) = false
    override fun assignPaidOutpost(guildId: UUID, transactionId: UUID, homeName: String) = false
    override fun assignAnyPaidOutpost(guildId: UUID, homeName: String) = false
}
