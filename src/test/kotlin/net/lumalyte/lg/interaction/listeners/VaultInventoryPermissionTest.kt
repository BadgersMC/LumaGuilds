package net.lumalyte.lg.interaction.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.persistence.ProgressionRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.domain.entities.GuildProgression
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.inventory.VaultInventoryHolder
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VaultInventoryPermissionTest {
    @BeforeEach fun setup() = stopKoin()

    @AfterEach fun cleanup() {
        stopKoin()
        MockBukkit.unmock()
    }

    @ParameterizedTest
    @EnumSource(
        value = InventoryAction::class,
        names = [
            "PICKUP_ALL", "PICKUP_SOME", "PICKUP_HALF", "PICKUP_ONE", "DROP_ALL_SLOT", "DROP_ONE_SLOT",
            "MOVE_TO_OTHER_INVENTORY", "COLLECT_TO_CURSOR", "CLONE_STACK",
        ],
    )
    fun viewOnlyCannotWithdraw(action: InventoryAction) = denied(action, 1)

    @ParameterizedTest
    @EnumSource(value = InventoryAction::class, names = ["PLACE_ALL", "PLACE_SOME", "PLACE_ONE"])
    fun viewOnlyCannotDeposit(action: InventoryAction) = denied(action, 1)

    @Test fun shiftDepositDenied() = denied(InventoryAction.MOVE_TO_OTHER_INVENTORY, PLAYER_SLOT)

    @Test fun collectionDenied() = denied(InventoryAction.COLLECT_TO_CURSOR, PLAYER_SLOT)

    @Test fun unknownActionFailsClosed() = denied(InventoryAction.UNKNOWN, 1)

    @ParameterizedTest
    @EnumSource(value = InventoryAction::class, names = ["HOTBAR_SWAP", "HOTBAR_MOVE_AND_READD", "SWAP_WITH_CURSOR"])
    fun depositOnlyCannotSwapOut(action: InventoryAction) {
        denied(action, 1, setOf(RankPermission.ACCESS_VAULT, RankPermission.DEPOSIT_TO_VAULT))
    }

    private fun denied(
        action: InventoryAction,
        slot: Int,
        permissions: Set<RankPermission> = setOf(RankPermission.ACCESS_VAULT),
    ) {
        val fixture = Fixture(permissions)
        val event = fixture.click(action, slot)
        fixture.listener.onInventoryClick(event)
        assertTrue(event.isCancelled, "$action at $slot must require the direction permission")
        verify(exactly = 0) { fixture.manager.validateAndRepairVault(any(), any()) }
    }
}

internal class VaultSessionPermissionTest {
    @BeforeEach fun setup() = stopKoin()

    @AfterEach fun cleanup() {
        stopKoin()
        MockBukkit.unmock()
    }

    @Test fun revokedViewCloses() {
        val permissions = mutableSetOf(RankPermission.ACCESS_VAULT)
        val fixture = Fixture(permissions)
        permissions.clear()
        val event = fixture.click(InventoryAction.PICKUP_ALL, 1)
        fixture.listener.onInventoryClick(event)
        assertTrue(event.isCancelled)
        fixture.server.scheduler.performOneTick()
        assertTrue(fixture.player.openInventory.topInventory !== fixture.inventory)
    }

    @Test fun restoredViewStays() {
        val permissions = mutableSetOf<RankPermission>()
        val fixture = Fixture(permissions)
        fixture.listener.onInventoryClick(fixture.click(InventoryAction.PICKUP_ALL, 1))
        permissions.add(RankPermission.ACCESS_VAULT)
        fixture.server.scheduler.performOneTick()
        assertTrue(fixture.player.openInventory.topInventory === fixture.inventory)
    }

    @Test fun replacementViewStays() {
        val fixture = Fixture(emptySet())
        fixture.listener.onInventoryClick(fixture.click(InventoryAction.PICKUP_ALL, 1))
        val replacement = fixture.server.createInventory(null, REPLACEMENT_SIZE, Component.text("Replacement"))
        fixture.player.openInventory(replacement)
        fixture.server.scheduler.performOneTick()
        assertTrue(fixture.player.openInventory.topInventory === replacement)
    }

    @Test fun playerInventoryAllowed() {
        val fixture = Fixture(setOf(RankPermission.ACCESS_VAULT))
        val event = fixture.click(InventoryAction.PICKUP_ALL, PLAYER_SLOT)
        fixture.listener.onInventoryClick(event)
        assertFalse(event.isCancelled)
        verify(exactly = 0) { fixture.manager.validateAndRepairVault(any(), any()) }
    }

    @Test fun shiftDepositSynchronizes() {
        val fixture = Fixture(setOf(RankPermission.ACCESS_VAULT, RankPermission.DEPOSIT_TO_VAULT))
        val event = fixture.click(InventoryAction.MOVE_TO_OTHER_INVENTORY, PLAYER_SLOT)
        fixture.listener.onInventoryClick(event)
        assertFalse(event.isCancelled)
        fixture.server.scheduler.performOneTick()
        verify(exactly = 1) { fixture.manager.syncInventoryToCache(fixture.guildId, fixture.inventory) }
    }

    @Test fun withdrawalAllowed() {
        val fixture = Fixture(setOf(RankPermission.ACCESS_VAULT, RankPermission.WITHDRAW_FROM_VAULT))
        val event = fixture.click(InventoryAction.PICKUP_ALL, 1)
        fixture.listener.onInventoryClick(event)
        assertFalse(event.isCancelled)
    }

    @Test fun postPrestigeOverflowIsWithdrawalOnly() {
        val fixture = Fixture(RankPermission.entries.toSet(), unlockedCapacity = 9)

        for (action in listOf(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
            InventoryAction.DROP_ONE_SLOT, InventoryAction.MOVE_TO_OTHER_INVENTORY,
        )) {
            val withdrawal = fixture.click(action, 53)
            fixture.listener.onInventoryClick(withdrawal)
            assertFalse(withdrawal.isCancelled, "$action should retrieve an occupied overflow item")
        }

        for (action in listOf(
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE,
            InventoryAction.SWAP_WITH_CURSOR, InventoryAction.HOTBAR_SWAP,
            InventoryAction.CLONE_STACK,
        )) {
            val insertion = fixture.click(action, 53)
            fixture.listener.onInventoryClick(insertion)
            assertTrue(insertion.isCancelled, "$action must not deposit into a locked overflow slot")
        }

        val shiftDeposit = fixture.click(InventoryAction.MOVE_TO_OTHER_INVENTORY, PLAYER_SLOT)
        fixture.listener.onInventoryClick(shiftDeposit)
        assertTrue(shiftDeposit.isCancelled, "Shift-deposit must not auto-fill locked overflow slots")

        val unlockedDeposit = fixture.click(InventoryAction.PLACE_ALL, 5)
        fixture.listener.onInventoryClick(unlockedDeposit)
        assertFalse(unlockedDeposit.isCancelled, "The unlocked first row must still accept items")
    }

    @Test fun alreadyOpenVaultCannotAcceptOverflowAfterPrestige() {
        // The cached guild record and open holder can still describe level 100;
        // progression is refreshed from the committed prestige transaction.
        val fixture = Fixture(
            RankPermission.entries.toSet(),
            unlockedCapacity = 54,
            progressionLevel = 1,
            cachedProgressionLevel = 100,
        )
        val place = fixture.click(InventoryAction.PLACE_ALL, 53)
        fixture.listener.onInventoryClick(place)
        assertTrue(place.isCancelled)
        val pickup = fixture.click(InventoryAction.PICKUP_ALL, 53)
        fixture.listener.onInventoryClick(pickup)
        assertFalse(pickup.isCancelled)
    }

    @Test fun staleCachedLevelCannotBypassPrestigeOverflow() {
        val fixture = Fixture(
            RankPermission.entries.toSet(), unlockedCapacity = 54,
            progressionLevel = 1, cachedProgressionLevel = 100,
        )
        val attemptedDeposit = fixture.click(InventoryAction.PLACE_ALL, 53)
        fixture.listener.onInventoryClick(attemptedDeposit)
        assertTrue(attemptedDeposit.isCancelled, "Authoritative refreshed level, not cached level, governs deposits")
        verify(exactly = 1) { fixture.progression.readCommittedLevel(fixture.guildId) }

        val ordinaryClick = fixture.click(InventoryAction.PLACE_ALL, 5)
        fixture.listener.onInventoryClick(ordinaryClick)
        assertFalse(ordinaryClick.isCancelled)
        verify(exactly = 1) { fixture.progression.readCommittedLevel(fixture.guildId) }
    }

    @Test fun failureToRefreshProgressionFailsClosedForUpperRows() {
        val fixture = Fixture(
            RankPermission.entries.toSet(), unlockedCapacity = 54,
            progressionLevel = 100, cachedProgressionLevel = 100,
        )
        every { fixture.progression.readCommittedLevel(fixture.guildId) } throws IllegalStateException("SQLite unavailable")
        val attemptedDeposit = fixture.click(InventoryAction.PLACE_ALL, 53)
        fixture.listener.onInventoryClick(attemptedDeposit)
        assertTrue(attemptedDeposit.isCancelled)
        val withdrawal = fixture.click(InventoryAction.PICKUP_ALL, 53)
        fixture.listener.onInventoryClick(withdrawal)
        assertFalse(withdrawal.isCancelled, "Existing overflow items remain withdrawable")
    }

    @Test fun dragIntoOverflowIsCancelledBeforeInventoryMutation() {
        val fixture = Fixture(RankPermission.entries.toSet(), unlockedCapacity = 9, progressionLevel = 1)
        val untouched = ItemStack(Material.DIAMOND, 4)
        fixture.inventory.setItem(53, untouched)
        val drag = InventoryDragEvent(
            fixture.player.openInventory,
            ItemStack(Material.DIAMOND, 2),
            ItemStack(Material.DIAMOND, 1),
            false,
            mapOf(53 to ItemStack(Material.DIAMOND, 2)),
        )
        fixture.listener.onInventoryDrag(drag)
        assertTrue(drag.isCancelled)
        assertTrue(fixture.inventory.getItem(53)?.isSimilar(untouched) == true)
    }

    @Test fun vaultWideCollectionAndHotbarReaddAreBlockedWhenOverflowExists() {
        val fixture = Fixture(RankPermission.entries.toSet(), unlockedCapacity = 9, progressionLevel = 1)
        for (action in listOf(
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.HOTBAR_MOVE_AND_READD,
        )) {
            val event = fixture.click(action, 5)
            fixture.listener.onInventoryClick(event)
            assertTrue(event.isCancelled, "$action may affect an unseen overflow destination")
        }
    }

    @Test fun cancelledClickDoesNotSync() {
        val fixture = Fixture(RankPermission.entries.toSet())
        val event = fixture.click(InventoryAction.PICKUP_ALL, 1)
        event.isCancelled = true
        fixture.listener.onInventoryClick(event)
        fixture.server.scheduler.performOneTick()
        verify(exactly = 0) { fixture.manager.validateAndRepairVault(any(), any()) }
        verify(exactly = 0) { fixture.manager.syncInventoryToCache(any(), any()) }
    }

    @Test fun unauthorizedOpenDenied() {
        val fixture = Fixture(emptySet())
        fixture.server.pluginManager.registerEvents(fixture.listener, fixture.plugin)
        val event = InventoryOpenEvent(fixture.player.openInventory)
        fixture.server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
        verify(exactly = 0) { fixture.manager.openVaultFor(any(), any(), any()) }
    }
}

private class Fixture(
    permissions: Set<RankPermission>,
    private val unlockedCapacity: Int = VAULT_SIZE,
    private val progressionLevel: Int = 100,
    private val cachedProgressionLevel: Int = progressionLevel,
) {
    val server = MockBukkit.mock()
    val plugin = MockBukkit.createMockPlugin()
    val player = server.addPlayer()
    val guildId = UUID.randomUUID()
    val manager = mockk<VaultInventoryManager>(relaxed = true)
    private val members = mockk<MemberService>(relaxed = true)
    val progression = mockk<ProgressionRepository>(relaxed = true)
    private val holder = mockk<VaultInventoryHolder>()
    val inventory = server.createInventory(holder, VAULT_SIZE, Component.text("Vault"))
    val listener =
        VaultInventoryListener(
            plugin,
            manager,
            mockk(relaxed = true),
            VaultConfig(),
            mockk(relaxed = true),
            members,
            mockk(relaxed = true),
            progression,
        )

    init {
        every { holder.guildId } returns guildId
        every { holder.guildName } returns "Guild"
        every { holder.inventory } returns inventory
        every { holder.getCapacity() } returns unlockedCapacity
        val cachedProgression = mockk<GuildProgression>()
        every { cachedProgression.currentLevel } returns cachedProgressionLevel
        every { progression.getGuildProgression(guildId) } returns cachedProgression
        every { progression.readCommittedLevel(guildId) } returns progressionLevel
        every { members.hasPermission(player.uniqueId, guildId, any()) } answers
            { thirdArg<RankPermission>() in permissions }
        val lang = mockk<LangService>(relaxed = true)
        every { lang.msg(any()) } returns Component.text("Permission denied")
        startKoin { modules(module { single { lang } }) }
        player.openInventory(inventory)
    }

    fun click(action: InventoryAction, slot: Int): InventoryClickEvent =
        InventoryClickEvent(player.openInventory, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, action)
}

private const val VAULT_SIZE = 54
private const val PLAYER_SLOT = 60
private const val REPLACEMENT_SIZE = 9
