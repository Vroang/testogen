package com.testogen.app

import android.app.Application
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    @ColumnInfo(defaultValue = "") val topic: String = "",
    // Шаг 31: синхронизация с Supabase
    val cloudId: String? = null,
    @ColumnInfo(defaultValue = "pending_upload") val syncStatus: String = "pending_upload"
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

    @Query("SELECT * FROM questions WHERE syncStatus = 'pending_upload'")
    suspend fun getPendingUpload(): List<Question>

    @Query("SELECT COUNT(*) FROM questions WHERE syncStatus != 'synced'")
    fun countPendingFlow(): Flow<Int>

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
    val timestamp: Long,
    // Шаг 31: синхронизация с Supabase
    val cloudId: String? = null,
    @ColumnInfo(defaultValue = "pending_upload") val syncStatus: String = "pending_upload"
)

@Dao
interface ReplacementReasonDao {
    @Insert
    suspend fun insert(reason: ReplacementReason)

    @Query("SELECT * FROM replacement_reasons ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<ReplacementReason>

    @Query("SELECT * FROM replacement_reasons WHERE syncStatus = 'pending_upload'")
    suspend fun getPendingUpload(): List<ReplacementReason>

    @Query("SELECT COUNT(*) FROM replacement_reasons WHERE syncStatus != 'synced'")
    fun countPendingFlow(): Flow<Int>

    @Update
    suspend fun update(reason: ReplacementReason)

    @Query("DELETE FROM replacement_reasons")
    suspend fun deleteAll()

    @Query("DELETE FROM replacement_reasons WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// Шаг 25: журнал обращений к ИИ (диагностика каскада моделей).
@Entity(tableName = "ai_log", indices = [Index(value = ["timestamp"])])
data class AiLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val model: String,
    val operation: String,
    val status: String,
    val message: String,
    val requestPreview: String
)

@Dao
interface AiLogDao {
    @Insert
    suspend fun insert(entry: AiLogEntry)

    @Query("SELECT * FROM ai_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 50): List<AiLogEntry>

    @Query("SELECT * FROM ai_log ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestFlow(limit: Int = 50): Flow<List<AiLogEntry>>

    @Query("DELETE FROM ai_log")
    suspend fun clearAll()

    @Query("DELETE FROM ai_log WHERE id NOT IN (SELECT id FROM ai_log ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun trimOlderThan(keep: Int = 200)
}

// Шаг 27: учебник из библиотеки Supabase (локальный кэш метаданных).
// Шаг 30: телефон главный — syncStatus ведёт очередь отправки в облако.
@Entity(tableName = "textbooks")
data class Textbook(
    @PrimaryKey val id: String,
    val name: String,
    val format: String,
    val paragraphCount: Int,
    val uploadedAt: Long,
    val storagePath: String,
    val localCachePath: String? = null,
    // synced / pending_upload / pending_delete
    @ColumnInfo(defaultValue = "synced") val syncStatus: String = "synced",
    @ColumnInfo(defaultValue = "") val userId: String = ""
)

@Dao
interface TextbookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(textbook: Textbook)

    @Query("SELECT * FROM textbooks ORDER BY uploadedAt DESC")
    fun getAll(): Flow<List<Textbook>>

    @Query("SELECT * FROM textbooks ORDER BY uploadedAt DESC")
    suspend fun getAllOnce(): List<Textbook>

    @Query("SELECT * FROM textbooks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Textbook?

    @Query("SELECT * FROM textbooks WHERE syncStatus = 'pending_upload'")
    suspend fun getPendingUpload(): List<Textbook>

    @Query("SELECT COUNT(*) FROM textbooks WHERE syncStatus != 'synced'")
    fun countPendingFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM textbooks")
    suspend fun countAll(): Int

    @Update
    suspend fun update(textbook: Textbook)

    @Query("DELETE FROM textbooks WHERE id = :id")
    suspend fun delete(id: String)
}

// Шаг 30: параграфы учебника — теперь локально (телефон главный).
@Entity(tableName = "paragraphs", indices = [Index(value = ["textbookId"])])
data class Paragraph(
    @PrimaryKey val id: String,
    val textbookId: String,
    val number: Int,
    val title: String,
    val text: String,
    val startPage: Int,
    val endPage: Int
)

@Dao
interface ParagraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(paragraphs: List<Paragraph>)

    @Query("SELECT * FROM paragraphs WHERE textbookId = :textbookId ORDER BY number ASC")
    suspend fun getByTextbook(textbookId: String): List<Paragraph>

    @Query("DELETE FROM paragraphs WHERE textbookId = :textbookId")
    suspend fun deleteByTextbook(textbookId: String)
}

// Шаг 30: очередь удалений, которые ещё не ушли в облако.
// Шаг 31: type = "textbook" / "question" / "reason".
@Entity(tableName = "pending_deletes")
data class PendingDelete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textbookId: String,
    val storagePath: String,
    val userId: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "textbook") val type: String = "textbook",
    @ColumnInfo(defaultValue = "") val cloudId: String = ""
)

@Dao
interface PendingDeleteDao {
    @Insert
    suspend fun insert(entry: PendingDelete)

    @Query("SELECT * FROM pending_deletes ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PendingDelete>

    @Query("DELETE FROM pending_deletes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_deletes")
    fun countFlow(): Flow<Int>
}

@Database(
    entities = [
        Question::class,
        ReplacementReason::class,
        AiLogEntry::class,
        Textbook::class,
        Paragraph::class,
        PendingDelete::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun replacementReasonDao(): ReplacementReasonDao
    abstract fun aiLogDao(): AiLogDao
    abstract fun textbookDao(): TextbookDao
    abstract fun paragraphDao(): ParagraphDao
    abstract fun pendingDeleteDao(): PendingDeleteDao

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

        // Шаг 25: таблица журнала ИИ (банк и причины замен сохраняются).
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "model TEXT NOT NULL, " +
                        "operation TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "message TEXT NOT NULL, " +
                        "requestPreview TEXT NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_log_timestamp ON ai_log(timestamp)")
            }
        }
        // Шаг 27: таблица учебников (локальный кэш библиотеки).
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS textbooks (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "format TEXT NOT NULL, " +
                        "paragraphCount INTEGER NOT NULL, " +
                        "uploadedAt INTEGER NOT NULL, " +
                        "storagePath TEXT NOT NULL, " +
                        "localCachePath TEXT)"
                )
            }
        }
        // Шаг 30: телефон главный — таблицы paragraphs и pending_deletes.
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS paragraphs (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "textbookId TEXT NOT NULL, " +
                        "number INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "startPage INTEGER NOT NULL, " +
                        "endPage INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_paragraphs_textbookId ON paragraphs(textbookId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pending_deletes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "textbookId TEXT NOT NULL, " +
                        "storagePath TEXT NOT NULL, " +
                        "userId TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        // Шаг 30: поля синхронизации у учебников (старые — уже в облаке).
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE textbooks ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'synced'")
                db.execSQL("ALTER TABLE textbooks ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }

        // Шаг 31: синхронизация вопросов и причин замен.
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN cloudId TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'pending_upload'")
                db.execSQL("ALTER TABLE replacement_reasons ADD COLUMN cloudId TEXT")
                db.execSQL("ALTER TABLE replacement_reasons ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'pending_upload'")
                db.execSQL("ALTER TABLE pending_deletes ADD COLUMN type TEXT NOT NULL DEFAULT 'textbook'")
                db.execSQL("ALTER TABLE pending_deletes ADD COLUMN cloudId TEXT NOT NULL DEFAULT ''")
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
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        AiLogger.init(database.aiLogDao())
    }
}

// Шаг 25: запись в журнал ИИ. Ошибки логирования никогда
// не ломают генерацию — глотаются молча.
object AiLogger {
    private var dao: AiLogDao? = null

    fun init(aiLogDao: AiLogDao) {
        dao = aiLogDao
    }

    suspend fun log(
        operation: String,
        status: String,
        model: String,
        message: String,
        requestPreview: String = ""
    ) {
        try {
            val target = dao ?: return
            target.insert(
                AiLogEntry(
                    timestamp = System.currentTimeMillis(),
                    model = model,
                    operation = operation,
                    status = status,
                    message = message.take(200),
                    requestPreview = requestPreview.take(80)
                )
            )
            target.trimOlderThan(200)
        } catch (e: Exception) {
        }
    }
}
