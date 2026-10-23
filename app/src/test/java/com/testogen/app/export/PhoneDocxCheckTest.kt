package com.testogen.app.export

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Отладка: раскладка файла, созданного на телефоне (examples/check/big.docx), по модели Paginator
 * с метриками настоящего Times New Roman — для сверки с тем, как файл раскладывает LibreOffice/Word.
 */
class PhoneDocxCheckTest {
    @Test
    fun layoutOfPhoneFile() {
        val f = File("../examples/check/big.docx")
        assumeTrue(f.exists())
        val xml = ZipFile(f).use { z -> z.getInputStream(z.getEntry("word/document.xml")).readBytes().toString(Charsets.UTF_8) }
        val paragraphs = Regex("<w:p>(.*?)</w:p>").findAll(xml).map { m ->
            Regex("<w:t(?: [^>]*)?>(.*?)</w:t>|<w:tab/>").findAll(m.groupValues[1]).joinToString("") { if (it.value == "<w:tab/>") "\t" else it.groupValues[1] }
                .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        }.toList()
        val variants = mutableListOf<DocVariant>()
        var questions = mutableListOf<DocQuestion>()
        var number = 0
        for (p in paragraphs) {
            when {
                p.startsWith("Вариант ") -> {
                    if (number > 0) variants += DocVariant(number, questions)
                    number = p.removePrefix("Вариант ").toInt()
                    questions = mutableListOf()
                }
                Regex("^\\d+\\.\t").containsMatchIn(p) -> {
                    val q = questions.removeAt(questions.lastIndex)
                    questions += q.copy(options = q.options + p.substringAfter('\t'))
                }
                Regex("^\\d+\\. ").containsMatchIn(p) -> questions += DocQuestion(p.substringBefore('.').toInt(), p.substringAfter(". "), emptyList(), 0)
            }
        }
        variants += DocVariant(number, questions)
        val size = Regex("<w:sz w:val=\"(\\d+)\"").find(xml)!!.groupValues[1].toFloat() / 2
        val cols = Regex("<w:cols w:num=\"(\\d+)\"").find(xml)!!.groupValues[1].toInt()
        val landscape = xml.contains("w:orient=\"landscape\"")
        val layout = DocLayout(LayoutMode.PER_PAGE, landscape, cols, size)
        val pages = Paginator(AwtMeasurerForCheck()).paginate(variants, layout)
        val report = buildString {
            appendLine("pages=${pages.size} layout=$layout variants=${variants.size} q0=${variants.first().questions.size} opts=${variants.first().questions.map { it.options.size }} paragraphs=${paragraphs.size}")
            appendLine(paragraphs.take(3).joinToString(" || ") { it.take(60) })
            appendLine("starts=" + pages.mapIndexedNotNull { i, pg -> if (pg.blocks.any { it is Paginator.PlacedTitle }) i + 1 else null })
            pages.forEachIndexed { i, pg -> if (i + 1 !in 29..33) return@forEachIndexed
                val left = pg.blocks.filterIsInstance<Paginator.PlacedQuestion>().filter { it.x < layout.pageWidth / 2 }.map { it.question.number }
                val right = pg.blocks.filterIsInstance<Paginator.PlacedQuestion>().filter { it.x >= layout.pageWidth / 2 }.map { it.question.number }
                appendLine("page ${i + 1} left $left right $right")
                pg.blocks.filterIsInstance<Paginator.PlacedQuestion>().lastOrNull()?.let { appendLine("   last bottom=${it.y + it.height} limit=${layout.margin + layout.contentHeight}") }
            }
        }
        val m = AwtMeasurerForCheck()
        val v15 = variants[14]
        val detail = v15.questions.take(16).joinToString("\n") { q ->
            val t = m.lines("${q.number}. ${q.text}", layout.textWidth, layout.fontSize, true)
            val o = q.options.map { m.lines(it, layout.textWidth - layout.optionIndent, layout.fontSize, false) }
            "Q${q.number} total=${t + o.sum()} text=$t opts=$o"
        }
        File("build/phone-check.txt").writeText(report + "\n" + detail)
        println(report)
    }
}

private class AwtMeasurerForCheck : TextMeasurer {
    private val frc = java.awt.font.FontRenderContext(null, true, true)
    override fun lines(text: String, widthPt: Float, sizePt: Float, bold: Boolean): Int {
        if (text.isEmpty()) return 1
        val font = java.awt.Font("Times New Roman", if (bold) java.awt.Font.BOLD else java.awt.Font.PLAIN, 1).deriveFont(sizePt)
        val s = java.text.AttributedString(text).apply { addAttribute(java.awt.font.TextAttribute.FONT, font) }
        val m = java.awt.font.LineBreakMeasurer(s.iterator, frc)
        var n = 0
        while (m.position < text.length) {
            m.nextLayout(widthPt)
            n++
        }
        return n
    }
}
