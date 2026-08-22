package com.lumi.reminders

/**
 * Notified once a captured reminder row has landed.
 *
 * Exists because the thing that wants to know — the home-screen widget, which must not show a
 * stale list after a capture — cannot be reached from here: this class runs in JVM tests where
 * no Context exists, and handing it one would end that. The DI graph supplies the real
 * implementation (a widget re-render); tests supply a recorder.
 */
fun interface OnReminderSaved {
    suspend fun onSaved(reminderId: Long)
}
