package net.lumalyte.lg.interaction.menus.guild

import net.lumalyte.lg.domain.entities.RankPermission

/** Shared supported permission groups for creation and editing. */
internal object RankPermissionCatalog {
    fun categories(claimsEnabled: Boolean): Map<String, List<RankPermission>> =
        RankPermission.entries.filter { claimsEnabled || it !in claims }.groupBy { permission ->
            when (permission) {
                RankPermission.MANAGE_RANKS, RankPermission.MANAGE_MEMBERS,
                RankPermission.MANAGE_BANNER, RankPermission.MANAGE_EMOJI,
                RankPermission.MANAGE_DESCRIPTION, RankPermission.MANAGE_HOME,
                RankPermission.MANAGE_MODE, RankPermission.MANAGE_GUILD_SETTINGS -> "Guild Management"
                RankPermission.MANAGE_RELATIONS, RankPermission.DECLARE_WAR,
                RankPermission.PLACE_WAR_BANNER, RankPermission.ACCEPT_ALLIANCES,
                RankPermission.MANAGE_PARTIES, RankPermission.SEND_PARTY_REQUESTS,
                RankPermission.ACCEPT_PARTY_INVITES, RankPermission.USE_ALLY_HOMES -> "Diplomacy"
                RankPermission.SEND_ANNOUNCEMENTS, RankPermission.SEND_PINGS,
                RankPermission.MODERATE_CHAT -> "Communication"
                RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS,
                RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS,
                RankPermission.DELETE_CLAIMS -> "Claims"
                RankPermission.ACCESS_ADMIN_COMMANDS, RankPermission.BYPASS_RESTRICTIONS,
                RankPermission.VIEW_AUDIT_LOGS, RankPermission.MANAGE_INTEGRATIONS -> "Administrative"
                else -> "Banking"
            }
        }

    private val claims = setOf(RankPermission.MANAGE_CLAIMS, RankPermission.MANAGE_FLAGS,
        RankPermission.MANAGE_PERMISSIONS, RankPermission.CREATE_CLAIMS, RankPermission.DELETE_CLAIMS)
}
