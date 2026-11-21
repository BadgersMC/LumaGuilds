package net.lumalyte.lg.interaction.menus

import io.mockk.mockk
import net.lumalyte.lg.di.legacyMenuServices
import org.koin.core.context.GlobalContext

/** Adapts existing Koin-backed behavioral fixtures to the explicit production composition root. */
internal fun javaBLegacyTestMenuServices(): MenuServices {
    val koin = GlobalContext.get()
    val factory = koin.getOrNull<MenuFactory>() ?: mockk(relaxed = true)
    return koin.getScope("_root_").legacyMenuServices(factory)
}
