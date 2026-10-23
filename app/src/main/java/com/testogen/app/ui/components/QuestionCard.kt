package com.testogen.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.ui.theme.Palette

/** Вопрос банка: тап раскрывает варианты (верный отмечен) и пояснение. */
@Composable
fun QuestionCard(
    question: QuestionEntity,
    modifier: Modifier = Modifier,
    number: Int? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(question.id) { mutableStateOf(false) }
    SoftCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = if (expanded) 8.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (number != null) {
                Text("$number.", style = MaterialTheme.typography.titleMedium, color = Palette.TextSecondary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                question.text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DifficultyTag(Difficulty.of(question.difficulty))
            question.grade?.let {
                Text("$it класс", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(260)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(220)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                val options = listOf(question.option1, question.option2, question.option3, question.option4)
                options.forEachIndexed { i, option ->
                    val correct = i == question.correctIndex
                    Surface(
                        color = if (correct) Palette.PrimarySoft else Palette.Background,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}) $option",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (correct) Palette.OnPrimarySoft else Palette.Text,
                                modifier = Modifier.weight(1f),
                            )
                            if (correct) {
                                Icon(Icons.Filled.Check, contentDescription = "Верный ответ", tint = Palette.Primary, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                question.explanation?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Palette.TextSecondary)
                }
                if (onEdit != null || onDelete != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (onEdit != null) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Изменить")
                            }
                        }
                        if (onDelete != null) {
                            TextButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = Palette.Error)
                                Spacer(Modifier.width(6.dp))
                                Text("Удалить", color = Palette.Error)
                            }
                        }
                    }
                }
            }
        }
    }
}
