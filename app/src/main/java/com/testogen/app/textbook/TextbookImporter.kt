package com.testogen.app.textbook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.testogen.app.ai.GenerationProgress
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.GlossaryEntity
import com.testogen.app.data.db.SectionBlockEntity
import com.testogen.app.data.db.TextbookChapterEntity
import com.testogen.app.data.db.TextbookEntity
import com.testogen.app.data.db.TextbookSectionEntity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ImportJobParams(val textbookId: Long)

/** Учебник не подходит: понятная причина для мамы. */
class ImportException(message: String) : Exception(message)

/**
 * Загрузка учебника: копия файла в память телефона → извлечение текста → разбор структуры
 * → сохранение глав, параграфов, блоков и словаря. Тяжёлая часть идёт в фоне (GenerationWorker).
 */
class TextbookImporter(private val context: Context, private val db: AppDatabase) {

    private val dir get() = File(context.filesDir, "textbooks").apply { mkdirs() }

    /** Копирует выбранный файл к себе (быстро, потоком) и создаёт запись учебника. */
    suspend fun copyIn(uri: Uri): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "учебник"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val format = when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "pdf"
            "docx" -> "docx"
            "txt" -> "txt"
            else -> when (resolver.getType(uri)) {
                "application/pdf" -> "pdf"
                "text/plain" -> "txt"
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                else -> throw ImportException("Подходят только файлы PDF, DOCX и TXT")
            }
        }
        val now = System.currentTimeMillis()
        val target = File(dir, "book-$now.$format")
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it, 256 * 1024) } }
            ?: throw ImportException("Не удалось открыть файл")
        db.textbooks().insert(
            TextbookEntity(
                title = name.substringBeforeLast('.').replace('_', ' ').trim(),
                fileName = name,
                format = format,
                filePath = target.absolutePath,
                status = "importing",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun run(params: ImportJobParams, jobId: Long, progress: GenerationProgress) = withContext(Dispatchers.Default) {
        val book = db.textbooks().get(params.textbookId) ?: return@withContext
        val file = File(book.filePath)
        try {
            progress.stage("Открываю учебник…")
            val pages = when (book.format) {
                "pdf" -> {
                    PDFBoxResourceLoader.init(context)
                    PdfExtractor.extract(file) { done, total ->
                        if (done == 1) db.jobs().setTotal(jobId, total)
                        if (done % 3 == 0 || done == total) {
                            db.jobs().setDone(jobId, done)
                            progress.stage("Читаю страницу $done из $total…")
                        }
                    }
                }
                "docx" -> DocxExtractor.extract(file)
                else -> TxtExtractor.extract(file)
            }
            progress.stage("Разбираю главы и параграфы…")
            val structure = try {
                TextbookAnalyzer.analyze(pages)
            } catch (e: ScannedPdfException) {
                db.textbooks().setStatus(book.id, "scan", e.message)
                throw ImportException(
                    "В этом PDF нет текста — это отсканированные страницы. Распознавание текста пока не делаем: " +
                        "нужен PDF с текстом (например, электронная версия учебника).",
                )
            }
            if (structure.paragraphs.isEmpty()) {
                throw ImportException("Не нашёл в учебнике параграфов со знаком «§». Проверьте, тот ли это файл.")
            }
            progress.stage("Сохраняю…")
            save(book, structure, pages.size)
            db.jobs().setDone(jobId, pages.size.coerceAtLeast(1))
        } catch (e: ImportException) {
            if (db.textbooks().get(book.id)?.status != "scan") db.textbooks().setStatus(book.id, "failed", e.message)
            throw e
        } catch (e: Exception) {
            db.textbooks().setStatus(book.id, "failed", "Не удалось разобрать файл: ${e.message ?: e.javaClass.simpleName}")
            throw ImportException("Не удалось разобрать файл: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun save(book: TextbookEntity, s: BookStructure, pageCount: Int) {
        val dao = db.textbooks()
        db.withTransaction {
            dao.clearSections(book.id)
            dao.clearChapters(book.id)
            dao.clearGlossary(book.id)
            val chapterIds = s.chapters.mapIndexed { i, c ->
                dao.insertChapter(TextbookChapterEntity(textbookId = book.id, ord = i, label = c.label, title = c.title))
            }
            s.sections.forEachIndexed { ord, sec ->
                val paragraph = sec.kind == SectionKind.PARAGRAPH
                val id = dao.insertSection(
                    TextbookSectionEntity(
                        textbookId = book.id,
                        chapterId = sec.chapterIndex?.let { chapterIds.getOrNull(it) },
                        ord = ord,
                        kind = if (paragraph) 0 else 1,
                        numFrom = sec.numFrom,
                        numTo = sec.numTo,
                        title = sec.title,
                        keywords = sec.keywords.joinToString("\n"),
                        bookPageStart = sec.bookPageStart,
                        bookPageEnd = sec.bookPageEnd,
                        pdfPageStart = sec.pdfPageStart,
                        pdfPageEnd = sec.pdfPageEnd,
                        charCount = sec.blocks.sumOf { it.text.length },
                        useForQuestions = paragraph,
                    ),
                )
                dao.insertBlocks(
                    sec.blocks.mapIndexed { i, b ->
                        SectionBlockEntity(
                            textbookId = book.id,
                            sectionId = id,
                            ord = i,
                            kind = b.kind.code,
                            text = b.text,
                            pdfPage = b.pdfPage,
                            bookPage = b.bookPage,
                        )
                    },
                )
            }
            dao.insertGlossary(s.glossary.map { GlossaryEntity(textbookId = book.id, term = it.term, definition = it.definition, pdfPage = it.pdfPage) })
            val title = if (book.title.length < 4 || book.title.startsWith("book")) s.title ?: book.title else book.title
            dao.setReady(
                book.id,
                title = title,
                grade = s.grade,
                pages = pageCount,
                pageMap = s.bookPages.joinToString(",") { it?.toString().orEmpty() },
                warnings = s.warnings.joinToString("\n"),
            )
        }
    }

    /** Удалить учебник вместе с файлом. Вопросы, созданные по нему, остаются в банке. */
    suspend fun delete(id: Long) {
        val book = db.textbooks().get(id) ?: return
        db.textbooks().delete(id)
        withContext(Dispatchers.IO) { File(book.filePath).delete() }
    }
}
