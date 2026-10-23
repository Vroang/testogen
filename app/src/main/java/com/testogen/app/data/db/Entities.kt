package com.testogen.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Сложность вопроса. В базе хранится [code]. */
enum class Difficulty(val code: Int, val title: String) {
    EASY(1, "Лёгкий"),
    MEDIUM(2, "Средний"),
    HARD(3, "Сложный");

    companion object {
        fun of(code: Int): Difficulty = entries.firstOrNull { it.code == code } ?: MEDIUM
    }
}

/** Откуда взялся вопрос. В базе хранится [code]. */
enum class QuestionSource(val code: String) {
    MANUAL("manual"),
    TOPIC("topic"),
    TEXTBOOK("textbook");

    companion object {
        fun of(code: String): QuestionSource = entries.firstOrNull { it.code == code } ?: MANUAL
    }
}

/**
 * Вопрос банка: всегда ровно 4 варианта, [correctIndex] — 0..3.
 * [topicKey] и [dedupKey] — нормализованные тема и текст (поиск, группировка, отсев дублей).
 * Поля textbookId, sectionId и quote… заполняются у вопросов из учебника (этап 3):
 * цитата-источник и её положение в тексте параграфа.
 */
@Entity(
    tableName = "questions",
    indices = [
        Index("topicKey"),
        Index("dedupKey"),
        Index("createdAt"),
        Index("sectionId"),
        Index("jobId"),
    ],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val option1: String,
    val option2: String,
    val option3: String,
    val option4: String,
    val correctIndex: Int,
    val difficulty: Int,
    val topic: String,
    val topicKey: String,
    val grade: Int? = null,
    val source: String,
    val explanation: String? = null,
    val textbookId: Long? = null,
    val sectionId: Long? = null,
    val quote: String? = null,
    val quoteStart: Int? = null,
    val quoteEnd: Int? = null,
    /** 0 — цитаты нет, 1 — найдена в тексте, 2 — не найдена. */
    @ColumnInfo(defaultValue = "0") val quoteStatus: Int = 0,
    val dedupKey: String,
    /** Задание генерации, которое создало вопрос (null — вручную). */
    val jobId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Задание фоновой генерации. Живёт в базе, поэтому переживает сворачивание,
 * перезапуск приложения и телефона; работу выполняет GenerationWorker.
 */
@Entity(tableName = "generation_jobs", indices = [Index("status"), Index("createdAt")])
data class GenerationJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** «topic», «textbook», «replace». */
    val kind: String,
    /** «queued», «running», «done», «failed», «cancelled». */
    val status: String,
    /** Параметры задания (JSON). */
    val params: String,
    /** Для показа: тема или параграфы. */
    val title: String,
    val total: Int,
    val saved: Int = 0,
    /** Отсеяно: дубли, брак, не прошли проверку. */
    val dropped: Int = 0,
    /** Что происходит сейчас — «Придумываю вопросы…». */
    val stage: String = "",
    val costUsd: Double = 0.0,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Тест (черновик): параметры сборки; сами вопросы — в [TestItemEntity]. */
@Entity(tableName = "tests", indices = [Index("updatedAt")])
data class TestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Темы через перевод строки. */
    val topics: String,
    val variantsCount: Int,
    val questionsPerVariant: Int,
    /** Коды сложностей через запятую, например «1,2,3». */
    val difficulties: String,
    val allowRepeats: Boolean,
    /** Настройки раскладки Word (JSON), этап 5. */
    val layoutJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Вопрос на месте [position] в варианте [variant] теста [testId]. */
@Entity(
    tableName = "test_items",
    primaryKeys = ["testId", "variant", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TestEntity::class,
            parentColumns = ["id"],
            childColumns = ["testId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("questionId")],
)
data class TestItemEntity(
    val testId: Long,
    val variant: Int,
    val position: Int,
    val questionId: Long,
)

/** Правило «Чего не делать» для нейросети. Правила только дополняются; мама может выключить или удалить их вручную. */
@Entity(tableName = "ai_rules")
data class AiRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** «auto» — выведено из причин замен, «manual» — вписано вручную. */
    val origin: String,
    val active: Boolean = true,
    val createdAt: Long,
)

/** Причина, по которой мама заменила вопрос. [processed] — уже учтена в правилах. */
@Entity(tableName = "replacement_reasons")
data class ReplacementReasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionText: String,
    val reason: String,
    val processed: Boolean = false,
    val createdAt: Long,
)

/** Журнал запросов к нейросети: токены и стоимость. */
@Entity(tableName = "ai_requests", indices = [Index("startedAt")])
data class AiRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val durationMs: Long,
    /** «deepseek» или «openrouter». */
    val provider: String,
    val model: String,
    /** Что делали: «topic», «textbook», «replace», «rules»… */
    val operation: String,
    /** «ok» или «error». */
    val status: String,
    val promptTokens: Int = 0,
    /** Токены запроса, взятые из кэша провайдера (дешевле). */
    val cachedTokens: Int = 0,
    val completionTokens: Int = 0,
    val costUsd: Double = 0.0,
    /** Понятный текст ошибки или короткая пометка. */
    val message: String? = null,
)
