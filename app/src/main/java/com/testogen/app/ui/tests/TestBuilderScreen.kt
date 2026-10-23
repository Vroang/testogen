package com.testogen.app.ui.tests

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ai.QuestionGenerator
import com.testogen.app.ai.TextbookJobParams
import com.testogen.app.ai.TopicJobParams
import com.testogen.app.data.Candidate
import com.testogen.app.data.TestBuilder
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.GenerationJobEntity
import com.testogen.app.data.db.TopicCount
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.ChoicePill
import com.testogen.app.ui.components.CountStepper
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.PulsingBadge
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.money
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestBuilderViewModel(private val c: AppContainer) : ViewModel() {
    val topics: StateFlow<List<TopicCount>> = c.database.questions().observeTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selected by mutableStateOf<List<String>>(emptyList())
        private set
    var title by mutableStateOf("")
    private var titleEdited = false
    var variants by mutableStateOf(2)
    var perVariant by mutableStateOf(10)
    var difficulties by mutableStateOf(setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD))
        private set
    var allowRepeats by mutableStateOf(false)
    var pool by mutableStateOf<List<Candidate>>(emptyList())
        private set
    var building by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Задания догенерации, запущенные с этого экрана. */
    private val startedJobs = MutableStateFlow<List<Long>>(emptyList())
    val jobs: StateFlow<List<GenerationJobEntity>> = combine(startedJobs, c.database.jobs().observeActive()) { ids, active ->
        active.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val needed get() = TestBuilder.needed(variants, perVariant)
    val missing get() = (needed - pool.size).coerceAtLeast(0)

    init {
        // Когда догенерация закончилась — пересчитать, сколько вопросов есть
        viewModelScope.launch { jobs.collect { refreshPool() } }
    }

    fun toggleTopic(t: TopicCount) {
        selected = if (t.topicKey in selected) selected - t.topicKey else selected + t.topicKey
        if (!titleEdited) {
            val names = topics.value.filter { it.topicKey in selected }.map { it.topic }
            val joined = names.joinToString(", ")
            title = when {
                names.isEmpty() -> ""
                names.size == 1 || joined.length <= 60 -> joined
                else -> "${names.first()} и ещё ${names.size - 1}"
            }
        }
        refreshPool()
    }

    fun onTitle(value: String) {
        title = value
        titleEdited = true
    }

    fun toggleDifficulty(d: Difficulty) {
        difficulties = if (d in difficulties) {
            if (difficulties.size > 1) difficulties - d else difficulties
        } else {
            difficulties + d
        }
        refreshPool()
    }

    private fun refreshPool() {
        viewModelScope.launch {
            pool = if (selected.isEmpty()) emptyList() else c.database.questions().candidates(selected, difficulties.map { it.code })
        }
    }

    val estimate: Double
        get() = QuestionGenerator.estimateCostUsd(missing, review = true, existingInTopic = pool.size / selected.size.coerceAtLeast(1), rules = 0)

    /** Догенерировать недостающие: поровну по выбранным темам; темы из учебника — по тексту параграфа. */
    fun generateMissing() {
        val need = missing
        if (need == 0 || selected.isEmpty()) return
        viewModelScope.launch {
            val review = c.prefs.review.first()
            val grade = c.prefs.lastGrade.first()
            val share = QuestionGenerator.allocate(need + selected.size, List(selected.size) { 1 })
            val ids = mutableListOf<Long>()
            selected.forEachIndexed { i, key ->
                val count = share[i].coerceAtLeast(1)
                val topic = topics.value.firstOrNull { it.topicKey == key }?.topic ?: return@forEachIndexed
                val section = c.database.questions().mainSection(key)?.let { c.database.textbooks().section(it) }
                ids += if (section != null) {
                    c.jobs.startTextbook(
                        TextbookJobParams(section.textbookId, listOf(section.id), count, difficulties.map { it.code }.sorted(), review),
                        section.fullTitle,
                    )
                } else {
                    c.jobs.startTopic(TopicJobParams(topic, grade, count, difficulties.map { it.code }.sorted(), review))
                }
            }
            startedJobs.value = startedJobs.value + ids
        }
    }

    fun build(onBuilt: (Long) -> Unit) {
        if (building || selected.isEmpty()) return
        building = true
        message = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { TestBuilder.build(pool, variants, perVariant, allowRepeats) }
            when (result) {
                is TestBuilder.Result.NotEnough -> {
                    message = "Не хватает вопросов: есть ${result.have}, нужно ${result.need}."
                    building = false
                }
                is TestBuilder.Result.Ok -> {
                    val id = c.tests.create(
                        title = title.ifBlank { "Тест" },
                        topics = topics.value.filter { it.topicKey in selected }.map { it.topic },
                        difficulties = difficulties.map { it.code }.sorted(),
                        allowRepeats = allowRepeats,
                        variants = result.variants,
                        perVariant = perVariant,
                    )
                    building = false
                    onBuilt(id)
                }
            }
        }
    }
}

/** Сборка теста: темы, варианты, вопросы в варианте, сложность; догенерация недостающих. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestBuilderScreen(onBack: () -> Unit, onBuilt: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TestBuilderViewModel = viewModel { TestBuilderViewModel(container) }
    val topics by vm.topics.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header") { BackHeader("Новый тест", onBack) }
        item(key = "topics") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Text("Темы", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (topics.isEmpty()) "В банке пока нет вопросов — сначала создайте их во вкладке «Вопросы» или по учебнику."
                    else "Отметьте одну или несколько тем.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.forEach { t ->
                        ChoicePill("${t.topic.ifBlank { "Без темы" }} · ${t.count}", t.topicKey in vm.selected, { vm.toggleTopic(t) }, singleLine = false)
                    }
                }
            }
        }
        item(key = "size") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Text("Вариантов", style = MaterialTheme.typography.titleMedium)
                CountStepper(vm.variants, { vm.variants = it }, range = 1..40, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text("Вопросов в каждом варианте", style = MaterialTheme.typography.titleMedium)
                CountStepper(vm.perVariant, { vm.perVariant = it }, range = 1..40, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(14.dp))
                Text("Сложность", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { d ->
                        ChoicePill(
                            when (d) {
                                Difficulty.EASY -> "Лёгкие"
                                Difficulty.MEDIUM -> "Средние"
                                Difficulty.HARD -> "Сложные"
                            },
                            d in vm.difficulties,
                            { vm.toggleDifficulty(d) },
                            Modifier.weight(1f),
                            horizontalPadding = 4.dp,
                        )
                    }
                }
            }
        }
        if (vm.selected.isNotEmpty()) {
            item(key = "availability") {
                Availability(vm, jobs, Modifier.padding(horizontal = pad))
            }
        }
        item(key = "title") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Text("Название теста", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                AppTextField(
                    vm.title,
                    vm::onTitle,
                    placeholder = "Например: Государство",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
        item(key = "build") {
            Column(Modifier.padding(horizontal = pad)) {
                vm.message?.let { Text(it, color = Palette.Error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                val canBuild = vm.selected.isNotEmpty() && (vm.missing == 0 || (vm.allowRepeats && vm.pool.size >= vm.perVariant))
                PrimaryButton(
                    if (vm.building) "Собираю…" else "Собрать тест",
                    { vm.build(onBuilt) },
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Document,
                    enabled = canBuild && !vm.building,
                )
            }
        }
    }
}

@Composable
private fun Availability(vm: TestBuilderViewModel, jobs: List<GenerationJobEntity>, modifier: Modifier = Modifier) {
    val enough = vm.missing == 0
    SoftCard(modifier.fillMaxWidth(), color = if (enough) Palette.PrimarySoft else Palette.AccentSoft) {
        val content = if (enough) Palette.OnPrimarySoft else Palette.OnAccentSoft
        Text(
            "Подходящих вопросов: ${vm.pool.size}. Нужно ${vm.needed} (${vm.variants} × ${vm.perVariant}).",
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
        if (enough) {
            Text("Хватает — вопросы в вариантах не будут повторяться.", style = MaterialTheme.typography.bodyMedium, color = content)
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "Не хватает ${vm.missing} ${plural(vm.missing, "вопроса", "вопросов", "вопросов")}. Можно создать недостающие или разрешить повторы между вариантами.",
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            Spacer(Modifier.height(12.dp))
            if (jobs.isEmpty()) {
                PrimaryButton(
                    "Создать ${vm.missing} · ${money(vm.estimate)}",
                    vm::generateMissing,
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Sparkle,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Разрешить повторы между вариантами",
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = vm.allowRepeats,
                    onCheckedChange = { vm.allowRepeats = it },
                    enabled = vm.pool.size >= vm.perVariant,
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, uncheckedTrackColor = Palette.Surface),
                )
            }
        }
        AnimatedVisibility(jobs.isNotEmpty()) {
            Column {
                jobs.forEach { job ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingBadge(AppIcons.Sparkle, active = true, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${job.stage} ${job.saved} из ${job.total}", style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                        }
                    }
                    val progress by animateFloatAsState(if (job.total > 0) job.saved.toFloat() / job.total else 0f, tween(500), label = "builderJob")
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Palette.Primary,
                        trackColor = Palette.Surface,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
