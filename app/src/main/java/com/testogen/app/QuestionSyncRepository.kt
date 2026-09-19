package com.testogen.app

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// Шаг 31: local-first синхронизация вопросов и причин замен.
// Вопросы/причины сохраняются в Room сразу; в Supabase уходят
// фоном; при отсутствии сети — позже (syncStatus/pending_deletes).

@Serializable
private data class QuestionRow(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    val text: String = "",
    @SerialName("option_a") val optionA: String? = null,
    @SerialName("option_b") val optionB: String? = null,
    @SerialName("option_c") val optionC: String? = null,
    @SerialName("option_d") val optionD: String? = null,
    @SerialName("correct_index") val correctIndex: Int? = 0,
    val difficulty: String? = "medium",
    val tricky: Boolean = false,
    val topic: String? = "",
    val source: String? = "manual",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class ReasonRow(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("replaced_question_text") val replacedQuestionText: String? = null,
    val reason: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

object QuestionSyncRepository {

    @Volatile
    private var lastAutoPullAt = 0L

    private fun db(context: Context): AppDatabase =
        (context.applicationContext as TestoGenApp).database

    /** Полный проход: очередь удалений + ожидающие вопросы и причины. */
    suspend fun syncAll(context: Context) {
        try {
            syncPendingDeletes(context)
            syncPendingQuestions(context)
            syncPendingReasons(context)
        } catch (e: Exception) {
            // Нет сети — останется pending и уйдёт при следующей попытке.
        }
    }

    suspend fun syncPendingQuestions(context: Context) {
        if (!AuthManager.requireSignedIn()) return
        val user = AuthManager.currentUserAsync() ?: return
        val database = db(context)
        val pending = database.questionDao().getPendingUpload()
        for (q in pending) {
            try {
                val cloudId = q.cloudId ?: UUID.randomUUID().toString()
                SupabaseClient.client.postgrest.from("questions").upsert(
                    QuestionRow(
                        id = cloudId,
                        userId = user.id,
                        text = q.text,
                        optionA = q.optionA,
                        optionB = q.optionB,
                        optionC = q.optionC,
                        optionD = q.optionD,
                        correctIndex = q.correctIndex,
                        difficulty = q.difficulty,
                        tricky = q.tricky,
                        topic = q.topic,
                        source = q.source,
                        createdAt = java.time.Instant.ofEpochMilli(q.createdAt).toString()
                    )
                )
                database.questionDao().update(q.copy(cloudId = cloudId, syncStatus = "synced"))
            } catch (e: Exception) {
                // Оставляем pending до следующей попытки.
            }
        }
    }

    suspend fun syncPendingReasons(context: Context) {
        if (!AuthManager.requireSignedIn()) return
        val user = AuthManager.currentUserAsync() ?: return
        val database = db(context)
        val pending = database.replacementReasonDao().getPendingUpload()
        for (reason in pending) {
            try {
                val cloudId = reason.cloudId ?: UUID.randomUUID().toString()
                SupabaseClient.client.postgrest.from("replacement_reasons").upsert(
                    ReasonRow(
                        id = cloudId,
                        userId = user.id,
                        replacedQuestionText = reason.replacedQuestionText,
                        reason = reason.reason,
                        createdAt = java.time.Instant.ofEpochMilli(reason.timestamp).toString()
                    )
                )
                database.replacementReasonDao().update(
                    reason.copy(cloudId = cloudId, syncStatus = "synced")
                )
            } catch (e: Exception) {
                // Оставляем pending до следующей попытки.
            }
        }
    }

    private suspend fun syncPendingDeletes(context: Context) {
        if (!AuthManager.requireSignedIn()) return
        val database = db(context)
        val items = database.pendingDeleteDao().getAllOnce()
        for (item in items) {
            try {
                when (item.type) {
                    "question" -> {
                        if (item.cloudId.isNotBlank()) {
                            SupabaseClient.client.postgrest.from("questions").delete {
                                filter { eq("id", item.cloudId) }
                            }
                        }
                        database.pendingDeleteDao().deleteById(item.id)
                    }
                    "reason" -> {
                        if (item.cloudId.isNotBlank()) {
                            SupabaseClient.client.postgrest.from("replacement_reasons").delete {
                                filter { eq("id", item.cloudId) }
                            }
                        }
                        database.pendingDeleteDao().deleteById(item.id)
                    }
                    // "textbook" обрабатывает TextbookRepository.
                }
            } catch (e: Exception) {
                // Оставляем в очереди до следующей попытки.
            }
        }
    }

    /** Локальное удаление вопроса + постановка в очередь на облако. */
    suspend fun onQuestionDeleted(context: Context, question: Question) {
        val database = db(context)
        if (question.cloudId != null) {
            database.pendingDeleteDao().insert(
                PendingDelete(
                    textbookId = "",
                    storagePath = "",
                    userId = "",
                    createdAt = System.currentTimeMillis(),
                    type = "question",
                    cloudId = question.cloudId ?: ""
                )
            )
        }
        database.questionDao().delete(question)
        syncPendingDeletes(context)
    }

    /** Полная очистка банка: очередь на все облачные вопросы. */
    suspend fun onAllQuestionsDeleted(context: Context) {
        val database = db(context)
        val all = database.questionDao().getAllOnce()
        val now = System.currentTimeMillis()
        all.filter { it.cloudId != null }.forEach { question ->
            database.pendingDeleteDao().insert(
                PendingDelete(
                    textbookId = "",
                    storagePath = "",
                    userId = "",
                    createdAt = now,
                    type = "question",
                    cloudId = question.cloudId ?: ""
                )
            )
        }
        database.questionDao().deleteAll()
        syncPendingDeletes(context)
    }

    /** Очистка причин замен: очередь на облачные записи. */
    suspend fun onReasonsCleared(context: Context) {
        val database = db(context)
        val all = database.replacementReasonDao().getAllOnce()
        val now = System.currentTimeMillis()
        all.filter { it.cloudId != null }.forEach { reason ->
            database.pendingDeleteDao().insert(
                PendingDelete(
                    textbookId = "",
                    storagePath = "",
                    userId = "",
                    createdAt = now,
                    type = "reason",
                    cloudId = reason.cloudId ?: ""
                )
            )
        }
        database.replacementReasonDao().deleteAll()
        syncPendingDeletes(context)
    }

    /**
     * Шаг 31.1: подтянуть вопросы и причины из облака (например,
     * созданные в веб-версии). Без дублей: сначала по cloudId,
     * затем по совпадению текста (связываем с локальной записью).
     * Возвращает Pair(новых вопросов, новых причин).
     */
    suspend fun pullQuestionsAndReasons(context: Context): Result<Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.requireSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
                val database = db(context)
                var newQuestions = 0
                var newReasons = 0

                val localQuestions = database.questionDao().getAllOnce()
                val questionsByCloudId =
                    localQuestions.filter { it.cloudId != null }.associateBy { it.cloudId }
                val cloudQuestions = SupabaseClient.client.postgrest.from("questions")
                    .select { }
                    .decodeList<QuestionRow>()
                for (row in cloudQuestions) {
                    if (questionsByCloudId.containsKey(row.id)) continue
                    val textMatch = localQuestions.firstOrNull {
                        it.text == row.text && it.correctIndex == (row.correctIndex ?: 0)
                    }
                    if (textMatch != null) {
                        // Уже есть локально (возможно, ещё не отправлен) —
                        // связываем с облаком, чтобы не создавать дубль.
                        if (textMatch.cloudId == null) {
                            database.questionDao().update(textMatch.copy(cloudId = row.id))
                        }
                        continue
                    }
                    database.questionDao().insert(
                        Question(
                            text = row.text,
                            optionA = row.optionA.orEmpty(),
                            optionB = row.optionB.orEmpty(),
                            optionC = row.optionC.orEmpty(),
                            optionD = row.optionD.orEmpty(),
                            correctIndex = row.correctIndex ?: 0,
                            difficulty = row.difficulty?.ifBlank { "medium" } ?: "medium",
                            tricky = row.tricky,
                            createdAt = parseInstant(row.createdAt),
                            source = row.source ?: "manual",
                            topic = row.topic.orEmpty(),
                            cloudId = row.id,
                            syncStatus = "synced"
                        )
                    )
                    newQuestions++
                }

                val localReasons = database.replacementReasonDao().getAllOnce()
                val reasonsByCloudId =
                    localReasons.filter { it.cloudId != null }.associateBy { it.cloudId }
                val cloudReasons = SupabaseClient.client.postgrest.from("replacement_reasons")
                    .select { }
                    .decodeList<ReasonRow>()
                for (row in cloudReasons) {
                    if (reasonsByCloudId.containsKey(row.id)) continue
                    val textMatch = localReasons.firstOrNull { it.reason == row.reason }
                    if (textMatch != null) {
                        if (textMatch.cloudId == null) {
                            database.replacementReasonDao().update(textMatch.copy(cloudId = row.id))
                        }
                        continue
                    }
                    database.replacementReasonDao().insert(
                        ReplacementReason(
                            replacedQuestionText = row.replacedQuestionText.orEmpty(),
                            reason = row.reason,
                            timestamp = parseInstant(row.createdAt),
                            cloudId = row.id,
                            syncStatus = "synced"
                        )
                    )
                    newReasons++
                }
                Result.success(newQuestions to newReasons)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    /** Шаг 31.1: восстановление всего — учебники + вопросы + причины. */
    suspend fun restoreAll(context: Context): Result<Triple<Int, Int, Int>> {
        val textbookResult = TextbookRepository.restoreFromCloud(context)
        val restoredTextbooks = textbookResult.getOrNull()
            ?: return Result.failure(
                textbookResult.exceptionOrNull() ?: Exception("Не удалось восстановить")
            )
        val pullResult = pullQuestionsAndReasons(context)
        val pulled = pullResult.getOrNull()
            ?: return Result.failure(
                pullResult.exceptionOrNull() ?: Exception("Не удалось восстановить")
            )
        return Result.success(Triple(restoredTextbooks, pulled.first, pulled.second))
    }

    /** Шаг 31.1: автоподтягивание при старте — не чаще раза в 5 минут. */
    suspend fun pullAllIfStale(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAutoPullAt < 5 * 60_000L) return
        lastAutoPullAt = now
        runCatching { pullQuestionsAndReasons(context) }
    }

    private fun parseInstant(value: String?): Long =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())

    private fun mapError(e: Exception): Exception {
        val raw = e.message ?: ""
        return when {
            raw.contains("row-level security", ignoreCase = true) ->
                Exception(
                    "Supabase заблокировал операцию (RLS). Выполните SQL-скрипт из инструкции шага 31 в SQL Editor и повторите."
                )
            else -> e
        }
    }
}
