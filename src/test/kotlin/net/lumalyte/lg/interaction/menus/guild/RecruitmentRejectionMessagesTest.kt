package net.lumalyte.lg.interaction.menus.guild

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RecruitmentRejectionMessagesTest {
    @Test
    fun editDenialHasActionableHint() {
        assertEquals(
            RecruitmentRejection.EDIT_DENIED,
            recruitmentRejectionReason(IllegalArgumentException("Recruitment settings permission required")),
        )
    }

    @Test
    fun adapterDetailsStayPrivate() {
        val failure = java.sql.SQLException("private database address and account details")
        assertEquals(null, recruitmentRejectionReason(failure))
        assertEquals(null, recruitmentRejectionReason(IllegalArgumentException("private input details")))
    }
}
