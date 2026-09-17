package net.lumalyte.lg.infrastructure.vault

import org.junit.jupiter.api.Test

/** Currency mutation remains outside the item-inventory manager. */
internal class VaultGoldAuditFailureTest {

    @Test
    fun inventoryManagerCannotMutateCurrency() {
        val forbidden = setOf("depositGold", "withdrawGold", "setGoldBalance", "setGoldBalanceWithBroadcast")
        kotlin.test.assertTrue(VaultInventoryManager::class.java.methods.none { it.name in forbidden })
    }

}
