package com.testogen.app.export

import kotlin.math.floor

/**
 * Раскладка теста по страницам — та же логика, по которой Word раскладывает созданный файл:
 * в режиме «несколько на листе» варианты — ячейки таблицы, строка таблицы не разрывается,
 * каждая страница — отдельная таблица; ряд вариантов выше страницы режется по вопросам на куски,
 * на следующей странице вариант продолжается без нового заголовка;
 * в режиме «вариант на странице» вариант начинается с новой страницы и течёт по колонкам,
 * вопрос вместе со своими вариантами ответа не разрывается.
 * Ответы в том же файле — таблицы после теста (под линией отреза или с новой страницы), разрывы между
 * их кусками ставятся явно. Результат нужен и для предпросмотра, и для файла, и для автоматического выбора раскладки.
 */
class Paginator(private val measurer: TextMeasurer) {

    /** Блок на странице: заголовок варианта или вопрос, с координатами (пункты от верхнего левого угла листа). */
    sealed interface Placed {
        val x: Float
        val y: Float
        val width: Float
        val height: Float
    }

    data class PlacedTitle(
        val variant: Int,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
    ) : Placed

    /** [atTop] — вопрос в начале колонки или куска варианта: отступа перед ним нет. */
    data class PlacedQuestion(
        val question: DocQuestion,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val atTop: Boolean = false,
    ) : Placed

    /** Ячейка таблицы с вариантом (только для режима «несколько на листе») — для рисования линий отреза. */
    data class Cell(val x: Float, val y: Float, val width: Float, val height: Float)

    data class Page(val blocks: List<Placed>, val cells: List<Cell>, val answers: List<AnswerTable> = emptyList())

    /** Кусок варианта в ячейке таблицы; [continued] — продолжение варианта с прошлой страницы (без заголовка). */
    data class CellPart(val variant: Int, val questions: List<DocQuestion>, val continued: Boolean)

    /** Строка таблицы «несколько на листе»: по куску на колонку (null — пустая ячейка). */
    data class TableRow(val parts: List<CellPart?>, val height: Float)

    /**
     * Кусок таблицы ответов: колонки — номера вопросов [numbers], строки — варианты [rows].
     * [title] — строка «Ответы» сверху (только у первого куска), [cutLine] — над ней пунктир отреза.
     */
    data class AnswerTable(
        val x: Float,
        val y: Float,
        val firstWidth: Float,
        val cellWidth: Float,
        val lineHeight: Float,
        val numbers: List<Int>,
        val rows: List<DocVariant>,
        val title: Boolean,
        val cutLine: Boolean,
    ) {
        val width get() = firstWidth + cellWidth * numbers.size
        val rowCount get() = (if (title) 1 else 0) + 1 + rows.size
        /** Шаг строк: рамка над строкой входит в её высоту (так раскладывает LibreOffice). */
        val pitch get() = lineHeight + BORDER
        val height get() = rowCount * pitch + BORDER
        /** Верх текста в строке [k] (0 — первая строка куска). */
        fun rowTop(k: Int) = y + BORDER + k * pitch
    }

    enum class Break { NONE, COLUMN, PAGE }

    /** Что идёт в файле после теста: пустой абзац высотой [height] (с разрывом колонки/страницы или без) или таблица. */
    sealed interface AnswerItem {
        data class Spacer(val height: Float, val brk: Break) : AnswerItem
        data class Table(val table: AnswerTable) : AnswerItem
    }

    /** Полная раскладка: страницы для предпросмотра и то, что нужно файлу. */
    class Result(val pages: List<Page>, val compactRows: List<List<TableRow>>, val answerItems: List<AnswerItem>)

    // ---------- размеры блоков ----------

    fun titleHeight(l: DocLayout) = l.lineHeight + spacingAfterTitle(l)

    fun questionHeight(q: DocQuestion, l: DocLayout): Float {
        val textLines = measurer.lines("${q.number}. ${q.text}", l.textWidth, l.fontSize, bold = true)
        val optionLines = q.options.sumOf { measurer.lines(it, l.textWidth - l.optionIndent, l.fontSize, bold = false) }
        return spacingBeforeQuestion(l) + (textLines + optionLines) * l.lineHeight + spacingAfterQuestionText(l)
    }

    fun variantHeight(v: DocVariant, l: DocLayout): Float =
        titleHeight(l) + v.questions.sumOf { questionHeight(it, l).toDouble() }.toFloat()

    // ---------- раскладка ----------

    fun paginate(variants: List<DocVariant>, l: DocLayout): List<Page> = layout(variants, l).pages

    fun layout(variants: List<DocVariant>, l: DocLayout): Result {
        val rows = if (l.mode == LayoutMode.COMPACT) compactPages(variants, l) else emptyList()
        val (pages, end) = if (l.mode == LayoutMode.COMPACT) compact(rows, l) else perPage(variants, l)
        if (l.answers == AnswersPlacement.SEPARATE || variants.isEmpty()) return Result(pages, rows, emptyList())
        val flow = AnswerFlow(l, pages.size, end)
        flow.place(variants)
        val withAnswers = List(flow.pageCount) { i ->
            val page = pages.getOrNull(i) ?: Page(emptyList(), emptyList())
            page.copy(answers = flow.tables[i].orEmpty())
        }
        return Result(withAnswers, rows, flow.items)
    }

    /** Высота куска варианта: у продолжения нет заголовка и отступа перед первым вопросом. */
    private fun partHeight(p: CellPart, l: DocLayout): Float {
        val questions = p.questions.sumOf { questionHeight(it, l).toDouble() }.toFloat()
        return if (p.continued) questions - (if (p.questions.isEmpty()) 0f else spacingBeforeQuestion(l)) else titleHeight(l) + questions
    }

    /** Толщина рамки таблицы «несколько на листе»: линии отреза есть — над каждой строкой и под последней. */
    private fun border(l: DocLayout) = if (l.cutLines) BORDER else 0f

    private fun rowHeight(parts: List<CellPart?>, l: DocLayout): Float =
        parts.maxOf { p -> p?.let { partHeight(it, l) } ?: l.lineHeight } + 2 * l.cellPadding + border(l)

    /** Сколько места под таблицу на странице «несколько на листе»: за вычетом служебных абзацев до и после неё. */
    fun compactAvailable(l: DocLayout) = l.contentHeight - 2 * SEPARATOR

    /**
     * Строки таблиц «несколько на листе» по страницам. Ряд, который не помещается на остаток страницы,
     * переносится на следующую. Ряд выше целой страницы начинается с новой страницы, и каждый вариант
     * режется между вопросами на куски не выше страницы — так в файле ничего не наезжает друг на друга.
     */
    fun compactPages(variants: List<DocVariant>, l: DocLayout): List<List<TableRow>> {
        val avail = compactAvailable(l)
        val pages = mutableListOf<List<TableRow>>()
        var page = mutableListOf<TableRow>()
        // Нижняя рамка таблицы
        var used = border(l)
        fun newPage() {
            if (page.isEmpty()) return
            pages += page
            page = mutableListOf()
            used = border(l)
        }
        fun add(parts: List<CellPart?>) {
            val padded = List(l.columns) { parts.getOrNull(it) }
            val h = rowHeight(padded, l)
            page += TableRow(padded, h)
            used += h
        }
        for (group in variants.chunked(l.columns)) {
            val whole = group.map { CellPart(it.number, it.questions, continued = false) }
            if (rowHeight(whole, l) + border(l) <= avail + DocLayout.EPS) {
                if (used + rowHeight(whole, l) > avail + DocLayout.EPS) newPage()
                add(whole)
                continue
            }
            newPage()
            val pieces = group.map { split(it, avail - 2 * l.cellPadding - 2 * border(l), l) }
            for (k in 0 until pieces.maxOf { it.size }) {
                if (k > 0) newPage()
                add(pieces.map { it.getOrNull(k) })
            }
        }
        newPage()
        return pages
    }

    /** Вариант по кускам высотой не больше [capacity]; вопрос не разрывается, у продолжения нет заголовка. */
    private fun split(v: DocVariant, capacity: Float, l: DocLayout): List<CellPart> {
        val parts = mutableListOf<CellPart>()
        var current = mutableListOf<DocQuestion>()
        var h = titleHeight(l)
        for (q in v.questions) {
            val qh = questionHeight(q, l)
            if (h + qh > capacity + DocLayout.EPS && current.isNotEmpty()) {
                parts += CellPart(v.number, current, continued = parts.isNotEmpty())
                current = mutableListOf()
                h = -spacingBeforeQuestion(l)
            }
            current += q
            h += qh
        }
        parts += CellPart(v.number, current, continued = parts.isNotEmpty())
        return parts
    }

    /** Где закончился тест: страница, колонка, высота. */
    private data class Cursor(val page: Int, val column: Int, val y: Float)

    private fun compact(rows: List<List<TableRow>>, l: DocLayout): Pair<List<Page>, Cursor> {
        var endY = l.margin + SEPARATOR
        val pages = rows.map { pageRows ->
            val blocks = mutableListOf<Placed>()
            val cells = mutableListOf<Cell>()
            var y = l.margin + SEPARATOR
            for (row in pageRows) {
                row.parts.forEachIndexed { i, part ->
                    val x = l.margin + i * l.columnWidth
                    // Линия рамки — посередине своей толщины
                    cells += Cell(x, y + border(l) / 2, l.columnWidth, row.height)
                    if (part == null) return@forEachIndexed
                    var cy = y + border(l) + l.cellPadding
                    val tx = x + l.cellPadding
                    if (!part.continued) {
                        blocks += PlacedTitle(part.variant, tx, cy, l.textWidth, titleHeight(l))
                        cy += titleHeight(l)
                    }
                    part.questions.forEachIndexed { qi, q ->
                        val top = part.continued && qi == 0
                        val h = questionHeight(q, l) - if (top) spacingBeforeQuestion(l) else 0f
                        blocks += PlacedQuestion(q, tx, cy, l.textWidth, h, atTop = top)
                        cy += h
                    }
                }
                y += row.height
            }
            endY = y + border(l)
            Page(blocks, cells)
        }
        return pages to Cursor(pages.lastIndex.coerceAtLeast(0), 0, endY)
    }

    private fun perPage(variants: List<DocVariant>, l: DocLayout): Pair<List<Page>, Cursor> {
        val pages = mutableListOf<Page>()
        var end = Cursor(0, 0, l.margin)
        for (v in variants) {
            var blocks = mutableListOf<Placed>()
            var column = 0
            var y = l.margin
            fun x() = l.margin + column * (l.columnWidth + l.columnGap)
            fun nextColumn() {
                column++
                if (column >= l.columns) {
                    pages += Page(blocks, emptyList())
                    blocks = mutableListOf()
                    column = 0
                }
                y = l.margin
            }
            // Заголовок держится вместе с первым вопросом
            val first = v.questions.firstOrNull()?.let { questionHeight(it, l) } ?: 0f
            blocks += PlacedTitle(v.number, x(), y, l.columnWidth, titleHeight(l))
            y += titleHeight(l)
            v.questions.forEachIndexed { i, q ->
                var h = if (i == 0) first else questionHeight(q, l)
                // Отступ перед вопросом в начале колонки Word и LibreOffice не ставят; в середине колонки он входит в проверку «помещается ли»
                val noBefore = h - spacingBeforeQuestion(l)
                if (y + h > l.margin + l.contentHeight + DocLayout.EPS && !(i == 0 || y == l.margin)) nextColumn()
                val atTop = i > 0 && y == l.margin
                if (atTop) h = noBefore
                blocks += PlacedQuestion(q, x(), y, l.columnWidth, h, atTop)
                y += h
            }
            pages += Page(blocks, emptyList())
            end = Cursor(pages.lastIndex, column, y)
        }
        return pages to end
    }

    /**
     * Ответы после теста. Течёт как текст: в режиме «вариант на странице» — по колонкам, иначе — по страницам.
     * Каждый переход в новую колонку или на новую страницу — явный разрыв в служебном абзаце высотой
     * [SEPARATOR]; под самым низом колонки всегда остаётся место на такой абзац.
     */
    private inner class AnswerFlow(private val l: DocLayout, existingPages: Int, start: Cursor) {
        val items = mutableListOf<AnswerItem>()
        val tables = HashMap<Int, MutableList<AnswerTable>>()
        var pageCount = existingPages
            private set
        private var page = start.page
        private var column = start.column
        private var y = start.y
        private val columns = if (l.mode == LayoutMode.PER_PAGE) l.columns else 1
        private val width = if (l.mode == LayoutMode.PER_PAGE) l.columnWidth else l.contentWidth
        private val top = l.margin
        private val limit = l.margin + l.contentHeight - SEPARATOR
        private val rh = DocLayout.lineHeight(l.fontSize)

        private fun x() = l.margin + column * (l.columnWidth + l.columnGap)

        private fun next() {
            // Меньше места, чем на служебный абзац: он сам уйдёт в следующую колонку, разрыв не нужен
            val brk = when {
                y > limit + DocLayout.EPS -> Break.NONE
                column + 1 < columns -> Break.COLUMN
                else -> Break.PAGE
            }
            if (column + 1 < columns) {
                column++
            } else {
                column = 0
                page++
            }
            pageCount = maxOf(pageCount, page + 1)
            items += AnswerItem.Spacer(SEPARATOR, brk)
            y = top + SEPARATOR
        }

        private fun newPage() {
            column = 0
            page++
            pageCount = maxOf(pageCount, page + 1)
            items += AnswerItem.Spacer(SEPARATOR, Break.PAGE)
            y = top + SEPARATOR
        }

        fun place(variants: List<DocVariant>) {
            if (l.answers == AnswersPlacement.NEW_PAGE) {
                newPage()
            } else if (y + END_GAP <= limit + DocLayout.EPS) {
                items += AnswerItem.Spacer(END_GAP, Break.NONE)
                y += END_GAP
            } else {
                next()
            }
            val firstWidth = DocLayout.twips(l.fontSize * 3.2f)
            val maxQ = variants.maxOf { it.questions.size }.coerceAtLeast(1)
            val perRow = floor((width - firstWidth) / (l.fontSize * 2.1f)).toInt().coerceIn(1, MAX_ANSWER_COLUMNS).coerceAtMost(maxQ)
            val cellWidth = floor((width - firstWidth) / perRow * 20) / 20f
            var first = true
            (1..maxQ).chunked(perRow).forEachIndexed { ci, numbers ->
                if (ci > 0) {
                    val gap = rh / 2
                    if (y + gap + 3 * (rh + BORDER) + BORDER <= limit + DocLayout.EPS) {
                        items += AnswerItem.Spacer(gap, Break.NONE)
                        y += gap
                    } else {
                        next()
                    }
                }
                var r = 0
                while (r < variants.size) {
                    // Строка «Ответы» (у первого куска) и шапка с номерами, затем строки вариантов; каждая — с рамкой над ней
                    val head = ((if (first) 2 else 1)) * (rh + BORDER) + BORDER
                    if (y + head + rh + BORDER > limit + DocLayout.EPS) next()
                    val n = floor((limit + DocLayout.EPS - y - head) / (rh + BORDER)).toInt().coerceIn(1, variants.size - r)
                    val t = AnswerTable(
                        x(), y, firstWidth, cellWidth, rh, numbers, variants.subList(r, r + n),
                        title = first, cutLine = first && l.answers == AnswersPlacement.END,
                    )
                    items += AnswerItem.Table(t)
                    tables.getOrPut(page) { mutableListOf() } += t
                    y += t.height
                    r += n
                    first = false
                    if (r < variants.size) next()
                }
            }
        }
    }

    /** Сколько страниц займёт вариант. */
    fun pagesFor(v: DocVariant, l: DocLayout) = perPage(listOf(v), l).first.size

    // ---------- автоматический выбор ----------

    /**
     * Небольшие варианты — компактно, по несколько на листе (как в образце: альбомный лист, 2 колонки, 10 пт),
     * если вариант целиком помещается в половину альбомного листа. Иначе — каждый вариант с новой страницы:
     * подбирается самая удобная раскладка, при которой самый длинный вариант помещается на одну страницу;
     * шрифт не мельче 10 пт. Не помещается и так — самая плотная раскладка, вариант продолжается на следующей.
     */
    fun auto(variants: List<DocVariant>): DocLayout {
        // Проверяем по самым длинным вариантам — остальные короче
        val longest = variants.sortedByDescending { v -> v.questions.sumOf { q -> q.text.length + q.options.sumOf { it.length } } }.take(3)
        val compact = DocLayout(LayoutMode.COMPACT, landscape = true, columns = 2, fontSize = 10f)
        if (variants.size > 1 && longest.all { variantHeight(it, compact) + 2 * compact.cellPadding + 2 * BORDER <= compactAvailable(compact) }) {
            return compact
        }
        val candidates = listOf(
            DocLayout(LayoutMode.PER_PAGE, landscape = false, columns = 1, fontSize = 12f),
            DocLayout(LayoutMode.PER_PAGE, landscape = false, columns = 1, fontSize = 11f),
            DocLayout(LayoutMode.PER_PAGE, landscape = false, columns = 2, fontSize = 11f),
            DocLayout(LayoutMode.PER_PAGE, landscape = true, columns = 2, fontSize = 11f),
            DocLayout(LayoutMode.PER_PAGE, landscape = false, columns = 2, fontSize = 10f),
            DocLayout(LayoutMode.PER_PAGE, landscape = true, columns = 2, fontSize = 10f),
        )
        return candidates.firstOrNull { c -> longest.all { pagesFor(it, c) == 1 } }
            ?: DocLayout(LayoutMode.PER_PAGE, landscape = false, columns = 2, fontSize = 10f)
    }

    companion object {
        /** Высота служебного пустого абзаца: перед каждой таблицей «несколько на листе», после последней, в разрывах ответов. */
        const val SEPARATOR = 1f

        /** Толщина рамки таблицы (w:sz=6 — 6/8 пт): LibreOffice прибавляет её к высоте строки. */
        const val BORDER = 0.75f

        /** Отступ от теста до линии отреза с ответами. */
        const val END_GAP = 24f

        /** Больше номеров вопросов в одной строке таблицы ответов не ставится. */
        const val MAX_ANSWER_COLUMNS = 20

        fun titleText(variant: Int) = "Вариант $variant"

        // Отступа после заголовка нет: Word и LibreOffice по-разному складывают «после» и «перед» соседних абзацев,
        // поэтому промежуток между заголовком и первым вопросом задаёт только отступ перед вопросом
        fun spacingAfterTitle(l: DocLayout) = 0f
        fun spacingBeforeQuestion(l: DocLayout) = l.fontSize * 0.45f
        fun spacingAfterQuestionText(l: DocLayout) = 0f
    }
}
