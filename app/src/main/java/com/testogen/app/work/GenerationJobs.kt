package com.testogen.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.testogen.app.MainActivity
import com.testogen.app.R
import com.testogen.app.TestoGenApp
import com.testogen.app.ai.AiException
import com.testogen.app.ai.GenerationProgress
import com.testogen.app.ai.TopicJobParams
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.GenerationJobEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Создание, запуск и отмена заданий генерации. Задание хранится в базе, работа — в WorkManager. */
class GenerationJobs(private val context: Context, private val db: AppDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observe(id: Long): Flow<GenerationJobEntity?> = db.jobs().observe(id)

    fun observeActive(): Flow<List<GenerationJobEntity>> = db.jobs().observeActive()

    suspend fun startTopic(params: TopicJobParams): Long {
        val now = System.currentTimeMillis()
        val id = db.jobs().insert(
            GenerationJobEntity(
                kind = KIND_TOPIC,
                status = "queued",
                params = json.encodeToString(TopicJobParams.serializer(), params),
                title = params.topic,
                total = params.total,
                stage = "Готовлю задание…",
                createdAt = now,
                updatedAt = now,
            ),
        )
        enqueue(id, ExistingWorkPolicy.REPLACE)
        return id
    }

    /** Продолжить упавшее или прерванное задание с того места, где оно остановилось. */
    suspend fun retry(id: Long) {
        db.jobs().setStatus(id, "queued", "Готовлю задание…", null)
        enqueue(id, ExistingWorkPolicy.REPLACE)
    }

    suspend fun cancel(id: Long) {
        db.jobs().setStatus(id, "cancelled", "Остановлено", null)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    /** При запуске приложения: незавершённые задания снова ставятся в очередь (если их там уже нет). */
    suspend fun resumeUnfinished() {
        db.jobs().deleteFinishedBefore(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        db.jobs().active().forEach { enqueue(it.id, ExistingWorkPolicy.KEEP) }
    }

    private fun enqueue(id: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<GenerationWorker>()
            .setInputData(workDataOf(KEY_JOB to id))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), policy, request)
    }

    companion object {
        const val KIND_TOPIC = "topic"
        const val KEY_JOB = "job"
        fun workName(id: Long) = "generation-$id"
    }
}

/** Выполняет задание генерации в фоне с уведомлением о ходе работы. */
class GenerationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val container = (context.applicationContext as TestoGenApp).container
    private val jobId = params.inputData.getLong(GenerationJobs.KEY_JOB, -1)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val db = container.database
        val job = db.jobs().get(jobId) ?: return Result.success()
        if (job.status == "done" || job.status == "cancelled") return Result.success()

        runCatching { setForeground(foregroundInfo(job.title, job.saved, job.total)) }
        db.jobs().setStatus(jobId, "running", "Готовлю задание…", null)

        val progress = object : GenerationProgress {
            override suspend fun stage(text: String) {
                db.jobs().setStage(jobId, text)
            }

            override suspend fun saved(totalSaved: Int, dropped: Int, costUsd: Double) {
                db.jobs().addProgress(jobId, totalSaved, dropped, costUsd)
                runCatching { setForeground(foregroundInfo(job.title, totalSaved, job.total)) }
            }
        }

        return try {
            when (job.kind) {
                GenerationJobs.KIND_TOPIC -> container.generator.runTopic(
                    jobId,
                    json.decodeFromString(TopicJobParams.serializer(), job.params),
                    progress,
                )
                else -> error("Неизвестный вид задания: ${job.kind}")
            }
            db.jobs().setStatus(jobId, "done", "Готово", null)
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                // Отмена мамой — «cancelled» уже записано; остановка системой — задание продолжится позже
                if (db.jobs().get(jobId)?.status == "running") db.jobs().setStatus(jobId, "queued", "Ждёт продолжения…", null)
            }
            throw e
        } catch (e: AiException) {
            db.jobs().setStatus(jobId, "failed", "Ошибка", e.message)
            Result.failure()
        } catch (e: Exception) {
            db.jobs().setStatus(jobId, "failed", "Ошибка", "Что-то пошло не так: ${e.message ?: e.javaClass.simpleName}")
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val job = container.database.jobs().get(jobId)
        return foregroundInfo(job?.title.orEmpty(), job?.saved ?: 0, job?.total ?: 0)
    }

    private fun foregroundInfo(title: String, saved: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Создание вопросов", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Создаю вопросы: $title")
            .setContentText(if (total > 0) "Готово $saved из $total" else "Готовлю задание…")
            .setProgress(total.coerceAtLeast(1), saved, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        val id = NOTIFICATION_BASE + jobId.toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private companion object {
        const val CHANNEL = "generation"
        const val NOTIFICATION_BASE = 1000
    }
}
