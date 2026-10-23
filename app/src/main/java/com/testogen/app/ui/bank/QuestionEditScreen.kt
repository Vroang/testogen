package com.testogen.app.ui.bank

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ai.QuestionQuality
import com.testogen.app.data.QuestionDraft
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.data.db.QuestionSource
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.ChoicePill
import com.testogen.app.ui.components.GradePicker
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuestionEditViewModel(private val c: AppContainer, private val id: Long, defaultTopic: String) : ViewModel() {
    var existing: QuestionEntity? = null
        private set
    var loaded by mutableStateOf(id == 0L)
        private set
    var topic by mutableStateOf(defaultTopic)
    var grade by mutableStateOf<Int?>(null)
    var text by mutableStateOf("")
    val options = mutableStateListOf("", "", "", "")
    var correct by mutableStateOf(0)
    var difficulty by mutableStateOf(Difficulty.MEDIUM)
    var explanation by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            if (id != 0L) {
                c.database.questions().get(id)?.let { q ->
                    existing = q
                    topic = q.topic
                    grade = q.grade
                    text = q.text
                    listOf(q.option1, q.option2, q.option3, q.option4).forEachIndexed { i, o -> options[i] = o }
                    correct = q.correctIndex
                    difficulty = Difficulty.of(q.difficulty)
                    explanation = q.explanation.orEmpty()
                }
            } else {
                grade = c.prefs.lastGrade.first()
            }
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        error = when {
            topic.isBlank() -> "Укажите тему — по ней вопрос найдётся при сборке теста"
            text.isBlank() -> "Впишите текст вопроса"
            options.any { it.isBlank() } -> "Заполните все четыре варианта ответа"
            options.map { QuestionQuality.normalize(it) }.toSet().size < 4 -> "Варианты ответа не должны повторяться"
            else -> null
        }
        if (error != null) return
        viewModelScope.launch {
            c.questions.save(
                QuestionDraft(
                    text = text,
                    options = options.toList(),
                    correctIndex = correct,
                    difficulty = difficulty,
                    topic = topic,
                    grade = grade,
                    source = existing?.let { QuestionSource.of(it.source) } ?: QuestionSource.MANUAL,
                    explanation = explanation,
                ),
                existing,
            )
            onDone()
        }
    }
}

/** Ручной ввод и правка вопроса. */
@Composable
fun QuestionEditScreen(id: Long, defaultTopic: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: QuestionEditViewModel = viewModel(key = "edit-$id-$defaultTopic") { QuestionEditViewModel(container, id, defaultTopic) }
    val pad = LocalDimens.current.screenPadding
    if (!vm.loaded) return

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header") { BackHeader(if (id == 0L) "Новый вопрос" else "Правка вопроса", onBack) }
        item(key = "topic") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                FieldLabel("Тема")
                AppTextField(
                    vm.topic,
                    { vm.topic = it },
                    placeholder = "Например: Смутное время",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(14.dp))
                FieldLabel("Класс")
                GradePicker(vm.grade, { vm.grade = it })
            }
        }
        item(key = "text") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                FieldLabel("Вопрос")
                AppTextField(
                    vm.text,
                    { vm.text = it },
                    placeholder = "Текст вопроса",
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
        item(key = "options") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                FieldLabel("Варианты ответа")
                Text(
                    "Отметьте кружком верный ответ. В файле варианты будут в этом же порядке.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                vm.options.forEachIndexed { i, option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        RadioButton(
                            selected = vm.correct == i,
                            onClick = { vm.correct = i },
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Primary),
                        )
                        AppTextField(
                            option,
                            { vm.options[i] = it },
                            placeholder = "Вариант ${i + 1}",
                            singleLine = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item(key = "difficulty") {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad)) {
                FieldLabel("Сложность")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { d ->
                        ChoicePill(d.title, vm.difficulty == d, { vm.difficulty = d }, Modifier.weight(1f), horizontalPadding = 4.dp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                FieldLabel("Пояснение (необязательно)")
                AppTextField(vm.explanation, { vm.explanation = it }, placeholder = "Почему этот ответ верный", minLines = 2)
            }
        }
        item(key = "save") {
            Column(Modifier.padding(horizontal = pad)) {
                vm.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.Error, modifier = Modifier.padding(bottom = 10.dp))
                }
                PrimaryButton("Сохранить", { vm.save(onBack) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}
