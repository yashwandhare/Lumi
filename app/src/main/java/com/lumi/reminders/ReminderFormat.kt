package com.lumi.reminders

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The plain-language label for when a reminder is due.
 *
 * One formatter rather than inline date formatting at every surface that names a due time —
 * the confirmation line, the audit entry, the chat card, and the list screen must all describe
 * the same instant with the same words, or the user reads two different reminders where one
 * was meant.
 *
 * Pure on purpose: both instants are arguments, so a JVM test pins "now" instead of racing the
 * wall clock. Formatters are built per call because `SimpleDateFormat` is not thread-safe and
 * this runs from both the chat path and background paths; the cost is negligible at reminder
 * frequency. The locale is fixed to US because every other string in the app is English; a
 * locale-dependent label would make the wording untestable and drift per device.
 */
object ReminderFormat {

    /** "today at 6:00 PM", "tomorrow at 9:30 AM", "Friday at 8:00 AM", "Aug 25 at 6:00 PM". */
    fun dueLabel(dueMs: Long, nowMs: Long): String {
        val time = format("h:mm a", dueMs)
        return when (dayDistance(dueMs, nowMs)) {
            in Int.MIN_VALUE..0 -> "today at $time"
            1 -> "tomorrow at $time"
            in 2..6 -> "${format("EEEE", dueMs)} at $time"
            else -> "${datePart(dueMs, nowMs)} at $time"
        }
    }

    /** Whole calendar days between now and the due instant, counted midnight to midnight. */
    private fun dayDistance(dueMs: Long, nowMs: Long): Int {
        val days = (startOfDay(dueMs) - startOfDay(nowMs)) / DAY_MS
        // A zone offset that splits the day unevenly can make an adjacent day read as zero;
        // trust the actual instants over the calendar arithmetic when they disagree.
        return when {
            days < 0 && dueMs > nowMs -> 0
            days > 0 && dueMs < nowMs -> 0
            else -> days.toInt()
        }
    }

    private fun startOfDay(ms: Long): Long = calendarAt(ms).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun datePart(dueMs: Long, nowMs: Long): String =
        if (calendarAt(dueMs).get(Calendar.YEAR) == calendarAt(nowMs).get(Calendar.YEAR)) {
            format("MMM d", dueMs)
        } else {
            format("MMM d, yyyy", dueMs)
        }

    private fun format(pattern: String, ms: Long): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(ms))

    private fun calendarAt(ms: Long): Calendar =
        Calendar.getInstance().apply { timeInMillis = ms }

    private const val DAY_MS = 86_400_000L
}
