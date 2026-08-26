package net.lumalyte.lg.architecture

import net.lumalyte.lg.infrastructure.vault.VaultAutoSaveService
import net.lumalyte.lg.infrastructure.vault.VaultInventory
import net.lumalyte.lg.infrastructure.vault.VaultInventoryManager
import net.lumalyte.lg.infrastructure.vault.ViewerSession
import net.lumalyte.lg.infrastructure.vault.WriteBuffer
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse

class VaultOwnershipContractTest {
    @Test
    fun `Bukkit vault implementation types live in infrastructure`() {
        listOf(
            VaultInventory::class,
            ViewerSession::class,
            WriteBuffer::class,
            VaultInventoryManager::class,
            VaultAutoSaveService::class
        )
    }

    @Test
    fun `old vault implementation source paths remain absent`() {
        val oldPaths = listOf(
            "src/main/kotlin/net/lumalyte/lg/domain/entities/VaultInventory.kt",
            "src/main/kotlin/net/lumalyte/lg/domain/entities/ViewerSession.kt",
            "src/main/kotlin/net/lumalyte/lg/domain/entities/WriteBuffer.kt",
            "src/main/kotlin/net/lumalyte/lg/application/services/VaultInventoryManager.kt",
            "src/main/kotlin/net/lumalyte/lg/application/services/VaultAutoSaveService.kt"
        )
        oldPaths.forEach { assertFalse(Files.exists(Path.of(it)), it) }
    }
}
