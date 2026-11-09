package com.qlots.app.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val DAY_MS = 86_400_000.0

private fun isoParser() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }

fun parseIso(iso: String?): Date? = try {
    if (iso.isNullOrBlank()) null else isoParser().parse(iso.take(10))
} catch (_: Exception) { null }

/** Days since 1970-01-01 as a chart x-value. */
fun epochDay(iso: String): Double = (parseIso(iso)?.time ?: 0L) / DAY_MS

private fun formatDate(iso: String?, pattern: String): String {
    val date = parseIso(iso) ?: return "—"
    return SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}

/** 10 Nov 2027 */
fun prettyDate(iso: String?): String = formatDate(iso, "d MMM yyyy")

/** 10 Nov */
fun shortDate(iso: String?): String = formatDate(iso, "d MMM")

/** Nov 27 */
fun monthYear(iso: String?): String = formatDate(iso, "MMM yy")

fun axisDate(epochDay: Double): String {
    val date = Date((epochDay * DAY_MS).toLong())
    return SimpleDateFormat("MMM yy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}

fun axisDateFull(epochDay: Double): String {
    val date = Date((epochDay * DAY_MS).toLong())
    return SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}

fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun millisUntilNextMidnight(now: Long = System.currentTimeMillis()): Long {
    val next = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return (next.timeInMillis - now).coerceAtLeast(1_000L)
}

fun durationLabel(months: Int?): String = when {
    months == null -> "—"
    months < 12 -> "$months mo"
    months % 12 == 0 -> "${months / 12} yr"
    else -> "${months / 12} yr ${months % 12} mo"
}

fun pct(value: Double): String = String.format(Locale.US, "%.1f%%", value)

/** Short, evenly formatted axis label: ₹1.5 Cr, ₹36.5 L, ₹80 K. */
fun axisMoney(value: Double): String {
    val sign = if (value < 0) "-" else ""
    val a = kotlin.math.abs(value)
    fun one(x: Double) = String.format(Locale.US, "%.1f", x).removeSuffix(".0")
    return sign + when {
        a >= 10_000_000 -> "₹${one(a / 10_000_000)} Cr"
        a >= 100_000 -> "₹${one(a / 100_000)} L"
        a >= 1_000 -> "₹${one(a / 1_000)} K"
        else -> "₹${a.toLong()}"
    }
}
