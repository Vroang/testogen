package com.testogen.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")

/** «23 окт., 14:05». */
fun formatDate(millis: Long): String = SimpleDateFormat("d MMM, HH:mm", ru).format(Date(millis))

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
