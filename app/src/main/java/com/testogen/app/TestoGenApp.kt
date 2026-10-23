package com.testogen.app

import android.app.Application
import com.testogen.app.data.prefs.LegacyCleanup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val BANK_CLEANUP_VERSION = 2

class TestoGenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            LegacyCleanup.runOnce(this@TestoGenApp, container.keys)
            // Разовая чистка банка от «по тексту учебника» (номер растёт, когда чистка дополняется)
            if (container.prefs.cleanupVersion.first() < BANK_CLEANUP_VERSION) {
                container.questions.cleanSourceRefs()
                container.prefs.setCleanupVersion(BANK_CLEANUP_VERSION)
            }
            // Незавершённые генерации (приложение закрыли или телефон перезагрузился) — продолжить
            container.jobs.resumeUnfinished()
        }
    }
}
