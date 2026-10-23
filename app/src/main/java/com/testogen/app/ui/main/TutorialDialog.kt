package com.testogen.app.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.testogen.app.tutorial.TutorialState
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.Palette
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Загрузка обучающего ролика: кольцо прогресса, проценты, «Отмена»; ошибка — «Повторить». */
@Composable
fun TutorialDialog() {
    val tutorial = LocalAppContainer.current.tutorial
    val state by tutorial.state.collectAsStateWithLifecycle()
    val s = state
    if (s == TutorialState.Idle) return
    val busy = s == TutorialState.Checking || s is TutorialState.Downloading
    Dialog(
        onDismissRequest = { if (!busy) tutorial.dismiss() },
        properties = DialogProperties(dismissOnClickOutside = !busy, usePlatformDefaultWidth = false),
    ) {
        SoftCard(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                when (s) {
                    TutorialState.Checking, is TutorialState.Downloading -> {
                        val d = s as? TutorialState.Downloading
                        ProgressRing(d?.progress)
                        Spacer(Modifier.height(18.dp))
                        Text("Загружаю обучающий ролик", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                d == null -> "Ищу ролик…"
                                d.progress != null -> "${(d.progress * 100).toInt()} % · ${mb(d.doneBytes)} из ${mb(d.totalBytes)} МБ"
                                else -> "${mb(d.doneBytes)} МБ"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ролик скачается один раз и откроется сам. Потом его можно смотреть и без интернета.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextHint,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        SecondaryButton("Отмена", tutorial::cancel, Modifier.fillMaxWidth(), singleLine = true)
                    }
                    is TutorialState.Failed -> {
                        Text("Не получилось загрузить ролик", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Закрыть", tutorial::dismiss, Modifier.weight(1f), singleLine = true)
                            PrimaryButton("Повторить", tutorial::watch, Modifier.weight(1f), singleLine = true)
                        }
                    }
                    TutorialState.NotPublished -> {
                        Text("Ролик ещё готовится", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Он появится с одним из следующих обновлений. Посмотреть его можно будет в «Настройках» — «Как пользоваться».",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        PrimaryButton("Понятно", tutorial::dismiss, Modifier.fillMaxWidth(), singleLine = true)
                    }
                    TutorialState.Idle -> Unit
                }
            }
        }
    }
}

private fun mb(bytes: Long) = String.format(Locale("ru"), "%.1f", bytes / 1_048_576.0)

/**
 * Кольцо ожидания: дуга растёт с процентами (пока размер неизвестен — бежит по кругу), по кольцу кружит
 * искорка, значок «Play» в центре мягко дышит. Все анимации — в фазе рисования.
 */
@Composable
private fun ProgressRing(progress: Float?) {
    val transition = rememberInfiniteTransition(label = "ring")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "spin")
    val breath by transition.animateFloat(
        0.92f,
        1.06f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val shown by animateFloatAsState(progress ?: 0f, tween(350), label = "progress")
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val stroke = 10.dp.toPx()
            val radius = (size.minDimension - stroke) / 2
            drawCircle(Palette.PrimarySoft, radius = radius, style = Stroke(stroke))
            if (progress == null) {
                rotate(spin) {
                    drawArc(Palette.Primary, -90f, 90f, false, style = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                }
            } else {
                drawArc(Palette.Primary, -90f, 360f * shown, false, style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
            }
            // Искорка на конце дуги
            val a = Math.toRadians((if (progress == null) spin else -90f + 360f * shown).toDouble())
            val c = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
            drawCircle(Palette.Accent.copy(alpha = 0.25f), radius = stroke * 1.1f, center = c)
            drawCircle(Palette.Accent, radius = stroke * 0.45f, center = c)
        }
        Box(
            Modifier.size(64.dp).graphicsLayer {
                scaleX = breath
                scaleY = breath
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(64.dp)) { drawCircle(Palette.PrimarySoft) }
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Palette.Primary, modifier = Modifier.size(38.dp))
        }
    }
}
