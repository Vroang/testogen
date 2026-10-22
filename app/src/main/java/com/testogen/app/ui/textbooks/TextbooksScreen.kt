package com.testogen.app.ui.textbooks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.testogen.app.ui.components.EmptyState
import com.testogen.app.ui.components.ScreenHeader
import com.testogen.app.ui.components.SettingsButton
import com.testogen.app.ui.theme.AppIcons

@Composable
fun TextbooksScreen(onOpenSettings: () -> Unit, showMessage: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header", contentType = "header") {
            ScreenHeader(title = "Учебники", subtitle = "Вопросы по параграфам") {
                SettingsButton(onOpenSettings)
            }
        }
        item(key = "empty", contentType = "empty") {
            EmptyState(
                icon = AppIcons.Book,
                title = "Учебников пока нет",
                text = "Загрузите учебник в PDF, DOCX или TXT — приложение разберёт его на главы и параграфы.",
                actionText = "Загрузить учебник",
                onAction = { showMessage("Загрузка учебников появится на этапе 3") },
            )
        }
    }
}
