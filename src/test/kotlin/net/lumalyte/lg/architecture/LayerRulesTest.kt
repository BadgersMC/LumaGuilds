package net.lumalyte.lg.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.jupiter.api.Test

class LayerRulesTest {

    @Test
    fun `domain layer depends on nothing outside domain and kotlin stdlib`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("domain", "net.lumalyte.lg.domain..")
            val application = Layer("application", "net.lumalyte.lg.application..")
            val infrastructure = Layer("infrastructure", "net.lumalyte.lg.infrastructure..")
            domain.dependsOnNothing()
        }
    }

    @Test
    fun `application layer depends only on domain`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("domain", "net.lumalyte.lg.domain..")
            val application = Layer("application", "net.lumalyte.lg.application..")
            val infrastructure = Layer("infrastructure", "net.lumalyte.lg.infrastructure..")
            application.dependsOn(domain)
        }
    }

    @Test
    fun `infrastructure layer depends only on application and domain`() {
        Konsist.scopeFromProduction().assertArchitecture {
            val domain = Layer("domain", "net.lumalyte.lg.domain..")
            val application = Layer("application", "net.lumalyte.lg.application..")
            val infrastructure = Layer("infrastructure", "net.lumalyte.lg.infrastructure..")
            infrastructure.dependsOn(application, domain)
        }
    }
}
