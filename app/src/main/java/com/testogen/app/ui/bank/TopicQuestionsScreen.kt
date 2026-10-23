package com.testogen.app.ui.bank

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.QuestionCard
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TopicQuestionsViewModel(private val c: AppContainer, topicKey: String) : ViewModel() {
    val questions: StateFlow<List<QuestionEntity>?> = c.database.questions().observeByTopic(topicKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(q: QuestionEntity) = viewModelScope.launch { c.questions.delete(q.id) }
    fun restore(q: QuestionEntity) = viewModelScope.launch { c.questions.restore(q) }
}

/** Вопросы одной темы; удаление можно отменить. */
@Composable
fun TopicQuestionsScreen(
    topicKey: String,
    title: String,
    onBack: () -> Unit,
    onGenerateMore: (String) -> Unit,
    onAddManual: (String) -> Unit,
    onEdit: (Long) -> Unit,
    onSource: (QuestionEntity) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TopicQuestionsViewModel = viewModel(key = "topic-$topicKey") { TopicQuestionsViewModel(container, topicKey) }
    val questions by vm.questions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pad = LocalDimens.current.screenPadding
    val list = questions.orEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { BackHeader(title, onBack) }
            item(key = "info") {
                Text(
                    "${list.size} ${plural(list.size, "вопрос", "вопроса", "вопросов")} · нажмите на вопрос, чтобы увидеть ответы",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(horizontal = pad),
                )
            }
            item(key = "actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = pad), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Ещё", { onGenerateMore(title) }, Modifier.weight(1f), icon = AppIcons.Sparkle)
                    SecondaryButton("Вручную", { onAddManual(title) }, Modifier.weight(1f), icon = Icons.Filled.Edit)
                }
            }
            itemsIndexed(list, key = { _, q -> q.id }, contentType = { _, _ -> "question" }) { i, q ->
                QuestionCard(
                    q,
                    number = i + 1,
                    onEdit = { onEdit(q.id) },
                    onSource = { onSource(q) },
                    onDelete = {
                        vm.delete(q)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar("Вопрос удалён", actionLabel = "Вернуть", duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) vm.restore(q)
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = pad)
                        .animateItem(fadeInSpec = tween(300), placementSpec = tween(300), fadeOutSpec = tween(200)),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
