package com.testogen.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class KeyStatus { Idle, Checking, Valid, Invalid, NetworkError }

private val SettingsCardBorder = Color(0xFFE3E8E6)
private val StatusOk = Color(0xFF0E7C6B)
private val StatusBad = Color(0xFFC62828)
private val StatusWarn = Color(0xFFB26A00)

private fun isFree(model: ModelInfo): Boolean =
    (model.pricing.prompt.toDoubleOrNull() ?: 0.0) <= 0.0 &&
        (model.pricing.completion.toDoubleOrNull() ?: 0.0) <= 0.0

private fun priceLabel(model: ModelInfo): String {
    val prompt = (model.pricing.prompt.toDoubleOrNull() ?: 0.0) * 1_000_000
    return if (prompt <= 0.0 && (model.pricing.completion.toDoubleOrNull() ?: 0.0) <= 0.0) {
        "Бесплатно"
    } else {
        String.format(java.util.Locale.US, "$%.2f/1M", prompt)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, SettingsCardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAiInstructions: () -> Unit,
    onOpenAiLog: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val repo = (context.applicationContext as TestoGenApp).settingsRepository
    val scope = rememberCoroutineScope()

    val settings by repo.settings.collectAsState(initial = null)

    var initialized by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var onlyFree by remember { mutableStateOf(true) }
    var cascadeFreeText by remember { mutableStateOf<String?>(null) }
    var cascadePaidText by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var modelsLoadedOnce by remember { mutableStateOf(false) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var keyStatus by remember { mutableStateOf<KeyStatus>(KeyStatus.Idle) }
    var cascadeStatus by remember { mutableStateOf<String?>(null) }
    var cascadeChecking by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        val s = settings
        if (s != null && !initialized) {
            apiKey = s.apiKey
            initialized = true
        }
    }

    val loadModels: () -> Unit = {
        if (!modelsLoading) {
            modelsLoading = true
            modelsError = null
            scope.launch {
                when (val r = OpenRouterClient.fetchModels(apiKey)) {
                    is ApiResult.Success -> {
                        models = r.data
                        modelsLoadedOnce = true
                        // Шаг 10.1: динамический дефолт бесплатного каскада —
                        // 2–3 актуальные :free-модели по порядку ответа API.
                        // Применяется только если пользователь сам не менял список.
                        val sNow = settings
                        if (sNow != null && !sNow.cascadeFreeCustom) {
                            val freeTop = models
                                .filter { it.id.endsWith(":free") && isFree(it) }
                                .take(3)
                            if (freeTop.isNotEmpty()) {
                                repo.saveCascadeFree(freeTop.joinToString(", ") { it.id })
                            }
                        }
                    }
                    is ApiResult.Error -> modelsError = r.message
                }
                modelsLoading = false
            }
        }
    }

    LaunchedEffect(initialized) {
        if (initialized && apiKey.isNotBlank() && !modelsLoadedOnce) {
            loadModels()
        }
    }

    val checkKey: () -> Unit = {
        if (apiKey.isBlank()) {
            Toast.makeText(context, "Введите ключ", Toast.LENGTH_SHORT).show()
        } else {
            keyStatus = KeyStatus.Checking
            scope.launch {
                repo.saveApiKey(apiKey)
                when (val r = OpenRouterClient.checkKey(apiKey)) {
                    is ModelCheck.Works -> {
                        keyStatus = KeyStatus.Valid
                        if (!modelsLoadedOnce) loadModels()
                    }
                    is ModelCheck.RateLimited -> keyStatus = KeyStatus.Valid
                    is ModelCheck.Failed -> keyStatus =
                        if (r.message.contains("Неверный")) KeyStatus.Invalid else KeyStatus.NetworkError
                }
            }
        }
    }

    val runCascade: () -> Unit = {
        val current = settings
        if (apiKey.isBlank()) {
            Toast.makeText(context, "Сначала введите ключ", Toast.LENGTH_SHORT).show()
        } else {
            cascadeChecking = true
            cascadeStatus = null
            scope.launch {
                val order = current?.cascadeOrder().orEmpty()
                if (order.isEmpty()) {
                    cascadeChecking = false
                    cascadeStatus = "Каскад пуст — укажите модели"
                    return@launch
                }
                val report = StringBuilder()
                var working: String? = null
                for (model in order) {
                    when (val r = OpenRouterClient.checkModel(apiKey, model)) {
                        is ModelCheck.Works -> {
                            report.append("✓ ").append(model).append(" — работает\n")
                            if (working == null) working = model
                        }
                        is ModelCheck.RateLimited ->
                            report.append("… ").append(model).append(" — лимит исчерпан (429)\n")
                        is ModelCheck.Failed ->
                            report.append("✗ ").append(model).append(" — ").append(r.message).append('\n')
                    }
                }
                cascadeChecking = false
                cascadeStatus =
                    (if (working != null) "Первая рабочая модель: $working\n" else "Рабочих моделей не найдено\n") +
                        report
            }
        }
    }

    val cascadeFreeEffective = cascadeFreeText ?: settings?.cascadeFree.orEmpty()
    val cascadePaidEffective = cascadePaidText ?: settings?.cascadePaid.orEmpty()
    val selectedModel = settings?.model.orEmpty()
    val filteredModels = if (onlyFree) models.filter(::isFree) else models

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 180,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "settingsAppear"
    )

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
                    text = "Настройки",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsCard {
                SectionLabel("API-ключ OpenRouter")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        scope.launch { repo.saveApiKey(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text(text = "sk-or-v1-…", fontSize = 14.sp) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = checkKey,
                    enabled = keyStatus != KeyStatus.Checking,
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
                        text = if (keyStatus == KeyStatus.Checking) "Проверяем…" else "Проверить ключ",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                when (keyStatus) {
                    KeyStatus.Valid -> Spacer(modifier = Modifier.height(8.dp))
                    KeyStatus.Invalid -> Spacer(modifier = Modifier.height(8.dp))
                    KeyStatus.NetworkError -> Spacer(modifier = Modifier.height(8.dp))
                    else -> {}
                }
                when (keyStatus) {
                    KeyStatus.Valid -> Text(
                        text = "Ключ работает",
                        fontSize = 13.sp,
                        color = StatusOk
                    )
                    KeyStatus.Invalid -> Text(
                        text = "Неверный ключ",
                        fontSize = 13.sp,
                        color = StatusBad
                    )
                    KeyStatus.NetworkError -> Text(
                        text = "Нет соединения — проверьте интернет",
                        fontSize = 13.sp,
                        color = StatusWarn
                    )
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard {
                SectionLabel("Модель")
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Surface(
                        onClick = { onlyFree = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        color = if (onlyFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (onlyFree) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (onlyFree) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Только бесплатные",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    androidx.compose.material3.Surface(
                        onClick = { onlyFree = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        color = if (!onlyFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (!onlyFree) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = if (!onlyFree) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Все модели",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box {
                    OutlinedTextField(
                        value = selectedModel.ifBlank { "Выберите модель" },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                if (models.isNotEmpty()) dropdownExpanded = true
                                else Toast.makeText(context, "Сначала проверьте ключ — загрузится список моделей", Toast.LENGTH_SHORT).show()
                            }
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val autoEntry = ModelInfo(id = "openrouter/auto", name = "openrouter/auto")
                        val displayList =
                            listOf(autoEntry) + filteredModels.filter { it.id != "openrouter/auto" }
                        displayList.forEach { model ->
                            val isAuto = model.id == "openrouter/auto"
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = model.id,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (isAuto) {
                                                "автоматический выбор (рекомендуется)"
                                            } else {
                                                model.name
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Text(
                                        text = if (isAuto) "рекомендуется" else priceLabel(model),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    scope.launch { repo.saveModel(model.id) }
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                if (modelsLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Загрузка моделей…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                modelsError?.let { error ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = error, fontSize = 12.sp, color = StatusBad)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard {
                SectionLabel("Каскад моделей")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Если основная модель упрётся в лимит (429), приложение попробует резервные бесплатные, затем платные.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Основная модель:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = selectedModel.ifBlank { "не выбрана" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Резервные бесплатные (через запятую):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = cascadeFreeEffective,
                    onValueChange = {
                        cascadeFreeText = it
                        scope.launch {
                            repo.saveCascadeFree(it)
                            repo.saveCascadeFreeCustom(true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    placeholder = {
                        Text(
                            text = "Заполнится автоматически после проверки ключа",
                            fontSize = 13.sp
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Резервные платные (через запятую):",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = cascadePaidEffective,
                    onValueChange = {
                        cascadePaidText = it
                        scope.launch { repo.saveCascadePaid(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = runCascade,
                    enabled = !cascadeChecking,
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
                        text = if (cascadeChecking) "Проверяем каскад…" else "Проверить каскад",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                cascadeStatus?.let { status ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard {
                SectionLabel("Инструкции для ИИ")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Основная инструкция, правила «чего не делать» и предметные нюансы. " +
                        "Прикладываются к каждому запросу к ИИ.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAiInstructions,
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
                        text = "Открыть редактор",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard {
                SectionLabel("Журнал ИИ")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последние запросы к моделям: какая пробовалась, " +
                        "что упало и почему. Помогает понять, почему вопросы не сгенерировались.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAiLog,
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
                        text = "Посмотреть последние запросы",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Шаг 30: состояние синхронизации + восстановление из облака.
            val appDb = (context.applicationContext as TestoGenApp).database
            val pendingUploads by appDb.textbookDao().countPendingFlow().collectAsState(initial = 0)
            val pendingDeletes by appDb.pendingDeleteDao().countFlow().collectAsState(initial = 0)
            // Шаг 31: в очередь входят и вопросы с причинами замен.
            val pendingQuestions by appDb.questionDao().countPendingFlow().collectAsState(initial = 0)
            val pendingReasons by appDb.replacementReasonDao().countPendingFlow().collectAsState(initial = 0)
            val pendingTotal = pendingUploads + pendingDeletes + pendingQuestions + pendingReasons
            var restoring by remember { mutableStateOf(false) }
            SettingsCard {
                SectionLabel("Синхронизация с облаком")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (pendingTotal == 0) {
                        "Всё синхронизировано"
                    } else {
                        "Синхронизация с облаком: $pendingTotal в очереди"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                var confirmRestore by remember { mutableStateOf(false) }
                Button(
                    onClick = { confirmRestore = true },
                    enabled = !restoring,
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
                        text = if (restoring) "Восстанавливаем…" else "Восстановить из облака",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (confirmRestore) {
                    AlertDialog(
                        onDismissRequest = { confirmRestore = false },
                        text = {
                            Text(
                                text = "Восстановить учебники из облака? Уже скачанные будут пропущены.",
                                fontSize = 15.sp
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmRestore = false
                                restoring = true
                                scope.launch {
                                    val result = TextbookRepository.restoreFromCloud(context)
                                    restoring = false
                                    result.fold(
                                        onSuccess = { n ->
                                            Toast.makeText(
                                                context,
                                                "Восстановлено учебников: $n",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onFailure = { e ->
                                            Toast.makeText(
                                                context,
                                                "Не удалось восстановить: ${e.message ?: "ошибка"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                            }) {
                                Text(
                                    text = "Восстановить",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRestore = false }) {
                                Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var confirmSignOut by remember { mutableStateOf(false) }
            TextButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Выйти из аккаунта",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (confirmSignOut) {
                AlertDialog(
                    onDismissRequest = { confirmSignOut = false },
                    text = {
                        Text(
                            text = "Выйти из аккаунта? Учебники останутся в облаке.",
                            fontSize = 15.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmSignOut = false
                            onSignOut()
                        }) {
                            Text(
                                text = "Выйти",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmSignOut = false }) {
                            Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
