package com.testogen.app.export

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Вид раскладки: несколько вариантов на листе (с линиями отреза) или каждый вариант с новой страницы. */
@Serializable
enum class LayoutMode { COMPACT, PER_PAGE }

/** Настройки файла теста; [auto] — выбраны приложением, мама может поменять. */
@Serializable
data class DocLayout(
    val mode: LayoutMode,
    val landscape: Boolean,
    val columns: Int,
    val fontSize: Float,
    val cutLines: Boolean = true,
    val auto: Boolean = true,
) {
    /** Лист A4 в пунктах. */
    val pageWidth get() = if (landscape) A4_LONG else A4_SHORT
    val pageHeight get() = if (landscape) A4_SHORT else A4_LONG
    /** Поля, отступы и промежутки округлены до twips (1/20 пункта) — ровно так их хранит и раскладывает Word. */
    val margin get() = twips(if (mode == LayoutMode.COMPACT) CM else 1.5f * CM)
    val contentWidth get() = pageWidth - 2 * margin
    val contentHeight get() = pageHeight - 2 * margin
    /** Расстояние между колонками в режиме «вариант на странице». */
    val columnGap get() = twips(0.8f * CM)
    val columnWidth get() = if (mode == LayoutMode.COMPACT) contentWidth / columns else (contentWidth - columnGap * (columns - 1)) / columns
    /** Поля внутри ячейки таблицы (режим «несколько на листе»). */
    val cellPadding get() = twips(0.25f * CM)
    /** Ширина текста внутри одного варианта. */
    val textWidth get() = if (mode == LayoutMode.COMPACT) columnWidth - 2 * cellPadding else columnWidth

    /** Высота строки: в файле задана точно, поэтому совпадает с предпросмотром. */
    val lineHeight get() = lineHeight(fontSize)
    val optionIndent get() = fontSize * 1.6f

    companion object {
        const val CM = 28.3465f
        const val A4_SHORT = 595.3f
        const val A4_LONG = 841.9f
        const val MIN_FONT = 10f

        fun lineHeight(size: Float) = ceil(size * 1.15f * 2) / 2f

        fun twips(pt: Float) = (pt * 20).roundToInt() / 20f

        /** Запас на погрешность сложения дробных высот: блок, вылезающий за поле хоть на 0,1 пт, Word переносит. */
        const val EPS = 0.02f
    }
}

/** Вопрос в файле: номер, текст, варианты ответа (верный — [correct]). */
data class DocQuestion(val number: Int, val text: String, val options: List<String>, val correct: Int)

data class DocVariant(val number: Int, val questions: List<DocQuestion>)

/** Измерение текста шрифтом Times New Roman (на телефоне — метрически совместимым Liberation Serif). */
interface TextMeasurer {
    /** Число строк, которое займёт [text] шириной [widthPt] при размере [sizePt]. */
    fun lines(text: String, widthPt: Float, sizePt: Float, bold: Boolean): Int
}
