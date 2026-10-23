package com.testogen.app.textbook

/**
 * Поиск цитаты-источника в тексте параграфа. Сравнение нестрогое: без учёта регистра, «ё», пробелов,
 * знаков препинания и переносов; если дословно не нашлось — ищется окно с ≥ 80 % тех же слов в том же порядке.
 * Возвращает положение в исходном тексте (для подсветки) или null.
 */
object QuoteMatcher {

    fun find(text: String, quote: String): IntRange? {
        val q = quote.replace("…", " ").replace("...", " ").trim().trim('«', '»', '"', '„', '“')
        val (normText, map) = normalizeWithMap(text)
        val (normQuote, _) = normalizeWithMap(q)
        if (normQuote.length < 12) return null
        val exact = normText.indexOf(normQuote)
        if (exact >= 0) return map[exact]..map[exact + normQuote.length - 1]
        return fuzzy(text, q)
    }

    /** Только буквы и цифры в нижнем регистре; map[i] — позиция символа в исходном тексте. */
    private fun normalizeWithMap(s: String): Pair<String, IntArray> {
        val sb = StringBuilder(s.length)
        val map = IntArray(s.length)
        var n = 0
        for (i in s.indices) {
            val c = s[i]
            if (c.isLetterOrDigit()) {
                sb.append(if (c == 'ё' || c == 'Ё') 'е' else c.lowercaseChar())
                map[n++] = i
            }
        }
        return sb.toString() to map.copyOf(n)
    }

    private class Word(val norm: String, val start: Int, val end: Int)

    private fun words(s: String): List<Word> =
        Regex("[\\p{L}\\p{Nd}]+").findAll(s).map {
            Word(it.value.lowercase().replace('ё', 'е').take(6), it.range.first, it.range.last)
        }.toList()

    private fun fuzzy(text: String, quote: String): IntRange? {
        val t = words(text)
        val q = words(quote).map { it.norm }
        if (q.size < 3 || t.isEmpty()) return null
        var best = 0.0
        var bestRange: IntRange? = null
        val window = q.size
        for (start in t.indices) {
            if (t[start].norm != q[0] && t[start].norm != q.getOrNull(1)) continue
            // В тексте бывают лишние слова (инициалы, даты в скобках) — окно с запасом
            val end = minOf(t.size, start + window * 3 / 2 + 3)
            // Совпадение по порядку: сколько слов цитаты встречается в окне друг за другом
            var qi = 0
            var matched = 0
            var last = start
            for (ti in start until end) {
                if (qi >= q.size) break
                if (t[ti].norm == q[qi]) {
                    matched++
                    qi++
                    last = ti
                } else if (qi + 1 < q.size && t[ti].norm == q[qi + 1]) {
                    matched++
                    qi += 2
                    last = ti
                }
            }
            val score = matched.toDouble() / q.size
            if (score > best) {
                best = score
                bestRange = t[start].start..t[last].end
            }
        }
        return if (best >= 0.8) bestRange else null
    }
}
