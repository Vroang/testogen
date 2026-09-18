package com.testogen.app

import android.app.Application
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "questions",
    indices = [Index(value = ["topic"]), Index(value = ["createdAt"])]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctIndex: Int,
    val difficulty: String,
    val tricky: Boolean,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "manual") val source: String = "manual",
    @ColumnInfo(defaultValue = "") val topic: String = ""
)

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: Question): Long

    @Query("SELECT COUNT(*) FROM questions")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Question>>

    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Question>

    @Delete
    suspend fun delete(question: Question)

    @Update
    suspend fun update(question: Question)

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Question?

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}

// Шаг 24: фильтр ИИ-дублей. Сравнение по «нормализованному» тексту —
// нижний регистр, без пробелов, без хвостовых знаков ? ! .
// В банк сохраняется исходный текст вопроса БЕЗ изменений.
fun normalizeQuestionText(raw: String): String =
    raw.lowercase().filter { !it.isWhitespace() }.trimEnd('?', '!', '.')

// Возвращает: сколько сохранено, сколько отброшено как дубли.
suspend fun QuestionDao.insertUnique(questions: List<Question>): Pair<Int, Int> {
    val known = getAllOnce().mapTo(mutableSetOf()) { normalizeQuestionText(it.text) }
    val fresh = mutableListOf<Question>()
    var duplicates = 0
    for (question in questions) {
        val key = normalizeQuestionText(question.text)
        if (key.isEmpty() || !known.add(key)) {
            duplicates++
        } else {
            fresh.add(question)
        }
    }
    fresh.forEach { insert(it) }
    return fresh.size to duplicates
}

@Entity(tableName = "replacement_reasons")
data class ReplacementReason(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val replacedQuestionText: String,
    val reason: String,
    val timestamp: Long
)

@Dao
interface ReplacementReasonDao {
    @Insert
    suspend fun insert(reason: ReplacementReason)

    @Query("SELECT * FROM replacement_reasons ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<ReplacementReason>

    @Query("DELETE FROM replacement_reasons")
    suspend fun deleteAll()
}

@Database(
    entities = [Question::class, ReplacementReason::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun replacementReasonDao(): ReplacementReasonDao

    companion object {
        // Шаг 4: добавляется таблица причин замен; банк вопросов сохраняется.
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS replacement_reasons (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "replacedQuestionText TEXT NOT NULL, " +
                        "reason TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
            }
        }

        // Шаг 9: источник вопроса (manual / ai / pdf); старые вопросы — manual.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
            }
        }

        // Шаг 17: тема вопроса; старые вопросы — без темы.
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN topic TEXT NOT NULL DEFAULT ''")
            }
        }

        // Шаг 18: индексы для фильтрации по теме и сортировки по дате.
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_topic ON questions(topic)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_createdAt ON questions(createdAt)")
            }
        }
    }
}

class TestoGenApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "testogen.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
