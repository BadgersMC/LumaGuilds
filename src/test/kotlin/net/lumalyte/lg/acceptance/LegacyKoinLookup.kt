package net.lumalyte.lg.acceptance

import org.koin.core.context.GlobalContext

/** The original production-failing call, retained only as a regression control. */
object LegacyKoinLookup {
    @JvmStatic fun resolve(): String = GlobalContext.get().get<String>()
}
