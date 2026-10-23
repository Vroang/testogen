package com.testogen.app.export

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import com.testogen.app.R

/**
 * Перенос строк шрифтом Liberation Serif — он совпадает с Times New Roman по ширине букв,
 * поэтому строки в предпросмотре рвутся там же, где в Word. Работает в фоновом потоке.
 */
class AndroidText(context: Context) : TextMeasurer {
    private val regular: Typeface = ResourcesCompat.getFont(context, R.font.liberation_serif_regular) ?: Typeface.SERIF
    private val bold: Typeface = ResourcesCompat.getFont(context, R.font.liberation_serif_bold) ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val cache = LruCache<String, List<String>>(4000)

    fun typeface(isBold: Boolean) = if (isBold) bold else regular

    /** Строки, на которые разобьётся текст при ширине [widthPt] (пункты) и размере [sizePt]. */
    // Layout.BREAK_STRATEGY_SIMPLE равна LineBreaker.BREAK_STRATEGY_SIMPLE, но LineBreaker есть только с API 29
    @SuppressLint("WrongConstant")
    fun breakLines(text: String, widthPt: Float, sizePt: Float, isBold: Boolean): List<String> {
        if (text.isEmpty()) return listOf("")
        val key = "$isBold|$sizePt|$widthPt|$text"
        cache.get(key)?.let { return it }
        // Считаем в увеличенном масштабе — точнее при дробных ширинах
        // Дробные ширины букв без подгонки к пикселям — как в Word
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG or TextPaint.LINEAR_TEXT_FLAG).apply {
            hinting = TextPaint.HINTING_OFF
            typeface = typeface(isBold)
            textSize = sizePt * SCALE
            // Без кернинга: у Liberation Serif есть пары для кириллицы, которых нет у Times New Roman,
            // и строки получались чуть уже, чем в Word
            fontFeatureSettings = NO_KERNING
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (widthPt * SCALE).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        val lines = (0 until layout.lineCount).map { text.substring(layout.getLineStart(it), layout.getLineEnd(it)).trimEnd() }
        cache.put(key, lines)
        return lines
    }

    override fun lines(text: String, widthPt: Float, sizePt: Float, bold: Boolean) = breakLines(text, widthPt, sizePt, bold).size

    companion object {
        private const val SCALE = 4f
        const val NO_KERNING = "'kern' 0"
    }
}

/** Что рисовать на странице предпросмотра (координаты в пунктах). */
sealed interface DrawItem {
    data class Line(val x: Float, val baseline: Float, val text: String, val bold: Boolean, val size: Float, val center: Boolean = false, val width: Float = 0f) : DrawItem
    data class DashedRect(val x: Float, val y: Float, val width: Float, val height: Float) : DrawItem
    /** Отрезок: рамка таблицы ответов (серая сплошная) или линия отреза над ней (пунктир). */
    data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val dashed: Boolean) : DrawItem
}

class PreviewPage(val width: Float, val height: Float, val items: List<DrawItem>)

/** Превращает раскладку Paginator в строки текста для рисования — так же, как их расположит Word. */
fun buildPreview(pages: List<Paginator.Page>, layout: DocLayout, text: AndroidText): List<PreviewPage> = pages.map { page ->
    val items = mutableListOf<DrawItem>()
    val lh = layout.lineHeight
    val size = layout.fontSize
    // Базовая линия внутри строки фиксированной высоты (как у Word при точном интервале)
    val baseline = lh * 0.8f
    if (layout.mode == LayoutMode.COMPACT && layout.cutLines) {
        page.cells.forEach { items += DrawItem.DashedRect(it.x, it.y, it.width, it.height) }
    }
    for (b in page.blocks) {
        when (b) {
            is Paginator.PlacedTitle -> items += DrawItem.Line(b.x, b.y + baseline, Paginator.titleText(b.variant), bold = true, size = size, center = true, width = b.width)
            is Paginator.PlacedQuestion -> {
                var y = b.y + if (b.atTop) 0f else Paginator.spacingBeforeQuestion(layout)
                for (line in text.breakLines("${b.question.number}. ${b.question.text}", b.width, size, true)) {
                    items += DrawItem.Line(b.x, y + baseline, line, bold = true, size = size)
                    y += lh
                }
                b.question.options.forEachIndexed { i, option ->
                    val lines = text.breakLines(option, b.width - layout.optionIndent, size, false)
                    items += DrawItem.Line(b.x, y + baseline, "${i + 1}.", bold = false, size = size)
                    for (line in lines) {
                        items += DrawItem.Line(b.x + layout.optionIndent, y + baseline, line, bold = false, size = size)
                        y += lh
                    }
                }
            }
        }
    }
    for (t in page.answers) answerTable(items, t, layout, baseline)
    PreviewPage(layout.pageWidth, layout.pageHeight, items)
}

/** Кусок таблицы ответов: так же, как его нарисует Word (поля ячеек по 2 пт, строки точной высоты). */
private fun answerTable(items: MutableList<DrawItem>, t: Paginator.AnswerTable, layout: DocLayout, baseline: Float) {
    val size = layout.fontSize
    val pad = DocxBuilder.ANSWER_PAD / 20f
    var k = 0
    // Линии рамки — посередине своей толщины, над каждой строкой
    fun lineY(row: Int) = t.y + Paginator.BORDER / 2 + row * t.pitch
    if (t.title) {
        if (t.cutLine) items += DrawItem.Segment(t.x, lineY(0), t.x + t.width, lineY(0), dashed = true)
        items += DrawItem.Line(t.x, t.rowTop(k) + baseline, "Ответы", bold = true, size = size, center = true, width = t.width)
        k++
    }
    val gridTop = lineY(k)
    val bottom = lineY(t.rowCount)
    // Шапка с номерами вопросов, затем строка на каждый вариант
    items += DrawItem.Line(t.x + pad, t.rowTop(k) + baseline, DocxBuilder.ANSWER_HEADER, bold = true, size = size)
    t.numbers.forEachIndexed { i, n ->
        items += DrawItem.Line(t.x + t.firstWidth + i * t.cellWidth, t.rowTop(k) + baseline, "$n", bold = true, size = size, center = true, width = t.cellWidth)
    }
    k++
    for (v in t.rows) {
        items += DrawItem.Line(t.x + pad, t.rowTop(k) + baseline, "${v.number}", bold = true, size = size)
        t.numbers.forEachIndexed { i, n ->
            val q = v.questions.getOrNull(n - 1) ?: return@forEachIndexed
            items += DrawItem.Line(t.x + t.firstWidth + i * t.cellWidth, t.rowTop(k) + baseline, "${q.correct + 1}", bold = false, size = size, center = true, width = t.cellWidth)
        }
        k++
    }
    for (row in (if (t.title) 1 else 0)..t.rowCount) {
        items += DrawItem.Segment(t.x, lineY(row), t.x + t.width, lineY(row), dashed = false)
    }
    items += DrawItem.Segment(t.x, gridTop, t.x, bottom, dashed = false)
    for (i in 0..t.numbers.size) {
        val x = t.x + t.firstWidth + i * t.cellWidth
        items += DrawItem.Segment(x, gridTop, x, bottom, dashed = false)
    }
}
