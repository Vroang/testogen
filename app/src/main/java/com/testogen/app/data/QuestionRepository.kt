package com.testogen.app.data

import androidx.room.withTransaction
import com.testogen.app.ai.QuestionQuality
import com.testogen.app.data.db.AppDatabase
import com.testogen.app.data.db.Difficulty
import com.testogen.app.data.db.QuestionEntity
import com.testogen.app.data.db.QuestionSource

/** Тема для группировки: без лишних пробелов, регистра и «ё». */
fun topicKey(topic: String): String =
    topic.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

/** Аккуратная тема для показа: одиночные пробелы, первая буква заглавная. */
fun cleanTopic(topic: String): String =
    topic.trim().replace(Regex("\\s+"), " ").replaceFirstChar { it.uppercaseChar() }

/** Данные вопроса из формы ручного ввода или от нейросети. */
data class QuestionDraft(
    val text: String,
    val options: List<String>,
    val correctIndex: Int,
    val difficulty: Difficulty,
    val topic: String,
    val grade: Int?,
    val source: QuestionSource,
    val explanation: String? = null,
    val jobId: Long? = null,
    val textbookId: Long? = null,
    val sectionId: Long? = null,
    val quote: String? = null,
    val quoteStart: Int? = null,
    val quoteEnd: Int? = null,
)

class QuestionRepository(private val db: AppDatabase) {

    private val dao = db.questions()

    /**
     * Убирает «согласно тексту учебника», «в тексте» и т. п. из уже сохранённых вопросов нейросети
     * (новые чистятся при разборе ответа). Возвращает, сколько вопросов исправлено.
     */
    suspend fun cleanSourceRefs(): Int {
        var changed = 0
        db.withTransaction {
            for (q in dao.generatedTexts()) {
                val clean = QuestionQuality.stripSourceRefs(q.text)
                if (clean != q.text) {
                    dao.setText(q.id, clean)
                    changed++
                }
            }
        }
        return changed
    }

    fun toEntity(draft: QuestionDraft, now: Long, id: Long = 0, createdAt: Long = now): QuestionEntity {
        require(draft.options.size == 4)
        val topic = cleanTopic(draft.topic)
        return QuestionEntity(
            id = id,
            text = draft.text.trim(),
            option1 = draft.options[0].trim(),
            option2 = draft.options[1].trim(),
            option3 = draft.options[2].trim(),
            option4 = draft.options[3].trim(),
            correctIndex = draft.correctIndex,
            difficulty = draft.difficulty.code,
            topic = topic,
            topicKey = topicKey(topic),
            grade = draft.grade,
            source = draft.source.code,
            explanation = draft.explanation?.trim()?.ifBlank { null },
            dedupKey = QuestionQuality.normalize(draft.text),
            jobId = draft.jobId,
            textbookId = draft.textbookId,
            sectionId = draft.sectionId,
            quote = draft.quote,
            quoteStart = draft.quoteStart,
            quoteEnd = draft.quoteEnd,
            quoteStatus = if (draft.quoteStart != null) 1 else 0,
            createdAt = createdAt,
            updatedAt = now,
        )
    }

    suspend fun insertAll(drafts: List<QuestionDraft>): List<Long> {
        val now = System.currentTimeMillis()
        // Время чуть разное, чтобы порядок в списке совпадал с порядком генерации
        val entities = drafts.mapIndexed { i, d -> toEntity(d, now + i) }
        return db.withTransaction { dao.insertAll(entities) }
    }

    suspend fun save(draft: QuestionDraft, existing: QuestionEntity?): Long {
        val now = System.currentTimeMillis()
        return if (existing == null) {
            dao.insert(toEntity(draft, now))
        } else {
            val entity = toEntity(draft, now, id = existing.id, createdAt = existing.createdAt)
                .copy(
                    textbookId = existing.textbookId,
                    sectionId = existing.sectionId,
                    quote = existing.quote,
                    quoteStart = existing.quoteStart,
                    quoteEnd = existing.quoteEnd,
                    quoteStatus = existing.quoteStatus,
                    jobId = existing.jobId,
                )
            dao.update(entity)
            existing.id
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Вернуть удалённый вопрос (кнопка «Отменить»). */
    suspend fun restore(entity: QuestionEntity) {
        dao.insert(entity)
    }
}
