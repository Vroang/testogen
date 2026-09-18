package com.testogen.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

sealed class ModelCheck {
    object Works : ModelCheck()
    object RateLimited : ModelCheck()
    data class Failed(val message: String) : ModelCheck()
}

sealed class GenerationText {
    data class Success(val text: String) : GenerationText()
    object RateLimited : GenerationText()
    data class Failed(val message: String) : GenerationText()
}

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int,
    val temperature: Double = 0.7
)

@Serializable
data class ChatChoice(val message: ChatMessage? = null)

@Serializable
data class ChatCompletionResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class GeneratedQuestion(
    val text: String = "",
    val options: List<String> = emptyList(),
    val correct: Int = 0,
    val difficulty: String = "medium"
)

@Serializable
data class GeneratedQuestions(val questions: List<GeneratedQuestion> = emptyList())

data class GenerationOutcome(
    val questions: List<GeneratedQuestion>,
    val modelUsed: String?,
    val report: String
)

data class AiInstructions(val system: String, val avoid: String, val subjects: String)

fun AppSettings.toAiInstructions(): AiInstructions = AiInstructions(
    system = aiSystem.ifBlank { SettingsRepository.DEFAULT_AI_SYSTEM_TEXT },
    avoid = aiAvoid,
    subjects = aiSubjects
)

@Serializable
data class ModelPricing(val prompt: String = "0", val completion: String = "0")

@Serializable
data class ModelInfo(
    val id: String,
    val name: String = id,
    val pricing: ModelPricing = ModelPricing()
)

@Serializable
data class ModelsResponse(val data: List<ModelInfo> = emptyList())

object OpenRouterClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://openrouter.ai/api/v1"

    private fun get(url: String, apiKey: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://testogen.app")
            .header("X-Title", "TestoGen")
            .get()
            .build()

    /** Минимальная проверка ключа: запрос информации о ключе (токены не тратятся). */
    suspend fun checkKey(apiKey: String): ModelCheck = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ModelCheck.Failed("Ключ пуст")
        try {
            client.newCall(get("$BASE/key", apiKey)).execute().use { resp ->
                when {
                    resp.isSuccessful -> ModelCheck.Works
                    resp.code == 401 || resp.code == 403 -> ModelCheck.Failed("Неверный ключ")
                    else -> ModelCheck.Failed("HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            ModelCheck.Failed("Нет соединения")
        }
    }

    /** Список моделей OpenRouter с ценами. */
    suspend fun fetchModels(apiKey: String): ApiResult<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            client.newCall(get("$BASE/models", apiKey)).execute().use { resp ->
                val body = resp.body?.string()
                if (body == null) {
                    ApiResult.Error("Пустой ответ сервера")
                } else if (!resp.isSuccessful) {
                    ApiResult.Error("HTTP ${resp.code}")
                } else {
                    ApiResult.Success(json.decodeFromString<ModelsResponse>(body).data.sortedBy { it.id })
                }
            }
        } catch (e: Exception) {
            ApiResult.Error("Нет соединения")
        }
    }

    /** Минимальный запрос генерации (1 токен): работает ли модель прямо сейчас. */
    suspend fun checkModel(apiKey: String, modelId: String): ModelCheck = withContext(Dispatchers.IO) {
        try {
            val safeId = modelId.replace("\"", "\\\"")
            val body = "{\"model\":\"$safeId\",\"messages\":[{\"role\":\"user\",\"content\":\"1\"}],\"max_tokens\":1}"
            val req = Request.Builder()
                .url("$BASE/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> ModelCheck.Works
                    resp.code == 429 -> ModelCheck.RateLimited
                    resp.code == 404 -> ModelCheck.Failed("модель не найдена (устарела)")
                    resp.code == 400 -> ModelCheck.Failed("запрос не принят (400)")
                    resp.code == 401 || resp.code == 403 -> ModelCheck.Failed("Неверный ключ")
                    else -> ModelCheck.Failed("HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            ModelCheck.Failed("Нет соединения")
        }
    }

    private fun difficultyText(difficulties: Collection<String>): String {
        val names = buildList {
            if ("easy" in difficulties) add("лёгкий")
            if ("medium" in difficulties) add("средний")
            if ("hard" in difficulties) add("сложный")
        }
        return if (names.isEmpty()) "разный" else names.joinToString(", ")
    }

    /**
     * System prompt = основная инструкция пользователя
     * + блок «ЧЕГО НЕ ДЕЛАТЬ» (если не пусто)
     * + блок «ПРЕДМЕТНЫЕ НЮАНСЫ» (если не пусто)
     * + дополнительный блок (например, ограничение «только из учебника»).
     */
    private fun composeSystem(ai: AiInstructions, extra: String = ""): String {
        val sb = StringBuilder()
        sb.append(ai.system.trim())
        if (ai.avoid.isNotBlank()) {
            sb.append("\n\n")
            sb.append("ЧЕГО НЕ ДЕЛАТЬ:\n")
            sb.append(ai.avoid.trim())
        }
        if (ai.subjects.isNotBlank()) {
            sb.append("\n\n")
            sb.append("ПРЕДМЕТНЫЕ НЮАНСЫ:\n")
            sb.append(ai.subjects.trim())
        }
        if (extra.isNotBlank()) {
            sb.append("\n\n")
            sb.append(extra)
        }
        return sb.toString()
    }

    /** Промпты для генерации по теме (Шаг 8). */
    fun buildTopicPrompts(
        topic: String,
        count: Int,
        difficulties: Collection<String>,
        ai: AiInstructions
    ): Pair<String, String> {
        val distribution = if (difficulties.size > 1) {
            "Распредели вопросы по этим сложностям примерно поровну.\n"
        } else ""
        val user = "Составь ровно $count тестовых вопросов по теме «$topic».\n" +
            "Уровень сложности вопросов: ${difficultyText(difficulties)}.\n" +
            distribution +
            "Требования:\n" +
            "- у каждого вопроса ровно 4 варианта ответа, ровно один правильный;\n" +
            "- фактическая точность, однозначность, школьная программа;\n" +
            "- вопросы не должны повторяться.\n" +
            "Формат ответа (строго валидный JSON):\n" +
            "{\"questions\":[{\"text\":\"текст вопроса\",\"options\":" +
            "[\"вариант1\",\"вариант2\",\"вариант3\",\"вариант4\"]," +
            "\"correct\":0,\"difficulty\":\"medium\"}]}\n" +
            "correct — индекс правильного варианта от 0 до 3."
        return composeSystem(ai) to user
    }

    /** Промпты для генерации по тексту учебника (Шаг 10). */
    fun buildPdfPrompts(
        topic: String,
        count: Int,
        difficulties: Collection<String>,
        textbookText: String,
        ai: AiInstructions
    ): Pair<String, String> {
        val extra = "Используй ТОЛЬКО приведённый ниже текст учебника и указанную тему. " +
            "Не придумывай факты, которых нет в тексте. " +
            "Если в тексте недостаточно материала для $count вопросов — сделай сколько сможешь."
        val user = "Тема/класс: $topic\n" +
            "Количество вопросов: $count\n" +
            "Сложность: ${difficultyText(difficulties)}\n" +
            (if (difficulties.size > 1) "Распредели вопросы по этим сложностям примерно поровну.\n" else "") +
            "\n" +
            "Текст учебника:\n$textbookText\n\n" +
            "Верни строгий JSON: " +
            "[{\"text\": \"...\", \"options\": [\"...\", \"...\", \"...\", \"...\"], \"correct\": 0-3}, ...]"
        return composeSystem(ai, extra) to user
    }

    /** Запрос генерации с готовыми промптами. Возвращает текст ответа модели. */
    suspend fun generateRaw(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int
    ): GenerationText =
        withContext(Dispatchers.IO) {
            try {
                val request = ChatCompletionRequest(
                    model = modelId,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", userPrompt)
                    ),
                    max_tokens = maxTokens,
                    temperature = 0.7
                )
                val req = Request.Builder()
                    .url("$BASE/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(json.encodeToString(ChatCompletionRequest.serializer(), request)
                        .toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> {
                            val body = resp.body?.string()
                            if (body == null) {
                                GenerationText.Failed("Пустой ответ")
                            } else {
                                val content = json.decodeFromString<ChatCompletionResponse>(body)
                                    .choices.firstOrNull()?.message?.content.orEmpty()
                                if (content.isBlank()) GenerationText.Failed("Пустой ответ модели")
                                else GenerationText.Success(content)
                            }
                        }
                        resp.code == 429 -> GenerationText.RateLimited
                        resp.code == 404 -> GenerationText.Failed("модель не найдена (устарела)")
                        resp.code == 400 -> GenerationText.Failed("запрос не принят (400)")
                        resp.code == 401 || resp.code == 403 -> GenerationText.Failed("Неверный ключ")
                        else -> GenerationText.Failed("HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                if (e is java.net.SocketTimeoutException) {
                    GenerationText.Failed("превышено время ожидания")
                } else {
                    GenerationText.Failed("Нет соединения")
                }
            }
        }
}

data class SqueezeOutcome(val rules: List<String>?, val error: String?)

/**
 * Генерация вопросов по каскаду моделей: последняя рабочая модель
 * пробуется первой; при 429 — переход к следующей.
 */
object AiQuestionGenerator {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Модели, ответившие 404 в этой сессии: не пробуются до перезапуска приложения. */
    private val deadModels = mutableSetOf<String>()

    private const val SQUEEZE_SYSTEM_PROMPT =
        "Ты — помощник учителя. Ты сжимаешь список причин замен вопросов в короткие правила. " +
            "Отвечай только списком правил, каждое правило с новой строки, без нумерации и без markdown."

    fun parseQuestions(content: String): List<GeneratedQuestion> {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val candidates = mutableListOf<String>()
        val objStart = cleaned.indexOf('{')
        val objEnd = cleaned.lastIndexOf('}')
        if (objStart != -1 && objEnd > objStart) candidates.add(cleaned.substring(objStart, objEnd + 1))
        val arrStart = cleaned.indexOf('[')
        val arrEnd = cleaned.lastIndexOf(']')
        if (arrStart != -1 && arrEnd > arrStart) candidates.add(cleaned.substring(arrStart, arrEnd + 1))
        for (candidate in candidates) {
            val parsed = try {
                lenientJson.decodeFromString<GeneratedQuestions>(candidate).questions
            } catch (e: Exception) {
                try {
                    lenientJson.decodeFromString<List<GeneratedQuestion>>(candidate)
                } catch (e2: Exception) {
                    null
                }
            }
            if (!parsed.isNullOrEmpty()) {
                return parsed
                    .filter { it.text.isNotBlank() && it.options.count { o -> o.isNotBlank() } >= 4 }
                    .map { q ->
                        q.copy(options = q.options.take(4), correct = q.correct.coerceIn(0, 3))
                    }
            }
        }
        return emptyList()
    }

    /** Шаг 8: генерация по теме. */
    suspend fun generate(
        apiKey: String,
        settings: AppSettings,
        topic: String,
        count: Int,
        difficulties: Collection<String>,
        operation: String = "topic"
    ): GenerationOutcome = runCascade(apiKey, settings, count, difficulties, operation, topic) { n, d ->
        OpenRouterClient.buildTopicPrompts(topic, n, d, settings.toAiInstructions())
    }

    /** Шаг 10: генерация по тексту учебника. */
    suspend fun generateFromPdf(
        apiKey: String,
        settings: AppSettings,
        topic: String,
        count: Int,
        difficulties: Collection<String>,
        textbookText: String
    ): GenerationOutcome = runCascade(apiKey, settings, count, difficulties, "pdf", topic) { n, d ->
        OpenRouterClient.buildPdfPrompts(topic, n, d, textbookText, settings.toAiInstructions())
    }

    private suspend fun runCascade(
        apiKey: String,
        settings: AppSettings,
        count: Int,
        difficulties: Collection<String>,
        operation: String,
        requestPreview: String,
        prompts: (Int, Collection<String>) -> Pair<String, String>
    ): GenerationOutcome {
        val all = buildList {
            if (settings.lastWorking.isNotBlank()) add(settings.lastWorking)
            addAll(settings.cascadeOrder())
        }.distinct()
        // Модели, ответившие 404, не пробуем до перезапуска приложения.
        val order = all.filter { it !in deadModels }
        if (order.isEmpty()) {
            return GenerationOutcome(
                emptyList(), null,
                if (all.isNotEmpty()) {
                    "Все выбранные модели устарели. Зайдите в Настройки и выберите openrouter/auto"
                } else {
                    "Каскад моделей пуст — настройте его в Настройках"
                }
            )
        }
        val report = StringBuilder()
        for (model in order) {
            val (systemPrompt, userPrompt) = prompts(count, difficulties)
            when (
                val r = OpenRouterClient.generateRaw(
                    apiKey, model, systemPrompt, userPrompt,
                    (200 + count * 160).coerceAtMost(4000)
                )
            ) {
                is GenerationText.Success -> {
                    deadModels.remove(model)
                    val parsed = parseQuestions(r.text)
                    if (parsed.isNotEmpty()) {
                        val fixed = parsed
                            .map { q -> q.copy(difficulty = difficulties.random()) }
                            .take(count)
                        AiLogger.log(operation, "success", model, "${fixed.size} вопросов", requestPreview)
                        return GenerationOutcome(fixed, model, report.toString())
                    }
                    report.append("• ").append(model).append(": не удалось разобрать ответ\n")
                    AiLogger.log(operation, "error", model, "не удалось разобрать ответ", requestPreview)
                }
                is GenerationText.RateLimited -> {
                    report.append("• ").append(model).append(": лимит исчерпан (429)\n")
                    AiLogger.log(operation, "limit", model, "лимит запросов (429)", requestPreview)
                }
                is GenerationText.Failed -> {
                    if (r.message.contains("не найдена")) deadModels.add(model)
                    report.append("• ").append(model).append(": ").append(r.message).append('\n')
                    val status = when {
                        r.message.contains("не найдена") -> "not_found"
                        r.message.contains("время ожидания") -> "timeout"
                        else -> "error"
                    }
                    AiLogger.log(operation, status, model, r.message, requestPreview)
                }
            }
        }
        return GenerationOutcome(emptyList(), null, report.toString())
    }

    /** Шаг 12: сжать причины замен в короткие правила для avoid-инструкции. */
    suspend fun squeezeReasons(
        apiKey: String,
        settings: AppSettings,
        reasons: List<ReplacementReason>
    ): SqueezeOutcome {
        val order = buildList {
            if (settings.lastWorking.isNotBlank()) add(settings.lastWorking)
            addAll(settings.cascadeOrder())
        }.distinct().filter { it !in deadModels }
        if (order.isEmpty()) {
            return SqueezeOutcome(
                null,
                "Все выбранные модели устарели. Зайдите в Настройки и выберите openrouter/auto"
            )
        }
        val reasonsList = buildString {
            reasons.forEachIndexed { index, reason ->
                append("${index + 1}. ")
                append(reason.replacedQuestionText.take(120))
                append(" → ")
                append(reason.reason)
                append('\n')
            }
        }
        val userPrompt = "Ниже — список причин, по которым учитель заменял вопросы в тестах. " +
            "Сожми их в 10–30 КОРОТКИХ правил по одной строке. Каждое правило — про то, " +
            "чего НЕ надо делать при генерации вопросов. Формат ответа: только список правил, " +
            "каждое с новой строки, без нумерации, без markdown, без вводных фраз. " +
            "Объединяй похожие причины. Не выдумывай новых правил, которых нет в причинах.\n\n" +
            "Причины:\n$reasonsList"
        var lastError = ""
        for (model in order) {
            when (
                val r = OpenRouterClient.generateRaw(
                    apiKey, model, SQUEEZE_SYSTEM_PROMPT, userPrompt, 800
                )
            ) {
                is GenerationText.Success -> {
                    val rules = r.text.lines()
                        .map { it.trim().trimStart('-', '•', '*', ' ') }
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .distinct()
                    if (rules.isNotEmpty()) {
                        deadModels.remove(model)
                        AiLogger.log("squeeze", "success", model, "${rules.size} правил", reasonsList)
                        return SqueezeOutcome(rules, null)
                    }
                    lastError = "не удалось разобрать ответ"
                    AiLogger.log("squeeze", "error", model, lastError, reasonsList)
                }
                is GenerationText.RateLimited -> {
                    lastError = "лимит исчерпан (429)"
                    AiLogger.log("squeeze", "limit", model, "лимит запросов (429)", reasonsList)
                }
                is GenerationText.Failed -> {
                    if (r.message.contains("не найдена")) deadModels.add(model)
                    lastError = r.message
                    val status = when {
                        r.message.contains("не найдена") -> "not_found"
                        r.message.contains("время ожидания") -> "timeout"
                        else -> "error"
                    }
                    AiLogger.log("squeeze", status, model, r.message, reasonsList)
                }
            }
        }
        return SqueezeOutcome(null, lastError.ifBlank { "Не удалось обновить" })
    }
}
