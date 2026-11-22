package net.lumalyte.lg.interaction.menus.bedrock

import io.mockk.every
import io.mockk.mockk
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.PlatformDetectionService
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.interaction.menus.MenuFactory
import net.lumalyte.lg.interaction.menus.testMenuServices
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.impl.FormDefinition
import org.geysermc.cumulus.form.impl.FormDefinitions
import java.util.UUID
import java.util.logging.Logger
import kotlin.reflect.KClass

/**
 * Koin-free fixture for the Bedrock worker tests (REQ-NEXUS-DI-1..3): an explicit MenuFactory whose
 * MenuServices come from `testMenuServices` and whose Bedrock bundle is built by an explicit provider.
 */
internal class BedrockWorkerFixture(
    overrides: Map<KClass<*>, Any?> = emptyMap(),
    forceBedrock: Boolean = true,
) {
    val plugin: Plugin = mockk(relaxed = true)
    val lang: LangService = mockk<LangService>().also { lang ->
        every { lang.msg(any(), *anyVararg()) } answers { Component.text(firstArg<String>()) }
    }
    val config: ConfigService = mockk<ConfigService>().also { config ->
        every { config.loadConfig() } returns MainConfig().apply {
            bedrock.bedrockMenusEnabled = true
            bedrock.forceBedrockMenus = forceBedrock
        }
    }
    var bedrockProviderCalls = 0
        private set
    var servicesProviderCalls = 0
        private set
    lateinit var dependencies: BedrockMenuDependencies
        private set

    val factory: MenuFactory = MenuFactory(
        platformDetectionService = mockk<PlatformDetectionService>(relaxed = true),
        configService = config,
        logger = Logger.getAnonymousLogger(),
        lang = lang,
        bedrockDependencyProvider = { owner ->
            bedrockProviderCalls++
            BedrockMenuDependencies(
                menuFactory = owner,
                localization = mockk(relaxed = true),
                plugin = plugin,
                lang = lang,
                cache = mockk(relaxed = true),
                validation = mockk(relaxed = true),
                config = config,
            ).also { dependencies = it }
        },
        menuServicesProvider = { owner ->
            servicesProviderCalls++
            testMenuServices(owner, mapOf(
                LangService::class to lang,
                Plugin::class to plugin,
                ConfigService::class to config,
            ) + overrides)
        },
    )

    /** The same bundle MenuFactory hands to forms; resolved through the explicit provider. */
    fun deps(): BedrockMenuDependencies = factory.requiredBedrockDependencies()

    val playerId: UUID = UUID.randomUUID()
    val player: Player = mockk<Player>(relaxed = true).also {
        every { it.uniqueId } returns playerId
        every { it.name } returns "BedrockTester"
    }
}

/** Reads the explicit bundle stored by BaseBedrockMenu. */
internal fun BaseBedrockMenu.storedDependencies(): BedrockMenuDependencies? =
    BaseBedrockMenu::class.java.getDeclaredField("dependencies").apply { isAccessible = true }.get(this)
        as BedrockMenuDependencies?

/** Reads a delegated property's backing Lazy without resolving it. */
internal fun Any.lazyDelegate(property: String): Lazy<*> =
    javaClass.getDeclaredField("$property\$delegate").apply { isAccessible = true }.get(this) as Lazy<*>

/** Simulates a Bedrock client response through Cumulus' own form definition. */
@Suppress("UNCHECKED_CAST")
internal fun Form.respond(raw: String) {
    val definition = FormDefinitions.instance().definitionFor(this) as FormDefinition<Form, *, *>
    definition.handleFormResponse(this, raw)
}
