package com.testogen.app.export

/**
 * Раскладка теста по страницам — та же логика, по которой Word раскладывает созданный файл:
 * в режиме «несколько на листе» варианты — ячейки таблицы, строка таблицы не разрывается,
 * каждая страница — отдельная таблица; ряд вариантов выше страницы режется по вопросам на куски
 * («Вариант N (продолжение)» на следующей странице);
 * в режиме «вариант на странице» вариант начинается с новой страницы и течёт по колонкам,
 * вопрос вместе со своими вариантами ответа не разрывается.
 * Результат нужен и для предпросмотра, и для автоматического выбора раскладки.
 */
class Paginator(private val measurer: TextMeasurer) {

    /** Блок на странице: заголовок варианта или вопрос, с координатами (пункты от верхнего левого угла листа). */
    sealed interface Placed {
        val x: Float
        val y: Float
        val width: Float
        val height: Float
    }

    /** [continued] — заголовок куска, продолжающего вариант с прошлой страницы. */
    data class PlacedTitle(
        val variant: Int,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val continued: Boolean = false,
    ) : Placed
    /** [atTop] — вопрос в начале колонки после автоматического перехода: отступ перед ним Word не ставит. */
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

    data class Page(val blocks: List<Placed>, val cells: List<Cell>)

    /** Кусок варианта в ячейке таблицы; [continued] — продолжение варианта с прошлой страницы. */
    data class CellPart(val variant: Int, val questions: List<DocQuestion>, val continued: Boolean)

    /** Строка таблицы «несколько на листе»: по куску на колонку (null — пустая ячейка). */
    data class TableRow(val parts: List<CellPart?>, val height: Float)

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

    fun paginate(variants: List<DocVariant>, l: DocLayout): List<Page> =
        if (l.mode == LayoutMode.COMPACT) compact(variants, l) else perPage(variants, l)

    private fun partHeight(p: CellPart, l: DocLayout): Float =
        titleHeight(l) + p.questions.sumOf { questionHeight(it, l).toDouble() }.toFloat()

    private fun rowHeight(parts: List<CellPart?>, l: DocLayout): Float =
        parts.maxOf { p -> p?.let { partHeight(it, l) } ?: l.lineHeight } + 2 * l.cellPadding

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
        var used = 0f
        fun newPage() {
            if (page.isEmpty()) return
            pages += page
            page = mutableListOf()
            used = 0f
        }
        fun add(parts: List<CellPart?>) {
            val padded = List(l.columns) { parts.getOrNull(it) }
            val h = rowHeight(padded, l)
            page += TableRow(padded, h)
            used += h
        }
        for (group in variants.chunked(l.columns)) {
            val whole = group.map { CellPart(it.number, it.questions, continued = false) }
            if (rowHeight(whole, l) <= avail + DocLayout.EPS) {
                if (used + rowHeight(whole, l) > avail + DocLayout.EPS) newPage()
                add(whole)
                continue
            }
            newPage()
            val pieces = group.map { split(it, avail - 2 * l.cellPadding, l) }
            for (k in 0 until pieces.maxOf { it.size }) {
                if (k > 0) newPage()
                add(pieces.map { it.getOrNull(k) })
            }
        }
        newPage()
        return pages
    }

    /** Вариант по кускам высотой не больше [capacity]; вопрос не разрывается. */
    private fun split(v: DocVariant, capacity: Float, l: DocLayout): List<CellPart> {
        val parts = mutableListOf<CellPart>()
        var current = mutableListOf<DocQuestion>()
        var h = titleHeight(l)
        for (q in v.questions) {
            val qh = questionHeight(q, l)
            if (h + qh > capacity + DocLayout.EPS && current.isNotEmpty()) {
                parts += CellPart(v.number, current, continued = parts.isNotEmpty())
                current = mutableListOf()
                h = titleHeight(l)
            }
            current += q
            h += qh
        }
        parts += CellPart(v.number, current, continued = parts.isNotEmpty())
        return parts
    }

    private fun compact(variants: List<DocVariant>, l: DocLayout): List<Page> = compactPages(variants, l).map { rows ->
        val blocks = mutableListOf<Placed>()
        val cells = mutableListOf<Cell>()
        var y = l.margin + SEPARATOR
        for (row in rows) {
            row.parts.forEachIndexed { i, part ->
                val x = l.margin + i * l.columnWidth
                cells += Cell(x, y, l.columnWidth, row.height)
                if (part == null) return@forEachIndexed
                var cy = y + l.cellPadding
                val tx = x + l.cellPadding
                blocks += PlacedTitle(part.variant, tx, cy, l.textWidth, titleHeight(l), part.continued)
                cy += titleHeight(l)
                for (q in part.questions) {
                    val h = questionHeight(q, l)
                    blocks += PlacedQuestion(q, tx, cy, l.textWidth, h)
                    cy += h
                }
            }
            y += row.height
        }
        Page(blocks, cells)
    }

    private fun perPage(variants: List<DocVariant>, l: DocLayout): List<Page> {
        val pages = mutableListOf<Page>()
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
        }
        return pages
    }

    /** Сколько страниц займёт вариант. */
    fun pagesFor(v: DocVariant, l: DocLayout) = perPage(listOf(v), l).size

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
        if (variants.size > 1 && longest.all { variantHeight(it, compact) + 2 * compact.cellPadding <= compactAvailable(compact) }) {
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
        /** Высота служебного пустого абзаца перед каждой таблицей «несколько на листе» и после последней. */
        const val SEPARATOR = 1f

        fun titleText(variant: Int, continued: Boolean) = if (continued) "Вариант $variant (продолжение)" else "Вариант $variant"

        // Отступа после заголовка нет: Word и LibreOffice по-разному складывают «после» и «перед» соседних абзацев,
        // поэтому промежуток между заголовком и первым вопросом задаёт только отступ перед вопросом
        fun spacingAfterTitle(l: DocLayout) = 0f
        fun spacingBeforeQuestion(l: DocLayout) = l.fontSize * 0.45f
        fun spacingAfterQuestionText(l: DocLayout) = 0f
    }
}
