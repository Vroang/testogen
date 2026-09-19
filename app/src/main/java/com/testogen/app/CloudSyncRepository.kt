package com.testogen.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Шаг 32: обратная синхронизация с подтверждением.
// Телефон — мастер: push уходит тихо, а всё, что пришло из облака
// (веб-версии), сначала показывается пользователю списком и
// применяется только после подтверждения.

data class QuestionPreview(val id: String, val text: String)
data class QuestionDiff(val id: String, val oldText: String, val newText: String)
data class TextbookPreview(val id: String, val name: String)

data class CloudChanges(
    val newQuestions: List<QuestionPreview> = emptyList(),
    val updatedQuestions: List<QuestionDiff> = emptyList(),
    val deletedQuestions: List<QuestionPreview> = emptyList(),
    val newTextbooks: List<TextbookPreview> = emptyList(),
    val deletedTextbooks: List<TextbookPreview> = emptyList(),
    val newReasons: Int = 0,
    val updatedReasons: Int = 0,
    val deletedReasons: Int = 0
) {
    val totalChanges: Int
        get() = newQuestions.size + updatedQuestions.size + deletedQuestions.size +
            newTextbooks.size + deletedTextbooks.size + newReasons + updatedReasons + deletedReasons

    val isEmpty: Boolean get() = totalChanges == 0

    /** Массовое удаление — требуем особого предупреждения. */
    val isMassDeletion: Boolean get() = deletedQuestions.size >= 10
}

/** Состояние для UI: диалог, индикатор, отложенный набор. */
object CloudSyncState {
    var pendingChanges by mutableStateOf<CloudChanges?>(null)
    var isSyncing by mutableStateOf(false)
    var lastDeclinedHash by mutableStateOf("")
}

object CloudSyncRepository {

    private fun db(context: Context): AppDatabase =
        (context.applicationContext as TestoGenApp).database

    private fun settingsRepo(context: Context): SettingsRepository =
        (context.applicationContext as TestoGenApp).settingsRepository

    sealed class SyncOutcome {
        object UpToDate : SyncOutcome()
        object Declined : SyncOutcome()
        data class NeedsConfirmation(val changes: CloudChanges) : SyncOutcome()
        data class Error(val message: String) : SyncOutcome()
    }

    /**
     * 1) тихий push локальных изменений; 2) обнаружение облачных
     * изменений; 3) при showPrompt — диалог через CloudSyncState.
     */
    suspend fun syncWithCloud(
        context: Context,
        showPrompt: Boolean = true,
        ignoreDeclinedHash: Boolean = false
    ): SyncOutcome {
        if (CloudSyncState.isSyncing) return SyncOutcome.UpToDate
        CloudSyncState.isSyncing = true
        try {
            // 1. Push — тихо, без уведомлений.
            TextbookRepository.syncAllPending(context)
            QuestionSyncRepository.syncAll(context)

            // 2. Pull-обнаружение.
            val changes = detectCloudChanges(context).getOrElse { e ->
                return SyncOutcome.Error(e.message ?: "Ошибка синхронизации")
            }
            settingsRepo(context).setLastSyncTimestamp(System.currentTimeMillis())

            if (changes.isEmpty) {
                // Всё сошлось — сбрасываем отложенный набор.
                settingsRepo(context).setPendingChangesHash("")
                return SyncOutcome.UpToDate
            }

            // 3. Не отменял ли пользователь уже этот набор?
            val hash = changes.hashCode().toString()
            val declined = settingsRepo(context).getPendingChangesHash()
            if (!ignoreDeclinedHash && declined.isNotEmpty() && declined == hash) {
                CloudSyncState.lastDeclinedHash = hash
                return SyncOutcome.Declined
            }

            if (showPrompt) {
                CloudSyncState.pendingChanges = changes
            }
            return SyncOutcome.NeedsConfirmation(changes)
        } finally {
            CloudSyncState.isSyncing = false
        }
    }

    /** Шаг 32: обнаружение расхождений «облако vs телефон». */
    suspend fun detectCloudChanges(context: Context): Result<CloudChanges> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.requireSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
                val database = db(context)
                val queue = database.pendingDeleteDao().getAllOnce()
                val queuedQuestionIds = queue.filter { it.type == "question" }.map { it.cloudId }.toSet()
                val queuedTextbookIds = queue.filter { it.type == "textbook" }.map { it.textbookId }.toSet()
                val queuedReasonIds = queue.filter { it.type == "reason" }.map { it.cloudId }.toSet()

                // Вопросы
                val localQuestions = database.questionDao().getAllOnce()
                val localQByCloud =
                    localQuestions.filter { it.cloudId != null }.associateBy { it.cloudId!! }
                val cloudQuestions = SupabaseClient.client.postgrest.from("questions")
                    .select { }
                    .decodeList<QuestionRow>()

                val newQuestions = mutableListOf<QuestionPreview>()
                val updatedQuestions = mutableListOf<QuestionDiff>()
                for (row in cloudQuestions) {
                    if (row.id in queuedQuestionIds) continue
                    val local = localQByCloud[row.id]
                    if (local == null) {
                        newQuestions.add(QuestionPreview(row.id, row.text.take(80)))
                    } else {
                        if (local.syncStatus == "pending_upload") continue
                        val differs =
                            local.text != row.text ||
                                local.optionA != row.optionA.orEmpty() ||
                                local.optionB != row.optionB.orEmpty() ||
                                local.optionC != row.optionC.orEmpty() ||
                                local.optionD != row.optionD.orEmpty() ||
                                local.correctIndex != (row.correctIndex ?: 0) ||
                                local.difficulty != (row.difficulty ?: "medium") ||
                                local.tricky != row.tricky ||
                                local.topic != row.topic.orEmpty()
                        if (differs && local.syncStatus == "synced") {
                            updatedQuestions.add(
                                QuestionDiff(row.id, local.text.take(80), row.text.take(80))
                            )
                        }
                    }
                }
                val deletedQuestions = mutableListOf<QuestionPreview>()
                val cloudQuestionIds = cloudQuestions.map { it.id }.toSet()
                for (local in localQuestions) {
                    val cid = local.cloudId ?: continue
                    if (cid in queuedQuestionIds) continue
                    if (cid !in cloudQuestionIds && local.syncStatus == "synced") {
                        deletedQuestions.add(QuestionPreview(cid, local.text.take(80)))
                    }
                }

                // Учебники (новые/удалённые)
                val localBooks = database.textbookDao().getAllOnce()
                val localBookById = localBooks.associateBy { it.id }
                val cloudTextbooks = SupabaseClient.client.postgrest.from("textbooks")
                    .select { }
                    .decodeList<TextbookRow>()
                val newTextbooks = mutableListOf<TextbookPreview>()
                for (row in cloudTextbooks) {
                    if (row.id in queuedTextbookIds) continue
                    if (localBookById[row.id] == null) {
                        newTextbooks.add(TextbookPreview(row.id, row.name))
                    }
                }
                val deletedTextbooks = mutableListOf<TextbookPreview>()
                val cloudTextbookIds = cloudTextbooks.map { it.id }.toSet()
                for (book in localBooks) {
                    if (book.id in queuedTextbookIds) continue
                    if (book.id !in cloudTextbookIds && book.syncStatus == "synced") {
                        deletedTextbooks.add(TextbookPreview(book.id, book.name))
                    }
                }

                // Причины замен (счётчики)
                val localReasons = database.replacementReasonDao().getAllOnce()
                val localRByCloud =
                    localReasons.filter { it.cloudId != null }.associateBy { it.cloudId!! }
                val cloudReasons = SupabaseClient.client.postgrest.from("replacement_reasons")
                    .select { }
                    .decodeList<ReasonRow>()
                var newReasons = 0
                var updatedReasons = 0
                for (row in cloudReasons) {
                    if (row.id in queuedReasonIds) continue
                    val local = localRByCloud[row.id]
                    if (local == null) {
                        newReasons++
                    } else if (local.reason != row.reason && local.syncStatus == "synced") {
                        updatedReasons++
                    }
                }
                var deletedReasons = 0
                val cloudReasonIds = cloudReasons.map { it.id }.toSet()
                for (local in localReasons) {
                    val cid = local.cloudId ?: continue
                    if (cid in queuedReasonIds) continue
                    if (cid !in cloudReasonIds && local.syncStatus == "synced") deletedReasons++
                }

                Result.success(
                    CloudChanges(
                        newQuestions = newQuestions,
                        updatedQuestions = updatedQuestions,
                        deletedQuestions = deletedQuestions,
                        newTextbooks = newTextbooks,
                        deletedTextbooks = deletedTextbooks,
                        newReasons = newReasons,
                        updatedReasons = updatedReasons,
                        deletedReasons = deletedReasons
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Применить подтверждённые изменения: новые — создать,
     * изменённые — обновить, удалённые — снести локально.
     * Возвращает Triple: создано / обновлено / удалено (вопросы).
     */
    suspend fun applyCloudChanges(context: Context, changes: CloudChanges): Result<Triple<Int, Int, Int>> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.requireSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
                val database = db(context)
                var added = 0
                var updated = 0
                var deleted = 0

                val needQuestionRows =
                    changes.newQuestions.isNotEmpty() || changes.updatedQuestions.isNotEmpty()
                val cloudQuestionsById = if (needQuestionRows) {
                    SupabaseClient.client.postgrest.from("questions")
                        .select { }
                        .decodeList<QuestionRow>()
                        .associateBy { it.id }
                } else {
                    emptyMap()
                }

                for (preview in changes.newQuestions) {
                    val row = cloudQuestionsById[preview.id] ?: continue
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
                            createdAt = QuestionSyncRepository.parseInstant(row.createdAt),
                            source = row.source ?: "manual",
                            topic = row.topic.orEmpty(),
                            cloudId = row.id,
                            syncStatus = "synced"
                        )
                    )
                    added++
                }
                if (changes.updatedQuestions.isNotEmpty()) {
                    val localAll = database.questionDao().getAllOnce()
                    val localByCloudId = localAll.filter { it.cloudId != null }.associateBy { it.cloudId!! }
                    for (diff in changes.updatedQuestions) {
                        val row = cloudQuestionsById[diff.id] ?: continue
                        val local = localByCloudId[diff.id] ?: continue
                        database.questionDao().update(
                            local.copy(
                                text = row.text,
                                optionA = row.optionA.orEmpty(),
                                optionB = row.optionB.orEmpty(),
                                optionC = row.optionC.orEmpty(),
                                optionD = row.optionD.orEmpty(),
                                correctIndex = row.correctIndex ?: 0,
                                difficulty = row.difficulty?.ifBlank { "medium" } ?: "medium",
                                tricky = row.tricky,
                                topic = row.topic.orEmpty(),
                                syncStatus = "synced"
                            )
                        )
                        updated++
                    }
                }
                if (changes.deletedQuestions.isNotEmpty()) {
                    val ids = changes.deletedQuestions.map { it.id }.toSet()
                    database.questionDao().getAllOnce()
                        .filter { it.cloudId != null && it.cloudId in ids }
                        .forEach { question ->
                            database.questionDao().delete(question)
                            deleted++
                        }
                }

                // Причины замен: новые/изменённые/удалённые
                val needReasonRows =
                    changes.newReasons > 0 || changes.updatedReasons > 0 || changes.deletedReasons > 0
                if (needReasonRows) {
                    val cloudReasonsById = SupabaseClient.client.postgrest.from("replacement_reasons")
                        .select { }
                        .decodeList<ReasonRow>()
                        .associateBy { it.id }
                    val localReasons = database.replacementReasonDao().getAllOnce()
                    val localReasonByCloudId =
                        localReasons.filter { it.cloudId != null }.associateBy { it.cloudId!! }
                    for (row in cloudReasonsById.values) {
                        val local = localReasonByCloudId[row.id]
                        if (local == null) {
                            database.replacementReasonDao().insert(
                                ReplacementReason(
                                    replacedQuestionText = row.replacedQuestionText.orEmpty(),
                                    reason = row.reason,
                                    timestamp = QuestionSyncRepository.parseInstant(row.createdAt),
                                    cloudId = row.id,
                                    syncStatus = "synced"
                                )
                            )
                        } else if (local.reason != row.reason && local.syncStatus == "synced") {
                            database.replacementReasonDao().update(
                                local.copy(reason = row.reason, syncStatus = "synced")
                            )
                        }
                    }
                    val cloudReasonIds = cloudReasonsById.keys.toSet()
                    localReasons.filter {
                        it.cloudId != null && it.cloudId !in cloudReasonIds && it.syncStatus == "synced"
                    }.forEach { local ->
                        database.replacementReasonDao().deleteById(local.id)
                    }
                }

                // Учебники: новые — восстановить, удалённые — снести локально
                if (changes.newTextbooks.isNotEmpty()) {
                    val cloudTextbooksById = SupabaseClient.client.postgrest.from("textbooks")
                        .select { }
                        .decodeList<TextbookRow>()
                        .associateBy { it.id }
                    for (preview in changes.newTextbooks) {
                        val row = cloudTextbooksById[preview.id] ?: continue
                        TextbookRepository.restoreCloudTextbook(context, row)
                    }
                }
                if (changes.deletedTextbooks.isNotEmpty()) {
                    val ids = changes.deletedTextbooks.map { it.id }.toSet()
                    database.textbookDao().getAllOnce()
                        .filter { it.id in ids }
                        .forEach { book ->
                            database.paragraphDao().deleteByTextbook(book.id)
                            book.localCachePath?.let { path -> java.io.File(path).delete() }
                            database.textbookDao().delete(book.id)
                        }
                }

                settingsRepo(context).setLastSyncTimestamp(System.currentTimeMillis())
                settingsRepo(context).setPendingChangesHash("")
                Result.success(Triple(added, updated, deleted))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
