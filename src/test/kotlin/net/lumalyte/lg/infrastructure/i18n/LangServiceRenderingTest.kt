package net.lumalyte.lg.infrastructure.i18n

import net.badgersmc.nexus.i18n.LangHost
import net.badgersmc.nexus.i18n.LangService
import net.badgersmc.nexus.i18n.Locale
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LangServiceRenderingTest {
    @Test
    fun `shipped bank titles are compact and independent of guild length`() {
        val lang = langService()
        assertEquals(lang.guiTitle("menu.bank.history.compact_title"), lang.bankHistoryTitle("G".repeat(32)))
        assertEquals(lang.guiTitle("menu.bank.history.member_filter.title"), lang.bankHistoryTitle("G".repeat(32), true))
    }

    @Test
    fun `persisted original bank title compacts without rewriting locale file`() {
        val locale = dataFolder.resolve("lang/en_US.yml").toFile()
        locale.parentFile.mkdirs()
        val original = "menu:\n  bank:\n    history:\n      title: '<white>Bank Transaction History - <guild>'\n"
        locale.writeText(original)
        val lang = langService()
        assertEquals(lang.guiTitle("menu.bank.history.compact_title"), lang.bankHistoryTitle("GoldProofOct26"))
        assertEquals(original, locale.readText())
    }

    @Test
    fun `custom bank titles keep interpolation in history and member filter`() {
        val locale = dataFolder.resolve("lang/en_US.yml").toFile()
        locale.parentFile.mkdirs()
        val original = "menu:\n  bank:\n    history:\n      title: '<aqua>Ledger: <guild>'\n"
        locale.writeText(original)
        val lang = langService()
        val expected = lang.guiTitle("menu.bank.history.title", "guild" to "North Farm")
        assertEquals(expected, lang.bankHistoryTitle("North Farm"))
        assertEquals(expected, lang.bankHistoryTitle("North Farm", true))
        assertEquals(original, locale.readText())
    }

    @Test
    fun `new tree hints do not concatenate persisted legacy instruction sentences`() {
        val locale = dataFolder.resolve("lang/en_US.yml").toFile()
        locale.parentFile.mkdirs()
        val old = "achievements:\n  vanilla: 'Full vanilla advancement tree'\n  open_vanilla: 'Legacy full instructions with default L.'\n"
        locale.writeText(old)
        val lang = langService()
        assertEquals("Open advancement tree", plainText.serialize(lang.msg("achievements.tree_action")))
        assertEquals("Close", plainText.serialize(lang.msg("dialog_v2.close_menu")))
        assertEquals("Prestige rewards", plainText.serialize(lang.msg("chronicle.prestige.action")))
        assertEquals("Open Minecraft Advancements with your configured key: ", plainText.serialize(lang.msg("achievements.tree_key_hint")))
        org.junit.jupiter.api.Assertions.assertTrue(lang.raw("achievements.tree_find_hint").contains("Guild Mastery"))
        assertEquals(old, locale.readText())
    }

    @TempDir
    lateinit var dataFolder: Path

    private val plainText = PlainTextComponentSerializer.plainText()

    /** Nested validation components must display text rather than object diagnostics. */
    @Test
    fun rankFeedbackText() {
        val rendered = langService().rankNameError(net.kyori.adventure.text.Component.text("Use valid color codes"))
        assertEquals("❌ Invalid name: Use valid color codes", plainText.serialize(rendered))
    }

    @Test
    fun `claim message renders a named claim placeholder`() {
        val rendered = langService().msg(
            "command.claim.remove.success",
            "claim" to "North Farm",
        )

        assertEquals(
            "Partition has been removed for claim North Farm.",
            plainText.serialize(rendered),
        )
    }

    @Test
    fun `partition location renders four named coordinate placeholders`() {
        val rendered = langService().msg(
            "menu.edit_tool.item.partition.lore.location",
            "lower_x" to 10,
            "lower_z" to 20,
            "upper_x" to 30,
            "upper_z" to 40,
        )

        assertEquals(
            "Lower (10, 20) | Upper (30, 40)",
            plainText.serialize(rendered),
        )
    }

    @Test
    fun `trust message renders named permission player and claim placeholders`() {
        val rendered = langService().msg(
            "command.claim.trust.success",
            "permission" to "Build",
            "player" to "Ada",
            "claim" to "North Farm",
        )

        assertEquals(
            "Permission Build has been assigned to player Ada in claim North Farm.",
            plainText.serialize(rendered),
        )
    }

    @Test
    fun `GUI message accepts a nested Component without legacy serialization`() {
        val lang = langService()
        val bar = lang.gui(
            "menu.guild_progression.bar.red",
            "filled" to "████",
            "empty" to "████",
        )

        val rendered = lang.gui("menu.guild_progression.level.bar", "bar" to bar)

        assertEquals("████████", plainText.serialize(rendered))
        assertEquals(0xFF000000.toInt(), rendered.shadowColor()?.value())
    }

    @Test
    fun `Bedrock GUI text uses small caps while preserving user placeholders`() {
        val rendered = langService().bedrock(
            "command.claim.remove.success",
            "claim" to "North Farm",
        )

        assertEquals("ᴘᴀʀᴛɪᴛɪᴏɴ ʜᴀꜱ ʙᴇᴇɴ ʀᴇᴍᴏᴠᴇᴅ ꜰᴏʀ ᴄʟᴀɪᴍ North Farm.", rendered)
    }

    private fun langService(): LangService = LangService(
        object : LangHost {
            override val dataFolder: File = this@LangServiceRenderingTest.dataFolder.toFile()
            override val resourceClassLoader: ClassLoader = LumaGuildsLang::class.java.classLoader
        },
        Locale("en_US"),
        LumaGuildsLang::class.java,
    )
}
