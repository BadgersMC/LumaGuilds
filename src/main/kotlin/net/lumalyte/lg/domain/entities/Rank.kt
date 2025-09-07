package net.lumalyte.lg.domain.entities

import java.util.UUID

/**
 * Represents a rank within a guild.
 *
 * @property id The unique identifier for the rank.
 * @property guildId The unique identifier of the guild this rank belongs to.
 * @property name The name of the rank.
 * @property priority The priority/order of the rank (lower numbers = higher priority).
 * @property permissions The set of permissions associated with this rank.
 * @property icon The material icon representing this rank (optional).
 */
data class Rank(
    val id: UUID,
    val guildId: UUID,
    val name: String,
    val priority: Int = 0,
    val permissions: Set<RankPermission> = emptySet(),
    val icon: String? = null
) {
    init {
        require(name.length in 1..24) { "Rank name must be between 1 and 24 characters." }
        require(priority >= 0) { "Rank priority must be non-negative." }
    }
}

/**
 * Represents permissions that can be assigned to ranks.
 */
enum class RankPermission {
    // Guild management
    MANAGE_RANKS,
    MANAGE_MEMBERS,
    MANAGE_BANNER,
    MANAGE_EMOJI,
    MANAGE_HOME,
    MANAGE_MODE,
    MANAGE_GUILD_SETTINGS,
    
    // Relations & Diplomacy
    MANAGE_RELATIONS,
    DECLARE_WAR,
    ACCEPT_ALLIANCES,
    MANAGE_PARTIES,
    SEND_PARTY_REQUESTS,
    ACCEPT_PARTY_INVITES,
    
    // Banking & Economy
    DEPOSIT_TO_BANK,
    WITHDRAW_FROM_BANK,
    VIEW_BANK_TRANSACTIONS,
    EXPORT_BANK_DATA,
    MANAGE_BANK_SETTINGS,
    
    // Communication
    SEND_ANNOUNCEMENTS,
    SEND_PINGS,
    MODERATE_CHAT,
    
    // Claims & Territory
    MANAGE_CLAIMS,
    MANAGE_FLAGS,
    MANAGE_PERMISSIONS,
    CREATE_CLAIMS,
    DELETE_CLAIMS,
    
    // Special Roles
    ACCESS_ADMIN_COMMANDS,
    BYPASS_RESTRICTIONS,
    VIEW_AUDIT_LOGS,
    MANAGE_INTEGRATIONS
}
