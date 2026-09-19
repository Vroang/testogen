package com.testogen.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Шаг 24: иконка «книга» (Material MenuBook) — рисуется кодом,
// чтобы не подключать библиотеку material-icons-extended.
private val MenuBookIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuBookFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            strokeAlpha = 1f
        ) {
            moveTo(21f, 5f)
            curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
            curveToRelative(-1.95f, 0f, -4.05f, 0.4f, -5.5f, 1.5f)
            curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
            curveTo(4.55f, 4.5f, 2.45f, 4.9f, 1f, 6f)
            lineTo(1f, 20.65f)
            curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
            curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
            curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
            curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
            curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
            curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
            curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
            curveToRelative(0.25f, 0f, 0.5f, -0.25f, 0.5f, -0.5f)
            lineTo(23f, 6f)
            curveToRelative(-0.6f, -0.45f, -1.25f, -0.75f, -2f, -1f)
            close()
            moveTo(21f, 18.5f)
            curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
            curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
            lineTo(12f, 8f)
            curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
            curveToRelative(1.2f, 0f, 2.4f, 0.15f, 3.5f, 0.5f)
            lineTo(21f, 18.5f)
            close()
        }
    }.build()
}

object AppState {
    /** Тема с главного экрана (для автоподстановки в банк/формы). */
    var mainTopic: String = ""
}

class MainActivity : ComponentActivity() {
    companion object {
        // Проверка обновлений — один раз за запуск приложения
        var updateCheckStarted = false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestoGenTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navScope = rememberCoroutineScope()
    // Шаг 28.1: стартовый маршрут решается ПОСЛЕ асинхронной
    // загрузки сохранённой сессии Supabase.
    var startDestination by remember { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        val signedIn = AuthManager.requireSignedIn()
        startDestination = if (signedIn) "main" else "login"
        if (signedIn) {
            // Шаг 30/31: догоняем облако в фоне (учебники + вопросы + причины).
            navScope.launch {
                TextbookRepository.syncAllPending(appContext)
                QuestionSyncRepository.syncAll(appContext)
                // Шаг 31.1: автоподтягивание изменений из веб-версии
                // (не чаще раза в 5 минут).
                QuestionSyncRepository.pullAllIfStale(appContext)
            }
        }
    }
    val destination = startDestination
    if (destination == null) {
        // Пока идёт проверка — маленький индикатор вместо пустого экрана.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    NavHost(
        navController = navController,
        startDestination = destination,
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }
        },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = {
            fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 }
        }
    ) {
        composable("main") {
            MainScreen(
                onOpenBank = { navController.navigate("question_bank") },
                onOpenDraft = { navController.navigate("draft") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable(
            "new_question?questionId={questionId}&defaultTopic={defaultTopic}",
            arguments = listOf(
                navArgument("questionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("defaultTopic") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            NewQuestionScreen(
                questionId = entry.arguments?.getLong("questionId") ?: -1L,
                defaultTopic = entry.arguments?.getString("defaultTopic").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable("question_bank") {
            QuestionBankScreen(
                onBack = { navController.popBackStack() },
                onAddQuestion = { topic ->
                    navController.navigate("new_question?defaultTopic=" + Uri.encode(topic))
                },
                onEditQuestion = { id -> navController.navigate("new_question?questionId=$id") },
                onOpenTextbookRange = { id -> navController.navigate("textbook_range/$id") }
            )
        }
        composable("draft") {
            DraftScreen(
                onBack = { navController.popBackStack() },
                onEditQuestion = { id ->
                    navController.navigate("new_question?questionId=$id")
                }
            )
        }
        composable("login") {
            LoginScreen(
                onSignedIn = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAiInstructions = { navController.navigate("ai_instructions_editor") },
                onOpenAiLog = { navController.navigate("ai_log") },
                onSignOut = {
                    navScope.launch {
                        AuthManager.signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("ai_instructions_editor") {
            AiInstructionsScreen(onBack = { navController.popBackStack() })
        }
        composable("ai_log") {
            AiLogScreen(onBack = { navController.popBackStack() })
        }
        composable("textbooks") {
            TextbooksScreen(
                onBack = { navController.popBackStack() },
                onOpenRange = { id -> navController.navigate("textbook_range/$id") }
            )
        }
        composable(
            "textbook_range/{textbookId}",
            arguments = listOf(
                navArgument("textbookId") { type = NavType.StringType }
            )
        ) { entry ->
            TextbookRangeScreen(
                textbookId = entry.arguments?.getString("textbookId").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2ECE6),
    onPrimaryContainer = Color(0xFF06302A),
    secondary = Color(0xFF3F645C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EAE4),
    onSecondaryContainer = Color(0xFF0B241F),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF18211F),
    surface = Color.White,
    onSurface = Color(0xFF18211F),
    surfaceVariant = Color(0xFFE7ECEA),
    onSurfaceVariant = Color(0xFF5B6663),
    outline = Color(0xFFDFE5E3)
)

private val CardBorder = Color(0xFFE3E8E6)
private val InfoCardBackground = Color(0xFFE3F2ED)
private val InfoCardText = Color(0xFF0A4A3F)

@Composable
fun TestoGenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenBank: () -> Unit,
    onOpenDraft: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val db = (context.applicationContext as TestoGenApp).database
    val settingsRepo = (context.applicationContext as TestoGenApp).settingsRepository
    val settings by settingsRepo.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf("") }
    var variants by rememberSaveable { mutableStateOf(1) }
    var questions by rememberSaveable { mutableStateOf(10) }
    var selectedDifficulties by rememberSaveable {
        mutableStateOf(arrayListOf("easy", "medium", "hard"))
    }
    var tricky by rememberSaveable { mutableStateOf(false) }
    var showAnswers by rememberSaveable { mutableStateOf(true) }
    var pendingFiltered by remember { mutableStateOf<List<Question>?>(null) }
    var pendingTopicFilter by remember { mutableStateOf("") }
    var confirmDraft by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var emptyTopicDraft by remember { mutableStateOf<Pair<String, Int>?>(null) }

    LaunchedEffect(topic) { AppState.mainTopic = topic }

    var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }

    // Проверка обновлений — один раз за запуск приложения
    LaunchedEffect(Unit) {
        if (MainActivity.updateCheckStarted) return@LaunchedEffect
        MainActivity.updateCheckStarted = true
        val release = withContext(Dispatchers.IO) {
            UpdaterClient.checkLatestRelease()
        }
        if (release != null && release.versionCode > BuildConfig.VERSION_CODE) {
            pendingUpdate = release
        }
    }
    var topUpRunning by remember { mutableStateOf(false) }
    var topUpDone by remember { mutableIntStateOf(0) }
    var topUpTarget by remember { mutableIntStateOf(0) }
    var topUpStopRequested by remember { mutableStateOf(false) }
    var partialTopUp by remember { mutableStateOf<Int?>(null) }
    var extraTopicsRaw by rememberSaveable { mutableStateOf("") }
    var showExtraSheet by remember { mutableStateOf(false) }
    val sheetSelected = remember { mutableStateListOf<String>() }
    val extraTopicsList = remember(extraTopicsRaw) {
        extraTopicsRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    var topicFieldFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val suggestionsOffsetY = with(density) { 62.dp.roundToPx() }
    val bankAll by db.questionDao().getAll().collectAsState(initial = emptyList())
    val bankTopics = remember(bankAll) {
        bankAll.map { it.topic.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val buildPool: (List<Question>) -> List<Question> = { all ->
        val t = topic.trim()
        val base = DraftBuilder.filterQuestions(all, selectedDifficulties, tricky, t)
        if (t.isNotBlank() && extraTopicsList.isNotEmpty()) {
            val mainIds = base.map { it.id }.toSet()
            base + all.filter { q ->
                !mainIds.contains(q.id) &&
                    extraTopicsList.any { q.topic.equals(it, ignoreCase = true) } &&
                    q.difficulty in selectedDifficulties &&
                    (tricky || !q.tricky)
            }
        } else {
            base
        }
    }

    val buildAndOpenDraft: () -> Unit = {
        scope.launch {
            val all = db.questionDao().getAllOnce()
            val filteredNow = buildPool(all)
            DraftHolder.variants = DraftBuilder.build(filteredNow, variants, questions)
            DraftHolder.questionsPerVariant = questions
            onOpenDraft()
        }
    }

    val runTopUp: (Int) -> Unit = { toAdd ->
        val s = settings
        if (s == null || s.apiKey.isBlank()) {
            confirmDraft = null
            emptyTopicDraft = null
            Toast.makeText(context, "Сначала добавьте ключ OpenRouter в Настройках", Toast.LENGTH_SHORT).show()
        } else {
            if (topic.isBlank()) {
                Toast.makeText(
                    context,
                    "Укажите тему на главном экране (или добор пройдёт без темы)",
                    Toast.LENGTH_SHORT
                ).show()
            }
            topUpTarget = toAdd
            topUpDone = 0
            topUpStopRequested = false
            topUpRunning = true
            confirmDraft = null
            emptyTopicDraft = null
            scope.launch {
                var added = 0
                var generated = 0
                var dups = 0
                var failed = false
                while (added < toAdd && generated < toAdd && !topUpStopRequested && !failed) {
                    val portion = minOf(10, toAdd - generated)
                    val outcome = AiQuestionGenerator.generate(
                        apiKey = s.apiKey,
                        settings = s,
                        topic = topic.ifBlank { "общая тематика теста" },
                        count = portion,
                        difficulties = selectedDifficulties,
                        operation = "topup"
                    )
                    if (outcome.questions.isNotEmpty()) {
                        val now = System.currentTimeMillis()
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
                                createdAt = now - index,
                                source = "ai",
                                topic = topic.trim()
                            )
                        }
                        val (fresh, dupCount) = db.questionDao().insertUnique(candidates)
                        outcome.modelUsed?.let { settingsRepo.saveLastWorking(it) }
                        added += fresh
                        dups += dupCount
                        topUpDone = added
                        generated += outcome.questions.size
                    } else {
                        failed = true
                    }
                }
                topUpRunning = false
                if (dups > 0) {
                    Toast.makeText(
                        context,
                        "Добавлено $added вопросов (отброшено $dups дублей)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (added < toAdd) {
                    if (topUpStopRequested) {
                        Toast.makeText(
                            context,
                            "Остановлено, добрано $added вопросов",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    partialTopUp = added
                } else {
                    buildAndOpenDraft()
                }
            }
        }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "screenAppear"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        this.translationY = (1f - alpha) * 40f
                    }
            ) {
                Header(onOpenSettings = onOpenSettings)

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TuneGlyph()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Настройте структуру теста",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Все параметры можно менять в любой момент",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "Тематика теста",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box {
                            OutlinedTextField(
                                value = topic,
                                onValueChange = { topic = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { topicFieldFocused = it.isFocused },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                placeholder = {
                                    Text(
                                        text = "Например: обществознание 9 класс",
                                        fontSize = 15.sp
                                    )
                                }
                            )
                            if (topicFieldFocused && topic.isNotBlank()) {
                                val suggestions = bankTopics
                                    .filter { it.startsWith(topic.trim(), ignoreCase = true) }
                                    .take(5)
                                if (suggestions.isNotEmpty()) {
                                    Popup(
                                        alignment = Alignment.TopStart,
                                        offset = IntOffset(0, suggestionsOffsetY),
                                        onDismissRequest = { },
                                        properties = PopupProperties(focusable = false)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                            shadowElevation = 8.dp,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                suggestions.forEach { suggestion ->
                                                    Text(
                                                        text = suggestion,
                                                        fontSize = 14.sp,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                topic = suggestion
                                                                focusManager.clearFocus(force = true)
                                                            }
                                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Используется для поиска вопросов в интернете и в заголовке теста",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (topic.isBlank()) 0.55f else 1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Дополнительные темы",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Можно взять часть вопросов из этих тем, если основной темы не хватит",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (extraTopicsList.isEmpty()) {
                                            Text(
                                                text = "Не выбраны",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(extraTopicsList) { t ->
                                                    TagChip(
                                                        text = t,
                                                        background = Color(0xFFEEF1F0),
                                                        content = Color(0xFF5B6663)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    TextButton(
                                        onClick = { showExtraSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = if (extraTopicsList.isEmpty()) "Добавить" else "Изменить",
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        StepperSlider(
                            title = "Количество вариантов теста",
                            caption = "Каждый вариант — отдельная страница в файле со своим набором вопросов",
                            value = variants,
                            range = 1..40,
                            onValueChange = { variants = it }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        StepperSlider(
                            title = "Вопросов в каждом варианте",
                            caption = null,
                            value = questions,
                            range = 5..40,
                            onValueChange = { questions = it }
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "Сложность вопросов",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "easy" to "Лёгкие",
                                "medium" to "Средние",
                                "hard" to "Сложные"
                            ).forEach { (value, label) ->
                                ChoiceChip(
                                    label = label,
                                    selected = value in selectedDifficulties,
                                    onClick = {
                                        selectedDifficulties =
                                            if (value in selectedDifficulties) {
                                                ArrayList(selectedDifficulties - value)
                                            } else {
                                                ArrayList(selectedDifficulties + value)
                                            }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        CheckboxCard(
                            title = "Включать вопросы с подвохом",
                            subtitle = "Каверзные вопросы на внимательность",
                            checked = tricky,
                            onCheckedChange = { tricky = it }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        CheckboxCard(
                            title = "Показывать правильные ответы в конце отдельным блоком",
                            subtitle = "В конце теста для всех вариантов будет отдельная страница с ответами",
                            checked = showAnswers,
                            onCheckedChange = { showAnswers = it }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        InfoCard(
                            variants = variants,
                            questions = questions
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (topUpRunning) return@launch
                                    if (selectedDifficulties.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Выберите хотя бы одну сложность",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val all = db.questionDao().getAllOnce()
                                    val filtered = buildPool(all)
                                    val needed = variants * questions
                                    when {
                                        filtered.isEmpty() -> {
                                            if (topic.isNotBlank()) {
                                                pendingTopicFilter = topic.trim()
                                                emptyTopicDraft = topic.trim() to needed
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "В банке нет подходящих вопросов. Загляните в Банк вопросов.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }

                                        filtered.size < needed -> {
                                            pendingFiltered = filtered
                                            confirmDraft = filtered.size to needed
                                        }

                                        else -> {
                                            DraftHolder.variants = DraftBuilder.build(
                                                filtered,
                                                variants,
                                                questions
                                            )
                                            DraftHolder.questionsPerVariant = questions
                                            onOpenDraft()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "✨ Собрать тест",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(96.dp))
            }

            ExtendedFloatingActionButton(
                onClick = onOpenBank,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = MenuBookIcon,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Банк",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    confirmDraft?.let { (have, need) ->
        val target = (need * 0.8).roundToInt()
        val canTopUp = have.toDouble() < need * 0.8
        AlertDialog(
            onDismissRequest = { confirmDraft = null },
            text = {
                Column {
                    Text(
                        text = if (pendingTopicFilter.isNotBlank()) {
                            "По теме «$pendingTopicFilter» в банке $have вопросов, " +
                                "для теста нужно $need. Некоторые вопросы повторятся."
                        } else {
                            "В банке $have вопросов, для теста нужно $need. " +
                                "Некоторые вопросы повторятся."
                        },
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (canTopUp) {
                        Button(
                            onClick = {
                                confirmDraft = null
                                runTopUp((target - have).coerceAtLeast(1))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Добрать и собрать",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Добрать до $target вопросов (не более 20% повторов в тесте)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { confirmDraft = null }) {
                            Text(
                                text = "Отмена",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            confirmDraft = null
                            val filtered = pendingFiltered.orEmpty()
                            DraftHolder.variants = DraftBuilder.build(filtered, variants, questions)
                            DraftHolder.questionsPerVariant = questions
                            onOpenDraft()
                        }) {
                            Text(
                                text = "Собрать с повторами",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    emptyTopicDraft?.let { (topicName, need) ->
        AlertDialog(
            onDismissRequest = { emptyTopicDraft = null },
            text = {
                Text(
                    text = "В банке нет вопросов по теме «$topicName». " +
                        "Сгенерировать $need вопросов, чтобы собрать тест?",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        emptyTopicDraft = null
                        runTopUp(need)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "✨ Сгенерировать",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { emptyTopicDraft = null }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (topUpRunning) {
        AlertDialog(
            onDismissRequest = { },
            text = {
                Column {
                    Text(
                        text = "Добираем вопросы…",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val progress = if (topUpTarget > 0) topUpDone.toFloat() / topUpTarget else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Добрано $topUpDone из $topUpTarget",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { topUpStopRequested = true }) {
                    Text(
                        text = "Остановить",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    partialTopUp?.let { added ->
        AlertDialog(
            onDismissRequest = { partialTopUp = null },
            text = {
                Text(
                    text = "Удалось добрать только $added вопросов. Продолжить сборку?",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    partialTopUp = null
                    buildAndOpenDraft()
                }) {
                    Text(
                        text = "Собрать",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { partialTopUp = null }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    pendingUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = {
                Text(
                    text = "Доступно обновление",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Установить новую версию ${release.versionName}?",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUpdate = null
                    UpdaterClient.downloadAndInstall(context, release)
                }) {
                    Text(
                        text = "Обновить",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdate = null }) {
                    Text(text = "Позже", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showExtraSheet) {
        val availableTopics = bankTopics.filter { it != topic.trim() }
        ModalBottomSheet(
            onDismissRequest = { showExtraSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Дополнительные темы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Отметьте темы, из которых можно добрать вопросы",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (availableTopics.isEmpty()) {
                    Text(
                        text = "В банке пока нет других тем",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        availableTopics.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (sheetSelected.contains(t)) {
                                            sheetSelected.remove(t)
                                        } else {
                                            sheetSelected.add(t)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = sheetSelected.contains(t),
                                    onCheckedChange = null
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = t, fontSize = 15.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        extraTopicsRaw = sheetSelected.toSortedSet().joinToString("\n")
                        showExtraSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Готово",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Конструктор тестов",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "для учителей",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Настройки",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun TuneGlyph() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.5.dp.toPx()
        val y1 = size.height * 0.32f
        val y2 = size.height * 0.68f
        drawLine(color, Offset(0f, y1), Offset(size.width, y1), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, y2), Offset(size.width, y2), stroke, StrokeCap.Round)
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width * 0.68f, y1))
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width * 0.32f, y2))
    }
}

@Composable
private fun StepperSlider(
    title: String,
    caption: String?,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                symbol = "−",
                enabled = value > range.first,
                onClick = { onValueChange(value - 1) }
            )
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = value.toString(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp)
            )
            Spacer(modifier = Modifier.width(18.dp))
            StepButton(
                symbol = "+",
                enabled = value < range.last,
                onClick = { onValueChange(value + 1) }
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        if (caption != null) {
            Text(
                text = caption,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Text(text = symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipContainer by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(120),
        label = "chipContainer"
    )
    val chipContentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(120),
        label = "chipContent"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = chipContainer,
        contentColor = chipContentColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CheckboxCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoCard(variants: Int, questions: Int) {
    val total = variants * questions
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = InfoCardBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("Будет собрано: ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("$variants") }
                    append(" вариантов × ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("$questions") }
                    append(" вопросов = ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("$total") }
                    append(" вопросов в банке.")
                },
                fontSize = 15.sp,
                color = InfoCardText
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Чего-то не хватает? Зайдите в Шаг 1: наполните банк вручную или из интернета",
                fontSize = 13.sp,
                color = InfoCardText.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun NewQuestionScreen(questionId: Long, defaultTopic: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = (context.applicationContext as TestoGenApp).database
    val scope = rememberCoroutineScope()

    val bankCount by db.questionDao().getCountFlow().collectAsState(initial = 0)

    val isEditing = questionId > 0
    var loadedQuestion by remember { mutableStateOf<Question?>(null) }

    LaunchedEffect(questionId) {
        if (isEditing) {
            loadedQuestion = db.questionDao().getById(questionId)
        }
    }

    var questionTopic by remember(loadedQuestion) {
        mutableStateOf(loadedQuestion?.topic ?: if (questionId <= 0) defaultTopic else "")
    }

    if (isEditing && loadedQuestion == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    var questionText by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.text ?: "") }
    var optionA by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.optionA ?: "") }
    var optionB by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.optionB ?: "") }
    var optionC by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.optionC ?: "") }
    var optionD by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.optionD ?: "") }
    var correctIndex by remember(loadedQuestion) { mutableIntStateOf(loadedQuestion?.correctIndex ?: 0) }
    var difficulty by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.difficulty ?: "medium") }
    var tricky by remember(loadedQuestion) { mutableStateOf(loadedQuestion?.tricky ?: false) }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "formAppear"
    )

    val optionLabels = listOf("А", "Б", "В", "Г")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    this.translationY = (1f - alpha) * 40f
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isEditing) "Редактировать вопрос" else "Новый вопрос",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Вопросов в банке: $bankCount",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            FormCard {
                FieldLabel("Тема")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = questionTopic,
                    onValueChange = { questionTopic = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            text = "Например: Столетняя война, 6 класс",
                            fontSize = 14.sp
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                FieldLabel("Текст вопроса")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(text = "Введите текст вопроса", fontSize = 15.sp)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FormCard {
                FieldLabel("Варианты ответов")
                Spacer(modifier = Modifier.height(12.dp))
                OptionField(label = "А", value = optionA, onValueChange = { optionA = it })
                Spacer(modifier = Modifier.height(10.dp))
                OptionField(label = "Б", value = optionB, onValueChange = { optionB = it })
                Spacer(modifier = Modifier.height(10.dp))
                OptionField(label = "В", value = optionC, onValueChange = { optionC = it })
                Spacer(modifier = Modifier.height(10.dp))
                OptionField(label = "Г", value = optionD, onValueChange = { optionD = it })
            }

            Spacer(modifier = Modifier.height(16.dp))

            FormCard {
                FieldLabel("Правильный ответ")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    optionLabels.forEachIndexed { index, label ->
                        ChoiceChip(
                            label = label,
                            selected = correctIndex == index,
                            onClick = { correctIndex = index },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Отметьте, какой из вариантов выше правильный",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            FormCard {
                FieldLabel("Сложность вопроса")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("easy" to "Лёгкий", "medium" to "Средний", "hard" to "Сложный")
                        .forEach { (value, label) ->
                            ChoiceChip(
                                label = label,
                                selected = difficulty == value,
                                onClick = { difficulty = value },
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CheckboxCard(
                title = "С подвохом",
                subtitle = "Каверзный вопрос на внимательность",
                checked = tricky,
                onCheckedChange = { tricky = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    when {
                        questionText.isBlank() ->
                            Toast.makeText(context, "Введите текст вопроса", Toast.LENGTH_SHORT).show()
                        optionA.isBlank() ->
                            Toast.makeText(context, "Заполните вариант А", Toast.LENGTH_SHORT).show()
                        optionB.isBlank() ->
                            Toast.makeText(context, "Заполните вариант Б", Toast.LENGTH_SHORT).show()
                        optionC.isBlank() ->
                            Toast.makeText(context, "Заполните вариант В", Toast.LENGTH_SHORT).show()
                        optionD.isBlank() ->
                            Toast.makeText(context, "Заполните вариант Г", Toast.LENGTH_SHORT).show()
                        else -> {
                            if (isEditing) {
                                val base = loadedQuestion
                                if (base != null) {
                                    // Обновляем существующий вопрос, сохраняя id и источник
                                    scope.launch {
                                    db.questionDao().update(
                                        base.copy(
                                            text = questionText.trim(),
                                            optionA = optionA.trim(),
                                            optionB = optionB.trim(),
                                            optionC = optionC.trim(),
                                            optionD = optionD.trim(),
                                            correctIndex = correctIndex,
                                            difficulty = difficulty,
                                            tricky = tricky,
                                            topic = questionTopic.trim(),
                                            syncStatus = "pending_upload"
                                        )
                                    )
                                    QuestionSyncRepository.syncPendingQuestions(context)
                                    }
                                    Toast.makeText(context, "Вопрос обновлён", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            } else {
                                // Вопрос собираем из значений состояния ДО запуска
                                // корутины и ДО очистки полей: корутина выполнится
                                // позже, и если читать состояние внутри неё, уйдут
                                // пустые строки и дефолты.
                                val question = Question(
                                    text = questionText.trim(),
                                    optionA = optionA.trim(),
                                    optionB = optionB.trim(),
                                    optionC = optionC.trim(),
                                    optionD = optionD.trim(),
                                    correctIndex = correctIndex,
                                    difficulty = difficulty,
                                    tricky = tricky,
                                    createdAt = System.currentTimeMillis(),
                                    source = "manual",
                                    topic = questionTopic.trim()
                                )
                                scope.launch {
                                    db.questionDao().insert(question)
                                }
                                Toast.makeText(context, "Вопрос сохранён", Toast.LENGTH_SHORT).show()
                                questionText = ""
                                optionA = ""
                                optionB = ""
                                optionC = ""
                                optionD = ""
                                correctIndex = 0
                                difficulty = "medium"
                                tricky = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (isEditing) "Сохранить изменения" else "Сохранить",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun OptionField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text(text = "Вариант $label", fontSize = 15.sp)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionBankScreen(
    onBack: () -> Unit,
    onAddQuestion: (String) -> Unit,
    onEditQuestion: (Long) -> Unit,
    onOpenTextbookRange: (String) -> Unit
) {
    val context = LocalContext.current
    val db = (context.applicationContext as TestoGenApp).database
    val scope = rememberCoroutineScope()

    val questions by db.questionDao().getAll().collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }

    val settingsRepo = (context.applicationContext as TestoGenApp).settingsRepository
    val settings by settingsRepo.settings.collectAsState(initial = null)

    var activeTab by remember { mutableStateOf("manual") }
    var genTopic by remember { mutableStateOf("") }
    var genCount by remember { mutableIntStateOf(5) }
    var genDifficulties by remember { mutableStateOf(arrayListOf("easy", "medium", "hard")) }
    var generating by remember { mutableStateOf(false) }
    var genResult by remember { mutableStateOf<String?>(null) }
    var genIsError by remember { mutableStateOf(false) }
    var internetTopicTouched by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab) {
        if (activeTab == "internet" && !internetTopicTouched && genTopic.isBlank()) {
            genTopic = AppState.mainTopic
        }
        if (activeTab == "file") {
            // Шаг 30/31: попытка досинхронизировать всё ожидающее.
            scope.launch {
                TextbookRepository.syncAllPending(context.applicationContext)
                QuestionSyncRepository.syncAll(context.applicationContext)
            }
        }
    }

    var topicFilter by remember { mutableStateOf<String?>(null) }

    // Шаг 28: «Файл» = «Мои учебники» (Supabase + локальный кэш Room).
    val textbooks by db.textbookDao().getAll().collectAsState(initial = emptyList())
    var textbookUploading by remember { mutableStateOf(false) }
    var textbookOversizeMb by remember { mutableStateOf<Int?>(null) }
    var textbookPendingDelete by remember { mutableStateOf<Textbook?>(null) }
    val textbookDateFormat = remember { SimpleDateFormat("d MMM yy", Locale("ru")) }

    val textbookPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !textbookUploading) {
            textbookUploading = true
            scope.launch {
                val result = TextbookRepository.uploadTextbook(context, uri)
                textbookUploading = false
                result.fold(
                    onSuccess = { book ->
                        Toast.makeText(
                            context,
                            "Учебник загружен: ${book.paragraphCount} параграфов",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { e ->
                        if (e is OversizeException) {
                            textbookOversizeMb = e.mb
                        } else {
                            Toast.makeText(
                                context,
                                "Не удалось загрузить: ${e.message ?: "неизвестная ошибка"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }

    val runGeneration: () -> Unit = {
        val s = settings
        if (s == null || s.apiKey.isBlank()) {
            Toast.makeText(context, "Сначала добавьте ключ OpenRouter в Настройках", Toast.LENGTH_SHORT).show()
        } else if (genTopic.isBlank()) {
            Toast.makeText(context, "Введите тему", Toast.LENGTH_SHORT).show()
        } else if (genDifficulties.isEmpty()) {
            Toast.makeText(context, "Выберите хотя бы одну сложность", Toast.LENGTH_SHORT).show()
        } else {
            generating = true
            genResult = null
            scope.launch {
                val outcome = AiQuestionGenerator.generate(
                    apiKey = s.apiKey,
                    settings = s,
                    topic = genTopic.trim(),
                    count = genCount,
                    difficulties = genDifficulties
                )
                generating = false
                if (outcome.questions.isNotEmpty()) {
                    val now = System.currentTimeMillis()
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
                            createdAt = now - index,
                            source = "ai",
                            topic = genTopic.trim()
                        )
                    }
                    val (fresh, dups) = db.questionDao().insertUnique(candidates)
                    outcome.modelUsed?.let { settingsRepo.saveLastWorking(it) }
                    activeTab = "manual"
                    Toast.makeText(
                        context,
                        when {
                            fresh == 0 -> "Все сгенерированные вопросы уже есть в банке"
                            dups > 0 -> "Добавлено $fresh вопросов (отброшено $dups дублей)"
                            else -> "Добавлено $fresh вопросов"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    genIsError = true
                    genResult = outcome.report.ifBlank { "Не удалось сгенерировать вопросы" }
                }
            }
        }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "bankAppear"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isFormTab = activeTab == "internet"
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isFormTab) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = if (isFormTab) 16.dp else 0.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        this.translationY = (1f - alpha) * 40f
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Банк вопросов",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = questions.size.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (isFormTab || activeTab == "file") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = "Ручной ввод",
                            selected = activeTab == "manual",
                            onClick = { activeTab = "manual" },
                            modifier = Modifier.weight(1f)
                        )
                        ChoiceChip(
                            label = "Файл",
                            selected = activeTab == "file",
                            onClick = { activeTab = "file" },
                            modifier = Modifier.weight(1f)
                        )
                        ChoiceChip(
                            label = "Интернет",
                            selected = activeTab == "internet",
                            onClick = { activeTab = "internet" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (activeTab == "internet") {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Генерация вопросов по теме",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Вопросы составит ИИ через OpenRouter и добавит в банк",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = "Тема",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = genTopic,
                                onValueChange = {
                                    genTopic = it
                                    internetTopicTouched = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                placeholder = {
                                    Text(text = "Например: Древний Египет", fontSize = 15.sp)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Количество вопросов",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StepButton(
                                    symbol = "−",
                                    enabled = genCount > 1,
                                    onClick = { genCount -= 1 }
                                )
                                Spacer(modifier = Modifier.width(18.dp))
                                Text(
                                    text = genCount.toString(),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(56.dp)
                                )
                                Spacer(modifier = Modifier.width(18.dp))
                                StepButton(
                                    symbol = "+",
                                    enabled = genCount < 50,
                                    onClick = { genCount += 1 }
                                )
                            }
                            if (genCount > 30) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Генерация может занять несколько минут",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                                        selected = value in genDifficulties,
                                        onClick = {
                                            genDifficulties =
                                                if (value in genDifficulties) {
                                                    ArrayList(genDifficulties - value)
                                                } else {
                                                    ArrayList(genDifficulties + value)
                                                }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = runGeneration,
                                enabled = !generating,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = if (generating) "Генерируем…" else "✨ Сгенерировать",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (generating) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Идёт запрос к ИИ — может занять до минуты",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            genResult?.let { result ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = result,
                                    fontSize = 12.sp,
                                    color = if (genIsError) Color(0xFFC62828) else Color(0xFF0E7C6B)
                                )
                            }
                        }
                    }
                } else if (activeTab == "file") {
                    Text(
                        text = "Учебники хранятся в облаке. Тап — выбрать диапазон параграфов, долгий тап — удалить.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (textbookUploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (textbooks.isEmpty() && !textbookUploading) {
                        Text(
                            text = "Пока нет учебников. Нажмите «+», чтобы загрузить первый (PDF, DOCX или TXT с параграфами §).",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(textbooks) { book ->
                                OutlinedCard(
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (!textbookUploading) {
                                                    onOpenTextbookRange(book.id)
                                                }
                                            },
                                            onLongClick = { textbookPendingDelete = book }
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
                                                textbookDateFormat.format(
                                                    java.util.Date(book.uploadedAt)
                                                ) +
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
                } else {
                    val topicValues = remember(questions) {
                        questions.map { it.topic.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .sorted()
                    }
                    val hasUntitled = remember(questions) { questions.any { it.topic.isBlank() } }
                    val visibleQuestions = remember(questions, topicFilter) {
                        when (topicFilter) {
                            "" -> questions.filter { it.topic.isBlank() }
                            null -> questions
                            else -> questions.filter { it.topic.trim() == topicFilter }
                        }
                    }
                    val groups = remember(visibleQuestions) {
                        visibleQuestions
                            .groupBy { it.topic.trim() }
                            .toList()
                            .sortedWith(compareBy({ it.first.isBlank() }, { it.first }))
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "tabs") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceChip(
                                    label = "Ручной ввод",
                                    selected = activeTab == "manual",
                                    onClick = { activeTab = "manual" },
                                    modifier = Modifier.weight(1f)
                                )
                                ChoiceChip(
                                    label = "Файл",
                                    selected = activeTab == "file",
                                    onClick = { activeTab = "file" },
                                    modifier = Modifier.weight(1f)
                                )
                                ChoiceChip(
                                    label = "Интернет",
                                    selected = activeTab == "internet",
                                    onClick = { activeTab = "internet" },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        item(key = "filters") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    ThemeFilterChip(
                                        label = "Все темы",
                                        selected = topicFilter == null,
                                        onClick = { topicFilter = null }
                                    )
                                }
                                items(topicValues) { topic ->
                                    ThemeFilterChip(
                                        label = topic,
                                        selected = topicFilter == topic,
                                        onClick = { topicFilter = topic }
                                    )
                                }
                                if (hasUntitled) {
                                    item {
                                        ThemeFilterChip(
                                            label = "Без темы",
                                            selected = topicFilter == "",
                                            onClick = { topicFilter = "" }
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "clear") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showClearDialog = true },
                                    enabled = questions.isNotEmpty(),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Очистить",
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        if (visibleQuestions.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Пока пусто. Добавьте первый вопрос кнопкой +",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else if (topicFilter == null) {
                            groups.forEach { (topic, list) ->
                                item(key = "header_$topic") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (topic.isBlank()) "Без темы" else topic,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${list.size})",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                items(list, key = { "q_${it.id}" }) { question ->
                                    QuestionCard(
                                        question = question,
                                        onDelete = {
                                            scope.launch {
                                                QuestionSyncRepository.onQuestionDeleted(context, question)
                                            }
                                        },
                                        onEdit = { onEditQuestion(question.id) }
                                    )
                                }
                            }
                        } else {
                            items(visibleQuestions, key = { "q_${it.id}" }) { question ->
                                QuestionCard(
                                    question = question,
                                    onDelete = {
                                        scope.launch {
                                            QuestionSyncRepository.onQuestionDeleted(context, question)
                                        }
                                    },
                                    onEdit = { onEditQuestion(question.id) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(96.dp))
            }

            if (activeTab == "file") {
                FloatingActionButton(
                    onClick = {
                        if (!textbookUploading) {
                            textbookPicker.launch(
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
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    if (textbookUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Загрузить учебник"
                        )
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { onAddQuestion(AppState.mainTopic) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(text = "+", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    textbookOversizeMb?.let { mb ->
        AlertDialog(
            onDismissRequest = { textbookOversizeMb = null },
            text = {
                Text(
                    text = "Файл слишком большой ($mb МБ). Максимум для загрузки — 50 МБ. " +
                        "Попробуйте сжать PDF или использовать другую версию файла.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { textbookOversizeMb = null }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    textbookPendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { textbookPendingDelete = null },
            text = {
                Text(
                    text = "Удалить учебник «${book.name}»? Он будет удалён и из облака, и из списка.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = book
                    textbookPendingDelete = null
                    scope.launch {
                        val result = TextbookRepository.deleteTextbook(context, toDelete)
                        result.fold(
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    "Учебник удалён",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(
                                    context,
                                    "Не удалось удалить: ${e.message ?: "ошибка"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }) {
                    Text(text = "Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { textbookPendingDelete = null }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = "Удалить ВСЕ вопросы?") },
            text = { Text(text = "Это нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch { QuestionSyncRepository.onAllQuestionsDeleted(context) }
                    }
                ) {
                    Text(text = "Удалить", color = Color(0xFFC62828))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = "Отмена")
                }
            }
        )
    }
}

@Composable
private fun QuestionCard(question: Question, onDelete: () -> Unit, onEdit: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = question.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (tagBg, tagFg) = when (question.difficulty) {
                            "easy" -> Color(0xFFDFF2E9) to Color(0xFF0B7A5E)
                            "hard" -> Color(0xFFFBE3E0) to Color(0xFFB3402F)
                            else -> Color(0xFFFBF1DC) to Color(0xFF91661B)
                        }
                        TagChip(
                            text = difficultyLabel(question.difficulty),
                            background = tagBg,
                            content = tagFg
                        )
                        if (question.tricky) {
                            TagChip(
                                text = "С подвохом",
                                background = Color(0xFFFCE7E7),
                                content = Color(0xFFC62828)
                            )
                        }
                        TagChip(
                            text = when (question.source) {
                                "ai" -> "Интернет/ИИ"
                                "pdf" -> "Из файла"
                                else -> "Вручную"
                            },
                            background = Color(0xFFEEF1F0),
                            content = Color(0xFF5B6663)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Изменить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            if (expanded) {
                val optionLabels = listOf("А", "Б", "В", "Г")
                val options = listOf(
                    question.optionA,
                    question.optionB,
                    question.optionC,
                    question.optionD
                )
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEachIndexed { optionIndex, optionText ->
                        val isCorrect = optionIndex == question.correctIndex
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCorrect) Color(0xFFDFF2E9) else Color(0xFFF5F7F6)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${optionLabels[optionIndex]}) $optionText",
                                    fontSize = 13.sp,
                                    color = if (isCorrect) {
                                        Color(0xFF0B6B52)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCorrect) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Правильный ответ",
                                        tint = Color(0xFF0B7A5E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val chipContainer by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = tween(120),
        label = "themeChipContainer"
    )
    val chipContent by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(120),
        label = "themeChipContent"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = chipContainer,
        contentColor = chipContent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TagChip(text: String, background: Color, content: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun difficultyLabel(value: String): String = when (value) {
    "easy" -> "Лёгкий"
    "hard" -> "Сложный"
    else -> "Средний"
}

private data class PdfExtractResult(val name: String, val pages: Int, val text: String)

private data class ReplaceTarget(
    val variantIndex: Int,
    val questionIndex: Int,
    val question: Question
)

@Composable
fun DraftScreen(onBack: () -> Unit, onEditQuestion: (Long) -> Unit) {
    val context = LocalContext.current
    val db = (context.applicationContext as TestoGenApp).database
    val scope = rememberCoroutineScope()
    val settingsRepo = (context.applicationContext as TestoGenApp).settingsRepository
    val settings by settingsRepo.settings.collectAsState(initial = null)

    val bankQuestions by db.questionDao().getAll().collectAsState(initial = emptyList())

    var variantsState by remember { mutableStateOf(DraftHolder.variants) }
    var replaceTarget by remember { mutableStateOf<ReplaceTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<ReplaceTarget?>(null) }
    val expandedVariants = remember { mutableStateListOf<Int>() }
    var squeezeDialogDismissed by remember { mutableStateOf(false) }
    var squeezeRunning by remember { mutableStateOf(false) }

    val writeFile: (Uri, ByteArray) -> Unit = { uri, bytes ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                Toast.makeText(context, "Файл сохранён", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveTestDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        if (uri != null && variantsState.isNotEmpty()) {
            writeFile(uri, DocxWriter.buildTestDocx(variantsState))
        }
    }

    val saveAnswersDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri ->
        if (uri != null && variantsState.isNotEmpty()) {
            writeFile(uri, DocxWriter.buildAnswersDocx(variantsState))
        }
    }

    val shareDocs: () -> Unit = {
        scope.launch {
            try {
                val uris = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "shared")
                    dir.mkdirs()
                    val testFile = File(dir, DocxFiles.fileName(AppState.mainTopic, "Тест"))
                    testFile.writeBytes(DocxWriter.buildTestDocx(variantsState))
                    val answersFile = File(dir, DocxFiles.fileName(AppState.mainTopic, "Ответы"))
                    answersFile.writeBytes(DocxWriter.buildAnswersDocx(variantsState))
                    arrayListOf(
                        FileProvider.getUriForFile(context, "com.testogen.app.fileprovider", testFile),
                        FileProvider.getUriForFile(context, "com.testogen.app.fileprovider", answersFile)
                    )
                }
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Поделиться тестом и ответами"))
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось подготовить файлы", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Синхронизация содержимого черновика с банком (после редактирования вопроса)
    LaunchedEffect(bankQuestions) {
        if (bankQuestions.isEmpty() || variantsState.isEmpty()) return@LaunchedEffect
        val byId = bankQuestions.associateBy { it.id }
        val refreshed = variantsState.map { v -> v.map { q -> byId[q.id] ?: q } }
        variantsState = refreshed
        DraftHolder.variants = refreshed
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "draftAppear"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha
                    this.translationY = (1f - alpha) * 40f
                },
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Черновик",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Вариантов: ${variantsState.size} · Вопросов в каждом: ${DraftHolder.questionsPerVariant}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            if (variantsState.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Черновик пуст. Соберите тест заново.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                variantsState.forEachIndexed { index, questions ->
                    item(key = "v_$index") {
                        VariantCard(
                            index = index,
                            questions = questions,
                            expanded = expandedVariants.contains(index),
                            onToggle = {
                                if (expandedVariants.contains(index)) {
                                    expandedVariants.remove(index)
                                } else {
                                    expandedVariants.add(index)
                                }
                            },
                            onEditClick = { questionIndex, question ->
                                onEditQuestion(question.id)
                            },
                            onReplaceClick = { questionIndex, question ->
                                deleteTarget = null
                                replaceTarget = ReplaceTarget(index, questionIndex, question)
                            },
                            onDeleteClick = { questionIndex, question ->
                                deleteTarget = ReplaceTarget(index, questionIndex, question)
                            }
                        )
                    }
                }
                item(key = "export_test") {
                    Button(
                        onClick = {
                            if (variantsState.isEmpty()) {
                                Toast.makeText(context, "Черновик пуст — соберите тест", Toast.LENGTH_SHORT).show()
                            } else {
                                saveTestDoc.launch(DocxFiles.fileName(AppState.mainTopic, "Тест"))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Скачать файл",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item(key = "export_answers") {
                    Button(
                        onClick = {
                            if (variantsState.isEmpty()) {
                                Toast.makeText(context, "Черновик пуст — соберите тест", Toast.LENGTH_SHORT).show()
                            } else {
                                saveAnswersDoc.launch(DocxFiles.fileName(AppState.mainTopic, "Ответы"))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Файл с ответами",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item(key = "export_share") {
                    OutlinedButton(
                        onClick = {
                            if (variantsState.isEmpty()) {
                                Toast.makeText(context, "Черновик пуст — соберите тест", Toast.LENGTH_SHORT).show()
                            } else {
                                shareDocs()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Поделиться",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                item(key = "export_caption") {
                    Text(
                        text = "Два файла .docx: тест — каждый вариант с новой страницы; " +
                            "ответы — таблица «№ | Ответ» для каждого варианта",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        replaceTarget?.let { target ->
            ReplaceSheet(
                target = target,
                variantQuestions = variantsState.getOrElse(target.variantIndex) { emptyList() },
                bankQuestions = bankQuestions,
                settings = settings ?: AppSettings(),
                apiKey = settings?.apiKey.orEmpty(),
                onDismiss = { replaceTarget = null },
                onReplace = { newQuestion, reason, saveToBank ->
                    scope.launch {
                        var finalQuestion = newQuestion
                        if (saveToBank) {
                            val newId = db.questionDao().insert(newQuestion)
                            finalQuestion = newQuestion.copy(id = newId)
                        }
                        db.replacementReasonDao().insert(
                            ReplacementReason(
                                replacedQuestionText = target.question.text,
                                reason = reason,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        settingsRepo.incrementReplacementsSinceSqueeze()
                        val updated = variantsState.mapIndexed { vi, qs ->
                            if (vi == target.variantIndex) {
                                qs.mapIndexed { qi, q ->
                                    if (qi == target.questionIndex) finalQuestion else q
                                }
                            } else {
                                qs
                            }
                        }
                        variantsState = updated
                        DraftHolder.variants = updated
                        Toast.makeText(context, "Вопрос заменён", Toast.LENGTH_SHORT).show()
                        replaceTarget = null
                    }
                },
                onDeleteQuestion = {
                    scope.launch {
                        QuestionSyncRepository.onQuestionDeleted(context, target.question)
                        val updated = variantsState.mapIndexed { vi, qs ->
                            if (vi == target.variantIndex) {
                                qs.filterIndexed { qi, _ -> qi != target.questionIndex }
                            } else {
                                qs
                            }
                        }
                        variantsState = updated
                        DraftHolder.variants = updated
                        Toast.makeText(context, "Вопрос удалён", Toast.LENGTH_SHORT).show()
                        replaceTarget = null
                    }
                }
            )
        }

        if (!squeezeDialogDismissed && !squeezeRunning && (settings?.replacementsSinceSqueeze ?: 0) >= 15) {
            AlertDialog(
                onDismissRequest = { squeezeDialogDismissed = true },
                text = {
                    Text(
                        text = "У вас накопилось много замен. Обновить инструкции ИИ, " +
                            "чтобы он учитывал их в будущем?",
                        fontSize = 15.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        squeezeDialogDismissed = true
                        val s = settings
                        if (s == null || s.apiKey.isBlank()) {
                            Toast.makeText(context, "Сначала добавьте ключ OpenRouter в Настройках", Toast.LENGTH_SHORT).show()
                        } else {
                            squeezeRunning = true
                            Toast.makeText(context, "Обновляем инструкции…", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val reasons = db.replacementReasonDao().getAllOnce()
                                if (reasons.isEmpty()) {
                                    squeezeRunning = false
                                    Toast.makeText(context, "Нет замен для анализа", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val outcome = AiQuestionGenerator.squeezeReasons(s.apiKey, s, reasons)
                                squeezeRunning = false
                                if (outcome.rules != null) {
                                    val joined = outcome.rules.joinToString("\n")
                                    settingsRepo.saveAiAvoid(joined)
                                    QuestionSyncRepository.onReasonsCleared(context)
                                    settingsRepo.resetReplacementsSinceSqueeze()
                                    Toast.makeText(
                                        context,
                                        "Инструкции обновлены (${outcome.rules.size} правил)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Не удалось обновить: ${outcome.error}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }) {
                        Text(
                            text = "Обновить",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { squeezeDialogDismissed = true }) {
                        Text(text = "Не сейчас", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                text = {
                    Text(
                        text = "Удалить вопрос из банка? Больше он не появится ни в одном тесте.",
                        fontSize = 15.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteTarget = null
                        scope.launch {
                            QuestionSyncRepository.onQuestionDeleted(context, target.question)
                            val updated = variantsState.mapIndexed { vi, qs ->
                                if (vi == target.variantIndex) {
                                    qs.filterIndexed { qi, _ -> qi != target.questionIndex }
                                } else {
                                    qs
                                }
                            }
                            variantsState = updated
                            DraftHolder.variants = updated
                            Toast.makeText(context, "Вопрос удалён", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(text = "Удалить", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

@Composable
private fun VariantCard(
    index: Int,
    questions: List<Question>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditClick: (Int, Question) -> Unit,
    onReplaceClick: (Int, Question) -> Unit,
    onDeleteClick: (Int, Question) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Вариант ${index + 1}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${questions.size} вопросов",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CardBorder)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    questions.forEachIndexed { questionIndex, question ->
                        DraftQuestionBlock(
                            number = questionIndex + 1,
                            question = question,
                            onEditClick = { onEditClick(questionIndex, question) },
                            onReplaceClick = { onReplaceClick(questionIndex, question) },
                            onDeleteClick = { onDeleteClick(questionIndex, question) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftQuestionBlock(
    number: Int,
    question: Question,
    onEditClick: () -> Unit,
    onReplaceClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val optionLabels = listOf("А", "Б", "В", "Г")
    val options = listOf(
        question.optionA,
        question.optionB,
        question.optionC,
        question.optionD
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onReplaceClick) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Заменить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Редактировать",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("$number. ")
                    }
                    append(question.text)
                },
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        options.forEachIndexed { optionIndex, optionText ->
            val isCorrect = optionIndex == question.correctIndex
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (isCorrect) Color(0xFFDFF2E9) else Color(0xFFF5F7F6)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${optionLabels[optionIndex]}) $optionText",
                        fontSize = 13.sp,
                        color = if (isCorrect) {
                            Color(0xFF0B6B52)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (isCorrect) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Правильный ответ",
                            tint = Color(0xFF0B7A5E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceSheet(
    target: ReplaceTarget,
    variantQuestions: List<Question>,
    bankQuestions: List<Question>,
    settings: AppSettings,
    apiKey: String,
    onDismiss: () -> Unit,
    onReplace: (newQuestion: Question, reason: String, saveToBank: Boolean) -> Unit,
    onDeleteQuestion: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("choose") }
    var generating by remember { mutableStateOf(false) }
    var showGenFailDialog by remember { mutableStateOf(false) }
    var confirmFallbackDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (mode == "manual") {
            ManualReplaceForm(
                onCancel = onDismiss,
                onReplace = { newQuestion -> onReplace(newQuestion, reason.trim(), true) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Text(text = "Замена вопроса", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = target.question.text,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Причина замены",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = {
                        Text(
                            text = "Например: не по теме, не проходят в этом классе",
                            fontSize = 14.sp
                        )
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintChip("Не по теме", onHint = { reason = "Не по теме" })
                    HintChip("Не проходят в этом классе", onHint = { reason = "Не проходят в этом классе" })
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintChip("Слишком сложный", onHint = { reason = "Слишком сложный" })
                    HintChip("Слишком лёгкий", onHint = { reason = "Слишком лёгкий" })
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintChip("Неоднозначный", onHint = { reason = "Неоднозначный" })
                    HintChip("Другое", onHint = { reason = "Другое" })
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Новый вопрос",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (reason.isBlank()) {
                                Toast.makeText(context, "Укажите причину замены", Toast.LENGTH_SHORT).show()
                            } else {
                                generating = true
                                scope.launch {
                                    val usedIds = variantQuestions.map { it.id }.toSet()
                                    val candidates = bankQuestions.filter { c ->
                                        c.id != target.question.id &&
                                            c.id !in usedIds &&
                                            c.topic.equals(target.question.topic, ignoreCase = true)
                                    }
                                    val picked = candidates.randomOrNull()
                                    if (picked != null) {
                                        generating = false
                                        onReplace(picked, reason.trim(), false)
                                    } else {
                                        generating = false
                                        showGenFailDialog = true
                                    }
                                }
                            }
                        },
                        enabled = !generating,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = if (generating) "Генерируем…" else "Сгенерировать",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (generating) return@OutlinedButton
                            if (reason.isBlank()) {
                                Toast.makeText(context, "Укажите причину замены", Toast.LENGTH_SHORT).show()
                            } else {
                                mode = "manual"
                            }
                        },
                        enabled = !generating,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Вписать вручную",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "«Сгенерировать» подберёт вопрос той же темы из банка, " +
                        "а если его нет — предложит создать через ИИ или вручную.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Отмена",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showGenFailDialog) {
        AlertDialog(
            onDismissRequest = { showGenFailDialog = false },
            text = {
                Column {
                    Text(
                        text = "Не удалось сгенерировать замену. Что делать?",
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showGenFailDialog = false
                            mode = "manual"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Вписать вручную",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showGenFailDialog = false
                            confirmFallbackDelete = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(text = "Удалить вопрос", fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showGenFailDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Отмена",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (confirmFallbackDelete) {
        AlertDialog(
            onDismissRequest = { confirmFallbackDelete = false },
            text = {
                Text(
                    text = "Удалить вопрос из банка? Больше он не появится ни в одном тесте.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFallbackDelete = false
                    onDeleteQuestion()
                }) {
                    Text(text = "Удалить", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFallbackDelete = false }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HintChip(text: String, onHint: () -> Unit) {
    Surface(
        onClick = onHint,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ManualReplaceForm(onCancel: () -> Unit, onReplace: (Question) -> Unit) {
    val context = LocalContext.current
    var questionText by remember { mutableStateOf("") }
    var optionA by remember { mutableStateOf("") }
    var optionB by remember { mutableStateOf("") }
    var optionC by remember { mutableStateOf("") }
    var optionD by remember { mutableStateOf("") }
    var correctIndex by remember { mutableIntStateOf(0) }
    var difficulty by remember { mutableStateOf("medium") }
    var tricky by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(text = "Новый вопрос вручную", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        FieldLabel("Текст вопроса")
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = questionText,
            onValueChange = { questionText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text(text = "Введите текст вопроса", fontSize = 15.sp) }
        )

        Spacer(modifier = Modifier.height(14.dp))
        FieldLabel("Варианты ответов")
        Spacer(modifier = Modifier.height(8.dp))
        OptionField(label = "А", value = optionA, onValueChange = { optionA = it })
        Spacer(modifier = Modifier.height(8.dp))
        OptionField(label = "Б", value = optionB, onValueChange = { optionB = it })
        Spacer(modifier = Modifier.height(8.dp))
        OptionField(label = "В", value = optionC, onValueChange = { optionC = it })
        Spacer(modifier = Modifier.height(8.dp))
        OptionField(label = "Г", value = optionD, onValueChange = { optionD = it })

        Spacer(modifier = Modifier.height(14.dp))
        FieldLabel("Правильный ответ")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("А", "Б", "В", "Г").forEachIndexed { index, label ->
                ChoiceChip(
                    label = label,
                    selected = correctIndex == index,
                    onClick = { correctIndex = index },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        FieldLabel("Сложность вопроса")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("easy" to "Лёгкий", "medium" to "Средний", "hard" to "Сложный")
                .forEach { (value, label) ->
                    ChoiceChip(
                        label = label,
                        selected = difficulty == value,
                        onClick = { difficulty = value },
                        modifier = Modifier.weight(1f)
                    )
                }
        }

        Spacer(modifier = Modifier.height(12.dp))
        CheckboxCard(
            title = "С подвохом",
            subtitle = "Каверзный вопрос на внимательность",
            checked = tricky,
            onCheckedChange = { tricky = it }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(text = "Отмена", fontSize = 14.sp)
            }
            Button(
                onClick = {
                    when {
                        questionText.isBlank() ->
                            Toast.makeText(context, "Введите текст вопроса", Toast.LENGTH_SHORT).show()
                        optionA.isBlank() ->
                            Toast.makeText(context, "Заполните вариант А", Toast.LENGTH_SHORT).show()
                        optionB.isBlank() ->
                            Toast.makeText(context, "Заполните вариант Б", Toast.LENGTH_SHORT).show()
                        optionC.isBlank() ->
                            Toast.makeText(context, "Заполните вариант В", Toast.LENGTH_SHORT).show()
                        optionD.isBlank() ->
                            Toast.makeText(context, "Заполните вариант Г", Toast.LENGTH_SHORT).show()
                        else -> onReplace(
                            Question(
                                text = questionText.trim(),
                                optionA = optionA.trim(),
                                optionB = optionB.trim(),
                                optionC = optionC.trim(),
                                optionD = optionD.trim(),
                                correctIndex = correctIndex,
                                difficulty = difficulty,
                                tricky = tricky,
                                createdAt = System.currentTimeMillis(),
                                source = "manual"
                            )
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = "Заменить", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// Шаг 25: экран «Журнал ИИ» — последние запросы к моделям.
private val aiLogStatusColors = mapOf(
    "success" to Color(0xFF2E7D32),
    "error" to Color(0xFFC62828),
    "limit" to Color(0xFFEF6C00),
    "not_found" to Color(0xFF757575),
    "timeout" to Color(0xFFF9A825)
)

private fun aiLogStatusLabel(status: String): String = when (status) {
    "success" -> "успех"
    "error" -> "ошибка"
    "limit" -> "лимит"
    "not_found" -> "не найдена"
    "timeout" -> "таймаут"
    else -> status
}

private fun aiLogOperationLabel(operation: String): String = when (operation) {
    "topic" -> "Тема"
    "pdf" -> "Файл"
    "topup" -> "Добор"
    "replace" -> "Замена"
    "squeeze" -> "Сжатие"
    else -> operation
}

@Composable
fun AiLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = (context.applicationContext as TestoGenApp).database
    val entries by db.aiLogDao().getLatestFlow(50).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("d MMM HH:mm", Locale("ru")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = "Журнал ИИ",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { confirmClear = true }) {
                Text(text = "Очистить", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = "Последние 50 запросов. Обновляется автоматически.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                text = "Пока пусто",
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
                items(entries) { entry ->
                    val statusColor = aiLogStatusColors[entry.status] ?: Color(0xFF757575)
                    OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = aiLogStatusLabel(entry.status),
                                    color = statusColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = timeFormat.format(Date(entry.timestamp)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = entry.model,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = aiLogOperationLabel(entry.operation) +
                                    if (entry.message.isNotBlank()) " · " + entry.message else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(text = "Очистить журнал ИИ?", fontSize = 15.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { db.aiLogDao().clearAll() }
                }) {
                    Text(
                        text = "Очистить",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
