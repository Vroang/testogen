package com.testogen.app

import android.content.Context
import com.testogen.app.ai.AiClient
import com.testogen.app.ai.QuestionGenerator
import com.testogen.app.ai.RulesUpdater
import com.testogen.app.data.QuestionRepository
import com.testogen.app.data.TestRepository
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.prefs.AppPrefs
import com.testogen.app.data.prefs.KeysRepository
import com.testogen.app.textbook.TextbookImporter
import com.testogen.app.update.Updater
import com.testogen.app.work.GenerationJobs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.testogen.app.tutorial.TutorialVideo
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Все долгоживущие объекты приложения (ручное внедрение зависимостей). */
class AppContainer(context: Context) {
    /** Область для работы, которая не должна обрываться вместе с экраном. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(context) }

    val keys = KeysRepository(context)

    val prefs = AppPrefs(context)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val updater: Updater by lazy { Updater(context, http, appScope) }

    val tutorial: TutorialVideo by lazy { TutorialVideo(context, http, appScope) }

    val ai: AiClient by lazy { AiClient(http, keys, database.aiRequests()) }

    val questions: QuestionRepository by lazy { QuestionRepository(database) }

    val tests: TestRepository by lazy { TestRepository(database) }

    val generator: QuestionGenerator by lazy { QuestionGenerator(database, ai, questions) }

    val rules: RulesUpdater by lazy { RulesUpdater(database, ai) }

    val jobs: GenerationJobs by lazy { GenerationJobs(context, database) }

    val importer: TextbookImporter by lazy { TextbookImporter(context, database) }
}
