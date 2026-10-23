package com.testogen.app.ui.textbooks

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.testogen.app.AppContainer
import com.testogen.app.ui.LocalAppContainer
import com.testogen.app.ui.components.BackHeader
import com.testogen.app.ui.components.SecondaryButton
import com.testogen.app.ui.components.SkeletonCard
import com.testogen.app.ui.theme.Palette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Оригинальная страница PDF, отрисованная системным PdfRenderer (без сторонних библиотек). */
class PdfPageViewModel(private val c: AppContainer, private val textbookId: Long, startPage: Int, private val widthPx: Int) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private var renderer: PdfRenderer? = null
    private var pageMap: List<Int?> = emptyList()

    var page by mutableIntStateOf(startPage)
        private set
    var pageCount by mutableStateOf(0)
        private set
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val bookPage: Int? get() = pageMap.getOrNull(page)

    init {
        viewModelScope.launch {
            val book = c.database.textbooks().get(textbookId) ?: return@launch
            pageMap = book.pageMap.split(',').map { it.toIntOrNull() }
            try {
                withContext(io) {
                    renderer = PdfRenderer(ParcelFileDescriptor.open(File(book.filePath), ParcelFileDescriptor.MODE_READ_ONLY))
                }
                pageCount = renderer?.pageCount ?: 0
                render()
            } catch (e: Exception) {
                error = "Не удалось открыть PDF: ${e.message}"
            }
        }
    }

    fun go(delta: Int) {
        val next = (page + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (next == page) return
        page = next
        viewModelScope.launch { render() }
    }

    private suspend fun render() {
        val target = page
        val bmp = withContext(io) {
            val r = renderer ?: return@withContext null
            r.openPage(target).use { p ->
                val width = widthPx.coerceIn(800, 2000)
                val height = (width.toFloat() * p.height / p.width).toInt()
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                b
            }
        }
        if (target == page) bitmap = bmp
    }

    override fun onCleared() {
        // Область ViewModel уже отменена — закрываем сразу (это быстро)
        runCatching { renderer?.close() }
        renderer = null
    }
}

@Composable
fun PdfPageScreen(textbookId: Long, pdfPage: Int, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.toInt() * 2
    val vm: PdfPageViewModel = viewModel(key = "pdf-$textbookId-$pdfPage") { PdfPageViewModel(container, textbookId, pdfPage, widthPx) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        BackHeader(vm.bookPage?.let { "Страница $it" } ?: "Страница ${vm.page + 1}", onBack)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.2f
                        offset = Offset.Zero
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(vm.bitmap, animationSpec = tween(220), label = "pdfPage") { bmp ->
                when {
                    vm.error != null -> Text(vm.error.orEmpty(), color = Palette.Error)
                    bmp == null -> SkeletonCard(Modifier.fillMaxSize())
                    else -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Страница учебника",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton("Назад", { vm.go(-1); scale = 1f; offset = Offset.Zero }, Modifier.weight(1f), enabled = vm.page > 0)
            Text("${vm.page + 1} / ${vm.pageCount}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
            SecondaryButton("Дальше", { vm.go(1); scale = 1f; offset = Offset.Zero }, Modifier.weight(1f), enabled = vm.page < vm.pageCount - 1)
        }
    }
}
