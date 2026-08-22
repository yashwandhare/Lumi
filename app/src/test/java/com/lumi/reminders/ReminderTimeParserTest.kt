package com.lumi.reminders

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins what a spoken sentence means as an instant.
 *
 * These are the demo phrases and their honest readings: "at six" said at seven rings tomorrow,
 * not never; "today at nine" said at eleven is refused rather than quietly moved; relative
 * offsets count from the moment they were said. Every case pins "now" instead of racing the
 * wall clock, because the parser takes both instants as arguments for exactly this reason.
 */
class ReminderTimeParserTest {

    /** Wednesday, 10 June 2026, 10:00 local — a date no zone shifts around. */
    private val now: Long = local(2026, month = 6, day = 10, hour = 10)

    @Test
    fun `a relative offset counts from the moment it was said`() {
        assertEquals(now + 20 * MINUTE_MS, parse("remind me in 20 minutes"))
    }

    @Test
    fun `an hour written as a word still counts forward`() {
        assertEquals(now + HOUR_MS, parse("remind me in an hour"))
    }

    @Test
    fun `a bare hour ahead of now lands today`() {
        assertEquals(local(2026, 6, 10, 18), parse("remind me to call mom at six"))
    }

    @Test
    fun `a bare hour with both faces behind rolls to tomorrow's earlier face`() {
        val eveningNow = local(2026, 6, 10, 19)
        // Six and eighteen-hundred are both behind an evening "now", so the next time the
        // clock reads six is tomorrow morning — the honest next occurrence of an ambiguous hour.
        assertEquals(dayAfter(eveningNow, atHour = 6), parse("call mom at six", nowMs = eveningNow))
    }

    @Test
    fun `an explicit am or pm pins its half of the day`() {
        assertEquals(local(2026, 6, 10, 21, 30), parse("remind me at 9:30 pm"))
    }

    @Test
    fun `hours written as words are clock times`() {
        val earlyNow = local(2026, 6, 10, 8)
        assertEquals(local(2026, 6, 10, 9), parse("remind me at nine", nowMs = earlyNow))
    }

    @Test
    fun `tomorrow keeps its named day even when that hour has passed today`() {
        assertEquals(
            dayAfter(now, atHour = 7),
            parse("water the plants tomorrow at 7 am"),
        )
    }

    @Test
    fun `today with a time already past is refused rather than moved`() {
        assertNull(parse("call the bank today at nine", nowMs = local(2026, 6, 10, 11)))
    }

    @Test
    fun `tonight defaults to eight oclock while eight is still ahead`() {
        assertEquals(local(2026, 6, 10, 20), parse("remind me tonight"))
    }

    @Test
    fun `tonight said after eight slips to tomorrow evening`() {
        assertEquals(
            dayAfter(now, atHour = 20),
            parse("remind me tonight", nowMs = local(2026, 6, 10, 21)),
        )
    }

    @Test
    fun `a named weekday rolls forward to its next occurrence`() {
        assertEquals(local(2026, 6, 12, 17), parse("submit the report on friday at 5 pm"))
    }

    @Test
    fun `minutes survive into the instant`() {
        assertEquals(local(2026, 6, 10, 18, 45), parse("start dinner by 6:45"))
    }

    @Test
    fun `a period word alone names its default morning hour`() {
        assertEquals(local(2026, 6, 11, 8), parse("take the tablets in the morning"))
    }

    @Test
    fun `no readable time returns null`() {
        assertNull(parse("remind me someday"))
    }

    private fun parse(utterance: String, nowMs: Long = now): Long? =
        ReminderTimeParser.parse(utterance, nowMs)

    /** The same wall-clock [atHour] on the day after [dayMs], in local time. */
    private fun dayAfter(dayMs: Long, atHour: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayMs
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, atHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 3_600_000L
    }
}
