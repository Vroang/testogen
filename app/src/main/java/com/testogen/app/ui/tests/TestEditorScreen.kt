package com.testogen.app.ui.tests

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ai.AiException
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.data.db.ReplacementReasonEntity
import com.testogen.app.data.db.TestEntity
import com.testogen.app.data.db.TestItemEntity
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.ChoicePill
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.PulsingBadge
import com.testogen.app.ui.components.QuestionCard
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.money
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Вопрос на своём месте в варианте. [question] = null — вопрос удалён из банка. */
data class TestSlot(val item: TestItemEntity, val question: QuestionEntity?)

/** Состояние окна замены. */
sealed interface ReplaceState {
    data object Choosing : ReplaceState
    data object Working : ReplaceState
    data class Failed(val message: String) : ReplaceState
}

class TestEditorViewModel(private val c: AppContainer, private val testId: Long) : ViewModel() {
    val test: StateFlow<TestEntity?> = c.database.tests().observe(testId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Варианты: список мест с вопросами. */
    val variants: StateFlow<List<List<TestSlot>>?> = combine(
        c.database.tests().observeItems(testId),
        c.database.questions().observeForTest(testId),
    ) { items, questions ->
        val byId = questions.associateBy { it.id }
        items.groupBy { it.variant }.toSortedMap().values.map { list -> list.sortedBy { it.position }.map { TestSlot(it, byId[it.questionId]) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var replacing by mutableStateOf<TestSlot?>(null)
        private set
    var replaceState by mutableStateOf<ReplaceState>(ReplaceState.Choosing)
        private set
    var lastCost by mutableDoubleStateOf(0.0)
        private set

    fun remove(slot: TestSlot) = viewModelScope.launch { c.tests.removeItem(slot.item) }
    fun restore(slot: TestSlot) = viewModelScope.launch { c.tests.restoreItem(slot.item) }
    fun rename(title: String) = viewModelScope.launch { c.database.tests().rename(testId, title.trim().ifBlank { "Тест" }) }
    fun deleteTest(onDone: () -> Unit) = viewModelScope.launch { c.database.tests().delete(testId); onDone() }

    fun openReplace(slot: TestSlot) {
        replacing = slot
        replaceState = ReplaceState.Choosing
    }

    fun closeReplace() {
        if (replaceState != ReplaceState.Working) replacing = null
    }

    /** Сколько вопросов той же темы есть в банке и не входят в тест. */
    suspend fun bankAlternatives(slot: TestSlot): Int {
        val q = slot.question ?: return 0
        val used = variants.value.orEmpty().flatten().map { it.item.questionId }
        return c.database.questions().unusedInTopic(q.topicKey, used).size
    }

    /**
     * Заменить вопрос. [fromBank] — взять неиспользованный вопрос той же темы (сложность — по причине),
     * иначе нейросеть составит новый с учётом причины. Причина сохраняется: из накопившихся причин
     * позже сами собой дополняются правила для нейросети.
     */
    fun replace(reason: String, target: Difficulty, fromBank: Boolean) {
        val slot = replacing ?: return
        val old = slot.question ?: return
        replaceState = ReplaceState.Working
        // В области приложения: замена доделается, даже если окно закроют
        c.appScope.launch {
            try {
                val newId = if (fromBank) {
                    val used = variants.value.orEmpty().flatten().map { it.item.questionId }
                    val options = c.database.questions().unusedInTopic(old.topicKey, used)
                    (options.firstOrNull { it.difficulty == target.code } ?: options.firstOrNull())?.id
                        ?: throw AiException("В банке нет других вопросов этой темы")
                } else {
                    val r = c.generator.replaceOne(old, reason, target, c.prefs.review.first())
                    lastCost = r.costUsd
                    r.questionId
                }
                c.tests.replace(slot.item, newId)
                if (reason.isNotBlank()) {
                    c.database.replacementReasons().insert(
                        ReplacementReasonEntity(questionText = old.text, reason = reason, createdAt = System.currentTimeMillis()),
                    )
                    // Накопилось — дополнить правила (тихо, ошибки не мешают)
                    if (c.database.replacementReasons().unprocessedCount() >= RULES_BATCH) runCatching { c.rules.learnFromReasons() }
                }
                replaceState = ReplaceState.Choosing
                replacing = null
            } catch (e: AiException) {
                replaceState = ReplaceState.Failed(e.message ?: "Не получилось")
            } catch (e: Exception) {
                replaceState = ReplaceState.Failed("Не получилось: ${e.message}")
            }
        }
    }

    companion object {
        const val RULES_BATCH = 5
    }
}

/** Тест: все варианты, проверка, замена, правка и удаление вопросов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestEditorScreen(
    testId: Long,
    onBack: () -> Unit,
    onEditQuestion: (Long) -> Unit,
    onSource: (QuestionEntity) -> Unit,
    onExport: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TestEditorViewModel = viewModel(key = "test-$testId") { TestEditorViewModel(container, testId) }
    val test by vm.test.collectAsStateWithLifecycle()
    val variants by vm.variants.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding
    var current by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val list = variants.orEmpty()
    val variant = list.getOrNull(current).orEmpty()
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { BackHeader(test?.title ?: "Тест", onBack) }
            item(key = "info") {
                Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        test?.let { "${it.variantsCount} ${plural(it.variantsCount, "вариант", "варианта", "вариантов")} × ${it.questionsPerVariant} ${plural(it.questionsPerVariant, "вопрос", "вопроса", "вопросов")}" }.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { renaming = true }) { Text("Переименовать") }
                }
            }
            item(key = "variants") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = pad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list.size, key = { it }) { i ->
                        ChoicePill("Вариант ${i + 1}", i == current, { current = i; scope.launch { listState.scrollToItem(2) } })
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Нажмите на вопрос, чтобы проверить ответы, заменить или изменить его.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextHint,
                    modifier = Modifier.padding(horizontal = pad),
                )
            }
            itemsIndexed(variant, key = { _, s -> "v$current-${s.item.position}-${s.item.questionId}" }, contentType = { _, _ -> "slot" }) { i, slot ->
                val q = slot.question
                if (q == null) {
                    Text(
                        "${i + 1}. Вопрос удалён из банка",
                        color = Palette.Error,
                        modifier = Modifier.padding(horizontal = pad),
                    )
                } else {
                    QuestionCard(
                        q,
                        number = i + 1,
                        onReplace = { vm.openReplace(slot) },
                        onEdit = { onEditQuestion(q.id) },
                        onSource = { onSource(q) },
                        onDelete = {
                            vm.remove(slot)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val r = snackbar.showSnackbar("Вопрос убран из варианта ${current + 1}", actionLabel = "Вернуть", duration = SnackbarDuration.Long)
                                if (r == SnackbarResult.ActionPerformed) vm.restore(slot)
                            }
                        },
                        modifier = Modifier.padding(horizontal = pad).animateItem(fadeInSpec = tween(250), placementSpec = tween(300), fadeOutSpec = tween(200)),
                    )
                }
            }
            item(key = "delete") {
                TextButton(onClick = { deleting = true }, modifier = Modifier.padding(horizontal = pad)) {
                    Text("Удалить тест", color = Palette.TextSecondary)
                }
            }
        }
        // Внизу всегда под рукой: файлы теста
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = pad, vertical = 12.dp),
        ) {
            PrimaryButton("Скачать и отправить", { onExport(testId) }, Modifier.fillMaxWidth(), icon = AppIcons.Document)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }

    vm.replacing?.let { slot ->
        ModalBottomSheet(
            onDismissRequest = vm::closeReplace,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Palette.Surface,
        ) {
            ReplaceSheet(vm, slot)
        }
    }

    if (renaming) {
        var title by remember { mutableStateOf(test?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Название теста") },
            text = { AppTextField(title, { title = it }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) },
            confirmButton = { TextButton(onClick = { vm.rename(title); renaming = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Отмена") } },
            containerColor = Palette.Surface,
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Удалить тест?") },
            text = { Text("Вопросы останутся в банке, удалится только этот набор вариантов.") },
            confirmButton = { TextButton(onClick = { deleting = false; vm.deleteTest(onBack) }) { Text("Удалить", color = Palette.Error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } },
            containerColor = Palette.Surface,
        )
    }
}

private val REASONS = listOf(
    "Слишком сложный",
    "Слишком лёгкий",
    "Не по теме",
    "Ошибка в ответе",
    "Неоднозначный",
    "Этого не проходили",
    "Повторяет другой вопрос",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplaceSheet(vm: TestEditorViewModel, slot: TestSlot) {
    val q = slot.question ?: return
    var reason by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    var bankCount by remember { mutableIntStateOf(-1) }
    androidx.compose.runtime.LaunchedEffect(slot) { bankCount = vm.bankAlternatives(slot) }
    val fullReason = listOf(reason, custom.trim()).filter { it.isNotBlank() }.joinToString(". ")
    val current = Difficulty.of(q.difficulty)
    val target = when (reason) {
        "Слишком сложный" -> Difficulty.of((current.code - 1).coerceAtLeast(1))
        "Слишком лёгкий" -> Difficulty.of((current.code + 1).coerceAtMost(3))
        else -> current
    }

    Column(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 20.dp),
    ) {
        Text("Замена вопроса", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(q.text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, maxLines = 3)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            vm.replaceState,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            contentKey = { it::class },
            label = "replace",
        ) { state ->
            when (state) {
                ReplaceState.Working -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                    PulsingBadge(AppIcons.Sparkle, active = true, size = 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Придумываю замену…", style = MaterialTheme.typography.titleMedium)
                        Text("Учитываю причину: ${fullReason.ifBlank { "без причины" }}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                    }
                }
                else -> Column {
                    Text("Почему заменить?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        REASONS.forEach { r -> ChoicePill(r, reason == r, { reason = if (reason == r) "" else r }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    AppTextField(custom, { custom = it }, placeholder = "Или своими словами", minLines = 1)
                    if (state is ReplaceState.Failed) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, color = Palette.Error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        "Новый вопрос от нейросети",
                        { vm.replace(fullReason, target, fromBank = false) },
                        Modifier.fillMaxWidth(),
                        icon = AppIcons.Sparkle,
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        if (bankCount > 0) "Взять из банка (есть $bankCount)" else "В банке нет других вопросов темы",
                        { vm.replace(fullReason, target, fromBank = true) },
                        Modifier.fillMaxWidth(),
                        enabled = bankCount > 0,
                    )
                    if (vm.lastCost > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text("Прошлая замена стоила ${money(vm.lastCost)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextHint)
                    }
                }
            }
        }
    }
}
