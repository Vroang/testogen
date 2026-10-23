package com.testogen.app.data

import kotlin.random.Random

/** Минимум о вопросе, нужный для раскладки по вариантам; [text] — чтобы не взять один и тот же вопрос дважды. */
data class Candidate(val id: Long, val difficulty: Int, val topicKey: String, val text: String = "")

/**
 * Раскладка вопросов банка по вариантам теста.
 * Без повторов между вариантами: из банка берётся нужное число вопросов с сохранением пропорций
 * сложностей и тем, и они раздаются по вариантам «по кругу» — в каждом варианте похожая смесь.
 * С повторами (если вопросов меньше, чем вариантов × вопросов): каждый вопрос используется
 * примерно одинаково часто, внутри одного варианта повторов нет.
 * Внутри варианта — от лёгких к сложным. Работает мгновенно и для 40 × 40.
 */
object TestBuilder {

    sealed interface Result {
        data class Ok(val variants: List<List<Long>>) : Result
        /** Не хватает вопросов даже с повторами: нужно хотя бы [need] в банке. */
        data class NotEnough(val have: Int, val need: Int) : Result
    }

    /** Сколько разных вопросов нужно для теста без повторов. */
    fun needed(variants: Int, perVariant: Int) = variants * perVariant

    /**
     * Банк без дублей: вопросы с одинаковым текстом (например, созданные дважды по двум копиям учебника)
     * считаются одним — иначе «без повторов» один и тот же вопрос попал бы в разные варианты.
     */
    fun unique(pool: List<Candidate>): List<Candidate> =
        pool.distinctBy { c -> c.text.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }.ifEmpty { "#${c.id}" } }

    /**
     * Повторы между вариантами бывают, только если разных вопросов меньше, чем вариантов × вопросов,
     * и [allowRepeats] включён явно; иначе — [Result.NotEnough].
     */
    fun build(all: List<Candidate>, variants: Int, perVariant: Int, allowRepeats: Boolean, random: Random = Random.Default): Result {
        val pool = unique(all)
        val total = variants * perVariant
        if (pool.size >= total) return Result.Ok(deal(stratified(pool, total, random), variants, perVariant))
        if (!allowRepeats || pool.size < perVariant) {
            return Result.NotEnough(pool.size, if (allowRepeats) perVariant else total)
        }
        // С повторами: идём по кругу по перемешанному (с чередованием сложностей) банку
        val ordered = stratified(pool, pool.size, random)
        val result = List(variants) { v ->
            List(perVariant) { j -> ordered[(v * perVariant + j) % ordered.size] }
        }
        return Result.Ok(result.map { sortByDifficulty(it, random) })
    }

    /**
     * Выбирает [count] вопросов, сохраняя доли сложностей и тем, и выстраивает их так,
     * чтобы при раздаче по кругу варианты получились похожими.
     */
    private fun stratified(pool: List<Candidate>, count: Int, random: Random): List<Candidate> {
        val groups = pool.groupBy { it.difficulty to it.topicKey }.mapValues { it.value.shuffled(random).toMutableList() }
        val quotas = HashMap<Pair<Int, String>, Int>()
        // Пропорциональные доли с округлением по наибольшему остатку
        val raw = groups.mapValues { (_, g) -> g.size.toDouble() * count / pool.size }
        raw.forEach { (k, v) -> quotas[k] = v.toInt() }
        var rest = count - quotas.values.sum()
        for (k in raw.keys.sortedByDescending { raw.getValue(it) - raw.getValue(it).toInt() }) {
            if (rest == 0) break
            if (quotas.getValue(k) < groups.getValue(k).size) {
                quotas[k] = quotas.getValue(k) + 1
                rest--
            }
        }
        // Порядок: сначала по сложности, внутри — темы вперемешку, чтобы раздача по кругу их распределила
        val picked = groups.keys.sortedWith(compareBy({ it.first }, { it.second })).flatMap { k ->
            groups.getValue(k).take(quotas.getValue(k))
        }
        return picked
    }

    private fun deal(cards: List<Candidate>, variants: Int, perVariant: Int): List<List<Long>> {
        val buckets = List(variants) { mutableListOf<Candidate>() }
        cards.forEachIndexed { i, c -> buckets[i % variants] += c }
        return buckets.map { b -> sortByDifficulty(b.take(perVariant), Random.Default) }
    }

    /** В варианте сначала лёгкие, потом средние и сложные; внутри одной сложности — случайно. */
    private fun sortByDifficulty(list: List<Candidate>, random: Random): List<Long> =
        list.shuffled(random).sortedBy { it.difficulty }.map { it.id }
}
