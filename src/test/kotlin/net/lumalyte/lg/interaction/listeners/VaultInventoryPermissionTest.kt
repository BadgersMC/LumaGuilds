package net.lumalyte.lg.interaction.listeners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.badgersmc.nexus.i18n.LangService
import net.kyori.adventure.text.Component
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.config.VaultConfig
import net.lumalyte.lg.domain.entities.RankPermission
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.interaction.inventory.VaultInventoryHolder
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
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

    @Test fun shiftDepositRequiresPermission() = denied(InventoryAction.MOVE_TO_OTHER_INVENTORY, PLAYER_SLOT)

    @Test fun playerCollectionRequiresWithdrawal() = denied(InventoryAction.COLLECT_TO_CURSOR, PLAYER_SLOT)

    @Test fun unknownActionFailsClosed() = denied(InventoryAction.UNKNOWN, 1)

    @ParameterizedTest
    @EnumSource(value = InventoryAction::class, names = ["HOTBAR_SWAP", "HOTBAR_MOVE_AND_READD", "SWAP_WITH_CURSOR"])
    fun depositOnlyCannotSwapOut(action: InventoryAction) {
        denied(action, 1, setOf(RankPermission.ACCESS_VAULT, RankPermission.DEPOSIT_TO_VAULT))
    }

    @Test fun revokedAccessCancelsAndClosesCurrentView() {
        val permissions = mutableSetOf(RankPermission.ACCESS_VAULT)
        val fixture = Fixture(permissions)
        permissions.clear()
        val event = fixture.click(InventoryAction.PICKUP_ALL, 1)
        fixture.listener.onInventoryClick(event)
        assertTrue(event.isCancelled)
        fixture.server.scheduler.performOneTick()
        assertTrue(fixture.player.openInventory.topInventory !== fixture.inventory)
    }

    @Test fun restoredAccessPreventsScheduledClosure() {
        val permissions = mutableSetOf<RankPermission>()
        val fixture = Fixture(permissions)
        fixture.listener.onInventoryClick(fixture.click(InventoryAction.PICKUP_ALL, 1))
        permissions.add(RankPermission.ACCESS_VAULT)
        fixture.server.scheduler.performOneTick()
        assertTrue(fixture.player.openInventory.topInventory === fixture.inventory)
    }

    @Test fun scheduledClosurePreservesReplacementMenu() {
        val fixture = Fixture(emptySet())
        fixture.listener.onInventoryClick(fixture.click(InventoryAction.PICKUP_ALL, 1))
        val replacement = fixture.server.createInventory(null, 9, Component.text("Replacement"))
        fixture.player.openInventory(replacement)
        fixture.server.scheduler.performOneTick()
        assertTrue(fixture.player.openInventory.topInventory === replacement)
    }

    @Test fun authorizedPlayerOnlyClickRemainsAllowed() {
        val fixture = Fixture(setOf(RankPermission.ACCESS_VAULT))
        val event = fixture.click(InventoryAction.PICKUP_ALL, PLAYER_SLOT)
        fixture.listener.onInventoryClick(event)
        assertFalse(event.isCancelled)
        verify(exactly = 0) { fixture.manager.validateAndRepairVault(any(), any()) }
    }

    @Test fun authorizedShiftDepositSynchronizesAfterClick() {
        val fixture = Fixture(setOf(RankPermission.ACCESS_VAULT, RankPermission.DEPOSIT_TO_VAULT))
        val event = fixture.click(InventoryAction.MOVE_TO_OTHER_INVENTORY, PLAYER_SLOT)
        fixture.listener.onInventoryClick(event)
        assertFalse(event.isCancelled)
        fixture.server.scheduler.performOneTick()
        verify(exactly = 1) { fixture.manager.syncInventoryToCache(fixture.guildId, fixture.inventory) }
    }

    @Test fun authorizedWithdrawalRemainsAllowed() {
        val fixture = Fixture(setOf(RankPermission.ACCESS_VAULT, RankPermission.WITHDRAW_FROM_VAULT))
        val event = fixture.click(InventoryAction.PICKUP_ALL, 1)
        fixture.listener.onInventoryClick(event)
        assertFalse(event.isCancelled)
    }

    @Test fun priorCancellationDoesNotRepairOrScheduleVault() {
        val fixture = Fixture(RankPermission.entries.toSet())
        val event = fixture.click(InventoryAction.PICKUP_ALL, 1)
        event.isCancelled = true
        fixture.listener.onInventoryClick(event)
        fixture.server.scheduler.performOneTick()
        verify(exactly = 0) { fixture.manager.validateAndRepairVault(any(), any()) }
        verify(exactly = 0) { fixture.manager.syncInventoryToCache(any(), any()) }
    }

    @Test fun unauthorizedOpenIsCancelledBeforeViewerRegistration() {
        val fixture = Fixture(emptySet())
        fixture.server.pluginManager.registerEvents(fixture.listener, fixture.plugin)
        val event = InventoryOpenEvent(fixture.player.openInventory)
        fixture.server.pluginManager.callEvent(event)
        assertTrue(event.isCancelled)
        verify(exactly = 0) { fixture.manager.openVaultFor(any(), any(), any()) }
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

    private class Fixture(
        permissions: Set<RankPermission>,
    ) {
        val server = MockBukkit.mock()
        val plugin = MockBukkit.createMockPlugin()
        val player = server.addPlayer()
        val guildId = UUID.randomUUID()
        val manager = mockk<VaultInventoryManager>(relaxed = true)
        private val members = mockk<MemberService>(relaxed = true)
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
            )

        init {
            every { holder.guildId } returns guildId
            every { holder.guildName } returns "Guild"
            every { holder.inventory } returns inventory
            every { holder.getCapacity() } returns VAULT_SIZE
            every { members.hasPermission(player.uniqueId, guildId, any()) } answers
                { thirdArg<RankPermission>() in permissions }
            val lang = mockk<LangService>(relaxed = true)
            every { lang.msg(any()) } returns Component.text("Permission denied")
            startKoin { modules(module { single { lang } }) }
            player.openInventory(inventory)
        }

        fun click(
            action: InventoryAction,
            slot: Int,
        ) = InventoryClickEvent(player.openInventory, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, action)
    }

    private companion object {
        const val VAULT_SIZE = 54
        const val PLAYER_SLOT = 60
    }
}
