package com.testogen.app.ui.export

import android.content.Intent
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.export.AndroidText
import com.testogen.app.export.DocLayout
import com.testogen.app.export.DocQuestion
import com.testogen.app.export.DocVariant
import com.testogen.app.export.DocxBuilder
import com.testogen.app.export.DrawItem
import com.testogen.app.export.LayoutMode
import com.testogen.app.export.Paginator
import com.testogen.app.export.PreviewPage
import com.testogen.app.export.buildPreview
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.ChoicePill
import com.testogen.app.ui.components.CountStepper
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SkeletonCard
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.components.softShadow
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette
import com.testogen.app.util.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ExportViewModel(private val c: AppContainer, private val testId: Long, context: android.content.Context) : ViewModel() {
    private val text = AndroidText(context.applicationContext)
    private val paginator = Paginator(text)
    private val json = Json { ignoreUnknownKeys = true }

    var title by mutableStateOf("")
        private set
    var variants by mutableStateOf<List<DocVariant>>(emptyList())
        private set
    var layout by mutableStateOf<DocLayout?>(null)
        private set
    var pages by mutableStateOf<List<PreviewPage>>(emptyList())
        private set
    var busy by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)
    private var autoLayout: DocLayout? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val test = c.database.tests().get(testId) ?: return@launch
            title = test.title
            val items = c.database.tests().items(testId)
            val questions = c.database.questions().observeForTest(testId).first().associateBy { it.id }
            variants = items.groupBy { it.variant }.toSortedMap().map { (v, list) ->
                DocVariant(
                    v + 1,
                    list.sortedBy { it.position }.mapNotNull { questions[it.questionId] }.mapIndexed { i, q ->
                        DocQuestion(i + 1, q.text, listOf(q.option1, q.option2, q.option3, q.option4), q.correctIndex)
                    },
                )
            }
            val auto = withContext(Dispatchers.Default) { paginator.auto(variants) }
            autoLayout = auto
            val saved = test.layoutJson?.let { runCatching { json.decodeFromString(DocLayout.serializer(), it) }.getOrNull() }
            apply(saved ?: auto, save = false)
        }
    }

    /** Новая раскладка → пересчёт страниц в фоне (с небольшой задержкой при быстрых нажатиях). */
    fun apply(newLayout: DocLayout, save: Boolean = true) {
        layout = newLayout
        job?.cancel()
        job = viewModelScope.launch {
            busy = true
            delay(120)
            pages = withContext(Dispatchers.Default) { buildPreview(paginator.paginate(variants, newLayout), newLayout, text) }
            busy = false
            if (save) c.database.tests().setLayout(testId, if (newLayout.auto) null else json.encodeToString(DocLayout.serializer(), newLayout))
        }
    }

    fun change(transform: (DocLayout) -> DocLayout) {
        val l = layout ?: return
        apply(transform(l).copy(auto = false))
    }

    fun resetAuto() {
        autoLayout?.let { apply(it) }
    }

    fun testBytes() = DocxBuilder.test(title, variants, layout ?: autoLayout!!)
    fun answersBytes() = DocxBuilder.answers(title, variants)

    fun fileName(kind: String) = "${title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60)} — $kind.docx"

    fun saveTo(context: android.content.Context, uri: Uri, answers: Boolean) = viewModelScope.launch {
        message = try {
            withContext(Dispatchers.IO) {
                val bytes = if (answers) answersBytes() else testBytes()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("нет доступа")
            }
            if (answers) "Файл с ответами сохранён" else "Файл теста сохранён"
        } catch (e: Exception) {
            "Не получилось сохранить: ${e.message}"
        }
    }

    /** Оба файла — через «Поделиться» (почта, мессенджер, диск…). */
    fun share(context: android.content.Context) = viewModelScope.launch {
        try {
            val uris = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val test = File(dir, fileName("тест")).apply { writeBytes(testBytes()) }
                val answers = File(dir, fileName("ответы")).apply { writeBytes(answersBytes()) }
                arrayListOf(test, answers).map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Отправить тест и ответы"))
        } catch (e: Exception) {
            message = "Не получилось подготовить файлы: ${e.message}"
        }
    }
}

/** Предпросмотр файла теста (совпадает с тем, что увидит Word), настройки раскладки, скачивание и «Поделиться». */
@Composable
fun ExportScreen(testId: Long, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: ExportViewModel = viewModel(key = "export-$testId") { ExportViewModel(container, testId, context) }
    val pad = LocalDimens.current.screenPadding
    val saveTest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX)) { uri -> uri?.let { vm.saveTo(context, it, answers = false) } }
    val saveAnswers = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX)) { uri -> uri?.let { vm.saveTo(context, it, answers = true) } }
    val layout = vm.layout
    var zoomed by remember { mutableStateOf<PreviewPage?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 190.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { BackHeader("Файлы теста", onBack) }
            item(key = "settings") {
                if (layout == null) {
                    SkeletonCard(Modifier.padding(horizontal = pad))
                } else {
                    LayoutCard(layout, vm, Modifier.padding(horizontal = pad))
                }
            }
            item(key = "label") {
                Text(
                    if (vm.busy) "Раскладываю по страницам…" else "Так будет выглядеть файл: ${vm.pages.size} ${plural(vm.pages.size, "страница", "страницы", "страниц")}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(horizontal = pad),
                )
            }
            itemsIndexed(vm.pages, key = { i, _ -> "page-$i" }, contentType = { _, _ -> "page" }) { i, page ->
                PageView(page, i + 1, Modifier.padding(horizontal = pad).clickable { zoomed = page })
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = pad, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Скачать тест", { saveTest.launch(vm.fileName("тест")) }, Modifier.weight(1f), enabled = layout != null)
                SecondaryButton("Ответы", { saveAnswers.launch(vm.fileName("ответы")) }, Modifier.weight(1f), enabled = layout != null)
            }
            SecondaryButton("Отправить оба файла", { vm.share(context) }, Modifier.fillMaxWidth(), icon = AppIcons.Document, enabled = layout != null)
        }
    }

    zoomed?.let { page -> ZoomedPage(page) { zoomed = null } }
}

/** Страница во весь экран: двумя пальцами — увеличить, двойное касание — крупнее/обратно. */
@Composable
private fun ZoomedPage(page: PreviewPage, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE63B3633))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = androidx.compose.ui.geometry.Offset.Zero },
                        onTap = { onClose() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            PageView(
                page,
                0,
                Modifier.padding(12.dp).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}

private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
private fun LayoutCard(layout: DocLayout, vm: ExportViewModel, modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
        Text("Раскладка", style = MaterialTheme.typography.titleMedium)
        if (layout.auto) {
            Text("Подобрана автоматически — можно поменять", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
        } else {
            TextButton(onClick = vm::resetAuto, contentPadding = PaddingValues(0.dp)) { Text("Вернуть, как предлагает приложение") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Несколько на листе", layout.mode == LayoutMode.COMPACT, { vm.change { it.copy(mode = LayoutMode.COMPACT) } }, Modifier.weight(1f), horizontalPadding = 4.dp, singleLine = false)
            ChoicePill("Вариант на странице", layout.mode == LayoutMode.PER_PAGE, { vm.change { it.copy(mode = LayoutMode.PER_PAGE) } }, Modifier.weight(1f), horizontalPadding = 4.dp, singleLine = false)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Книжная", !layout.landscape, { vm.change { it.copy(landscape = false) } }, Modifier.weight(1f), horizontalPadding = 4.dp)
            ChoicePill("Альбомная", layout.landscape, { vm.change { it.copy(landscape = true) } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("1 колонка", layout.columns == 1, { vm.change { it.copy(columns = 1) } }, Modifier.weight(1f), horizontalPadding = 4.dp)
            ChoicePill("2 колонки", layout.columns == 2, { vm.change { it.copy(columns = 2) } }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Шрифт", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            CountStepper(layout.fontSize.toInt(), { v -> vm.change { it.copy(fontSize = v.toFloat()) } }, range = 10..14)
        }
        AnimatedVisibility(layout.mode == LayoutMode.COMPACT) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Линии отреза", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = layout.cutLines,
                    onCheckedChange = { v -> vm.change { it.copy(cutLines = v) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, uncheckedTrackColor = Palette.SurfaceSunken),
                )
            }
        }
    }
}

/** Страница предпросмотра: белый лист с тенью, текст шрифтом, совпадающим с Times New Roman. */
@Composable
private fun PageView(page: PreviewPage, number: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text = remember { AndroidText(context.applicationContext) }
    val paints = remember {
        val regular = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = text.typeface(false)
            color = android.graphics.Color.BLACK
            fontFeatureSettings = AndroidText.NO_KERNING
        }
        val bold = Paint(regular).apply { typeface = text.typeface(true) }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.rgb(140, 140, 140)
            strokeWidth = 0.75f
            pathEffect = DashPathEffect(floatArrayOf(3f, 2f), 0f)
        }
        Triple(regular, bold, dash)
    }
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(page.width / page.height)
                .softShadow(MaterialTheme.shapes.extraSmall, elevation = 4.dp),
        ) {
            drawRect(Color.White)
            val scale = size.width / page.width
            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                canvas.save()
                canvas.scale(scale, scale)
                for (item in page.items) {
                    when (item) {
                        is DrawItem.Line -> {
                            val paint = if (item.bold) paints.second else paints.first
                            paint.textSize = item.size
                            val x = if (item.center) item.x + (item.width - paint.measureText(item.text)) / 2 else item.x
                            canvas.drawText(item.text, x, item.baseline, paint)
                        }
                        is DrawItem.DashedRect -> canvas.drawRect(item.x, item.y, item.x + item.width, item.y + item.height, paints.third)
                    }
                }
                canvas.restore()
            }
        }
        if (number > 0) {
            Text("$number · нажмите, чтобы рассмотреть", style = MaterialTheme.typography.bodySmall, color = Palette.TextHint, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        }
    }
}
