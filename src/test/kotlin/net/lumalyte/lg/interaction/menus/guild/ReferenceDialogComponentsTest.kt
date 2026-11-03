package net.lumalyte.lg.interaction.menus.guild

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.recruitment.RecruitmentMode
import net.lumalyte.lg.domain.recruitment.RecruitmentProfile
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ReferenceDialogComponentsTest : RecruitmentDialogFixture() {
    @Test
    fun progressBarClampsOvershootAndZeroTarget() {
        val plain = PlainTextComponentSerializer.plainText()
        assertEquals("▰".repeat(12), plain.serialize(questProgressBar(Long.MAX_VALUE, 5)))
        assertEquals("▱".repeat(12), plain.serialize(questProgressBar(12, 0)))
        assertEquals("▱".repeat(12), plain.serialize(questProgressBar(-1, 5)))
    }

    @Test
    fun feeAndLevelFiltersCombineAndResetWithoutMatchingHiddenPitch() {
        val id = UUID.randomUUID()
        val guild =
            Guild(id, "Builders", level = 35, createdAt = Instant.EPOCH, joinFeeEnabled = true, joinFeeAmount = 100)
        val profile = RecruitmentProfile(id, RecruitmentMode.IN_GAME, pitch = "SecretSearchWord")
        controller.feeFilter = 2
        controller.levelFilter = 1
        assertTrue(controller.matchesPreferences(guild, profile))
        controller.feeFilter = 1
        assertFalse(controller.matchesPreferences(guild, profile))
        controller.feeFilter = 0
        controller.levelFilter = 2
        assertFalse(controller.matchesPreferences(guild, profile))
        controller.levelFilter = 0
        controller.query = "SecretSearchWord"
        assertFalse(controller.matchesPreferences(guild, profile))
        controller.query = "build"
        assertTrue(controller.matchesPreferences(guild, profile))
    }
}
