package com.testogen.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Тема банка и число вопросов в ней — для списка тем. */
data class TopicCount(val topic: String, val topicKey: String, val count: Int)

/** Строка списка тестов. */
data class TestSummary(
    val id: Long,
    val title: String,
    val variantsCount: Int,
    val questionsPerVariant: Int,
    val updatedAt: Long,
)

/** Итог расходов на нейросеть за период. */
data class AiSpend(val requests: Int, val costUsd: Double)

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: QuestionEntity): Long

    @Insert
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun get(id: Long): QuestionEntity?

    @Query("SELECT COUNT(*) FROM questions")
    fun observeCount(): Flow<Int>

    /** Тексты вопросов от нейросети (свои вопросы мамы не трогаются). */
    @Query("SELECT id, text FROM questions WHERE source != 'manual'")
    suspend fun generatedTexts(): List<IdText>

    @Query("UPDATE questions SET text = :text WHERE id = :id")
    suspend fun setText(id: Long, text: String)

    @Query(
        "SELECT MIN(topic) AS topic, topicKey, COUNT(*) AS count FROM questions " +
            "GROUP BY topicKey ORDER BY topicKey",
    )
    fun observeTopics(): Flow<List<TopicCount>>

    @Query("SELECT * FROM questions WHERE topicKey = :topicKey ORDER BY createdAt DESC")
    fun observeByTopic(topicKey: String): Flow<List<QuestionEntity>>

    /** Текст вопроса и верный ответ — для отсева похожих. */
    @Query(
        "SELECT text, CASE correctIndex WHEN 0 THEN option1 WHEN 1 THEN option2 WHEN 2 THEN option3 ELSE option4 END AS answer " +
            "FROM questions WHERE topicKey = :topicKey ORDER BY createdAt DESC",
    )
    suspend fun textsByTopic(topicKey: String): List<QuestionText>

    @Query("SELECT * FROM questions WHERE jobId = :jobId ORDER BY createdAt")
    fun observeByJob(jobId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT COUNT(*) FROM questions WHERE jobId = :jobId")
    suspend fun countByJob(jobId: Long): Int

    @Query("SELECT COUNT(*) FROM questions WHERE jobId = :jobId AND sectionId = :sectionId")
    suspend fun countByJobSection(jobId: Long, sectionId: Long): Int

    @Query("SELECT COUNT(*) FROM questions WHERE sectionId = :sectionId")
    fun observeCountBySection(sectionId: Long): Flow<Int>

    @Query("SELECT sectionId AS code, COUNT(*) AS count FROM questions WHERE textbookId = :textbookId GROUP BY sectionId")
    fun observeCountsBySection(textbookId: Long): Flow<List<LongCount>>

    @Query("SELECT difficulty AS code, COUNT(*) AS count FROM questions WHERE jobId = :jobId GROUP BY difficulty")
    suspend fun difficultyCountsByJob(jobId: Long): List<CodeCount>

    @Query("SELECT MIN(topic) FROM questions GROUP BY topicKey ORDER BY MAX(updatedAt) DESC LIMIT 30")
    suspend fun recentTopics(): List<String>

    @Query("SELECT id, difficulty, topicKey, text FROM questions WHERE topicKey IN (:topicKeys) AND difficulty IN (:difficulties)")
    suspend fun candidates(topicKeys: List<String>, difficulties: List<Int>): List<com.testogen.app.data.Candidate>

    @Query("SELECT * FROM questions WHERE id IN (SELECT questionId FROM test_items WHERE testId = :testId)")
    fun observeForTest(testId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE topicKey = :topicKey AND id NOT IN (:exclude) ORDER BY RANDOM()")
    suspend fun unusedInTopic(topicKey: String, exclude: List<Long>): List<QuestionEntity>

    /** Тема, по которой большинство вопросов создано из параграфа учебника: id этого параграфа. */
    @Query("SELECT sectionId FROM questions WHERE topicKey = :topicKey AND sectionId IS NOT NULL GROUP BY sectionId ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun mainSection(topicKey: String): Long?
}

data class QuestionText(val text: String, val answer: String)

data class IdText(val id: Long, val text: String)

data class CodeCount(val code: Int, val count: Int)

data class LongCount(val code: Long, val count: Int)

@Dao
interface GenerationJobDao {
    @Insert
    suspend fun insert(job: GenerationJobEntity): Long

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    suspend fun get(id: Long): GenerationJobEntity?

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    fun observe(id: Long): Flow<GenerationJobEntity?>

    @Query("SELECT * FROM generation_jobs WHERE status IN ('queued', 'running') ORDER BY createdAt")
    fun observeActive(): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE status IN ('queued', 'running')")
    suspend fun active(): List<GenerationJobEntity>

    @Query("UPDATE generation_jobs SET status = :status, stage = :stage, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, stage: String, error: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE generation_jobs SET stage = :stage, updatedAt = :now WHERE id = :id")
    suspend fun setStage(id: Long, stage: String, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE generation_jobs SET saved = :saved, dropped = dropped + :dropped, costUsd = costUsd + :cost, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun addProgress(id: Long, saved: Int, dropped: Int, cost: Double, now: Long = System.currentTimeMillis())

    @Query("UPDATE generation_jobs SET total = :total WHERE id = :id")
    suspend fun setTotal(id: Long, total: Int)

    @Query("UPDATE generation_jobs SET saved = :done, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM generation_jobs WHERE kind = 'import' AND status IN ('queued', 'running')")
    fun observeImports(): Flow<List<GenerationJobEntity>>

    @Query("UPDATE generation_jobs SET costUsd = costUsd + :cost WHERE id = :id")
    suspend fun addCost(id: Long, cost: Double)

    @Query("DELETE FROM generation_jobs WHERE status IN ('done', 'failed', 'cancelled') AND updatedAt < :before")
    suspend fun deleteFinishedBefore(before: Long)
}

@Dao
interface TestDao {
    @Insert
    suspend fun insert(test: TestEntity): Long

    @Update
    suspend fun update(test: TestEntity)

    @Query("DELETE FROM tests WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT id, title, variantsCount, questionsPerVariant, updatedAt FROM tests " +
            "ORDER BY updatedAt DESC",
    )
    fun observeSummaries(): Flow<List<TestSummary>>

    @Insert
    suspend fun insertItems(items: List<TestItemEntity>)

    @Query("SELECT * FROM test_items WHERE testId = :testId ORDER BY variant, position")
    suspend fun items(testId: Long): List<TestItemEntity>

    @Query("SELECT * FROM test_items WHERE testId = :testId ORDER BY variant, position")
    fun observeItems(testId: Long): Flow<List<TestItemEntity>>

    @Query("SELECT * FROM tests WHERE id = :id")
    fun observe(id: Long): Flow<TestEntity?>

    @Query("SELECT * FROM tests WHERE id = :id")
    suspend fun get(id: Long): TestEntity?

    @Query("UPDATE test_items SET questionId = :questionId WHERE testId = :testId AND variant = :variant AND position = :position")
    suspend fun setQuestion(testId: Long, variant: Int, position: Int, questionId: Long)

    @Query("DELETE FROM test_items WHERE testId = :testId AND variant = :variant AND position = :position")
    suspend fun deleteItem(testId: Long, variant: Int, position: Int)

    /** Сдвиг номеров в варианте (удаление/возврат вопроса). Через отрицательные числа — чтобы не столкнуться ключами. */
    @Query("UPDATE test_items SET position = -(position + :delta) WHERE testId = :testId AND variant = :variant AND position >= :from")
    suspend fun shiftNegative(testId: Long, variant: Int, from: Int, delta: Int)

    @Query("UPDATE test_items SET position = -position WHERE testId = :testId AND variant = :variant AND position < 0")
    suspend fun flipPositive(testId: Long, variant: Int)

    @Query("UPDATE tests SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tests SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tests SET layoutJson = :json, updatedAt = :now WHERE id = :id")
    suspend fun setLayout(id: Long, json: String?, now: Long = System.currentTimeMillis())
}

@Dao
interface AiRuleDao {
    @Insert
    suspend fun insert(rule: AiRuleEntity): Long

    @Update
    suspend fun update(rule: AiRuleEntity)

    @Query("DELETE FROM ai_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM ai_rules ORDER BY createdAt")
    fun observeAll(): Flow<List<AiRuleEntity>>

    @Query("SELECT * FROM ai_rules WHERE active = 1 ORDER BY createdAt")
    suspend fun active(): List<AiRuleEntity>

    @Query("SELECT * FROM ai_rules ORDER BY createdAt")
    suspend fun all(): List<AiRuleEntity>
}

@Dao
interface ReplacementReasonDao {
    @Insert
    suspend fun insert(reason: ReplacementReasonEntity): Long

    @Query("SELECT * FROM replacement_reasons WHERE processed = 0 ORDER BY createdAt")
    suspend fun unprocessed(): List<ReplacementReasonEntity>

    @Query("SELECT COUNT(*) FROM replacement_reasons WHERE processed = 0")
    fun observeUnprocessedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM replacement_reasons WHERE processed = 0")
    suspend fun unprocessedCount(): Int

    @Query("UPDATE replacement_reasons SET processed = 1 WHERE id IN (:ids)")
    suspend fun markProcessed(ids: List<Long>)
}

@Dao
interface AiRequestDao {
    @Insert
    suspend fun insert(entry: AiRequestEntity): Long

    @Query("SELECT * FROM ai_requests ORDER BY startedAt DESC LIMIT :limit")
    fun observeLatest(limit: Int = 300): Flow<List<AiRequestEntity>>

    @Query("SELECT COUNT(*) AS requests, COALESCE(SUM(costUsd), 0) AS costUsd FROM ai_requests WHERE startedAt >= :since")
    fun observeSpendSince(since: Long): Flow<AiSpend>

    @Query("DELETE FROM ai_requests WHERE startedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(
    entities = [
        QuestionEntity::class,
        TestEntity::class,
        TestItemEntity::class,
        AiRuleEntity::class,
        ReplacementReasonEntity::class,
        AiRequestEntity::class,
        GenerationJobEntity::class,
        TextbookEntity::class,
        TextbookChapterEntity::class,
        TextbookSectionEntity::class,
        SectionBlockEntity::class,
        GlossaryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questions(): QuestionDao
    abstract fun tests(): TestDao
    abstract fun aiRules(): AiRuleDao
    abstract fun replacementReasons(): ReplacementReasonDao
    abstract fun aiRequests(): AiRequestDao
    abstract fun jobs(): GenerationJobDao
    abstract fun textbooks(): TextbookDao

    companion object {
        /** Новое имя: старая база `testogen.db` не переносится, а удаляется (см. LegacyCleanup). */
        const val NAME = "testogen2.db"

        fun create(context: Context): AppDatabase =
            // Этап 6: данные мамы ценны — при изменении структуры писать миграции (addMigrations),
            // схемы лежат в app/schemas. Пересоздание базы с потерей данных запрещено.
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .build()
    }
}
