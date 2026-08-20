package net.lumalyte.lg.infrastructure.i18n

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

data class LocalizationCall(
    val file: Path,
    val line: Int,
    val renderer: String,
    val key: String,
    val placeholderNames: Set<String>,
)

data class PlayerTextCandidate(val file: Path, val line: Int, val source: String)

data class PlaceholderMismatch(
    val key: String,
    val expected: Set<String>,
    val actual: Set<String>,
)

data class LocaleSourceInventory(
    val calls: List<LocalizationCall>,
    val dynamicCalls: List<PlayerTextCandidate>,
    val playerTextCandidates: List<PlayerTextCandidate>,
) {
    val literalKeys: Set<String> get() = calls.mapTo(mutableSetOf()) { it.key }

    fun placeholderMismatches(localeValues: Map<String, String>): List<PlaceholderMismatch> =
        calls.mapNotNull { call ->
            val expected = localeValues[call.key]?.let(LocaleSourceScanner::placeholderNames) ?: return@mapNotNull null
            if (expected == call.placeholderNames) {
                null
            } else {
                PlaceholderMismatch(call.key, expected, call.placeholderNames)
            }
        }.sortedBy { it.key }
}

object LocaleSourceScanner {
    private val rendererCall = Regex("""\blang\.(msg|legacy|raw)\s*\(""")
    private val literalKey = Regex("^\"([^\"]+)\"")
    private val namedPair = Regex("""["']([A-Za-z][A-Za-z0-9_]*)["']\s+to\b""")
    private val placeholder = Regex("""</?([A-Za-z][A-Za-z0-9_-]*)(?::[^>]*)?>""")

    private val miniMessageTags = setOf(
        "aqua", "black", "blue", "dark_aqua", "dark_blue", "dark_gray", "dark_green", "dark_purple",
        "dark_red", "gold", "gray", "green", "light_purple", "red", "white", "yellow",
        "bold", "italic", "underlined", "strikethrough", "obfuscated", "reset", "newline", "br",
        "gradient", "rainbow", "transition", "font", "insertion", "click", "hover", "keybind",
        "selector", "score", "nbt", "translatable", "fallback", "lang", "pride", "shadow", "prefix"
    )

    fun scan(root: Path): LocaleSourceInventory {
        val calls = mutableListOf<LocalizationCall>()
        val dynamicCalls = mutableListOf<PlayerTextCandidate>()
        val playerTextCandidates = mutableListOf<PlayerTextCandidate>()

        Files.walk(root).use { paths ->
            paths.asSequence()
                .filter { it.isRegularFile() && it.extension == "kt" }
                .sorted()
                .forEach { file -> scanFile(file, calls, dynamicCalls, playerTextCandidates) }
        }

        return LocaleSourceInventory(
            calls = calls.sortedWith(compareBy({ it.file.toString() }, { it.line }, { it.key })),
            dynamicCalls = dynamicCalls.sortedWith(compareBy({ it.file.toString() }, { it.line })),
            playerTextCandidates = playerTextCandidates.sortedWith(compareBy({ it.file.toString() }, { it.line })),
        )
    }

    fun placeholderNames(value: String): Set<String> =
        placeholder.findAll(value)
            .map { it.groupValues[1] }
            .filterNot { it.lowercase() in miniMessageTags }
            .toSortedSet()

    private fun scanFile(
        file: Path,
        calls: MutableList<LocalizationCall>,
        dynamicCalls: MutableList<PlayerTextCandidate>,
        playerTextCandidates: MutableList<PlayerTextCandidate>,
    ) {
        val source = Files.readString(file)
        source.lineSequence().forEachIndexed { index, line ->
            if ('§' in line) {
                playerTextCandidates += PlayerTextCandidate(file, index + 1, line.trim())
            }
        }

        rendererCall.findAll(source).forEach { match ->
            val openParenthesis = source.indexOf('(', match.range.first)
            val callSource = source.substring(openParenthesis + 1, callEnd(source, openParenthesis)).trim()
            val line = source.substring(0, match.range.first).count { it == '\n' } + 1
            val literal = literalKey.find(callSource)

            if (literal == null) {
                dynamicCalls += PlayerTextCandidate(file, line, source.lineAt(match.range.first))
                return@forEach
            }

            calls += LocalizationCall(
                file = file,
                line = line,
                renderer = match.groupValues[1],
                key = literal.groupValues[1],
                placeholderNames = namedPair.findAll(callSource).map { it.groupValues[1] }.toSortedSet(),
            )
        }
    }

    private fun callEnd(source: String, openParenthesis: Int): Int {
        var depth = 0
        var quote: Char? = null
        var escaped = false

        for (index in openParenthesis until source.length) {
            val character = source[index]
            if (quote != null) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == quote) {
                    quote = null
                }
                continue
            }
            if (character == '\"' || character == '\'') {
                quote = character
            } else if (character == '(') {
                depth++
            } else if (character == ')') {
                depth--
                if (depth == 0) return index
            }
        }
        return source.length
    }

    private fun String.lineAt(index: Int): String =
        substring(lastIndexOf('\n', index).let { if (it < 0) 0 else it + 1 })
            .substringBefore('\n')
            .trim()
}
