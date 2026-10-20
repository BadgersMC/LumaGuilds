package net.lumalyte.lg.application.persistence

import net.lumalyte.lg.application.results.OwnerRecoveryPreview
import net.lumalyte.lg.application.results.OwnerRecoveryPreviewResult
import net.lumalyte.lg.application.results.OwnerRecoveryRejection
import net.lumalyte.lg.application.results.OwnerRecoveryResult
import net.lumalyte.lg.domain.entities.Member
import java.util.UUID

/**
 * A repository that handles the persistence of Members.
 */
interface MemberRepository {
    /** Forget cached state after a committed guild disband transaction. */
    fun evictGuild(guildId: UUID) {}

    /**
     * Gets all members that exist.
     *
     * @return The set of all members.
     */
    fun getAll(): Set<Member>

    /**
     * Gets a member by player ID and guild ID.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return The found member, or null if it doesn't exist.
     */
    fun getByPlayerAndGuild(playerId: UUID, guildId: UUID): Member?

    /**
     * Gets all members of a specific guild.
     *
     * @param guildId The id of the guild to retrieve members for.
     * @return A set of members belonging to the guild.
     */
    fun getByGuild(guildId: UUID): Set<Member>

    /**
     * Gets all guilds that a player is a member of.
     *
     * @param playerId The id of the player to retrieve guilds for.
     * @return A set of guild IDs the player belongs to.
     */
    fun getGuildsByPlayer(playerId: UUID): Set<UUID>

    /**
     * Gets the rank ID for a player in a specific guild.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return The rank ID, or null if the player is not a member.
     */
    fun getRankId(playerId: UUID, guildId: UUID): UUID?

    /**
     * Gets all members with a specific rank in a guild.
     *
     * @param guildId The id of the guild.
     * @param rankId The id of the rank.
     * @return A set of members with the specified rank.
     */
    fun getByRank(guildId: UUID, rankId: UUID): Set<Member>

    /**
     * Gets the member count for a specific guild.
     *
     * @param guildId The id of the guild.
     * @return The number of members in the guild.
     */
    fun getMemberCount(guildId: UUID): Int

    /**
     * Adds a new member.
     *
     * @param member The member to add.
     * @return true if successful, false otherwise.
     */
    fun add(member: Member): Boolean

    /**
     * Updates an existing member without assigning or removing ownership.
     * Adapters must guard the stored rank in the mutation itself; a stale caller
     * must not demote a member who became Owner after its permission checks.
     *
     * @param member The member to update.
     * @return true if successful, false otherwise.
     */
    fun update(member: Member): Boolean

    /**
     * Removes an existing non-Owner member. An Owner must transfer ownership first.
     * Adapters must enforce the stored rank guard atomically with the deletion.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return true if successful, false otherwise.
     */
    fun remove(playerId: UUID, guildId: UUID): Boolean

    /**
     * Removes all members from a specific guild.
     *
     * @param guildId The id of the guild.
     * @return true if successful, false otherwise.
     */
    fun removeByGuild(guildId: UUID): Boolean

    /**
     * Checks if a player is a member of a specific guild.
     *
     * @param playerId The id of the player.
     * @param guildId The id of the guild.
     * @return true if the player is a member, false otherwise.
     */
    fun isPlayerInGuild(playerId: UUID, guildId: UUID): Boolean

    /**
     * Gets the total number of members across all guilds.
     *
     * @return The total count of members.
     */
    fun getTotalCount(): Int

    /**
     * Commits both owner rank changes together, checking the supplied previous rank IDs.
     * Unsupported adapters fail closed; cache state must change only after a committed transfer.
     */
    fun transferOwnership(current: Member, next: Member, demotedRankId: UUID): Boolean = false

    /**
     * Reads a consistent database preview for an existing member of an ownerless guild.
     * This must not infer ownership from a potentially stale repository cache.
     */
    fun previewOwnerRecovery(guildId: UUID, playerId: UUID): OwnerRecoveryPreviewResult =
        OwnerRecoveryPreviewResult.Rejected(OwnerRecoveryRejection.UNSUPPORTED_REPOSITORY)

    /**
     * Atomically recovers an ownerless guild only if all previewed state still matches.
     * The adapter revalidates membership, same-guild ranks, and absence of Owners inside
     * the transaction; it publishes cache changes only after commit. No member is created.
     */
    fun recoverOwnership(preview: OwnerRecoveryPreview): OwnerRecoveryResult =
        OwnerRecoveryResult.Rejected(OwnerRecoveryRejection.UNSUPPORTED_REPOSITORY)
}
