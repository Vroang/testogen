package com.testogen.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.testogen.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Новая версия из GitHub Releases. Тег релиза — `v<versionCode>`. */
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkName: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    /** [progress] — 0..1, или null, если размер файла неизвестен. */
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    data class Failed(val release: ReleaseInfo, val message: String) : UpdateState
}

@Serializable
private data class ReleaseAssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("assets") val assets: List<ReleaseAssetDto> = emptyList(),
)

/**
 * Автообновление: раз за запуск спрашивает GitHub о последнем релизе,
 * по согласию качает APK в кэш и открывает системную установку.
 */
class Updater(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checked = false
    private var download: Job? = null

    fun checkOnce() {
        if (checked) return
        checked = true
        scope.launch {
            val release = fetchLatest() ?: return@launch
            if (release.versionCode > BuildConfig.VERSION_CODE) {
                _state.value = UpdateState.Available(release)
            }
        }
    }

    fun dismiss() {
        download?.cancel()
        _state.value = UpdateState.Idle
    }

    fun install(release: ReleaseInfo) {
        if (download?.isActive == true) return
        download = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            try {
                val file = downloadApk(release)
                _state.value = UpdateState.Idle
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(release, describe(e))
            }
        }
    }

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TestogenUpdater")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val release = json.decodeFromString<ReleaseDto>(response.body?.string() ?: return@withContext null)
                val code = Regex("^v(\\d+)$").find(release.tagName.trim())?.groupValues?.get(1)?.toInt()
                    ?: return@withContext null
                val asset = release.assets.firstOrNull { it.browserDownloadUrl.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext null
                ReleaseInfo(
                    versionCode = code,
                    versionName = release.name?.takeIf { it.isNotBlank() } ?: "v$code",
                    apkUrl = asset.browserDownloadUrl,
                    apkName = asset.name.ifBlank { "update.apk" },
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun downloadApk(release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)
        val client = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(release.apkUrl).header("User-Agent", "TestogenUpdater").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(if (response.code == 403 || response.code == 404) "Файл обновления недоступен" else "Ошибка сервера (${response.code})")
            }
            val body = response.body ?: throw IOException("Пустой ответ сервера")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastShown) {
                                lastShown = percent
                                _state.value = UpdateState.Downloading(release, percent / 100f)
                            }
                        }
                    }
                }
            }
        }
        target
    }

    private fun describe(e: Exception): String = when {
        e is SocketTimeoutException -> "Интернет слишком медленный, попробуйте позже"
        e is UnknownHostException -> "Нет подключения к интернету"
        e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true ->
            "На телефоне не хватает места"
        e is IOException && e.message != null -> e.message!!
        else -> "Не получилось скачать обновление"
    }

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/Vroang/testogen/releases/latest"
    }
}
