package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class GuildBankLocalizationTest {
    @TempDir lateinit var dataFolder: Path
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `bank menu runtime keys resolve to meaningful leaf strings`() {
        val lang = langService()
        val expected = linkedMapOf(
            "menu.bank.back_to_control_panel" to "Back to Control Panel",
            "menu.bank.custom.deposit" to "Custom Deposit",
            "menu.bank.custom.withdraw" to "Custom Withdraw",
            "menu.bank.history.no_transactions" to "No transactions yet",
            "menu.bank.quick.deposit.100" to "Deposit 100",
            "menu.bank.quick.deposit.1000" to "Deposit 1,000",
            "menu.bank.quick.deposit.10000" to "Deposit 10,000",
            "menu.bank.quick.deposit.all" to "Deposit All",
            "menu.bank.quick.withdraw.100" to "Withdraw 100",
            "menu.bank.quick.withdraw.1000" to "Withdraw 1,000",
            "menu.bank.quick.withdraw.10000" to "Withdraw 10,000",
            "menu.bank.quick.withdraw.all" to "Withdraw All",
            "menu.bank.history.type.deposit" to "Deposit",
            "menu.bank.history.type.withdrawal" to "Withdrawal",
            "menu.bank.history.type.fee" to "Fee",
            "menu.bank.history.type.deduction" to "Deduction",
        )
        expected.forEach { (key, value) -> assertEquals(value, lang.raw(key), key) }
        assertEquals("Guild Balance: <balance>", lang.raw("menu.bank.balance.current"))
        val balance = plain.serialize(lang.gui("menu.bank.balance.current", "balance" to 1234))
        kotlin.test.assertTrue(balance.contains("1234"))
        kotlin.test.assertFalse(balance.contains("<balance>"))
        kotlin.test.assertFalse(balance.contains("menu.bank"))
    }

    private fun langService() = LangService(
        object : LangHost {
            override val dataFolder: File = this@GuildBankLocalizationTest.dataFolder.toFile()
            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
        },
        Locale("en_US"),
        LumaGuildsLang::class.java,
    )
}
