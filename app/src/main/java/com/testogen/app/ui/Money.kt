package com.testogen.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.testogen.app.data.prefs.AppPrefs
import com.testogen.app.util.formatMoney

/** Курс доллара в рублях для показа цен; задаётся в настройках. */
val LocalUsdRate = compositionLocalOf { AppPrefs.DEFAULT_RATE }

/** Цена в долларах и рублях по текущему курсу. */
@Composable
@ReadOnlyComposable
fun money(usd: Double): String = formatMoney(usd, LocalUsdRate.current)
