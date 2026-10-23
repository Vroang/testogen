package com.testogen.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.testogen.app.data.db.Difficulty
import com.testogen.app.ui.theme.DifficultyPalette
import com.testogen.app.ui.theme.LocalDimens
import com.testogen.app.ui.theme.Palette

/** Мягкая тёплая тень. */
fun Modifier.softShadow(shape: Shape, elevation: Dp = 6.dp): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = Palette.Shadow.copy(alpha = 0.10f),
    spotColor = Palette.Shadow.copy(alpha = 0.16f),
)

/** Лёгкое «проседание» при нажатии — масштаб считается в фазе отрисовки, без перекомпоновки. */
@Composable
private fun pressScale(interaction: MutableInteractionSource): () -> Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return { scale }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(LocalDimens.current.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    if (onClick == null) {
        Surface(
            modifier = modifier.softShadow(shape),
            shape = shape,
            color = color,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        val interaction = remember { MutableInteractionSource() }
        val scale = pressScale(interaction)
        Surface(
            onClick = onClick,
            modifier = modifier
                .graphicsLayer { scale().let { scaleX = it; scaleY = it } }
                .softShadow(shape),
            shape = shape,
            color = color,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = LocalDimens.current.buttonHeight)
            .graphicsLayer { scale().let { scaleX = it; scaleY = it } },
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = Palette.SurfaceSunken,
            disabledContentColor = Palette.TextHint,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = LocalDimens.current.buttonHeight)
            .graphicsLayer { scale().let { scaleX = it; scaleY = it } },
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Palette.PrimarySoft,
            contentColor = Palette.OnPrimarySoft,
        ),
    ) {
        ButtonContent(text, icon)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
}

/** Крупный заголовок вкладки; справа — необязательные кнопки. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = LocalDimens.current.screenPadding, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** Заголовок вложенного экрана со стрелкой «назад». */
@Composable
fun BackHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 6.dp, end = LocalDimens.current.screenPadding, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Иконка в мягком кружке. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(Palette.PrimarySoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Primary, modifier = Modifier.size(size * 0.5f))
    }
}

/** Пустое состояние: иконка, заголовок, пояснение и необязательная кнопка. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, size = 84.dp)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(actionText, onAction)
        }
    }
}

@Composable
fun DifficultyTag(difficulty: Difficulty, modifier: Modifier = Modifier) {
    val colors = when (difficulty) {
        Difficulty.EASY -> DifficultyPalette.Easy
        Difficulty.MEDIUM -> DifficultyPalette.Medium
        Difficulty.HARD -> DifficultyPalette.Hard
    }
    Surface(color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            difficulty.title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Шестерёнка в заголовке вкладки. */
@Composable
fun SettingsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(end = 4.dp).size(56.dp)) {
        Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = Palette.TextSecondary, modifier = Modifier.size(28.dp))
    }
}

/** Выбираемая «таблетка» (сложность, класс). */
@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
) {
    val container by androidx.compose.animation.animateColorAsState(
        if (selected) Palette.Primary else Palette.Surface,
        androidx.compose.animation.core.tween(180),
        label = "pill",
    )
    val content by androidx.compose.animation.animateColorAsState(
        if (selected) Color.White else Palette.Text,
        androidx.compose.animation.core.tween(180),
        label = "pillText",
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Palette.Outline),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

/** Крупные кнопки «−» и «+» вокруг числа. */
@Composable
fun CountStepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled = value > range.first) { onChange((value - step).coerceAtLeast(range.first)) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(88.dp),
        )
        StepButton("+", enabled = value < range.last) { onChange((value + step).coerceAtMost(range.last)) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) Palette.PrimarySoft else Palette.SurfaceSunken,
        contentColor = if (enabled) Palette.OnPrimarySoft else Palette.TextHint,
        modifier = Modifier.size(60.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

/** Поле ввода в общем стиле. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Palette.TextHint) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        keyboardOptions = keyboardOptions,
        leadingIcon = leading,
        trailingIcon = trailing,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Palette.Surface,
            focusedContainerColor = Palette.Surface,
            unfocusedBorderColor = Palette.Outline,
        ),
    )
}

/** Пульсирующая иконка «идёт работа» — анимация только в фазе отрисовки. */
@Composable
fun PulsingBadge(icon: ImageVector, active: Boolean, modifier: Modifier = Modifier, size: Dp = 76.dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    IconBadge(
        icon,
        size = size,
        modifier = modifier.graphicsLayer {
            val s = if (active) pulse else 1f
            scaleX = s
            scaleY = s
        },
    )
}

/** Выбор класса: «Не важно» и 5–11 в две ровные строки — всё видно сразу, без прокрутки. */
@Composable
fun GradePicker(grade: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Не важно", grade == null, { onChange(null) }, Modifier.weight(2f), horizontalPadding = 4.dp)
            for (g in 5..7) ChoicePill("$g", grade == g, { onChange(g) }, Modifier.weight(1f), horizontalPadding = 4.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (g in 8..11) ChoicePill("$g", grade == g, { onChange(g) }, Modifier.weight(1f), horizontalPadding = 4.dp)
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Заготовка карточки, пока нейросеть думает: мягко «дышит», анимация только в фазе отрисовки. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.graphicsLayer { this.alpha = alpha }) {
            listOf(0.9f, 0.6f).forEach { width ->
                Box(Modifier.fillMaxWidth(width).height(18.dp).background(Palette.SurfaceSunken, MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.height(10.dp))
            }
            Box(Modifier.width(76.dp).height(26.dp).background(Palette.SurfaceSoft, MaterialTheme.shapes.extraSmall))
        }
    }
}
