package com.testogen.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

// Шаг 24.2: самописная проверка обновлений через GitHub API.
// Схема тегов: v<versionCode> (v33, v34, ...), «человеческое» имя
// релиза — в поле name. Сравнение строго численное по versionCode.
data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkName: String
)

@Serializable
private data class ReleaseAssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("assets") val assets: List<ReleaseAssetDto> = emptyList()
)

// Шаг 26.1: понятная ошибка скачивания (показывается пользователю).
private class DownloadException(message: String) : Exception(message)

object UpdaterClient {
    private const val LATEST_URL = "https://api.github.com/repos/Vroang/testogen/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    // Возвращает null, если запрос не упал или тег не в формате v<число>.
    suspend fun checkLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TestogenUpdater")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val release = json.decodeFromString<ReleaseDto>(body)
                val match = Regex("^v(\\d+)$").find(release.tagName.trim())
                    ?: return@withContext null
                val asset = release.assets.firstOrNull {
                    it.browserDownloadUrl.endsWith(".apk", ignoreCase = true)
                } ?: return@withContext null
                ReleaseInfo(
                    versionCode = match.groupValues[1].toInt(),
                    versionName = release.name?.takeIf { it.isNotBlank() } ?: "v${match.groupValues[1]}",
                    apkUrl = asset.browserDownloadUrl,
                    apkName = asset.name
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // Шаг 26.1: своё скачивание через OkHttp в кэш приложения —
    // системный DownloadManager падал молча, без уведомлений.
    // Теперь есть диалог с процентами и явные сообщения об ошибках.
    fun downloadAndInstall(context: Context, release: ReleaseInfo) {
        val progressDialog = AlertDialog.Builder(context)
            .setTitle("Обновление ${release.versionName}")
            .setMessage("Скачивание…")
            .setCancelable(false)
            .create()
        progressDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "downloads").apply {
                        if (!exists()) mkdirs()
                    }
                    dir.listFiles()?.forEach { it.delete() }
                    File(dir, release.apkName.ifBlank { "update.apk" })
                }
                val client = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                val request = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", "TestogenUpdater/1.0")
                    .build()
                withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw DownloadException(
                                if (response.code == 403 || response.code == 404) {
                                    "Файл недоступен"
                                } else {
                                    "Ошибка сервера (HTTP ${response.code})"
                                }
                            )
                        }
                        val body = response.body
                            ?: throw DownloadException("Пустой ответ сервера")
                        val total = body.contentLength()
                        val input = body.byteStream()
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        withContext(Dispatchers.Main) {
                                            progressDialog.setMessage("Скачивание… $percent%")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                progressDialog.dismiss()
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "com.testogen.app.fileprovider",
                    target
                )
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(install)
            } catch (e: Exception) {
                if (progressDialog.isShowing) progressDialog.dismiss()
                val message = when {
                    e is DownloadException -> e.message ?: "Ошибка скачивания"
                    e is java.net.SocketTimeoutException -> "Превышено время ожидания"
                    e is java.net.UnknownHostException || e is java.net.ConnectException ->
                        "Нет соединения"
                    e is java.io.IOException &&
                        (e.message?.contains("ENOSPC") == true ||
                            e.message?.contains("No space") == true) ->
                        "Недостаточно места на устройстве"
                    else -> "Ошибка скачивания: ${e.message ?: "неизвестная ошибка"}"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
