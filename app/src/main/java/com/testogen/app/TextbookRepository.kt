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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.util.UUID

// Схема таблиц Supabase (проверена по живому REST API):
// textbooks(id uuid, user_id uuid, name, format, paragraph_count,
//           storage_path, uploaded_at timestamptz)
// paragraphs(id uuid, textbook_id uuid, number int, text text)

@Serializable
private data class TextbookRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val format: String,
    @SerialName("paragraph_count") val paragraphCount: Int,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("uploaded_at") val uploadedAt: String
)

@Serializable
private data class ParagraphInsert(
    @SerialName("textbook_id") val textbookId: String,
    val number: Int,
    val text: String
)

@Serializable
data class ParagraphRow(
    val number: Int,
    val text: String
)

// Файл больше 50 МБ — mb: размер в МБ (округлён вверх) для диалога.
class OversizeException(val mb: Int) : Exception("Файл слишком большой")

object TextbookRepository {

    private const val MAX_BYTES = 50L * 1024L * 1024L

    suspend fun uploadTextbook(context: Context, uri: Uri): Result<Textbook> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.isSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
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
                val rawText = when (format) {
                    "pdf" -> extractPdfText(bytes)
                    "docx" -> extractDocxText(bytes)
                    else -> String(bytes, Charsets.UTF_8)
                }.replace("\r\n", "\n")
                    .replace('\r', '\n')
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
                if (rawText.isBlank()) {
                    throw Exception("Текст не извлечён (возможно, файл — сканы картинок)")
                }
                val paragraphs = splitParagraphs(rawText)
                if (paragraphs.isEmpty()) {
                    throw Exception("Не найдено параграфов с маркером § (нужны заголовки вида «§ 12»)")
                }

                val user = AuthManager.currentUser()
                    ?: throw Exception("Войдите в аккаунт")
                val bookId = UUID.randomUUID().toString()
                val storagePath = "textbooks/$bookId/$displayName"
                val uploadedAtIso = java.time.Instant.now().toString()

                SupabaseClient.client.storage.from("textbooks").upload(storagePath, bytes)
                SupabaseClient.client.postgrest.from("textbooks").insert(
                    TextbookRow(
                        id = bookId,
                        userId = user.id,
                        name = displayName,
                        format = format,
                        paragraphCount = paragraphs.size,
                        storagePath = storagePath,
                        uploadedAt = uploadedAtIso
                    )
                )
                paragraphs.chunked(100).forEach { batch ->
                    SupabaseClient.client.postgrest.from("paragraphs").insert(
                        batch.map { ParagraphInsert(bookId, it.first, it.second) }
                    )
                }

                val book = Textbook(
                    id = bookId,
                    name = displayName,
                    format = format,
                    paragraphCount = paragraphs.size,
                    uploadedAt = System.currentTimeMillis(),
                    storagePath = storagePath,
                    localCachePath = null
                )
                (context.applicationContext as TestoGenApp).database.textbookDao().insert(book)
                Result.success(book)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    suspend fun deleteTextbook(context: Context, book: Textbook): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.isSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
                runCatching {
                    SupabaseClient.client.postgrest.from("paragraphs").delete {
                        filter { eq("textbook_id", book.id) }
                    }
                }
                SupabaseClient.client.postgrest.from("textbooks").delete {
                    filter { eq("id", book.id) }
                }
                runCatching {
                    SupabaseClient.client.storage.from("textbooks").delete(book.storagePath)
                }
                (context.applicationContext as TestoGenApp).database.textbookDao().delete(book.id)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    suspend fun fetchParagraphText(textbookId: String, from: Int, to: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val rows = SupabaseClient.client.postgrest.from("paragraphs").select {
                    filter {
                        eq("textbook_id", textbookId)
                        gte("number", from)
                        lte("number", to)
                    }
                    order("number", Order.ASCENDING)
                }.decodeList<ParagraphRow>()
                if (rows.isEmpty()) {
                    return@withContext Result.failure(Exception("В этом диапазоне нет параграфов"))
                }
                val joined = rows.joinToString("\n\n") { "§ ${it.number}\n${it.text}" }
                Result.success(joined)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    private fun mapError(e: Exception): Exception {
        val raw = e.message ?: ""
        return if (raw.contains("413") || raw.contains("too large", ignoreCase = true) ||
            raw.contains("Payload", ignoreCase = true)
        ) {
            Exception("Файл слишком большой для загрузки. Максимум — 50 МБ.")
        } else {
            e
        }
    }

    /** Параграфы по маркерам «§ N»; возвращает пары (номер, текст). */
    fun splitParagraphs(text: String): List<Pair<Int, String>> {
        val marker = Regex("§\\s*(\\d+)")
        val matches = marker.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Int, String>>()
        for (i in matches.indices) {
            val number = matches[i].groupValues[1].toIntOrNull() ?: continue
            val start = matches[i].range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val content = text.substring(start, end).trim()
            if (content.isNotBlank()) {
                result.add(number to content)
            }
        }
        return result
    }

    private fun extractPdfText(bytes: ByteArray): String =
        PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
            PDFTextStripper().getText(doc)
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
