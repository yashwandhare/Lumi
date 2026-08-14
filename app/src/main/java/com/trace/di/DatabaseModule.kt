package com.trace.di

import android.content.Context
import androidx.room.Room
import com.trace.data.local.AuditDao
import com.trace.data.local.ChatDao
import com.trace.data.local.DocumentDao
import com.trace.data.local.EmergencyContactDao
import com.trace.data.local.JournalDao
import com.trace.data.local.MemoryDao
import com.trace.data.local.RoutineDao
import com.trace.data.local.TraceDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * There is deliberately no `fallbackToDestructiveMigration` here.
     *
     * This database holds the user's notes, journal entries, and emergency contacts.
     * Silently dropping it on a schema mismatch would delete all of that, and the user would
     * find out by opening an empty app. Every schema change gets a real migration; during
     * development, uninstall instead.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TraceDatabase =
        Room.databaseBuilder(context, TraceDatabase::class.java, TraceDatabase.NAME).build()

    @Provides
    fun provideChatDao(database: TraceDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideDocumentDao(database: TraceDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideRoutineDao(database: TraceDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideMemoryDao(database: TraceDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideJournalDao(database: TraceDatabase): JournalDao = database.journalDao()

    @Provides
    fun provideAuditDao(database: TraceDatabase): AuditDao = database.auditDao()

    @Provides
    fun provideEmergencyContactDao(database: TraceDatabase): EmergencyContactDao =
        database.emergencyContactDao()
}
