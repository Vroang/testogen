package com.testogen.app.textbook

import android.util.Xml
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * PDF постранично: строки с размером шрифта, жирностью и положением — для TextbookAnalyzer.
 * Файл читается с диска, крупные данные уходят во временные файлы, а не в память.
 */
object PdfExtractor {

    /** Страницы и название из свойств PDF (если оно там есть). */
    class Result(val pages: List<RawPage>, val title: String?)

    suspend fun extract(file: File, onPage: suspend (done: Int, total: Int) -> Unit): Result {
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val title = runCatching { doc.documentInformation?.title }.getOrNull()
            val total = doc.numberOfPages
            val stripper = LineStripper()
            val pages = ArrayList<RawPage>(total)
            for (i in 0 until total) {
                coroutineContext.ensureActive()
                val box = doc.getPage(i).mediaBox
                stripper.reset(i + 1)
                stripper.getText(doc)
                pages += RawPage(i, box.width, box.height, stripper.lines.toList())
                onPage(i + 1, total)
            }
            return Result(pages, title)
        }
    }

    /** Собирает строки вместе с размером шрифта и координатами первого символа. */
    private class LineStripper : PDFTextStripper() {
        val lines = mutableListOf<RawLine>()
        private val text = StringBuilder()
        private val positions = mutableListOf<TextPosition>()

        fun reset(page: Int) {
            startPage = page
            endPage = page
            lines.clear()
            text.clear()
            positions.clear()
        }

        override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
            text.append(string)
            positions += textPositions
        }

        override fun writeWordSeparator() {
            text.append(' ')
        }

        override fun writeLineSeparator() = flush()

        override fun writePageEnd() = flush()

        private fun flush() {
            // Маркер списка из «символьного» шрифта PDFBox отдаёт пробелом в начале строки
            val raw = text.toString()
            val t = (if (raw.firstOrNull()?.isWhitespace() == true && raw.isNotBlank()) "• " else "") + raw.trim()
            if (t.isNotEmpty() && positions.isNotEmpty()) {
                // Размер — тот, которым набрано больше всего символов строки
                val size = positions.groupingBy { (it.fontSizeInPt * 2).toInt() / 2f }.eachCount().maxBy { it.value }.key
                val first = positions.first()
                val fontName = first.font?.name.orEmpty()
                lines += RawLine(
                    text = t,
                    size = size,
                    bold = BOLD.containsMatchIn(fontName) || (first.font?.fontDescriptor?.fontWeight ?: 0f) >= 600f,
                    italic = ITALIC.containsMatchIn(fontName),
                    x = first.xDirAdj,
                    y = first.yDirAdj - first.heightDir,
                )
            }
            text.clear()
            positions.clear()
        }

        companion object {
            val BOLD = Regex("(?i)bold|demi|black|heavy|semibold")
            val ITALIC = Regex("(?i)italic|oblique")
        }
    }
}

/** DOCX: абзацы из word/document.xml потоковым разбором (без загрузки всего файла в память). */
object DocxExtractor {
    fun extract(file: File): List<RawPage> {
        val lines = mutableListOf<RawLine>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: error("Это не похоже на файл Word (.docx)")
            zip.getInputStream(entry).use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(input, "UTF-8")
                val paragraph = StringBuilder()
                var event = parser.eventType
                var inText = false
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "w:t" -> inText = true
                            "w:tab" -> paragraph.append(' ')
                            "w:br" -> paragraph.append('\n')
                        }
                        XmlPullParser.TEXT -> if (inText) paragraph.append(parser.text)
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "w:t" -> inText = false
                            "w:p" -> {
                                paragraph.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines += RawLine(it, 10f) }
                                paragraph.clear()
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
        return listOf(RawPage(0, 0f, 0f, lines, hasLayout = false))
    }
}

/** TXT: кодировка UTF-8 или Windows-1251; абзацы — по пустым строкам, переносы внутри абзаца склеиваются. */
object TxtExtractor {
    fun extract(file: File): List<RawPage> {
        val bytes = file.readBytes()
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val lines = mutableListOf<RawLine>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotBlank()) lines += RawLine(buffer.toString().trim(), 10f)
            buffer.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            val isHeading = line.startsWith("§") || line.matches(Regex("(?iu)^(глава|раздел)\\s+\\S+.*")) ||
                (line.length in 3..80 && line == line.uppercase() && line.any { it.isLetter() })
            when {
                line.isEmpty() -> flush()
                isHeading -> {
                    flush()
                    lines += RawLine(line, 10f)
                }
                buffer.isEmpty() -> buffer.append(line)
                buffer.endsWith("-") && line.first().isLowerCase() -> buffer.setLength(buffer.length - 1).also { buffer.append(line) }
                else -> buffer.append(' ').append(line)
            }
        }
        flush()
        return listOf(RawPage(0, 0f, 0f, lines, hasLayout = false))
    }

    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix(Char(0xFEFF).toString())
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1251"))
    }
}
