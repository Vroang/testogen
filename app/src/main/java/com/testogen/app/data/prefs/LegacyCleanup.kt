package com.testogen.app.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File

/** Настройки старой версии (до переписывания) — читаются один раз ради ключа OpenRouter. */
private val Context.legacySettingsStore by preferencesDataStore(name = "settings")

/**
 * Один раз после обновления со старой версии (≤ 49) убирает её следы:
 * старую базу, файлы учебников, настройки и сессию Supabase.
 * Ключ OpenRouter переносится в новое хранилище ключей.
 */
object LegacyCleanup {

    private val DONE = booleanPreferencesKey("legacy_cleanup_done")
    private val OLD_API_KEY = stringPreferencesKey("api_key")

    suspend fun runOnce(context: Context, keys: KeysRepository) {
        if (context.prefsStore.data.first()[DONE] == true) return
        try {
            val oldSettings = File(context.filesDir, "datastore/settings.preferences_pb")
            if (oldSettings.exists()) {
                val oldKey = context.legacySettingsStore.data.first()[OLD_API_KEY].orEmpty()
                if (oldKey.isNotBlank() && keys.openRouterKey.first().isBlank()) {
                    keys.setOpenRouterKey(oldKey)
                }
                oldSettings.delete()
            }
            for (name in listOf("testogen.db", "testogen.db-wal", "testogen.db-shm", "testogen.db-journal")) {
                context.getDatabasePath(name).delete()
            }
            File(context.filesDir, "textbooks").deleteRecursively()
            File(context.cacheDir, "shared").deleteRecursively()
            // Сессия Supabase и прочие настройки старых библиотек
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w("LegacyCleanup", "Не удалось убрать старые данные", e)
        }
        context.prefsStore.edit { it[DONE] = true }
    }
}
