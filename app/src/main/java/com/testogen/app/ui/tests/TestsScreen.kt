package com.testogen.app.ui.tests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.TestSummary
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.IconBadge
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.ScreenHeader
import com.testogen.app.ui.components.SettingsButton
import com.testogen.app.ui.components.SectionLabel
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.util.formatDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TestsViewModel(db: AppDatabase) : ViewModel() {
    val tests: StateFlow<List<TestSummary>> = db.tests().observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun TestsScreen(onOpenSettings: () -> Unit, onNewTest: () -> Unit, onOpenTest: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TestsViewModel = viewModel { TestsViewModel(container.database) }
    val tests by vm.tests.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header", contentType = "header") {
            ScreenHeader(title = "Тесты", subtitle = "Сборка вариантов для класса") {
                SettingsButton(onOpenSettings)
            }
        }
        item(key = "new", contentType = "new") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Sparkle)
                    Spacer(Modifier.width(16.dp))
                    Text("Новый тест", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Укажите тему, число вариантов и вопросов — приложение разложит вопросы из банка по вариантам.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = "Собрать тест",
                    onClick = onNewTest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "label", contentType = "label") {
            SectionLabel("Сохранённые тесты", Modifier.padding(horizontal = pad))
        }
        if (tests.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    "Здесь появятся собранные тесты. Их можно будет открыть, поправить и снова скачать.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = pad),
                )
            }
        } else {
            items(tests, key = { it.id }, contentType = { "test" }) { test ->
                SoftCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                    onClick = { onOpenTest(test.id) },
                ) {
                    Text(test.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${test.variantsCount} вар. × ${test.questionsPerVariant} вопр. · ${formatDate(test.updatedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
