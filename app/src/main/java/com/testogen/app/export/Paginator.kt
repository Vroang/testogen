package com.testogen.app.export

/**
 * Раскладка теста по страницам — та же логика, по которой Word раскладывает созданный файл:
 * в режиме «несколько на листе» варианты — ячейки таблицы, строка таблицы не разрывается;
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

    data class PlacedTitle(val variant: Int, override val x: Float, override val y: Float, override val width: Float, override val height: Float) : Placed
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

    private fun compact(variants: List<DocVariant>, l: DocLayout): List<Page> {
        val pages = mutableListOf<Page>()
        var blocks = mutableListOf<Placed>()
        var cells = mutableListOf<Cell>()
        var y = l.margin
        for (row in variants.chunked(l.columns)) {
            val rowHeight = row.maxOf { variantHeight(it, l) } + 2 * l.cellPadding
            if (y + rowHeight > l.margin + l.contentHeight + 0.5f && blocks.isNotEmpty()) {
                pages += Page(blocks, cells)
                blocks = mutableListOf()
                cells = mutableListOf()
                y = l.margin
            }
            row.forEachIndexed { i, v ->
                val x = l.margin + i * l.columnWidth
                cells += Cell(x, y, l.columnWidth, rowHeight)
                var cy = y + l.cellPadding
                val tx = x + l.cellPadding
                blocks += PlacedTitle(v.number, tx, cy, l.textWidth, titleHeight(l))
                cy += titleHeight(l)
                for (q in v.questions) {
                    val h = questionHeight(q, l)
                    blocks += PlacedQuestion(q, tx, cy, l.textWidth, h)
                    cy += h
                }
            }
            y += rowHeight
        }
        if (blocks.isNotEmpty()) pages += Page(blocks, cells)
        return pages
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
                // Отступ перед вопросом в начале колонки Word и LibreOffice не ставят
                val noBefore = h - spacingBeforeQuestion(l)
                if (y + noBefore > l.margin + l.contentHeight + 0.5f && !(i == 0 || y == l.margin)) nextColumn()
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
        if (variants.size > 1 && longest.all { variantHeight(it, compact) + 2 * compact.cellPadding <= compact.contentHeight }) {
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
        fun spacingAfterTitle(l: DocLayout) = l.fontSize * 0.5f
        fun spacingBeforeQuestion(l: DocLayout) = l.fontSize * 0.45f
        fun spacingAfterQuestionText(l: DocLayout) = 0f
    }
}
