package net.lumalyte.lg.interaction.menus

import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.interaction.menus.common.ConfirmationMenu
import net.lumalyte.lg.interaction.menus.guild.GuildInfoMenu
import net.lumalyte.lg.interaction.menus.guild.GuildMemberListMenu
import net.lumalyte.lg.interaction.menus.guild.GuildSelectionMenu
import net.lumalyte.lg.interaction.menus.guild.PartyCreationMenu
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.reflect.KClass

private typealias MenuBuilder = (MenuFactory, MenuNavigator, Player, Array<out Any?>) -> Menu

private val MENU_BUILDERS: Map<KClass<out Menu>, MenuBuilder> =
    mapOf(
        ConfirmationMenu::class to ::confirmationMenu,
        GuildSelectionMenu::class to ::selectionMenu,
        GuildInfoMenu::class to ::informationMenu,
        GuildMemberListMenu::class to ::memberListMenu,
        PartyCreationMenu::class to ::partyCreationMenu,
    )

/** Used by the existing public reified factory without changing its dispatch or argument contract. */
@PublishedApi
internal fun MenuFactory.createKnownMenu(
    type: KClass<out Menu>,
    navigator: MenuNavigator,
    player: Player,
    args: Array<out Any?>,
): Menu {
    val build = MENU_BUILDERS[type] ?: throw IllegalArgumentException("Unsupported menu type: ${type.simpleName}")
    return build(this, navigator, player, args)
}

@Suppress("UNCHECKED_CAST") // Existing public vararg boundary: the callback type is erased at runtime.
private fun confirmationMenu(
    factory: MenuFactory,
    navigator: MenuNavigator,
    player: Player,
    args: Array<out Any?>,
): Menu = factory.createConfirmationMenu(navigator, player, args[0] as String, args[1] as () -> Unit)

@Suppress("UNCHECKED_CAST") // Preserve the established mutable selection argument.
private fun selectionMenu(
    factory: MenuFactory,
    navigator: MenuNavigator,
    player: Player,
    args: Array<out Any?>,
): Menu = factory.createGuildSelectionMenu(navigator, player, args[0] as Guild, args[1] as MutableSet<UUID>)

private fun informationMenu(
    factory: MenuFactory,
    navigator: MenuNavigator,
    player: Player,
    args: Array<out Any?>,
): Menu = factory.createGuildInfoMenu(navigator, player, args[0] as Guild)

private fun memberListMenu(
    factory: MenuFactory,
    navigator: MenuNavigator,
    player: Player,
    args: Array<out Any?>,
): Menu = factory.createGuildMemberListMenu(navigator, player, args[0] as Guild)

private fun partyCreationMenu(
    factory: MenuFactory,
    navigator: MenuNavigator,
    player: Player,
    args: Array<out Any?>,
): Menu = factory.createPartyCreationMenu(navigator, player, args[0] as Guild)
