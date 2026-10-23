package com.testogen.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Что мама выбирала в прошлый раз — чтобы не вводить заново. */
class AppPrefs(private val context: Context) {

    /** Класс по умолчанию; null — не указан. */
    val lastGrade: Flow<Int?> = context.prefsStore.data.map { p -> p[GRADE]?.takeIf { it > 0 } }

    /** Перепроверять вопросы вторым проходом нейросети. */
    val review: Flow<Boolean> = context.prefsStore.data.map { it[REVIEW] ?: true }

    /** Курс доллара в рублях для показа цен (по умолчанию — курс сервиса оплаты). */
    val usdRate: Flow<Double> = context.prefsStore.data.map { it[RATE] ?: DEFAULT_RATE }

    suspend fun setUsdRate(value: Double) {
        context.prefsStore.edit { it[RATE] = value }
    }

    suspend fun setLastGrade(grade: Int?) {
        context.prefsStore.edit { it[GRADE] = grade ?: 0 }
    }

    suspend fun setReview(value: Boolean) {
        context.prefsStore.edit { it[REVIEW] = value }
    }

    /** Праздничный экран версии 2.0 уже показан (показывается один раз). */
    val celebrationShown: Flow<Boolean> = context.prefsStore.data.map { it[CELEBRATION] ?: false }

    suspend fun setCelebrationShown() {
        context.prefsStore.edit { it[CELEBRATION] = true }
    }

    /** Какая версия разовой чистки банка уже проведена (см. QuestionRepository.cleanSourceRefs). */
    val cleanupVersion: Flow<Int> = context.prefsStore.data.map { it[CLEANUP] ?: 0 }

    suspend fun setCleanupVersion(value: Int) {
        context.prefsStore.edit { it[CLEANUP] = value }
    }

    companion object {
        const val DEFAULT_RATE = 103.0
        private val RATE = doublePreferencesKey("usd_rate")
        private val GRADE = intPreferencesKey("last_grade")
        private val REVIEW = booleanPreferencesKey("review_questions")
        private val CLEANUP = intPreferencesKey("bank_cleanup_version")
        private val CELEBRATION = booleanPreferencesKey("celebration_2_0_shown")
    }
}
