package com.testogen.app.ui.generate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.data.db.GenerationJobEntity
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.data.topicKey
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.IconBadge
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.PulsingBadge
import com.testogen.app.ui.components.QuestionCard
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SectionLabel
import com.testogen.app.ui.components.SkeletonCard
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.formatUsd
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JobViewModel(private val c: AppContainer, private val jobId: Long) : ViewModel() {
    val job: StateFlow<GenerationJobEntity?> = c.jobs.observe(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val questions: StateFlow<List<QuestionEntity>> = c.database.questions().observeByJob(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel() = viewModelScope.launch { c.jobs.cancel(jobId) }
    fun retry() = viewModelScope.launch { c.jobs.retry(jobId) }
}

/** Ход генерации: этап, плавный прогресс и появляющиеся вопросы. */
@Composable
fun JobScreen(
    jobId: Long,
    onBack: () -> Unit,
    onOpenTopic: (key: String, title: String) -> Unit,
    onEditQuestion: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: JobViewModel = viewModel(key = "job-$jobId") { JobViewModel(container, jobId) }
    val job by vm.job.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { BackHeader("Создание вопросов", onBack) }
        item(key = "status") {
            job?.let { StatusCard(it, vm::cancel, vm::retry, onBack, onOpenTopic, Modifier.padding(horizontal = pad)) }
        }
        if (questions.isNotEmpty()) {
            item(key = "label") {
                SectionLabel("Новые вопросы", Modifier.padding(horizontal = pad))
            }
        }
        itemsIndexed(questions, key = { _, q -> q.id }, contentType = { _, _ -> "question" }) { i, q ->
            QuestionCard(
                q,
                number = i + 1,
                onEdit = { onEditQuestion(q.id) },
                modifier = Modifier
                    .padding(horizontal = pad)
                    .animateItem(fadeInSpec = tween(400), placementSpec = tween(300)),
            )
        }
        val running = job?.status == "running" || job?.status == "queued"
        if (running) {
            if (questions.isEmpty()) item(key = "label-wait") { SectionLabel("Новые вопросы", Modifier.padding(horizontal = pad)) }
            items(2, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                SkeletonCard(Modifier.padding(horizontal = pad).animateItem(fadeOutSpec = tween(200)))
            }
        }
    }
}

@Composable
private fun StatusCard(
    job: GenerationJobEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onOpenTopic: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = job.status == "running" || job.status == "queued"
    val target = if (job.total > 0) job.saved.toFloat() / job.total else 0f
    val progress by animateFloatAsState(target, tween(700), label = "jobProgress")

    SoftCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (job.status) {
                "done" -> IconBadge(Icons.Filled.Check, size = 64.dp)
                "failed", "cancelled" -> IconBadge(Icons.Filled.Warning, size = 64.dp)
                else -> PulsingBadge(AppIcons.Sparkle, active = true, size = 64.dp)
            }
            Spacer(Modifier.width(16.dp))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(job.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                AnimatedContent(
                    targetState = when (job.status) {
                        "done" -> "Готово!"
                        "failed" -> "Остановилось из-за ошибки"
                        "cancelled" -> "Остановлено"
                        else -> job.stage.ifBlank { "Готовлю задание…" }
                    },
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 }) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 })
                    },
                    label = "stage",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Palette.Primary,
            trackColor = Palette.SurfaceSunken,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Готово ${job.saved} из ${job.total}" +
                (if (job.dropped > 0) " · отсеяно ${job.dropped}" else "") +
                (if (job.costUsd > 0) " · ${formatUsd(job.costUsd)}" else ""),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        job.error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.Error)
        }
        Spacer(Modifier.height(16.dp))
        when {
            running -> {
                Text(
                    "Можно свернуть приложение — работа продолжится.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextHint,
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Остановить", onCancel, Modifier.fillMaxWidth())
            }
            job.status == "done" -> {
                PrimaryButton(
                    "Открыть тему",
                    { onOpenTopic(topicKey(job.title), job.title) },
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Готово", onDone, Modifier.fillMaxWidth())
            }
            else -> {
                val left = job.total - job.saved
                PrimaryButton(
                    "Продолжить: ещё $left ${plural(left, "вопрос", "вопроса", "вопросов")}",
                    onRetry,
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
