package com.testogen.app.ai

import com.testogen.app.data.QuestionDraft
import com.testogen.app.data.QuestionRepository
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.QuestionSource
import com.testogen.app.data.topicKey
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Параметры задания «вопросы по теме» (хранятся в задании как JSON). */
@Serializable
data class TopicJobParams(
    val topic: String,
    val grade: Int?,
    val total: Int,
    /** Коды сложностей (Difficulty.code). */
    val difficulties: List<Int>,
    /** Второй проход: нейросеть-рецензент перепроверяет вопросы. */
    val review: Boolean,
)

/** Как продвигается генерация — для экрана и уведомления. */
interface GenerationProgress {
    suspend fun stage(text: String)
    suspend fun saved(totalSaved: Int, dropped: Int, costUsd: Double)
}

/**
 * Генерация вопросов по теме порциями по ~10: промпт → разбор и проверки →
 * отсев похожих на уже имеющиеся → (по желанию) проверка рецензентом → перемешивание
 * вариантов → сохранение. Повторный запуск продолжает с того места, где остановились.
 */
class QuestionGenerator(
    private val db: AppDatabase,
    private val ai: AiClient,
    private val questions: QuestionRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun runTopic(jobId: Long, params: TopicJobParams, progress: GenerationProgress) {
        val key = topicKey(params.topic)
        val difficulties = params.difficulties.map { Difficulty.of(it) }.ifEmpty { listOf(Difficulty.MEDIUM) }
        var saved = db.questions().countByJob(jobId)
        var batchLimit = BATCH
        var attempts = 0
        val maxAttempts = ceil(params.total / BATCH.toDouble()).toInt() * 3 + 2
        var retries = 0

        while (saved < params.total && attempts < maxAttempts) {
            attempts++
            val need = params.total - saved
            val batch = min(batchLimit, need)
            // Просим чуть больше: часть вопросов может отсеяться
            val ask = batch + if (batch >= 5) 3 else 1
            // Сколько каждой сложности ещё не хватает: модель сама ставит сложность и может перекосить
            val have = db.questions().difficultyCountsByJob(jobId).associate { Difficulty.of(it.code) to it.count }
            val target = split(params.total, difficulties)
            val counts = askCounts(ask, difficulties.associateWith { ((target[it] ?: 0) - (have[it] ?: 0)).coerceAtLeast(0) })

            progress.stage("Придумываю вопросы…")
            val existing = db.questions().textsByTopic(key)
            val rules = db.aiRules().active().map { it.text }
            val request = ChatRequest(
                operation = "topic",
                system = Prompts.GENERATE_SYSTEM,
                user = Prompts.topicUser(params.topic, params.grade, counts, rules, existing.take(EXISTING_IN_PROMPT).map { "${it.text.take(150)} → ${it.answer.take(60)}" }),
                maxTokens = min(8000, 300 + 450 * ask),
                temperature = 0.8,
                note = params.topic,
            )
            val result = try {
                ai.complete(request)
            } catch (e: AiException) {
                if (e.retryable && retries < 2) {
                    retries++
                    progress.stage("Нейросеть не ответила, пробую ещё раз…")
                    delay(if (retries == 1) 5_000L else 20_000L)
                    continue
                }
                throw e
            }
            retries = 0

            val parsed = QuestionQuality.parse(result.content)
            if (result.truncated && parsed.accepted.size < batch) batchLimit = max(3, batchLimit / 2)
            var dropped = parsed.rejected.size

            // Отсев похожих: на банк темы и друг на друга
            val known = existing.mapTo(mutableListOf()) { QuestionQuality.fingerprint(it.text, it.answer) }
            val fresh = mutableListOf<ParsedQuestion>()
            for (q in parsed.accepted) {
                val fp = QuestionQuality.fingerprint(q.question, q.correct)
                if (known.any { QuestionQuality.isDuplicate(it, fp) }) {
                    dropped++
                } else {
                    known += fp
                    fresh += q
                }
            }

            var cost = result.costUsd
            val approved = if (params.review && fresh.isNotEmpty()) {
                progress.stage("Проверяю вопросы…")
                val (ok, reviewCost) = review(fresh)
                cost += reviewCost
                dropped += fresh.size - ok.size
                ok
            } else {
                fresh
            }

            val toSave = approved.take(need)
            if (toSave.isNotEmpty()) {
                progress.stage("Сохраняю…")
                questions.insertAll(
                    toSave.map { q ->
                        val s = QuestionQuality.shuffle(q)
                        QuestionDraft(
                            text = q.question,
                            options = s.options,
                            correctIndex = s.correctIndex,
                            difficulty = q.difficulty,
                            topic = params.topic,
                            grade = params.grade,
                            source = QuestionSource.TOPIC,
                            explanation = q.explanation,
                            jobId = jobId,
                        )
                    },
                )
            }
            saved += toSave.size
            progress.saved(saved, dropped, cost)
        }
        if (saved == 0) {
            throw AiException("Нейросеть не смогла составить подходящие вопросы. Попробуйте уточнить тему.")
        }
    }

    /** Второй проход: возвращает одобренные вопросы и стоимость. Если рецензент сломался — пропускаем всё. */
    private suspend fun review(items: List<ParsedQuestion>): Pair<List<ParsedQuestion>, Double> {
        val result = try {
            ai.complete(
                ChatRequest(
                    operation = "check",
                    system = Prompts.REVIEW_SYSTEM,
                    user = Prompts.reviewUser(items),
                    maxTokens = 200 + 60 * items.size,
                    temperature = 0.1,
                ),
            )
        } catch (e: AiException) {
            return items to 0.0
        }
        val bad = runCatching {
            json.parseToJsonElement(result.content.substring(result.content.indexOf('{'), result.content.lastIndexOf('}') + 1))
                .jsonObject["reviews"]!!.jsonArray
                .mapNotNull { r ->
                    val o = r.jsonObject
                    val n = o["n"]?.jsonPrimitive?.intOrNull
                    val ok = o["ok"]?.jsonPrimitive?.booleanOrNull ?: true
                    if (n != null && !ok) n - 1 else null
                }.toSet()
        }.getOrDefault(emptySet())
        return items.filterIndexed { i, _ -> i !in bad } to result.costUsd
    }

    companion object {
        const val BATCH = 10
        const val EXISTING_IN_PROMPT = 60

        /** Делит [n] вопросов между сложностями поровну; остаток — средним, потом лёгким. */
        fun split(n: Int, difficulties: List<Difficulty>): Map<Difficulty, Int> {
            val sorted = difficulties.distinct().sortedBy { it.code }
            val base = n / sorted.size
            val bonus = sorted
                .sortedBy { if (it == Difficulty.MEDIUM) 0 else if (it == Difficulty.EASY) 1 else 2 }
                .take(n % sorted.size)
                .toSet()
            return sorted.associateWith { base + if (it in bonus) 1 else 0 }
        }

        /** Делит [ask] вопросов пропорционально недостающим [missing]; если всего хватает — поровну. */
        fun askCounts(ask: Int, missing: Map<Difficulty, Int>): Map<Difficulty, Int> {
            val total = missing.values.sum()
            if (total == 0) return split(ask, missing.keys.toList())
            val raw = missing.mapValues { (_, m) -> m.toDouble() * ask / total }
            val counts = raw.mapValues { it.value.toInt() }.toMutableMap()
            var rest = ask - counts.values.sum()
            for (d in raw.entries.sortedByDescending { it.value - it.value.toInt() }.map { it.key }) {
                if (rest == 0) break
                counts[d] = counts.getValue(d) + 1
                rest--
            }
            return counts.filterValues { it > 0 }
        }

        /**
         * Примерная стоимость в долларах (по текущим ценам DeepSeek и времени суток).
         * Считаем с запасом: каждый запрос просит на 3 вопроса больше.
         */
        fun estimateCostUsd(total: Int, review: Boolean, existingInTopic: Int, rules: Int, now: Long = System.currentTimeMillis()): Double {
            val batches = ceil(total / BATCH.toDouble()).toInt()
            val asked = total + batches * 3
            val system = Pricing.estimateTokens(Prompts.GENERATE_SYSTEM)
            val variable = 120 + min(existingInTopic + total / 2, EXISTING_IN_PROMPT) * 45 + rules * 25
            var cost = Pricing.costUsd(cacheHit = system * batches, cacheMiss = variable * batches, output = asked * 230, atMillis = now)
            if (review) {
                val reviewSystem = Pricing.estimateTokens(Prompts.REVIEW_SYSTEM)
                cost += Pricing.costUsd(reviewSystem * batches, asked * 110, asked * 25, now)
            }
            return cost
        }
    }
}
