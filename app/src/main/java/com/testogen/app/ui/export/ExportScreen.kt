package com.testogen.app.ui.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.EmptyState
import com.testogen.app.ui.theme.AppIcons

/** Файлы Word для теста — этап 5. */
@Composable
fun ExportScreen(testId: Long, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackHeader("Файлы теста", onBack)
        EmptyState(AppIcons.Document, "Скоро", "Скачивание теста и ответов в Word появится на следующем этапе.")
    }
}
