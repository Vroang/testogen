package com.testogen.app.textbook

import com.testogen.app.data.db.SectionBlockEntity

/**
 * Текст раздела в двух видах:
 * [full] — все блоки подряд через пустую строку, как на экране чтения (по нему ищутся цитаты и считаются
 * позиции подсветки); [parts] — материал для нейросети без заданий и списков литературы, длинный параграф
 * разбит на части по подзаголовкам, чтобы ничего не обрезалось.
 */
class SectionText(
    val full: String,
    val parts: List<String>,
    val bookQuestions: List<String>,
    /** Начало каждого блока в [full] (по id блока). */
    val blockStarts: Map<Long, Int>,
) {
    val sourceChars: Int get() = parts.sumOf { it.length }

    companion object {
        /** Сколько символов текста отправлять за раз (≈ 11 тыс. токенов). */
        const val PART_LIMIT = 28_000
        const val SEPARATOR = "\n\n"

        fun build(blocks: List<SectionBlockEntity>, includeSide: Boolean): SectionText {
            val full = StringBuilder()
            val starts = HashMap<Long, Int>()
            for (b in blocks) {
                if (full.isNotEmpty()) full.append(SEPARATOR)
                starts[b.id] = full.length
                full.append(b.text)
            }

            val parts = mutableListOf<String>()
            val current = StringBuilder()
            for (b in blocks) {
                val piece = when (BlockKind.of(b.kind)) {
                    BlockKind.TEXT -> b.text
                    BlockKind.HEADING -> "## ${b.text}"
                    BlockKind.SIDE -> if (includeSide && b.text.length > 40) "[Врезка] ${b.text}" else null
                    else -> null
                } ?: continue
                // Новая часть — на подзаголовке, если текущая уже большая
                if (current.length + piece.length > PART_LIMIT && current.isNotEmpty() &&
                    (BlockKind.of(b.kind) == BlockKind.HEADING || current.length > PART_LIMIT * 1.2)
                ) {
                    parts += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append(SEPARATOR)
                current.append(piece)
            }
            if (current.isNotBlank()) parts += current.toString()

            val questions = blocks.filter { BlockKind.of(it.kind) == BlockKind.QUESTIONS }.map { it.text }
            return SectionText(full.toString(), parts, questions, starts)
        }
    }
}
