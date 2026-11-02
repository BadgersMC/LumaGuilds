package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RecruitmentResponseSessionTest {
    @Test
    fun replacedFormCannotSubmit() {
        val session = RecruitmentResponseSession { { true } }
        var submissions = 0
        val oldForm = session.begin()
        val currentForm = session.begin()
        if (oldForm()) submissions++
        if (currentForm()) submissions++
        if (currentForm()) submissions++
        assertEquals(1, submissions)
    }

    @Test
    fun navigationRejectsOldResponse() {
        var navigation = 0
        val session =
            RecruitmentResponseSession {
                val token = navigation
                val guard: () -> Boolean = { navigation == token }
                guard
            }
        var submissions = 0
        val response = session.begin()
        navigation++
        if (response()) submissions++
        assertEquals(0, submissions)
    }
}
