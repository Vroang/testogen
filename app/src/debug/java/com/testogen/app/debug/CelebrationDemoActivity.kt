package com.testogen.app.debug

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.testogen.app.ui.celebration.CelebrationScreen
import com.testogen.app.ui.theme.TestoGenTheme

/**
 * Демо праздничного экрана (только debug-сборка): тот же экран, что в приложении, без отметки «уже показан».
 * am start -n com.testogen.app/.debug.CelebrationDemoActivity
 */
class CelebrationDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TestoGenTheme {
                CelebrationScreen(
                    onWatch = { Toast.makeText(this, "Демо: здесь начнётся загрузка ролика", Toast.LENGTH_SHORT).show() },
                    onLater = { finish() },
                )
            }
        }
    }
}
