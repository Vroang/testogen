package com.testogen.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.main.AppRoot
import com.testogen.app.ui.theme.TestoGenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Только светлая тема: тёмные значки системных панелей на прозрачном фоне
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as TestoGenApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                TestoGenTheme {
                    // Только в debug-сборке: открыть генерацию с готовой темой из adb
                    // (adb не умеет вводить кириллицу): am start ... --es debug_topic "Тема"
                    AppRoot(debugTopic = if (BuildConfig.DEBUG) intent.getStringExtra("debug_topic") else null)
                }
            }
        }
    }
}
