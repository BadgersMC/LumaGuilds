package net.lumalyte.lg.infrastructure.persistence.guilds

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InvalidClassException
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LegacyItemMapTest {
    private fun bytes(value: Any): ByteArray = ByteArrayOutputStream().also { out ->
        ObjectOutputStream(out).use { it.writeObject(value) }
    }.toByteArray()

    @Test fun `a legacy ItemStack map still reads`() {
        val map = linkedMapOf<String, Any>("v" to 4325, "type" to "DIAMOND", "amount" to 3)
        assertEquals(map, LegacyItemMap.read(bytes(map)))
    }

    @Test fun `classes outside java lang and java util are refused`() {
        assertFailsWith<InvalidClassException> { LegacyItemMap.read(bytes(hashMapOf("file" to File("x")))) }
    }

    @Test fun `a map with non-string keys is ignored`() {
        assertNull(LegacyItemMap.read(bytes(hashMapOf(1 to "a"))))
    }
}
