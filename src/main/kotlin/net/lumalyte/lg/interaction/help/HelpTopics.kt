package net.lumalyte.lg.interaction.help

/** Stable help-topic catalog; all player-facing help content lives in locale YAML. */
object HelpTopics {

    const val WIKI_BASE_URL = "https://badgersmc.github.io/LumaGuilds/players"

    val all: List<HelpTopic> = listOf(
        topic("guilds", "create", "join", "leave", "disband", "transfer", "info", "list"),
        topic("homes", "sethome", "home", "homes", "removehome", "setallyhome", "removeallyhome"),
        topic("ranks", "ranks", "menu"),
        topic("chat", "chat", "guild-chat", "allychat", "ally-chat", "modchat", "mod-chat", "announce"),
        topic("alliances", "ally", "enemy", "truce", "neutral"),
        topic("war", "war"),
        topic("progression", "info"),
        topic("vault", "vault", "getvault"),
        topic("identity", "tag", "description", "rename", "emoji"),
        topic("mode", "mode"),
        topic("lfg", "invite", "invites", "decline", "lfg", "kick"),
        topic("bedrock", "wiki"),
    )

    private val bySlugMap: Map<String, HelpTopic> = all.associateBy { it.slug }

    fun bySlug(slug: String): HelpTopic? = bySlugMap[slug.lowercase()]

    private fun topic(slug: String, vararg commands: String): HelpTopic =
        HelpTopic(slug, commands.map(::HelpCommandEntry))
}
