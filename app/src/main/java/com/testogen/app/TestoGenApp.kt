package com.testogen.app

import android.app.Application
import com.testogen.app.data.prefs.LegacyCleanup
import kotlinx.coroutines.launch

class TestoGenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { LegacyCleanup.runOnce(this@TestoGenApp, container.keys) }
    }
}
