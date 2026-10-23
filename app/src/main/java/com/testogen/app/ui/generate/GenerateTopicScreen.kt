package com.testogen.app.ui.generate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ai.QuestionGenerator
import com.testogen.app.ai.TopicJobParams
import com.testogen.app.data.cleanTopic
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.topicKey
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.ChoicePill
import com.testogen.app.ui.components.GradePicker
import com.testogen.app.ui.components.CountStepper
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.formatUsd
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GenerateTopicViewModel(private val c: AppContainer, initialTopic: String) : ViewModel() {
    var topic by mutableStateOf(initialTopic)
    var grade by mutableStateOf<Int?>(null)
    var count by mutableStateOf(20)
    var difficulties by mutableStateOf(setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD))
    var review by mutableStateOf(true)
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var hasKey by mutableStateOf(true)
        private set
    var existingInTopic by mutableStateOf(0)
        private set
    var rulesCount by mutableStateOf(0)
        private set
    var starting by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            grade = c.prefs.lastGrade.first()
            review = c.prefs.review.first()
            suggestions = c.database.questions().recentTopics()
            rulesCount = c.database.aiRules().active().size
            refreshExisting()
        }
    }

    fun refreshKey() {
        viewModelScope.launch { hasKey = c.ai.hasAnyKey() }
    }

    fun refreshExisting() {
        viewModelScope.launch {
            existingInTopic = if (topic.isBlank()) 0 else c.database.questions().textsByTopic(topicKey(topic)).size
        }
    }

    fun toggle(d: Difficulty) {
        difficulties = if (d in difficulties) {
            if (difficulties.size > 1) difficulties - d else difficulties
        } else {
            difficulties + d
        }
    }

    val estimate: Double
        get() = QuestionGenerator.estimateCostUsd(count, review, existingInTopic, rulesCount)

    fun start(onStarted: (Long) -> Unit) {
        if (starting || topic.isBlank()) return
        starting = true
        viewModelScope.launch {
            c.prefs.setLastGrade(grade)
            c.prefs.setReview(review)
            val id = c.jobs.startTopic(
                TopicJobParams(
                    topic = cleanTopic(topic),
                    grade = grade,
                    total = count,
                    difficulties = difficulties.map { it.code }.sorted(),
                    review = review,
                ),
            )
            onStarted(id)
        }
    }
}

@Composable
fun GenerateTopicScreen(
    initialTopic: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStarted: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: GenerateTopicViewModel = viewModel { GenerateTopicViewModel(container, initialTopic) }
    val context = LocalContext.current
    val pad = LocalDimens.current.screenPadding
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshKey()
        onPauseOrDispose {}
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.start(onStarted)
    }
    val start = {
        val needAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needAsk) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start(onStarted)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header") { BackHeader("Вопросы по теме", onBack) }

        if (!vm.hasKey) {
            item(key = "nokey") {
                SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad), color = Palette.AccentSoft) {
                    Text("Нужен ключ нейросети", style = MaterialTheme.typography.titleMedium, color = Palette.OnAccentSoft)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Вставьте ключ DeepSeek в настройках — без него вопросы не создать.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.OnAccentSoft,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Открыть настройки", onOpenSettings)
                }
            }
        }

        item(key = "topic") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Label("Тема")
                AppTextField(
                    value = vm.topic,
                    onValueChange = { vm.topic = it; vm.refreshExisting() },
                    placeholder = "Например: Петровские реформы",
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (vm.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(vm.suggestions, key = { it }) { s ->
                            ChoicePill(s, selected = topicKey(s) == topicKey(vm.topic), onClick = { vm.topic = s; vm.refreshExisting() })
                        }
                    }
                }
                if (vm.existingInTopic > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "В банке уже ${vm.existingInTopic} ${plural(vm.existingInTopic, "вопрос", "вопроса", "вопросов")} по этой теме — новые не будут их повторять.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }

        item(key = "grade") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Label("Класс")
                GradePicker(vm.grade, { vm.grade = it })
            }
        }

        item(key = "count") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Label("Сколько вопросов")
                CountStepper(vm.count, { vm.count = it }, range = 5..50, step = 5, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                Label("Сложность")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { d ->
                        ChoicePill(
                            when (d) {
                                Difficulty.EASY -> "Лёгкие"
                                Difficulty.MEDIUM -> "Средние"
                                Difficulty.HARD -> "Сложные"
                            },
                            selected = d in vm.difficulties,
                            onClick = { vm.toggle(d) },
                            modifier = Modifier.weight(1f),
                            horizontalPadding = 4.dp,
                        )
                    }
                }
            }
        }

        item(key = "review") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("Перепроверять вопросы", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Нейросеть второй раз сверит факты и уберёт сомнительные вопросы. Немного дольше.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextSecondary,
                        )
                    }
                    Spacer(Modifier.padding(6.dp))
                    Switch(
                        checked = vm.review,
                        onCheckedChange = { vm.review = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, uncheckedTrackColor = Palette.SurfaceSunken),
                    )
                }
            }
        }

        item(key = "start") {
            androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = pad)) {
                Text(
                    "Примерная стоимость: ${formatUsd(vm.estimate)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                PrimaryButton(
                    text = "Создать ${vm.count} ${plural(vm.count, "вопрос", "вопроса", "вопросов")}",
                    icon = AppIcons.Sparkle,
                    enabled = vm.topic.isNotBlank() && vm.hasKey && !vm.starting,
                    onClick = start,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Можно свернуть приложение — работа продолжится.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextHint,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
}
