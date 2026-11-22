package net.lumalyte.lg.acceptance

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.LumaGuilds
import net.lumalyte.lg.application.persistence.QuestRepository
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.QuestService
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URL
import java.net.URLClassLoader
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ProbeClassLoaderTest {
    private val providerLoader = LumaGuilds::class.java.classLoader
    private val consumerUrls = arrayOf(
        Unit::class.java.protectionDomain.codeSource.location,
        ProbeServices::class.java.protectionDomain.codeSource.location,
    )

    private class Consumer(urls: Array<URL>, provider: ClassLoader) : URLClassLoader(urls, provider) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            val isolated = name.startsWith("kotlin.") || name == ProbeServices::class.java.name
            val result = findLoadedClass(name) ?: if (isolated) findClass(name) else super.loadClass(name, false)
            if (resolve) resolveClass(result)
            result
        }
    }

    private fun isolated(check: (ClassLoader, Class<*>) -> Unit) {
        Consumer(consumerUrls, providerLoader).use { consumer ->
            assertNotSame(providerLoader.loadClass("kotlin.reflect.KClass"), consumer.loadClass("kotlin.reflect.KClass"))
            check(consumer, consumer.loadClass(ProbeServices::class.java.name))
        }
    }

    @Test fun `Java bridge returns the enabled provider's services across isolated Kotlin loaders`() = isolated { _, bridge ->
        val guilds = mockk<GuildService>()
        val quests = mockk<QuestService>()
        val repository = mockk<QuestRepository>()
        val provider = mockk<LumaGuilds>()
        every { provider.isEnabled } returns true
        every { provider.getGuildService() } returns guilds
        every { provider.getQuestService() } returns quests
        every { provider.getQuestRepository() } returns repository
        val bound = bridge.getMethod("bind", Plugin::class.java).invoke(null, provider)
        assertSame(guilds, bridge.getMethod("getGuilds").invoke(bound))
        assertSame(quests, bridge.getMethod("getQuests").invoke(bound))
        assertSame(repository, bridge.getMethod("getRepository").invoke(bound))
    }

    @Test fun `wrong provider loader fails closed`() = isolated { consumer, bridge ->
        val foreign = Proxy.newProxyInstance(consumer, arrayOf(Plugin::class.java)) { _, _, _ ->
            error("Foreign plugin was consulted")
        }
        val failure = assertFailsWith<InvocationTargetException> {
            bridge.getMethod("bind", Plugin::class.java).invoke(null, foreign)
        }
        assertIs<IllegalStateException>(failure.cause)
    }

    @Test fun `an unavailable service propagates its own provider failure`() = isolated { _, bridge ->
        val provider = mockk<LumaGuilds>()
        val closed = IllegalStateException("runtime closed")
        every { provider.isEnabled } returns true
        every { provider.getQuestRepository() } throws closed
        val bound = bridge.getMethod("bind", Plugin::class.java).invoke(null, provider)
        val failure = assertFailsWith<InvocationTargetException> {
            bridge.getMethod("getRepository").invoke(bound)
        }
        assertSame(closed, failure.cause)
    }

    @Test fun `disabled Guilds fails closed`() = isolated { _, bridge ->
        val provider = mockk<LumaGuilds>()
        every { provider.isEnabled } returns false
        val failure = assertFailsWith<InvocationTargetException> {
            bridge.getMethod("bind", Plugin::class.java).invoke(null, provider)
        }
        assertIs<IllegalStateException>(failure.cause)
    }
}
