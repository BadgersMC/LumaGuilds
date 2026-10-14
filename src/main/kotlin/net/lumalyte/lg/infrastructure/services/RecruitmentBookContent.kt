package net.lumalyte.lg.infrastructure.services

/** Read-only book or Bedrock details content obtained after actor authorization. */
internal data class RecruitmentBookContent(
    val title: String,
    val lines: List<String>,
    val returnLabel: String,
)
