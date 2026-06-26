package net.lumalyte.lg.interaction.help

/**
 * Source of truth for player-facing help topics.
 *
 * Every entry has a matching wiki page at `wiki/docs/players/<slug>.md`.
 * CI fails the build if any slug here lacks a wiki page, or vice versa.
 */
object HelpTopics {

    /** Base URL for wiki deep-links surfaced in the in-game help. */
    const val WIKI_BASE_URL = "https://badgersmc.github.io/LumaGuilds/players"

    val all: List<HelpTopic> = listOf(
        HelpTopic(
            slug = "guilds",
            displayName = "Guilds",
            summary = "Create, join, leave, transfer, and disband guilds.",
            commands = listOf(
                HelpCommandEntry("/g create <name>", "Create a new guild.", "/g create "),
                HelpCommandEntry("/g join <guild>", "Request to join a guild.", "/g join "),
                HelpCommandEntry("/g leave", "Leave your current guild.", "/g leave"),
                HelpCommandEntry("/g disband", "Disband your guild (owner only).", "/g disband"),
                HelpCommandEntry("/g transfer <player>", "Transfer ownership.", "/g transfer "),
                HelpCommandEntry("/g info [guild]", "View guild information.", "/g info "),
                HelpCommandEntry("/g list", "Browse all guilds.", "/g list"),
            ),
        ),
        HelpTopic(
            slug = "homes",
            displayName = "Homes",
            summary = "Set, name, visit, and restrict access to guild homes.",
            commands = listOf(
                HelpCommandEntry("/g sethome [name]", "Set a home at your current location.", "/g sethome "),
                HelpCommandEntry("/g home [name]", "Teleport to a guild home.", "/g home "),
                HelpCommandEntry("/g homes", "List your guild's homes.", "/g homes"),
                HelpCommandEntry("/g removehome <name>", "Remove a named home.", "/g removehome "),
                HelpCommandEntry("/g setallyhome", "Set your guild's ally-home.", "/g setallyhome"),
                HelpCommandEntry("/g removeallyhome", "Remove your guild's ally-home.", "/g removeallyhome"),
            ),
        ),
        HelpTopic(
            slug = "ranks",
            displayName = "Ranks & Permissions",
            summary = "Create ranks, set permissions, and manage member rank assignments.",
            commands = listOf(
                HelpCommandEntry("/g ranks", "Open the rank management menu.", "/g ranks"),
                HelpCommandEntry("/g menu", "Open the guild control panel.", "/g menu"),
            ),
        ),
        HelpTopic(
            slug = "chat",
            displayName = "Chat",
            summary = "Toggle guild and ally chat, and customize how your tag appears in messages.",
            commands = listOf(
                HelpCommandEntry("/g chat", "Toggle guild chat on/off.", "/g chat"),
                HelpCommandEntry("/g allychat", "Toggle ally chat on/off.", "/g allychat"),
            ),
        ),
        HelpTopic(
            slug = "alliances",
            displayName = "Alliances & Diplomacy",
            summary = "Form alliances, declare enemies, sign truces, and manage ally-home access.",
            commands = listOf(
                HelpCommandEntry("/g ally <guild>", "Request or accept an alliance.", "/g ally "),
                HelpCommandEntry("/g enemy <guild>", "Mark a guild as enemy.", "/g enemy "),
                HelpCommandEntry("/g truce <guild>", "Sign a truce.", "/g truce "),
                HelpCommandEntry("/g neutral <guild>", "Clear a relation.", "/g neutral "),
            ),
        ),
        HelpTopic(
            slug = "war",
            displayName = "War",
            summary = "Declare and fight wars between guilds.",
            commands = listOf(
                HelpCommandEntry("/g war <guild>", "Open the war control flow.", "/g war "),
            ),
        ),
        HelpTopic(
            slug = "progression",
            displayName = "Progression & Levels",
            summary = "Earn guild XP from member activity, level up, and unlock perks.",
            commands = listOf(
                HelpCommandEntry("/g info", "See your guild's level and XP.", "/g info"),
            ),
        ),
        HelpTopic(
            slug = "vault",
            displayName = "Vault",
            summary = "Use the shared guild vault for storing items.",
            commands = listOf(
                HelpCommandEntry("/g vault", "Open the guild vault.", "/g vault"),
                HelpCommandEntry("/g getvault", "Get a vault chest item.", "/g getvault"),
            ),
        ),
        HelpTopic(
            slug = "identity",
            displayName = "Tags, Banners & Identity",
            summary = "Set your guild's tag, description, and banner.",
            commands = listOf(
                HelpCommandEntry("/g tag [text]", "Set or edit your guild tag.", "/g tag "),
                HelpCommandEntry("/g desc <text>", "Set your guild description.", "/g desc "),
                HelpCommandEntry("/g rename <name>", "Rename your guild.", "/g rename "),
                HelpCommandEntry("/g emoji", "Pick a guild emoji.", "/g emoji"),
            ),
        ),
        HelpTopic(
            slug = "mode",
            displayName = "Mode (Peaceful / Hostile)",
            summary = "Switch your guild between peaceful and hostile mode.",
            commands = listOf(
                HelpCommandEntry("/g mode", "Open the mode selection menu.", "/g mode"),
            ),
        ),
        HelpTopic(
            slug = "lfg",
            displayName = "LFG & Invites",
            summary = "Manage invites, decline requests, and use LFG to find a guild.",
            commands = listOf(
                HelpCommandEntry("/g invite <player>", "Invite a player to your guild.", "/g invite "),
                HelpCommandEntry("/g invites", "List your pending invites.", "/g invites"),
                HelpCommandEntry("/g decline <guild>", "Decline an invite.", "/g decline "),
                HelpCommandEntry("/g lfg", "Toggle LFG (looking-for-guild).", "/g lfg"),
                HelpCommandEntry("/g kick <player>", "Kick a member from your guild.", "/g kick "),
            ),
        ),
        HelpTopic(
            slug = "bedrock",
            displayName = "Bedrock differences",
            summary = "Where LumaGuilds behaves differently on Bedrock/Geyser.",
            commands = listOf(
                HelpCommandEntry(
                    syntax = "(see wiki)",
                    blurb = "Bedrock menus open as forms; clickable chat maps to taps.",
                    prefill = "",
                ),
            ),
        ),
    )

    private val bySlugMap: Map<String, HelpTopic> = all.associateBy { it.slug }

    fun bySlug(slug: String): HelpTopic? = bySlugMap[slug.lowercase()]
}
