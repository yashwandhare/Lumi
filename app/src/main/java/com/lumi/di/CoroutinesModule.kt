package com.lumi.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/** Disk and network work. Injected rather than referenced directly so tests can substitute it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Model inference. A dedicated single-thread context, not [Dispatchers.Default].
 *
 * The native runtime holds one loaded model and one conversation at a time. Serialising
 * calls through a single thread is what keeps two coroutines from entering it at once, which
 * is a native crash rather than a caught exception.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InferenceDispatcher

/**
 * Lives as long as the process. For work that must outlast the screen that started it —
 * writing an audit entry, finishing an ingest — and must not be cancelled when a view model
 * clears.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @InferenceDispatcher
    fun provideInferenceDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "lumi-inference").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    /**
     * A [SupervisorJob] on purpose: one failed background write must not tear down every
     * other piece of long-lived work in the app.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
