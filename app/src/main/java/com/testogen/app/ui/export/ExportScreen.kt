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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.BuildConfig
import com.testogen.app.export.AndroidText
import com.testogen.app.export.AnswersPlacement
import com.testogen.app.export.DocLayout
import com.testogen.app.export.DocQuestion
import com.testogen.app.export.DocVariant
import com.testogen.app.export.DocxBuilder
import com.testogen.app.export.DrawItem
import com.testogen.app.export.LayoutMode
import com.testogen.app.export.Paginator
import com.testogen.app.export.PreviewPage
import com.testogen.app.export.buildPreview
import com.testogen.app.export.layoutJson
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.AppTextField
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.BottomPanel
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
    /** Название файла, которое мама может поменять; по умолчанию — название теста и дата. */
    var fileTitle by mutableStateOf("")
    private var autoLayout: DocLayout? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val test = c.database.tests().get(testId) ?: return@launch
            title = test.title
            fileTitle = "${test.title.trim().take(60).trim()} — ${SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())}"
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
            // Сохранено только место ответов — раскладка остаётся автоматической
            apply(if (saved == null) auto else if (saved.auto) auto.copy(answers = saved.answers) else saved, save = false)
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
            if (save) {
                val keep = !newLayout.auto || newLayout.answers != AnswersPlacement.SEPARATE
                c.database.tests().setLayout(testId, if (keep) json.encodeToString(DocLayout.serializer(), newLayout) else null)
            }
        }
    }

    fun change(transform: (DocLayout) -> DocLayout) {
        val l = layout ?: return
        apply(transform(l).copy(auto = false))
    }

    fun resetAuto() {
        autoLayout?.let { apply(it.copy(answers = layout?.answers ?: AnswersPlacement.SEPARATE)) }
    }

    /** Место ответов — отдельная настройка: «как предлагает приложение» её не сбрасывает. */
    fun setAnswers(placement: AnswersPlacement) {
        val l = layout ?: return
        apply(l.copy(answers = placement))
    }

    val answersInside get() = (layout?.answers ?: AnswersPlacement.SEPARATE) != AnswersPlacement.SEPARATE

    fun testBytes() = DocxBuilder.test(title, variants, layout ?: autoLayout!!, paginator)
    fun answersBytes() = DocxBuilder.answers(title, variants)

    /** Имя без запрещённых в именах файлов знаков; буквы не меняются — кириллица остаётся кириллицей. */
    private fun baseName(): String =
        fileTitle.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(120).trim()
            .ifEmpty { title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(60).ifEmpty { "Тест" } }

    fun testFileName() = "${baseName()}.docx"
    fun answersFileName() = "${baseName()} — ответы.docx"

    fun saveTo(context: android.content.Context, uri: Uri, answers: Boolean) = viewModelScope.launch {
        message = try {
            withContext(Dispatchers.IO) {
                val bytes = if (answers) answersBytes() else testBytes()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("нет доступа")
            }
            if (answers) "Сохранён файл «${answersFileName()}»" else "Сохранён файл «${testFileName()}»"
        } catch (e: Exception) {
            "Не получилось сохранить: ${e.message}"
        }
    }

    /** Файлы — через «Поделиться» (почта, мессенджер, диск…): тест и ответы или один файл, если ответы внутри. */
    fun share(context: android.content.Context) = viewModelScope.launch {
        try {
            val single = answersInside
            val uris = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val files = mutableListOf(File(dir, testFileName()).apply { writeBytes(testBytes()) })
                if (!single) files += File(dir, answersFileName()).apply { writeBytes(answersBytes()) }
                // Только для проверки разработчиком: раскладка предпросмотра для сверки с LibreOffice (tools/layout_check.py)
                if (BuildConfig.DEBUG) layout?.let { File(dir, "layout.json").writeText(layoutJson(paginator.paginate(variants, it), it)) }
                files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
            }
            val intent = if (single) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
                .setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, if (single) "Отправить тест" else "Отправить тест и ответы"))
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

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp),
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
            item(key = "file") {
                if (layout != null) FileCard(vm, Modifier.padding(horizontal = pad))
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
        BottomPanel {
            vm.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Primary) }
            if (vm.answersInside) {
                // Ответы внутри файла теста — один файл: скачать или отправить
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Скачать тест", { saveTest.launch(vm.testFileName()) }, Modifier.weight(1f), enabled = layout != null, singleLine = true)
                    SecondaryButton("Отправить", { vm.share(context) }, Modifier.weight(1f), icon = AppIcons.Document, enabled = layout != null, singleLine = true)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Скачать тест", { saveTest.launch(vm.testFileName()) }, Modifier.weight(1f), enabled = layout != null, singleLine = true)
                    SecondaryButton("Ответы", { saveAnswers.launch(vm.answersFileName()) }, Modifier.weight(1f), enabled = layout != null, singleLine = true)
                }
                SecondaryButton("Отправить оба файла", { vm.share(context) }, Modifier.fillMaxWidth(), icon = AppIcons.Document, enabled = layout != null, singleLine = true)
            }
        }
    }

    zoomed?.let { page -> ZoomedPage(page) { zoomed = null } }
}

/** Страница во весь экран: двумя пальцами — увеличить, двойное касание — крупнее/обратно. */
@Composable
private fun ZoomedPage(page: PreviewPage, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
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
            // Обычный текст-ссылка: длинная подпись переносится, а не обрезается по краям, как в TextButton
            Text(
                "Вернуть, как предлагает приложение",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = vm::resetAuto)
                    .padding(vertical = 8.dp),
            )
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
        // При крупном системном шрифте кнопки «−/+» уходят под подпись, а не ломают слово «Шрифт»
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Шрифт", style = MaterialTheme.typography.bodyLarge)
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

/** Где ответы и как назвать файл. */
@Composable
private fun FileCard(vm: ExportViewModel, modifier: Modifier = Modifier) {
    val answers = vm.layout?.answers ?: AnswersPlacement.SEPARATE
    SoftCard(modifier.fillMaxWidth()) {
        Text("Ответы", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Отдельным файлом", answers == AnswersPlacement.SEPARATE, { vm.setAnswers(AnswersPlacement.SEPARATE) }, Modifier.fillMaxWidth(), singleLine = false)
            ChoicePill("В конце теста, под линией отреза", answers == AnswersPlacement.END, { vm.setAnswers(AnswersPlacement.END) }, Modifier.fillMaxWidth(), singleLine = false)
            ChoicePill("В конце теста, на отдельной странице", answers == AnswersPlacement.NEW_PAGE, { vm.setAnswers(AnswersPlacement.NEW_PAGE) }, Modifier.fillMaxWidth(), singleLine = false)
        }
        Spacer(Modifier.height(16.dp))
        Text("Название файла", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        AppTextField(vm.fileTitle, { vm.fileTitle = it }, placeholder = "Например: Государство, 9 класс", singleLine = true)
        Spacer(Modifier.height(6.dp))
        Text(
            if (vm.answersInside) vm.testFileName() else "${vm.testFileName()}\n${vm.answersFileName()}",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
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
    // Рамка таблицы ответов — сплошная серая, как в файле
    val grid = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.rgb(140, 140, 140)
            strokeWidth = 0.75f
        }
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
                // Ничего не рисуется за краем листа
                canvas.clipRect(0f, 0f, page.width, page.height)
                for (item in page.items) {
                    when (item) {
                        is DrawItem.Line -> {
                            val paint = if (item.bold) paints.second else paints.first
                            paint.textSize = item.size
                            val x = if (item.center) item.x + (item.width - paint.measureText(item.text)) / 2 else item.x
                            canvas.drawText(item.text, x, item.baseline, paint)
                        }
                        is DrawItem.DashedRect -> canvas.drawRect(item.x, item.y, item.x + item.width, item.y + item.height, paints.third)
                        is DrawItem.Segment -> canvas.drawLine(item.x1, item.y1, item.x2, item.y2, if (item.dashed) paints.third else grid)
                    }
                }
                canvas.restore()
            }
        }
        if (number > 0) {
            Text(
                "Страница $number · нажмите, чтобы рассмотреть",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            )
        }
    }
}
