package com.testogen.app.ui.textbooks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.data.db.SectionBlockEntity
import com.testogen.app.data.db.TextbookSectionEntity
import com.testogen.app.textbook.BlockKind
import com.testogen.app.textbook.SectionText
import com.testogen.app.textbook.TextbookAnalyzer
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Строка экрана чтения. */
sealed interface ReaderItem {
    val key: String

    data class Page(val pdfPage: Int, val bookPage: Int?) : ReaderItem {
        override val key get() = "page-$pdfPage"
    }

    /** [highlight] — подсвечиваемый кусок внутри блока (позиции в тексте блока). */
    data class Block(val block: SectionBlockEntity, val highlight: IntRange?) : ReaderItem {
        override val key get() = "b-${block.id}"
    }
}

class ReaderViewModel(
    private val c: AppContainer,
    private val sectionId: Long,
    private val start: Int,
    private val end: Int,
    private val query: String,
) : ViewModel() {
    var section by mutableStateOf<TextbookSectionEntity?>(null)
        private set
    var format by mutableStateOf("")
        private set
    var items by mutableStateOf<List<ReaderItem>>(emptyList())
        private set
    /** Индекс строки с подсветкой — к ней прокручивается экран. */
    var focusIndex by mutableIntStateOf(-1)
        private set

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        val dao = c.database.textbooks()
        val s = dao.section(sectionId) ?: return@launch
        section = s
        format = dao.get(s.textbookId)?.format.orEmpty()
        val blocks = dao.blocks(sectionId)
        val built = withContext(Dispatchers.Default) { buildItems(blocks) }
        items = built.first
        focusIndex = built.second
    }

    private fun buildItems(blocks: List<SectionBlockEntity>): Pair<List<ReaderItem>, Int> {
        val text = SectionText.build(blocks, includeSide = true)
        val range: IntRange? = when {
            start >= 0 && end > start -> start until end
            query.isNotBlank() -> text.full.indexOf(query, ignoreCase = true).takeIf { it >= 0 }?.let { it until it + query.length }
            else -> null
        }
        val result = mutableListOf<ReaderItem>()
        var focus = -1
        var lastPage = -1
        for (b in blocks) {
            if (BlockKind.of(b.kind) == BlockKind.KEYWORDS) continue
            if (b.pdfPage != lastPage && format != "txt" && format != "docx") {
                result += ReaderItem.Page(b.pdfPage, b.bookPage)
                lastPage = b.pdfPage
            }
            val bStart = text.blockStarts[b.id] ?: 0
            val bEnd = bStart + b.text.length
            val hl = range?.let { r ->
                val from = maxOf(r.first, bStart)
                val to = minOf(r.last + 1, bEnd)
                if (from < to) (from - bStart) until (to - bStart) else null
            }
            if (hl != null && focus < 0) focus = result.size
            result += ReaderItem.Block(b, hl)
        }
        return result to focus
    }

    /** Разделить раздел: с этого блока начинается новый. */
    fun splitAt(block: SectionBlockEntity, onDone: () -> Unit) = viewModelScope.launch {
        val s = section ?: return@launch
        val title = TextbookAnalyzer.prettyTitle(block.text.take(70).substringBefore('.'))
        c.database.textbooks().split(s, block.ord, title)
        onDone()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SectionReaderScreen(
    sectionId: Long,
    highlightStart: Int,
    highlightEnd: Int,
    query: String,
    onBack: () -> Unit,
    onOpenPdfPage: (textbookId: Long, pdfPage: Int) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: ReaderViewModel = viewModel(key = "reader-$sectionId-$highlightStart-$query") {
        ReaderViewModel(container, sectionId, highlightStart, highlightEnd, query)
    }
    val pad = LocalDimens.current.screenPadding
    val state = rememberLazyListState()
    var splitBlock by remember { mutableStateOf<SectionBlockEntity?>(null) }
    val section = vm.section
    val isPdf = vm.format == "pdf"

    // Подсветка «вспыхивает» и плавно успокаивается
    val flash = remember { Animatable(1f) }
    LaunchedEffect(vm.focusIndex) {
        if (vm.focusIndex >= 0) {
            state.scrollToItem(vm.focusIndex + 2, scrollOffset = -200)
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1600))
        }
    }

    // Страница PDF, которая сейчас наверху экрана
    val currentPage by remember {
        derivedStateOf {
            val idx = (state.firstVisibleItemIndex - 2).coerceAtLeast(0)
            vm.items.take(idx + 1).lastOrNull { it is ReaderItem.Page }?.let { (it as ReaderItem.Page) }
                ?: vm.items.firstOrNull { it is ReaderItem.Page } as? ReaderItem.Page
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            item(key = "header") { BackHeader(section?.label?.ifEmpty { null } ?: "Учебник", onBack) }
            item(key = "title") {
                Column(Modifier.padding(horizontal = pad)) {
                    Text(section?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    val keywords = section?.keywords.orEmpty().lines().filter { it.isNotBlank() }
                    if (keywords.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Ключевые понятия", style = MaterialTheme.typography.titleSmall, color = Palette.TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            keywords.forEach { k ->
                                Surface(color = Palette.PrimarySoft, shape = MaterialTheme.shapes.extraSmall) {
                                    Text(k, style = MaterialTheme.typography.labelMedium, color = Palette.OnPrimarySoft, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(vm.items, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ReaderItem.Page -> PageMark(item, isPdf, { section?.let { onOpenPdfPage(it.textbookId, item.pdfPage) } }, Modifier.padding(horizontal = pad))
                    is ReaderItem.Block -> BlockView(
                        item,
                        flash = { flash.value },
                        onLongPress = { splitBlock = item.block },
                        modifier = Modifier.padding(horizontal = pad, vertical = 6.dp),
                    )
                }
            }
        }
        if (isPdf) {
            currentPage?.let { page ->
                SecondaryButton(
                    "Страница учебника" + (page.bookPage?.let { " $it" } ?: ""),
                    { section?.let { onOpenPdfPage(it.textbookId, page.pdfPage) } },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
                )
            }
        }
    }

    splitBlock?.let { block ->
        AlertDialog(
            onDismissRequest = { splitBlock = null },
            title = { Text("Начать здесь новый раздел?") },
            text = { Text("Текст с этого места станет отдельным разделом — его можно будет переименовать и пронумеровать в структуре учебника.") },
            confirmButton = {
                TextButton(onClick = { vm.splitAt(block) { onBack() }; splitBlock = null }) { Text("Разделить") }
            },
            dismissButton = { TextButton(onClick = { splitBlock = null }) { Text("Отмена") } },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun PageMark(page: ReaderItem.Page, isPdf: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Palette.OutlineSoft)
        TextButton(onClick = onOpen, enabled = isPdf) {
            Text(
                page.bookPage?.let { "стр. $it" } ?: "стр. PDF ${page.pdfPage + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
            )
        }
        HorizontalDivider(Modifier.weight(1f), color = Palette.OutlineSoft)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockView(item: ReaderItem.Block, flash: () -> Float, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val b = item.block
    val kind = BlockKind.of(b.kind)
    val raw = if (kind == BlockKind.HEADING) TextbookAnalyzer.prettyTitle(b.text) else b.text
    // Анимация подсветки читается только подсвеченным блоком — остальные не перерисовываются
    val text: AnnotatedString = if (item.highlight == null) AnnotatedString(raw) else highlighted(raw, item.highlight, flash())
    val clickable = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
    when (kind) {
        BlockKind.HEADING -> Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Primary,
            modifier = modifier.then(clickable).padding(top = 10.dp),
        )
        BlockKind.SIDE -> Surface(color = Palette.SurfaceSoft, shape = MaterialTheme.shapes.small, modifier = modifier.then(clickable)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.padding(14.dp))
        }
        BlockKind.QUESTIONS -> Surface(color = Palette.PrimarySoft.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small, modifier = modifier.then(clickable)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.OnPrimarySoft, modifier = Modifier.padding(14.dp))
        }
        BlockKind.EXTRA -> Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.TextHint, fontStyle = FontStyle.Italic, modifier = modifier.then(clickable))
        else -> Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 29.sp),
            modifier = modifier.then(clickable),
        )
    }
}

private fun highlighted(text: String, range: IntRange?, flash: Float): AnnotatedString = buildAnnotatedString {
    if (range == null || range.first >= text.length) {
        append(text)
        return@buildAnnotatedString
    }
    val end = (range.last + 1).coerceAtMost(text.length)
    append(text.substring(0, range.first))
    withStyle(SpanStyle(background = lerp(Palette.AccentSoft, Palette.Accent.copy(alpha = 0.55f), flash))) {
        append(text.substring(range.first, end))
    }
    append(text.substring(end))
}
