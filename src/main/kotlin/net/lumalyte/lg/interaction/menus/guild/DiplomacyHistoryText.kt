package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.domain.entities.DiplomacyEntry
import net.lumalyte.lg.domain.entities.DiplomacyKind
import net.lumalyte.lg.infrastructure.i18n.gui
import org.bukkit.Bukkit
import org.bukkit.Material
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** REQ-204: wording and icons for diplomacy history entries, shared by Java and Bedrock. */
internal object DiplomacyHistoryText {
    fun icon(kind: DiplomacyKind): Pair<String, Material> = when (kind) {
        DiplomacyKind.ALLIED, DiplomacyKind.ALLIANCE_ENDED -> "lg_alliance" to Material.LIME_BANNER
        DiplomacyKind.DECLARED_ENEMIES, DiplomacyKind.DECLARED_ENEMIES_BY -> "lg_enemy" to Material.RED_BANNER
        DiplomacyKind.TRUCE_STARTED, DiplomacyKind.TRUCE_ENDED -> "lg_truce" to Material.WHITE_BANNER
        DiplomacyKind.PEACE -> "lg_peace" to Material.FEATHER
        DiplomacyKind.WAR_WON, DiplomacyKind.WAR_LOST, DiplomacyKind.WAR_DRAWN -> "lg_nav_warfare" to Material.IRON_SWORD
    }

    fun title(lang: LangService, entry: DiplomacyEntry, guild: String): Component = when (entry.kind) {
        DiplomacyKind.ALLIED -> lang.gui("diplomacy_history.kind.allied", "guild" to guild)
        DiplomacyKind.ALLIANCE_ENDED -> lang.gui("diplomacy_history.kind.alliance_ended", "guild" to guild)
        DiplomacyKind.DECLARED_ENEMIES -> lang.gui("diplomacy_history.kind.declared_enemies", "guild" to guild)
        DiplomacyKind.DECLARED_ENEMIES_BY -> lang.gui("diplomacy_history.kind.declared_enemies_by", "guild" to guild)
        DiplomacyKind.TRUCE_STARTED -> lang.gui("diplomacy_history.kind.truce_started", "guild" to guild, "days" to (entry.days ?: 0))
        DiplomacyKind.TRUCE_ENDED -> lang.gui("diplomacy_history.kind.truce_ended", "guild" to guild)
        DiplomacyKind.PEACE -> lang.gui("diplomacy_history.kind.peace", "guild" to guild)
        DiplomacyKind.WAR_WON -> lang.gui("diplomacy_history.kind.war_won", "guild" to guild)
        DiplomacyKind.WAR_LOST -> lang.gui("diplomacy_history.kind.war_lost", "guild" to guild)
        DiplomacyKind.WAR_DRAWN -> lang.gui("diplomacy_history.kind.war_drawn", "guild" to guild)
    }

    fun dated(lang: LangService, entry: DiplomacyEntry): Component {
        val date = entry.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(lang.raw("menu.guild_info.overview.date_format")))
        val actor = entry.actorId?.let { Bukkit.getOfflinePlayer(it).name }
        return if (actor == null) lang.gui("diplomacy_history.when", "date" to date)
        else lang.gui("diplomacy_history.when_by", "date" to date, "player" to actor)
    }

    fun bedrockLine(lang: LangService, entry: DiplomacyEntry, guild: String): String {
        val plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        return plain.serialize(title(lang, entry, guild)) + "\n" + plain.serialize(dated(lang, entry))
    }
}
