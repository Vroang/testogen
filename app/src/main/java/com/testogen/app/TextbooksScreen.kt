package com.testogen.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Шаг 27: «Мои учебники» — список локального кэша Room.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextbooksScreen(
    onBack: () -> Unit,
    onOpenRange: (String) -> Unit
) {
    val context = LocalContext.current
    val db = (context.applicationContext as TestoGenApp).database
    val books by db.textbookDao().getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var oversizeMb by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<Textbook?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM yy", Locale("ru")) }

    // Шаг 30: при открытии списка — догоаляем всё, что ждёт облака.
    LaunchedEffect(Unit) {
        TextbookRepository.syncAllPending(context)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !uploading) {
            uploading = true
            scope.launch {
                val result = TextbookRepository.uploadTextbook(context, uri)
                uploading = false
                result.fold(
                    onSuccess = { book ->
                        android.widget.Toast.makeText(
                            context,
                            "Учебник загружен: ${book.paragraphCount} параграфов",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { e ->
                        if (e is OversizeException) {
                            oversizeMb = e.mb
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Не удалось загрузить: ${e.message ?: "неизвестная ошибка"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    text = "Мои учебники",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = "Файлы хранятся в облаке. Тап — выбрать диапазон параграфов, долгий тап — удалить.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uploading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (books.isEmpty() && !uploading) {
                Text(
                    text = "Пока нет учебников. Нажмите «+», чтобы загрузить первый (PDF, DOCX или TXT с параграфами §).",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(books) { book ->
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { if (!uploading) onOpenRange(book.id) },
                                    onLongClick = { pendingDelete = book }
                                )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = book.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = book.format.uppercase() + " · " +
                                                book.paragraphCount + " параграфов · " +
                                                dateFormat.format(java.util.Date(book.uploadedAt)) +
                                                if (book.syncStatus != "synced") {
                                                    " · ожидает отправки в облако"
                                                } else {
                                                    ""
                                                },
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                if (!uploading) {
                    picker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain"
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            if (uploading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Add, contentDescription = "Загрузить учебник")
            }
        }
    }

    oversizeMb?.let { mb ->
        AlertDialog(
            onDismissRequest = { oversizeMb = null },
            text = {
                Text(
                    text = "Файл слишком большой ($mb МБ). Максимум для загрузки — 50 МБ. " +
                        "Попробуйте сжать PDF или использовать другую версию файла.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { oversizeMb = null }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            text = {
                Text(
                    text = "Удалить учебник «${book.name}»? Он будет удалён и из облака, и из списка.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = book
                    pendingDelete = null
                    scope.launch {
                        val result = TextbookRepository.deleteTextbook(context, toDelete)
                        result.fold(
                            onSuccess = {
                                android.widget.Toast.makeText(
                                    context,
                                    "Учебник удалён",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            onFailure = { e ->
                                android.widget.Toast.makeText(
                                    context,
                                    "Не удалось удалить: ${e.message ?: "ошибка"}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }) {
                    Text(text = "Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

// Шаг 29: гибкий выбор — по параграфам (галочки) или по страницам,
// тема вопросов, содержание с названиями и страницами.
@Composable
fun TextbookRangeScreen(
    textbookId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TestoGenApp
    val db = app.database
    val settings by app.settingsRepository.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<Textbook?>(null) }
    var paragraphs by remember { mutableStateOf<List<ParagraphRow>?>(null) }
    var mode by rememberSaveable { mutableStateOf("paragraphs") }
    var topic by rememberSaveable { mutableStateOf("") }
    var quickFrom by rememberSaveable { mutableStateOf("") }
    var quickTo by rememberSaveable { mutableStateOf("") }
    var selectedNumbers by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var contentExpanded by rememberSaveable { mutableStateOf(false) }
    var pageFromText by rememberSaveable { mutableStateOf("") }
    var pageToText by rememberSaveable { mutableStateOf("") }
    var countText by rememberSaveable { mutableStateOf("10") }
    var difficulties by rememberSaveable {
        mutableStateOf(arrayListOf("easy", "medium", "hard"))
    }
    var generating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(textbookId) {
        book = db.textbookDao().getById(textbookId)
        // Шаг 30: параграфы читаются из локальной базы — работает
        // без интернета.
        paragraphs = db.paragraphDao().getByTextbook(textbookId).map {
            ParagraphRow(
                number = it.number,
                title = it.title,
                startPage = it.startPage,
                endPage = it.endPage,
                text = it.text
            )
        }
    }

    val rows = paragraphs.orEmpty()
    val isPdf = book?.format == "pdf"
    val quickFromNum = quickFrom.toIntOrNull()
    val quickToNum = quickTo.toIntOrNull()
    val pageFrom = pageFromText.toIntOrNull()
    val pageTo = pageToText.toIntOrNull()
    val count = countText.toIntOrNull() ?: 0

    val matchingPages = if (pageFrom != null && pageTo != null) {
        rows.filter { it.startPage <= pageTo && it.endPage >= pageFrom }
    } else {
        emptyList()
    }
    val pagePreview = when {
        mode != "pages" -> ""
        pageFrom == null || pageTo == null -> ""
        matchingPages.isEmpty() -> "В этом диапазоне нет параграфов"
        else -> "Будут включены: " + matchingPages.joinToString(", ") { "§ ${it.number}" } +
            " (страницы $pageFrom–$pageTo)"
    }
    val selectedSorted = selectedNumbers.toList().sorted()
    val defaultTopic = when {
        mode == "pages" && pageFrom != null && pageTo != null ->
            "${book?.name ?: "Учебник"}, стр. $pageFrom–$pageTo"
        selectedSorted.isNotEmpty() ->
            if (selectedSorted.size == 1) {
                "${book?.name ?: "Учебник"} § ${selectedSorted.first()}"
            } else {
                "${book?.name ?: "Учебник"} § ${selectedSorted.first()}-${selectedSorted.last()}"
            }
        else -> book?.name ?: "Учебник"
    }
    val finalTopic = topic.ifBlank { defaultTopic }
    val canGenerate = when {
        generating -> false
        mode == "paragraphs" -> selectedNumbers.isNotEmpty()
        else -> pageFrom != null && pageTo != null && pageFrom <= pageTo && matchingPages.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = book?.name ?: "Учебник",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Тема вопросов
            Text(
                text = "Тема вопросов",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text(text = "Например: § 4. Реформация", fontSize = 14.sp) }
            )
            if (topic.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Если оставить пустым — тема подставится автоматически",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Что включить
            Text(
                text = "Что включить",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    label = "По параграфам",
                    selected = mode == "paragraphs",
                    onClick = { mode = "paragraphs" },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = "По страницам",
                    selected = mode == "pages",
                    onClick = {
                        if (isPdf) {
                            mode = "pages"
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Выбор по страницам доступен только для PDF",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (paragraphs == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Загружаем параграфы…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mode == "paragraphs" && paragraphs != null) {
                // Быстрый диапазон
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickFrom,
                        onValueChange = { quickFrom = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "От §") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = quickTo,
                        onValueChange = { quickTo = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "До §") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            val from = quickFromNum ?: return@Button
                            val to = quickToNum ?: return@Button
                            if (from > to) return@Button
                            selectedNumbers =
                                rows.filter { it.number in from..to }.map { it.number }.toSet()
                            contentExpanded = true
                        },
                        enabled = quickFromNum != null && quickToNum != null,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Выделить", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Содержание с галочками
                OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { contentExpanded = !contentExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Содержание (" + rows.size + ")",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (contentExpanded) "скрыть ▲" else "показать ▼",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (contentExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                rows.forEach { row ->
                                    val checked = row.number in selectedNumbers
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedNumbers =
                                                    if (checked) {
                                                        selectedNumbers - row.number
                                                    } else {
                                                        selectedNumbers + row.number
                                                    }
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = {
                                            selectedNumbers =
                                                if (it) {
                                                    selectedNumbers + row.number
                                                } else {
                                                    selectedNumbers - row.number
                                                }
                                        })
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (row.title.isBlank()) {
                                                    "§ ${row.number} без названия"
                                                } else {
                                                    "§ ${row.number}. ${row.title}"
                                                },
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isPdf) {
                                                Text(
                                                    text = if (row.startPage == row.endPage) {
                                                        "стр. ${row.startPage}"
                                                    } else {
                                                        "стр. ${row.startPage}–${row.endPage}"
                                                    },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (selectedNumbers.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Отметьте параграфы галочками (или выделите диапазоном выше)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mode == "pages" && paragraphs != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pageFromText,
                        onValueChange = { pageFromText = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "От стр.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = pageToText,
                        onValueChange = { pageToText = it.filter { ch -> ch.isDigit() }.take(4) },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "До стр.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (pagePreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pagePreview,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Параметры генерации
            Text(
                text = "Параметры генерации",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (count > 1) countText = (count - 1).toString() },
                    enabled = count > 1,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "−", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = countText.ifBlank { "0" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = { if (count < 50) countText = (count + 1).toString() },
                    enabled = count < 50,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "+", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "вопросов (1–50)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "easy" to "Лёгкий",
                    "medium" to "Средний",
                    "hard" to "Сложный"
                ).forEach { (value, label) ->
                    ChoiceChip(
                        label = label,
                        selected = value in difficulties,
                        onClick = {
                            difficulties =
                                if (value in difficulties) {
                                    ArrayList(difficulties - value)
                                } else {
                                    ArrayList(difficulties + value)
                                }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (generating) return@Button
                    val s = settings
                    if (s == null || s.apiKey.isBlank()) {
                        android.widget.Toast.makeText(
                            context,
                            "Сначала добавьте ключ OpenRouter в Настройках",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    if (difficulties.isEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "Выберите хотя бы одну сложность",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    val countFinal = count
                    val difficultiesFinal = difficulties
                    val topicFinal = finalTopic
                    generating = true
                    statusText = null
                    scope.launch {
                        val generationText = TextbookRepository.buildGenerationText(
                            rows,
                            selectedNumbers = if (mode == "paragraphs") selectedNumbers else null,
                            pageFrom = if (mode == "pages") pageFrom else null,
                            pageTo = if (mode == "pages") pageTo else null
                        )
                        val capped =
                            if (generationText.length > 20000) {
                                generationText.take(20000) + "\n…(текст обрезан)"
                            } else {
                                generationText
                            }
                        val outcome = AiQuestionGenerator.generateFromPdf(
                            apiKey = s.apiKey,
                            settings = s,
                            topic = topicFinal,
                            count = countFinal,
                            difficulties = difficultiesFinal,
                            textbookText = capped
                        )
                        generating = false
                        if (outcome.questions.isEmpty()) {
                            statusText = outcome.report.ifBlank {
                                "Не удалось сгенерировать вопросы"
                            }
                        } else {
                            val candidates = outcome.questions.mapIndexed { index, q ->
                                Question(
                                    text = q.text.trim(),
                                    optionA = q.options.getOrElse(0) { "" }.trim(),
                                    optionB = q.options.getOrElse(1) { "" }.trim(),
                                    optionC = q.options.getOrElse(2) { "" }.trim(),
                                    optionD = q.options.getOrElse(3) { "" }.trim(),
                                    correctIndex = q.correct,
                                    difficulty = q.difficulty,
                                    tricky = false,
                                    createdAt = System.currentTimeMillis() - index,
                                    source = "pdf",
                                    topic = topicFinal
                                )
                            }
                            val (fresh, dups) = db.questionDao().insertUnique(candidates)
                            outcome.modelUsed?.let { app.settingsRepository.saveLastWorking(it) }
                            val message = when {
                                fresh == 0 -> "Все сгенерированные вопросы уже есть в банке"
                                dups > 0 -> "Добавлено $fresh вопросов (отброшено $dups дублей)"
                                else -> "Добавлено $fresh вопросов"
                            }
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = canGenerate && paragraphs != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (generating) "Генерируем…" else "✨ Сгенерировать вопросы",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (generating) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Обращаемся к ИИ — это может занять минуту",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            statusText?.let { result ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = result,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
