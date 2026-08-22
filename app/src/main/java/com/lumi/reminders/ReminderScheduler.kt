package com.lumi.reminders

/**
 * Arms the delivery of a stored reminder.
 *
 * A reminder's row already holds its due instant — this interface only answers "make sure
 * something rings at that instant" and the boot/work edge cases around it. Kept behind an
 * interface because the JVM tests need to assert that a created reminder was armed without
 * touching `AlarmManager`, and because the exact-alarm behaviour degrades on permission
 * denial — the capability must not care which road the arm took.
 *
 * v1 lesson carried over: `setExactAndAllowWhileIdle` with graceful degradation, never a
 * scheduler that silently stops firing on Doze.
 */
interface ReminderScheduler {

    /** Make sure the reminder rings at its due instant. Safe to call repeatedly for the same id. */
    fun arm(reminderId: Long, dueAtMs: Long)

    /**
     * Ensure the periodic sweep is registered, so a reminder whose exact delivery was
     * impossible still fires on the next work run. Call on create, on boot, and after
     * every fire.
     */
    fun ensureSweep()

    /** Re-arm every pending reminder after a reboot, and fire anything already due. */
    suspend fun rearmPending()
}
