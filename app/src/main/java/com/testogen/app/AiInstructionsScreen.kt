package com.testogen.app

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

private val AiCardBorder = Color(0xFFE3E8E6)
private val AiStatusOk = Color(0xFF0E7C6B)
private val AiStatusBad = Color(0xFFC62828)
private val AiStatusWarn = Color(0xFFB26A00)

private data class AiTexts(val system: String, val avoid: String, val subjects: String)

@Composable
fun AiInstructionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TestoGenApp
    val repo = app.settingsRepository
    val db = app.database
    val scope = rememberCoroutineScope()

    val settings by repo.settings.collectAsState(initial = null)

    var initialized by remember { mutableStateOf(false) }
    var systemText by remember { mutableStateOf("") }
    var avoidText by remember { mutableStateOf("") }
    var subjectsText by remember { mutableStateOf("") }
    var squeezing by remember { mutableStateOf(false) }
    var clearReasonsDialog by remember { mutableStateOf(false) }
    var clearAllDialog by remember { mutableStateOf(false) }
    var factoryResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        val s = settings
        if (s != null && !initialized) {
            systemText = s.aiSystem
            avoidText = s.aiAvoid
            subjectsText = s.aiSubjects
            initialized = true
        }
    }

    val avoidRuleCount = avoidText.lines().count { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() && !trimmed.startsWith("#")
    }

    val saveAll: () -> Unit = {
        scope.launch {
            repo.saveAiSystem(systemText)
            repo.saveAiAvoid(avoidText)
            repo.saveAiSubjects(subjectsText)
            Toast.makeText(context, "Инструкции сохранены", Toast.LENGTH_SHORT).show()
        }
    }

    val runSqueeze: () -> Unit = {
        val s = settings
        when {
            squeezing -> {}
            s == null || s.apiKey.isBlank() ->
                Toast.makeText(context, "Сначала добавьте ключ OpenRouter в Настройках", Toast.LENGTH_SHORT).show()
            else -> {
                squeezing = true
                scope.launch {
                    val reasons = db.replacementReasonDao().getAllOnce()
                    if (reasons.isEmpty()) {
                        squeezing = false
                        Toast.makeText(context, "Нет замен для анализа", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val outcome = AiQuestionGenerator.squeezeReasons(s.apiKey, s, reasons)
                    squeezing = false
                    if (outcome.rules != null) {
                        val joined = outcome.rules.joinToString("\n")
                        avoidText = joined
                        repo.saveAiAvoid(joined)
                        db.replacementReasonDao().deleteAll()
                        repo.resetReplacementsSinceSqueeze()
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
        }
    }

    val clearReasons: () -> Unit = {
        scope.launch {
            db.replacementReasonDao().deleteAll()
            repo.resetReplacementsSinceSqueeze()
            avoidText = ""
            repo.saveAiAvoid("")
            Toast.makeText(context, "Причины замен очищены", Toast.LENGTH_SHORT).show()
        }
    }

    val clearAllInstructions: () -> Unit = {
        scope.launch {
            systemText = SettingsRepository.DEFAULT_AI_SYSTEM_TEXT
            avoidText = ""
            subjectsText = ""
            repo.saveAiSystem(systemText)
            repo.saveAiAvoid("")
            repo.saveAiSubjects("")
            Toast.makeText(context, "Инструкции сброшены", Toast.LENGTH_SHORT).show()
        }
    }

    val factoryReset: () -> Unit = {
        scope.launch {
            db.replacementReasonDao().deleteAll()
            repo.resetReplacementsSinceSqueeze()
            systemText = SettingsRepository.DEFAULT_AI_SYSTEM_TEXT
            avoidText = ""
            subjectsText = ""
            repo.saveAiSystem(systemText)
            repo.saveAiAvoid("")
            repo.saveAiSubjects("")
            Toast.makeText(context, "Сброшено до заводских настроек", Toast.LENGTH_SHORT).show()
        }
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "aiInstructionsAppear"
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
                    text = "Инструкции для ИИ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Эти тексты автоматически прикладываются к каждому запросу к ИИ. " +
                    "Меняйте, если хотите скорректировать поведение модели.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            InstructionCard(
                title = "Основная инструкция (system)",
                explanation = "Кто ты и как отвечать. Трогайте осторожно",
                value = systemText,
                onValueChange = { systemText = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            InstructionCard(
                title = "Чего не делать (avoid)",
                explanation = "Правила «не делай так». Сюда будут добавляться причины замен. " +
                    "Можете править вручную",
                value = avoidText,
                onValueChange = { avoidText = it },
                ruleCount = avoidRuleCount
            )

            Spacer(modifier = Modifier.height(16.dp))

            InstructionCard(
                title = "Предметные нюансы (subjects)",
                explanation = "Особенности по предметам и классам. Свободная форма",
                value = subjectsText,
                onValueChange = { subjectsText = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = runSqueeze,
                enabled = !squeezing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (squeezing) "Обновляем…" else "Обновить инструкции",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = saveAll,
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
                    text = "Сохранить",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { clearReasonsDialog = true },
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
                Text(text = "Очистить причины замен", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { clearAllDialog = true },
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
                Text(text = "Очистить ВСЕ инструкции", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { factoryResetDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сбросить до заводских",
                    fontSize = 14.sp,
                    color = AiStatusBad
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (clearReasonsDialog) {
        AlertDialog(
            onDismissRequest = { clearReasonsDialog = false },
            text = {
                Text(
                    text = "Удалить все причины замен и очистить раздел «Чего не делать»? " +
                        "Это нельзя отменить.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearReasonsDialog = false
                    clearReasons()
                }) {
                    Text(text = "Удалить", color = AiStatusBad, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearReasonsDialog = false }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (clearAllDialog) {
        AlertDialog(
            onDismissRequest = { clearAllDialog = false },
            text = {
                Text(
                    text = "Сбросить все три инструкции к значениям по умолчанию? " +
                        "Это нельзя отменить.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearAllDialog = false
                    clearAllInstructions()
                }) {
                    Text(text = "Сбросить", color = AiStatusBad, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearAllDialog = false }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (factoryResetDialog) {
        AlertDialog(
            onDismissRequest = { factoryResetDialog = false },
            text = {
                Text(
                    text = "Полный сброс: инструкции к значениям по умолчанию " +
                        "И удаление всех причин замен. Это нельзя отменить.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    factoryResetDialog = false
                    factoryReset()
                }) {
                    Text(text = "Сбросить всё", color = AiStatusBad, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { factoryResetDialog = false }) {
                    Text(text = "Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun InstructionCard(
    title: String,
    explanation: String,
    value: String,
    onValueChange: (String) -> Unit,
    ruleCount: Int? = null
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, AiCardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Что это: $explanation",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (ruleCount != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ruleCount > 30) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = AiStatusWarn,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "Правил: $ruleCount",
                        fontSize = 12.sp,
                        color = if (ruleCount > 30) AiStatusWarn else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ruleCount > 30) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Правил многовато, подчистите лишнее",
                        fontSize = 11.sp,
                        color = AiStatusWarn
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${value.length} символов",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}
