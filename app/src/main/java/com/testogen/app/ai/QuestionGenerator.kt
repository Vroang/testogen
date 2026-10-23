package com.testogen.app.ai

import com.testogen.app.data.QuestionDraft
import com.testogen.app.data.QuestionRepository
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.GlossaryEntity
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.data.db.TextbookEntity
import com.testogen.app.data.db.TextbookSectionEntity
import com.testogen.app.data.db.QuestionSource
import com.testogen.app.data.db.QuestionText
import com.testogen.app.data.topicKey
import com.testogen.app.textbook.QuoteMatcher
import com.testogen.app.textbook.SectionText
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

/** Параметры задания «вопросы по учебнику». */
@Serializable
data class TextbookJobParams(
    val textbookId: Long,
    val sectionIds: List<Long>,
    val total: Int,
    val difficulties: List<Int>,
    val review: Boolean,
    /** Брать ли врезки (биографии, документы) как материал для вопросов. */
    val includeSide: Boolean = true,
)

/** Как продвигается генерация — для экрана и уведомления. */
interface GenerationProgress {
    suspend fun stage(text: String)
    suspend fun saved(totalSaved: Int, dropped: Int, costUsd: Double)
}

/**
 * Генерация вопросов порциями по ~10: промпт → разбор и проверки → (для учебника) поиск цитаты
 * в тексте → отсев похожих на уже имеющиеся → (по желанию) проверка рецензентом → перемешивание
 * вариантов → сохранение. Повторный запуск продолжает с того места, где остановились.
 */
class QuestionGenerator(
    private val db: AppDatabase,
    private val ai: AiClient,
    private val questions: QuestionRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Где в тексте параграфа ответ на вопрос. */
    private class Located(val start: Int, val end: Int)

    /** Одна «единица» генерации: тема или кусок параграфа. */
    private class Unit(
        val operation: String,
        val topic: String,
        val grade: Int?,
        val need: Int,
        /** Сообщение пользователю по нужным количествам, уже имеющимся вопросам и правилам. */
        val prompt: (counts: Map<Difficulty, Int>, existing: List<QuestionText>, rules: List<String>) -> String,
        val stage: String,
        val source: QuestionSource,
        /** Для учебника: найти цитату в тексте; null — вопрос отбраковывается. */
        val locate: ((ParsedQuestion) -> Located?)? = null,
        val textbookId: Long? = null,
        val sectionId: Long? = null,
    )

    private class Totals(var saved: Int, val total: Int)

    suspend fun runTopic(jobId: Long, params: TopicJobParams, progress: GenerationProgress) {
        val difficulties = params.difficulties.map { Difficulty.of(it) }.ifEmpty { listOf(Difficulty.MEDIUM) }
        val totals = Totals(db.questions().countByJob(jobId), params.total)
        val unit = Unit(
            operation = "topic",
            topic = params.topic,
            grade = params.grade,
            need = params.total - totals.saved,
            prompt = { counts, existing, rules -> Prompts.topicUser(params.topic, params.grade, counts, rules, existingLines(existing)) },
            stage = "Придумываю вопросы…",
            source = QuestionSource.TOPIC,
        )
        runUnit(jobId, unit, difficulties, params.review, totals, progress)
        if (totals.saved == 0) {
            throw AiException("Нейросеть не смогла составить подходящие вопросы. Попробуйте уточнить тему.")
        }
    }

    suspend fun runTextbook(jobId: Long, params: TextbookJobParams, progress: GenerationProgress) {
        val difficulties = params.difficulties.map { Difficulty.of(it) }.ifEmpty { listOf(Difficulty.MEDIUM) }
        val book = db.textbooks().get(params.textbookId) ?: throw AiException("Учебник удалён")
        val sections = db.textbooks().sectionsByIds(params.sectionIds)
        if (sections.isEmpty()) throw AiException("Не выбраны параграфы")
        val glossary = db.textbooks().glossary(book.id)
        progress.stage("Читаю параграфы…")
        val texts = sections.associate { it.id to SectionText.build(db.textbooks().blocks(it.id), params.includeSide) }
        val quotas = allocate(params.total, sections.map { texts.getValue(it.id).sourceChars })
        val totals = Totals(db.questions().countByJob(jobId), params.total)

        for ((index, section) in sections.withIndex()) {
            val text = texts.getValue(section.id)
            val left = quotas[index] - db.questions().countByJobSection(jobId, section.id)
            if (left <= 0 || text.parts.isEmpty()) continue
            val partQuotas = allocate(left, text.parts.map { it.length })
            for (pi in text.parts.indices) {
                if (partQuotas[pi] <= 0) continue
                val prefix = sectionPrefix(book, section, text, pi, glossary)
                val unit = Unit(
                    operation = "textbook",
                    topic = section.fullTitle,
                    grade = book.grade,
                    need = partQuotas[pi],
                    prompt = { counts, existing, rules -> prefix + "\n\n" + Prompts.textbookTask(counts, rules, existingLines(existing)) },
                    stage = "Придумываю вопросы по ${section.label.ifEmpty { section.title }}…",
                    source = QuestionSource.TEXTBOOK,
                    locate = { q ->
                        q.quote.takeIf { it.isNotBlank() }?.let { QuoteMatcher.find(text.full, it) }?.let { Located(it.first, it.last + 1) }
                    },
                    textbookId = book.id,
                    sectionId = section.id,
                )
                runUnit(jobId, unit, difficulties, params.review, totals, progress)
            }
        }
        if (totals.saved == 0) {
            throw AiException("Нейросеть не смогла составить вопросы с опорой на текст. Попробуйте другие параграфы.")
        }
    }

    /** Порции по ~10 вопросов, пока не наберётся [Unit.need] или не кончатся попытки. */
    private suspend fun runUnit(
        jobId: Long,
        unit: Unit,
        difficulties: List<Difficulty>,
        review: Boolean,
        totals: Totals,
        progress: GenerationProgress,
    ) {
        val key = topicKey(unit.topic)
        var savedHere = 0
        var batchLimit = BATCH
        var attempts = 0
        val maxAttempts = ceil(unit.need / BATCH.toDouble()).toInt() * 3 + 2
        var retries = 0

        while (savedHere < unit.need && attempts < maxAttempts) {
            attempts++
            val need = unit.need - savedHere
            val batch = min(batchLimit, need)
            // Просим чуть больше: часть вопросов может отсеяться
            val ask = batch + if (batch >= 5) 3 else 1
            // Сколько каждой сложности ещё не хватает: модель сама ставит сложность и может перекосить
            val have = db.questions().difficultyCountsByJob(jobId).associate { Difficulty.of(it.code) to it.count }
            val target = split(totals.total, difficulties)
            val counts = askCounts(ask, difficulties.associateWith { ((target[it] ?: 0) - (have[it] ?: 0)).coerceAtLeast(0) })

            progress.stage(unit.stage)
            val existing = db.questions().textsByTopic(key)
            val rules = db.aiRules().active().map { it.text }
            val result = try {
                ai.complete(
                    ChatRequest(
                        operation = unit.operation,
                        system = Prompts.GENERATE_SYSTEM,
                        user = unit.prompt(counts, existing, rules),
                        maxTokens = min(8000, 300 + 480 * ask),
                        temperature = 0.8,
                        note = unit.topic,
                    ),
                )
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

            // Для учебника: ответ должен опираться на цитату, которая действительно есть в тексте
            val located = HashMap<ParsedQuestion, Located>()
            val locate = unit.locate
            val withSource = if (locate == null) {
                parsed.accepted
            } else {
                parsed.accepted.filter { q ->
                    val loc = locate(q)
                    if (loc != null) located[q] = loc else dropped++
                    loc != null
                }
            }

            // Отсев похожих: на банк темы и друг на друга
            val known = existing.mapTo(mutableListOf()) { QuestionQuality.fingerprint(it.text, it.answer) }
            val fresh = mutableListOf<ParsedQuestion>()
            for (q in withSource) {
                val fp = QuestionQuality.fingerprint(q.question, q.correct)
                if (known.any { QuestionQuality.isDuplicate(it, fp) }) {
                    dropped++
                } else {
                    known += fp
                    fresh += q
                }
            }

            var cost = result.costUsd
            val approved = if (review && fresh.isNotEmpty()) {
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
                        val loc = located[q]
                        QuestionDraft(
                            text = q.question,
                            options = s.options,
                            correctIndex = s.correctIndex,
                            difficulty = q.difficulty,
                            topic = unit.topic,
                            grade = unit.grade,
                            source = unit.source,
                            explanation = q.explanation,
                            jobId = jobId,
                            textbookId = unit.textbookId,
                            sectionId = unit.sectionId,
                            quote = loc?.let { q.quote },
                            quoteStart = loc?.start,
                            quoteEnd = loc?.end,
                        )
                    },
                )
            }
            savedHere += toSave.size
            totals.saved += toSave.size
            progress.saved(totals.saved, dropped, cost)
        }
    }

    /** Итог замены: id нового вопроса в банке и стоимость. */
    data class Replacement(val questionId: Long, val costUsd: Double)

    /**
     * Новый вопрос на замену [old] с учётом причины. Для вопроса из учебника — по тексту того же
     * параграфа (с цитатой); иначе — по теме. Нейросеть предлагает три варианта, берётся первый,
     * прошедший все проверки. Новый вопрос сохраняется в банк.
     */
    suspend fun replaceOne(old: QuestionEntity, reason: String, difficulty: Difficulty, review: Boolean): Replacement {
        val section = old.sectionId?.let { db.textbooks().section(it) }
        val book = section?.let { db.textbooks().get(it.textbookId) }
        val text = section?.let { SectionText.build(db.textbooks().blocks(it.id), includeSide = true) }
        val existing = db.questions().textsByTopic(old.topicKey)
        val rules = db.aiRules().active().map { it.text }
        val oldAnswer = listOf(old.option1, old.option2, old.option3, old.option4)[old.correctIndex]
        val task = Prompts.replaceTask(
            old.topic, old.grade, old.text, oldAnswer, reason, difficulty, rules, existingLines(existing), fromTextbook = text != null,
        )
        val user = if (section != null && book != null && text != null && text.parts.isNotEmpty()) {
            // Часть параграфа, где была цитата старого вопроса (или первая); начало запроса — как при генерации (кэш)
            val partIndex = old.quote?.let { q -> text.parts.indexOfFirst { QuoteMatcher.find(it, q) != null } }?.takeIf { it >= 0 } ?: 0
            sectionPrefix(book, section, text, partIndex, db.textbooks().glossary(book.id)) + "\n\n" + task
        } else {
            task
        }
        val result = ai.complete(ChatRequest(operation = "replace", system = Prompts.GENERATE_SYSTEM, user = user, maxTokens = 1800, temperature = 0.8, note = old.topic))
        var cost = result.costUsd
        val parsed = QuestionQuality.parse(result.content).accepted.map { it.copy(difficulty = difficulty) }
        val known = existing.map { QuestionQuality.fingerprint(it.text, it.answer) }
        val candidates = parsed.mapNotNull { q ->
            if (known.any { QuestionQuality.isDuplicate(it, QuestionQuality.fingerprint(q.question, q.correct)) }) return@mapNotNull null
            if (text == null) return@mapNotNull q to null
            val loc = q.quote.takeIf { it.isNotBlank() }?.let { QuoteMatcher.find(text.full, it) } ?: return@mapNotNull null
            q to Located(loc.first, loc.last + 1)
        }
        if (candidates.isEmpty()) throw AiException("Нейросеть не смогла придумать подходящую замену. Попробуйте ещё раз или возьмите вопрос из банка.", retryable = true)
        val approved = if (review) {
            val (ok, reviewCost) = review(candidates.map { it.first })
            cost += reviewCost
            candidates.filter { it.first in ok }
        } else {
            candidates
        }
        val (q, loc) = approved.firstOrNull() ?: candidates.first()
        val s = QuestionQuality.shuffle(q)
        val id = questions.insertAll(
            listOf(
                QuestionDraft(
                    text = q.question,
                    options = s.options,
                    correctIndex = s.correctIndex,
                    difficulty = q.difficulty,
                    topic = old.topic,
                    grade = old.grade,
                    source = QuestionSource.of(old.source),
                    explanation = q.explanation,
                    textbookId = old.textbookId,
                    sectionId = old.sectionId,
                    quote = loc?.let { q.quote },
                    quoteStart = loc?.start,
                    quoteEnd = loc?.end,
                ),
            ),
        ).first()
        return Replacement(id, cost)
    }

    /** Начало сообщения по параграфу — одинаковое для генерации и замены, поэтому берётся из кэша DeepSeek. */
    private fun sectionPrefix(
        book: TextbookEntity,
        section: TextbookSectionEntity,
        text: SectionText,
        partIndex: Int,
        glossary: List<GlossaryEntity>,
    ): String {
        val keywords = section.keywords.lines().filter { it.isNotBlank() }
        val definitions = glossary
            .filter { g -> keywords.any { k -> g.term.equals(k, ignoreCase = true) || k.contains(g.term, ignoreCase = true) } }
            .map { "${it.term} — ${it.definition}" }
        return Prompts.textbookPrefix(
            bookTitle = book.title,
            grade = book.grade,
            sectionTitle = section.fullTitle,
            part = if (text.parts.size > 1) "часть ${partIndex + 1} из ${text.parts.size}" else null,
            text = text.parts[partIndex],
            bookQuestions = text.bookQuestions,
            definitions = definitions,
        )
    }

    private fun existingLines(existing: List<QuestionText>) =
        existing.take(EXISTING_IN_PROMPT).map { "${it.text.take(150)} → ${it.answer.take(60)}" }

    /** Второй проход: возвращает одобренные вопросы и стоимость. Если рецензент сломался — пропускаем всё. */
    private suspend fun review(items: List<ParsedQuestion>): Pair<List<ParsedQuestion>, Double> {
        val result = try {
            ai.complete(
                ChatRequest(
                    operation = "check",
                    system = Prompts.REVIEW_SYSTEM,
                    user = Prompts.reviewUser(items),
                    maxTokens = 200 + 70 * items.size,
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

        /** Делит [total] вопросов пропорционально объёму текста [weights]; заметным кускам — хотя бы по одному. */
        fun allocate(total: Int, weights: List<Int>): List<Int> {
            if (weights.isEmpty()) return emptyList()
            val sum = weights.sum().coerceAtLeast(1)
            val raw = weights.map { it.toDouble() * total / sum }
            val result = raw.map { it.toInt() }.toMutableList()
            var rest = total - result.sum()
            for (i in raw.indices.sortedByDescending { raw[it] - raw[it].toInt() }) {
                if (rest == 0) break
                result[i]++
                rest--
            }
            for (i in result.indices) {
                if (result[i] == 0 && weights[i] > 1500) {
                    val donor = result.indices.maxBy { result[it] }
                    if (result[donor] > 1) {
                        result[donor]--
                        result[i]++
                    }
                }
            }
            return result
        }

        /**
         * Примерная стоимость в долларах (по текущим ценам DeepSeek и времени суток), с запасом:
         * каждый запрос просит на 3 вопроса больше. [sourceChars] — объём текста учебника в каждом
         * куске (для генерации по теме — пусто); первый запрос по куску платит за текст полностью,
         * следующие берут его из кэша.
         */
        fun estimateCostUsd(
            total: Int,
            review: Boolean,
            existingInTopic: Int,
            rules: Int,
            sourceChars: List<Int> = emptyList(),
            now: Long = System.currentTimeMillis(),
        ): Double {
            val system = Pricing.estimateTokens(Prompts.GENERATE_SYSTEM)
            val variable = 150 + min(existingInTopic + total / 2, EXISTING_IN_PROMPT) * 45 + rules * 25
            var cost = 0.0
            var batchesTotal = 0
            if (sourceChars.isEmpty()) {
                val batches = ceil(total / BATCH.toDouble()).toInt()
                batchesTotal = batches
                cost += Pricing.costUsd(cacheHit = system * batches, cacheMiss = variable * batches, output = 0, atMillis = now)
            } else {
                val quotas = allocate(total, sourceChars)
                for ((i, chars) in sourceChars.withIndex()) {
                    if (quotas[i] == 0) continue
                    val batches = ceil(quotas[i] / BATCH.toDouble()).toInt()
                    batchesTotal += batches
                    // Текст параграфа + вопросы учебника + словарь, ~2,6 символа на токен
                    val source = (chars * 1.15 / 2.6).toInt()
                    cost += Pricing.costUsd(
                        cacheHit = system * batches + source * (batches - 1),
                        cacheMiss = source + variable * batches,
                        output = 0,
                        atMillis = now,
                    )
                }
            }
            val asked = total + batchesTotal * 3
            cost += Pricing.costUsd(0, 0, output = asked * 260, atMillis = now)
            if (review) {
                val reviewSystem = Pricing.estimateTokens(Prompts.REVIEW_SYSTEM)
                cost += Pricing.costUsd(reviewSystem * batchesTotal, asked * 140, asked * 25, now)
            }
            return cost
        }
    }
}
