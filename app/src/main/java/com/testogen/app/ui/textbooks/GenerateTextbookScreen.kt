package com.testogen.app.ui.textbooks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ai.QuestionGenerator
import com.testogen.app.ai.TextbookJobParams
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.TextbookChapterEntity
import com.testogen.app.data.db.TextbookEntity
import com.testogen.app.data.db.TextbookSectionEntity
import com.testogen.app.textbook.SectionText
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.ChoicePill
import com.testogen.app.ui.components.CountStepper
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.money
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GenerateTextbookViewModel(private val c: AppContainer, private val textbookId: Long, preselect: Long) : ViewModel() {
    var book by mutableStateOf<TextbookEntity?>(null)
        private set
    var chapters by mutableStateOf<List<TextbookChapterEntity>>(emptyList())
        private set
    var sections by mutableStateOf<List<TextbookSectionEntity>>(emptyList())
        private set
    /** Объём материала для нейросети по каждому разделу (символов). */
    private var sourceChars = emptyMap<Long, List<Int>>()
    var selected by mutableStateOf(if (preselect > 0) setOf(preselect) else emptySet())
    var count by mutableStateOf(20)
    var difficulties by mutableStateOf(setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD))
    var review by mutableStateOf(true)
    var includeSide by mutableStateOf(true)
    var hasKey by mutableStateOf(true)
        private set
    var starting by mutableStateOf(false)
        private set
    private var rulesCount = 0

    init {
        viewModelScope.launch {
            val dao = c.database.textbooks()
            book = dao.get(textbookId)
            chapters = dao.observeChapters(textbookId).first()
            sections = dao.sections(textbookId).filter { it.useForQuestions }
            sourceChars = sections.associate { s -> s.id to SectionText.build(dao.blocks(s.id), includeSide = true).parts.map { it.length } }
            review = c.prefs.review.first()
            rulesCount = c.database.aiRules().active().size
            hasKey = c.ai.hasAnyKey()
        }
    }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun toggleDifficulty(d: Difficulty) {
        difficulties = if (d in difficulties) {
            if (difficulties.size > 1) difficulties - d else difficulties
        } else {
            difficulties + d
        }
    }

    val estimate: Double
        get() {
            val chars = sections.filter { it.id in selected }.flatMap { sourceChars[it.id].orEmpty() }
            return QuestionGenerator.estimateCostUsd(count, review, 0, rulesCount, chars.ifEmpty { listOf(1) })
        }

    fun start(onStarted: (Long) -> Unit) {
        if (starting || selected.isEmpty()) return
        starting = true
        viewModelScope.launch {
            c.prefs.setReview(review)
            val chosen = sections.filter { it.id in selected }
            val title = if (chosen.size == 1) chosen.first().fullTitle else chosen.joinToString(", ") { it.label.ifEmpty { it.title } }
            val id = c.jobs.startTextbook(
                TextbookJobParams(
                    textbookId = textbookId,
                    sectionIds = chosen.map { it.id },
                    total = count,
                    difficulties = difficulties.map { it.code }.sorted(),
                    review = review,
                    includeSide = includeSide,
                ),
                title,
            )
            onStarted(id)
        }
    }
}

/** Создание вопросов по выбранным параграфам учебника. */
@Composable
fun GenerateTextbookScreen(textbookId: Long, preselect: Long, onBack: () -> Unit, onStarted: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm: GenerateTextbookViewModel = viewModel(key = "gen-book-$textbookId") { GenerateTextbookViewModel(container, textbookId, preselect) }
    val context = LocalContext.current
    val pad = LocalDimens.current.screenPadding
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start(onStarted) }
    val start = {
        val needAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needAsk) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start(onStarted)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header") { BackHeader("Вопросы по учебнику", onBack) }
        item(key = "book") {
            Text(
                listOfNotNull(vm.book?.title, vm.book?.grade?.let { "$it класс" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(horizontal = pad),
            )
        }
        item(key = "sections") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad), contentPadding = PaddingValues(vertical = 12.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Параграфы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.selected = if (vm.selected.isEmpty()) vm.sections.map { it.id }.toSet() else emptySet() }) {
                        Text(if (vm.selected.isEmpty()) "Выбрать все" else "Снять все")
                    }
                }
                var lastChapter: Long? = -1
                vm.sections.forEach { s ->
                    if (s.chapterId != lastChapter && s.chapterId != null) {
                        vm.chapters.firstOrNull { it.id == s.chapterId }?.let { ch ->
                            Text(
                                "${ch.label}. ${ch.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Palette.Primary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
                            )
                        }
                    }
                    lastChapter = s.chapterId
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggle(s.id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(s.id in vm.selected, { vm.toggle(s.id) }, colors = CheckboxDefaults.colors(checkedColor = Palette.Primary))
                        Text(s.fullTitle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                    }
                }
            }
        }
        item(key = "count") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                Text("Сколько вопросов всего", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                CountStepper(vm.count, { vm.count = it }, range = 5..100, step = 5, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(
                    "Делятся между параграфами по объёму текста.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
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
        item(key = "options") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                OptionRow("Перепроверять вопросы", "Нейросеть второй раз сверит ответ с текстом учебника.", vm.review) { vm.review = it }
                Spacer(Modifier.height(14.dp))
                OptionRow("Брать врезки", "Биографии и документы на полях — тоже материал для вопросов.", vm.includeSide) { vm.includeSide = it }
            }
        }
        item(key = "start") {
            Column(Modifier.padding(horizontal = pad)) {
                if (!vm.hasKey) {
                    Text("Нужен ключ нейросети — вставьте его в настройках.", color = Palette.Error, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                }
                if (vm.selected.isNotEmpty()) {
                    Text(
                        "Примерная стоимость: ${money(vm.estimate)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                PrimaryButton(
                    if (vm.selected.isEmpty()) "Выберите параграфы" else "Создать ${vm.count} ${plural(vm.count, "вопрос", "вопроса", "вопросов")}",
                    start,
                    Modifier.fillMaxWidth(),
                    icon = AppIcons.Sparkle,
                    enabled = vm.selected.isNotEmpty() && vm.hasKey && !vm.starting,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "У каждого вопроса будет ссылка «Откуда вопрос» — на место в учебнике.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextHint,
                )
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, uncheckedTrackColor = Palette.SurfaceSunken),
        )
    }
}
