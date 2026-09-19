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
// paragraphs(id uuid, textbook_id uuid, number int, text text,
//            title text, start_page int, end_page int)
// (title/start_page/end_page добавлены в шаге 29 — SQL у пользователя)

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

    suspend fun uploadTextbook(context: Context, uri: Uri): Result<Textbook> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.requireSignedIn()) {
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
                // Шаг 29: PDF читается постранично — это даёт номера
                // страниц для параграфов. Для DOCX/TXT страниц нет.
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

                val user = AuthManager.currentUserAsync()
                    ?: throw Exception("Не удалось определить пользователя — войдите заново")
                val bookId = UUID.randomUUID().toString()
                // Шаг 28.3: ключ в бакете — только ASCII «<uuid>.<ext>».
                val storagePath = "$bookId.$format"
                val uploadedAtIso = java.time.Instant.now().toString()

                SupabaseClient.client.storage.from("textbooks").upload(storagePath, bytes, true)
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
                        batch.map {
                            ParagraphInsert(
                                textbookId = bookId,
                                number = it.number,
                                title = it.title,
                                startPage = it.startPage,
                                endPage = it.endPage,
                                text = it.text
                            )
                        }
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
                if (!AuthManager.requireSignedIn()) {
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

    /** Все параграфы учебника по возрастанию номеров. */
    suspend fun fetchParagraphs(textbookId: String): Result<List<ParagraphRow>> =
        withContext(Dispatchers.IO) {
            try {
                if (!AuthManager.requireSignedIn()) {
                    throw Exception("Войдите в аккаунт")
                }
                val rows = SupabaseClient.client.postgrest.from("paragraphs").select {
                    filter { eq("textbook_id", textbookId) }
                    order("number", Order.ASCENDING)
                }.decodeList<ParagraphRow>()
                Result.success(rows)
            } catch (e: Exception) {
                Result.failure(mapError(e))
            }
        }

    /**
     * Шаг 29: собрать текст для ИИ.
     * selectedNumbers != null — режим «по параграфам» (берём их целиком);
     * иначе — режим «по страницам»: параграфы, пересекающие диапазон,
     * внутри параграфа текст срезается пропорционально страницам.
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
                // Приближённый срез: параграф длиннее диапазона страниц.
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
