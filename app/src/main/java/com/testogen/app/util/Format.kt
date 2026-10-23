package com.testogen.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")

/** «23 окт., 14:05». */
fun formatDate(millis: Long): String = SimpleDateFormat("d MMM, HH:mm", ru).format(Date(millis))

/** Цена в долларах и рублях: «$0,0023 · 0,24 ₽». [rate] — рублей за доллар. */
fun formatMoney(usd: Double, rate: Double): String = "${formatUsd(usd)} · ${formatRub(usd * rate)}"

/** Рубли: копейки видны, но без лишних знаков. */
fun formatRub(rub: Double): String = when {
    rub == 0.0 -> "0 ₽"
    rub < 0.01 -> "меньше копейки"
    rub < 100 -> String.format(ru, "%.2f ₽", rub)
    else -> String.format(ru, "%.0f ₽", rub)
}

/** Доллары с точностью, видимой даже для копеечных запросов: «$0,0012», «$1,25». */
fun formatUsd(value: Double): String = when {
    value == 0.0 -> "$0"
    value < 0.01 -> String.format(ru, "$%.4f", value)
    else -> String.format(ru, "$%.2f", value)
}

/** Русское склонение: plural(5, "вопрос", "вопроса", "вопросов") → «вопросов». */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
