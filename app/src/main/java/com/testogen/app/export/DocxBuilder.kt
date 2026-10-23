package com.testogen.app.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Лёгкий генератор .docx (XML + zip, без сторонних библиотек).
 * Times New Roman, лист A4, межстрочный интервал задан точно — так файл совпадает с предпросмотром
 * (сверено с LibreOffice: те же переносы строк и те же разрывы страниц).
 */
object DocxBuilder {

    private fun tw(pt: Float) = (pt * 20).roundToInt()

    /**
     * Файл теста с выбранной раскладкой; разбивку на страницы «несколько на листе» и место ответов в том же файле
     * задаёт [paginator] — та же, что в предпросмотре.
     */
    fun test(title: String, variants: List<DocVariant>, l: DocLayout, paginator: Paginator): ByteArray {
        val body = StringBuilder()
        val layout = paginator.layout(variants, l)
        if (l.mode == LayoutMode.COMPACT) compactBody(body, layout.compactRows, l) else perPageBody(body, variants, l)
        if (layout.answerItems.isNotEmpty()) {
            answerItems(body, layout.answerItems, l)
            // После таблицы документ должен кончаться абзацем
            body.append(separator(pageBreak = false))
        } else if (l.mode == LayoutMode.COMPACT) {
            body.append(separator(pageBreak = false))
        }
        body.append(sectPr(l.landscape, l.margin, if (l.mode == LayoutMode.PER_PAGE) l.columns else 1, l.columnGap))
        return zip(title, document(body.toString()), styles(l.fontSize, l.lineHeight))
    }

    /** Компактный файл ответов: по каждому варианту номер вопроса и номер верного ответа. */
    fun answers(title: String, variants: List<DocVariant>): ByteArray {
        val size = 11f
        val line = DocLayout.lineHeight(size)
        val body = StringBuilder()
        body.append(paragraph("Ответы. ${esc(title)}", bold = true, size = 13f, line = DocLayout.lineHeight(13f), after = 8f, center = true))
        val maxQ = variants.maxOfOrNull { it.questions.size } ?: 0
        val contentWidth = DocLayout.A4_SHORT - 2 * 1.5f * DocLayout.CM
        for (chunk in (1..maxQ).chunked(ANSWER_COLUMNS)) {
            val first = 2.2f * DocLayout.CM
            val cell = (contentWidth - first) / ANSWER_COLUMNS
            body.append("<w:tbl><w:tblPr><w:tblW w:w=\"${tw(first + cell * chunk.size)}\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>")
            body.append(borders("single", "8C8C8C"))
            body.append("<w:tblCellMar><w:left w:w=\"40\" w:type=\"dxa\"/><w:right w:w=\"40\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>")
            body.append("<w:gridCol w:w=\"${tw(first)}\"/>")
            chunk.forEach { _ -> body.append("<w:gridCol w:w=\"${tw(cell)}\"/>") }
            body.append("</w:tblGrid>")
            // Шапка: номера вопросов
            body.append("<w:tr><w:trPr><w:tblHeader/><w:cantSplit/></w:trPr>")
            body.append(tc(first, paragraph("Вариант", bold = true, size = size, line = line, center = true)))
            chunk.forEach { n -> body.append(tc(cell, paragraph("$n", bold = true, size = size, line = line, center = true), shade = true)) }
            body.append("</w:tr>")
            for (v in variants) {
                body.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
                body.append(tc(first, paragraph("${v.number}", bold = true, size = size, line = line, center = true)))
                chunk.forEach { n ->
                    val q = v.questions.getOrNull(n - 1)
                    body.append(tc(cell, paragraph(q?.let { "${it.correct + 1}" }.orEmpty(), size = size, line = line, center = true)))
                }
                body.append("</w:tr>")
            }
            body.append("</w:tbl>")
            body.append(paragraph("", size = size, line = line, after = 6f))
        }
        body.append(sectPr(false, 1.5f * DocLayout.CM, 1, 0f))
        return zip("Ответы. $title", document(body.toString()), styles(size, line))
    }

    // ---------- тест ----------

    /**
     * Каждая страница — своя таблица: перед ней крошечный пустой абзац (со второй страницы — «с новой страницы»),
     * после последней — такой же. Так разрывы страниц стоят ровно там, где их поставил Paginator.
     */
    private fun compactBody(sb: StringBuilder, pages: List<List<Paginator.TableRow>>, l: DocLayout) {
        val pad = tw(l.cellPadding)
        pages.forEachIndexed { p, rows ->
            sb.append(separator(pageBreak = p > 0))
            sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"${tw(l.contentWidth)}\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>")
            sb.append(if (l.cutLines) borders("dashed", "8C8C8C") else borders("nil", "auto"))
            sb.append("<w:tblCellMar><w:top w:w=\"$pad\" w:type=\"dxa\"/><w:left w:w=\"$pad\" w:type=\"dxa\"/><w:bottom w:w=\"$pad\" w:type=\"dxa\"/><w:right w:w=\"$pad\" w:type=\"dxa\"/></w:tblCellMar>")
            sb.append("</w:tblPr><w:tblGrid>")
            repeat(l.columns) { sb.append("<w:gridCol w:w=\"${tw(l.columnWidth)}\"/>") }
            sb.append("</w:tblGrid>")
            for (row in rows) {
                // Строка таблицы (ряд вариантов) не разрывается между страницами
                sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
                for (part in row.parts) {
                    val content = StringBuilder()
                    if (part == null) {
                        content.append(paragraph("", size = l.fontSize, line = l.lineHeight))
                    } else {
                        // Продолжение варианта на новой странице — без заголовка и без отступа перед первым вопросом
                        variantParagraphs(content, if (part.continued) null else Paginator.titleText(part.variant), part.questions, l, pageBreak = false)
                    }
                    sb.append(tc(l.columnWidth, content.toString()))
                }
                sb.append("</w:tr>")
            }
            sb.append("</w:tbl>")
        }
    }

    /** Пустой абзац высотой [height] пунктов; [columnBreak] — с разрывом колонки в начале. */
    private fun separator(pageBreak: Boolean, height: Float = Paginator.SEPARATOR, columnBreak: Boolean = false) =
        "<w:p><w:pPr>" + (if (pageBreak) "<w:pageBreakBefore/>" else "") +
            "<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"${tw(height)}\" w:lineRule=\"exact\"/>" +
            "<w:rPr><w:sz w:val=\"2\"/><w:szCs w:val=\"2\"/></w:rPr></w:pPr>" +
            (if (columnBreak) "<w:r><w:rPr><w:sz w:val=\"2\"/><w:szCs w:val=\"2\"/></w:rPr><w:br w:type=\"column\"/></w:r>" else "") +
            "</w:p>"

    /** Ответы в файле теста: служебные абзацы и куски таблицы ровно там, где их поставил Paginator. */
    private fun answerItems(sb: StringBuilder, items: List<Paginator.AnswerItem>, l: DocLayout) {
        val size = l.fontSize
        val line = DocLayout.lineHeight(size)
        for (item in items) {
            when (item) {
                is Paginator.AnswerItem.Spacer -> sb.append(
                    separator(pageBreak = item.brk == Paginator.Break.PAGE, height = item.height, columnBreak = item.brk == Paginator.Break.COLUMN),
                )
                is Paginator.AnswerItem.Table -> {
                    val t = item.table
                    sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"${tw(t.width)}\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/>")
                    sb.append(borders("single", "8C8C8C"))
                    sb.append("<w:tblCellMar><w:top w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"$ANSWER_PAD\" w:type=\"dxa\"/><w:bottom w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"$ANSWER_PAD\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>")
                    sb.append("<w:gridCol w:w=\"${tw(t.firstWidth)}\"/>")
                    t.numbers.forEach { _ -> sb.append("<w:gridCol w:w=\"${tw(t.cellWidth)}\"/>") }
                    sb.append("</w:tblGrid>")
                    if (t.title) {
                        // Строка «Ответы»: сверху пунктир отреза (или ничего), по бокам рамки нет
                        val top = if (t.cutLine) "<w:top w:val=\"dashed\" w:sz=\"6\" w:space=\"0\" w:color=\"8C8C8C\"/>" else "<w:top w:val=\"nil\"/>"
                        sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr><w:tc><w:tcPr><w:tcW w:w=\"${tw(t.width)}\" w:type=\"dxa\"/><w:gridSpan w:val=\"${t.numbers.size + 1}\"/>")
                        sb.append("<w:tcBorders>$top<w:left w:val=\"nil\"/><w:right w:val=\"nil\"/></w:tcBorders></w:tcPr>")
                        sb.append(paragraph("Ответы", bold = true, size = size, line = line, center = true))
                        sb.append("</w:tc></w:tr>")
                    }
                    sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
                    sb.append(tc(t.firstWidth, paragraph(ANSWER_HEADER, bold = true, size = size, line = line)))
                    t.numbers.forEach { n -> sb.append(tc(t.cellWidth, paragraph("$n", bold = true, size = size, line = line, center = true), shade = true)) }
                    sb.append("</w:tr>")
                    for (v in t.rows) {
                        sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
                        sb.append(tc(t.firstWidth, paragraph("${v.number}", bold = true, size = size, line = line)))
                        t.numbers.forEach { n ->
                            val q = v.questions.getOrNull(n - 1)
                            sb.append(tc(t.cellWidth, paragraph(q?.let { "${it.correct + 1}" }.orEmpty(), size = size, line = line, center = true)))
                        }
                        sb.append("</w:tr>")
                    }
                    sb.append("</w:tbl>")
                }
            }
        }
    }

    private fun perPageBody(sb: StringBuilder, variants: List<DocVariant>, l: DocLayout) {
        variants.forEachIndexed { i, v -> variantParagraphs(sb, Paginator.titleText(v.number), v.questions, l, pageBreak = i > 0) }
    }

    /** [title] null — продолжение варианта: заголовка нет, у первого вопроса нет отступа перед ним. */
    private fun variantParagraphs(sb: StringBuilder, title: String?, questions: List<DocQuestion>, l: DocLayout, pageBreak: Boolean) {
        if (title != null) {
            sb.append(
                paragraph(
                    title,
                    bold = true,
                    size = l.fontSize,
                    line = l.lineHeight,
                    after = Paginator.spacingAfterTitle(l),
                    center = true,
                    keepNext = true,
                    pageBreakBefore = pageBreak,
                ),
            )
        }
        questions.forEachIndexed { qi, q ->
            // Вопрос и его варианты ответа держатся вместе
            sb.append(
                paragraph(
                    esc("${q.number}. ${q.text}"),
                    bold = true,
                    size = l.fontSize,
                    line = l.lineHeight,
                    before = if (title == null && qi == 0) 0f else Paginator.spacingBeforeQuestion(l),
                    keepNext = true,
                ),
            )
            q.options.forEachIndexed { i, option ->
                sb.append(
                    paragraph(
                        "${i + 1}.<w:tab/>" + esc(option),
                        size = l.fontSize,
                        line = l.lineHeight,
                        hanging = l.optionIndent,
                        keepNext = i < q.options.lastIndex,
                        rawText = true,
                    ),
                )
            }
        }
    }

    // ---------- XML ----------

    private fun paragraph(
        text: String,
        bold: Boolean = false,
        size: Float,
        line: Float,
        before: Float = 0f,
        after: Float = 0f,
        center: Boolean = false,
        hanging: Float = 0f,
        keepNext: Boolean = false,
        pageBreakBefore: Boolean = false,
        rawText: Boolean = false,
    ): String = buildString {
        append("<w:p><w:pPr>")
        if (keepNext) append("<w:keepNext/>")
        append("<w:keepLines/>")
        if (pageBreakBefore) append("<w:pageBreakBefore/>")
        append("<w:spacing w:before=\"${tw(before)}\" w:after=\"${tw(after)}\" w:line=\"${tw(line)}\" w:lineRule=\"exact\"/>")
        if (hanging > 0) append("<w:ind w:left=\"${tw(hanging)}\" w:hanging=\"${tw(hanging)}\"/>")
        if (center) append("<w:jc w:val=\"center\"/>")
        append("</w:pPr>")
        if (text.isNotEmpty()) {
            append("<w:r><w:rPr>")
            if (bold) append("<w:b/><w:bCs/>")
            append("<w:sz w:val=\"${(size * 2).roundToInt()}\"/><w:szCs w:val=\"${(size * 2).roundToInt()}\"/></w:rPr>")
            if (rawText) {
                // Внутри уже есть <w:tab/>: разбиваем на текстовые куски
                val parts = text.split("<w:tab/>")
                parts.forEachIndexed { i, p ->
                    if (i > 0) append("<w:tab/>")
                    if (p.isNotEmpty()) append("<w:t xml:space=\"preserve\">$p</w:t>")
                }
            } else {
                append("<w:t xml:space=\"preserve\">$text</w:t>")
            }
            append("</w:r>")
        }
        append("</w:p>")
    }

    private fun tc(width: Float, content: String, shade: Boolean = false) =
        "<w:tc><w:tcPr><w:tcW w:w=\"${tw(width)}\" w:type=\"dxa\"/>" +
            (if (shade) "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"EFEFEF\"/>" else "") +
            "</w:tcPr>$content</w:tc>"

    private fun borders(style: String, color: String): String {
        val b = if (style == "nil") "w:val=\"nil\"" else "w:val=\"$style\" w:sz=\"6\" w:space=\"0\" w:color=\"$color\""
        return "<w:tblBorders><w:top $b/><w:left $b/><w:bottom $b/><w:right $b/><w:insideH $b/><w:insideV $b/></w:tblBorders>"
    }

    private fun sectPr(landscape: Boolean, margin: Float, columns: Int, gap: Float): String {
        val w = tw(if (landscape) DocLayout.A4_LONG else DocLayout.A4_SHORT)
        val h = tw(if (landscape) DocLayout.A4_SHORT else DocLayout.A4_LONG)
        val m = tw(margin)
        return "<w:sectPr><w:pgSz w:w=\"$w\" w:h=\"$h\"" + (if (landscape) " w:orient=\"landscape\"" else "") + "/>" +
            "<w:pgMar w:top=\"$m\" w:right=\"$m\" w:bottom=\"$m\" w:left=\"$m\" w:header=\"0\" w:footer=\"0\" w:gutter=\"0\"/>" +
            "<w:cols w:num=\"$columns\" w:space=\"${tw(gap)}\"/></w:sectPr>"
    }

    private fun document(body: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>""" +
            body + "</w:body></w:document>"

    private fun styles(size: Float, line: Float) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr>""" +
            """<w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:eastAsia="Times New Roman" w:cs="Times New Roman"/>""" +
            """<w:color w:val="000000"/><w:sz w:val="${(size * 2).roundToInt()}"/><w:szCs w:val="${(size * 2).roundToInt()}"/><w:lang w:val="ru-RU"/>""" +
            """</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:before="0" w:after="0" w:line="${tw(line)}" w:lineRule="exact"/>""" +
            """</w:pPr></w:pPrDefault></w:docDefaults>""" +
            """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>""" +
            """<w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr>""" +
            """<w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/>""" +
            """<w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style></w:styles>"""

    private const val SETTINGS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            """<w:defaultTabStop w:val="709"/><w:characterSpacingControl w:val="doNotCompress"/>""" +
            """<w:compat><w:compatSetting w:name="compatibilityMode" w:uri="http://schemas.microsoft.com/office/word" w:val="15"/></w:compat>""" +
            """</w:settings>"""

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""" +
            """<Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>""" +
            """<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""" +
            """</Types>"""

    private const val RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>""" +
            """</Relationships>"""

    private const val DOC_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>""" +
            """</Relationships>"""

    private fun core(title: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${esc(title)}</dc:title><dc:creator>ТестоГен</dc:creator></cp:coreProperties>"""

    private fun zip(title: String, document: String, styles: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", RELS)
            put("word/_rels/document.xml.rels", DOC_RELS)
            put("word/document.xml", document)
            put("word/styles.xml", styles)
            put("word/settings.xml", SETTINGS)
            put("docProps/core.xml", core(title))
        }
        return out.toByteArray()
    }

    /** Экранирование для XML; управляющие символы (недопустимые в XML) выбрасываются. */
    fun esc(s: String): String = buildString(s.length) {
        for (c in s) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c.code < 0x20 && c != '\t' -> append(' ')
                else -> append(c)
            }
        }
    }

    /** Сколько номеров вопросов в одной строке таблицы ответов. */
    const val ANSWER_COLUMNS = 20

    /** Поле слева и справа в ячейке таблицы ответов в файле теста, twips (2 пт). */
    const val ANSWER_PAD = 40

    const val ANSWER_HEADER = "Вар."
}
