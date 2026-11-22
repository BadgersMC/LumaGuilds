package net.lumalyte.lg.interaction.menus.guild

import io.mockk.mockk
import net.lumalyte.lg.di.legacyMenuServices
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.MenuServices
import net.lumalyte.lg.interaction.menus.testMenuServices
import org.koin.core.context.GlobalContext

/** Keeps existing behavior fixtures on their registered mocks while constructors become explicit. */
internal fun javaALegacyMenuServices(): MenuServices {
    val koin = GlobalContext.getOrNull() ?: return testMenuServices()
    val owner = koin.getOrNull<MenuFactory>() ?: mockk(relaxed = true)
    return koin.getScope("_root_").legacyMenuServices(owner)
}
