package com.lumi.reminders

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Turns the sentence the user said into the instant their reminder is due.
 *
 * Pure and deterministic for a given clock: no Android types, every instant derived from
 * [nowMs] — nothing reads the wall clock, so a JVM test can fix the moment instead of racing
 * midnight. Parsed **at creation time only**: the stored row carries the resolved instant,
 * and a firing worker reads that row and never re-interprets the sentence, the same rule
 * routines follow in `todo.md`.
 *
 * Returns the due instant, or null when the utterance carries no parseable time — including
 * a time the day cannot honour ("today at nine" said at eleven). A null does not drop the
 * request: callers store the row as a never-due todo, which the list screen still shows.
 *
 * A bare hour or a period ("in the morning") means the next occurrence: today if it is still
 * ahead, the next day once it has passed. That is the phrasing that matters for the demo —
 * "remind me at 6" said at 7 cannot ring in the past. A bare hour is read as whichever face,
 * am or pm, comes round next, because "at six" said in the afternoon almost never means a
 * dawn alarm; an explicit "am", "pm", or period word overrides that reading entirely.
 *
 * Repeating phrasings ("every day at 8") return null for now: the widget's explicit picker is
 * where repeats land first, so natural language keeps honest behaviour instead of a
 * half-built repeat state machine.
 */
object ReminderTimeParser {

    /**
     * A time closer than this to now is treated as already past. A reminder due in five
     * seconds would ring while the user is still reading the confirmation — the next
     * occurrence is the kinder reading. Relative offsets ("in 20 minutes") are exempt: they
     * are exact by construction.
     */
    private const val MINIMUM_LEAD_MS = 60_000L

    private val WORD_HOURS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    /** Parsed time-of-day. [explicitPeriod] is true when am/pm or a period word decided it. */
    private data class ClockTime(
        val hourOfDay: Int,
        val minute: Int,
        val explicitPeriod: Boolean,
    )

    /** The day the utterance named, with the default hour to use when the utterance gave none. */
    private data class ParsedDay(
        val calendar: Calendar,
        val defaultClock: ClockTime = ClockTime(9, 0, explicitPeriod = false),
    )

    fun parse(utterance: String, nowMs: Long): Long? {
        val normalized = normalize(utterance)

        offsetFromNow(normalized, nowMs)?.let { return it }

        val clock = clockOf(normalized)

        val day = resolveDay(normalized, nowMs, clock)
        if (day != null) {
            val due = instantOf(day.calendar, clock ?: day.defaultClock)
            // A named "today" whose time has passed cannot be moved quietly: the user said
            // today, and a tomorrow surprise is worse than an honest "no time" todo.
            return due.takeIf { it > nowMs } ?: return null
        }

        val calendar = calendarAt(nowMs)
        clock?.let {
            // A bare hour ("at six") is ambiguous between its two faces; the nearest
            // upcoming face is what a speaker almost always means. An explicit period
            // ("6 pm", "six in the evening") already decided, so it takes the plain path.
            return if (it.explicitPeriod) {
                nextOccurrence(instantOf(calendar, it), nowMs)
            } else {
                nearestFace(it, nowMs)
            }
        }

        periodOf(normalized)?.let { hour ->
            return nextOccurrence(instantOf(calendar, ClockTime(hour, 0, explicitPeriod = false)), nowMs)
        }
        return null
    }

    private fun normalize(utterance: String): String =
        utterance.lowercase(Locale.getDefault())
            .replace(" -", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

    /** "in 20 minutes", "in an hour" — counted forward from the moment it was said, exact. */
    private fun offsetFromNow(normalized: String, nowMs: Long): Long? {
        val match = Regex("\\bin (\\d+|an?)\\s*(minute|hour|day)s?\\b").find(normalized) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: 1L
        val unitMs = when (match.groupValues[2]) {
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            else -> 86_400_000L
        }
        return nowMs + amount * unitMs
    }

    /** The day the utterance names, or null when none is mentioned. */
    private fun resolveDay(normalized: String, nowMs: Long, clock: ClockTime?): ParsedDay? {
        when {
            Regex("\\btomorrow\\b").containsMatchIn(normalized) -> {
                val calendar = calendarAt(nowMs)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                return ParsedDay(calendar)
            }

            Regex("\\btoday\\b").containsMatchIn(normalized) ->
                return ParsedDay(calendarAt(nowMs))

            Regex("\\btonight\\b").containsMatchIn(normalized) -> {
                val hourOfDay = clock?.hourOfDay ?: 20
                val calendar = calendarAt(nowMs)
                val tonight = instantOf(calendar, ClockTime(hourOfDay, 0, explicitPeriod = false))
                if (tonight <= nowMs) calendar.add(Calendar.DAY_OF_YEAR, 1)
                return ParsedDay(calendar, defaultClock = ClockTime(20, 0, explicitPeriod = false))
            }
        }

        val weekday = weekdayOf(normalized) ?: return null
        val calendar = calendarAt(nowMs)
        var guard = 0
        while (calendar.get(Calendar.DAY_OF_WEEK) != weekday && guard++ < 7) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return ParsedDay(calendar)
    }

    private fun weekdayOf(normalized: String): Int? = when {
        Regex("\\bmonday\\b").containsMatchIn(normalized) -> Calendar.MONDAY
        Regex("\\btuesday\\b").containsMatchIn(normalized) -> Calendar.TUESDAY
        Regex("\\bwednesday\\b").containsMatchIn(normalized) -> Calendar.WEDNESDAY
        Regex("\\bthursday\\b").containsMatchIn(normalized) -> Calendar.THURSDAY
        Regex("\\bfriday\\b").containsMatchIn(normalized) -> Calendar.FRIDAY
        Regex("\\bsaturday\\b").containsMatchIn(normalized) -> Calendar.SATURDAY
        Regex("\\bsunday\\b").containsMatchIn(normalized) -> Calendar.SUNDAY
        else -> null
    }

    /**
     * The explicit hour the sentence names: "at six", "at 9:30", "2 pm", "by 6 in the morning".
     * Word hours ("at nine") count too — the demo phrasebook uses them. A period word pins
     * am/pm; a bare hour keeps its face value and lets the next-occurrence rule move it.
     */
    private fun clockOf(normalized: String): ClockTime? {
        val explicit = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b").find(normalized)
        explicit?.let {
            val hour12 = it.groupValues[1].toIntOrNull() ?: return null
            if (hour12 !in 1..23) return null
            val minute = it.groupValues[2].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
            val hourOfDay = when (it.groupValues[3]) {
                "am" -> if (hour12 == 12) 0 else hour12
                else -> if (hour12 == 12) 12 else hour12 + 12
            }
            return ClockTime(hourOfDay, minute, explicitPeriod = true)
        }

        val hourWord = WORD_HOURS.entries.firstOrNull { (word, _) ->
            Regex("\\b(?:at|by) $word\\b").containsMatchIn(normalized)
        }
        hourWord?.let { return ClockTime(it.value, 0, explicitPeriod = false) }

        val clockWord = Regex("\\b(?:at|by) (\\d{1,2})(?::(\\d{2}))?(?=\\s|$)").find(normalized)
        clockWord?.let {
            val hour = it.groupValues[1].toIntOrNull() ?: return null
            if (hour !in 0..23) return null
            val minute = it.groupValues[2].takeIf(String::isNotEmpty)?.toIntOrNull() ?: 0
            periodOf(normalized)?.let { periodHour ->
                return ClockTime(shiftIntoPeriod(hour, periodHour), minute, explicitPeriod = true)
            }
            return ClockTime(hour, minute, explicitPeriod = false)
        }

        return null
    }

    /** "tonight"/"evening"/"afternoon"/"morning" name a default hour even without a number. */
    private fun periodOf(normalized: String): Int? = when {
        Regex("\\btonight\\b").containsMatchIn(normalized) -> 20
        Regex("\\bevening\\b").containsMatchIn(normalized) -> 18
        Regex("\\bafternoon\\b").containsMatchIn(normalized) -> 14
        Regex("\\bmorning\\b").containsMatchIn(normalized) -> 8
        else -> null
    }

    private fun shiftIntoPeriod(hour: Int, periodHour: Int): Int =
        if (periodHour >= 12 && hour < 12) hour + 12 else hour

    /**
     * The epoch instant for the given day + clock. The calendar was built from [nowMs], so
     * setting local fields on it stays correct across the time zone and DST boundaries.
     */
    private fun instantOf(day: Calendar, clock: ClockTime): Long {
        day.set(Calendar.HOUR_OF_DAY, clock.hourOfDay)
        day.set(Calendar.MINUTE, clock.minute)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)
        return day.timeInMillis
    }

    /**
     * The next occurrence of a wall-clock moment: today when it is still ahead of now, the
     * next day once it has passed or comes too close to ring usefully.
     */
    private fun nextOccurrence(dueMs: Long, nowMs: Long): Long {
        if (dueMs >= nowMs + MINIMUM_LEAD_MS) return dueMs
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = dueMs
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    /**
     * The nearest upcoming reading of an hour with no am/pm attached: "at six" said at ten
     * means eighteen hundred, and the same sentence at nineteen means tomorrow morning.
     * Both faces ahead picks the closer one; both behind rolls to tomorrow's earlier face,
     * which is the honest next occurrence of an ambiguous hour.
     */
    private fun nearestFace(clock: ClockTime, nowMs: Long): Long {
        val lead = nowMs + MINIMUM_LEAD_MS
        val first = instantOf(calendarAt(nowMs), clock)
        val altHour = if (clock.hourOfDay < 12) clock.hourOfDay + 12 else clock.hourOfDay - 12
        val second = instantOf(calendarAt(nowMs), clock.copy(hourOfDay = altHour))
        return when {
            first >= lead && second >= lead -> minOf(first, second)
            first >= lead -> first
            second >= lead -> second
            else -> nextOccurrence(minOf(first, second), nowMs)
        }
    }

    private fun calendarAt(nowMs: Long): Calendar =
        Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = nowMs }
}
