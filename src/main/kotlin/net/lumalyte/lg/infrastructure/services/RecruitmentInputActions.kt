package net.lumalyte.lg.infrastructure.services

/** Session guard and callbacks supplied by the authorized interaction controller. */
internal data class RecruitmentInputActions(
    val valid: () -> Boolean,
    val accept: (String) -> Unit,
    val cancel: () -> Unit,
)
