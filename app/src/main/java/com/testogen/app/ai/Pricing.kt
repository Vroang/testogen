package com.testogen.app.ai

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

/**
 * Цены DeepSeek V4.1 Flash (модель `deepseek-flash`), $ за 1 млн токенов.
 * Сверено с api-docs.deepseek.com/quick_start/pricing 2026-10-23.
 * «Пиковые» часы — пн–пт 01–04 и 06–10 UTC, в них цена вдвое выше.
 * OpenRouter (`deepseek/deepseek-v4.1-flash-1017`) берёт столько же и сам сообщает стоимость.
 */
object Pricing {
    private const val CACHE_HIT = 0.003
    private const val CACHE_MISS = 0.15
    private const val OUTPUT = 0.6

    fun isPeak(atMillis: Long): Boolean {
        val time = Instant.ofEpochMilli(atMillis).atZone(ZoneOffset.UTC)
        if (time.dayOfWeek == DayOfWeek.SATURDAY || time.dayOfWeek == DayOfWeek.SUNDAY) return false
        val h = time.hour
        return h in 1..3 || h in 6..9
    }

    fun costUsd(cacheHit: Int, cacheMiss: Int, output: Int, atMillis: Long): Double {
        val k = if (isPeak(atMillis)) 2.0 else 1.0
        return k * (cacheHit * CACHE_HIT + cacheMiss * CACHE_MISS + output * OUTPUT) / 1_000_000.0
    }

    /** Грубая оценка числа токенов для русского текста (DeepSeek: ~2,6 символа на токен). */
    fun estimateTokens(text: String): Int = text.length * 10 / 26 + 1
}
