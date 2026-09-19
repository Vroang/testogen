package com.testogen.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun changesLine(changes: CloudChanges): String = buildString {
    append("Обнаружены изменения из веб-версии:")
    if (changes.newQuestions.isNotEmpty()) {
        append("\n• Новых вопросов: ${changes.newQuestions.size}")
        changes.newQuestions.take(3).forEach { append("\n   «${it.text}»") }
        if (changes.newQuestions.size > 3) {
            append("\n   и ещё ${changes.newQuestions.size - 3}")
        }
    }
    if (changes.updatedQuestions.isNotEmpty()) {
        append("\n• Изменённых: ${changes.updatedQuestions.size}")
        changes.updatedQuestions.take(3).forEach {
            append("\n   «${it.oldText}» → «${it.newText}»")
        }
        if (changes.updatedQuestions.size > 3) {
            append("\n   и ещё ${changes.updatedQuestions.size - 3}")
        }
    }
    if (changes.deletedQuestions.isNotEmpty()) {
        append("\n• Удалённых: ${changes.deletedQuestions.size}")
    }
    if (changes.newTextbooks.isNotEmpty()) {
        append("\n• Новых учебников: ${changes.newTextbooks.size}")
    }
    if (changes.deletedTextbooks.isNotEmpty()) {
        append("\n• Удалённых учебников: ${changes.deletedTextbooks.size}")
    }
    if (changes.newReasons > 0) append("\n• Новых причин замен: ${changes.newReasons}")
    if (changes.updatedReasons > 0) append("\n• Изменённых причин: ${changes.updatedReasons}")
    if (changes.deletedReasons > 0) append("\n• Удалённых причин: ${changes.deletedReasons}")
}

/** Шаг 32: диалог подтверждения изменений из веб-версии. */
@Composable
fun CloudChangesDialog(
    changes: CloudChanges,
    onCancel: () -> Unit,
    onDetails: () -> Unit,
    onApply: () -> Unit,
    applying: Boolean
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = if (changes.isMassDeletion) {
                    "⚠️ Массовое удаление"
                } else {
                    "Изменения из веб-версии"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (changes.isMassDeletion) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (changes.isMassDeletion) {
                    Text(
                        text = "Из веб-версии пришло удаление ${changes.deletedQuestions.size} вопросов. " +
                            "Это большая потеря данных. Проверьте, не ошиблись ли вы в вебе.",
                        fontSize = 14.sp,
                        color = Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Удалённые вопросы:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    changes.deletedQuestions.take(10).forEach {
                        Text(text = "• «${it.text}»", fontSize = 12.sp)
                    }
                    if (changes.deletedQuestions.size > 10) {
                        Text(
                            text = "…и ещё ${changes.deletedQuestions.size - 10} (полный список — в «Подробнее»)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(text = changesLine(changes), fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                enabled = !applying,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (applying) "Применяем…" else "Применить",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel, enabled = !applying) {
                    Text(text = "Отменить", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDetails, enabled = !applying) {
                    Text(text = "Подробнее", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    )
}

/** Шаг 32: полный список изменений с применением. */
@Composable
fun ChangesDetailScreen(
    changes: CloudChanges,
    applying: Boolean,
    onBack: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = "Изменения из веб-версии",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            changesSection("Новые вопросы (${changes.newQuestions.size})", changes.newQuestions.isNotEmpty()) {
                changes.newQuestions.forEach {
                    changesCard("«${it.text}»", MaterialTheme.colorScheme.surface)
                }
            }
            changesSection("Изменённые вопросы (${changes.updatedQuestions.size})", changes.updatedQuestions.isNotEmpty()) {
                changes.updatedQuestions.forEach {
                    changesCard("«${it.oldText}»\n→ «${it.newText}»", MaterialTheme.colorScheme.surface)
                }
            }
            changesSection("Удалённые вопросы (${changes.deletedQuestions.size})", changes.deletedQuestions.isNotEmpty()) {
                changes.deletedQuestions.forEach {
                    changesCard("«${it.text}»", Color(0xFFFFEBEE))
                }
            }
            val textbookChanges = changes.newTextbooks.isNotEmpty() || changes.deletedTextbooks.isNotEmpty()
            changesSection("Учебники: +${changes.newTextbooks.size} / −${changes.deletedTextbooks.size}", textbookChanges) {
                changes.newTextbooks.forEach { changesCard("Новый: ${it.name}", MaterialTheme.colorScheme.surface) }
                changes.deletedTextbooks.forEach {
                    changesCard("Удалён: ${it.name}", Color(0xFFFFEBEE))
                }
            }
            val reasonChanges = changes.newReasons > 0 || changes.updatedReasons > 0 || changes.deletedReasons > 0
            changesSection(
                "Причины замен: +${changes.newReasons} / ~${changes.updatedReasons} / −${changes.deletedReasons}",
                reasonChanges
            ) {}
            Spacer(modifier = Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onApply,
                enabled = !applying,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (applying) "Применяем…" else "Применить",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onBack,
                enabled = !applying,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(text = "Отменить", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun changesSection(title: String, visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
    content()
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun changesCard(text: String, container: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}
