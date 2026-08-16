package com.lumi.di

import com.lumi.core.ai.Embedder
import com.lumi.core.ai.ModelHarness
import com.lumi.data.ai.LiteRtModelHarness
import com.lumi.data.ai.MediaPipeEmbedder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one implementation of [ModelHarness].
 *
 * `@Singleton` is not a convenience here — it is the mechanism that enforces "the model loads once
 * and stays resident". A second instance would mean a second `Engine`, and a cold load costs tens of
 * seconds. Anything that needs generation injects the interface and gets the same object.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindModelHarness(implementation: LiteRtModelHarness): ModelHarness

    /**
     * One embedder for the whole app, for the same reason as the harness: it holds a loaded native
     * model, and a second instance would mean a second 180MB load.
     */
    @Binds
    @Singleton
    abstract fun bindEmbedder(implementation: MediaPipeEmbedder): Embedder
}
