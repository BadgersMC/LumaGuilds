package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import net.badgersmc.nexus.i18n.LangService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.infrastructure.services.GuildDialogPage
import net.lumalyte.lg.infrastructure.services.GuildDialogPresenter
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

internal abstract class RecruitmentDialogFixture {
    protected val player = mockk<Player>(relaxed = true)
    protected val lang = mockk<LangService>()
    protected val guilds = mockk<GuildService>()
    protected var renderedPage: GuildDialogPage? = null
    protected val presenter =
        GuildDialogPresenter { _, page, _, _ ->
            renderedPage = page
            true
        }
    protected val controller = RecruitmentController(player, {})
    protected var exits = 0
    protected var closes = 0

    @BeforeEach
    fun setup() {
        stopKoin()
        org.mockbukkit.mockbukkit.MockBukkit.mock()
        every { lang.raw(any()) } answers { firstArg() }
        every { guilds.getPlayerGuilds(any()) } returns emptySet()
        startKoin {
            modules(
                module {
                    single { lang }
                    single { guilds }
                },
            )
        }
    }

    @AfterEach
    fun cleanup() {
        stopKoin()
        unmockkAll()
        org.mockbukkit.mockbukkit.MockBukkit.unmock()
    }

    protected fun menu(): RecruitmentDialogMenu {
        return RecruitmentDialogMenu(
            controller,
            RecruitmentDialogCallbacks({ true }, { exits++ }, { closes++ }),
            presenter,
        )
    }
}
