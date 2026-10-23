package com.testogen.app.ui.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
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
import com.testogen.app.data.db.GenerationJobEntity
import com.testogen.app.data.db.TopicCount
import com.testogen.app.work.GenerationJobs
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.EmptyState
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.PulsingBadge
import com.testogen.app.ui.components.ScreenHeader
import com.testogen.app.ui.components.SettingsButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SectionLabel
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BankViewModel(db: AppDatabase, jobs: GenerationJobs) : ViewModel() {
    /** null — ещё загружается (чтобы не мигало пустое состояние). */
    val topics: StateFlow<List<TopicCount>?> = db.questions().observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeJobs: StateFlow<List<GenerationJobEntity>> = jobs.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun BankScreen(
    onOpenSettings: () -> Unit,
    onGenerate: (String) -> Unit,
    onAddManual: (String) -> Unit,
    onOpenTopic: (key: String, title: String) -> Unit,
    onOpenJob: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: BankViewModel = viewModel { BankViewModel(container.database, container.jobs) }
    val topics by vm.topics.collectAsStateWithLifecycle()
    val activeJobs by vm.activeJobs.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding
    val total = topics?.sumOf { it.count } ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header", contentType = "header") {
            ScreenHeader(
                title = "Вопросы",
                subtitle = if (total == 0) "Банк пока пуст" else "$total ${plural(total, "вопрос", "вопроса", "вопросов")} в банке",
            ) {
                SettingsButton(onOpenSettings)
            }
        }
        item(key = "actions", contentType = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    text = "По теме",
                    icon = AppIcons.Sparkle,
                    onClick = { onGenerate("") },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Вручную",
                    icon = Icons.Filled.Edit,
                    onClick = { onAddManual("") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        items(activeJobs, key = { "job-${it.id}" }, contentType = { "job" }) { job ->
            ActiveJobCard(job, { onOpenJob(job.id) }, Modifier.padding(horizontal = pad))
        }
        val list = topics
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty", contentType = "empty") {
                EmptyState(
                    icon = AppIcons.Quiz,
                    title = "Вопросов пока нет",
                    text = "Создайте вопросы по теме или по учебнику — нейросеть подготовит их за минуту.",
                )
            }
            else -> {
                item(key = "label", contentType = "label") {
                    SectionLabel("Темы", Modifier.padding(horizontal = pad))
                }
                items(list, key = { it.topicKey }, contentType = { "topic" }) { topic ->
                    SoftCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = pad),
                        onClick = { onOpenTopic(topic.topicKey, topic.topic) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(topic.topic.ifBlank { "Без темы" }, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${topic.count} ${plural(topic.count, "вопрос", "вопроса", "вопросов")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Palette.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Идущая генерация: тап открывает подробности. */
@Composable
private fun ActiveJobCard(job: GenerationJobEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress by androidx.compose.animation.core.animateFloatAsState(
        if (job.total > 0) job.saved.toFloat() / job.total else 0f,
        androidx.compose.animation.core.tween(600),
        label = "jobCard",
    )
    SoftCard(modifier.fillMaxWidth(), onClick = onClick, color = Palette.PrimarySoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingBadge(AppIcons.Sparkle, active = true, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, color = Palette.OnPrimarySoft, maxLines = 1)
                Text(
                    "${job.stage.ifBlank { "Готовлю задание…" }} ${job.saved} из ${job.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.OnPrimarySoft,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Palette.Primary,
            trackColor = Palette.Surface,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
