package com.lumi.di

import android.content.Context
import androidx.room.Room
import com.lumi.data.local.AuditDao
import com.lumi.data.local.ChatDao
import com.lumi.data.local.DocumentDao
import com.lumi.data.local.EmergencyContactDao
import com.lumi.data.local.JournalDao
import com.lumi.data.local.MemoryDao
import com.lumi.data.local.RoutineDao
import com.lumi.data.local.LumiDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): LumiDatabase =
        Room.databaseBuilder(context, LumiDatabase::class.java, LumiDatabase.NAME).build()

    @Provides
    fun provideChatDao(database: LumiDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideDocumentDao(database: LumiDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideRoutineDao(database: LumiDatabase): RoutineDao = database.routineDao()

    @Provides
    fun provideMemoryDao(database: LumiDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideJournalDao(database: LumiDatabase): JournalDao = database.journalDao()

    @Provides
    fun provideAuditDao(database: LumiDatabase): AuditDao = database.auditDao()

    @Provides
    fun provideEmergencyContactDao(database: LumiDatabase): EmergencyContactDao =
        database.emergencyContactDao()
}
