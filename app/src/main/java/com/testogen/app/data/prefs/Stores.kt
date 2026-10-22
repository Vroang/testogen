package com.testogen.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Обычные настройки приложения (попадают в резервную копию Android). */
internal val Context.prefsStore by preferencesDataStore(name = "prefs")

/** Ключи нейросети. Файл `datastore/secrets.preferences_pb` исключён из резервной копии (xml/backup_rules*). */
internal val Context.secretsStore by preferencesDataStore(name = "secrets")

/** Ключи DeepSeek и OpenRouter. При сохранении убираются пробелы и переводы строк. */
class KeysRepository(private val context: Context) {

    val deepSeekKey: Flow<String> = context.secretsStore.data.map { it[DEEPSEEK].orEmpty() }
    val openRouterKey: Flow<String> = context.secretsStore.data.map { it[OPENROUTER].orEmpty() }

    suspend fun setDeepSeekKey(value: String) = save(DEEPSEEK, value)

    suspend fun setOpenRouterKey(value: String) = save(OPENROUTER, value)

    private suspend fun save(key: Preferences.Key<String>, value: String) {
        val clean = cleanKey(value)
        context.secretsStore.edit { prefs ->
            if (clean.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = clean
            }
        }
    }

    companion object {
        private val DEEPSEEK = stringPreferencesKey("deepseek_key")
        private val OPENROUTER = stringPreferencesKey("openrouter_key")

        /** Ключ без пробелов, переводов строк и невидимых символов (часто прилипают при вставке). */
        fun cleanKey(raw: String): String = raw.filterNot { it.isWhitespace() || it == '​' || it == '﻿' }
    }
}
