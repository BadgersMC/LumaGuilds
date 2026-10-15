package net.lumalyte.lg.interaction.menus.guild

import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.infrastructure.services.GuildDialogAction
import net.lumalyte.lg.infrastructure.services.GuildDialogItem
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildMenuDialog
import net.lumalyte.lg.utils.NexoItemProvider
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal enum class GuildSettingsGroup(val key: String, val icon: String) {
    INFORMATION("information", "lg_nav_info"),
    APPEARANCE("appearance", "lg_tag"),
    ACCESS("access", "lg_home"),
    ;

    fun label(lang: LangService): Component = when (this) {
        INFORMATION -> lang.msg("guild_dialogs.settings.information")
        APPEARANCE -> lang.msg("guild_dialogs.settings.appearance")
        ACCESS -> lang.msg("guild_dialogs.settings.access")
    }
}

/** Group selection is presentation only; existing editors retain their service authorization. */
internal class GuildSettingsDialog(private val lang: LangService) {
    private val presenter = GuildMenuDialog()

    fun show(
        player: Player,
        valid: () -> Boolean,
        group: (GuildSettingsGroup) -> Unit,
        recruitment: () -> Unit,
        chat: () -> Unit,
        back: () -> Unit,
    ): Boolean {
        val actions = GuildSettingsGroup.entries.map { entry ->
            GuildDialogAction(entry.label(lang), Component.empty()) { group(entry) }
        } + listOf(
            GuildDialogAction(lang.msg("recruitment.manage"), Component.empty(), recruitment),
            GuildDialogAction(lang.msg("guild_dialogs.settings.chat"), Component.empty(), chat),
        )
        return presenter.show(
            player,
            GuildDialogPage(
                lang.msg("guild_dialogs.settings.title"),
                listOf(GuildDialogItem(
                    NexoItemProvider.getItemStackOrFallback("lg_nav_settings") { ItemStack.of(Material.COMPARATOR) },
                    lang.msg("guild_dialogs.settings.description"),
                )), actions,
                GuildDialogAction(lang.msg("recruitment.dialog.back"), Component.empty(), back),
            ), valid,
        ) { player.sendMessage(lang.msg("recruitment.feedback.failed")) }
    }
}
