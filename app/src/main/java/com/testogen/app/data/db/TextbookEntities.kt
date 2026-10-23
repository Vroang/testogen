package com.testogen.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Учебник: копия файла лежит в filesDir/textbooks, разобранная структура — в таблицах ниже.
 * [status]: «importing», «ready», «failed», «scan» (PDF без текста).
 */
@Entity(tableName = "textbooks")
data class TextbookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileName: String,
    /** «pdf», «docx», «txt». */
    val format: String,
    val filePath: String,
    val grade: Int? = null,
    val status: String,
    val pdfPageCount: Int = 0,
    /** Номер страницы книги для каждой страницы PDF через запятую (пусто — нет номера). */
    val pageMap: String = "",
    /** Замечания разбора (расхождения с оглавлением) через перевод строки. */
    val warnings: String = "",
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "textbook_chapters",
    foreignKeys = [ForeignKey(TextbookEntity::class, ["id"], ["textbookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("textbookId")],
)
data class TextbookChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textbookId: Long,
    val ord: Int,
    val label: String,
    val title: String,
)

/** Параграф (kind 0) или прочий раздел учебника (kind 1: вступление, задания к главе, словарь…). */
@Entity(
    tableName = "textbook_sections",
    foreignKeys = [ForeignKey(TextbookEntity::class, ["id"], ["textbookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("textbookId")],
)
data class TextbookSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textbookId: Long,
    val chapterId: Long?,
    val ord: Int,
    val kind: Int,
    val numFrom: Int?,
    val numTo: Int?,
    val title: String,
    /** Ключевые понятия через перевод строки. */
    val keywords: String = "",
    val bookPageStart: Int?,
    val bookPageEnd: Int?,
    val pdfPageStart: Int?,
    val pdfPageEnd: Int?,
    val charCount: Int,
    /** Брать ли раздел для создания вопросов (у «прочих» разделов по умолчанию нет). */
    val useForQuestions: Boolean,
) {
    val label: String
        get() = when {
            numFrom == null -> ""
            numTo != null && numTo != numFrom -> "§ $numFrom–$numTo"
            else -> "§ $numFrom"
        }

    /** «§ 6–7. Государство» или просто название раздела. */
    val fullTitle: String get() = if (label.isEmpty()) title else "$label. $title"
}

/** Кусок текста раздела: абзац, подзаголовок, врезка, задания… ([kind] — BlockKind.code). */
@Entity(
    tableName = "section_blocks",
    foreignKeys = [ForeignKey(TextbookSectionEntity::class, ["id"], ["sectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sectionId"), Index("textbookId")],
)
data class SectionBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textbookId: Long,
    val sectionId: Long,
    val ord: Int,
    val kind: Int,
    val text: String,
    val pdfPage: Int,
    val bookPage: Int?,
)

@Entity(
    tableName = "glossary",
    foreignKeys = [ForeignKey(TextbookEntity::class, ["id"], ["textbookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("textbookId")],
)
data class GlossaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textbookId: Long,
    val term: String,
    val definition: String,
    val pdfPage: Int,
)

/** Строка списка учебников. */
data class TextbookSummary(
    val id: Long,
    val title: String,
    val format: String,
    val grade: Int?,
    val status: String,
    val error: String?,
    val paragraphs: Int,
)

/** Найденное место при поиске по учебнику. */
data class BlockHit(
    val blockId: Long,
    val sectionId: Long,
    val text: String,
    val bookPage: Int?,
    val pdfPage: Int,
    val sectionTitle: String,
    val numFrom: Int?,
    val numTo: Int?,
)

@Dao
interface TextbookDao {
    @Insert
    suspend fun insert(book: TextbookEntity): Long

    @Query("SELECT * FROM textbooks WHERE id = :id")
    suspend fun get(id: Long): TextbookEntity?

    @Query("SELECT * FROM textbooks WHERE id = :id")
    fun observe(id: Long): Flow<TextbookEntity?>

    @Query(
        "SELECT t.id, t.title, t.format, t.grade, t.status, t.error, " +
            // Сдвоенный параграф «§ 2–3» — это два параграфа
            "(SELECT COALESCE(SUM(CASE WHEN s.numTo > s.numFrom THEN s.numTo - s.numFrom + 1 ELSE 1 END), 0) " +
            "FROM textbook_sections s WHERE s.textbookId = t.id AND s.kind = 0) AS paragraphs " +
            "FROM textbooks t ORDER BY t.createdAt DESC",
    )
    fun observeSummaries(): Flow<List<TextbookSummary>>

    @Query("UPDATE textbooks SET status = :status, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE textbooks SET status = 'ready', title = :title, grade = :grade, pdfPageCount = :pages, pageMap = :pageMap, " +
            "warnings = :warnings, error = NULL, updatedAt = :now WHERE id = :id",
    )
    suspend fun setReady(id: Long, title: String, grade: Int?, pages: Int, pageMap: String, warnings: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE textbooks SET title = :title, grade = :grade, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, grade: Int?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM textbooks WHERE id = :id")
    suspend fun delete(id: Long)

    // ---------- структура ----------

    @Insert
    suspend fun insertChapter(chapter: TextbookChapterEntity): Long

    @Insert
    suspend fun insertSection(section: TextbookSectionEntity): Long

    @Insert
    suspend fun insertBlocks(blocks: List<SectionBlockEntity>)

    @Insert
    suspend fun insertGlossary(items: List<GlossaryEntity>)

    @Query("DELETE FROM textbook_chapters WHERE textbookId = :id")
    suspend fun clearChapters(id: Long)

    @Query("DELETE FROM textbook_sections WHERE textbookId = :id")
    suspend fun clearSections(id: Long)

    @Query("DELETE FROM glossary WHERE textbookId = :id")
    suspend fun clearGlossary(id: Long)

    @Query("SELECT * FROM textbook_chapters WHERE textbookId = :id ORDER BY ord")
    fun observeChapters(id: Long): Flow<List<TextbookChapterEntity>>

    @Query("SELECT * FROM textbook_sections WHERE textbookId = :id ORDER BY ord")
    fun observeSections(id: Long): Flow<List<TextbookSectionEntity>>

    @Query("SELECT * FROM textbook_sections WHERE textbookId = :id ORDER BY ord")
    suspend fun sections(id: Long): List<TextbookSectionEntity>

    @Query("SELECT * FROM textbook_sections WHERE id = :id")
    suspend fun section(id: Long): TextbookSectionEntity?

    @Query("SELECT * FROM textbook_sections WHERE id IN (:ids) ORDER BY ord")
    suspend fun sectionsByIds(ids: List<Long>): List<TextbookSectionEntity>

    @Query("SELECT * FROM section_blocks WHERE sectionId = :sectionId ORDER BY ord")
    suspend fun blocks(sectionId: Long): List<SectionBlockEntity>

    @Query("SELECT * FROM glossary WHERE textbookId = :id ORDER BY term")
    suspend fun glossary(id: Long): List<GlossaryEntity>

    @Query("UPDATE textbook_sections SET title = :title WHERE id = :id")
    suspend fun renameSection(id: Long, title: String)

    @Query("UPDATE textbook_sections SET useForQuestions = :use WHERE id = :id")
    suspend fun setUseForQuestions(id: Long, use: Boolean)

    @Query("UPDATE textbook_sections SET kind = :kind, numFrom = :numFrom, numTo = :numTo, useForQuestions = :use WHERE id = :id")
    suspend fun setNumbering(id: Long, kind: Int, numFrom: Int?, numTo: Int?, use: Boolean)

    @Query(
        "SELECT b.id AS blockId, b.sectionId, b.text, b.bookPage, b.pdfPage, s.title AS sectionTitle, s.numFrom, s.numTo " +
            "FROM section_blocks b JOIN textbook_sections s ON s.id = b.sectionId " +
            "WHERE b.textbookId = :textbookId AND b.text LIKE '%' || :query || '%' ORDER BY s.ord, b.ord LIMIT 60",
    )
    suspend fun search(textbookId: Long, query: String): List<BlockHit>

    /** Объединить раздел со следующим: блоки следующего переезжают в конец этого. */
    @Transaction
    suspend fun mergeWithNext(section: TextbookSectionEntity, next: TextbookSectionEntity) {
        val offset = (blocks(section.id).maxOfOrNull { it.ord } ?: -1) + 1
        moveBlocks(next.id, section.id, offset)
        updateRange(
            section.id,
            next.bookPageEnd ?: section.bookPageEnd,
            next.pdfPageEnd ?: section.pdfPageEnd,
            section.charCount + next.charCount,
        )
        deleteSection(next.id)
    }

    /** Разделить раздел: блоки начиная с [fromOrd] уходят в новый раздел сразу после этого. */
    @Transaction
    suspend fun split(section: TextbookSectionEntity, fromOrd: Int, title: String): Long {
        val all = blocks(section.id)
        val moved = all.filter { it.ord >= fromOrd }
        val kept = all.filter { it.ord < fromOrd }
        shiftOrds(section.textbookId, section.ord)
        val newId = insertSection(
            section.copy(
                id = 0,
                ord = section.ord + 1,
                kind = 1,
                numFrom = null,
                numTo = null,
                title = title,
                keywords = "",
                bookPageStart = moved.firstNotNullOfOrNull { it.bookPage },
                pdfPageStart = moved.firstOrNull()?.pdfPage,
                charCount = moved.sumOf { it.text.length },
                useForQuestions = true,
            ),
        )
        moveBlocksFrom(section.id, newId, fromOrd)
        updateRange(section.id, kept.lastOrNull { it.bookPage != null }?.bookPage, kept.lastOrNull()?.pdfPage, kept.sumOf { it.text.length })
        return newId
    }

    @Query("UPDATE section_blocks SET sectionId = :to, ord = ord + :offset WHERE sectionId = :from")
    suspend fun moveBlocks(from: Long, to: Long, offset: Int)

    @Query("UPDATE section_blocks SET sectionId = :to WHERE sectionId = :from AND ord >= :fromOrd")
    suspend fun moveBlocksFrom(from: Long, to: Long, fromOrd: Int)

    @Query("UPDATE textbook_sections SET ord = ord + 1 WHERE textbookId = :textbookId AND ord > :after")
    suspend fun shiftOrds(textbookId: Long, after: Int)

    @Query("UPDATE textbook_sections SET bookPageEnd = :bookEnd, pdfPageEnd = :pdfEnd, charCount = :chars WHERE id = :id")
    suspend fun updateRange(id: Long, bookEnd: Int?, pdfEnd: Int?, chars: Int)

    @Query("DELETE FROM textbook_sections WHERE id = :id")
    suspend fun deleteSection(id: Long)
}
