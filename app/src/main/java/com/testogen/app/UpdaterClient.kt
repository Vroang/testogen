package com.testogen.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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

object UpdaterClient {
    private const val LATEST_URL = "https://api.github.com/repos/Vroang/testogen/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    // Возвращает null, если запрос не удался или тег не в формате v<число>.
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

    // Скачивание через системный DownloadManager; по завершении —
    // системный установщик APK.
    fun downloadAndInstall(context: Context, release: ReleaseInfo) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
            setTitle(release.apkName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, release.apkName)
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
        }
        val downloadId = dm.enqueue(request)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val doneId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (doneId != downloadId) return
                context.unregisterReceiver(this)
                val uri = dm.getUriForDownloadedFile(downloadId)
                if (uri == null) {
                    Toast.makeText(context, "Не удалось скачать обновление", Toast.LENGTH_SHORT).show()
                    return
                }
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(install)
            }
        }
        context.applicationContext.registerReceiver(
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }
}
