package net.lumalyte.lg.acceptance

import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import kotlin.test.*

class ProbeClassLoaderTest {
    private val stdlib = Unit::class.java.protectionDomain.codeSource.location
    private val providerUrls = arrayOf(stdlib, Koin::class.java.protectionDomain.codeSource.location,
        ProviderKoinFixture::class.java.protectionDomain.codeSource.location)
    private val consumerUrls = arrayOf(stdlib, ProbeServices::class.java.protectionDomain.codeSource.location,
        LegacyKoinLookup::class.java.protectionDomain.codeSource.location)

    private class Consumer(urls: Array<URL>, provider: ClassLoader) : URLClassLoader(urls, provider) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            val child = name.startsWith("kotlin.") || name == ProbeServices::class.java.name ||
                name == LegacyKoinLookup::class.java.name
            val result = findLoadedClass(name) ?: if (child) findClass(name) else super.loadClass(name, false)
            if (resolve) resolveClass(result)
            result
        }
    }

    private fun isolated(check: (ClassLoader, ClassLoader) -> Unit) {
        URLClassLoader(providerUrls, ClassLoader.getPlatformClassLoader()).use { provider ->
            provider.loadClass(ProviderKoinFixture::class.java.name).getMethod("start").invoke(null)
            Consumer(consumerUrls, provider).use { consumer ->
                assertNotSame(provider.loadClass("kotlin.reflect.KClass"), consumer.loadClass("kotlin.reflect.KClass"))
                check(provider, consumer)
            }
        }
    }

    @Test fun `old reified lookup reproduces the live loader constraint`() = isolated { _, consumer ->
        val failure = assertFailsWith<InvocationTargetException> {
            consumer.loadClass(LegacyKoinLookup::class.java.name).getMethod("resolve").invoke(null)
        }
        assertIs<LinkageError>(failure.cause)
        assertContains(failure.cause!!.message.orEmpty(), "loader constraint")
    }

    @Test fun `Java entry point resolves the provider binding with separate Kotlin loaders`() = isolated { provider, consumer ->
        val bridge = consumer.loadClass(ProbeServices::class.java.name)
        bridge.getMethod("requireOwner", ClassLoader::class.java).invoke(null, provider)
        assertEquals("provider-value", bridge.getMethod("resolve", Class::class.java).invoke(null, String::class.java))
    }

    @Test fun `wrong provider owner fails closed`() = isolated { _, consumer ->
        val failure = assertFailsWith<InvocationTargetException> {
            consumer.loadClass(ProbeServices::class.java.name).getMethod("requireOwner", ClassLoader::class.java)
                .invoke(null, consumer)
        }
        assertIs<IllegalStateException>(failure.cause)
    }

    @Test fun `missing service cannot silently bind from another provider`() = isolated { _, consumer ->
        val failure = assertFailsWith<InvocationTargetException> {
            consumer.loadClass(ProbeServices::class.java.name).getMethod("resolve", Class::class.java)
                .invoke(null, Int::class.javaObjectType)
        }
        assertEquals("org.koin.core.error.NoDefinitionFoundException", failure.cause!!::class.java.name)
    }
}
