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

    @Query(
        "SELECT MIN(topic) AS topic, topicKey, COUNT(*) AS count FROM questions " +
            "GROUP BY topicKey ORDER BY topicKey",
    )
    fun observeTopics(): Flow<List<TopicCount>>

    @Query("SELECT * FROM questions WHERE topicKey = :topicKey ORDER BY createdAt DESC")
    fun observeByTopic(topicKey: String): Flow<List<QuestionEntity>>

    @Query("SELECT dedupKey FROM questions WHERE topicKey = :topicKey")
    suspend fun dedupKeysByTopic(topicKey: String): List<String>
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
}

@Dao
interface ReplacementReasonDao {
    @Insert
    suspend fun insert(reason: ReplacementReasonEntity): Long

    @Query("SELECT * FROM replacement_reasons WHERE processed = 0 ORDER BY createdAt")
    suspend fun unprocessed(): List<ReplacementReasonEntity>

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
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questions(): QuestionDao
    abstract fun tests(): TestDao
    abstract fun aiRules(): AiRuleDao
    abstract fun replacementReasons(): ReplacementReasonDao
    abstract fun aiRequests(): AiRequestDao

    companion object {
        /** Новое имя: старая база `testogen.db` не переносится, а удаляется (см. LegacyCleanup). */
        const val NAME = "testogen2.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                // До первого релиза новой версии структура базы ещё меняется — данные не ценны.
                // Перед релизом (этап 6) убрать и дальше писать миграции.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
