package com.testogen.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val apiKey: String = "",
    val model: String = SettingsRepository.DEFAULT_MODEL,
    val cascadeFree: String = SettingsRepository.DEFAULT_CASCADE_FREE,
    val cascadePaid: String = SettingsRepository.DEFAULT_CASCADE_PAID,
    val lastWorking: String = "",
    val cascadeFreeCustom: Boolean = false,
    val aiSystem: String = SettingsRepository.DEFAULT_AI_SYSTEM_TEXT,
    val aiAvoid: String = "",
    val aiSubjects: String = "",
    val replacementsSinceSqueeze: Int = 0
) {
    /** Порядок каскада: основная → бесплатные → платные → openrouter/auto как последний резерв. */
    fun cascadeOrder(): List<String> {
        val free = cascadeFree.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val paid = cascadePaid.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return ((listOf(model) + free + paid).filter { it.isNotEmpty() }.distinct() + SettingsRepository.AUTO_MODEL)
            .distinct()
    }
}

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_MODEL = stringPreferencesKey("model")
        private val KEY_CASCADE_FREE = stringPreferencesKey("cascade_free")
        private val KEY_CASCADE_PAID = stringPreferencesKey("cascade_paid")
        private val KEY_LAST_WORKING = stringPreferencesKey("last_working_model")
        private val KEY_CASCADE_FREE_CUSTOM = booleanPreferencesKey("cascade_free_custom")
        private val KEY_AI_SYSTEM = stringPreferencesKey("ai_system_text")
        private val KEY_AI_AVOID = stringPreferencesKey("ai_avoid_text")
        private val KEY_AI_SUBJECTS = stringPreferencesKey("ai_subjects_text")
        private val KEY_REPLACEMENTS_SINCE = intPreferencesKey("replacements_since_squeeze")
        // Шаг 32: обратная синхронизация с подтверждением
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_timestamp")
        private val KEY_PENDING_HASH = stringPreferencesKey("pending_changes_hash")

        const val AUTO_MODEL = "openrouter/auto"
        const val DEFAULT_MODEL = AUTO_MODEL

        // Бесплатный каскад теперь заполняется динамически из /models (Шаг 10.1).
        const val DEFAULT_CASCADE_FREE = ""
        const val DEFAULT_CASCADE_PAID =
            "deepseek/deepseek-v4-flash, deepseek/deepseek-v4-flash-0423"

        const val DEFAULT_AI_SYSTEM_TEXT = """Ты — генератор школьных тестов по истории и обществознанию. Твоя задача — составить вопросы с вариантами ответов.

ПРАВИЛА:
- Ровно 4 варианта ответа на каждый вопрос.
- Только один правильный вариант.
- Вопросы чёткие, без двусмысленности.
- Правильный ответ НЕ всегда первый — распределяй позицию (А/Б/В/Г) случайно.
- Никаких вопросов «все перечисленное верно» / «ничего из перечисленного».
- Учитывай класс (если указан), не выходи за программу.
- Никакой воды, только факты, даты, термины, персоналии.

ФОРМАТ ОТВЕТА — строго JSON-массив, без markdown-обёрток:
[
  {"text": "текст вопроса",
   "options": ["вариант А", "вариант Б", "вариант В", "вариант Г"],
   "correct": 0}
]
где correct — индекс правильного варианта (0=А, 1=Б, 2=В, 3=Г)."""
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[KEY_API_KEY] ?: "",
            model = p[KEY_MODEL] ?: DEFAULT_MODEL,
            cascadeFree = p[KEY_CASCADE_FREE] ?: DEFAULT_CASCADE_FREE,
            cascadePaid = p[KEY_CASCADE_PAID] ?: DEFAULT_CASCADE_PAID,
            lastWorking = p[KEY_LAST_WORKING] ?: "",
            cascadeFreeCustom = p[KEY_CASCADE_FREE_CUSTOM] ?: false,
            aiSystem = p[KEY_AI_SYSTEM] ?: DEFAULT_AI_SYSTEM_TEXT,
            aiAvoid = p[KEY_AI_AVOID] ?: "",
            aiSubjects = p[KEY_AI_SUBJECTS] ?: "",
            replacementsSinceSqueeze = p[KEY_REPLACEMENTS_SINCE] ?: 0
        )
    }

    suspend fun incrementReplacementsSinceSqueeze() {
        context.dataStore.edit { p ->
            p[KEY_REPLACEMENTS_SINCE] = (p[KEY_REPLACEMENTS_SINCE] ?: 0) + 1
        }
    }

    suspend fun resetReplacementsSinceSqueeze() {
        context.dataStore.edit { p ->
            p[KEY_REPLACEMENTS_SINCE] = 0
        }
    }

    suspend fun saveLastWorking(v: String) {
        context.dataStore.edit { it[KEY_LAST_WORKING] = v }
    }

    suspend fun saveCascadeFreeCustom(v: Boolean) {
        context.dataStore.edit { it[KEY_CASCADE_FREE_CUSTOM] = v }
    }

    suspend fun saveAiSystem(v: String) {
        context.dataStore.edit { it[KEY_AI_SYSTEM] = v }
    }

    suspend fun saveAiAvoid(v: String) {
        context.dataStore.edit { it[KEY_AI_AVOID] = v }
    }

    suspend fun saveAiSubjects(v: String) {
        context.dataStore.edit { it[KEY_AI_SUBJECTS] = v }
    }

    suspend fun saveApiKey(v: String) {
        context.dataStore.edit { it[KEY_API_KEY] = v }
    }

    suspend fun saveModel(v: String) {
        context.dataStore.edit { it[KEY_MODEL] = v }
    }

    suspend fun saveCascadeFree(v: String) {
        context.dataStore.edit { it[KEY_CASCADE_FREE] = v }
    }

    suspend fun saveCascadePaid(v: String) {
        context.dataStore.edit { it[KEY_CASCADE_PAID] = v }
    }

    // Шаг 32: метки обратной синхронизации
    suspend fun getLastSyncTimestamp(): Long =
        context.dataStore.data.map { it[KEY_LAST_SYNC] ?: 0L }.first()

    suspend fun setLastSyncTimestamp(v: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNC] = v }
    }

    suspend fun getPendingChangesHash(): String =
        context.dataStore.data.map { it[KEY_PENDING_HASH] ?: "" }.first()

    suspend fun setPendingChangesHash(v: String) {
        context.dataStore.edit { it[KEY_PENDING_HASH] = v }
    }
}
