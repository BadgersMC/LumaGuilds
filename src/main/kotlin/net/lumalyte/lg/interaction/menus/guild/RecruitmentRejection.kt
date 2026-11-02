package net.lumalyte.lg.interaction.menus.guild

/** Recognized, public recruitment validation outcomes. */
internal enum class RecruitmentRejection {
    EDIT_DENIED,
    REVIEW_DENIED,
    GUILD_REMOVED,
    METHOD_CHANGED,
    APPLICATION_LIMIT,
    APPLICATION_REMOVED,
    STALE_APPLICATION,
    FEE_CHANGED,
    INELIGIBLE,
    DISCORD_SETUP,
    INVITE_CHANGED,
}
