package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.rewards.FanfareAudience
import net.lumalyte.lg.domain.rewards.FanfareCue
import net.lumalyte.lg.domain.rewards.PrestigeFanfareScore
import net.lumalyte.lg.domain.rewards.PrestigeRankUp

/** One previewable guild sound: what it is, when players hear it, and its cues. */
data class GuildSoundEntry(val id: String, val group: String, val label: String, val heardWhen: String, val cues: List<SoundCue>) {
    /** Ticks until the last cue has started and any timed stop has passed, plus a short tail. */
    val lengthTicks: Long
        get() = (cues.maxOfOrNull { it.delayTicks + (it.stopAfterTicks ?: TAIL_TICKS) } ?: 0L).coerceAtLeast(TAIL_TICKS)

    private companion object {
        const val TAIL_TICKS = 30L
    }
}

/**
 * REQ-254: every sound LumaGuilds plays for a guild event, so staff can audition them with
 * `/lumaguilds sounds`. War, prestige and rank-up entries read their cues from the same
 * definitions the live events use, so a preview can't drift from the real thing.
 */
object GuildSoundCatalog {
    val entries: List<GuildSoundEntry> = buildList {
        add(moment("war-declared", "Declaration of war", "A guild declares war on another", GuildMoment.DECLARED))
        add(moment("war-begins", "War begins", "A declared war is accepted and starts", GuildMoment.BEGINS))
        add(moment("war-victory", "War victory", "Your guild wins a war", GuildMoment.VICTORY))
        add(moment("war-defeat", "War defeat", "Your guild loses a war", GuildMoment.DEFEAT))
        add(moment("war-kill", "War kill", "You kill an enemy guild member during a war", GuildMoment.KILL))
        add(moment("peace", "Peace", "A war ends by agreement", GuildMoment.PEACE))
        add(moment("alliance", "Alliance formed", "Two guilds become allies", GuildMoment.ALLIANCE))
        for (tier in 1..6) {
            val numeral = PrestigeNumeral.of(tier)
            add(GuildSoundEntry("rankup-$tier", "Prestige", "Prestige $numeral rank-up",
                "Guild members watching the rank-up animation", PrestigeRankUp.memberCues(tier).map(::cue)))
            add(GuildSoundEntry("prestige-$tier", "Prestige", "Prestige $numeral fanfare",
                "Guild members without the animation (Bedrock, no pack)",
                PrestigeFanfareScore.cues(tier, FanfareAudience.MEMBERS).map(::cue)))
            add(GuildSoundEntry("prestige-$tier-server", "Prestige", "Prestige $numeral, rest of the server",
                "Everyone outside the guild when it prestiges",
                PrestigeFanfareScore.cues(tier, FanfareAudience.SERVER).map(::cue)))
        }
        add(simple("level-up", "Progression", "Guild level up", "Your guild gains a level", "entity.player.levelup", 1f, 1f))
        add(GuildSoundEntry("level-up-fanfare", "Progression", "Guild level up with announcement perk",
            "Your guild gains a level and has the announcement sound perk", listOf(
                SoundCue("ui.toast.challenge_complete", 1f, 1f), SoundCue("entity.player.levelup", 1f, 1.2f))))
        add(GuildSoundEntry("home-teleport", "Guild", "Home teleport (Quiet portal)",
            "You arrive at a guild or allied home", HomeArrivalSound.cues))
        add(simple("member-login", "Guild", "Member login", "A guildmate comes online", "block.note_block.chime", .35f, 1.35f))
        add(simple("announcement", "Guild", "Guild announcement", "A guild announcement or ping", "block.note_block.bell", .5f, 1f))
        add(simple("vault-open", "Guild", "Vault opens", "You open the guild vault", "block.ender_chest.open", 1f, 1f))
        add(simple("vault-placed", "Guild", "Second vault placed", "The Prestige VI second vault is placed", "block.ender_chest.open", 1f, 1.2f))
        add(GuildSoundEntry("vault-get", "Guild", "Vault chest received", "You use /g getvault", listOf(
            SoundCue("entity.item.pickup", 1f, 1.2f), SoundCue("block.chest.open", 1f, 1f))))
        add(GuildSoundEntry("vault-blocked", "Guild", "Vault tampering blocked", "A piston or hopper is stopped at a guild vault (heard nearby)", listOf(
            SoundCue("block.piston.contract", 1f, .5f), SoundCue("entity.item.break", 1f, 1f))))
        add(simple("bank-coins", "Guild", "Bank deposit or withdrawal", "Gold moves in or out of the guild bank", "entity.item.pickup", 1f, 1f))
        add(simple("bank-too-fast", "Guild", "Bank too fast", "Clicking bank buttons faster than once a second", "block.note_block.bass", 1f, .5f))
        add(simple("guild-left", "Guild", "Left the guild", "You confirm leaving your guild", "entity.villager.yes", 1f, 1f))
        add(simple("guild-disband", "Guild", "Guild disbanded", "The owner confirms disbanding", "entity.ender_dragon.growl", 1f, 1f))
        add(simple("claim-named", "Guild", "Claim renamed", "A claim is given a new name", "block.vault.open_shutter", 1f, 1f))
        add(simple("replant", "Guild", "Harvest replant", "Right-click harvesting a crop replants it", "item.crop.plant", 1f, 1f))
        add(simple("war-declare-menu", "War and diplomacy", "War declared from the menu", "You send a war declaration from the war menu", "entity.lightning_bolt.thunder", 1f, 1f))
        add(simple("enemy-declared", "War and diplomacy", "Enemy declared", "Your guild marks another guild as an enemy", "entity.ender_dragon.growl", 1f, .8f))
        add(simple("war-rejected", "War and diplomacy", "War declaration rejected", "Your guild turns down a war (the other guild hears it lower)", "block.note_block.bass", 1f, .8f))
        add(simple("truce-request", "War and diplomacy", "Truce requested", "You send a truce request", "block.note_block.bell", 1f, 1.2f))
        add(simple("peace-request", "War and diplomacy", "Peace requested", "You ask an enemy for peace", "block.note_block.bell", 1f, 1.5f))
        add(simple("ally-removed", "War and diplomacy", "Alliance ended", "You end an alliance", "entity.item.break", 1f, .8f))
        add(simple("quest-challenge", "Progression", "Big quest or war toast", "Challenge toasts: hard quests, war declared on you, victory", "ui.toast.challenge_complete", 1f, 1f))
        add(simple("menu-click", "Menus", "Menu click", "Most menu buttons", "ui.button.click", 1f, 1f))
        add(simple("menu-confirm", "Menus", "Confirmed", "A menu action succeeds", "entity.experience_orb.pickup", 1f, 1.2f))
        add(simple("menu-success", "Menus", "Big success", "Creating a guild, accepting an invite and similar", "entity.player.levelup", 1f, 1f))
        add(simple("menu-denied", "Menus", "Denied", "An action is refused", "entity.villager.no", 1f, 1f))
        add(simple("war-warning", "Menus", "War warning", "Confirming a war declaration or enemy status", "entity.ender_dragon.growl", 1f, .8f))
    }

    val ids: List<String> = entries.map { it.id }

    fun find(id: String): GuildSoundEntry? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

    private fun moment(id: String, label: String, heardWhen: String, moment: GuildMoment) =
        GuildSoundEntry(id, "War and diplomacy", label, heardWhen, moment.cues)

    private fun simple(id: String, group: String, label: String, heardWhen: String, sound: String, volume: Float, pitch: Float) =
        GuildSoundEntry(id, group, label, heardWhen, listOf(SoundCue(sound, volume, pitch)))

    private fun cue(cue: FanfareCue) = SoundCue(cue.sound, cue.volume, cue.pitch, cue.tick.toLong())
}
