package net.lumalyte.lg.interaction.menus.guild

import io.mockk.every
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.interaction.menus.MenuNavigator
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberListMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberRankConfirmationMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildMemberRankMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildPromotionMenu
import net.lumalyte.lg.interaction.menus.bedrock.BedrockGuildRankManagementMenu
import org.geysermc.cumulus.component.DropdownComponent
import org.geysermc.cumulus.component.InputComponent
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.SimpleForm
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the real Java inventories and native Bedrock form labels. */
internal class GuildMemberRankTooltipTest : GuildRankMenuFixture() {
    /** Every accepted hex representation must reach the item lore as color. */
    @ParameterizedTest
    @ValueSource(strings = [AMP_HEX, BARE_HEX, SECTION_HEX])
    fun hexRankColorRenders(storedName: String) {
        every { ranks.getPlayerRank(player.uniqueId, guild.id) } returns
            Rank(UUID.randomUUID(), guild.id, storedName)
        GuildMemberListMenu(MenuNavigator(player), player, guild).open()
        val lore = checkNotNull(player.openInventory.topInventory.getItem(0)?.lore())
        val rankLine = lore.first()
        val visible = PlainTextComponentSerializer.plainText().serialize(rankLine)
        assertFalse(visible.contains(HEX_DIGITS), "Raw hex leaked into member lore: $visible")
        assertTrue(visible.contains("ꜰᴏᴜɴᴅᴇʀ"), "Visible rank name is missing: $visible")
        assertTrue(hasColor(rankLine, expectedColor))
        assertEquals(storedName, ranks.getPlayerRank(player.uniqueId, guild.id)?.name)
    }

    /** Exercise every additional Java rank display through its inventory. */
    @ParameterizedTest
    @ValueSource(strings = [AMP_HEX, BARE_HEX, SECTION_HEX])
    fun remainingJavaMenusRenderRank(storedName: String) {
        val rank = bindRank(storedName)
        val navigator = MenuNavigator(player)
        val menus =
            listOf(
                GuildInfoMenu(navigator, player, guild),
                GuildPromotionMenu(navigator, player, guild),
                GuildMemberRankMenu(navigator, player, guild, member),
                GuildMemberRankConfirmationMenu(navigator, player, guild, member, rank),
                HomeAccessMenu(navigator, player, guild, "Home"),
            )
        assertAll(
            menus.map { menu ->
                {
                    menu.open()
                    assertRankInventory(menu.javaClass.simpleName)
                }
            },
        )
        assertEquals(storedName, rank.name)
    }

    /** Native forms use readable labels, with rank selection still indexed by ID. */
    @ParameterizedTest
    @ValueSource(strings = [AMP_HEX, BARE_HEX, SECTION_HEX])
    fun bedrockRankFormsDoNotLeakCodes(storedName: String) {
        val rank = bindRank(storedName)
        val navigator = MenuNavigator(player)
        val logger = Logger.getAnonymousLogger()
        val assignment = BedrockGuildMemberRankMenu(navigator, player, guild, member, logger).getForm() as CustomForm
        assignment.content().filterNotNull().forEach { assertFalse(it.text().contains(HEX_DIGITS)) }
        assertRankDropdown(assignment)
        val confirmation =
            BedrockGuildMemberRankConfirmationMenu(
                navigator,
                player,
                guild,
                member,
                rank,
                logger,
            ).getForm() as SimpleForm
        assertFalse(confirmation.content().contains(HEX_DIGITS))
        assertEquals(storedName, rank.name)
    }

    /** Rank editor neighbors must render colors as well as the edited rank. */
    @ParameterizedTest
    @ValueSource(strings = [AMP_HEX, BARE_HEX, SECTION_HEX])
    fun priorityNeighborsRenderRank(storedName: String) {
        val rank = bindRank(storedName).copy(priority = 1)
        val above = rank.copy(id = UUID.randomUUID(), priority = 0)
        val below = rank.copy(id = UUID.randomUUID(), priority = 2)
        every { ranks.listRanks(guild.id) } returns setOf(above, rank, below)
        RankEditMenu(MenuNavigator(player), player, guild, rank).open()
        assertRankInventory("RankEditMenu")
    }

    /** Member pickers and management labels hide syntax while the editor preserves it. */
    @ParameterizedTest
    @ValueSource(strings = [AMP_HEX, BARE_HEX, SECTION_HEX])
    fun bedrockManagementLabels(storedName: String) {
        val rank = bindRank(storedName)
        val navigator = MenuNavigator(player)
        val logger = Logger.getAnonymousLogger()
        val members = BedrockGuildMemberListMenu(navigator, player, guild, logger).getForm() as SimpleForm
        members.buttons().filterNotNull().forEach { assertFalse(it.text().contains(HEX_DIGITS)) }
        val promotion = BedrockGuildPromotionMenu(navigator, player, guild, logger).getForm() as CustomForm
        assertFormLabels(promotion)
        val editing = BedrockGuildRankManagementMenu(navigator, player, guild, rank, logger).getForm() as CustomForm
        assertFormLabels(editing)
        assertEquals(storedName, editing.content().filterIsInstance<InputComponent>().first().defaultText())
    }

    private fun assertFormLabels(form: CustomForm) {
        form.content().filterNotNull().forEach { component ->
            assertFalse(component.text().contains(HEX_DIGITS))
            if (component is DropdownComponent) {
                component.options().forEach { assertFalse(it.contains(HEX_DIGITS)) }
            }
        }
    }

    private fun assertRankDropdown(form: CustomForm) {
        val dropdown = form.content().filterIsInstance<DropdownComponent>().single()
        assertEquals(listOf("Founder"), dropdown.options())
        assertEquals(0, dropdown.defaultOption())
    }
}

private const val AMP_HEX = "&#f99801Founder"
private const val BARE_HEX = "#f99801Founder"
private const val SECTION_HEX = "§#f99801Founder"

private const val HEX_DIGITS = "f99801"
