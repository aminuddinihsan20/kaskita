package com.kaskita.lokal.ui.reports

sealed interface AiLine {
    data class Heading(val text: String) : AiLine
    data class Bullet(val text: String) : AiLine
    data class Body(val text: String) : AiLine
}

private val knownHeadings = setOf(
    "ringkasan bulan ini",
    "pengeluaran terbesar",
    "saran pengeluaran bulan depan",
    "saran menabung bulan depan",
)

/**
 * Turns the model's plain text into lines the card can style. The prompt asks for no markdown, but models add it
 * anyway, so `#`, `**`, `__` and `* ` bullets are flattened here rather than shown as symbols.
 */
fun parseAiText(raw: String): List<AiLine> = raw.replace("\r\n", "\n").lines().mapNotNull { original ->
    var line = original.trim().replace("**", "").replace("__", "")
    if (line.isEmpty()) return@mapNotNull null
    val hashes = line.takeWhile { it == '#' }.length
    if (hashes in 1..6 && line.getOrNull(hashes) == ' ') {
        return@mapNotNull AiLine.Heading(line.drop(hashes).trim().trimEnd(':'))
    }
    if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
        return@mapNotNull AiLine.Bullet(line.drop(2).trim())
    }
    line = line.trim()
    if (line.trimEnd(':').lowercase() in knownHeadings) AiLine.Heading(line.trimEnd(':')) else AiLine.Body(line)
}
