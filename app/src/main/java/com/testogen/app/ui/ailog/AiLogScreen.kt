package com.testogen.app.ui.ailog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import com.testogen.app.data.db.AiRequestEntity
import com.testogen.app.data.db.AiSpend
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.EmptyState
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.formatDate
import com.testogen.app.ui.money
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AiLogViewModel(db: AppDatabase) : ViewModel() {
    val entries: StateFlow<List<AiRequestEntity>?> = db.aiRequests().observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val month: StateFlow<AiSpend> = db.aiRequests()
        .observeSpendSince(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSpend(0, 0.0))
}

private fun operationTitle(op: String) = when (op) {
    "topic" -> "Вопросы по теме"
    "textbook" -> "Вопросы по учебнику"
    "replace" -> "Замена вопроса"
    "check" -> "Проверка вопросов"
    "rules" -> "Обновление правил"
    "toc" -> "Разбор оглавления"
    else -> op
}

@Composable
fun AiLogScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AiLogViewModel = viewModel { AiLogViewModel(container.database) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", contentType = "header") { BackHeader("Журнал запросов", onBack) }
        item(key = "summary", contentType = "summary") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad), color = Palette.PrimarySoft) {
                Text("За 30 дней", style = MaterialTheme.typography.titleSmall, color = Palette.OnPrimarySoft)
                Spacer(Modifier.height(4.dp))
                Text(money(month.costUsd), style = MaterialTheme.typography.headlineMedium, color = Palette.OnPrimarySoft)
                Text(
                    "${month.requests} ${plural(month.requests, "запрос", "запроса", "запросов")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.OnPrimarySoft,
                )
            }
        }
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = AppIcons.Receipt,
                    title = "Запросов ещё не было",
                    text = "Здесь будет видно каждое обращение к нейросети: сколько слов ушло и сколько это стоило.",
                )
            }
            else -> items(list, key = { it.id }, contentType = { "entry" }) { entry ->
                LogRow(entry, Modifier.padding(horizontal = pad))
            }
        }
    }
}

@Composable
private fun LogRow(entry: AiRequestEntity, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(operationTitle(entry.operation), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (entry.status == "ok") money(entry.costUsd) else "ошибка",
                style = MaterialTheme.typography.titleSmall,
                color = if (entry.status == "ok") Palette.Primary else Palette.Error,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${formatDate(entry.startedAt)} · ${entry.provider} · ${entry.model}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.status == "ok") {
            Text(
                "Токены: ${entry.promptTokens} (из кэша ${entry.cachedTokens}) → ${entry.completionTokens}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (entry.status == "ok") Palette.TextSecondary else Palette.Error)
        }
    }
}
