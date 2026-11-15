package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.application.services.GuildPrestigeService
import net.lumalyte.lg.domain.rewards.PrestigeOverview
import net.lumalyte.lg.domain.rewards.PrestigeQuote
import net.lumalyte.lg.domain.rewards.RewardCatalog
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockPrestigeConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockPrestigeSelectionMenu
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.junit.jupiter.api.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertTrue

/** REQ-173: prestige choices and the confirmation say what the kept reward does and what prestige gives. */
internal class PrestigeClarityTest : GuildRankMenuFixture() {
    private val catalog = RewardCatalog.chapterTwo()
    private val prestige = mockk<GuildPrestigeService>()
    private val quote = PrestigeQuote(UUID.randomUUID(), guild.id, UUID.randomUUID(), "bank-1", 20_000, 3, 1)

    private fun bind() {
        bindRank("Founder")
        every { prestige.overview(guild.id) } returns
            PrestigeOverview(true, 100, 1, 6, 20_000, listOf(catalog.find("bank-1")!!, catalog.find("ally-homes")!!))
        val perks = net.lumalyte.lg.application.services.GuildPrestigePerks(mockk(relaxed = true)) {
            net.lumalyte.lg.domain.rewards.PrestigePerks(net.lumalyte.lg.config.PrestigePerksConfig())
        }
        every { prestige.preview(any()) } answers { preview(firstArg<PrestigeQuote>().expectedPrestigeCount + 1) }
        loadKoinModules(module { single { prestige }; single { catalog }; single { perks } })
    }

    private var carry: net.lumalyte.lg.domain.rewards.PrestigePreview.Carry? = null

    private fun preview(tier: Int) = net.lumalyte.lg.domain.rewards.PrestigePreview(
        tier = tier, finalTier = tier == 6, homeSlotsBefore = 2, homeSlotsAfter = 3,
        perks = net.lumalyte.lg.domain.rewards.PrestigePerks(net.lumalyte.lg.config.PrestigePerksConfig()).unlockedAt(tier),
        carry = carry, memberLimitKept = if (tier == 1) 100 else null, discordRoleMinimumLevel = if (tier == 1) 50 else null,
        memberCreditTenureHours = null, firstOnServer = true, fanfare = true, discordPost = false, fee = 20_000,
    )

    @Test
    fun `java confirmation explains the kept reward, the home slot and the badge`() {
        bind()
        val lore = GuildPrestigeConfirmationMenu(player, quote, "Bank Extension 1", {}, {}).createGui()
            .let { gui -> gui.show(player); player.openInventory.topInventory.getItem(4)!!.lore()!! }
            .joinToString("\n") { plain(PlainTextComponentSerializer.plainText().serialize(it)) }
        assertTrue("keep forever: bank extension 1" in lore, lore)
        assertTrue("bank capacity: +1" in lore, lore)
        assertTrue("1 permanent home slot (2 → 3)" in lore, lore)
        assertTrue("prestige ii badge" in lore, lore)
        assertTrue("20,000 gold ore" in lore, lore)
        assertTrue("purchased reward reset" in lore, lore)
        assertTrue("first on the server" in lore, lore)
        // REQ-180: Prestige II starts the second allied home.
        assertTrue("second allied home" in lore, lore)
    }

    @Test
    fun `confirmation says whether XP carries over`() {
        bind()
        fun javaLore() = GuildPrestigeConfirmationMenu(player, quote, "Bank Extension 1", {}, {}).createGui()
            .let { gui -> gui.show(player); player.openInventory.topInventory.getItem(4)!!.lore()!! }
            .joinToString("\n") { plain(PlainTextComponentSerializer.plainText().serialize(it)) }
        assertTrue("guild xp back to 0" in javaLore())
        carry = net.lumalyte.lg.domain.rewards.PrestigePreview.Carry(1240, 3)
        val lore = javaLore()
        assertTrue("1,240 xp carried over: you start at level 3" in lore, lore)
        assertTrue("guild xp back to 0" !in lore, lore)
        val bedrock = plain((BedrockPrestigeConfirmationMenu(MenuNavigator(player), player, quote, "Bank Extension 1", {}, {},
            Logger.getAnonymousLogger()).getForm() as ModalForm).content())
        assertTrue("1,240 xp carried over: you start at level 3" in bedrock, bedrock)
    }

    @Test
    fun `bedrock confirmation says the same`() {
        bind()
        val content = plain((BedrockPrestigeConfirmationMenu(MenuNavigator(player), player, quote, "Bank Extension 1", {}, {},
            Logger.getAnonymousLogger()).getForm() as ModalForm).content())
        assertTrue("bank capacity: +1" in content, content)
        assertTrue("1 permanent home slot (2 → 3)" in content, content)
        assertTrue("prestige ii badge" in content, content)
        assertTrue("second allied home" in content, content)
    }

    @Test
    fun `reaching Prestige III lists the extra stall`() {
        bind()
        fun javaLore(q: PrestigeQuote) = GuildPrestigeConfirmationMenu(player, q, "Bank Extension 1", {}, {}).createGui()
            .let { gui -> gui.show(player); player.openInventory.topInventory.getItem(4)!!.lore()!! }
            .joinToString("\n") { plain(PlainTextComponentSerializer.plainText().serialize(it)) }
        val first = javaLore(quote.copy(expectedPrestigeCount = 0))
        assertTrue("new perk" !in first, first)
        assertTrue("member limit stays at 100" in first, first)
        assertTrue("discord role below level 50" in first, first)
        val last = javaLore(quote.copy(expectedPrestigeCount = 5))
        assertTrue("final prestige" in last, last)
        assertTrue("one more guild market stall" in javaLore(quote.copy(expectedPrestigeCount = 2)))
        val bedrock = plain((BedrockPrestigeConfirmationMenu(MenuNavigator(player), player, quote.copy(expectedPrestigeCount = 2),
            "Bank Extension 1", {}, {}, Logger.getAnonymousLogger()).getForm() as ModalForm).content())
        assertTrue("one more guild market stall" in bedrock, bedrock)
    }

    @Test
    fun `bedrock reward choices say what each reward does`() {
        bind()
        val buttons = (BedrockPrestigeSelectionMenu(MenuNavigator(player), player, guild, Logger.getAnonymousLogger()).getForm() as SimpleForm)
            .buttons().map { plain(it!!.text()) }
        assertTrue("bank capacity: +1" in buttons[0], buttons[0])
        assertTrue("allied guild homes" in buttons[1], buttons[1])
    }

    companion object {
        private const val SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ"

        /** The server font turns static text into small caps; compare letters, not glyphs. */
        fun plain(text: String) = text.map { c -> SMALL.indexOf(c).let { if (it >= 0) 'a' + it else c } }
            .joinToString("").lowercase()
    }
}
