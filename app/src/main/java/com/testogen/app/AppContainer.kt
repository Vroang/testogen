package com.testogen.app

import android.content.Context
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.prefs.KeysRepository
import com.testogen.app.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Все долгоживущие объекты приложения (ручное внедрение зависимостей). */
class AppContainer(context: Context) {
    /** Область для работы, которая не должна обрываться вместе с экраном. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(context) }

    val keys = KeysRepository(context)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val updater: Updater by lazy { Updater(context, http, appScope) }
}
