package com.kaskita.lokal.ui.reports

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTextTest {
    @Test
    fun knownHeadingsBulletsAndBodyAreTold() {
        val lines = parseAiText(
            """
            Ringkasan bulan ini
            Pemasukan Rp5.000.000, pengeluaran Rp300.000.

            Saran menabung bulan depan:
            - Sisihkan Rp1.000.000.
            Ini analisis otomatis dari catatanmu, bukan nasihat keuangan profesional.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                AiLine.Heading("Ringkasan bulan ini"),
                AiLine.Body("Pemasukan Rp5.000.000, pengeluaran Rp300.000."),
                AiLine.Heading("Saran menabung bulan depan"),
                AiLine.Bullet("Sisihkan Rp1.000.000."),
                AiLine.Body("Ini analisis otomatis dari catatanmu, bukan nasihat keuangan profesional."),
            ),
            lines,
        )
    }

    @Test
    fun markdownTheModelAddedAnywayIsFlattened() {
        val lines = parseAiText("## Pengeluaran terbesar\n**Makanan** menyumbang __75%__.\n* Kurangi jajan\r\n  * Masak sendiri")
        assertEquals(
            listOf(
                AiLine.Heading("Pengeluaran terbesar"),
                AiLine.Body("Makanan menyumbang 75%."),
                AiLine.Bullet("Kurangi jajan"),
                AiLine.Bullet("Masak sendiri"),
            ),
            lines,
        )
    }

    @Test
    fun aMarkdownHeadingOfAnyNameStillBecomesAHeading() {
        assertEquals(listOf(AiLine.Heading("Catatan tambahan")), parseAiText("### Catatan tambahan"))
    }

    @Test
    fun blankInputGivesNothing() {
        assertEquals(emptyList<AiLine>(), parseAiText("  \n\n "))
    }

    @Test
    fun numbersAndPercentSignsInsideTextSurvive() {
        assertEquals(listOf(AiLine.Body("Naik 12,5% dari Rp1.000 * 2 = Rp2.000")), parseAiText("Naik 12,5% dari Rp1.000 * 2 = Rp2.000"))
    }
}
