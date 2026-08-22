package com.lumi.di

import android.app.NotificationManager
import android.content.Context
import com.lumi.core.audit.AuditLog
import com.lumi.data.local.ReminderDao
import com.lumi.reminders.AlarmScheduler
import com.lumi.reminders.Clock
import com.lumi.reminders.FireProcessor
import com.lumi.reminders.OnReminderSaved
import com.lumi.reminders.ReminderNotifier
import com.lumi.reminders.ReminderScheduler
import com.lumi.ui.widget.refreshRemindersWidget
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Joins the reminder execution path together.
 *
 * The notifier is deliberately built here rather than injected into [FireProcessor]: the fire
 * path must also be reachable from a worker where Hilt construction does not exist, so the
 * processor keeps taking a plain function and this is the one place that knows it posts a
 * system notification.
 */
@Module
@InstallIn(SingletonComponent::class)
object RemindersModule {

    @Provides
    @Singleton
    fun provideFireProcessor(
        dao: ReminderDao,
        audit: AuditLog,
        scheduler: ReminderScheduler,
        @ApplicationContext context: Context,
    ): FireProcessor {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val notifier = ReminderNotifier(context, notifications)
        return FireProcessor(
            dao = dao,
            audit = audit,
            notifier = { reminder -> notifier.post(reminder) },
            scheduler = scheduler,
        )
    }

    @Provides
    fun provideClock(): Clock = Clock(System::currentTimeMillis)

    /**
     * The widget is the listener: every capture re-renders it, so it never shows a stale list
     * after "remind me…" lands. The capability itself stays Context-free for JVM testing.
     */
    @Provides
    @Singleton
    fun provideOnReminderSaved(@ApplicationContext context: Context): OnReminderSaved =
        OnReminderSaved { refreshRemindersWidget(context) }
}

/** The alarm-backed [ReminderScheduler]. Bound as an interface so tests can hand in a fake. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersBindingsModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(implementation: AlarmScheduler): ReminderScheduler
}
