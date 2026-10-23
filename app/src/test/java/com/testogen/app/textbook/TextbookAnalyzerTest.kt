package com.testogen.app.textbook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TextbookAnalyzerTest {

    /**
     * Настоящий учебник из examples/ (в git его нет — авторское право). Строки выгружены
     * скриптом с теми же полями, что даёт PdfExtractor. Без файла тест пропускается.
     */
    /** Выгрузка через PyMuPDF. */
    @Test
    fun realTextbookStructurePyMuPdf() = check(File("../examples/book_lines.jsonl"), "pymupdf")

    /** Выгрузка через PDFBox — тем же способом, что PdfExtractor на телефоне. */
    @Test
    fun realTextbookStructurePdfBox() = check(File("../examples/book_lines_pdfbox.jsonl"), "pdfbox")

    private fun check(file: File, name: String) {
        assumeTrue("нет ${file.path}", file.exists())
        val pages = file.readLines().filter { it.isNotBlank() }.map { row ->
            val o = Json.parseToJsonElement(row).jsonObject
            RawPage(
                index = o["p"]!!.jsonPrimitive.int,
                width = o["w"]!!.jsonPrimitive.float,
                height = o["h"]!!.jsonPrimitive.float,
                lines = o["lines"]!!.jsonArray.map { l ->
                    val lo = l.jsonObject
                    RawLine(
                        text = lo["t"]!!.jsonPrimitive.content,
                        size = lo["s"]!!.jsonPrimitive.float,
                        bold = lo["b"]!!.jsonPrimitive.boolean,
                        italic = lo["i"]!!.jsonPrimitive.boolean,
                        x = lo["x"]!!.jsonPrimitive.float,
                        y = lo["y"]!!.jsonPrimitive.float,
                    )
                },
            )
        }
        val book = TextbookAnalyzer.analyze(pages)
        val report = buildString {
            appendLine("title=${book.title} grade=${book.grade} glossary=${book.glossary.size} toc=${book.toc.size}")
            book.chapters.forEachIndexed { i, c -> appendLine("CHAPTER $i ${c.label}. ${c.title}") }
            for (s in book.sections) {
                val chars = s.blocks.sumOf { it.text.length }
                val kinds = s.blocks.groupingBy { it.kind }.eachCount()
                appendLine(
                    "${s.kind} ch=${s.chapterIndex} ${s.label} «${s.title}» стр.${s.bookPageStart}–${s.bookPageEnd} " +
                        "pdf ${s.pdfPageStart?.plus(1)}–${s.pdfPageEnd?.plus(1)} chars=$chars $kinds kw=${s.keywords.take(4)}",
                )
            }
            book.warnings.forEach { appendLine("WARN $it") }
            book.glossary.take(3).forEach { appendLine("GLOSS ${it.term} — ${it.definition.take(60)}") }
            val p67 = book.paragraphs.first { it.numFrom == 6 }
            book.paragraphs.flatMap { s -> s.blocks.filter { it.kind == BlockKind.HEADING }.map { s.label + " " + it.text } }.forEach { appendLine("H $it") }
            p67.blocks.take(25).forEach { appendLine("  [${it.kind} p${it.pdfPage + 1}/${it.bookPage}] ${it.text.take(110)}") }
        }
        File("build/analyzer-report-$name.txt").writeText(report)
        println(report)

        assertEquals(5, book.chapters.size)
        assertEquals(19, book.paragraphs.size)
        assertEquals(
            listOf("1", "2–3", "4", "5", "6–7", "8", "9", "10", "11", "12", "13–14", "15", "16", "17", "18", "19", "20", "21–22", "23"),
            book.paragraphs.map { it.label.removePrefix("§ ") },
        )
        assertEquals("Государство", book.paragraphs.first { it.numFrom == 6 }.title)
        assertEquals(50, book.paragraphs.first { it.numFrom == 6 }.bookPageStart)
        assertTrue(book.glossary.size > 30)
        assertEquals(9, book.grade)
        assertTrue(book.warnings.joinToString(), book.warnings.isEmpty())
        // Колонтитулы выкинуты, переносы склеены
        val all = book.sections.flatMap { it.blocks }.joinToString("\n") { it.text }
        assertTrue(!all.contains("§ 1. Человек\n"))
        assertTrue(all.contains("великой истории"))
        // Подзаголовки и ключевые понятия узнаются
        val p1 = book.paragraphs.first()
        assertTrue(p1.blocks.any { it.kind == BlockKind.HEADING && it.text.startsWith("ЗАГАДКА") })
        assertEquals(listOf("человек", "индивид", "личность"), p1.keywords.take(3))
        assertTrue(p1.blocks.any { it.kind == BlockKind.QUESTIONS })
    }

    @Test
    fun plainTextWithParagraphMarks() {
        val text = """
            Глава 1. Древняя Русь
            § 1. Восточные славяне
            Восточные славяне жили по берегам рек.
            Они занимались земледелием.
            § 2–3. Образование государства
            В 862 году был призван Рюрик.
            См. § 1.
        """.trimIndent()
        val page = RawPage(0, 0f, 0f, text.lines().map { RawLine(it.trim(), 10f) }, hasLayout = false)
        val book = TextbookAnalyzer.analyze(listOf(page, page.copy(index = 1, lines = List(30) { RawLine("Текст текст текст текст текст", 10f) })))
        assertEquals(1, book.chapters.size)
        assertEquals(listOf("§ 1", "§ 2–3"), book.paragraphs.map { it.label })
        assertEquals("Образование государства", book.paragraphs[1].title)
    }

    @Test
    fun tocParsing() {
        val toc = TextbookAnalyzer.parseToc(
            listOf("Глава II. Государство и право . . . . . . 49", "§ 6—7.", "Государство  . . . . . . . 50", "§ 13—14. Культура. Искусство . . . . . 114"),
        )
        assertEquals(3, toc.size)
        assertEquals("Глава II", toc[0].chapterLabel)
        assertEquals(6, toc[1].numFrom)
        assertEquals(7, toc[1].numTo)
        assertEquals(50, toc[1].bookPage)
        assertEquals("Культура. Искусство", toc[2].title)
    }
}
