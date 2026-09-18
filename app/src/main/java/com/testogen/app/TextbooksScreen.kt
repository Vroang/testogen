package com.testogen.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                                        dateFormat.format(java.util.Date(book.uploadedAt)),
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

// Шаг 27: выбор диапазона параграфов и генерация вопросов.
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
    var fromText by rememberSaveable { mutableStateOf("") }
    var toText by rememberSaveable { mutableStateOf("") }
    var countText by rememberSaveable { mutableStateOf("10") }
    var difficulties by rememberSaveable {
        mutableStateOf(arrayListOf("easy", "medium", "hard"))
    }
    var generating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(textbookId) {
        book = db.textbookDao().getById(textbookId)
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Диапазон параграфов",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it.filter { ch -> ch.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "От §") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it.filter { ch -> ch.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text(text = "До §") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Количество вопросов",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = countText,
                onValueChange = { countText = it.filter { ch -> ch.isDigit() }.take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Сколько вопросов сгенерировать") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Сложность",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
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
                    val from = fromText.toIntOrNull()
                    val to = toText.toIntOrNull()
                    val count = (countText.toIntOrNull() ?: 0)
                    when {
                        from == null || to == null || from < 1 || to < from ->
                            android.widget.Toast.makeText(
                                context,
                                "Укажите диапазон: «От §» меньше или равно «До §»",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        count < 1 || count > 50 ->
                            android.widget.Toast.makeText(
                                context,
                                "Количество вопросов — от 1 до 50",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        difficulties.isEmpty() ->
                            android.widget.Toast.makeText(
                                context,
                                "Выберите хотя бы одну сложность",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        s == null || s.apiKey.isBlank() ->
                            android.widget.Toast.makeText(
                                context,
                                "Сначала добавьте ключ OpenRouter в Настройках",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        else -> {
                            generating = true
                            statusText = null
                            val fromFinal = from
                            val toFinal = to
                            val countFinal = count
                            val difficultiesFinal = difficulties
                            val topic = "${book?.name ?: "Учебник"} § $fromFinal-$toFinal"
                            scope.launch {
                                val textResult = TextbookRepository.fetchParagraphText(
                                    textbookId, fromFinal, toFinal
                                )
                                textResult.fold(
                                    onFailure = { e ->
                                        generating = false
                                        android.widget.Toast.makeText(
                                            context,
                                            e.message ?: "Не удалось получить параграфы",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onSuccess = { fullText ->
                                        val capped =
                                            if (fullText.length > 20000) {
                                                fullText.take(20000) + "\n…(текст обрезан)"
                                            } else {
                                                fullText
                                            }
                                        val outcome = AiQuestionGenerator.generateFromPdf(
                                            apiKey = s.apiKey,
                                            settings = s,
                                            topic = topic,
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
                                                    topic = topic
                                                )
                                            }
                                            val (fresh, dups) = db.questionDao().insertUnique(candidates)
                                            outcome.modelUsed?.let {
                                                app.settingsRepository.saveLastWorking(it)
                                            }
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
                                )
                            }
                        }
                    }
                },
                enabled = !generating,
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
                        text = "Загружаем параграфы и обращаемся к ИИ — это может занять минуту",
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
