package com.testogen.app.debug

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.testogen.app.ui.components.IconBadge
import com.testogen.app.ui.components.PrimaryButton
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SoftCard
import com.testogen.app.ui.theme.AppIcons
import com.testogen.app.ui.theme.Palette
import com.testogen.app.ui.theme.TestoGenTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Демо праздничного экрана (этап 8, только debug-сборка, в приложение не встроено):
 * салют с разных углов экрана и поздравление с кнопками «Смотреть» и «Позже».
 */
class CelebrationDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TestoGenTheme {
                CelebrationScreen(
                    onWatch = { Toast.makeText(this, "Демо: здесь начнётся загрузка ролика", Toast.LENGTH_SHORT).show() },
                    onLater = { finish() },
                )
            }
        }
    }
}

@Composable
private fun CelebrationScreen(onWatch: () -> Unit, onLater: () -> Unit) {
    val density = LocalDensity.current.density
    val fireworks = remember { Fireworks(density) }
    // Счётчик кадров читается только при рисовании: каждый кадр перерисовывает холст без перекомпоновки
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        // Небольшая пауза: пусть сначала исчезнет системная заставка запуска
        kotlinx.coroutines.delay(START_DELAY_MS)
        var last = 0L
        while (true) {
            val alive = withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                fireworks.step(dt).also { frame++ }
            }
            if (!alive) break
        }
    }
    val card = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(START_DELAY_MS + 900)
        card.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            SoftCard(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = card.value
                    val s = 0.92f + 0.08f * card.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - card.value) * 40f * density
                },
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(AppIcons.Sparkle, size = 72.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Здравствуйте, Наталья Дмитриевна!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "ТестоГен полностью обновился. Claude Code провёл масштабное обновление под чутким руководством Вани.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Посмотрите короткий ролик — в нём показано, как всё теперь устроено.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Позже", onLater, Modifier.weight(1f), singleLine = true)
                        PrimaryButton("Смотреть", onWatch, Modifier.weight(1f), singleLine = true)
                    }
                }
            }
        }
        // Салют — на переднем плане, поверх карточки; нажатия он не перехватывает
        Canvas(Modifier.fillMaxSize()) {
            frame // подписка на кадр
            fireworks.draw(this)
        }
    }
}

private const val START_DELAY_MS = 600L

/**
 * Салют без лишних выделений памяти: искры в заранее выделенных массивах, расчёт и рисование —
 * простые циклы. Ракеты взлетают из нижних углов, с боков и снизу по расписанию, взрываются
 * облаком искр в цветах приложения; искры тормозят, падают и гаснут. Всё — за ~6 секунд.
 */
private class Fireworks(private val density: Float) {
    private val random = Random(7)
    private val capacity = 2200
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val color = IntArray(capacity)
    /** 0 — искра, 1 — ракета (при гибели взрывается), 2 — след ракеты. */
    private val kind = IntArray(capacity)
    private var count = 0

    private var width = 0f
    private var height = 0f
    private var time = 0f
    private var nextLaunch = 0

    // Вспышки взрывов: центр, возраст, цвет
    private val flashX = FloatArray(16)
    private val flashY = FloatArray(16)
    private val flashAge = FloatArray(16) { 10f }
    private val flashColor = IntArray(16)
    private var flashNext = 0

    /** Цвета приложения, только ярче: бирюза, терракота, золото, мята, коралл, зелень. */
    private val colors = listOf(
        Color(0xFF22A88C), Color(0xFFEE6E3F), Color(0xFFF4B531), Color(0xFF45CBA9), Color(0xFFF7905F), Palette.Primary,
    ).map { it.toArgb() }.toIntArray()

    /** Одна кисть на всё: меняются только цвет и прозрачность — без лишней работы на каждую искру. */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun setColor(c: Int, alpha: Float) {
        paint.color = (c and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }

    /** Время взлёта и откуда: 0 — левый нижний угол, 1 — правый нижний, 2 — снизу по центру, 3 — левый край, 4 — правый край. */
    private val schedule = listOf(
        0.00f to 0, 0.25f to 1, 0.60f to 2, 0.95f to 3, 1.25f to 4, 1.55f to 0, 1.85f to 1,
        2.25f to 2, 2.60f to 0, 2.90f to 1, 3.25f to 3, 3.55f to 4, 3.90f to 2, 4.20f to 0, 4.45f to 1,
    )

    private fun add(px: Float, py: Float, pvx: Float, pvy: Float, l: Float, s: Float, c: Int, k: Int) {
        if (count >= capacity) return
        x[count] = px; y[count] = py; vx[count] = pvx; vy[count] = pvy
        life[count] = l; maxLife[count] = l; size[count] = s; color[count] = c; kind[count] = k
        count++
    }

    private fun launch(from: Int) {
        val d = density
        val (sx, sy) = when (from) {
            0 -> width * 0.06f to height
            1 -> width * 0.94f to height
            2 -> width * (0.4f + 0.2f * random.nextFloat()) to height
            3 -> 0f to height * 0.72f
            else -> width to height * 0.72f
        }
        // Цель — по всему верху и середине экрана, в том числе над карточкой
        val tx = width * (0.12f + 0.76f * random.nextFloat())
        val ty = height * (0.08f + 0.47f * random.nextFloat())
        val t = 0.85f + 0.25f * random.nextFloat()
        val g = GRAVITY * d
        // Начальная скорость, чтобы за время t долететь до цели с учётом тяжести
        val pvx = (tx - sx) / t
        val pvy = (ty - sy - 0.5f * g * t * t) / t
        add(sx, sy, pvx, pvy, t, 2.6f * d, random.nextInt(colors.size), 1)
    }

    private fun explode(px: Float, py: Float, c: Int) {
        val d = density
        val n = 90 + random.nextInt(30)
        val speed = (170f + 100f * random.nextFloat()) * d
        val second = (c + 1 + random.nextInt(colors.size - 1)) % colors.size
        for (i in 0 until n) {
            val a = (2 * PI * i / n).toFloat() + random.nextFloat() * 0.12f
            val v = speed * (0.55f + 0.45f * random.nextFloat())
            add(px, py, cos(a) * v, sin(a) * v, 1.4f + 0.8f * random.nextFloat(), (2.0f + 1.6f * random.nextFloat()) * d, if (i % 4 == 0) second else c, 0)
        }
        val f = flashNext++ % flashX.size
        flashX[f] = px; flashY[f] = py; flashAge[f] = 0f; flashColor[f] = c
    }

    /** Шаг расчёта; false — салют закончился и искр не осталось. */
    fun step(dt: Float): Boolean {
        if (width == 0f) return true
        time += dt
        while (nextLaunch < schedule.size && schedule[nextLaunch].first <= time) launch(schedule[nextLaunch++].second)
        val g = GRAVITY * density
        val drag = exp(-DRAG * dt)
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                if (kind[i] == 1) explode(x[i], y[i], color[i])
                // Удаление перестановкой последней искры на место погасшей
                count--
                x[i] = x[count]; y[i] = y[count]; vx[i] = vx[count]; vy[i] = vy[count]
                life[i] = life[count]; maxLife[i] = maxLife[count]; size[i] = size[count]; color[i] = color[count]; kind[i] = kind[count]
                continue
            }
            if (kind[i] != 1) {
                vx[i] *= drag
                vy[i] = vy[i] * drag + g * 0.55f * dt
            } else {
                vy[i] += g * dt
                // След ракеты — короткоживущие тусклые искорки
                if (random.nextFloat() < 0.7f) add(x[i], y[i], vx[i] * 0.05f, vy[i] * 0.05f, 0.35f, 1.4f * density, color[i], 2)
            }
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            i++
        }
        for (f in flashAge.indices) flashAge[f] += dt
        return nextLaunch < schedule.size || count > 0 || flashAge.any { it < FLASH }
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) = with(scope) {
        width = size.width
        height = size.height
        val canvas = drawContext.canvas.nativeCanvas
        for (f in flashAge.indices) {
            val age = flashAge[f]
            if (age >= FLASH) continue
            val k = 1f - age / FLASH
            setColor(colors[flashColor[f]], 0.32f * k * k)
            canvas.drawCircle(flashX[f], flashY[f], (36f + 60f * (1f - k)) * density, paint)
        }
        // Искры раскладываются по корзинам «цвет × прозрачность × размер» и рисуются пачками drawPoints:
        // около сотни вызовов на кадр вместо двух на каждую искру
        java.util.Arrays.fill(bucketCount, 0)
        for (i in 0 until count) {
            val k = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val alpha = when (kind[i]) {
                1 -> 1f
                2 -> 0.45f * k
                // Искры гаснут к концу и чуть мерцают
                else -> (k * (0.8f + 0.2f * sin(life[i] * 40f + i))).coerceIn(0f, 1f)
            }
            val r = (if (kind[i] == 0) this@Fireworks.size[i] * (0.5f + 0.5f * k) else this@Fireworks.size[i]) / density
            val ai = (alpha * ALPHAS).toInt().coerceIn(0, ALPHAS - 1)
            val si = when {
                r < 1.6f -> 0
                r < 2.6f -> 1
                else -> 2
            }
            val bucket = (color[i] * ALPHAS + ai) * SIZES.size + si
            val n = bucketCount[bucket]
            if (n * 2 + 1 < bucketPoints[bucket].size) {
                bucketPoints[bucket][n * 2] = x[i]
                bucketPoints[bucket][n * 2 + 1] = y[i]
                bucketCount[bucket] = n + 1
            }
        }
        // Сначала мягкие ореолы (крупные полупрозрачные точки), сверху — сами искры
        for (halo in booleanArrayOf(true, false)) {
            for (bucket in bucketCount.indices) {
                val n = bucketCount[bucket]
                if (n == 0) continue
                val si = bucket % SIZES.size
                val ai = (bucket / SIZES.size) % ALPHAS
                val c = bucket / SIZES.size / ALPHAS
                val alpha = (ai + 1f) / ALPHAS
                setColor(colors[c], if (halo) alpha * 0.22f else alpha)
                points.strokeWidth = SIZES[si] * 2f * density * (if (halo) 2.6f else 1f)
                points.color = paint.color
                canvas.drawPoints(bucketPoints[bucket], 0, n * 2, points)
            }
        }
    }

    private val points = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bucketCount = IntArray(6 * ALPHAS * SIZES.size)
    private val bucketPoints = Array(bucketCount.size) { FloatArray(capacity * 2) }

    companion object {
        /** Тяжесть и сопротивление воздуха (в dp/с² и 1/с). */
        const val GRAVITY = 260f
        const val DRAG = 1.6f
        const val FLASH = 0.4f

        /** Ступени прозрачности и радиусы искр (dp) для пачек. */
        const val ALPHAS = 5
        val SIZES = floatArrayOf(1.2f, 2.1f, 3.0f)
    }
}
