package com.testogen.app.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.theme.Palette
import com.testogen.app.update.UpdateState

/** Предложение установить новую версию и прогресс её скачивания. */
@Composable
fun UpdateDialog() {
    val updater = LocalAppContainer.current.updater
    val state by updater.state.collectAsStateWithLifecycle()

    when (val s = state) {
        UpdateState.Idle -> Unit
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text("Есть новая версия") },
            text = { Text("Установить ${s.release.versionName}? Вопросы и тесты сохранятся.") },
            confirmButton = { TextButton(onClick = { updater.install(s.release) }) { Text("Обновить") } },
            dismissButton = { TextButton(onClick = updater::dismiss) { Text("Позже") } },
            containerColor = Palette.Surface,
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Скачиваю обновление") },
            text = {
                Column {
                    val target = s.progress
                    if (target == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round)
                    } else {
                        val progress by animateFloatAsState(target, tween(300), label = "download")
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("${(target * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = updater::dismiss) { Text("Отмена") } },
            containerColor = Palette.Surface,
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text("Не получилось обновить") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { updater.install(s.release) }) { Text("Ещё раз") } },
            dismissButton = { TextButton(onClick = updater::dismiss) { Text("Закрыть") } },
            containerColor = Palette.Surface,
        )
    }
}
