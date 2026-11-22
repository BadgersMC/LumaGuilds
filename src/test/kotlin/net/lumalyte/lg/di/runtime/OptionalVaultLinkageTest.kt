package net.lumalyte.lg.di.runtime

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Nexus reflects on every live singleton during shutdown, even on servers with no Vault jar. */
class OptionalVaultLinkageTest {
    @ParameterizedTest
    @ValueSource(strings = ["net.lumalyte.lg.LumaGuilds", "net.lumalyte.lg.infrastructure.services.BankServiceBukkit"])
    fun `runtime owners remain inspectable without Vault`(owner: String) {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("net.milkbowl.vault.")) throw ClassNotFoundException(name)
                if (name != owner) return super.loadClass(name, resolve)
                findLoadedClass(name)?.let { return it }
                val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
                return defineClass(name, bytes, 0, bytes.size)
            }
        }
        val type = loader.loadClass(owner)
        type.declaredMethods
        type.declaredFields
    }
}
