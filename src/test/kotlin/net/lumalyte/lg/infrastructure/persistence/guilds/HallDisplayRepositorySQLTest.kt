package net.lumalyte.lg.infrastructure.persistence.guilds

import net.lumalyte.lg.domain.rewards.HallSlot
import net.lumalyte.lg.domain.rewards.HallTheme
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** REQ-260/261: owned themes, choices and skin persist, and the per-guild limit holds. */
class HallDisplayRepositorySQLTest : RewardSqlTestFixture() {
    private val guild = UUID.randomUUID()

    @Test
    fun `themes, choices and skin survive reopen and the limit holds`() {
        val storage = openStorage()
        val repository = HallDisplayRepositorySQL(storage)
        assertTrue(repository.earn(guild, HallTheme.TIDAL, 2, Instant.ofEpochMilli(1)))
        assertFalse(repository.earn(guild, HallTheme.TIDAL, 2, Instant.ofEpochMilli(2)))
        assertTrue(repository.earn(guild, HallTheme.EMBER, 2, Instant.ofEpochMilli(3)))
        assertFalse(repository.earn(guild, HallTheme.GILDED, 2, Instant.ofEpochMilli(4)))
        repository.choose(guild, HallSlot.POSE, HallTheme.EMBER)
        repository.choose(guild, HallSlot.AURA, HallTheme.TIDAL)
        repository.choose(guild, HallSlot.AURA, null)
        val skin = UUID.randomUUID()
        repository.setSkin(guild, skin)
        val chest = net.lumalyte.lg.domain.rewards.HallArmorPiece(net.lumalyte.lg.domain.rewards.HallArmorMaterial.DIAMOND, "wayfinder", "gold")
        repository.setArmor(guild, net.lumalyte.lg.domain.rewards.HallArmorPart.CHEST, chest)
        repository.setArmor(guild, net.lumalyte.lg.domain.rewards.HallArmorPart.FEET, chest)
        repository.setArmor(guild, net.lumalyte.lg.domain.rewards.HallArmorPart.FEET, null)
        closeStorage(storage)

        val reopened = HallDisplayRepositorySQL(openStorage())
        assertEquals(listOf(HallTheme.TIDAL, HallTheme.EMBER), reopened.owned(guild))
        assertEquals(mapOf(HallSlot.POSE to HallTheme.EMBER), reopened.choices(guild))
        assertEquals(skin, reopened.skin(guild))
        assertEquals(mapOf(net.lumalyte.lg.domain.rewards.HallArmorPart.CHEST to chest), reopened.armor(guild))
        reopened.deleteAll(guild)
        assertEquals(emptyList(), reopened.owned(guild))
        assertNull(reopened.skin(guild))
    }
}
