package com.testogen.app

/**
 * Подготовка текста учебника для ИИ-генерации:
 * разбивка на куски и отбор релевантных теме.
 */
object PdfQuestionGenerator {

    /**
     * Разбить текст на куски ~targetSize символов.
     * Режем по абзацам (\n\n); если текст без абзацев — по строкам;
     * слишком длинные абзацы — по предложениям, чтобы не рвать мысль.
     */
    fun splitIntoChunks(text: String, targetSize: Int = 3000): List<String> {
        var paragraphs = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.size <= 1) {
            paragraphs = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (paragraph in paragraphs) {
            if (paragraph.length > targetSize) {
                if (current.isNotBlank()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                chunks.addAll(splitLongParagraph(paragraph, targetSize))
            } else if (current.length + paragraph.length + 2 > targetSize) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(paragraph)
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks.filter { it.isNotEmpty() }
    }

    private fun splitLongParagraph(paragraph: String, targetSize: Int): List<String> {
        val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > targetSize) {
                if (current.isNotBlank()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                var i = 0
                while (i < sentence.length) {
                    val end = minOf(i + targetSize, sentence.length)
                    chunks.add(sentence.substring(i, end).trim())
                    i = end
                }
            } else if (current.length + sentence.length + 1 > targetSize) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(sentence)
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks.filter { it.isNotEmpty() }
    }

    /**
     * Отобрать куски по теме: слова темы (4+ символа) считаются
     * регистронезависимо; топ-5 кусков. Если тема не встречается —
     * куски, равномерно распределённые по документу.
     * Возвращает единый текст, обрезанный до maxChars.
     */
    fun selectRelevantChunks(
        chunks: List<String>,
        topic: String,
        topCount: Int = 5,
        maxChars: Int = 8000
    ): String {
        if (chunks.isEmpty()) return ""
        if (chunks.size <= topCount) return chunks.joinToString("\n\n---\n\n").take(maxChars)
        val words = topic.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 4 }
            .distinct()
        val scored: List<Pair<String, Int>>? = if (words.isEmpty()) null else chunks.map { chunk ->
            val lower = chunk.lowercase()
            var score = 0
            for (word in words) {
                var idx = lower.indexOf(word)
                while (idx != -1) {
                    score++
                    idx = lower.indexOf(word, idx + word.length)
                }
            }
            chunk to score
        }
        val selected: List<String> = if (scored == null || scored.all { it.second == 0 }) {
            val n = chunks.size
            val step = (n - 1).coerceAtLeast(1)
            (0 until topCount).map { i -> (i.toLong() * step / (topCount - 1).coerceAtLeast(1)).toInt() }
                .distinct()
                .map { chunks[it] }
        } else {
            scored.sortedByDescending { it.second }.take(topCount).map { it.first }
        }
        return selected.joinToString("\n\n---\n\n").take(maxChars)
    }
}
