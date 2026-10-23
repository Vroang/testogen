package com.testogen.app.ui.textbooks

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.data.db.BlockHit
import com.testogen.app.data.db.TextbookChapterEntity
import com.testogen.app.data.db.TextbookEntity
import com.testogen.app.data.db.TextbookSectionEntity
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SectionLabel
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TextbookViewModel(private val c: AppContainer, val textbookId: Long) : ViewModel() {
    private val dao = c.database.textbooks()
    val book: StateFlow<TextbookEntity?> = dao.observe(textbookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val chapters: StateFlow<List<TextbookChapterEntity>> = dao.observeChapters(textbookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sections: StateFlow<List<TextbookSectionEntity>?> = dao.observeSections(textbookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val questionCounts: StateFlow<Map<Long, Int>> = c.database.questions().observeCountsBySection(textbookId)
        .map { list -> list.associate { it.code to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var query by mutableStateOf("")
        private set
    var hits by mutableStateOf<List<BlockHit>?>(null)
        private set
    private var searchJob: Job? = null

    fun onQuery(value: String) {
        query = value
        searchJob?.cancel()
        if (value.trim().length < 3) {
            hits = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            hits = dao.search(textbookId, value.trim())
        }
    }

    fun rename(section: TextbookSectionEntity, title: String, numFrom: Int?, numTo: Int?, use: Boolean) = viewModelScope.launch {
        if (title.isNotBlank()) dao.renameSection(section.id, title.trim())
        dao.setNumbering(section.id, if (numFrom != null) 0 else 1, numFrom, numTo?.takeIf { it != numFrom }, use)
    }

    fun mergeWithNext(section: TextbookSectionEntity) = viewModelScope.launch {
        val list = dao.sections(textbookId)
        val next = list.getOrNull(list.indexOfFirst { it.id == section.id } + 1) ?: return@launch
        dao.mergeWithNext(section, next)
    }

    fun setUse(section: TextbookSectionEntity, use: Boolean) = viewModelScope.launch { dao.setUseForQuestions(section.id, use) }

    fun renameBook(title: String, grade: Int?) = viewModelScope.launch { dao.rename(textbookId, title.trim(), grade) }
}

/** Учебник: оглавление (с правкой структуры), поиск и переход к созданию вопросов. */
@Composable
fun TextbookScreen(
    textbookId: Long,
    onBack: () -> Unit,
    onOpenSection: (sectionId: Long, highlightStart: Int, highlightEnd: Int, query: String) -> Unit,
    onGenerate: (textbookId: Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TextbookViewModel = viewModel(key = "book-$textbookId") { TextbookViewModel(container, textbookId) }
    val book by vm.book.collectAsStateWithLifecycle()
    val chapters by vm.chapters.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val counts by vm.questionCounts.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TextbookSectionEntity?>(null) }
    var editingBook by remember { mutableStateOf(false) }
    val list = sections.orEmpty()
    // По номерам: сдвоенный «§ 2–3» считается за два параграфа
    val paragraphs = list.filter { it.kind == 0 }.sumOf { s -> s.numFrom?.let { from -> s.numTo?.takeIf { it > from }?.let { it - from + 1 } } ?: 1 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            BackHeader(book?.title ?: "Учебник", onBack)
        }
        item(key = "summary") {
            Column(Modifier.padding(horizontal = pad)) {
                Text(
                    listOfNotNull(
                        book?.grade?.let { "$it класс" },
                        "$paragraphs ${plural(paragraphs, "параграф", "параграфа", "параграфов")}",
                        book?.pdfPageCount?.takeIf { it > 0 }?.let { "$it стр." },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Создать вопросы", { onGenerate(textbookId) }, Modifier.fillMaxWidth(), icon = AppIcons.Sparkle)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    if (editMode) "Готово" else "Проверить и поправить структуру",
                    { editMode = !editMode },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Edit,
                )
            }
        }
        val warnings = book?.warnings.orEmpty().lines().filter { it.isNotBlank() }
        if (editMode) {
            item(key = "edit-help") {
                SoftCard(Modifier.fillMaxWidth().padding(horizontal = pad), color = Palette.AccentSoft) {
                    Text(
                        if (warnings.isEmpty()) {
                            "Структура совпала с оглавлением учебника. Если что-то не так — поправьте: название, номер параграфа, " +
                                "соединение с соседним. Разделить параграф можно на экране чтения (долгое нажатие на абзац)."
                        } else {
                            "Есть расхождения с оглавлением:\n" + warnings.joinToString("\n") { "• $it" }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.OnAccentSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { editingBook = true }) { Text("Название и класс учебника") }
                }
            }
        }
        item(key = "search") {
            AppTextField(
                value = vm.query,
                onValueChange = vm::onQuery,
                placeholder = "Поиск по учебнику",
                singleLine = true,
                leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.TextSecondary) },
                trailing = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Очистить") }
                    }
                },
                modifier = Modifier.padding(horizontal = pad),
            )
        }

        val hits = vm.hits
        if (hits != null) {
            item(key = "hits-label") {
                SectionLabel(
                    if (hits.isEmpty()) "Ничего не нашлось" else "Найдено: ${hits.size}${if (hits.size >= 60) "+" else ""}",
                    Modifier.padding(horizontal = pad),
                )
            }
            items(hits, key = { "hit-${it.blockId}" }, contentType = { "hit" }) { hit ->
                val start = hit.text.indexOf(vm.query.trim(), ignoreCase = true).coerceAtLeast(0)
                SoftCard(
                    Modifier.fillMaxWidth().padding(horizontal = pad),
                    onClick = { onOpenSection(hit.sectionId, -1, -1, vm.query.trim()) },
                    contentPadding = PaddingValues(16.dp),
                ) {
                    val label = listOfNotNull(
                        hit.numFrom?.let { if (hit.numTo != null && hit.numTo != it) "§ $it–${hit.numTo}" else "§ $it" },
                        hit.sectionTitle,
                        hit.bookPage?.let { "стр. $it" },
                    ).joinToString(" · ")
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(snippet(hit.text, start, vm.query.trim().length), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            var lastChapter: Long? = -1
            for (section in list) {
                if (section.chapterId != lastChapter && section.chapterId != null) {
                    val chapter = chapters.firstOrNull { it.id == section.chapterId }
                    if (chapter != null) {
                        item(key = "ch-${chapter.id}-${section.id}") {
                            Text(
                                "${chapter.label}. ${chapter.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Palette.Primary,
                                modifier = Modifier.padding(start = pad, end = pad, top = 14.dp, bottom = 2.dp),
                            )
                        }
                    }
                }
                lastChapter = section.chapterId
                item(key = "s-${section.id}") {
                    SectionRow(
                        section = section,
                        questions = counts[section.id] ?: 0,
                        editMode = editMode,
                        onOpen = { onOpenSection(section.id, -1, -1, "") },
                        onEdit = { editing = section },
                        onMerge = { vm.mergeWithNext(section) },
                        onUse = { vm.setUse(section, it) },
                        isLast = section.id == list.last().id,
                        modifier = Modifier.padding(horizontal = pad).animateItem(),
                    )
                }
            }
        }
    }

    editing?.let { section ->
        EditSectionDialog(section, onDismiss = { editing = null }) { title, from, to, use ->
            vm.rename(section, title, from, to, use)
            editing = null
        }
    }
    if (editingBook) {
        book?.let { b ->
            EditBookDialog(b, onDismiss = { editingBook = false }) { title, grade ->
                vm.renameBook(title, grade)
                editingBook = false
            }
        }
    }
}

private fun snippet(text: String, start: Int, length: Int) = buildAnnotatedString {
    val from = (start - 60).coerceAtLeast(0)
    val to = (start + length + 120).coerceAtMost(text.length)
    if (from > 0) append("…")
    append(text.substring(from, start))
    withStyle(SpanStyle(background = Palette.AccentSoft)) { append(text.substring(start, (start + length).coerceAtMost(text.length))) }
    append(text.substring((start + length).coerceAtMost(text.length), to))
    if (to < text.length) append("…")
}

@Composable
private fun SectionRow(
    section: TextbookSectionEntity,
    questions: Int,
    editMode: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onUse: (Boolean) -> Unit,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val paragraph = section.kind == 0
    SoftCard(
        modifier.fillMaxWidth(),
        onClick = onOpen,
        color = if (paragraph) Palette.Surface else Palette.Background,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (paragraph) {
                Text(section.label, style = MaterialTheme.typography.titleMedium, color = Palette.Primary, modifier = Modifier.width(76.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    section.title,
                    style = if (paragraph) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = if (paragraph) Palette.Text else Palette.TextSecondary,
                )
                val pages = when {
                    section.bookPageStart == null -> null
                    section.bookPageEnd != null && section.bookPageEnd != section.bookPageStart -> "стр. ${section.bookPageStart}–${section.bookPageEnd}"
                    else -> "стр. ${section.bookPageStart}"
                }
                val info = listOfNotNull(pages, questions.takeIf { it > 0 }?.let { "$it ${plural(it, "вопрос", "вопроса", "вопросов")}" })
                if (info.isNotEmpty()) {
                    Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
                }
            }
        }
        AnimatedVisibility(editMode) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Для вопросов", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = section.useForQuestions,
                        onCheckedChange = onUse,
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, uncheckedTrackColor = Palette.SurfaceSunken),
                    )
                }
                Row {
                    TextButton(onClick = onEdit) { Text("Изменить") }
                    if (!isLast) TextButton(onClick = onMerge) { Text("Соединить со следующим") }
                }
            }
        }
    }
}

@Composable
private fun EditSectionDialog(
    section: TextbookSectionEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, from: Int?, to: Int?, use: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(section.title) }
    var from by remember { mutableStateOf(section.numFrom?.toString().orEmpty()) }
    var to by remember { mutableStateOf(section.numTo?.toString().orEmpty()) }
    var use by remember { mutableStateOf(section.useForQuestions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Раздел учебника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(title, { title = it }, placeholder = "Название")
                Text("Номер параграфа (пусто — это не параграф)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        from,
                        { from = it.filter(Char::isDigit).take(3) },
                        placeholder = "§",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Text("–")
                    AppTextField(
                        to,
                        { to = it.filter(Char::isDigit).take(3) },
                        placeholder = "до",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(use, { use = it }, colors = CheckboxDefaults.colors(checkedColor = Palette.Primary))
                    Text("Брать для создания вопросов")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, from.toIntOrNull(), to.toIntOrNull(), use) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = Palette.Surface,
    )
}

@Composable
private fun EditBookDialog(book: TextbookEntity, onDismiss: () -> Unit, onSave: (String, Int?) -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var grade by remember { mutableStateOf(book.grade?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Учебник") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(title, { title = it }, placeholder = "Название")
                AppTextField(
                    grade,
                    { grade = it.filter(Char::isDigit).take(2) },
                    placeholder = "Класс",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, grade.toIntOrNull()?.takeIf { it in 1..11 }) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = Palette.Surface,
    )
}
