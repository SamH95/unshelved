package com.samwise.unshelved.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

fun Double.toHhMmSs(): String {
    val totalSeconds = floor(this).toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun Double.toHoursMinutes(): String {
    val totalMinutes = (this / 60).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

fun Double.toRemainingHhMmSs(): String {
    return "-${toHhMmSs()}"
}

fun Long.toLocalizedDate(): String {
    val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    val locale = Locale.getDefault()
    val sameYear = date.year == LocalDate.now().year
    val skeleton = if (sameYear) "MMMMd" else "MMMMdyyyy"
    val pattern = android.icu.text.DateTimePatternGenerator
        .getInstance(locale)
        .getBestPattern(skeleton)
        .replace(Regex("E+,?\\s*"), "")
        .replace(Regex(",?\\s*E+"), "")
        .trim()
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
