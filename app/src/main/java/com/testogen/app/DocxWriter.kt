package com.testogen.app

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Имена файлов экспорта по теме главного экрана. */
object DocxFiles {
    fun fileName(topic: String, kind: String): String {
        val cleaned = topic
            .replace(Regex("[/\\\\:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val title = if (cleaned.isEmpty()) {
            "ТестоГен"
        } else {
            cleaned.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }
        }
        return "$title ($kind).docx"
    }
}

/**
 * Прямая сборка DOCX (ZIP + XML) без сторонних библиотек.
 * Тест: каждый вариант — с новой страницы.
 * Ответы: отдельный файл, для каждого варианта таблица «№ | Ответ».
 */
object DocxWriter {

    private val ANSWER_LABELS = listOf("А", "Б", "В", "Г")

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private val CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "</Types>"

    private val ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"

    private fun pageBreak(): String =
        "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"

    private fun heading(text: String, sizeHalfPoints: Int): String =
        "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"$sizeHalfPoints\"/></w:rPr>" +
            "<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"

    private fun boldParagraph(text: String): String =
        "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"

    private fun indentedParagraph(text: String): String =
        "<w:p><w:pPr><w:ind w:left=\"340\"/></w:pPr><w:r>" +
            "<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"

    private fun emptyParagraph(): String = "<w:p/>"

    private fun cell(text: String, bold: Boolean): String {
        val rPr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
        return "<w:tc><w:tcPr/><w:p><w:r>$rPr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p></w:tc>"
    }

    private fun row(vararg cells: Pair<String, Boolean>): String =
        "<w:tr>" + cells.joinToString("") { cell(it.first, it.second) } + "</w:tr>"

    private fun bordersTable(rowsXml: String): String =
        "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>" +
            "<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>" +
            "<w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>" +
            "<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>" +
            "<w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>" +
            "<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>" +
            "<w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"auto\"/>" +
            "</w:tblBorders><w:tblCellMar>" +
            "<w:top w:w=\"60\" w:type=\"dxa\"/><w:left w:w=\"100\" w:type=\"dxa\"/>" +
            "<w:bottom w:w=\"60\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/>" +
            "</w:tblCellMar></w:tblPr>" +
            "<w:tblGrid><w:gridCol w:w=\"800\"/><w:gridCol w:w=\"1600\"/></w:tblGrid>" +
            rowsXml +
            "</w:tbl>"

    private val SECTION_PROPS =
        "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>"

    /** Файл теста: варианты без ответов, каждый с новой страницы. */
    fun buildTestDocx(variants: List<List<Question>>): ByteArray {
        val sb = StringBuilder()
        sb.append(xmlProlog())
        sb.append("<w:body>")
        variants.forEachIndexed { index, questions ->
            if (index > 0) sb.append(pageBreak())
            sb.append(heading("Вариант ${index + 1}", 36))
            sb.append(emptyParagraph())
            questions.forEachIndexed { questionIndex, question ->
                sb.append(boldParagraph("${questionIndex + 1}. ${question.text}"))
                val options = listOf(question.optionA, question.optionB, question.optionC, question.optionD)
                options.forEachIndexed { optionIndex, optionText ->
                    sb.append(indentedParagraph("${ANSWER_LABELS[optionIndex]}) $optionText"))
                }
                sb.append(emptyParagraph())
            }
        }
        sb.append(SECTION_PROPS)
        sb.append("</w:body></w:document>")
        return zipDocx(sb.toString())
    }

    /** Файл ответов: для каждого варианта компактная таблица «№ | Ответ». */
    fun buildAnswersDocx(variants: List<List<Question>>): ByteArray {
        val sb = StringBuilder()
        sb.append(xmlProlog())
        sb.append("<w:body>")
        variants.forEachIndexed { index, questions ->
            if (index > 0) sb.append(pageBreak())
            sb.append(heading("Вариант ${index + 1} — ответы", 28))
            sb.append(emptyParagraph())
            val rows = StringBuilder()
            rows.append(row("№" to true, "Ответ" to true))
            questions.forEachIndexed { questionIndex, question ->
                val answer = ANSWER_LABELS.getOrElse(question.correctIndex) { "" }
                rows.append(row("${questionIndex + 1}" to false, answer to false))
            }
            sb.append(bordersTable(rows.toString()))
        }
        sb.append(SECTION_PROPS)
        sb.append("</w:body></w:document>")
        return zipDocx(sb.toString())
    }

    private fun xmlProlog(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"

    private fun zipDocx(documentXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(CONTENT_TYPES.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(ROOT_RELS.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
