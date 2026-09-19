package com.testogen.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

// Шаг 30: ТЕЛЕФОН ГЛАВНЫЙ, облако — бэкап.
// Всё сохраняется локально (Room + filesDir) и сразу доступно;
// отправка в Supabase идёт фоном и переживает отсутствие сети
// (syncStatus = pending_upload, очередь pending_deletes).

// Шаг 32: DTO открыт для CloudSyncRepository (detect/apply).
@Serializable
data class TextbookRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val format: String,
    @SerialName("paragraph_count") val paragraphCount: Int,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("uploaded_at") val uploadedAt: String
)

@Serializable
private data class CloudTextbookId(val id: String)

@Serializable
private data class ParagraphInsert(
    @SerialName("textbook_id") val textbookId: String,
    val number: Int,
    val title: String,
    @SerialName("start_page") val startPage: Int,
    @SerialName("end_page") val endPage: Int,
    val text: String
)

@Serializable
data class ParagraphRow(
    val number: Int,
    val title: String = "",
    @SerialName("start_page") val startPage: Int = 1,
    @SerialName("end_page") val endPage: Int = 1,
    val text: String = ""
)

// Файл больше 50 МБ — mb: размер в МБ (округлён вверх) для диалога.
class OversizeException(val mb: Int) : Exception("Файл слишком большой")

// Шаг 29: параграф с названием и диапазоном страниц.
data class ParsedParagraph(
    val number: Int,
    val title: String,
    val text: String,
    val startPage: Int,
    val endPage: Int
)

object TextbookRepository {

    private const val MAX_BYTES = 50L * 1024L * 1024L

    private fun db(context: Context): AppDatabase =
        (context.applicationContext as TestoGenApp).database

    /**
     * Шаг 30 (local-first): файл копируется в filesDir, текст и
     * параграфы сохраняются в Room со статусом pending_upload —
     * учебник доступен СРАЗУ, без интернета. Затем фоном
     * пробуется отправка в Supabase.
     */
    suspend fun uploadTextbook(context: Context, uri: Uri): Result<Textbook> =
        withContext(Dispatchers.IO) {
            try {
                var displayName = "textbook.pdf"
                var size = -1L
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let { displayName = it }
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
                if (size > MAX_BYTES) {
                    throw OversizeException(kotlin.math.ceil(size.toDouble() / (1024.0 * 1024.0)).toInt())
                }
                val format = when (displayName.substringAfterLast('.', "").lowercase()) {
                    "pdf" -> "pdf"
                    "docx" -> "docx"
                    "txt" -> "txt"
                    else -> throw Exception("Поддерживаются только PDF, DOCX и TXT")
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw Exception("Не удалось открыть файл")
                // Шаг 29: PDF читается постранично — даёт номера страниц.
                val pageTexts: List<Pair<Int, String>> = when (format) {
                    "pdf" -> extractPdfPages(bytes)
                    "docx" -> listOf(1 to extractDocxText(bytes))
                    else -> listOf(1 to String(bytes, Charsets.UTF_8))
                }
                val rawText = pageTexts.joinToString("\n") { it.second }
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                if (rawText.isBlank()) {
                    throw Exception("Текст не извлечён (возможно, файл — сканы картинок)")
                }
                val paragraphs = splitParagraphs(pageTexts)
                if (paragraphs.isEmpty()) {
                    throw Exception("Не найдено параграфов с маркером § (нужны заголовки вида «§ 12»)")
                }

                // Локальная копия файла
                val bookId = UUID.randomUUID().toString()
                val dir = File(context.filesDir, "textbooks").apply {
                    if (!exists()) mkdirs()
                }
                val localFile = File(dir, "$bookId.$format")
                localFile.writeBytes(bytes)

                // Всё в Room: доступно сразу, без сети
                val book = Textbook(
                    id = bookId,
                    name = displayName,
                    format = format,
                    paragraphCount = paragraphs.size,
                    uploadedAt = System.currentTimeMillis(),
                    storagePath = "$bookId.$format",
                    localCachePath = localFile.absolutePath,
                    syncStatus = "pending_upload",
                    userId = AuthManager.currentUserAsync()?.id.orEmpty()
                )
                val database = db(context)
                database.textbookDao().insert(book)
                database.paragraphDao().insertAll(
                    paragraphs.map {
                        Paragraph(
                            id = UUID.randomUUID().toString(),
                            textbookId = bookId,
                            number = it.number,
                            title = it.title,
                            text = it.text,
                            startPage = it.startPage,
                            endPage = it.endPage
                        )
                    }
                )

                // Фоновая попытка отправки в облако (может не удаться —
                // статус останется pending_upload)
                runCatching { syncTextbookToCloud(context, book) }
                    .onSuccess {
                        CoroutineScope(Dispatchers.IO).launch {
                            database.textbookDao().update(
                                book.copy(syncStatus = "synced")
                            )
                        }
                    }

                Result.success(book)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    /**
     * Шаг 30 (local-first): локально удаляется сразу; в облако —
     * через очередь pending_deletes (повтор при отсутствии сети).
     */
    suspend fun deleteTextbook(context: Context, book: Textbook): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val database = db(context)
                database.paragraphDao().deleteByTextbook(book.id)
                database.textbookDao().delete(book.id)
                book.localCachePath?.let { path -> File(path).delete() }
                if (book.syncStatus == "synced" || book.userId.isNotBlank()) {
                    database.pendingDeleteDao().insert(
                        PendingDelete(
                            textbookId = book.id,
                            storagePath = book.storagePath,
                            userId = book.userId,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                syncPendingDeletes(context)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Шаг 30: фоновая синхронизация — очередь удалений и все
     * учебники со статусом pending_upload. Вызывается при старте
     * приложения и при открытии списка учебников.
     */
    suspend fun syncAllPending(context: Context) {
        try {
            syncPendingDeletes(context)
            val database = db(context)
            val pending = database.textbookDao().getPendingUpload()
            for (book in pending) {
                runCatching { syncTextbookToCloud(context, book) }
                    .onSuccess {
                        database.textbookDao().update(book.copy(syncStatus = "synced"))
                    }
            }
        } catch (e: Exception) {
            // Без сети просто остаёмся в текущем состоянии.
        }
    }

    private suspend fun syncPendingDeletes(context: Context) {
        if (!AuthManager.requireSignedIn()) return
        val database = db(context)
        val items = database.pendingDeleteDao().getAllOnce()
        for (item in items) {
            // Шаг 31: вопросы и причины удаляет QuestionSyncRepository.
            if (item.type != "textbook") continue
            try {
                SupabaseClient.client.postgrest.from("paragraphs").delete {
                    filter { eq("textbook_id", item.textbookId) }
                }
                SupabaseClient.client.postgrest.from("textbooks").delete {
                    filter { eq("id", item.textbookId) }
                }
                if (item.storagePath.isNotBlank()) {
                    runCatching {
                        SupabaseClient.client.storage.from("textbooks").delete(item.storagePath)
                    }
                }
                database.pendingDeleteDao().deleteById(item.id)
            } catch (e: Exception) {
                // Оставляем в очереди до следующей попытки.
            }
        }
    }

    private suspend fun syncTextbookToCloud(context: Context, book: Textbook) {
        if (!AuthManager.requireSignedIn()) {
            throw Exception("Нет сессии")
        }
        val user = AuthManager.currentUserAsync() ?: throw Exception("Нет сессии")
        val database = db(context)
        val paragraphs = database.paragraphDao().getByTextbook(book.id)
        val localPath = book.localCachePath
            ?: throw Exception("Нет локального файла")
        val bytes = File(localPath).readBytes()

        SupabaseClient.client.storage.from("textbooks").upload(book.storagePath, bytes, true)
        SupabaseClient.client.postgrest.from("textbooks").upsert(
            TextbookRow(
                id = book.id,
                userId = book.userId.ifBlank { user.id },
                name = book.name,
                format = book.format,
                paragraphCount = paragraphs.size,
                storagePath = book.storagePath,
                uploadedAt = java.time.Instant.ofEpochMilli(book.uploadedAt).toString()
            )
        )
        // Перезаливаем параграфы начисто — идемпотентно.
        SupabaseClient.client.postgrest.from("paragraphs").delete {
            filter { eq("textbook_id", book.id) }
        }
        paragraphs.chunked(100).forEach { batch ->
            SupabaseClient.client.postgrest.from("paragraphs").insert(
                batch.map {
                    ParagraphInsert(
                        textbookId = book.id,
                        number = it.number,
                        title = it.title,
                        startPage = it.startPage,
                        endPage = it.endPage,
                        text = it.text
                    )
                }
            )
        }
    }

    /** Сколько учебников ждёт в облаке (для предложения восстановления). */
    suspend fun cloudTextbookCount(): Int = withContext(Dispatchers.IO) {
        try {
            if (!AuthManager.requireSignedIn()) return@withContext 0
            SupabaseClient.client.postgrest.from("textbooks")
                .select { }
                .decodeList<CloudTextbookId>()
                .size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Шаг 30: восстановление облако → телефон (метаданные,
     * параграфы и файлы). Возвращает число восстановленных.
     */
    suspend fun restoreFromCloud(context: Context): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.requireSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
                val database = db(context)
                val cloudRows = SupabaseClient.client.postgrest.from("textbooks")
                    .select { }
                    .decodeList<TextbookRow>()
                var restored = 0
                for (row in cloudRows) {
                    if (restoreCloudTextbook(context, row)) restored++
                }
                Result.success(restored)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    /** Шаг 32: восстановить один учебник (метаданные + параграфы + файл). */
    suspend fun restoreCloudTextbook(context: Context, row: TextbookRow): Boolean {
        val database = db(context)
        if (database.textbookDao().getById(row.id) != null) return false
        val user = AuthManager.currentUserAsync()
        val paragraphs = SupabaseClient.client.postgrest.from("paragraphs").select {
            filter { eq("textbook_id", row.id) }
            order("number", Order.ASCENDING)
        }.decodeList<ParagraphRow>()
        val ext = row.storagePath.substringAfterLast('.', "pdf")
        val localFile = File(
            File(context.filesDir, "textbooks").apply { if (!exists()) mkdirs() },
            "${row.id}.$ext"
        )
        runCatching {
            val bytes = SupabaseClient.client.storage.from("textbooks")
                .downloadAuthenticated(row.storagePath)
            localFile.writeBytes(bytes)
        }
        val uploadedAt = runCatching {
            java.time.Instant.parse(row.uploadedAt).toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
        database.textbookDao().insert(
            Textbook(
                id = row.id,
                name = row.name,
                format = row.format,
                paragraphCount = paragraphs.size,
                uploadedAt = uploadedAt,
                storagePath = row.storagePath,
                localCachePath = localFile.absolutePath,
                syncStatus = "synced",
                userId = row.userId.ifBlank { user?.id.orEmpty() }
            )
        )
        database.paragraphDao().insertAll(
            paragraphs.map {
                Paragraph(
                    id = UUID.randomUUID().toString(),
                    textbookId = row.id,
                    number = it.number,
                    title = it.title,
                    text = it.text,
                    startPage = it.startPage,
                    endPage = it.endPage
                )
            }
        )
        return true
    }

    /**
     * Шаг 29: собрать текст для ИИ (галочки по параграфам или
     * диапазон страниц со срезом внутри параграфа).
     */
    fun buildGenerationText(
        rows: List<ParagraphRow>,
        selectedNumbers: Set<Int>? = null,
        pageFrom: Int? = null,
        pageTo: Int? = null
    ): String {
        val chosen = if (selectedNumbers != null) {
            rows.filter { it.number in selectedNumbers }
        } else {
            val from = pageFrom ?: 1
            val to = pageTo ?: Int.MAX_VALUE
            rows.filter { it.startPage <= to && it.endPage >= from }
        }
        return chosen.joinToString("\n\n") { row ->
            val head = buildString {
                append("§ ").append(row.number)
                if (row.title.isNotBlank()) append(". ").append(row.title)
            }
            val body = if (pageFrom == null || pageTo == null ||
                (row.startPage >= pageFrom && row.endPage <= pageTo)
            ) {
                row.text
            } else {
                val span = (row.endPage - row.startPage + 1).coerceAtLeast(1)
                val startFrac = (pageFrom - row.startPage).coerceAtLeast(0).toFloat() / span
                val endFrac = (pageTo - row.startPage + 1).coerceIn(0, span).toFloat() / span
                val fromIndex = (row.text.length * startFrac).toInt().coerceIn(0, row.text.length)
                val toIndex = (row.text.length * endFrac).toInt().coerceIn(0, row.text.length)
                row.text.substring(fromIndex, toIndex).trim().ifBlank { row.text }
            }
            "$head\n$body"
        }
    }

    private fun mapError(e: Exception): Exception {
        val raw = e.message ?: ""
        return when {
            raw.contains("413") || raw.contains("too large", ignoreCase = true) ||
                raw.contains("Payload", ignoreCase = true) ->
                Exception("Файл слишком большой для загрузки. Максимум — 50 МБ.")
            raw.contains("row-level security", ignoreCase = true) ->
                Exception(
                    "Supabase заблокировал запись (RLS). Выполните SQL-скрипт из инструкции шага 28.4 в SQL Editor и повторите."
                )
            else -> e
        }
    }

    /** Параграфы по маркерам «§ N» с названием и страницами. */
    fun splitParagraphs(pages: List<Pair<Int, String>>): List<ParsedParagraph> {
        if (pages.isEmpty()) return emptyList()
        val combined = StringBuilder()
        val pageOffsets = mutableListOf<Pair<Int, Int>>() // страница → смещение
        pages.forEach { (number, text) ->
            pageOffsets.add(number to combined.length)
            combined.append(text)
            combined.append('\n')
        }
        val full = combined.toString()
        fun pageAt(index: Int): Int {
            var page = pageOffsets.first().first
            for ((number, offset) in pageOffsets) {
                if (offset <= index) page = number else break
            }
            return page
        }
        val marker = Regex("§\\s*(\\d+)")
        val matches = marker.findAll(full).toList()
        if (matches.isEmpty()) return emptyList()
        val result = mutableListOf<ParsedParagraph>()
        for (i in matches.indices) {
            val number = matches[i].groupValues[1].toIntOrNull() ?: continue
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else full.length
            val content = full.substring(start, end).trim()
            if (content.isBlank()) continue
            val startPage = pageAt(matches[i].range.first)
            val endPage = pageAt((end - 1).coerceAtLeast(start))
            result.add(
                ParsedParagraph(
                    number = number,
                    title = extractTitle(content),
                    text = content,
                    startPage = startPage,
                    endPage = endPage
                )
            )
        }
        return result
    }

    /** Название параграфа — первая строка после «§ N», до 100 символов. */
    private fun extractTitle(content: String): String =
        content.lineSequence().firstOrNull { it.isNotBlank() }
            .orEmpty()
            .replace(Regex("^\\d+\\s*[.\\-–—)]?\\s*"), "")
            .trim()
            .take(100)

    /** PDF постранично: список (номер страницы, текст страницы). */
    private fun extractPdfPages(bytes: ByteArray): List<Pair<Int, String>> =
        PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
            val stripper = PDFTextStripper()
            val pages = mutableListOf<Pair<Int, String>>()
            for (page in 1..doc.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                pages.add(page to stripper.getText(doc))
            }
            pages
        }

    private fun extractDocxText(bytes: ByteArray): String {
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().toString(Charsets.UTF_8)
                    return xml
                        .replace("</w:p>", "\n")
                        .replace("<w:br/>", "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                }
                entry = zip.nextEntry
            }
        }
        return ""
    }
}
