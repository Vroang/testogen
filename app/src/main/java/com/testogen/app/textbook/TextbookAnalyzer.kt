package com.testogen.app.textbook

import kotlin.math.abs

/** Строка текста, как её отдал извлекатель (PDF, DOCX, TXT). Координаты — от верхнего левого угла страницы. */
data class RawLine(
    val text: String,
    val size: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
)

/** Страница исходника. У DOCX и TXT «страница» одна, [hasLayout] = false. */
data class RawPage(val index: Int, val width: Float, val height: Float, val lines: List<RawLine>, val hasLayout: Boolean = true)

/** Вид блока текста внутри параграфа. */
enum class BlockKind(val code: Int) {
    TEXT(0), HEADING(1), SIDE(2), QUESTIONS(3), EXTRA(4), KEYWORDS(5);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: TEXT
    }
}

data class ParsedBlock(val kind: BlockKind, val text: String, val pdfPage: Int, val bookPage: Int?)

/** PARAGRAPH — параграф учебника; OTHER — вступление, задания к главе, словарь и т. п. */
enum class SectionKind { PARAGRAPH, OTHER }

data class ParsedSection(
    val kind: SectionKind,
    val numFrom: Int?,
    val numTo: Int?,
    var title: String,
    val chapterIndex: Int?,
    val blocks: MutableList<ParsedBlock> = mutableListOf(),
    val keywords: MutableList<String> = mutableListOf(),
) {
    /** «§ 6–7», «§ 1» или пусто. */
    val label: String
        get() = when {
            numFrom == null -> ""
            numTo != null && numTo != numFrom -> "§ $numFrom–$numTo"
            else -> "§ $numFrom"
        }
    val pdfPageStart get() = blocks.firstOrNull()?.pdfPage
    val pdfPageEnd get() = blocks.lastOrNull()?.pdfPage
    val bookPageStart get() = blocks.firstNotNullOfOrNull { it.bookPage }
    val bookPageEnd get() = blocks.lastOrNull { it.bookPage != null }?.bookPage
}

data class ParsedChapter(val label: String, var title: String)

data class TocEntry(val numFrom: Int?, val numTo: Int?, val chapterLabel: String?, val title: String, val bookPage: Int?)

data class GlossaryEntry(val term: String, val definition: String, val pdfPage: Int)

data class BookStructure(
    val title: String?,
    val grade: Int?,
    val chapters: List<ParsedChapter>,
    val sections: List<ParsedSection>,
    val glossary: List<GlossaryEntry>,
    val toc: List<TocEntry>,
    /** Номер страницы книги для каждой страницы PDF (null — не напечатан и не выводится). */
    val bookPages: List<Int?>,
    /** Расхождения с оглавлением и прочие замечания — показываются маме на экране проверки структуры. */
    val warnings: List<String>,
) {
    val paragraphs get() = sections.filter { it.kind == SectionKind.PARAGRAPH }
}

/** В PDF нет текстового слоя (скан). */
class ScannedPdfException : Exception("В этом PDF нет текста — похоже, это отсканированные картинки")

/**
 * Разбор учебника на главы и параграфы по оформлению: размер шрифта, жирность, положение на странице.
 * Колонтитулы и номера страниц выкидываются (номер страницы книги запоминается), переносы склеиваются,
 * врезки и подписи (мелкий шрифт), блоки заданий, ключевые понятия и словарь узнаются отдельно.
 * Найденная структура сверяется с оглавлением. Без Android — проверяется юнит-тестом на настоящем учебнике.
 */
object TextbookAnalyzer {

    private val SECTION_RE = Regex("^§\\s*(\\d{1,3})\\s*(?:[–—‒-]\\s*(\\d{1,3}))?\\s*\\.?\\s*(.*)$")
    private val CHAPTER_RE = Regex("(?iu)^(глава|раздел|часть|тема)\\s+([IVXLCDM]+|\\d{1,2})\\b\\.?\\s*(.*)$")
    private val PAGE_NUMBER_RE = Regex("^\\d{1,4}$")
    private val BULLET_RE = Regex("^\\s*[x•·–—▪◼■-]\\s+")
    private val TOC_TITLES = setOf("оглавление", "содержание")
    private val GLOSSARY_RE = Regex("(?iu)^(словарь|глоссарий)(\\s.*)?$")

    private val QUESTION_HEADINGS = listOf(
        "проверяем себя", "проверьте себя", "проверь себя", "вопросы и задания", "вопросы для обсуждения",
        "исследуем", "рассуждаем", "размышляем", "практикум", "задания", "вопросы", "подумайте", "обсудим",
    )
    private val EXTRA_HEADINGS = listOf(
        "дополнительные материалы", "учителю и ученику", "это интересно", "литература", "рекомендуем",
        "интернет-ресурсы", "фильмы", "книги",
    )
    private val KEYWORD_HEADINGS = listOf("ключевые понятия", "основные понятия", "ключевые слова", "термины", "запомните")

    fun analyze(pages: List<RawPage>): BookStructure {
        val layout = pages.any { it.hasLayout }
        checkHasText(pages)
        val body = bodySize(pages)
        val bookPages = detectBookPages(pages, body)
        val lines = contentLines(pages, body)
        return Builder(lines, body, layout, bookPages).build(pages)
    }

    private fun checkHasText(pages: List<RawPage>) {
        if (pages.isEmpty()) throw ScannedPdfException()
        val chars = pages.map { p -> p.lines.sumOf { it.text.length } }
        val empty = chars.count { it < 40 }
        if (chars.sum() < 500 || (pages.size >= 4 && empty > pages.size * 0.6)) throw ScannedPdfException()
    }

    /** Основной размер шрифта — тот, которым набрано больше всего букв. */
    private fun bodySize(pages: List<RawPage>): Float {
        val counts = HashMap<Float, Int>()
        for (p in pages) for (l in p.lines) {
            val key = (l.size * 2).toInt() / 2f
            counts[key] = (counts[key] ?: 0) + l.text.length
        }
        return counts.maxByOrNull { it.value }?.key ?: 10f
    }

    private fun isMargin(page: RawPage, line: RawLine, body: Float): Boolean =
        page.hasLayout && line.size < body * 1.6f && (line.y < page.height * 0.07f || line.y > page.height * 0.95f)

    /** Номера страниц книги: цифры в колонтитуле; пропуски достраиваются по самому частому сдвигу. */
    private fun detectBookPages(pages: List<RawPage>, body: Float): List<Int?> {
        val candidates = pages.map { p ->
            p.lines.firstOrNull { isMargin(p, it, body) && PAGE_NUMBER_RE.matches(it.text.trim()) }?.text?.trim()?.toInt()
        }
        val offset = candidates.withIndex()
            .mapNotNull { (i, n) -> n?.let { it - i } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: return pages.map { null }
        val first = candidates.indexOfFirst { it != null }
        val last = candidates.indexOfLast { it != null }
        return pages.indices.map { i ->
            val n = i + offset
            if (n >= 1 && i >= first - 2 && i <= last + 2) n else null
        }
    }

    /** Строка без колонтитулов, с номером страницы PDF. */
    data class Line(val raw: RawLine, val page: Int) {
        val text get() = raw.text
    }

    private fun contentLines(pages: List<RawPage>, body: Float): List<Line> =
        pages.flatMap { p ->
            val merged = mutableListOf<RawLine>()
            for (l in p.lines) {
                if (isMargin(p, l, body)) continue
                val text = normalizeSpaces(l.text)
                if (text.isEmpty()) continue
                val prev = merged.lastOrNull()
                // Части одной печатной строки (например, жирный термин и продолжение) — одна строка
                if (p.hasLayout && prev != null && abs(prev.y - l.y) < l.size * 0.5f && l.x > prev.x && abs(prev.size - l.size) < 1.5f) {
                    merged[merged.lastIndex] = prev.copy(text = prev.text + " " + text, bold = prev.bold && l.bold)
                } else {
                    merged += l.copy(text = text)
                }
            }
            merged.map { Line(it, p.index) }
        }

    fun normalizeSpaces(s: String): String =
        s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').replace(Regex("[ \\t]+"), " ").trim()

    private fun startsWithAny(text: String, list: List<String>): Boolean {
        val t = text.lowercase().replace('ё', 'е').trimEnd(':', '.', ' ')
        return list.any { t == it || t.startsWith("$it ") }
    }

    // ---------------------------------------------------------------------------------------------

    private class Builder(
        val lines: List<Line>,
        val body: Float,
        val layout: Boolean,
        val bookPages: List<Int?>,
    ) {
        val chapters = mutableListOf<ParsedChapter>()
        val sections = mutableListOf<ParsedSection>()
        val glossary = mutableListOf<GlossaryEntry>()
        val warnings = mutableListOf<String>()
        val tocLines = mutableListOf<String>()

        fun big(l: Line) = layout && l.raw.size >= body * 1.6f
        fun headingLike(l: Line): Boolean {
            if (!layout) return false
            val t = l.text
            val upper = t.count { it.isUpperCase() }.toFloat() / t.count { it.isLetter() }.coerceAtLeast(1)
            return (l.raw.size >= body * 1.05f && l.raw.bold && t.length < 120) ||
                // Подзаголовок прописными: размер у разных извлекателей «плавает» (10 или 10,5 при основном 11)
                (l.raw.bold && l.raw.size >= body * 0.85f && upper > 0.7f && t.length < 90)
        }
        fun sectionMarker(l: Line): MatchResult? {
            val m = SECTION_RE.find(l.text) ?: return null
            if (layout) {
                if (l.raw.size < body * 1.15f) return null
            } else if (l.text.length > 140 || l.text.contains(Regex("\\.{3,}|(\\. ){3,}"))) {
                return null
            }
            return m
        }
        fun chapterMarker(l: Line): MatchResult? {
            val m = CHAPTER_RE.find(l.text) ?: return null
            if (layout && l.raw.size < body * 1.3f) return null
            if (!layout && (l.text.length > 120 || l.text.contains(Regex("\\.{3,}|(\\. ){3,}")))) return null
            return m
        }

        fun build(pages: List<RawPage>): BookStructure {
            val starts = findSectionStarts()
            val skipLine = consumedMarkers
            var chapterIndex: Int? = null
            var current = ParsedSection(SectionKind.OTHER, null, null, "Начало книги", null)
            sections += current
            var mode = BlockKind.TEXT
            var modeSize = body
            var tocMode = false
            var glossaryMode = false
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                val start = starts[i]
                if (start != null) {
                    // Начало нового раздела: параграф, глава, задания к главе, словарь…
                    tocMode = false
                    glossaryMode = false
                    mode = BlockKind.TEXT
                    when (start) {
                        is Start.Chapter -> {
                            chapters += ParsedChapter(start.label, start.title)
                            chapterIndex = chapters.lastIndex
                            current = ParsedSection(SectionKind.OTHER, null, null, "Вступление к главе", chapterIndex)
                            sections += current
                        }
                        is Start.Paragraph -> {
                            current = ParsedSection(SectionKind.PARAGRAPH, start.from, start.to, start.title, chapterIndex)
                            sections += current
                        }
                        is Start.Other -> {
                            val lower = start.title.lowercase()
                            tocMode = lower in TOC_TITLES
                            glossaryMode = GLOSSARY_RE.matches(start.title)
                            if (!tocMode && !lower.startsWith("задания к ")) chapterIndex = if (glossaryMode || lower == "заключение") null else chapterIndex
                            current = ParsedSection(SectionKind.OTHER, null, null, start.title, chapterIndex)
                            sections += current
                        }
                    }
                    // skip == 0: раздел начинается с обычной строки — она же первая строка текста
                    if (start.skip > 0) {
                        i += start.skip
                        continue
                    }
                }
                if (skipLine[i]) {
                    i++
                    continue
                }
                if (tocMode) {
                    tocLines += line.text
                    i++
                    continue
                }
                if (glossaryMode && line.raw.bold && line.text.contains('—')) {
                    glossary += GlossaryEntry(line.text.substringBefore('—').trim(), line.text.substringAfter('—').trim(), line.page)
                } else if (glossaryMode && glossary.isNotEmpty() && glossary.last().pdfPage >= line.page - 1 && !headingLike(line)) {
                    val g = glossary.removeAt(glossary.lastIndex)
                    glossary += g.copy(definition = join(g.definition, line.text))
                }

                // Особые заголовки меняют вид следующих блоков
                val kind: BlockKind
                if (headingLike(line) || (!layout && line.text.length < 60 && line.text == line.text.uppercase() && line.text.any { it.isLetter() })) {
                    mode = when {
                        startsWithAny(line.text, QUESTION_HEADINGS) -> BlockKind.QUESTIONS
                        startsWithAny(line.text, EXTRA_HEADINGS) -> BlockKind.EXTRA
                        startsWithAny(line.text, KEYWORD_HEADINGS) -> BlockKind.KEYWORDS
                        else -> BlockKind.TEXT
                    }
                    modeSize = lines.getOrNull(i + 1)?.raw?.size ?: body
                    kind = BlockKind.HEADING
                } else {
                    // Обычный текст после мелкого блока заданий — снова основной текст параграфа
                    if (mode != BlockKind.TEXT && layout && modeSize < body * 0.93f && line.raw.size >= body * 0.97f && !line.raw.bold) {
                        mode = BlockKind.TEXT
                    }
                    kind = when {
                        mode == BlockKind.KEYWORDS -> BlockKind.KEYWORDS
                        mode != BlockKind.TEXT -> mode
                        layout && line.raw.size < body * 0.93f -> BlockKind.SIDE
                        else -> BlockKind.TEXT
                    }
                }
                if (kind == BlockKind.KEYWORDS) {
                    val bullet = BULLET_RE.find(line.text)
                    val word = line.text.substring(bullet?.range?.last?.plus(1) ?: 0).trim()
                    if (word.isNotEmpty()) {
                        if (bullet == null && current.keywords.isNotEmpty() && layout) {
                            current.keywords[current.keywords.lastIndex] = current.keywords.last() + " " + word
                        } else {
                            current.keywords += word
                        }
                    }
                    i++
                    continue
                }
                append(current, kind, line, lines.getOrNull(i - 1))
                i++
            }
            val toc = parseToc(tocLines)
            applyToc(toc)
            val cleaned = sections.filter { s -> s.blocks.isNotEmpty() || s.kind == SectionKind.PARAGRAPH }
            return BookStructure(
                title = guessTitle(pages),
                grade = guessGrade(pages),
                chapters = chapters,
                sections = cleaned,
                glossary = glossary,
                toc = toc,
                bookPages = bookPages,
                warnings = warnings,
            )
        }

        /** Добавляет строку в блок: новый блок при смене вида, страницы или красной строке; склеивает переносы. */
        fun append(section: ParsedSection, kind: BlockKind, line: Line, prev: Line?) {
            // Абзац, прерванный врезкой или концом страницы, продолжается со строчной буквы — доклеиваем к нему
            if (kind == BlockKind.TEXT && line.text.first().isLowerCase()) {
                val idx = section.blocks.indexOfLast { it.kind == BlockKind.TEXT }
                if (idx >= 0 && (idx + 1 until section.blocks.size).all { section.blocks[it].kind == BlockKind.SIDE } &&
                    !endsSentence(section.blocks[idx].text) && (idx < section.blocks.lastIndex || prev?.page != line.page)
                ) {
                    section.blocks[idx] = section.blocks[idx].copy(text = join(section.blocks[idx].text, line.text))
                    return
                }
            }
            val last = section.blocks.lastOrNull()
            // Заголовок в две строки — один блок
            if (kind == BlockKind.HEADING && last?.kind == BlockKind.HEADING && prev != null && prev.page == line.page &&
                abs(prev.raw.size - line.raw.size) < 0.6f && last.text.length < 100
            ) {
                section.blocks[section.blocks.lastIndex] = last.copy(text = join(last.text, line.text))
                return
            }
            val newParagraph = prev == null || prev.page != line.page ||
                (layout && line.raw.x - prev.raw.x >= 6f && abs(line.raw.size - prev.raw.size) < 0.6f) ||
                (layout && line.raw.size - prev.raw.size >= 0.6f) ||
                !layout || kind == BlockKind.HEADING || kind == BlockKind.KEYWORDS
            if (last == null || last.kind != kind || last.pdfPage != line.page || newParagraph) {
                section.blocks += ParsedBlock(kind, line.text, line.page, bookPages.getOrNull(line.page))
            } else {
                section.blocks[section.blocks.lastIndex] = last.copy(text = join(last.text, line.text))
            }
        }

        // ------------------------------- начала разделов --------------------------------

        sealed class Start(val skip: Int) {
            class Chapter(val label: String, val title: String, skip: Int) : Start(skip)
            class Paragraph(val from: Int, val to: Int?, val title: String, skip: Int) : Start(skip)
            class Other(val title: String, skip: Int) : Start(skip)
        }

        /**
         * Индекс строки → начало раздела. Значок «§ N» в PDF часто стоит в конце страницы, а заголовок
         * параграфа — в начале: тогда параграф начинается с заголовка, а сам значок пропускается.
         */
        /** Значки «§ N», уже учтённые как начало параграфа: в текст не попадают. */
        val consumedMarkers = BooleanArray(lines.size)

        fun findSectionStarts(): Array<Start?> {
            val result = arrayOfNulls<Start>(lines.size)
            val consumed = BooleanArray(lines.size)
            var seenStructure = false
            var i = 0
            while (i < lines.size) {
                val l = lines[i]
                val section = sectionMarker(l)
                val chapter = if (section == null) chapterMarker(l) else null
                if (chapter != null) {
                    var title = chapter.groupValues[3].trim()
                    var skip = 1
                    if (title.isEmpty()) {
                        val next = lines.getOrNull(i + 1)
                        if (next != null && (big(next) || headingLike(next) || !layout) && sectionMarker(next) == null) {
                            title = next.text
                            skip = 2
                        }
                    }
                    // Титульная страница главы (эпиграф, биография) начинается раньше самого заголовка
                    var startIndex = i
                    if (layout) {
                        var j = i
                        while (j > 0 && lines[j - 1].page == l.page && result[j - 1] == null && !consumed[j - 1]) j--
                        if (j < i) {
                            startIndex = j
                            for (k in i until i + skip) consumedMarkers[k] = true
                        }
                    }
                    result[startIndex] = Start.Chapter("Глава ${chapter.groupValues[2]}", prettyTitle(title), if (startIndex == i) skip else 0)
                    seenStructure = true
                    i += skip
                    continue
                }
                if (section != null && !consumed[i]) {
                    val from = section.groupValues[1].toInt()
                    val to = section.groupValues[2].takeIf { it.isNotEmpty() }?.toInt()
                    var title = section.groupValues[3].trim()
                    var startIndex = i
                    var skip = 1
                    if (layout && big(l)) {
                        // Крупный заголовок на той же странице выше значка — начало параграфа
                        var j = i - 1
                        var titleStart = -1
                        while (j >= 0 && lines[j].page == l.page) {
                            if (big(lines[j]) && sectionMarker(lines[j]) == null && chapterMarker(lines[j]) == null && result[j] == null) titleStart = j
                            j--
                        }
                        if (titleStart >= 0) {
                            var end = titleStart
                            val size = lines[titleStart].raw.size
                            while (end + 1 < i && big(lines[end + 1]) && abs(lines[end + 1].raw.size - size) < 0.6f) end++
                            if (title.isEmpty()) title = (titleStart..end).joinToString(" ") { lines[it].text }
                            for (k in titleStart..end) consumed[k] = true
                            startIndex = titleStart
                            skip = end - titleStart + 1
                            consumed[i] = true // сам значок
                            consumedMarkers[i] = true
                        }
                    }
                    if (title.isEmpty()) {
                        val next = lines.getOrNull(i + 1)
                        if (next != null && (big(next) || headingLike(next) || !layout) && next.text.length < 120 && sectionMarker(next) == null) {
                            title = next.text
                            if (startIndex == i) skip = 2
                        }
                    }
                    result[startIndex] = Start.Paragraph(from, to, prettyTitle(title), skip)
                    seenStructure = true
                    i++
                    continue
                }
                if (consumed[i]) {
                    i++
                    continue
                }
                // Прочие крупные заголовки после начала основной части: задания к главе, заключение, словарь, оглавление
                val lower = l.text.lowercase().trim()
                val isOtherBig = (big(l) && seenStructure && result[i] == null) ||
                    (!layout && (lower in TOC_TITLES || GLOSSARY_RE.matches(l.text) || lower == "заключение"))
                if (isOtherBig && !pageHasSectionMarker(l.page, i)) {
                    var end = i
                    while (end + 1 < lines.size && big(lines[end + 1]) && abs(lines[end + 1].raw.size - l.raw.size) < 0.6f &&
                        lines[end + 1].page == l.page && sectionMarker(lines[end + 1]) == null
                    ) end++
                    val title = (i..end).joinToString(" ") { lines[it].text }
                    result[i] = Start.Other(prettyTitle(title), end - i + 1)
                    i = end + 1
                    continue
                }
                if (!layout && lower in TOC_TITLES) {
                    result[i] = Start.Other("Оглавление", 1)
                }
                i++
            }
            return result
        }

        fun pageHasSectionMarker(page: Int, from: Int): Boolean {
            var j = from
            while (j < lines.size && lines[j].page == page) {
                if (sectionMarker(lines[j]) != null) return true
                j++
            }
            return false
        }

        // ------------------------------- оглавление --------------------------------

        fun applyToc(toc: List<TocEntry>) {
            if (toc.isEmpty()) return
            val paragraphs = sections.filter { it.kind == SectionKind.PARAGRAPH }
            for (entry in toc) {
                if (entry.numFrom != null) {
                    val s = paragraphs.firstOrNull { it.numFrom == entry.numFrom }
                    if (s == null) {
                        warnings += "В оглавлении есть § ${entry.numFrom}${entry.numTo?.let { "–$it" } ?: ""} «${entry.title}», а в тексте он не найден"
                        continue
                    }
                    if (entry.title.isNotBlank()) s.title = entry.title
                    val start = s.bookPageStart
                    if (entry.bookPage != null && start != null && abs(start - entry.bookPage) > 1) {
                        warnings += "${s.label}: в оглавлении стр. ${entry.bookPage}, найдено на стр. $start"
                    }
                } else if (entry.chapterLabel != null) {
                    chapters.firstOrNull { it.label.equals(entry.chapterLabel, ignoreCase = true) }?.let { it.title = entry.title }
                }
            }
            for (s in paragraphs) {
                if (toc.none { it.numFrom == s.numFrom }) warnings += "${s.label} «${s.title}» не найден в оглавлении"
            }
        }

        fun endsSentence(t: String) = t.trimEnd().lastOrNull()?.let { it in ".!?:;»\"…" } ?: true

        fun join(a: String, b: String): String {
            if (a.isEmpty()) return b
            val last = a.last()
            if ((last == '-' || last == '­') && a.length > 1 && a[a.length - 2].isLetter() && b.firstOrNull()?.isLowerCase() == true) {
                return a.dropLast(1) + b
            }
            return "$a $b"
        }
    }

    // ------------------------------- разбор оглавления --------------------------------

    private val TOC_ENTRY_RE = Regex(
        "(§\\s*\\d{1,3}(?:\\s*[–—‒-]\\s*\\d{1,3})?\\.?|(?iu:глава)\\s+[IVXLCDM\\d]+\\.?)?\\s*([^§]+?)\\s*(?:\\.\\s*){2,}\\s*(\\d{1,4})",
    )

    fun parseToc(lines: List<String>): List<TocEntry> {
        if (lines.isEmpty()) return emptyList()
        val joined = lines.joinToString(" ") { normalizeSpaces(it) }
        // Записи без точек-заполнителей, где номер стоит отдельно: «Человек 6» — точки добавляются перед числом
        return TOC_ENTRY_RE.findAll(joined).map { m ->
            val head = m.groupValues[1]
            val title = prettyTitle(m.groupValues[2].trim().trimEnd('.', ' '))
            val page = m.groupValues[3].toIntOrNull()
            val sec = SECTION_RE.find(head)
            when {
                sec != null -> TocEntry(sec.groupValues[1].toInt(), sec.groupValues[2].takeIf { it.isNotEmpty() }?.toInt(), null, title, page)
                head.isNotBlank() -> TocEntry(null, null, "Глава " + head.trim().split(Regex("\\s+"))[1].trimEnd('.'), title, page)
                else -> TocEntry(null, null, null, title, page)
            }
        }.toList()
    }

    /** «ЧЕЛОВЕК И ОБЩЕСТВО» → «Человек и общество»; обычные заголовки не трогаются. */
    fun prettyTitle(t: String): String {
        val s = normalizeSpaces(t).trimEnd('.', ' ')
        val letters = s.filter { it.isLetter() }
        if (letters.length >= 4 && letters.all { it.isUpperCase() }) {
            return s.lowercase().replaceFirstChar { it.uppercaseChar() }
                .replace(Regex("(?<=\\s)[ivxlcdm]+(?=\\s|$|\\.)")) { it.value.uppercase() }
        }
        return s
    }

    private fun guessGrade(pages: List<RawPage>): Int? {
        val text = pages.take(4).joinToString(" ") { p -> p.lines.joinToString(" ") { it.text } }
        val m = Regex("(\\d{1,2})\\s*[-‐]?\\s*(?:й\\s*)?к\\s*л\\s*а\\s*с\\s*с").find(text) ?: Regex("к\\s*л\\s*а\\s*с\\s*с\\s*(\\d{1,2})").find(text)
        return m?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..11 }
    }

    /**
     * Название с титула: сначала выходные данные («Обществознание : 9-й класс : учебник / …»),
     * иначе самая крупная надпись первых страниц («ОБЩЕСТВОЗНАНИЕ») и класс, если он найден.
     */
    private fun guessTitle(pages: List<RawPage>): String? {
        val lines = pages.take(8).flatMap { it.lines }
        for (line in lines) {
            val m = IMPRINT_RE.find(normalizeSpaces(line.text)) ?: continue
            return "${prettyTitle(m.groupValues[1].trim())}. ${m.groupValues[2]} класс"
        }
        val big = pages.take(3).flatMap { it.lines }
            .filter { l -> l.text.count { it.isLetter() } >= 5 }
            .maxByOrNull { it.size }
            ?.text?.let { prettyTitle(it) } ?: return null
        val grade = guessGrade(pages)
        return if (grade != null && !big.contains("класс", ignoreCase = true)) "$big. $grade класс" else big
    }

    /** Выходные данные учебника: «Название : 9-й класс : учебник» или «Название. 9 класс : учебник». */
    private val IMPRINT_RE = Regex("""^([А-ЯЁ][^:/\d]{2,80}?)\s*[.:]\s*(\d{1,2})(?:\s*[-‐–]\s*й)?\s+класс\s*:.*учебник""", RegexOption.IGNORE_CASE)
}
