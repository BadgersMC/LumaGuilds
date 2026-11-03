package net.lumalyte.lg.acceptance

import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext

/** Isolated provider fixture: no server, database, player or external connection. */
object ProviderKoinFixture {
    @JvmStatic fun start() {
        val application = KoinApplication.init()
        application.koin.declare("provider-value")
        GlobalContext.startKoin(application)
    }
}
