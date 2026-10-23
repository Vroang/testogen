package com.testogen.app.tutorial

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface TutorialState {
    data object Idle : TutorialState
    /** Ищу ролик среди файлов релизов на GitHub. */
    data object Checking : TutorialState
    /** [progress] — 0..1 или null, если размер неизвестен. */
    data class Downloading(val progress: Float?, val doneBytes: Long, val totalBytes: Long) : TutorialState
    data class Failed(val message: String) : TutorialState
    /** Ролик ещё не выложен ни к одному релизу. */
    data object NotPublished : TutorialState
}

@Serializable
private data class AssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
)

@Serializable
private data class ReleaseDto(@SerialName("assets") val assets: List<AssetDto> = emptyList())

/**
 * Обучающий ролик: ищется среди файлов релизов на GitHub (первый .mp4, начиная с новых), скачивается
 * один раз в папку приложения и открывается в системном видеоплеере. Скачанный ролик открывается
 * и без интернета.
 */
class TutorialVideo(private val context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<TutorialState>(TutorialState.Idle)
    val state: StateFlow<TutorialState> = _state.asStateFlow()
    private var job: Job? = null
    private var call: Call? = null
    /** Номер попытки: отменённая загрузка, пока доходит до проверки отмены, не трогает состояние новой. */
    @Volatile private var attempt = 0
    private val dir get() = File(context.filesDir, "tutorial").apply { mkdirs() }

    /** Только для проверки разработчиком (debug-сборка): качать ролик с этого адреса, а не с GitHub. */
    var overrideUrl: String? = null

    /** Уже скачанный ролик. */
    fun saved(): File? = dir.listFiles { f -> f.name.endsWith(".mp4", ignoreCase = true) }?.maxByOrNull { it.lastModified() }

    /** «Смотреть»: открыть скачанный ролик или скачать его и открыть. */
    fun watch() {
        saved()?.let { open(it); return }
        if (job?.isActive == true) return
        val my = ++attempt
        fun set(state: TutorialState) {
            if (my == attempt) _state.value = state
        }
        job = scope.launch {
            set(TutorialState.Checking)
            try {
                val url = overrideUrl ?: findVideoUrl()
                if (url == null) {
                    set(TutorialState.NotPublished)
                    return@launch
                }
                val file = download(url, my, ::set)
                set(TutorialState.Idle)
                if (my == attempt) open(file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                set(TutorialState.Failed(describe(e)))
            }
        }
    }

    /** «Отмена»: оборвать запрос сразу (не дожидаясь следующей порции данных); недокачанный файл удаляется. */
    fun cancel() {
        attempt++
        call?.cancel()
        job?.cancel()
        _state.value = TutorialState.Idle
    }

    fun dismiss() {
        if (job?.isActive != true) _state.value = TutorialState.Idle
    }

    private suspend fun findVideoUrl(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TestogenTutorial")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 429) throw IOException("GitHub сейчас не отвечает. Попробуйте через час.")
            if (!response.isSuccessful) throw IOException("Не удалось связаться с GitHub (ошибка ${response.code})")
            val releases = json.decodeFromString<List<ReleaseDto>>(response.body?.string().orEmpty().ifEmpty { "[]" })
            releases.firstNotNullOfOrNull { r -> r.assets.firstOrNull { it.name.endsWith(".mp4", ignoreCase = true) && it.url.isNotBlank() }?.url }
        }
    }

    private suspend fun download(url: String, my: Int, set: (TutorialState) -> Unit): File = withContext(Dispatchers.IO) {
        val name = url.substringAfterLast('/').substringBefore('?').takeIf { it.endsWith(".mp4", ignoreCase = true) } ?: "tutorial.mp4"
        // У каждой попытки свой временный файл: отменённая не удалит файл новой
        val part = File(dir, "$name.$my$PART")
        val target = File(dir, name)
        val client = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(url).header("User-Agent", "TestogenTutorial").build()
        try {
            client.newCall(request).also { call = it }.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(if (response.code == 403 || response.code == 404) "Файл ролика недоступен" else "Ошибка сервера (${response.code})")
                }
                val body = response.body ?: throw IOException("Пустой ответ сервера")
                val total = body.contentLength()
                set(TutorialState.Downloading(if (total > 0) 0f else null, 0, total))
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var shown = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            done += read
                            val step = if (total > 0) (done * 200 / total).toInt() else (done / (256 * 1024)).toInt()
                            if (step != shown) {
                                shown = step
                                set(TutorialState.Downloading(if (total > 0) done.toFloat() / total else null, done, total))
                            }
                        }
                        if (total > 0 && done < total) throw IOException("Загрузка прервалась")
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить ролик")
            target
        } finally {
            part.delete()
        }
    }

    private fun open(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _state.value = TutorialState.Failed("На телефоне нет программы для просмотра видео")
        }
    }

    private fun describe(e: Exception): String = when {
        e is UnknownHostException || e is java.net.ConnectException -> "Нет подключения к интернету. Проверьте Wi-Fi или мобильный интернет и нажмите «Повторить»."
        e is SocketTimeoutException -> "Интернет слишком медленный. Попробуйте ещё раз чуть позже."
        e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true -> "На телефоне не хватает места для ролика"
        // Свои понятные сообщения — как есть; английские ошибки сети — общими словами
        e is IOException && e.message?.any { it in 'А'..'я' } == true -> e.message!!
        e is IOException -> "Связь прервалась. Проверьте интернет и нажмите «Повторить»."
        else -> "Не получилось загрузить ролик"
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Vroang/testogen/releases?per_page=20"
        const val PART = ".part"
    }
}
