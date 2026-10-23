package com.testogen.app.ui.textbooks

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.data.db.GenerationJobEntity
import com.testogen.app.data.db.TextbookSummary
import com.testogen.app.textbook.ImportException
import com.testogen.app.textbook.ImportJobParams
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.EmptyState
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.PulsingBadge
import com.testogen.app.ui.components.ScreenHeader
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SettingsButton
import com.testogen.app.ui.components.SkeletonCard
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class TextbooksViewModel(private val c: AppContainer) : ViewModel() {
    val books: StateFlow<List<TextbookSummary>?> = c.database.textbooks().observeSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Идущие загрузки по id учебника. */
    val imports: StateFlow<Map<Long, GenerationJobEntity>> = c.database.jobs().observeImports()
        .map { jobs ->
            jobs.mapNotNull { j ->
                runCatching { Json.decodeFromString(ImportJobParams.serializer(), j.params).textbookId to j }.getOrNull()
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var copying by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun import(uri: Uri) {
        if (copying) return
        copying = true
        error = null
        viewModelScope.launch {
            try {
                val id = c.importer.copyIn(uri)
                val title = c.database.textbooks().get(id)?.title.orEmpty()
                c.jobs.startImport(id, title)
            } catch (e: ImportException) {
                error = e.message
            } catch (e: Exception) {
                error = "Не удалось скопировать файл: ${e.message}"
            }
            copying = false
        }
    }

    fun delete(id: Long) = viewModelScope.launch { c.importer.delete(id) }
}

@Composable
fun TextbooksScreen(onOpenSettings: () -> Unit, onOpenTextbook: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm: TextbooksViewModel = viewModel { TextbooksViewModel(container) }
    val books by vm.books.collectAsStateWithLifecycle()
    val imports by vm.imports.collectAsStateWithLifecycle()
    val pad = LocalDimens.current.screenPadding
    var toDelete by remember { mutableStateOf<TextbookSummary?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    val pick = {
        picker.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain",
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.gap),
    ) {
        item(key = "header", contentType = "header") {
            ScreenHeader(title = "Учебники", subtitle = "Вопросы по параграфам") {
                SettingsButton(onOpenSettings)
            }
        }
        val list = books
        if (list != null && list.isNotEmpty()) {
            item(key = "add", contentType = "add") {
                PrimaryButton(
                    if (vm.copying) "Копирую файл…" else "Загрузить учебник",
                    pick,
                    Modifier.fillMaxWidth().padding(horizontal = pad),
                    icon = AppIcons.Book,
                    enabled = !vm.copying,
                )
            }
        }
        vm.error?.let { message ->
            item(key = "error", contentType = "error") {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = Palette.Error, modifier = Modifier.padding(horizontal = pad))
            }
        }
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty", contentType = "empty") {
                if (vm.copying) {
                    SkeletonCard(Modifier.padding(horizontal = pad))
                } else {
                    EmptyState(
                        icon = AppIcons.Book,
                        title = "Учебников пока нет",
                        text = "Загрузите учебник в PDF, DOCX или TXT — приложение разберёт его на главы и параграфы и сохранит на телефоне.",
                        actionText = "Загрузить учебник",
                        onAction = pick,
                    )
                }
            }
            else -> items(list, key = { it.id }, contentType = { "book" }) { book ->
                BookCard(
                    book = book,
                    job = imports[book.id],
                    onOpen = { onOpenTextbook(book.id) },
                    onDelete = { toDelete = book },
                    modifier = Modifier.padding(horizontal = pad).animateItem(),
                )
            }
        }
    }

    toDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить учебник?") },
            text = { Text("«${book.title}» будет удалён с телефона. Вопросы, созданные по нему, останутся в банке.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(book.id); toDelete = null }) { Text("Удалить", color = Palette.Error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun BookCard(
    book: TextbookSummary,
    job: GenerationJobEntity?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = book.status == "ready"
    SoftCard(modifier.fillMaxWidth(), onClick = if (ready) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingBadge(AppIcons.Book, active = book.status == "importing", size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    when (book.status) {
                        "ready" -> listOfNotNull(
                            book.grade?.let { "$it класс" },
                            "${book.paragraphs} ${plural(book.paragraphs, "параграф", "параграфа", "параграфов")}",
                            book.format.uppercase(),
                        ).joinToString(" · ")
                        "importing" -> job?.stage?.ifBlank { null } ?: "Готовлю…"
                        "scan" -> "PDF без текста (скан)"
                        else -> "Не получилось загрузить"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (book.status == "failed" || book.status == "scan") Palette.Error else Palette.TextSecondary,
                )
            }
            if (ready) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.TextSecondary)
        }
        if (book.status == "importing") {
            val target = if (job != null && job.total > 0) job.saved.toFloat() / job.total else 0f
            val progress by animateFloatAsState(target, tween(500), label = "import")
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Palette.Primary,
                trackColor = Palette.SurfaceSunken,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Большой учебник читается пару минут. Можно свернуть приложение.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextHint,
            )
        }
        if (book.status == "failed" || book.status == "scan") {
            book.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Удалить", onDelete)
        }
        if (ready) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Удалить", color = Palette.TextSecondary) }
            }
        }
    }
}
