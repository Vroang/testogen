package com.testogen.app.ai

import com.testogen.app.data.db.AiRequestDao
import com.testogen.app.data.db.AiRequestEntity
import com.testogen.app.data.prefs.KeysRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import kotlin.coroutines.resumeWithException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

enum class Provider(val title: String, val model: String) {
    DEEPSEEK("DeepSeek", "deepseek-flash"),
    OPENROUTER("OpenRouter", "deepseek/deepseek-v4.1-flash-1017"),
}

/** Запрос к нейросети. [system] у одного вида задач всегда одинаковый — так срабатывает кэш DeepSeek. */
data class ChatRequest(
    val operation: String,
    val system: String,
    val user: String,
    val maxTokens: Int,
    val temperature: Double = 0.7,
    val note: String? = null,
)

data class ChatResult(
    val content: String,
    val provider: Provider,
    val truncated: Boolean,
    val costUsd: Double,
)

/** Ошибка с понятным маме текстом. [retryable] — есть смысл попробовать позже. */
class AiException(message: String, val retryable: Boolean = false) : Exception(message)

/** Баланс на счёте провайдера (для проверки ключа в настройках). */
data class Balance(val provider: Provider, val text: String)

/**
 * Обращения к нейросети: сначала DeepSeek напрямую, при его недоступности — OpenRouter.
 * Режим размышлений выключен, ответ — JSON. Каждая попытка пишется в журнал с токенами и ценой.
 */
class AiClient(
    baseHttp: OkHttpClient,
    private val keys: KeysRepository,
    private val log: AiRequestDao,
) {
    private val http = baseHttp.newBuilder()
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    suspend fun hasAnyKey(): Boolean =
        keys.deepSeekKey.first().isNotBlank() || keys.openRouterKey.first().isNotBlank()

    suspend fun complete(request: ChatRequest): ChatResult {
        val deepSeek = keys.deepSeekKey.first()
        val openRouter = keys.openRouterKey.first()
        if (deepSeek.isBlank() && openRouter.isBlank()) {
            throw AiException("Не задан ключ нейросети. Откройте «Настройки» и вставьте ключ DeepSeek.")
        }
        var firstError: AiException? = null
        if (deepSeek.isNotBlank()) {
            try {
                return call(Provider.DEEPSEEK, deepSeek, request)
            } catch (e: AiException) {
                firstError = e
            }
        }
        if (openRouter.isNotBlank()) {
            try {
                return call(Provider.OPENROUTER, openRouter, request)
            } catch (e: AiException) {
                throw firstError?.let { AiException("${it.message}\nЗапасной OpenRouter тоже не помог: ${e.message}", it.retryable || e.retryable) } ?: e
            }
        }
        throw firstError!!
    }

    private suspend fun call(provider: Provider, key: String, request: ChatRequest): ChatResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val body = buildJsonObject {
                put("model", provider.model)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "system"); put("content", request.system) })
                    add(buildJsonObject { put("role", "user"); put("content", request.user) })
                }
                put("max_tokens", request.maxTokens)
                put("temperature", request.temperature)
                putJsonObject("response_format") { put("type", "json_object") }
                when (provider) {
                    Provider.DEEPSEEK -> putJsonObject("thinking") { put("type", "disabled") }
                    Provider.OPENROUTER -> {
                        putJsonObject("reasoning") { put("enabled", false) }
                        putJsonObject("usage") { put("include", true) }
                    }
                }
            }.toString()
            val url = when (provider) {
                Provider.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
                Provider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            }
            val httpRequest = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .apply {
                    if (provider == Provider.OPENROUTER) {
                        header("HTTP-Referer", "https://github.com/Vroang/testogen")
                        header("X-Title", "TestoGen")
                    }
                }
                .post(body.toRequestBody(mediaType))
                .build()

            fun logError(message: String) = AiRequestEntity(
                startedAt = started,
                durationMs = System.currentTimeMillis() - started,
                provider = provider.name.lowercase(),
                model = provider.model,
                operation = request.operation,
                status = "error",
                message = message,
            )

            val responseText: String
            val code: Int
            try {
                // При отмене задания запрос к нейросети обрывается сразу, а не доезжает до конца
                http.newCall(httpRequest).await().use { response ->
                    code = response.code
                    responseText = response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                val error = networkError(e)
                log.insert(logError(error.message.orEmpty()))
                throw error
            }

            if (code !in 200..299) {
                val error = httpError(provider, code, responseText)
                log.insert(logError(error.message.orEmpty()))
                throw error
            }

            val root = try {
                json.parseToJsonElement(responseText).jsonObject
            } catch (e: Exception) {
                val error = AiException("${provider.title} прислал непонятный ответ", retryable = true)
                log.insert(logError(error.message.orEmpty()))
                throw error
            }
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val content = choice?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            val finish = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
            val usage = root["usage"]?.jsonObject
            val promptTokens = usage.int("prompt_tokens")
            val completionTokens = usage.int("completion_tokens")
            val cacheHit = usage?.get("prompt_cache_hit_tokens")?.jsonPrimitive?.intOrNull
                ?: usage?.get("prompt_tokens_details")?.jsonObject.int("cached_tokens")
            val cost = usage?.get("cost")?.jsonPrimitive?.doubleOrNull
                ?: Pricing.costUsd(cacheHit, (promptTokens - cacheHit).coerceAtLeast(0), completionTokens, started)

            val problem = when {
                finish == "content_filter" -> "Нейросеть отказалась отвечать на эту тему"
                finish == "insufficient_system_resource" -> "${provider.title} перегружен, попробуйте позже"
                content.isBlank() -> "${provider.title} прислал пустой ответ"
                else -> null
            }
            log.insert(
                AiRequestEntity(
                    startedAt = started,
                    durationMs = System.currentTimeMillis() - started,
                    provider = provider.name.lowercase(),
                    model = provider.model,
                    operation = request.operation,
                    status = if (problem == null) "ok" else "error",
                    promptTokens = promptTokens,
                    cachedTokens = cacheHit,
                    completionTokens = completionTokens,
                    costUsd = cost,
                    message = problem ?: if (finish == "length") "ответ оборван по лимиту" else request.note,
                ),
            )
            if (problem != null) throw AiException(problem, retryable = finish == "insufficient_system_resource")
            ChatResult(content, provider, truncated = finish == "length", costUsd = cost)
        }

    /** Проверка ключа: баланс на счёте. null — ключ не задан. */
    suspend fun balance(provider: Provider): Balance? = withContext(Dispatchers.IO) {
        val key = when (provider) {
            Provider.DEEPSEEK -> keys.deepSeekKey.first()
            Provider.OPENROUTER -> keys.openRouterKey.first()
        }
        if (key.isBlank()) return@withContext null
        val url = when (provider) {
            Provider.DEEPSEEK -> "https://api.deepseek.com/user/balance"
            Provider.OPENROUTER -> "https://openrouter.ai/api/v1/credits"
        }
        var (code, text) = get(url, key)
        if (provider == Provider.OPENROUTER && code in 401..403) {
            // Не у всех ключей OpenRouter есть доступ к балансу — тогда просто проверяем сам ключ
            val (keyCode, keyText) = get("https://openrouter.ai/api/v1/key", key)
            if (keyCode in 200..299) return@withContext Balance(provider, "ключ работает")
            code = keyCode
            text = keyText
        }
        if (code !in 200..299) throw httpError(provider, code, text)
        val root = json.parseToJsonElement(text).jsonObject
        val value = when (provider) {
            Provider.DEEPSEEK -> {
                val info = root["balance_infos"]?.jsonArray?.firstOrNull()?.jsonObject
                val amount = info?.get("total_balance")?.jsonPrimitive?.contentOrNull
                val currency = info?.get("currency")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (amount == null) "ключ работает" else "на счёте $amount ${if (currency == "USD") "$" else currency}"
            }
            Provider.OPENROUTER -> {
                val data = root["data"]?.jsonObject
                val total = data?.get("total_credits")?.jsonPrimitive?.double ?: 0.0
                val used = data?.get("total_usage")?.jsonPrimitive?.double ?: 0.0
                "на счёте ${"%.2f".format(total - used)} $"
            }
        }
        Balance(provider, value)
    }

    private fun get(url: String, key: String): Pair<Int, String> {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
        return try {
            http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: IOException) {
            throw networkError(e)
        }
    }

    private fun networkError(e: IOException): AiException = when (e) {
        is UnknownHostException -> AiException("Нет подключения к интернету", retryable = true)
        is InterruptedIOException -> AiException("Нейросеть слишком долго не отвечает", retryable = true)
        is SSLException -> AiException("Не удалось установить защищённое соединение", retryable = true)
        else -> AiException("Связь с нейросетью прервалась", retryable = true)
    }

    private fun httpError(provider: Provider, code: Int, body: String): AiException {
        val name = provider.title
        val detail = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.take(160)
        return when (code) {
            401, 403 -> AiException("Ключ $name не подходит — проверьте его в настройках")
            402 -> AiException("На счёте $name закончились деньги — пополните баланс")
            429 -> AiException("$name просит подождать: слишком много запросов. Попробуйте через минуту", retryable = true)
            400, 422 -> AiException("$name не принял запрос" + (detail?.let { ": $it" } ?: ""))
            500, 502, 503, 504 -> AiException("Сервер $name перегружен или сломался, попробуйте позже", retryable = true)
            else -> AiException("$name ответил ошибкой $code" + (detail?.let { ": $it" } ?: ""), retryable = code >= 500)
        }
    }

    /** Отменяемый вызов OkHttp: отмена корутины отменяет и HTTP-запрос. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, value, _ -> value.close() }
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            },
        )
    }

    private fun JsonObject?.int(name: String): Int = this?.get(name)?.jsonPrimitive?.intOrNull ?: 0
}
